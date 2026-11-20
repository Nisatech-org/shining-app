# Shining server

The server watches Samad for next week's menus. It gives the app the status, and (from Phase 2) sends the alerts.

## Build and test

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew :server:test :server:installDist
```

## Commands

```powershell
.\server\build\install\shining-server\bin\shining-server.bat ping
.\server\build\install\shining-server\bin\shining-server.bat discover
.\server\build\install\shining-server\bin\shining-server.bat run
```

- **`ping`** checks that Samad can be reached, without logging in. `HTTP 401` is the expected answer.
- **`discover`** (Phase 0) asks for a student number and a password. The password isn't shown as you type. It then saves the cafeteria list and this week's and next week's menus to `discovery-output\`, which git ignores.
- **`run`** starts the watcher. It reads these environment variables:

| Variable | Default | |
|---|---|---|
| `SAMAD_USERNAME` | (required) | The Samad account the server reads menus with |
| `SAMAD_PASSWORD` | (required) | Its password. Keep it in a root-owned `0600` file, never in the repo |
| `SHINING_DATA_DIR` | `data` | Where `state.json` is kept |
| `SHINING_HOST` | `127.0.0.1` | Listen address (nginx sits in front) |
| `SHINING_PORT` | `8050` | Listen port |
| `SAMAD_BASE_URL` | Samad | Only for testing against something that isn't the real Samad |
| `SHINING_FCM_KEY_FILE` | (none) | The Firebase service-account key file. Without it, alerts only go to the log. Never commit it |
| `SHINING_LOG_LEVEL` | `INFO` | `DEBUG` also logs every Samad request |
| `SHINING_DEBUG_TOKEN` | (none) | 16+ characters; turns on `GET /v1/debug`. See [docs/DEBUGGING.md](../docs/DEBUGGING.md) |

**`push-test [message]`** sends one alert to the `admin` topic through Firebase (it needs `SHINING_FCM_KEY_FILE`). To receive it, a phone needs admin alerts on: long-press the version line in Settings.

## Live at shining-api.nisatech.org

It runs on a small Ubuntu server, set up by the scripts in `deploy/`:

- **DNS:** on Cloudflare. An A record `shining-api` → the server's address, with the **proxy off (DNS only)**.
- **HTTPS:** the site's own Let's Encrypt certificate, from certbot (webroot `/var/www/letsencrypt`), renewed by `certbot.timer`.
- **nginx:** port 80 only answers certbot and redirects to https; 443 adds `noindex`, rate-limits, and exposes **only** `/v1/status`, `/v1/debug` and `/health`, proxied to 127.0.0.1:**8060**.
- **`/srv/shining`:**
  - `releases/<commit>/`, the last 5 builds, with `current` → the live one;
  - `data/`: `state.json`, `logs/`, `archive/` (every version of each menu, see below), and state backups from before each deploy;
  - `secrets/`: `shining.env` and `firebase-admin.json`.
- **Its own system user, `shining`.** Only it and the deploy user can read `/srv/shining`.
- **Java 17** from Ubuntu (`openjdk-17-jre-headless`). **The server never builds anything.** `deploy.sh` builds and tests on the deploy machine, because Android's build tools can't be downloaded from Iran, then uploads the finished program.
- **Memory:** about 100 MB of RAM, capped at 300 MB. The server has 1 CPU and ~1 GB of RAM, shared.

Checked from the server on 2026-11-04, without a VPN: Samad (401), Firebase's send API (401) and Google's token service (400) are all reachable.

Every script reads the deploy user's SSH login from `SERVER`, e.g. `export SERVER=<user>@<server>` first.

```bash
# once, by the server admin (the deploy user needs no sudo), after the DNS record exists:
scp -r deploy <user>@<server>:shining-deploy
ssh -t <admin>@<server> 'sudo DEPLOY_USER=<user> bash ~<user>/shining-deploy/server-setup.sh'
# once (and whenever the password or the key changes): asks for the Samad password, hidden
bash deploy/set-secrets.sh path/to/firebase-admin.json
# once, and again whenever the server's admin alert asks (about twice a year): type Samad's captcha
bash deploy/signin.sh   # or the page https://shining-api.nisatech.org/v1/debug?signin (see "Signing in to Samad")
# every deploy (commit to main first; needs no root; rolls back by itself if /health fails):
bash deploy/deploy.sh
# day to day:
bash deploy/logs.sh                  # follow the log
bash deploy/logs.sh grep 'check failed'
bash deploy/debug.sh                 # state, last 100 checks and 30 pushes, next check and why
bash deploy/server.sh push-test      # an admin alert sent from the server itself
```

## Signing in to Samad (captcha)

Since early November 2026 Samad (`setad.dining.sharif.edu/oauth/token`) asks for a captcha on **every** password login: `Captcha params is invalid` / «پارامترهای کپچا نامعتبر است». Renewing a session with a refresh token needs none. A person has to solve the captcha; the server never tries to read it.

So the server signs itself in, with you typing the captcha, **on the sign-in page: https://shining-api.nisatech.org/v1/debug?signin**. Bookmark it on your phone.

1. When the server has no working session, it sends an admin alert («سرور شاینینگ باید دوباره وارد سماد بشه…») with the page's address, and again every 12 hours while it's still waiting. Before a session runs out, it warns you ahead of time (see below).
2. On the page, the server fetches a captcha from Samad itself. You type the code, and the server logs in with the account in `shining.env`, **from its own address**. A session started on another computer (the old `deploy/samad-login.py`) was refused the first time the server used it, on 2026-11-08.
3. A check runs within a minute. Its admin alert («…روشنه و به سماد وصله» or «…دوباره به سماد وصله») confirms it worked end to end.

**From a terminal instead** (nothing to copy, since the token is read on the server): `bash deploy/signin.sh`. On Windows, use Git Bash: `& "C:\Program Files\Git\bin\bash.exe" deploy/signin.sh`. In PowerShell, plain `bash` may start WSL instead. The captcha picture opens in your image viewer, and you type the code in the terminal.

The page asks once for the debug token (from `set-secrets.sh`; keep it in your password manager) and keeps it in that browser. Paste only the 48 letters and digits, without quotes. Every action is a `POST /v1/debug` with the token in a header; the page itself holds nothing secret. It lives on `/v1/debug` because nginx only lets that path, `/v1/status` and `/health` through, so nginx needs no change.

After that the server keeps the session going alone. It renews the access token when it runs out and saves each new refresh token to `/srv/shining/data/samad-session` (mode 660, group `shining`), so restarts keep it. Checks run at least hourly all week, so a session that stopped working is noticed within the hour, not on Monday morning.

**How long a session lasts.** Samad's access tokens last about **180 days** (`expires_in` 15551999, seen 2026-11-15). But Samad refuses the server's renewals: on 2026-11-15 a refresh token was refused 80 seconds after a sign-in from the server itself. So the access token is what keeps the server signed in. It's saved to `/srv/shining/data/samad-session-access` (with its end date, mode 660, group `shining`) next to the refresh token, and used again after every restart and deploy. Before 1.4.1 it was only in memory, so each restart needed a new captcha. The log's `token issued` lines show each token's lifetime, and a refused renewal logs Samad's reason.

The server shows the session's end as `samadSessionUntil` on the debug page, and warns you 7 days and again 1 day before. Sign in again then (page or `signin.sh`), and checks never stop. That's about twice a year.

**Give the server its own Samad account.** Another login on the same account might end the server's session. The server uses its own account since 2026-11-15; don't log in with it anywhere else. Change it with `deploy/set-secrets.sh` (it also makes a new debug token), then sign in again.

Without a working session it doesn't keep failing logins on Samad: a refused session isn't retried, and a password login without a captcha is only tried every 6 hours, in case Samad stops asking for one.

## How `run` works

1. **When it checks:** always, at least hourly. From Sunday 00:00 (Tehran time) until next week has started, every 5 minutes. Menus usually come out on Monday; Sunday catches an early one. Once a vendor has 3 or more weeks of history, it checks every 5 minutes only from 2 hours before that vendor's usual time until 4 hours after, and hourly otherwise. On Saturday, and once every vendor has its whole week (Saturday to Wednesday), it checks hourly. It never stops, so late changes are caught and the Samad session stays in use.
2. **What it compares:** each check reads the cafeteria list and next week's menu of every vendor's cafeteria. **A day counts once it has a food that can be reserved** (Samad's `reserveRuleViolated` is false, or missing). That gives "appeared" and "added days" events. The very first check after install only records what's there.
   - **A menu that's visible but can't be reserved yet** isn't announced. The admin gets one alert («… توی سماد هست ولی هنوز رزرو نمی‌شه»), and users get the real alert when it opens. So a menu seen early is never "used up" by the first check.
3. **Bundling: a 20-minute window** (since 1.6.0). When a vendor's first unannounced day opens, the server waits 20 minutes. Days that open in that window, of any vendor, go out in the same alert; days after it start a new window and get their own alert. Being first to reserve is the point of the app, so it doesn't wait longer, and day-by-day alerts show that it's first. If every vendor waiting already has its whole week (Saturday to Wednesday) open, there's nothing more to wait for, and the alert goes out at once. So on 2026-11-16, with سلف opening Saturday, then Sunday and Monday 15 minutes later, then Tuesday and Wednesday, that's two alerts instead of three. This applies day or night (no quiet hours since 2026-11-08). `/v1/status` lists only days already announced, so phones catching up from it don't announce held days one by one. (1.5.0 had waited for the whole week, or 2 quiet hours, for one alert per vendor: too slow.)
4. **Health:** after 3 failed checks in a row, the admin gets one alert, and another when checks recover. When the reason is that Samad needs a sign-in, the alert goes out at once, with the sign-in page (see "Signing in to Samad"). After every start, the first good check sends one admin alert («سرور شاینینگ روشنه و به سماد وصله…»), so a deploy is confirmed end to end, Firebase included.
5. **Archive:** every version of each cafeteria's menu (all fields Samad sends except the account's own reservations) is saved to `data/archive/<week>/<yyyyMMdd-HHmmss>-self<id>.json` when it changes: days, foods, prices, or whether it can be reserved. Not on every check, since vote counts change all the time. 12 weeks are kept. This is how we see afterwards exactly when a menu came out and when it opened.
6. **Only the server finds menus.** The phone reports of 1.3.0 (`POST /v1/status`) are gone since 1.4.0, and app 1.9.0 no longer checks the server's cafeterias itself, even when the server is down. Phones only check cafeterias the server's account can't see (dorms, the girls' self), with their owner's login. So alerts depend on the server staying signed in to Samad (see "Signing in to Samad").
7. **What it serves:** `GET /v1/status` returns next week's state per vendor, and is cached for 60 s. `GET /health` returns `ok`.

Alerts go out as Firebase data messages to the `menus` topic (and `admin` for health alerts). The app decides what to show. Without `SHINING_FCM_KEY_FILE`, they're only printed to the log (lines starting with `[push]`).

Java 17 doesn't trust Samad's root certificate (Certum Trusted Root CA), so the server bundles it. See `src/main/kotlin/app/shining/server/samad/SamadTls.kt`.
