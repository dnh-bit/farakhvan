# فراخوان (Farakhvan)

A Persian-only, offline bulk SMS Android app for named groups. A fresh Kotlin/Compose
implementation of the core idea of [Text by Carlos Anyona](https://github.com/carloscj6/Text),
not a patch of its 2019 sources. The original MIT copyright notice is retained.

**Delivery status:** source implementation, not a compiled or device-certified release.
This delivery environment has no Android SDK, Gradle cache, or internet access.
The provided GitHub workflow is the compiler/build gate. Do not treat static review as
proof that compilation, permission behavior, or a carrier/device integration passed.

## Features

- Named groups with emoji/color, search, rename, confirmed deletion and swipe undo.
- Contact selection, manual number entry, bulk paste and copying saved numbers from
  other groups. Canonical normalization and per-group deduplication.
- Group, pasted-list and contact targets. GSM/Unicode segment estimates, `{name}`
  personalization and a preview with actual personalized segment totals.
- Optional active-SIM selection with SIM 1 selected after permission is granted.
- Persisted campaigns/results, pacing from 1 to 30 seconds, a foreground notification,
  pause/resume/cancel, explicit failed-only and individual retries.
- History, per-number callbacks, safe CSV sharing via a FileProvider.
- Persian/RTL UI, bundled Vazirmatn font, light/dark/system themes.
- No INTERNET permission, account, server, ads, telemetry, default-SMS role or scheduler.

## Screenshots

Screenshot placeholder: capture Groups, New SMS, live progress and History on a
physical Persian-locale Android device after the first successful CI build.
No simulated screenshot is presented as evidence of a running Android app.

## Build

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, Linux with
`curl`, `unzip` and `sha256sum`. Dependency downloads require internet access **at
build time only**; the installed app never uses the internet.

```sh
./gradlew assembleRelease
./gradlew testDebugUnitTest
python3 static_review.py
```

Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, matching KSP (KSP2 enabled) and Room 2.7.2
are pinned. Minimum SDK is 24;
compile/target SDK is 35. `applicationId` is `com.revosleap.text`; version is 0.1.0,
code 1. The launcher label is «فراخوان».

`gradlew` is a small, source-only bootstrap, not the usual wrapper JAR script.
It reads the pinned wrapper properties, downloads the official Gradle ZIP and checks
its SHA-256 before running Gradle. This avoids shipping an unverifiable binary JAR.
It has the requested `./gradlew assembleRelease` interface. The binary checksum is
from [Gradle's official checksum reference](https://gradle.org/release-checksums/).
The bootstrap is Linux-oriented; Windows users should use WSL.

## GitHub CI / releases

Push this folder's contents to a repository root on `main`, or run the workflow
manually. The workflow installs SDK 35, builds with Temurin 17, runs unit tests and
uploads one APK as an artifact. Tags matching `v*` also create a GitHub Release named
for the tag and attach `farakhvan-v0.1.0.apk` (the name is read from `versionName`).
The APK source is discovered via `app/build/outputs/apk/release/*.apk`.

Optional repository secrets:

- `ANDROID_KEYSTORE_B64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD` (optional, defaults to the store password)

When the first three are present, CI decodes the keystore into a temporary directory.
Gradle uses environment variables only. Without them, the release variant uses debug
signing so it is installable. No signing material belongs in this repository.
The runner deletes its temporary keystore even after build failure.

Debug signing is not suitable for stable distribution. The default debug key on a
fresh CI runner may differ across runs, so upgrading a previously installed APK may
require uninstalling it, which loses local data. Configure a stable secret-backed
keystore before sending the app to customers. A differently signed original Text
installation also cannot be upgraded in place.

## Queue and result semantics

Room v1 contains Group, Member, Campaign, SendResult and SendPart. Campaigns snapshot
the target name, message, SIM, delay and rendered message for each recipient.
Deleting or renaming a group does not change past campaigns.

Only one service queue runs at a time. Results are claimed as `sending` **before**
calling SmsManager. Each multipart callback has a distinct immutable PendingIntent
URI identifying result, attempt and part. The composite SendPart key deduplicates
callbacks; attempt IDs reject stale callbacks after an explicit retry. All parts must
report success before the row becomes `sent`. Callback writes are transactional.

`sent` is the platform **sent-result callback**, not proof of delivery to the recipient.
Delivery reports are not requested. Carriers and Android can still throttle or block
bulk sends, and the device may display its own rate/premium-SMS warnings.

After 90 seconds without all sent callbacks, the row becomes `unknown` and the queue
is interrupted. Partial multipart success is also `unknown`. Resume only processes
`pending` rows, never `sent` or `unknown` rows. An unknown row can be retried only by
tapping it and accepting the explicit duplicate/cost warning. Cancel cannot recall
an SMS already submitted to the modem; late callbacks can still resolve its result.

On a new app process without a live service, running/paused/prepared campaigns become
`interrupted`, and in-flight rows become `unknown`. The service is START_NOT_STICKY:
there is no silent boot/process-restart send. The UI can resume persisted pending rows.
Late callbacks from the interrupted attempt may still reconcile an unknown row.

**Exactly-once delivery cannot be guaranteed by SmsManager.** The database write and
the modem operation are not one transaction. The conservative `unknown` state avoids
silent duplicates when a process dies between submission and callback persistence.

The `specialUse` foreground service type is used on Android 14+ because sending
user-initiated local SMS does not qualify as dataSync or remoteMessaging. It has the
required permission and manifest subtype description. The service is started from
the visible activity and is not automatically scheduled. Play Store foreground-service
policy review is out of scope; OEM battery restrictions still need physical-device testing.

## Permissions / SIM behavior

- SEND_SMS: requested with an explanation before dispatch; denial creates no new send.
- READ_CONTACTS: requested only when contact selection is invoked; already granted
  access can refresh matched display names during preview.
- POST_NOTIFICATIONS: requested on Android 13+; refusal does not block the foreground
  service (Android still exposes it in its active-apps surface).
- READ_PHONE_STATE: requested only when enabling SIM selection. READ_PHONE_NUMBERS
  is unnecessary and is not requested.

With SIM access granted, the first active slot defaults to SIM 1. Without access,
the platform's configured default SMS subscription is used; selecting SIM 1 cannot
be guaranteed without subscription access. A previously chosen SIM that is removed
or unavailable interrupts the campaign rather than silently switching SIMs.

There is no request to become the default SMS app. Physical SIM/device testing is
required; an emulator does not establish real carrier send behavior.

## Number and message rules

Iranian mobile variants `+98912…`, `0098912…`, `98912…`, `0912…` and bare `912…`
normalize to `+989…`. Persian/Arabic-Indic digits are converted; spaces, hyphens,
parentheses and common bidi markers are removed. General international `+`/`00`
E.164-shaped numbers of 8 to 15 digits are accepted. Bare non-Iranian numbers are
invalid rather than guessed. Local landlines and short codes are rejected.

Bulk paste uses whitespace, Latin/Persian commas or semicolons as **separators**.
Therefore paste each formatted number without internal spaces, or use manual entry
for a single formatted number. Invalid values may be saved for correction/removal;
they are red in the UI and are recorded as skipped/failed, never submitted.

Syntactic validation cannot prove a number is reachable or determine international
premium tariffs. Check destination charges with the carrier. Nothing automates
premium/short-code messaging or changes the device's premium-SMS policy.

The live counter uses Android's SmsMessage.calculateLength; the confirmation sheet
recalculates each personalized body. SmsManager.divideMessage determines the actual
parts used by the selected subscription. OEM/carrier encoding can differ from the
generic live estimate, so this is a segment estimate, not a monetary price quote.
Carrier fees are not displayed as invented currency amounts.

## Brief conflicts resolved

The later, explicit **Persian-only UI** requirement takes precedence over the earlier
request for English language switching. There is no language selector.

The mandatory pre-send review takes precedence over allowing confirmation to be
disabled. The setting disables only an **additional final confirmation**; the
recipient/message/cost sheet is always shown. Resume and all retries are also reviewed.

## CSV

Export columns are `number,status,timestamp`, with UTC ISO timestamps and UTF-8 BOM.
Status values are the stored machine-readable states. Values are quoted and escaped.
For spreadsheet safety, fields beginning with `=`, `+`, `-`, `@`, tab or carriage
return are prefixed with an apostrophe. This includes canonical `+98…` numbers; a
programmatic importer should remove that protective apostrophe from the number field.
CSV files remain in the app's private cache and are shared only by explicit user action.

## Verification

See `SELF_REVIEW.txt` and `TESTING.txt`.
Normalization unit tests are included. The deliverable has undergone static scans
for balanced delimiters/string termination, missing string resources, duplicate imports,
legacy imports, manifest/XML validity and excluded files. Compilation and runtime
acceptance remain pending CI and physical devices; no APK is included in this ZIP.

## Licenses

App: MIT, original copyright © 2019 Carlos Anyona, retained in `LICENSE` and in
the APK's `assets/licenses/LICENSE.txt`.
Vazirmatn: SIL Open Font License 1.1, notice included in
`app/src/main/assets/licenses/OFL-Vazirmatn.txt`; the unmodified font is bundled.
AndroidX, Kotlin and Material retain their respective upstream licenses.
