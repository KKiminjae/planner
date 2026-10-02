#!/usr/bin/env python3
"""Download a known S3 backup and verify metadata hash and gzip integrity."""
import argparse
from contextlib import closing
import gzip
import hashlib
import os
from pathlib import Path
import re
import tempfile

import boto3
from botocore.config import Config


def download(key, expected_sha=None):
    if not re.fullmatch(r'backups/db-[0-9]{8}T[0-9]{6}-[0-9]+\.sql\.gz', key):
        raise ValueError('Provide an exact backup key under backups/')
    if expected_sha is not None and not re.fullmatch(r'[a-f0-9]{64}', expected_sha):
        raise ValueError('Invalid expected SHA-256')
    os.environ['AWS_SHARED_CREDENTIALS_FILE'] = '/home/ubuntu/photo-calendar/aws.credentials'
    client = boto3.Session(profile_name='photo-calendar', region_name='ap-northeast-2').client(
        's3', config=Config(connect_timeout=5, read_timeout=30,
                           retries={'mode': 'standard', 'total_max_attempts': 3}))
    directory = Path('/var/backups/photo-calendar')
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    destination = directory / Path(key).name
    response = client.get_object(Bucket='photo-calendar-mj-20261001-a7', Key=key)
    temporary = None
    with closing(response['Body']) as body:
        metadata_sha = response.get('Metadata', {}).get('sha256', '')
        if not re.fullmatch(r'[a-f0-9]{64}', metadata_sha):
            raise ValueError('Backup object has no valid SHA-256 metadata')
        if expected_sha is not None and expected_sha != metadata_sha:
            raise ValueError('Backup metadata does not match the supplied expected hash')
        descriptor, name = tempfile.mkstemp(prefix='.s3-restore-', dir=directory)
        temporary = Path(name)
        try:
            checksum = hashlib.sha256()
            with os.fdopen(descriptor, 'wb') as target:
                for block in body.iter_chunks(chunk_size=1024 * 1024):
                    checksum.update(block)
                    target.write(block)
                target.flush()
                os.fsync(target.fileno())
            if checksum.hexdigest() != metadata_sha:
                raise ValueError('Downloaded backup SHA-256 mismatch')
            with gzip.open(temporary, 'rb') as source:
                while source.read(1024 * 1024):
                    pass
            if destination.exists() or destination.is_symlink():
                if destination.is_symlink() or not destination.is_file():
                    raise ValueError('Unsafe existing backup destination')
                with destination.open('rb') as original:
                    if hashlib.file_digest(original, 'sha256').hexdigest() != metadata_sha:
                        raise ValueError('Different local backup already exists; refusing to overwrite')
            else:
                # Hard-link publication fails safely if another process creates this name.
                os.link(temporary, destination)
            sidecar = destination.with_name(destination.name + '.sha256')
            if sidecar.is_symlink():
                raise ValueError('Unsafe checksum file')
            sidecar.write_text(metadata_sha + '  ' + str(destination) + '\n')
            sidecar.chmod(0o600)
        finally:
            temporary.unlink(missing_ok=True)
    print('S3 download SHA-256 and gzip integrity verified:', destination.name)
    return destination


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('key')
    parser.add_argument('--sha256', help='Optional independent expected digest from a release manifest')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run with sudo on the server')
    os.umask(0o077)
    download(args.key, args.sha256)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # AWS error details and database contents are never printed.
        print('Backup download/verification failed:', type(error).__name__)
        raise SystemExit(1)
