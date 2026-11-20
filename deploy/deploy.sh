#!/usr/bin/env bash
# Deploys the committed main branch's server to the Nisatech server. From the repo root (Git Bash):
#   bash deploy/deploy.sh
# The server is built and tested here (the server can't download Android build tools from Iran),
# then the finished program is uploaded; the server only runs it with its Java 17. Needs the
# one-time root setup (deploy/server-setup.sh) and the secrets (deploy/set-secrets.sh) first.
set -euo pipefail

SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/deploy.sh"}
ROOT=/srv/shining

cd "$(git rev-parse --show-toplevel)"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Uncommitted changes; commit them first so the server runs exactly what is in git:" >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || { echo "Deploy from main." >&2; exit 1; }
release=$(git rev-parse --short=10 HEAD)

if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Java/jdk-17" ]; then
  export JAVA_HOME="C:/Program Files/Java/jdk-17"
fi

echo "==> Testing and building the server ($release)"
./gradlew --no-daemon -q :server:test :server:installDist

tarball=$(mktemp -d)/shining-server-$release.tar.gz
tar -czf "$tarball" -C server/build/install shining-server

echo "==> Uploading release $release to $SERVER"
ssh "$SERVER" "test -d $ROOT/releases" || { echo "Run deploy/server-setup.sh on the server first." >&2; exit 1; }
scp -q "$tarball" "$SERVER:$ROOT/releases/"
ssh "$SERVER" "umask 007; mkdir -p $ROOT/releases/$release && tar -xzf $ROOT/releases/$(basename "$tarball") -C $ROOT/releases/$release && rm $ROOT/releases/$(basename "$tarball")"
scp -q deploy/remote-deploy.sh "$SERVER:$ROOT/releases/$release/remote-deploy.sh"

echo "==> Server side"
ssh "$SERVER" "bash $ROOT/releases/$release/remote-deploy.sh $release"
