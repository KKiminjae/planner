#!/usr/bin/env python3
"""Root-only JAR deployment with backup, migration checks and automatic rollback."""
import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import zipfile

APP = Path('/home/ubuntu/photo-calendar')
LIVE = APP / 'photo_calendar-0.0.1-SNAPSHOT.jar'
RELEASES = APP / 'releases'
BACKUPS = Path('/var/backups/photo-calendar')
PUBLIC_URL = 'https://photo-calendar.15-165-115-190.sslip.io'


class DeploymentError(Exception):
    pass


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def migrations(path):
    with zipfile.ZipFile(path) as archive:
        if 'META-INF/MANIFEST.MF' not in archive.namelist() or archive.testzip():
            raise DeploymentError('Invalid application JAR')
        files = {name: hashlib.sha256(archive.read(name)).hexdigest()
                 for name in archive.namelist()
                 if name.startswith('BOOT-INF/classes/db/migration/') and not name.endswith('/')}
    if not files:
        raise DeploymentError('No embedded migrations found')
    return files


def run(*args):
    subprocess.run(args, check=True, cwd=APP)


def check_endpoints(base):
    with urllib.request.urlopen(base + '/api/auth/csrf', timeout=5) as response:
        body = json.load(response)
        if response.status != 200 or body.get('headerName') != 'X-CSRF-TOKEN' or not body.get('token'):
            raise DeploymentError('CSRF endpoint check failed')
    try:
        with urllib.request.urlopen(base + '/api/auth/me', timeout=5):
            raise DeploymentError('Unauthenticated account endpoint was allowed')
    except urllib.error.HTTPError as error:
        with error:
            if error.code != 401 or json.load(error).get('code') != 'AUTHENTICATION_REQUIRED':
                raise DeploymentError('Account endpoint check failed')


