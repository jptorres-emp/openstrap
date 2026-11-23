# Screen sweep

Every screen of the app at font sizes 1.0, 1.15 and 1.3, captured on the emulator with demo data, to check a change against the Halo boards before and after.

## Running it

1. Start the emulator with the Mac's GPU (software drawing, `swiftshader_indirect`, makes every screen several times slower), build and install the dev app, then set the emulator up:
   ```bash
   emulator -avd openstrap -no-snapshot-save -no-audio -no-boot-anim -no-window -gpu host
   ./gradlew :app:assembleDebug -PdevApp && adb install -r app/build/outputs/apk/debug/app-debug.apk
   docs/testing/screens/setup.sh
   ```
2. Capture, all groups or some:
   ```bash
   docs/testing/screens/sweep.sh ~/screens/before
   FONTS=1.15 docs/testing/screens/sweep.sh ~/screens/after details tabs
   FONTS=1.3 docs/testing/screens/sweep.sh ~/screens/after changed
   ```
   `changed` captures only the groups whose screens the files changed since `SINCE` (default `HEAD`, the uncommitted work) can show on.

The emulator is an API 36 phone at 1080 × 2400 and 420 dpi. The clock is the sample week's Friday, 13 Nov 2026 at 18:05. Taps by position assume that screen. A tap in any app other than unstrap stops the run.

Nothing waits a fixed time. After each start, tap and scroll the sweep watches the screen and moves on once it has stopped changing (2.5 s after a cold start, `STILL`), up to `WAIT` (30 s). Animations are off for the run, so a screen is still as soon as it is drawn (`ANIM=1` keeps them on). A screen with a ticking timer, like Live, never settles and waits its whole limit. A capture with an empty Today or the splash means the screen sat still while loading: raise `STILL`.

## Screens

| Group | Screens | Reached by |
|---|---|---|
| today | Today at each moment (Waking, Midday, WorkoutWindow, After, Evening, Night), Edit Today | `--es moment X`, the Edit Today button at the bottom |
| details | every metric detail at Day, Week, Month and Year | `--es open_metric X`, then the range control |
| tabs | Recovery, Sleep, Activity | the tab bar |
| data | pairing steps (ready, search, connecting, connected, failed), first nights, no sleep, partial day, first score | `--es data_state X` |
| strap | the strap page, and its stale, stuck, disconnected, charging, not paired and recovery fail states | `--ez open_strap true`, `--es strap_state X` |
| sheets | the calendar, hold on a card, the metric switcher, the (i) sheet | taps on Today and on Heart rate |
| you | You, each settings page, Weekly recap, Updates | the avatar, then the row by its name; `--ez open_recap true`, `--ez open_updates true` |
| ready | Choose a sport, Ready for each sport | `--ez ready_pick true`, `--es ready_sport N` |
| live | Live, its second view and Paused for each sport, strength's set, log and exercise sheet, then each summary | `--es simulate_hr 120x60,150x600`, `--es workout start:N`, `pause`, `resume`, `end` |
| summary | the summary of the latest phone workout | `--ez show_summary true` |
| widgets | each widget with the sample day and while recording | the widget gallery (`app/src/debug/AndroidManifest.xml` must have it switched on) |
| route | a run with its route, the full map, a split picked | Activity, Workouts, the latest run |

Sport ids are in `data/SportTypes.kt` (`SPORTS` sets which ones run). A workout shorter than a minute isn't kept, so each Live run lasts 75 s.

## Not covered

The real strap (key rejected, a pairing that finds a strap), the Zepp sign-in, GPS movement, 12-hour clock, zh-CN, TalkBack.
