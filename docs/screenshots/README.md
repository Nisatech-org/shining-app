# Screenshots

Every screen of the app with sample data, in light (`name.png`) and dark (`name-dark.png`), at 540×1200. `overview.png` is the strip at the top of the main readme, and `market-flow.mp4` is one market trade from both phones, recorded live with real taps, typing and the keyboard: the buyer (left) buys a meal, pays card to card in a made-up bank app, pastes its receipt as the note and gets the forget code; the seller (right) lists the meal, types its price, reads the receipt and sends the code. Every name and number in it is invented.

| File | Screen |
| --- | --- |
| `splash` | Splash |
| `login` | Login (with a made-up captcha) |
| `home` | Home: sell card, next week's menus, reservations |
| `account-switcher` | Account switcher (long-press the Profile tab) |
| `reserve` | Reserve, with the daily-sale button in its header |
| `daily-sale` | Daily sale, opened from Reserve |
| `market` | Market: listings and a trade in progress |
| `market-join` | Market before joining |
| `market-info` | "How does the market work?" sheet |
| `market-meal` | One meal's board (from a notification or a trade) |
| `trade` | A trade, buyer's side, waiting for payment |
| `profile` | Profile: credit, accounts, transactions |
| `settings` | Settings |

None of it comes from a real account: the names, codes and card numbers are made up in the tests.

## Making them again

After a change to how a screen looks, run:

```bash
bash docs/screenshots/refresh.sh               # screenshots and video, about 3 minutes
SKIP_VIDEO=1 bash docs/screenshots/refresh.sh  # just the screenshots
SKIP_SHOTS=1 bash docs/screenshots/refresh.sh  # just the video
```

It needs an Android emulator (it starts the AVD named in `$AVD`, `foodie_api34` by default, if none is running), Python 3 with Pillow (with raqm, for the Persian labels) and ffmpeg. The emulator must render on the GPU (`-gpu host`): with software rendering it lags seconds behind the video's script. The script leaves the results in the working tree to look at and commit, and only rewrites a picture that really looks different. A few always change a little because the sample dates are relative to today ("فردا", weekday names); leave those unless the screen itself changed.

What it does:

- Builds both APKs and installs them with adb. Gradle's `connectedAndroidTest` would uninstall the app afterwards, and the pictures with it, so the tests run with `am instrument`.
- Puts the status bar in demo mode (12:00, full battery, no notifications) and restores it at the end.
- Runs `ScreenshotTest` twice, with the emulator in light and then dark mode: sheets and dialogs follow the system's setting, not the app's theme. Each screen is drawn with sample data and the whole screen is captured, sheets included, then scaled to half (540×1200). `overview.png` is made from five of them.
- Records `MarketFlowTest`'s two sides in dark mode, one phone at a time. Each test plays its side of one trade on the same 27-second script with real taps and typing (found through accessibility, injected as touches and key presses) and records the screen to `/sdcard/Download`. `make_market_flow_video.py` lines the two recordings up by their black lead-in and puts them side by side with the phones' names above, at full size (each phone 1080×2400, as recorded, so text stays sharp). The emulator sometimes stalls in the middle of a take; the video script notices (a long lead-in, or a take that runs past the script) and `refresh.sh` records both sides again.

When adding a screen, add it to `ScreenshotTest` (and to the table above). When the trade flow changes, the two scripts in `MarketFlowTest` share one timeline: change both together, and keep anything that would reach Samad or the Shining server played with sample data instead of tapped.