def healthy(timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            run('systemctl', 'is-active', '--quiet', 'photo-calendar.service')
            check_endpoints('http://127.0.0.1:8080')
            check_endpoints(PUBLIC_URL)
            return
        except (DeploymentError, subprocess.CalledProcessError, OSError, ValueError):
            time.sleep(2)
    raise DeploymentError('Startup/API checks did not pass within the readiness window')


def install_live(source, owner):
    descriptor, name = tempfile.mkstemp(prefix='.deploy-jar-', dir=APP)
    try:
        with os.fdopen(descriptor, 'wb') as target, source.open('rb') as original:
            shutil.copyfileobj(original, target)
            target.flush()
            os.fsync(target.fileno())
        os.chown(name, owner.st_uid, owner.st_gid)
        os.chmod(name, 0o600)
        os.replace(name, LIVE)
    finally:
        Path(name).unlink(missing_ok=True)


def save_record(directory, record):
    temporary = directory / 'manifest.json.partial'
    temporary.write_text(json.dumps(record, indent=2) + '\n')
    os.replace(temporary, directory / 'manifest.json')


def deployment(source, expected_sha, reviewed_migrations=False, rollback_of=None):
    if not re.fullmatch(r'[a-f0-9]{64}', expected_sha):
        raise DeploymentError('Expected SHA-256 must have 64 lowercase hexadecimal characters')
    if LIVE.is_symlink() or not LIVE.is_file():
        raise DeploymentError('Current JAR must be a regular file')
    owner = LIVE.stat()
    RELEASES.mkdir(mode=0o700, exist_ok=True)
    release_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8]
    directory = RELEASES / release_id
    directory.mkdir(mode=0o700)
    previous, candidate = directory / 'previous.jar', directory / 'candidate.jar'
    shutil.copyfile(LIVE, previous)
    shutil.copyfile(source, candidate)
    for artifact in (previous, candidate):
        artifact.chmod(0o600)
    if digest(candidate) != expected_sha:
        raise DeploymentError('Uploaded JAR SHA-256 mismatch; current application unchanged')
    old_sql, new_sql = migrations(previous), migrations(candidate)
    changed = old_sql != new_sql
    if any(new_sql.get(name) != checksum for name, checksum in old_sql.items()):
        raise DeploymentError('Existing migrations were removed or edited; deployment refused')
    if changed and not reviewed_migrations:
        raise DeploymentError('New migrations require --reviewed-migrations; review DB recovery first')
    record = {'release_id': release_id, 'previous_sha256': digest(previous),
              'candidate_sha256': expected_sha, 'migration_changed': changed,
              'rollback_of': rollback_of, 'status': 'preparing', 'backup': None}
    save_record(directory, record)
    print(f'Release {release_id}: migrations changed={changed}', flush=True)
    backup_lock = None
    swapped = False
    try:
        # A successful backup unit includes upload and content verification.
        existing_backups = set(BACKUPS.glob('db-*.sql.gz'))
        run('systemctl', 'start', 'photo-calendar-backup.service')
        backup_lock = (BACKUPS / '.backup.lock').open('a')
        fcntl.flock(backup_lock, fcntl.LOCK_EX)
        fresh_backups = set(BACKUPS.glob('db-*.sql.gz')) - existing_backups
        if not fresh_backups:
            raise DeploymentError('No fresh pre-deployment backup was created')
        latest = max(fresh_backups, key=lambda item: item.stat().st_mtime_ns)
        run('sha256sum', '--check', '--status', str(latest) + '.sha256')
        record['backup'] = latest.name
        record['backup_sha256'] = digest(latest)
        save_record(directory, record)
        print(f'Pre-deployment backup verified: {latest.name}', flush=True)
        # Stop first so no classes are read from a partially replaced artifact.
        run('systemctl', 'stop', 'photo-calendar.service')
        swapped = True
        install_live(candidate, owner)
        run('systemctl', 'start', 'photo-calendar.service')
        healthy()
        record['status'] = 'success'
        save_record(directory, record)
        print(f'Deployment and local/public API checks passed: {release_id}', flush=True)
        return release_id
    except (Exception, KeyboardInterrupt) as error:
        record['error_type'] = type(error).__name__
        if not swapped:
            record['status'] = 'failed-before-replacement'
        elif changed:
            record['status'] = 'failed-migration-review-required'
            save_record(directory, record)
            run('systemctl', 'stop', 'photo-calendar.service')
            print('Application stopped. DB may have changed; JAR-only rollback was not attempted.', flush=True)
        else:
            record['status'] = 'rollback-failed'
            save_record(directory, record)
            try:
                run('systemctl', 'stop', 'photo-calendar.service')
                install_live(previous, owner)
                run('systemctl', 'start', 'photo-calendar.service')
                healthy()
                record['status'] = 'rolled-back'
                print('Previous JAR restored; local/public API checks passed.', flush=True)
            finally:
                save_record(directory, record)
        save_record(directory, record)
        raise DeploymentError(f'Deployment failed; result={record["status"]}, release={release_id}') from error
    finally:
        if backup_lock:
            backup_lock.close()


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command', required=True)
    deploy = commands.add_parser('deploy')
    deploy.add_argument('incoming_jar', type=Path)
    deploy.add_argument('sha256')
    deploy.add_argument('--reviewed-migrations', action='store_true')
    rollback = commands.add_parser('rollback')
    rollback.add_argument('release_id')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run with sudo on the deployment server')
    os.umask(0o077)
    with Path('/var/lock/photo-calendar-deploy.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise DeploymentError('Another deployment is running')
        if args.command == 'deploy':
            source = args.incoming_jar.resolve(strict=True)
            if not source.is_relative_to((APP / 'incoming').resolve()) or not source.is_file():
                raise DeploymentError('Incoming artifact must be inside the server incoming directory')
            deployment(source, args.sha256, args.reviewed_migrations)
        else:
            if not re.fullmatch(r'[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}', args.release_id):
                raise DeploymentError('Invalid release ID')
            directory = RELEASES / args.release_id
            record = json.loads((directory / 'manifest.json').read_text())
            previous = directory / 'previous.jar'
            if record['status'] not in ('success', 'rolled-back'):
                raise DeploymentError('Only a completed release can be selected for rollback')
            if migrations(LIVE) != migrations(previous):
                raise DeploymentError('DB migration files differ; JAR-only rollback refused')
            deployment(previous, record['previous_sha256'], rollback_of=args.release_id)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Avoid dumping environment variables, credentials or SQL contents.
        print(str(error) if isinstance(error, DeploymentError) else type(error).__name__, flush=True)
        raise SystemExit(1)
