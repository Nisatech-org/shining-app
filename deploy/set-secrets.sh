#!/usr/bin/env bash
# Writes the server's secrets to /srv/shining/secrets/: the Samad account it reads menus with,
# the Firebase service-account key, and a fresh token for /v1/debug. Run it yourself: it asks
# for the password without showing it, and nothing is saved on this computer.
#   SERVER=<user>@<server> bash deploy/set-secrets.sh path/to/firebase-admin.json
# (or set KEY to the key's path instead of passing it). Run again to change the password or the
# key, then restart (bash deploy/deploy.sh or ssh <user>@<server> sudo systemctl restart shining).
set -euo pipefail

SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/set-secrets.sh"}
ROOT=/srv/shining
KEY=${1:-${KEY:-}}
[ -n "$KEY" ] || { echo "Pass the Firebase service-account key's path as the first argument, or set KEY." >&2; exit 1; }

[ -f "$KEY" ] || { echo "No Firebase key at $KEY (pass its path as the first argument, or set KEY)." >&2; exit 1; }
grep -q '"type": *"service_account"' "$KEY" || { echo "$KEY isn't a Firebase service-account key." >&2; exit 1; }

read -r -p "Samad student number for the server's account: " user
read -r -s -p "That account's Samad password (hidden): " pass
echo
[ -n "$user" ] && [ -n "$pass" ] || { echo "Both are needed." >&2; exit 1; }
# systemd reads the file with single-quoted values; a quote inside would end it early.
case "$pass$user" in *"'"*) echo "A ' in the password can't be stored safely; change the password first." >&2; exit 1 ;; esac

token=$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')

echo "==> Uploading the Firebase key"
scp -q "$KEY" "$SERVER:$ROOT/secrets/firebase-admin.json.tmp"
ssh "$SERVER" "chmod 640 $ROOT/secrets/firebase-admin.json.tmp && mv $ROOT/secrets/firebase-admin.json.tmp $ROOT/secrets/firebase-admin.json"

echo "==> Writing $ROOT/secrets/shining.env (readable only by you and the service)"
# Sent over SSH's stdin, never on a command line where other users could see it.
printf "SAMAD_USERNAME='%s'\nSAMAD_PASSWORD='%s'\nSHINING_FCM_KEY_FILE='%s'\nSHINING_DEBUG_TOKEN='%s'\n" \
  "$user" "$pass" "$ROOT/secrets/firebase-admin.json" "$token" |
  ssh "$SERVER" "umask 077; cat > $ROOT/secrets/shining.env.tmp && chmod 640 $ROOT/secrets/shining.env.tmp && mv $ROOT/secrets/shining.env.tmp $ROOT/secrets/shining.env"
unset pass

echo
echo "Done. The debug token is kept on the server; bash deploy/debug.sh uses it from there."
echo "If you want it for a browser (https://shining-api.nisatech.org/v1/debug?token=...), save it in your password manager now:"
echo "  $token"
