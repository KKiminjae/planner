#!/usr/bin/env bash
# Run on Linux with a temporary directory; never touches production backups.
set -euo pipefail
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
cat > "$work_dir/fake-backup" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [[ -f "$TEST_COUNT_FILE" ]]; then read -r count < "$TEST_COUNT_FILE"; fi
count=$((count + 1))
echo "$count" > "$TEST_COUNT_FILE"
[[ "$count" -gt "$TEST_FAILURES" ]]
SH
chmod 700 "$work_dir/fake-backup"
export BACKUP_COMMAND="$work_dir/fake-backup" BACKUP_RETRY_DELAY=0
export TEST_COUNT_FILE="$work_dir/count" TEST_FAILURES=2
export BACKUP_STATE_DIR="$work_dir/success"
bash "$(dirname "$0")/retry-backup.sh"
[[ "$(cat "$TEST_COUNT_FILE")" == 3 ]]
[[ -f "$BACKUP_STATE_DIR/last-success" && ! -f "$BACKUP_STATE_DIR/last-failure" ]]
export TEST_COUNT_FILE="$work_dir/failed-count" TEST_FAILURES=3
export BACKUP_STATE_DIR="$work_dir/failure"
if bash "$(dirname "$0")/retry-backup.sh"; then
  echo 'Expected final failure' >&2
  exit 1
fi
[[ "$(cat "$TEST_COUNT_FILE")" == 3 ]]
[[ -f "$BACKUP_STATE_DIR/last-failure" && ! -f "$BACKUP_STATE_DIR/last-success" ]]
echo 'Retry recovery and final failure state checks passed.'
