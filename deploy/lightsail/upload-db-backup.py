#!/usr/bin/env python3
"""Upload a backup and validate its bytes without logging credentials or data."""
import hashlib
from contextlib import closing
import os
from pathlib import Path
import sys

import boto3

os.environ['AWS_SHARED_CREDENTIALS_FILE'] = '/home/ubuntu/photo-calendar/aws.credentials'
backup = Path(sys.argv[1]).resolve()
if backup.parent != Path('/var/backups/photo-calendar') or not backup.name.endswith('.sql.gz'):
    raise SystemExit('Unexpected backup location')
client = boto3.Session(profile_name='photo-calendar', region_name='ap-northeast-2').client('s3')
bucket = 'photo-calendar-mj-20261001-a7'
key = 'backups/' + backup.name
digest = hashlib.sha256(backup.read_bytes()).hexdigest()
try:
    client.upload_file(str(backup), bucket, key,
                       ExtraArgs={'ServerSideEncryption': 'AES256', 'Metadata': {'sha256': digest}})
    response = client.get_object(Bucket=bucket, Key=key)
    downloaded_hash = hashlib.sha256()
    with closing(response['Body']) as body:
        for block in body.iter_chunks(chunk_size=1024 * 1024):
            downloaded_hash.update(block)
    if downloaded_hash.hexdigest() != digest:
        raise SystemExit('S3 backup content verification failed')
except Exception as error:
    # Exception strings can contain request details; report the class only.
    print('S3 backup failed:', type(error).__name__, file=sys.stderr)
    raise SystemExit(1)
print('S3 backup uploaded and SHA-256 content verified:', backup.name)
