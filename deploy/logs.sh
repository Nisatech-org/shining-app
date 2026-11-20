#!/usr/bin/env bash
# Follows the server's log (today's file). Ctrl+C to stop.
#   bash deploy/logs.sh          last 100 lines, then new ones as they come
#   bash deploy/logs.sh 500      last 500 lines
#   bash deploy/logs.sh grep 'check failed'    search every kept day (14)
set -euo pipefail
SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/logs.sh"}
LOGS=/srv/shining/data/logs

if [ "${1:-}" = grep ]; then
  shift
  ssh "$SERVER" "grep -h -- $(printf '%q' "$*") $LOGS/shining-*.log | tail -n 200"
else
  ssh -t "$SERVER" "tail -n ${1:-100} -F $LOGS/shining-\$(TZ=Asia/Tehran date +%F).log"
fi
