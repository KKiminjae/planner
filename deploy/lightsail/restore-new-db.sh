#!/usr/bin/env bash
# Restore into a newly created database only; never overwrite an existing schema.
set -euo pipefail
umask 077
cd /home/ubuntu/photo-calendar
backup_file=${1:?Provide an absolute verified SQL gzip backup path}
restore_db=${2:?Provide a new database name starting with recovery_}
[[ "$backup_file" == /var/backups/photo-calendar/db-*.sql.gz && ! -L "$backup_file" ]] || exit 1
[[ "$restore_db" =~ ^recovery_[a-zA-Z0-9_]{1,48}$ ]] || exit 1
gzip -t "$backup_file"
sha256sum --check --status "$backup_file.sha256"
exec 9>/var/backups/photo-calendar/.backup.lock
flock -n 9 || exit 75
mysql_command() {
  docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$@"' sh "$@"
}
# CREATE without IF NOT EXISTS refuses to reuse or overwrite an existing database.
mysql_command -e "CREATE DATABASE \`$restore_db\`;"
if ! gzip -dc "$backup_file" | mysql_command "$restore_db"; then
  echo "Restore failed; isolated database $restore_db is preserved for inspection." >&2
  exit 1
fi
mysql_command "$restore_db" -e 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) AS restored_table_count FROM information_schema.tables WHERE table_schema=DATABASE();'
echo "Restored into isolated database: $restore_db"
echo 'Production database and application settings have not been changed.'
