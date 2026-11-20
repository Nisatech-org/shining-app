#!/usr/bin/env bash
# Runs a shining-server command on the server with its secrets, e.g.
#   bash deploy/server.sh ping              can the server reach Samad?
#   bash deploy/server.sh push-test         an admin alert, from the server itself
#   bash deploy/server.sh push-test menus   a sample menu alert to admin phones only
set -euo pipefail
SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/server.sh"}
[ $# -gt 0 ] || { echo "Usage: bash deploy/server.sh <command> [args]" >&2; exit 1; }

args=$(printf '%q ' "$@")
ssh -t "$SERVER" "set -a; . /srv/shining/secrets/shining.env; set +a; SHINING_DATA_DIR=/tmp/shining-cli /srv/shining/current/bin/shining-server $args"
