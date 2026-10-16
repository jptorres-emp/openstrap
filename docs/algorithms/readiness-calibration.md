# Readiness calibration against the strap

**Status: collecting data.** Started 2026-10-16. Revisit after about two weeks of daily data, around **2026-10-30**. OpenStrap's Readiness formula stays as it is until then.

## Why

The strap computes its own Readiness (channel module `0x0049`). OpenStrap computes its own as well (see [recovery.md](recovery.md)). The two can disagree a lot. On the first day:

| | Strap | OpenStrap |
|---|---|---|
| Readiness | 91 | 52 |
| HRV part | 89 (62 ms overnight) | none yet: no HRV baseline ("learning 0/14 nights") |
| Resting HR part | 86 (66 bpm) | none yet |
| Skin temperature part | 100 | none yet |
| Breathing (AHI) part | 100 | none yet |
| Sleep | not a part of the strap's score | 3h 30m asleep, scored low; carries most of the 52 |

A single day can't support a fit, so we collect first. Possible changes once there's data:

* Scale OpenStrap's HRV and resting-HR parts to match the strap's (HRV 25 % and RHR 15 % of our weight).
* Use the strap's parts while OpenStrap's own baselines are still learning.
* Keep OpenStrap's sleep parts either way. The strap leaves sleep out, so its score reads high after a short night.

## What is collected

* **When:** at the end of every sync (`HistorySync.syncReadiness`), OpenStrap asks the strap for its score (`08 <since>`).
* **What comes back:** the strap holds only its latest score and returns it whatever `since` is. A history therefore exists only for days the phone synced.
* **Stored in:** table `strap_readiness`, one row per local day (`day` = local midnight, epoch seconds).
  * `score`
  * `hrvScore`, `restingHrScore`, `skinTemperatureScore`, `breathingScore`
  * `sleepHrv` (ms) and `sleepRestingHr` (bpm)
  * `algorithmVersion` (the strap reported `4.3`)
* **OpenStrap's own score is not stored.** It is computed from the synced data (`RecoveryEngine`), so the score for any past day can be recomputed during the comparison.

**Gaps:** a day the phone doesn't sync before the next morning's score replaces it is lost. Open the app at least once a day while collecting.

## Pulling the data

On a debug build connected over adb:

```bash
adb shell run-as org.openstrap.app cat databases/openstrap.db > openstrap.db
adb shell run-as org.openstrap.app cat databases/openstrap.db-wal > openstrap.db-wal
sqlite3 openstrap.db "SELECT date(day, 'unixepoch', 'localtime'), score, hrvScore, restingHrScore, skinTemperatureScore, breathingScore, sleepHrv, sleepRestingHr FROM strap_readiness ORDER BY day"
```

## When revisiting

1. Check that there are at least 10–14 days of rows and that OpenStrap's baselines have finished learning.
2. For each day, recompute OpenStrap's Readiness and its contributors. Put them next to the strap's score and parts.
3. Compare part by part:
   * our HRV balance against the strap's `hrvScore`
   * our resting HR against `restingHrScore`
   * our temperature against `skinTemperatureScore`

   Then compare the overall score with the sleep parts left out.
4. Decide between rescaling our parts, falling back to the strap's parts while learning, or leaving the formula alone. Record the decision and the numbers here, and update [recovery.md](recovery.md).
