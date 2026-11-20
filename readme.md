# ☀️ Shining: SUT Food Reservation Client

**Shining** is a standalone client for the Sharif University of Technology (SUT) food reservation system, **Samad**. The app talks to the Samad servers directly. **Your login and reservations stay on your phone.** A Nisatech server only watches when next week's menus are published and sends the alert; it never receives anything about you or your account (see [server/](server/README.md)).

Shining is built on [Foodie](https://github.com/amirHravan/foodie) by Amir Hossein Ravan Nakhjavani, with a redesigned interface.

![Shining screens: home, reserve, daily sale, profile, and home in dark mode](docs/screenshots/overview.png)

## ✨ Features

- **Home overview**: your credit, how many days are reserved and eaten, and every reserved meal with its forget code. Fetch a code once and it stays next to the meal, even after restarting the app.
- **Food reservation**: every cafeteria's foods in one list, one day at a time; reserve or cancel with one tap. Days that already have a reservation are marked.
- **Daily sale**: buy food when the university runs a daily sale, with a live "sold" bar for each item.
- **Profile**: credit, transactions and student details, plus quick switching between up to five Samad accounts (long-press the Profile tab).
- **Next-week menu alerts**: a notification as soon as next week's menu of سلف, کاله, کلانا, کلین‌فود, یونی‌فود or فست‌فود شریف comes out (choose which in Settings); tapping it opens Reserve on that day. A card on Home shows each one's status and when it usually arrives.
- **Reminders**: on Tuesday and Wednesday, only if menus are out and you still have days left to reserve.
- **Light and dark themes**, following the system setting.

### Design

- Light, card-based layout with a floating bottom bar; the current tab grows into a labelled pill.
- Main color `#2862FF` (blue). The logo is 🍟 fries with no face (`res/drawable-nodpi/logo_fries.png`) on no background, both as the app icon and inside the app. The French fries food picture keeps the Apple emoji.
- Pictures (tabs, meals, cards) are Apple emoji, bundled as PNGs in `res/drawable-nodpi/emoji_*.png`. Action icons (add, refresh, copy, ...) stay as line icons.
- Typeface: [Vazirmatn](https://github.com/rastikerdar/vazirmatn) (SIL Open Font License, see [`licenses/Vazirmatn-OFL.txt`](licenses/Vazirmatn-OFL.txt)).

## 📱 Screens

| Home | Reserve | Daily sale | Profile |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/home.png" width="200" alt="Home" /> | <img src="docs/screenshots/order.png" width="200" alt="Reserve food" /> | <img src="docs/screenshots/daily_sale.png" width="200" alt="Daily sale" /> | <img src="docs/screenshots/profile.png" width="200" alt="Profile" /> |

| Login | Home (dark) | Reserve (dark) | |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/login.png" width="200" alt="Login" /> | <img src="docs/screenshots/home-dark.png" width="200" alt="Home in dark mode" /> | <img src="docs/screenshots/order-dark.png" width="200" alt="Reserve food in dark mode" /> | |

The screens show sample data.

## 📲 Install on your phone

1. Build the debug APK (JDK 17 and the Android SDK are needed):
   ```bash
   ./gradlew assembleDebug
   ```
2. Install it with USB debugging enabled:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   Or copy the APK to the phone and open it.

Shining uses its own application id (`app.shining`), so it installs next to Foodie without replacing it. Accounts are not shared between the two; log in again in Shining.

## 🚀 Releases

Signed APKs are on the [Releases](https://github.com/Nisatech-org/shining-app/releases) page. They're built by [`.github/workflows/release.yml`](.github/workflows/release.yml): pushing a tag such as `v1.12.0` (it must match `versionName`) builds the signed release APK and publishes it with its SHA-256 checksum. The release notes are the tagged commit's message body. Running the workflow by hand builds the same APK as a run artifact without publishing it. The signing key and Firebase config come from repository secrets, listed at the top of the workflow.

## 🛠️ Architecture & Tech Stack

Shining follows **MVVM**.

- **Jetpack Compose** for the UI, with a small in-house design system in `domain/ui` (theme tokens, cards, buttons, pills, states).
- **Retrofit2** for the Samad API.
- **Koin** for dependency injection.
- **Room** for local storage.
- **Coroutines** for asynchronous work.

## 🤝 Contributing

See [contributing.md](contributing.md).

## 📜 License

Shining is a derivative of Foodie and is distributed under the same custom MIT License with additional restrictions (no commercial use). See [LICENSE](LICENSE).
