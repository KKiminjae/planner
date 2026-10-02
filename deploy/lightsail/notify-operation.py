#!/usr/bin/env python3
"""Send a fixed operational alert through SNS; never print credentials."""
import os
import sys
import boto3
from botocore.config import Config

topic = os.environ.get('ALERT_SNS_TOPIC_ARN', '')
if not topic:
    print('Email alert not configured: ALERT_SNS_TOPIC_ARN missing.', file=sys.stderr)
    raise SystemExit(1)
os.environ['AWS_SHARED_CREDENTIALS_FILE'] = '/home/ubuntu/photo-calendar/aws.credentials'
message = sys.argv[1] if len(sys.argv) > 1 else 'Backup failed after 3 attempts. Check photo-calendar-backup.service logs.'
try:
    boto3.Session(profile_name='photo-calendar', region_name='ap-northeast-2').client(
        'sns', config=Config(connect_timeout=5, read_timeout=10,
                             retries={'mode': 'standard', 'total_max_attempts': 3})).publish(
        TopicArn=topic, Subject='Photo Calendar operational alert', Message=message)
except Exception as error:
    print('Email alert publish failed:', type(error).__name__, file=sys.stderr)
    raise SystemExit(1)
print('Email alert published to SNS.')
