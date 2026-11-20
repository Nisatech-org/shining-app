#!/usr/bin/env bash
# One-time root setup for the Shining menu server (Ubuntu 24.04, nginx already installed). The
# server admin runs it, with DEPLOY_USER set to the user that deploys (it needs no sudo itself):
#   scp -r deploy <user>@<server>:shining-deploy
#   ssh -t <admin>@<server> 'sudo DEPLOY_USER=<user> bash ~<user>/shining-deploy/server-setup.sh'
# Safe to re-run (do, after changing anything under deploy/nginx or deploy/systemd, or once the
# DNS record exists if the certificate step failed).
set -euo pipefail

DEPLOY_USER=${DEPLOY_USER:?"set DEPLOY_USER to the user that deploys, e.g. sudo DEPLOY_USER=<user> bash server-setup.sh"}
DOMAIN=${DOMAIN:-shining-api.nisatech.org}
ROOT=/srv/shining
HERE=$(cd "$(dirname "$0")" && pwd)

[ "$(id -u)" -eq 0 ] || { echo "Run with sudo."; exit 1; }

echo "==> Java 17 (the server is built on the deploy machine; only a runtime is needed here)"
if ! java -version 2>&1 | grep -q 'version "17'; then
  apt-get update -q
  apt-get install -y -q --no-install-recommends openjdk-17-jre-headless
fi
java -version 2>&1 | head -1

echo "==> Service user 'shining' and $ROOT"
id shining &>/dev/null || useradd --system --no-create-home --home-dir "$ROOT" --shell /usr/sbin/nologin shining
usermod -aG shining "$DEPLOY_USER"
# setgid: everything created inside stays in group shining, so the deploy user and the service
# share it. No access for other users: secrets/ holds the Samad password and the Firebase key.
install -d -o shining -g shining -m 2770 "$ROOT"
install -d -o shining -g shining -m 2770 "$ROOT/data"
install -d -o shining -g shining -m 2770 "$ROOT/releases"
install -d -o shining -g shining -m 2770 "$ROOT/secrets"

echo "==> systemd unit"
install -m 644 "$HERE/systemd/shining.service" /etc/systemd/system/shining.service
systemctl daemon-reload
systemctl enable shining  # started by the first deploy, once the code and secrets are in place

echo "==> HTTPS for $DOMAIN (Let's Encrypt)"
# Cloudflare's proxy is off for nisatech.org (DNS only), so phones come straight here and the site
# answers HTTPS itself. It includes tls-$DOMAIN.conf for its HTTPS lines; until certbot has a
# certificate, a short-lived self-signed one stands in so nginx takes the site and its port-80
# block can answer certbot's challenge.
LIVE=/etc/letsencrypt/live/$DOMAIN
TEMP=/etc/nginx/ssl/$DOMAIN-temp
write_tls() {
  local cert=$TEMP.pem key=$TEMP.key extra=()
  if [ -f "$LIVE/fullchain.pem" ]; then
    cert=$LIVE/fullchain.pem key=$LIVE/privkey.pem
    extra=('include /etc/letsencrypt/options-ssl-nginx.conf;' 'ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;')
  elif [ ! -f "$TEMP.pem" ]; then
    install -d -m 700 /etc/nginx/ssl
    openssl req -x509 -nodes -newkey rsa:2048 -days 7 -subj "/CN=$DOMAIN" \
      -keyout "$TEMP.key" -out "$TEMP.pem" 2>/dev/null
  fi
  printf '%s\n' 'listen 443 ssl;' 'listen [::]:443 ssl;' "ssl_certificate $cert;" "ssl_certificate_key $key;" \
    "${extra[@]}" > "/etc/nginx/snippets/tls-$DOMAIN.conf"
}
install -d /var/www/letsencrypt /etc/nginx/snippets
write_tls

echo "==> nginx site for $DOMAIN"
sed "s/__DOMAIN__/$DOMAIN/g" "$HERE/nginx/shining.conf" > /etc/nginx/sites-available/shining
ln -sf /etc/nginx/sites-available/shining /etc/nginx/sites-enabled/shining
# A broken site would also block reloads for the other sites: take it out again.
if ! nginx -t; then
  rm -f /etc/nginx/sites-enabled/shining
  echo "nginx rejected the shining site; it was not enabled." >&2
  exit 1
fi
systemctl reload nginx

if [ ! -f "$LIVE/fullchain.pem" ]; then
  echo "==> Let's Encrypt certificate for $DOMAIN"
  # Checked over plain HTTP on the site's port-80 block. Webroot, so certbot never edits the site
  # file; the hook puts each renewal (certbot.timer) in use.
  if certbot certonly --webroot -w /var/www/letsencrypt --cert-name "$DOMAIN" -d "$DOMAIN" \
      --non-interactive --agree-tos --register-unsafely-without-email --deploy-hook 'systemctl reload nginx'; then
    write_tls
    nginx -t
    systemctl reload nginx
    rm -f "$TEMP.pem" "$TEMP.key"
  else
    echo "   WARNING: no certificate yet (does $DOMAIN resolve to this server?). HTTPS shows a self-signed" >&2
    echo "   one until then, so the app's Home card stays hidden; push alerts work anyway." >&2
    echo "   Re-run this script once the DNS record exists." >&2
  fi
fi

echo "==> Let $DEPLOY_USER restart the service without a password (and nothing else)"
# Deliberately not journalctl or systemctl status: both open a pager, and a pager run with sudo
# can start a root shell. The server writes its own log files to $ROOT/data/logs instead, which
# the deploy user can read through the shining group.
SUDOERS=/etc/sudoers.d/shining-deploy
printf '%s ALL=(root) NOPASSWD: /usr/bin/systemctl restart shining, /usr/bin/systemctl is-active shining\n' "$DEPLOY_USER" > "$SUDOERS.tmp"
visudo -cf "$SUDOERS.tmp"
install -m 440 "$SUDOERS.tmp" "$SUDOERS"
rm -f "$SUDOERS.tmp"

echo
echo "Done. $DEPLOY_USER must open a new SSH session to pick up the shining group."
echo "Then from the repo: bash deploy/set-secrets.sh   (once), then: bash deploy/deploy.sh"
