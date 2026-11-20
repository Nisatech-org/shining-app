#!/usr/bin/env bash
# Shows the server's debug page (state, last 100 checks and 30 pushes, next check and why).
# The token is read on the server, so it never leaves it.
#   bash deploy/debug.sh
set -euo pipefail
SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/debug.sh"}

# printf is a shell builtin and curl reads the header from stdin, so the token never shows up in
# the server's process list.
ssh "$SERVER" 'set -a; . /srv/shining/secrets/shining.env; set +a; printf "X-Debug-Token: %s\n" "$SHINING_DEBUG_TOKEN" | curl -s -H @- http://127.0.0.1:8060/v1/debug'
echo
