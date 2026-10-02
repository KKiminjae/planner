#!/usr/bin/env python3
"""Apply a verified web bundle using the existing guarded backend deployer."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import time
import urllib.request
import urllib.error

APP = Path('/home/ubuntu/photo-calendar')
WEB = Path('/var/www/photo-calendar')
SITE = Path('/etc/nginx/sites-available/photo-calendar')
URL = 'https://photo-calendar.15-165-115-190.sslip.io'


def run(*args):
    subprocess.run(args, check=True)


def fetch(path):
    with urllib.request.urlopen(URL + path, timeout=15) as response:
        return response.read(), response.headers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('bundle', type=Path)
    parser.add_argument('sha256')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run with sudo')
    bundle = args.bundle.resolve(strict=True)
    if not bundle.is_relative_to(APP / 'incoming'):
        parser.error('Bundle must be in incoming')
    if hashlib.sha256(bundle.read_bytes()).hexdigest() != args.sha256:
        raise RuntimeError('Bundle checksum mismatch')
    staging = bundle.parent / 'verified'
    staging.mkdir(mode=0o700)
    with tarfile.open(bundle, 'r:gz') as archive:
        for member in archive.getmembers():
            if not member.isfile() or member.name.startswith('/') or '..' in Path(member.name).parts:
                raise RuntimeError('Unexpected archive entry')
        archive.extractall(staging, filter='data')
    manifest = json.loads((staging / 'manifest.json').read_text())
    for name, expected in manifest['files'].items():
        path = staging / name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise RuntimeError('Artifact verification failed')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    release = WEB / 'releases' / stamp
    WEB.mkdir(mode=0o755, parents=True, exist_ok=True)
    release.parent.mkdir(mode=0o755, exist_ok=True)
    shutil.copytree(staging / 'frontend', release)
    for path in [release, *release.rglob('*')]:
        path.chmod(0o755 if path.is_dir() else 0o644)
    backup = bundle.parent / 'previous-nginx.conf'
    shutil.copy2(SITE, backup)
    current = WEB / 'current'
    if current.exists() and not current.is_symlink():
        raise RuntimeError('Current web path must be a symlink')
    previous = os.readlink(current) if current.is_symlink() else None
    print('Bundle verified; deploying backend with pre-deployment DB/S3 backup.', flush=True)
    command = ['/usr/local/sbin/photo-calendar-deploy', 'deploy', str(staging / 'backend.jar'), manifest['files']['backend.jar']]
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, flush=True)
    if result.returncode:
        raise RuntimeError('Backend deployment failed; web configuration unchanged')
    match = re.search(r'Deployment and local/public API checks passed: ([0-9]{8}T[0-9]{6}Z-[a-f0-9]{8})', result.stdout)
    if not match:
        raise RuntimeError('Backend release ID missing; web configuration unchanged')
    backend_id = match.group(1)
    record = {'backend_release': backend_id, 'web_release': stamp, 'previous_web': previous, 'status': 'applying'}
    record_path = bundle.parent / 'web-deployment.json'
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    try:
        temporary = WEB / ('current-' + stamp)
        temporary.symlink_to(release)
        os.replace(temporary, current)
        shutil.copyfile(staging / 'nginx-https.conf', SITE)
        SITE.chmod(0o644)
        run('nginx', '-t')
        run('systemctl', 'reload', 'nginx')
        for name, expected in manifest['files'].items():
            if name.startswith('frontend/'):
                deadline = time.monotonic() + 20
                while True:
                    try:
                        content, _ = fetch('/' + name.removeprefix('frontend/'))
                        if hashlib.sha256(content).hexdigest() != expected:
                            raise RuntimeError('Public static artifact checksum mismatch')
                        break
                    except (urllib.error.URLError, RuntimeError):
                        if time.monotonic() >= deadline:
                            raise
                        time.sleep(1)
        body, headers = fetch('/api/auth/csrf')
        if 'application/json' not in headers.get('Content-Type', '') or not json.loads(body).get('token'):
            raise RuntimeError('Public API check failed')
        try:
            fetch('/api/auth/me')
            raise RuntimeError('Unauthenticated account access allowed')
        except urllib.error.HTTPError as error:
            if error.code != 401:
                raise
        record['status'] = 'success'
        print(f'Web and backend deployed: {stamp}; backend={backend_id}', flush=True)
    except Exception:
        shutil.copy2(backup, SITE)
        if previous is None:
            current.unlink()
        else:
            temporary = WEB / ('rollback-' + stamp)
            temporary.symlink_to(previous)
            os.replace(temporary, current)
        run('nginx', '-t')
        run('systemctl', 'reload', 'nginx')
        run('/usr/local/sbin/photo-calendar-deploy', 'rollback', backend_id)
        record['status'] = 'rolled-back'
        raise
    finally:
        record_path.write_text(json.dumps(record, indent=2) + '\n')


if __name__ == '__main__':
    main()
