#!/usr/bin/env python3
"""Build/upload a JAR and invoke the server's guarded deployment command."""
import argparse
import hashlib
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = 'ubuntu@15.165.115.190'
SERVER_APP = '/home/ubuntu/photo-calendar'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, help='Use an already-built JAR instead of building')
    parser.add_argument('--key', type=Path, default=Path.home() / 'Downloads/LightsailDefaultKey-ap-northeast-2.pem')
    parser.add_argument('--reviewed-migrations', action='store_true',
                        help='Permit new migrations after reviewing recovery; disables automatic JAR rollback')
    args = parser.parse_args()
    if args.jar is None:
        subprocess.run(['./gradlew', 'bootJar'], cwd=ROOT, check=True)
    jar = (args.jar or ROOT / 'build/libs/photo_calendar-0.0.1-SNAPSHOT.jar').resolve(strict=True)
    with jar.open('rb') as source:
        checksum = hashlib.file_digest(source, 'sha256').hexdigest()
    ssh = ['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-i', str(args.key.expanduser()), DESTINATION]
    command = f'install -d -m 700 {SERVER_APP}/incoming && mktemp -d {SERVER_APP}/incoming/release-XXXXXXXX'
    upload_dir = subprocess.check_output(ssh + [command], text=True).strip()
    if not re.fullmatch(SERVER_APP + r'/incoming/release-[A-Za-z0-9]{8}', upload_dir):
        raise RuntimeError('Unexpected remote staging directory')
    remote_jar = upload_dir + '/candidate.jar'
    subprocess.run(['scp', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-i', str(args.key.expanduser()),
                    str(jar), DESTINATION + ':' + remote_jar], check=True)
    deploy = ['sudo', '/usr/local/sbin/photo-calendar-deploy', 'deploy', remote_jar, checksum]
    if args.reviewed_migrations:
        deploy.append('--reviewed-migrations')
    print('JAR uploaded. Verifying backup and deploying; login sessions will be reset.', flush=True)
    subprocess.run(ssh + [shlex.join(deploy)], check=True)


if __name__ == '__main__':
    main()
