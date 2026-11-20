#!/usr/bin/env bash
# Signs the server in to Samad from this computer, with nothing to copy: the debug token is read on
# the server (like debug.sh), so it never leaves it. The server fetches Samad's captcha, the
# picture opens here, you type the code, and the server logs in from its own address with the
# account in shining.env. The same as the sign-in page (/v1/debug?signin), from a terminal.
#   bash deploy/signin.sh          (Git Bash on Windows: & "C:\Program Files\Git\bin\bash.exe" deploy/signin.sh)
set -euo pipefail
SERVER=${SERVER:?"set SERVER to the deploy user's SSH login, e.g. SERVER=<user>@<server> bash deploy/signin.sh"}

# One sign-in action on the server; prints its JSON reply. $1 holds only quote-free characters.
act() {
  ssh "$SERVER" "set -a; . /srv/shining/secrets/shining.env; set +a; printf 'X-Debug-Token: %s\n' \"\$SHINING_DEBUG_TOKEN\" | curl -s -m 60 -H @- -H 'Content-Type: application/json' -d '$1' http://127.0.0.1:8060/v1/debug"
}

field() { sed -n "s/.*\"$1\":\"\\{0,1\\}\\([^\",}]*\\).*/\\1/p" <<<"$2"; }

open_picture() {
  if command -v cygpath >/dev/null 2>&1; then
    cmd.exe //c start "" "$(cygpath -w "$1")"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$1" >/dev/null 2>&1 &
  else
    open "$1"
  fi
}

picture="${TMPDIR:-/tmp}/samad-captcha.png"
for attempt in 1 2 3; do
  echo "==> Asking the server for a captcha from Samad"
  reply=$(act '{"action":"captcha"}')
  nonce=$(field nonce "$reply")
  image=$(field image "$reply")
  if [ -z "$nonce" ] || [ -z "$image" ]; then
    echo "The server gave no captcha: $(field message "$reply")${reply:0:200}" >&2
    exit 1
  fi
  [ "$(field needed "$reply")" = true ] || echo "    (the server is already signed in; this signs it in again)"
  printf '%s' "$image" | base64 -d >"$picture"
  open_picture "$picture"
  read -r -p "Type the code in the picture: " code
  # Persian digits become English ones; anything that isn't a letter or digit is dropped.
  code=$(printf '%s' "$code" | sed 's/۰/0/g; s/۱/1/g; s/۲/2/g; s/۳/3/g; s/۴/4/g; s/۵/5/g; s/۶/6/g; s/۷/7/g; s/۸/8/g; s/۹/9/g' | tr -cd '0-9A-Za-z')
  rm -f "$picture"
  reply=$(act "{\"action\":\"signin\",\"nonce\":\"$nonce\",\"text\":\"$code\"}")
  echo "    $(field message "$reply")"
  if [ "$(field ok "$reply")" = true ]; then
    echo "Done. Within a minute the server checks Samad and sends you an admin alert."
    exit 0
  fi
done
echo "Three tries didn't work. Run this again for a new picture." >&2
exit 1
