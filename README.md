# فراخوان (Farakhvan)

Bulk group messaging for Android — **no server, no internet, no cost other than your SIM's SMS charges.**

Create named groups of phone numbers, type one message, and send it to every member with a single tap while watching per-number progress. Built for the typical shop owner who messages their own customers from their own phone.

> Persian-only UI (fa), fully RTL, Material 3, Jetpack Compose. Package `com.farakhvan.text`, version `0.1.0` (code 1).

<!-- Screenshot placeholder: add screenshots/groups.png, screenshots/new-sms.png, screenshots/progress.png -->
![Screenshots](docs/screenshots.png)

## Features

- **Groups** – create / rename / delete (confirmation, or swipe with Undo), colour + emoji, search, member count, "last used".
- **Four ways to add members** – contact picker (in-app multi-select), single number, paste a bulk list (newline / comma / semicolon / space), copy from another group.
- **Number handling** – strips spaces, dashes, parentheses; converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits; accepts `+98912…`, `0098912…`, `98912…`, `0912…`, `912…` and stores a single canonical form (`+98912…`); de-duplicates and reports how many duplicates were removed; invalid numbers are shown in red and are **skipped** while sending.
- **Compose** – target = group / pasted numbers / contacts, live SMS part counter (GSM-7 vs Unicode, "N parts per recipient"), `{name}` personalisation, dual-SIM selector, sticky big Send button, confirmation sheet (count, first numbers, message, SMS-part cost).
- **Sending engine** – foreground service, configurable delay (1–30 s, default 3 s), per-recipient status pending → sending → sent / failed(reason), real delivery result through a `PendingIntent`, multipart messages, pause / resume / cancel, retry failed only, retry a single number.
- **Survives leaving the app** – progress notification ("Sending 12/40…"), tap returns to the app.
- **Interrupted campaigns** – after a process kill the campaign is shown as *interrupted* and can be resumed from the first unsent number. Rows already marked *sent* are never resent; a row that was mid-send at the time of the kill is marked *failed (outcome unknown)* instead of being silently resent.
- **History** – every campaign (date, target, message, totals, per-number rows), CSV export (`number,status,timestamp`) through the share sheet.
- **Settings** – delay, default SIM, theme (system/light/dark), confirmation toggle, version info.
- **Privacy** – no `INTERNET` permission, no account, no analytics, no ads, no third-party SDKs (AndroidX + Material only). The app does **not** need to be, and never asks to be, the default SMS app.

## Tech

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · JDK 17 · compileSdk/targetSdk 35 · minSdk 24 · Jetpack Compose + Material 3 · Navigation Compose (single activity) · Room 2.6.1 (KSP) · Vazirmatn font (SIL OFL 1.1).

Room entities: `GroupEntity`, `MemberEntity`, `CampaignEntity`, `SendResultEntity` (schema v1, no migrations).

## Build

```bash
# once, if gradle/wrapper/gradle-wrapper.jar is missing (binary files are not part of the source drop):
gradle wrapper --gradle-version 8.9

./gradlew assembleRelease
# APK: app/build/outputs/apk/release/*.apk
```

Notes:

- The Vazirmatn TTFs are downloaded automatically by the `downloadVazirmatn` Gradle task (runs before every build, skips files that already exist) into `app/src/main/res/font/`. They are git-ignored. The build machine therefore needs internet access once; the app itself never does.
- Signing: the release build is signed with the keystore described by the environment variables `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (and optionally `ANDROID_KEY_PASSWORD`). Without them it falls back to the debug key so the APK is still installable. **Never commit a keystore, `key.properties` or passwords.**

## CI / Releases

`.github/workflows/build-android.yml`

- push to `main` or manual dispatch → build with JDK 17 → upload the APK as an artifact.
- push of a tag `v*` → same build, then a GitHub Release is created with `farakhvan-v<versionName>.apk` attached.
- Signing secrets (optional): `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (+ `ANDROID_KEY_PASSWORD`).

## Project layout

```
app/src/main/java/com/farakhvan/text/
  MainActivity.kt, FarakhvanApp.kt
  data/     Entities, Daos, AppDatabase, Repo, Settings
  sending/  SendController (UI entry), SendService (foreground engine)
  util/     PhoneNormalizer, SmsUtil (parts, SIMs), ContactsHelper, Formatting (dates, CSV)
  ui/       AppRoot (nav + bottom bar), Groups, GroupDetail, Compose (New SMS),
            Campaign (progress + history detail), History, Settings, Dialogs, Theme
app/src/main/res/values/strings.xml   (all UI text, Persian)
```

## Design decisions worth knowing

- One screen (`CampaignScreen`) serves as both the live sending-progress view and the history detail view.
- The language row in Settings is informational: the product brief makes Persian the single UI language.
- Foreground service type is `dataSync`; Android 15 limits that type to ~6 h per run, which is far above a normal campaign (≈ 7 000 messages at 3 s).
- Carriers and regulators may restrict bulk SMS. You are responsible for only messaging people who expect to hear from you.

## License

MIT — see [LICENSE](LICENSE). Original copyright © 2019 Carlos Anyona, whose open-source app *Text* (`github.com/carloscj6/Text`) inspired this project's core idea ("pick numbers → write one message → send to all from this phone's SIM"). The Vazirmatn font is distributed under the SIL Open Font License 1.1.
