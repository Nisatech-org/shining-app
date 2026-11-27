# Shining privacy policy

Last updated: 2026-11-27

Shining is an Android app by Nisatech for Sharif University's food reservation system (Samad). This policy says what the app keeps, what it sends where, and how to delete it. Contact: [shining@nisatech.org](mailto:shining@nisatech.org).

## On your phone

- Your Samad sign-in (student number, password and the session token Samad returns) is stored only on your phone. The app sends it only to Samad itself (`setad.dining.sharif.edu`) to read menus, reservations and credit and to reserve food for you.
- Your settings, cached menus and the app's own debug log stay on the phone. Secrets are blanked out of the log, and it leaves the phone only if you choose to share it from the hidden debug screen.

## Menu alerts

If you turn on menu alerts, the app subscribes to a push topic through Google's Firebase Cloud Messaging, which handles the delivery. Turning alerts off unsubscribes.

## The market (opt-in)

Nothing in this section applies unless you tap «ورود به بازار».

- To join as a student, the app sends your Samad token to the Shining server **once**. The server reads your student number from Samad, keeps only a keyed hash of it (so one student has one market account and gets the verified tick) and does not keep the token. A guest sends nothing about themselves: the server makes an account with a random id.
- For people who join, the server keeps: that hash or random id and a random push topic; your listings, offers and trades (meal, price, times, status, your rating, and the note a buyer may add when saying they've paid, which only that trade's seller sees); if you sell, your card number, encrypted, shown only to the buyer of your trade while they pay; and the forget code of each listing, encrypted, until it reaches the buyer or the listing ends.
- Names are never shown in the app. A buyer paying card to card sees the card holder's name in their own bank app.
- Forget codes are deleted from the server as soon as the buyer's phone has them, or when the listing is taken down or its meal is over.
- All traffic to the Shining server and to Samad is encrypted (HTTPS).

## What Shining does not do

No ads, no advertising ID, no analytics or tracking SDKs, and no selling or sharing of your data with anyone. Shining is meant for university students and is not directed at children under 18.

## Deleting your data

- In the app: «خروج از بازار» (under «بازار چطوری کار می‌کنه؟» in the market) deletes your market account, your card number, your open listings and their forget codes from the server right away. It waits until any trade in progress is finished. Past trades stay in the other side's history, without you in them. Uninstalling the app deletes everything stored on the phone.
- By email: write to [shining@nisatech.org](mailto:shining@nisatech.org) and we delete your market account and everything tied to it within 30 days.

## Changes

When this policy changes, the new version is published here with a new date.

---

# سیاست حریم خصوصی شاینینگ

شاینینگ اپ اندرویدی نیساتک برای سامانه‌ی رزرو غذای دانشگاه شریف (سماد) است.

- اطلاعات ورود به سماد فقط روی گوشی خودت ذخیره می‌شود و فقط به خود سماد فرستاده می‌شود.
- بازار اختیاری است. برای ورود دانشجویی، توکن سماد یک بار به سرور شاینینگ می‌رود؛ سرور فقط یک شناسه‌ی رمزشده به جای شماره‌ی دانشجویی نگه می‌دارد، نه خود توکن. مهمان‌ها یک شناسه‌ی تصادفی می‌گیرند.
- سرور آگهی‌ها و معامله‌ها (با یادداشتی که خریدار موقع پرداخت برای فروشنده می‌نویسد)، و اگر بفروشی شماره‌ی کارتت را (رمزشده، فقط برای خریدار همان معامله) نگه می‌دارد. کد فراموشی همین که به خریدار برسد پاک می‌شود.
- تبلیغ، ردیابی و فروش داده نداریم.
- با «خروج از بازار» حساب و کارتت از سرور پاک می‌شود. برای پاک کردن از راه ایمیل به [shining@nisatech.org](mailto:shining@nisatech.org) بنویس.
