# Debugging Shining

Where to look when something goes wrong in the app (menu alerts, Home, login, reserving), and how to run the tests. The server has its own debugging notes in its private repo.

## Where to look first

| Symptom | Look at |
|---|---|
| No alert arrived on a phone | **The phone's debug log:** did a `push received` line come in? If yes, the line after it says why nothing was shown (already announced, vendor off, permission off). If not, the server didn't send it (that's on the server's side) |
| Alerts are late | **The phone's `push received … delay=…s priority=…` lines** (Android may have lowered the priority); a big delay before the push left is on the server's side |
| The Home card is missing | **The phone's debug screen › "Saved server status":** "never reached the server" means `/v1/status` isn't reachable (see `status request failed` lines) |
| The same alert came twice | **The phone's debug screen › "Announced"**, and the `in batch […] new […]` lines |
| A reminder came (or didn't) | **The phone's log:** a `reminder` line shows `menusUp`, `reservedDays` and the decision |
| Login problems | **The phone's log:** `token check …`, `saved access token …` and `token request …` lines, and `http` lines with 401. On start, and on switching accounts, the app keeps the saved access token while Samad accepts it (they last about 180 days). Only a 401 renews it, and Samad refuses most renewals and wants a captcha for a password login, so after `saved access token was rejected` the login screen usually opens (since 1.13.1; before, every start renewed and signed out). That screen starts with the saved password, so only the captcha is left (since 1.14.0) |
| Home says «به سماد وصل نشدیم» with an old saved copy | **The phone couldn't reach Samad** (no internet, a VPN, Samad down): look for `http` lines that time out or fail on `rest/reserves`. Since 1.14.1 a session Samad ended (`rest/reserves` 401, renewal refused) says «سماد نشستت رو بست و باید دوباره وارد شی» with a «ورود دوباره» button instead; before, it was shown as a connection problem, and the app kept running on the saved copy until it was restarted |
| "Full" (ظرفیت تکمیل) at سلف مرکزی | **Samad's own error:** سلف مرکزی holds 7000 a day and can't fill up, so since 1.14.0 the app adds «سلف مرکزی پر نمی‌شه؛ چند لحظه دیگه دوباره رزرو کن» to Samad's message (`order/domain/model/ReserveError.kt`). کاله and the others really fill up, and their message is shown as it is |
| سلف's alert came late, or in one piece | **On purpose since server 1.8.0 / app 1.14.0:** سلف waits for its whole week (Saturday to Wednesday), or 1 hour without a new day. |

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

## Tests

```bash
./gradlew testDebugUnitTest
```

CI runs them on every push, and uploads the reports when something fails.

| Area | What's covered |
|---|---|
| App | Alert texts, no-duplicates, which vendors are shown and why, the Reserve day, the Home card texts, the reminder decision, the own-cafeteria check, the status cache, the log file and redaction |

What tests don't cover: things only a real phone does (Firebase delivery, battery savers delaying the background check), and the real Samad. For those, use the debug screen and the logs above.
