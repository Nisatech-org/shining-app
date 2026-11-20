#!/usr/bin/env bash
# Runs on the server as the deploy user, called by deploy/deploy.sh with the release id after the
# build is unpacked into /srv/shining/releases/<id>/.
set -euo pipefail
umask 007

ROOT=/srv/shining
release=${1:?release id}
new=$ROOT/releases/$release/shining-server

[ -x "$new/bin/shining-server" ] || { echo "Release $release is incomplete." >&2; exit 1; }
[ -f "$ROOT/secrets/shining.env" ] || { echo "No $ROOT/secrets/shining.env: run deploy/set-secrets.sh first." >&2; exit 1; }

echo "==> Checking Samad and Firebase are reachable from this server (no login)"
"$new/bin/shining-server" ping || echo "    Samad didn't answer; the service will keep retrying and alert after 3 failures."
fcm=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST https://fcm.googleapis.com/v1/projects/x/messages:send || true)
echo "    Firebase send API answered HTTP $fcm (401 means reachable)"

if [ -f "$ROOT/data/state.json" ]; then
  mkdir -p "$ROOT/data/backups"
  cp "$ROOT/data/state.json" "$ROOT/data/backups/state-before-$release.json"
  ls -1t "$ROOT"/data/backups/state-before-*.json | tail -n +11 | xargs -r rm --
fi

echo "==> Switching to release $release"
previous=$(readlink "$ROOT/current" 2>/dev/null || true)
ln -sfn "$new" "$ROOT/current"
sudo /usr/bin/systemctl restart shining

ok=""
for _ in $(seq 1 20); do
  sleep 1
  if [ "$(curl -s --max-time 2 http://127.0.0.1:8060/health || true)" = ok ]; then ok=1; break; fi
done
if [ -z "$ok" ]; then
  echo "Health check failed: /health didn't answer 'ok' within 20 s." >&2
  if [ -n "$previous" ]; then
    echo "Rolling back to $previous" >&2
    ln -sfn "$previous" "$ROOT/current"
    sudo /usr/bin/systemctl restart shining
  fi
  echo "--- last log lines:" >&2
  tail -n 40 "$ROOT"/data/logs/shining-*.log 2>/dev/null | tail -n 40 >&2 || true
  exit 1
fi
sudo /usr/bin/systemctl is-active shining
echo "    /health answers ok"

# Keep the five newest releases.
ls -1dt "$ROOT"/releases/*/ | tail -n +6 | xargs -r rm -rf --

echo "==> Recent log"
tail -n 15 "$(ls -1t "$ROOT"/data/logs/shining-*.log | head -1)" | cut -c1-220
