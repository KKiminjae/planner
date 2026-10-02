#!/usr/bin/env bash
set -euo pipefail
echo 'Services:'
systemctl is-active photo-calendar nginx || true
echo 'Application memory and restarts:'
systemctl show photo-calendar.service -p MemoryCurrent -p MemoryPeak -p MemoryMax -p NRestarts
echo 'MySQL container resources:'
cd /home/ubuntu/photo-calendar
docker compose stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.MemPerc}} {{.CPUPerc}}'
echo 'Backup state (UTC):'
for marker in last-attempt last-success last-failure; do
  printf '%s: ' "$marker"
  if [[ -f "/var/lib/photo-calendar-backup/$marker" ]]; then
    cat "/var/lib/photo-calendar-backup/$marker"
  else
    echo 'not recorded'
  fi
done
echo 'Backup timer:'
systemctl list-timers photo-calendar-backup.timer --no-pager
echo 'Memory:'
free -h
echo 'Root disk:'
df -h /
