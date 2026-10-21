<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-dark.png">
    <img src="docs/assets/logo-light.png" alt="OpenStrap logo" height="56">
  </picture>
</p>

# OpenStrap: open-source Amazfit Helio Strap app for Android (Zepp alternative)

OpenStrap replaces the Zepp app for the **Amazfit Helio Strap**. It pairs the strap, syncs sleep, heart rate, HRV, SpO₂, stress and workouts, and computes Recovery and Charge scores. Everything stays on your phone. After the first pairing you don't need a cloud account.

- **Built against real hardware.** Every protocol step is checked on a real strap and the result written down: firmware 3.20.0.1 and 3.25.0.3, on an Android 16 phone. See the *Verified on hardware* sections in [`docs/protocol/`](docs/protocol).
- **The scores are explained.** Every score is written out in plain words in [`calculations.md`](docs/algorithms/calculations.md). [`evidence-review.md`](docs/algorithms/evidence-review.md) says which parts rest on published research and which are design choices.
- **Local-first.** No Zepp cloud for your health data, no analytics, no trackers. [Network use](#network-use) lists every request the app makes.
- **Clean-room.** The protocol code is written from specs in [`docs/protocol/`](docs/protocol), which come only from static analysis of the official app. It uses no Gadgetbridge (AGPL) code.

<p align="center">
  <img src="docs/screenshots/today.png" width="30%" alt="Today">
  <img src="docs/screenshots/recovery.png" width="30%" alt="Recovery">
  <img src="docs/screenshots/sleep.png" width="30%" alt="Sleep">
</p>
<p align="center">
  <img src="docs/screenshots/activity.png" width="30%" alt="Activity">
  <img src="docs/screenshots/heart-rate.png" width="30%" alt="Heart rate">
  <img src="docs/screenshots/you.png" width="30%" alt="You">
</p>

*Screenshots use the sample data that debug builds can generate.*

## Install

Download `OpenStrap-vX.Y.Z.apk` from the [latest release](https://github.com/sayed0am/openstrap/releases/latest) and open it on your phone (Android 9 or newer). Each release lists the APK's SHA-256 in `SHA256SUMS.txt`. Updates install over the previous version and keep your data.

You need the strap's auth key to pair. See [Getting the auth key](#getting-the-auth-key).

## Status

I use it daily with one Helio Strap on one Android 16 phone. Other phones and firmware versions are untested. Reports are welcome.

| Feature | State |
|---|---|
| Pair with auth key, ECDH (B-163) auth, encrypted channel | ✅ Works |
| Sign in with Zepp to fetch the auth key | ✅ Works |
| Battery, firmware version, time sync | ✅ Works |
| Live heart rate | ✅ Works |
| History sync: activity, sleep stages, HR, HRV, resting HR, SpO₂, stress, skin temperature, breathing | ✅ Works |
| Workouts with heart-rate zones, training effect and recovery | ✅ Works |
| Workouts started on the phone: live zones, strap coaching, GPS and maps, auto-start, auto-end, missed-workout suggestions | 🧪 New, being tested |
| Recovery, Charge and sleep scores, PAI, training load | ✅ Works ([see limitations](#known-limitations)) |
| Dashboard: Today, Recovery, Sleep, Activity, You (foldables and tablets too) | ✅ Works |
| Alarms, strap health alerts | ✅ Works |
| Strap buzzes for phone calls and notifications, with a choice of pattern | ✅ Works |
| Export and import: CSV zip (every table), Android Health Connect | ✅ Works |
| Firmware update (online check with your Zepp token, or from a file) | ✅ Works |

Something not right? **You → Diagnostics → copy** captures a log to attach to an issue.

## Known limitations

- **The first pairing still needs Zepp.** The strap only accepts a first pairing that Zepp's server has signed. Every connection after that is local.
- **The scores won't match Zepp's.** Zepp computes BioCharge and Readiness on the phone with closed native libraries. OpenStrap uses its own model, and the strap can't hand over BioCharge. Readiness needs about a week of nights before it shows a score, and four weeks to settle.
- **The score weights are not yet backtested on real data.** Some constants are set to be reviewed after more nights of data ([readiness-calibration.md](docs/algorithms/readiness-calibration.md)).
- **Android only.** There is no iOS version.

## Not a medical device

OpenStrap is a general wellness app. Its scores, trends and alerts are estimates for personal insight, not medical advice. They are not meant to diagnose, treat or prevent any condition. Neither the strap's sensors nor OpenStrap's methods are clinically validated. If you feel unwell or are worried about a reading, talk to a health professional.

## Getting the auth key

1. **Link the strap in the Zepp app**, once. OpenStrap signs in with **email and password** only, because Google, Apple, Facebook and Xiaomi sign-in can't run inside it. If you signed up with Google or Apple, add an email and password to the account in the Zepp app's account settings.
2. **Get the strap ready.** Put it on its charger near the phone, and force-stop or uninstall Zepp. **Do not unlink the strap in Zepp**: that wipes the key on the strap.
3. In OpenStrap, go to **You → your strap → Sign in with Zepp**. Sign in with the same email, check the strap it finds, and tap **Pair this strap**. OpenStrap pairs straight away.

If you can't add an email, get the key on a computer with the [Gadgetbridge guide](https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/#on-a-computer). Then tap **Enter it yourself**, scan, and paste it.

If the strap rejects the key, OpenStrap says why (wrong key, strap unlinked, and so on) and offers **Start pairing again**.

Treat the key like a password. Anyone who has it can connect to your strap.

## Network use

OpenStrap goes online only for your Zepp account, for firmware and for workout maps, and only when you ask it to. It never uploads health data.

- **Sign in with Zepp** opens Zepp's own sign-in page (watchface.zepp.com) in an in-app browser. OpenStrap never sees your password. After sign-in it reads the app token and user ID from the page's cookies. It then makes one request to Zepp's device list (`GET users/{userId}/devices` on `api-mifit*.zepp.com`) and shows you the strap's Bluetooth address and auth key to confirm. It saves the token and user ID for firmware checks and clears the Zepp cookies.
- **Check for update** sends your Zepp token, user ID, and the strap's firmware version, hardware version and serial number to the update server you set. **Download** fetches the image from the URL that server returns.
- **Workout maps** load map tiles from [OpenFreeMap](https://openfreemap.org) (`tiles.openfreemap.org`), only when you open the map of a GPS workout. No account, API key or cookies. Like any tile server it sees roughly where the map is and your IP address; **Route only** on the map page loads no tiles at all. Your route itself never leaves the phone.

You can skip the sign-in and paste the key and token by hand instead.

## Permissions

- **Nearby devices (Bluetooth)** to find and connect to the strap. On Android 11 and older, Android calls this Location.
- **Location** only if you turn on GPS for an outdoor workout, and only while that workout records. Never in the background otherwise. The route is used for distance, pace and the map, then discarded unless you choose **Keep route on this phone**. Without location, distance comes from the strap's steps (shown with ≈).
- **Notifications** for the "Helio Strap" connection notification and health alerts.
- **Health Connect** read and write, only if you export to or restore from it.
- **Phone state and notification access** only if you turn on phone alerts (see [Phone access](#phone-access)).
- **Internet** only for the Zepp sign-in and firmware (see [Network use](#network-use)).
- **Keep strap connected in the background.** At the end of pairing, or from that row on the strap page, OpenStrap asks Android to link the strap to it as a companion device. Android shows its own prompt for that strap. It's a plain link, not Android's watch profile, so OpenStrap doesn't get access to calls, contacts or calendar. Once it's linked, Android leaves OpenStrap out of battery optimization and lets it reconnect from the background. On Android 12 and newer, Android also wakes OpenStrap when the strap comes into range, so it reconnects after the app is stopped or the phone restarts, without opening the app. Forget this strap removes the link. On phones without companion device support, or if you turn the prompt down, the row asks to turn off battery optimization for OpenStrap instead.

## Phone access

Both are off until you switch them on in **You → Alarms & alerts → Phone alerts**.

- **Incoming calls** needs the phone-state permission. OpenStrap uses it only to know when a call is ringing, answered or over. It never reads numbers, contacts or the call log. During Do Not Disturb the strap buzzes only if Do Not Disturb lets calls from anyone through; calls let through only as starred contacts or repeat callers ring the phone but not the strap, because telling them apart would need the caller's number.
- **Notifications** needs notification access. OpenStrap looks only at whether a notification would make a sound (Do Not Disturb and silent apps are followed) and at its category. It never reads the title or text, stores nothing, and sends the strap only a buzz. It asks Android only for notifications that alert, plus conversations. You can narrow that further, or leave out apps, on the notification-access page.

## Backup and restore

- **You → Export CSV** saves a zip with one CSV per table. **Import CSV** restores it on this phone or a new one. Data the strap synced always wins. The backup only fills in the days before the phone's earliest record. Import after the strap's first sync on a new phone. A failed import changes nothing.
- **Restore from Health Connect** brings back what OpenStrap wrote there, even after OpenStrap was uninstalled. Like the CSV import, it only fills gaps.

## How it was built

<!-- Your call: keep, reword, or delete this section. -->
I reverse-engineered the protocol from the Zepp 10.9.0 app by static analysis only, and wrote it up as specs first. The code was written with AI assistance. Every protocol behaviour was then checked on a real strap. Where the hardware disagreed with the spec, the spec was corrected and the result recorded. The `protocol/` module has a simulated strap that tests the whole handshake and sync end to end.

## Building

Requires JDK 21 and the Android SDK (compileSdk 37).

```bash
./gradlew :protocol:test :app:testDebugUnitTest :app:assembleDebug
```

Debug builds can fill an empty database with three weeks of sample data:

```bash
adb shell am start -n org.openstrap.app/.MainActivity --ez demo_data true
```

### Layout

- **`protocol/`**: pure Kotlin/JVM, no Android dependencies. It holds the B-163 ECDH, channel framing, AES/CRC session cipher, auth handshake, service codecs, and the GDSP history fetch and parsers.
- **`app/`**: the Android app. It holds the GATT client, `StrapManager`, `HistorySync`, Room storage, the Compose UI, and the exporters.
- **`docs/protocol/`**: the clean-room specs. **`docs/algorithms/`**: how each score is computed.

### Releases

CI ([`android.yml`](.github/workflows/android.yml)) tests and builds every push. Pushing a tag `vX.Y.Z` publishes a signed release, with `versionCode = X*10000 + Y*100 + Z`. The `OPENSTRAP_KEYSTORE*` and `OPENSTRAP_KEY_*` environment variables supply the signing key (repository secrets in CI). Without them, release builds use the debug key.

## License

Dual-licensed under [MIT](LICENSE-MIT) or [Apache-2.0](LICENSE-APACHE), at your option. Fonts are under the SIL OFL 1.1 ([licenses/](licenses)).

OpenStrap is not affiliated with Zepp Health or Amazfit.
