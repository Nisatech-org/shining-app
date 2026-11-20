# Debugging Shining

Where to look when something goes wrong with menu alerts, and how to run the tests.

## Where to look first

| Symptom | Look at |
|---|---|
| No alert arrived on a phone | **The phone's debug log:** did a `push received` line come in? If yes, the line after it says why nothing was shown (already announced, vendor off, permission off). If not, check **the server's log** for `sent` |
| The server never sent anything | **The server's log:** `check done` lines (are the days changing? `foodDays` counts all days with food, `days` only the ones that can be reserved, `closed` names vendors whose menu is visible but not open yet), `vendor appeared`, `sending menu bundle`, `FCM rejected` |
| When exactly did a menu come out, or open? | **The server's archive:** `data/archive/<week>/`, one file per menu change, named by its Tehran time, with every food, price and `reserveRuleViolated` flag. `bash deploy/logs.sh grep 'menu changed'` lists them |
| Alerts are late | **The server's `next check planned … why=…` lines**, and the phone's `push received … delay=…s priority=…` (Android may have lowered the priority) |
| The Home card is missing | **The phone's debug screen › "Saved server status":** "never reached the server" means `/v1/status` isn't reachable (see `status request failed` lines) |
| The same alert came twice | **The phone's debug screen › "Announced"**, and the `in batch […] new […]` lines |
| A reminder came (or didn't) | **The phone's log:** a `reminder` line shows `menusUp`, `reservedDays` and the decision |
| Login problems | **The phone's log:** `token request …` lines and `http` lines with 401 |
| The server can't reach Samad (`SignInNeededException`) | **Samad wants a captcha for password logins**, and the server has no working session. Open the sign-in page, https://shining-api.nisatech.org/v1/debug?signin (the admin alert links to it), and type the code. The log's `waiting for someone to sign in to Samad` and `the saved Samad session was refused` lines say when it started. `samadSignInNeeded` on the debug page says whether it's still waiting |

## The phone: debug screen and log

- **Open it:** Settings › **tap the version line 5 times** quickly. A long press on the same line toggles admin alerts.
- **It shows:**
  - the app version and device;
  - which server the app talks to;
  - whether Firebase is configured;
  - the notification permission;
  - admin alerts;
  - vendors turned off;
  - your dorm cafeterias that were found;
  - what was announced this week;
  - the last server status;
  - the background check's state and next run.
- **Buttons:**
  - **Copy log:** copies the report and the newest part of the log, ready to paste;
  - **Share log:** sends the report and the log as text, e.g. to Telegram;
  - **Run menu check now:** works on any day;
  - **Run reminder check now**;
  - **Refresh**;
  - **Clear log**.
- **The log** is in the app's private storage (`files/logs/shining.log`). It's capped at about 512 KB: once it grows past that, the older half is dropped.
  - **What's logged:** every push received, every alert decision with its reason, every background check, every Samad request (method, path, status, time), every server-status request, and crashes with their stack trace.
  - **What's never logged:** tokens and passwords. They're blanked before anything is written. The account number is shown as `•••786`.
- **Logcat:** every line also goes to Logcat with the tag prefix `Shining/`:

```bash
adb logcat -s "Shining/push" "Shining/alerts" "Shining/fallback" "Shining/reminder" "Shining/status" "Shining/http" "Shining/crash"
```

## The server: log, debug page, settings

- **The log:**
  - **Where it goes:** to standard output (on the server, `journalctl -u shining -f`), and to daily files `data/logs/shining-YYYY-MM-DD.log`, kept for 14 days.
  - **Line format:** `time level component message | key=value …`
  - **What's logged:** a summary of every check (`week`, `ms`, reservable days, days with food and foods per vendor, closed vendors, events), every vendor event, every menu change saved to the archive, the next check and **why** it's then, every push with its Firebase message id, login and refresh, every Samad request at DEBUG level, and errors with their full stack trace.
  - **What's never logged:** tokens, passwords and keys. Automatic blanking runs on every line, and tests check it.
- **More detail:** `SHINING_LOG_LEVEL=DEBUG` adds every Samad request (path, status, time and size). The default is `INFO`.
- **The debug page:** set `SHINING_DEBUG_TOKEN` (16+ characters) to turn it on.
  - **What it shows:** the whole saved state, i.e. this week's snapshot, unsent events, release history, **the last 100 checks and the last 30 pushes**, plus the next check time and reason.
  - **Without the token** (or with a wrong one) it answers 404.
  - **It's never cached.** It sends `Cache-Control: no-store`.

```bash
curl -H "X-Debug-Token: $SHINING_DEBUG_TOKEN" https://shining-api.nisatech.org/v1/debug
```

- **Test pushes** (they need `SHINING_FCM_KEY_FILE`). Both go to phones with admin alerts on only:
  - `shining-server push-test [message]` sends an admin alert;
  - `shining-server push-test menus` sends a sample menu alert for a made-up vendor.

## Tests

```bash
./gradlew :server:test
```

```bash
./gradlew testDebugUnitTest
```

CI runs both on every push, and uploads the reports when something fails.

| Area | What's covered |
|---|---|
| Server, network | The real Samad client against **`FakeSamad`**, a small HTTP server in the tests: login, wrong password, expired session and refresh, server errors, `ERROR` replies |
| Server, end to end | `EndToEndTest`: fake Samad → real watcher → real Firebase sender → fake Google, over a simulated week (baseline, a release, one push, an outage with admin alerts, recovery). It also restarts the server mid-week, and **asserts no password or token ever appears in the log** |
| Server, logic | Diff, bundling and quiet hours, schedule and learning, vendor rules, status and debug pages, settings, the logger's format, files and redaction |
| App | Alert texts, no-duplicates, which vendors are shown and why, the Reserve day, the Home card texts, the reminder decision, the own-cafeteria check, the status cache, the log file and redaction |

What tests don't cover: things only a real phone does (Firebase delivery, battery savers delaying the background check), and the real Samad. For those, use the debug screen and the logs above.
