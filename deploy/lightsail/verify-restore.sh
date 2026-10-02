#!/usr/bin/env bash
set -euo pipefail
cd /home/ubuntu/photo-calendar
backup_file=${1:?Provide the absolute path of a backup created by backup-db.sh}
[[ "$backup_file" == /var/backups/photo-calendar/db-*.sql.gz ]] || exit 1
gzip -t "$backup_file"
sha256sum --check "$backup_file.sha256" >/dev/null
restore_db="backup_verify_$(date -u +%Y%m%d%H%M%S)_$RANDOM"
mysql_command() {
  docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$@"' sh "$@"
}
mysql_command -e "CREATE DATABASE \`$restore_db\`;"
trap 'mysql_command -e "DROP DATABASE IF EXISTS \`$restore_db\`;"' EXIT
gzip -dc "$backup_file" | mysql_command "$restore_db"
fingerprint() {
  docker compose exec -T mysql sh -c '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    target=${1:-$MYSQL_DATABASE}
    exec mysqldump -uroot --no-tablespaces --set-gtid-purged=OFF --single-transaction \
      --skip-comments --compact --order-by-primary "$target"
  ' sh "$1" | sha256sum | cut -d ' ' -f 1
}
original_hash=$(fingerprint '')
restore_hash=$(fingerprint "$restore_db")
if [[ "$original_hash" != "$restore_hash" ]]; then
  echo 'Restore completed but comparison differs; check whether production data changed during verification.' >&2
  exit 1
fi
echo 'Restore verified: schema and data match the current database.'
echo 'Temporary verification database will be removed.'
