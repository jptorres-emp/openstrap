# Readiness and Charge

OpenStrap's own recovery model. Code: `app/src/main/kotlin/org/openstrap/app/data/Recovery.kt` (pure model) and `RecoveryEngine.kt` (database → model). Tests: `app/src/test/.../RecoveryTest.kt`. Constant names below match the code.

## Why our own

Zepp's **BioCharge** is not reusable:

* The phone side is a closed native library, `libcharge_jni` (`ChargeAlgorithm.runChargeAlgo`), which takes about 40 arguments.
* The strap side runs over channel module `0x51`.
* The decompiled app shows only the inputs and outputs: per-minute stress, HR, activity, sleep stages, resting HR, age and sex in; `totalCharge`, `phyCharge`, `mentCharge`, `chronicWeight` and `exertionScore` out. Readiness is the same story (`libreadiness`).

Two ideas from Zepp carry over: splitting charge into physical and mental drains, and modelling stress apart from activity (`ChargeStressInfo.proto` regresses stress on HR and activity). Nothing else does, and we don't need it. What follows is built from published methods so every rule can be explained and checked.

## Readiness (0–100, each morning)

Each contributor compares the night that ended this morning with the user's own history. History covers up to `BASELINE_NIGHTS` = 60 prior nights. A baseline needs `MIN_BASELINE_NIGHTS` = 4 nights, and the score is labelled "learning" until `CALIBRATED_NIGHTS` = 14 nights.

### How contributors are scored

Most contributors are z-scores (deviation from baseline in SDs, positive = better). `scoreFromZ` maps them with a logistic curve:

| z | Score |
|---|---|
| 0 (normal) | 80 |
| +1 | 93 |
| −1 | 55 |
| −2 | 27 |

### Contributors

**HRV balance** (weight 0.25)
* Input: ln(mean nightly RMSSD).
* Score: 0.6·z(tonight) + 0.4·z(7-night rolling mean), against the baseline mean and SD, with the SD floored at `HRV_LN_SD_FLOOR`.
* Source: Plews et al. 2012 (Eur J Appl Physiol, DOI 10.1007/s00421-012-2354-4) used the 7-day rolling lnRMSSD against an individual normal range to spot overreaching. Vesterinen et al. 2016 (MSSE) guided training with it.
* Why log: RMSSD is right-skewed, and ln makes the SD meaningful.
* Why nightly: whole-night averages are the most accurate wearable HRV (Oura vs ECG, JMIR 2022, PMC8808342).
* Alert: z(tonight) ≤ −1.5.

**Resting heart rate** (weight 0.15)
* Input: the night's lowest 30-minute average HR.
* Score: z against baseline, inverted so lower is better, with the SD floored at 2 bpm.
* Source: Buchheit 2014 (Front Physiol 5:73): judge HR changes against measurement noise and the smallest worthwhile change, not absolute thresholds.
* Alert: ≥ max(5 bpm, 2 SD) above baseline. Elevated resting HR precedes symptoms in most infections (Mishra et al. 2020, Nat Biomed Eng, s41551-020-00640-6).

**Last night's sleep** (weight 0.18)
* The strap's own sleep score when present.
* Otherwise our own score, `sleepScore`: duration vs 8 h (50%), efficiency (25%), and deep + REM share vs 45% (25%).

**Sleep balance** (weight 0.10)
* Input: weighted mean nightly shortfall against 8 h over the last 14 nights. Weights halve every 4 nights, and a long night repays at most 1 h of debt.
* Score: −`SLEEP_DEBT_POINTS_PER_HOUR` = 25 points per hour short, so 1 h short ≈ 75 and 2 h ≈ 50.
* Source: Van Dongen et al. 2003 (Sleep 26:117): 6 h nights for 14 days caused deficits that built up night by night, equal to one or two nights of total deprivation, which subjects didn't notice.

**Recovery index** (weight 0.07)
* Input: where the night's lowest 10-minute HR falls.
* Score: 100 when it falls in the first half of the night, then sliding down to 40 at the very end.
* Source: Oura's recovery index (a late nadir suggests late meals, alcohol or late exercise).

**Body temperature** (weight 0.10)
* Input: mean skin temperature during sleep (≥ 30 readings) minus the baseline mean.
* Score: −70 points per °C beyond a ±0.2 °C dead band, in either direction.
* Source: TemPredict (Oura); temperature change is a key early-infection signal.
* Alert: ≥ +0.5 °C.

**Breathing rate** (weight 0.03)
* Input: deviation from the baseline mean.
* Score: −30 points per breath/min beyond ±1. Nightly breathing rate is very stable, so a change of 2 or more is notable.
* Alert: ≥ +2 breaths/min.

**Previous day's load** (weight 0.07)
* Input: yesterday's TRIMP divided by the chronic EWMA load. Above 1.5× the score falls by 25 points per unit, to a floor of 30.

