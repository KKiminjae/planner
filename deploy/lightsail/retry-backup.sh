#!/usr/bin/env bash
set -euo pipefail
umask 077
backup_command=${BACKUP_COMMAND:-/usr/local/sbin/photo-calendar-backup}
state_dir=${BACKUP_STATE_DIR:-/var/lib/photo-calendar-backup}
retry_delay=${BACKUP_RETRY_DELAY:-60}
install -d -m 700 "$state_dir"
exec 8>"$state_dir/.retry.lock"
flock -n 8 || exit 0
write_state() {
  date -u +%Y-%m-%dT%H:%M:%SZ > "$state_dir/$1.partial"
  mv "$state_dir/$1.partial" "$state_dir/$1"
}
write_state last-attempt
for attempt in 1 2 3; do
  echo "DB backup attempt $attempt/3"
  if timeout --kill-after=30s 15m "$backup_command"; then
    write_state last-success
    echo 'DB backup succeeded (local dump and S3 content verified).'
    exit 0
  fi
  if [[ "$attempt" -lt 3 ]]; then
    echo "DB backup failed; retrying in ${retry_delay}s."
    sleep "$retry_delay"
  fi
done
write_state last-failure
echo 'DB backup failed after 3 attempts; local backups retained.' >&2
exit 1
