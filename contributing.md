# Contributing to Shining

Thanks for wanting to help! Bug reports, ideas and code are all welcome, in Persian or English.

Everyone taking part follows our [Code of Conduct](.github/CODE_OF_CONDUCT.md).

## Reporting bugs and ideas

Open an [issue](https://github.com/Nisatech-org/shining-app/issues/new/choose) and pick a form. Search the existing issues first.

- A problem with a market trade goes through «مشکل دارم» in the app, not an issue: see [SUPPORT.md](.github/SUPPORT.md).
- A security problem goes to our email, not an issue: see [SECURITY.md](.github/SECURITY.md).
- Never post a password, token, forget code, card number or student number, even your own.

## How pull requests work here

This repository is published from the team's private repo: `main` always holds a single commit, which is replaced with each release (see [Releases](readme.md#-releases) in the readme). So a pull request can't be merged as is. When we accept one, we apply your change in the team's repo, it ships in the next release, and we close the pull request with a link to that release.

For anything bigger than a small fix, open an issue first so we can agree on the approach before you write the code.

1. Fork the repo and branch off `main`.
2. Make the change, with tests where they fit.
3. Run `./gradlew assembleDebug testDebugUnitTest`. CI runs the same on every pull request.
4. Open the pull request and fill in its template.

The Shining server (menu alerts and the market) is private. If a change needs the server to change too, say so in the issue and we'll handle that side.

## Setting up

You need JDK 17 and the Android SDK (API 34). Android Studio Hedgehog or newer works.

```bash
git clone https://github.com/Nisatech-org/shining-app.git
cd shining-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The debug build has the same app id as the release (`app.shining`) but a different signature, so uninstall the Cafe Bazaar or GitHub version first. It builds without any private files:

- Without `app/google-services.json`, push notifications (menu alerts, market updates) are off. Everything else works.
- Without `keystore.properties`, the release build is unsigned. Use the debug build.
- The public build uses the Vazirmatn font (`app/src/fonts-fallback`). The release app uses a licensed font that can't be shared.

Unit tests: `./gradlew testDebugUnitTest`. They run on the JVM, no phone needed.

## Finding your way around

The app is one module, `app`, in MVVM with one package per feature under `app/src/main/java/app/shining`:

| Package | What it is |
| --- | --- |
| `domain` | Shared code: the Samad API client, utilities, and the design system in `domain/ui` (theme, cards, buttons, pills, states) |
| `login`, `account` | Login, guest login and switching between Samad accounts |
| `home`, `reserveinfo` | The home screen and the bottom bar; reservations and forget codes |
| `order`, `dailysell` | Reserving food; the daily sale |
| `market` | The market: listings, trades, the meal board |
| `profile`, `credit`, `settings` | Profile, credit and transactions, settings |
| `push` | Menu alerts, reminders and market notifications |

Libraries: Jetpack Compose with Material 3, Koin for dependency injection, Retrofit for Samad and the Shining server, Room for local storage, coroutines.

## Code guidelines

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and match the code around you.
- Build screens from the components in `domain/ui` and the theme's colors and type, not one-off styles. Check every screen in light and dark.
- The app's text is Persian and written in the code. Keep it short and friendly, the way the rest of the app talks.
- Persian text runs right to left. Wrap numbers mixed with symbols, like card numbers with `*`, in U+2066 ... U+2069 so they don't come out reversed.
- A change to a Room entity needs a version bump, a migration, and the exported schema in `app/schemas`.
- Never log or store tokens, passwords, forget codes or card numbers in plain text.
- Sample data in tests and screenshots is invented: no real names, student numbers or card numbers.

## Screenshots

If you change how a screen looks, add before and after screenshots to the pull request. The ones in [`docs/screenshots`](docs/screenshots) are made by a script; [its readme](docs/screenshots/README.md) explains how. You don't need to run it yourself, we refresh them when we apply the change.

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/) with a scope, in the imperative or as a short description of the result:

- `feat(app): pick a reservation to fill in the meal you sell`
- `fix(app): keep the forget code after a restart`
- `docs: explain the guest market`

Reference the issue in the body when there is one (`Closes #123`).

## License

Shining is licensed under the [Apache License 2.0](LICENSE). By sending a contribution, you agree that it's released under the same license.
