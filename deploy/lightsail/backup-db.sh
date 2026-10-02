#!/usr/bin/env bash
set -euo pipefail
umask 077
cd /home/ubuntu/photo-calendar
backup_dir=/var/backups/photo-calendar
install -d -m 700 "$backup_dir"
exec 9>"$backup_dir/.backup.lock"
flock -n 9 || exit 75
backup_file="$backup_dir/db-$(date -u +%Y%m%dT%H%M%S)-$RANDOM.sql.gz"
temporary_file="$backup_file.partial"
trap 'rm -f -- "$temporary_file"' EXIT
docker compose exec -T mysql sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump -uroot --single-transaction --quick --no-tablespaces \
    --set-gtid-purged=OFF --routines --events --triggers "$MYSQL_DATABASE"
' | gzip > "$temporary_file"
gzip -t "$temporary_file"
mv "$temporary_file" "$backup_file"
chmod 600 "$backup_file"
sha256sum "$backup_file" > "$backup_file.sha256"
/usr/bin/python3 /usr/local/lib/photo-calendar/upload-db-backup.py "$backup_file"
# Retain at least the newly verified backup before pruning old files.
find "$backup_dir" -maxdepth 1 -type f \( -name 'db-*.sql.gz' -o -name 'db-*.sql.gz.sha256' \) -mtime +14 -delete
echo "DB backup created: $(basename "$backup_file")"
