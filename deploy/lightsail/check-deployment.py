#!/usr/bin/env python3
"""Verify deployment failure handling in temporary files with mocked services."""
import gzip
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('deploy_release', Path(__file__).with_name('deploy-release.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentChecks(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        root = Path(self.work.name)
        self.app, self.backups = root / 'app', root / 'backups'
        self.app.mkdir()
        self.backups.mkdir()
        self.live, self.candidate = self.app / 'app.jar', self.app / 'incoming.jar'
        self.jar(self.live, b'old application')
        self.jar(self.candidate, b'new application')
        backup = self.backups / 'db-20261002T000000-123.sql.gz'
        backup.write_bytes(gzip.compress(b'-- test dump'))
        backup.with_name(backup.name + '.sha256').write_text(deploy.digest(backup) + '  ' + str(backup))
        for name, value in [('APP', self.app), ('LIVE', self.live),
                            ('RELEASES', self.app / 'releases'), ('BACKUPS', self.backups)]:
            mock = patch.object(deploy, name, value)
            mock.start()
            self.addCleanup(mock.stop)
        def completed_command(*args):
            if args == ('systemctl', 'start', 'photo-calendar-backup.service'):
                fresh = backup.with_name('db-20261002T000001-456.sql.gz')
                fresh.write_bytes(backup.read_bytes())
                fresh.with_name(fresh.name + '.sha256').write_text(deploy.digest(fresh) + '  ' + str(fresh))
        self.run = patch.object(deploy, 'run', side_effect=completed_command).start()
        self.health = patch.object(deploy, 'healthy').start()
        self.addCleanup(patch.stopall)

    def jar(self, path, application, extra=False, old_sql=b'CREATE TABLE test(id INT);'):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
            archive.writestr('BOOT-INF/classes/db/migration/V1__initial.sql', old_sql)
            if extra:
                archive.writestr('BOOT-INF/classes/db/migration/V2__extra.sql', 'ALTER TABLE test ADD note TEXT;')
            archive.writestr('BOOT-INF/classes/application.bin', application)

    def record(self):
        return json.loads(next((self.app / 'releases').glob('*/manifest.json')).read_text())

    def execute(self, reviewed=False):
        return deploy.deployment(self.candidate, deploy.digest(self.candidate), reviewed)

    def test_success_keeps_previous_and_records_verified_backup(self):
        old = self.live.read_bytes()
        self.execute()
        self.assertEqual(self.live.read_bytes(), self.candidate.read_bytes())
        self.assertEqual(next((self.app / 'releases').glob('*/previous.jar')).read_bytes(), old)
        self.assertEqual(self.record()['status'], 'success')
        self.assertIsNotNone(self.record()['backup_sha256'])

    def test_failed_startup_restores_original_and_checks_it(self):
        old = self.live.read_bytes()
        self.health.side_effect = [deploy.DeploymentError('simulated startup failure'), None]
        with self.assertRaises(deploy.DeploymentError):
            self.execute()
        self.assertEqual(self.live.read_bytes(), old)
        self.assertEqual(self.record()['status'], 'rolled-back')
        self.assertEqual(self.health.call_count, 2)

    def test_backup_failure_never_stops_or_replaces_app(self):
        old = self.live.read_bytes()
        self.run.side_effect = RuntimeError('simulated backup failure')
        with self.assertRaises(deploy.DeploymentError):
            self.execute()
        self.assertEqual(self.live.read_bytes(), old)
        self.assertEqual(self.run.call_count, 1)
        self.health.assert_not_called()
        self.assertEqual(self.record()['status'], 'failed-before-replacement')

    def test_success_exit_without_fresh_backup_never_replaces_app(self):
        old = self.live.read_bytes()
        for backup in self.backups.glob('*.gz'):
            os.utime(backup, (1, 1))
        self.run.side_effect = None
        with self.assertRaises(deploy.DeploymentError):
            self.execute()
        self.assertEqual(self.live.read_bytes(), old)
        self.health.assert_not_called()
        self.assertEqual(self.run.call_count, 1)

    def test_new_migration_requires_review_before_backup(self):
        old = self.live.read_bytes()
        self.jar(self.candidate, b'new', extra=True)
        with self.assertRaises(deploy.DeploymentError):
            self.execute()
        self.assertEqual(self.live.read_bytes(), old)
        self.run.assert_not_called()

    def test_edited_migration_rejected_even_with_review_flag(self):
        self.jar(self.candidate, b'new', old_sql=b'DROP TABLE test;')
        with self.assertRaises(deploy.DeploymentError):
            self.execute(reviewed=True)
        self.run.assert_not_called()

    def test_reviewed_migration_failure_does_not_restore_old_jar(self):
        self.jar(self.candidate, b'new', extra=True)
        self.health.side_effect = deploy.DeploymentError('simulated migration failure')
        with self.assertRaises(deploy.DeploymentError):
            self.execute(reviewed=True)
        self.assertEqual(self.live.read_bytes(), self.candidate.read_bytes())
        self.assertEqual(self.record()['status'], 'failed-migration-review-required')
        self.assertEqual(self.health.call_count, 1)
        self.assertEqual(self.run.call_args.args, ('systemctl', 'stop', 'photo-calendar.service'))

    def test_hash_mismatch_does_not_touch_service(self):
        old = self.live.read_bytes()
        with self.assertRaises(deploy.DeploymentError):
            deploy.deployment(self.candidate, '0' * 64)
        self.assertEqual(self.live.read_bytes(), old)
        self.run.assert_not_called()

    def test_failed_rollback_is_never_recorded_as_success(self):
        self.health.side_effect = deploy.DeploymentError('simulated health failure')
        with self.assertRaises(deploy.DeploymentError):
            self.execute()
        self.assertEqual(self.record()['status'], 'rollback-failed')


if __name__ == '__main__':
    unittest.main()