**Training load balance** (weight 0.05, needs ≥ 14 days of load)
* Input: acute:chronic ratio from EWMA, 7 vs 28 days, λ = 2/(N+1) (Williams et al. 2017, BJSM; Murray et al. 2017, BJSM 10.1136/bjsports-2016-097152).
* Score: 100 inside 0.8–1.3 (Gabbett 2016), falling outside it.
* Low weight on purpose: the "sweet spot" is disputed (Impellizzeri et al. 2020; Fanchini et al. 2018), so it only nudges the score.

### Combining contributors

* **Weighting:** weights are renormalised over the contributors that have data, so readiness works from the first night using sleep alone.
* **Red-flag cap:** if any physiological contributor (HRV, resting HR, temperature, breathing) is below `RED_FLAG` = 40, readiness can't exceed that contributor + 30. One collapsed body signal shouldn't be averaged away by a good sleep score.
* **Strain alert:** two or more alert conditions at once, for example resting HR up with temperature up, set `strainAlert` and cap readiness at 55. Several signals moving together is what the wearable illness-detection studies rely on, and it cuts false alarms from any single noisy signal.

## Charge (0–100, minute by minute)

A body battery, simulated per minute. The simulation starts 48 h before the day shown, so the arbitrary starting value (50) washes out through two nights of sleep. Until the simulation has seen at least one sleep (`anchored`), no value is shown: charge is measured from how full a night left you, so without one the number would only echo the starting guess.

### Minute states

Each minute gets a state, following Firstbeat's stress/recovery method (*Stress and Recovery Analysis Method Based on 24-hour HRV*, 2014). In it, activity is VO₂ > 30% of VO₂max, daily activity 20–30%, and recovery after exercise (falling EPOC) is never counted as stress. We use heart-rate reserve as the stand-in for %VO₂ reserve (Swain & Leutholtz 1997): HRR = (HR − rest)/(max − rest).

| State | When | Effect per minute |
|---|---|---|
| Sleep | inside a sleep stage | `SLEEP_K`·w(stage)·relax·hrvFactor·(100 − C). Stage weights: deep 1.0, light 0.8, REM 0.65, awake-in-bed 0.1. relax = 1.2 − 0.04·(HR − night resting), limited to 0.5–1.2. hrvFactor comes from tonight's HRV contributor (0.6–1.1). |
| Exercise | HRR ≥ 0.30 | −`BASE_DRAIN` − 0.32·TRIMP(HRR). Also builds an EPOC-like debt. |
| Exercise recovery | debt > 5 and HR not rising | −`BASE_DRAIN` only, even if the strap reports high stress |
| Activity | HRR ≥ 0.20 or ≥ 60 steps/min | −`BASE_DRAIN` − 0.32·TRIMP(HRR) |
| Stress | strap stress ≥ 40 | −`BASE_DRAIN` − 0.0035·(stress − 40) |
| Relax | stress < 25 and still | −`BASE_DRAIN` + ¼ of light-sleep recharge |
| Rest | otherwise | −`BASE_DRAIN` (0.04, about 2.4/h) |
| No data | strap off the wrist | no change: we can't tell rest from effort, so the value holds |

### Calibration targets (checked by unit tests)

* 7.5 h of normal sleep takes charge from 30 to about 90.
* A restless night (high HR, low HRV) recharges clearly less.
* A quiet waking day drains about 2.4 per hour.
* An hour at 60% of reserve drains 20–32.
* A post-workout cool-down never counts as stress.

### What the screen shows

* The charge at waking and now.
* What was recharged, and what was drained by movement, by stress, and by simply being awake. This is our version of Zepp's physical/mental split.

## Training load (TRIMP)

* Formula (Banister 1991): TRIMP per minute = x·0.64·e^(1.92x) for men and x·0.86·e^(1.67x) for women, where x is HRR. Unknown sex uses the men's curve.
* Only awake minutes with x ≥ 0.2 count.
* Days with fewer than 8 h of heart-rate data are left out of the load history.
* Live heart rate is stored several times a minute; the engine averages it to one value per minute first.

## Inputs from the profile (Device → Your profile)

* **Max HR:** Tanaka et al. 2001 (JACC): 208 − 0.7·age. Without an age, the strap's own max HR (from the Zepp profile) is used, else 190. Like Firstbeat, it's raised to the highest HR the user has actually reached, by at most +15 bpm.
* **Sex:** selects the TRIMP curve.
* **Weight and height:** stored for future energy-expenditure work; not used yet.
* **Resting HR (daytime):** median of the strap's daily resting HR over 14 days, else the median nightly lowest + 3 bpm.

## Limits

* These are wellness estimates, not medical advice. The alert wording says so.
* HRV here is the strap's own RMSSD samples; we don't have beat-to-beat data. Readiness accuracy depends on the strap being worn every night.
* Charge constants are calibrated to typical published body-battery behaviour and the targets above, not fitted to lab data.
