# Readiness and Charge

OpenStrap's own recovery model. Code: `app/src/main/kotlin/org/openstrap/app/data/Recovery.kt` (pure model) and `RecoveryEngine.kt` (database → model). Tests: `app/src/test/.../RecoveryTest.kt`. Constant names below match the code.

Planned changes and their evidence: [evidence-review.md](evidence-review.md).

## Why our own

Zepp's **BioCharge** is not reusable:

* The phone side is a closed native library, `libcharge_jni` (`ChargeAlgorithm.runChargeAlgo`), which takes about 40 arguments.
* The strap side runs over channel module `0x51`.
* The decompiled app shows only the inputs and outputs: per-minute stress, HR, activity, sleep stages, resting HR, age and sex in; `totalCharge`, `phyCharge`, `mentCharge`, `chronicWeight` and `exertionScore` out. Readiness is the same story (`libreadiness`).

Two ideas from Zepp carry over: splitting charge into physical and mental drains, and modelling stress apart from activity (`ChargeStressInfo.proto` regresses stress on HR and activity). Nothing else does, and we don't need it. What follows is built from published methods so every rule can be explained and checked.

## Readiness (0–100, each morning)

Each contributor compares the night that ended this morning with the user's own history.

### Valid nights and baselines

* **Validity rule** (`Night.valid`): main sleep ≥ 3 h, ≥ `MIN_HRV_SAMPLES` = 6 RMSSD samples in 5–200 ms, and ≥ `MIN_HR_MINUTES` = 120 minutes of heart rate in 30–220 bpm. Only valid nights enter the HRV and resting-HR baselines.
* **Baseline** (`baseline`): centre = median over the prior ≤ `BASELINE_NIGHTS` = 60 valid nights; scale = max(`MAD_SCALE`·MAD, floor), `MAD_SCALE` = 1.4826. A mean and SD let one bad stretch (illness, alcohol, artefacts) become "normal"; the median and MAD don't.
* **Floors:** ln HRV `HRV_LN_SD_FLOOR` = 0.05, resting HR `RHR_SD_FLOOR` = 2 bpm (above device error of about 1–1.8 bpm), breathing `BREATH_SD_FLOOR` = 0.5 /min, skin temperature `TEMP_SD_FLOOR` = 0.15 °C, SpO2 `SPO2_SD_FLOOR` = 0.5 %.
* **Phases** (`phase`, by valid nights; they replace the old 4 / 14 nights):

| Valid nights | HRV and resting HR |
|---|---|
| < `SCORED_NIGHTS` = 7 | withheld; the strap's score and parts show as "Strap's estimate" |
| 7–13 | scored, labelled "learning" |
| 14–27 | scored |
| ≥ `CALIBRATED_NIGHTS` = 28 | labelled "calibrated" |

* **Withheld:** Readiness has no score unless an HRV or resting-HR contributor is present. On 2026-10-16 sleep alone gave 52 against the strap's 91; such a day shows the sleep contributor and the strap's estimate instead.

The strap's own Readiness is logged daily to calibrate this score against; see [readiness-calibration.md](readiness-calibration.md).

### How contributors are scored

Most contributors are z-scores (deviation from the baseline centre in units of its scale, positive = better). HRV, resting HR, breathing and temperature first go through a dead zone at the smallest worthwhile change, `deadZone`: z_eff = sign(z)·max(0, |z| − `SWC_SD`), `SWC_SD` = 0.5, because changes under half a scale unit are noise (Buchheit, Plews). `scoreFromZ` then maps them with a logistic curve, 100 / (1 + e^−(1.386 + 1.19·z)):

| z | Score |
|---|---|
| 0 (normal) | 80 |
| +1 | 93 |
| −1 | 55 |
| −2 | 27 |

### Contributors

**HRV balance** (weight 0.30)
* Input: `hrv_n` = ln(mean of the night's gated RMSSD samples) on a valid night. This definition never changes inside a baseline.
* `hrv_7` = mean of `hrv_n` over the last 7 nights, needing ≥ `HRV_WEEK_MIN_NIGHTS` = 3 valid nights; "thin" below `HRV_WEEK_FULL_NIGHTS` = 5.
* Score: `HRV_NIGHT_WEIGHT` = 0.4·z(tonight) + `HRV_WEEK_WEIGHT` = 0.6·z(`hrv_7`), through the dead zone. Weighted towards the week because nightly wearable HRV error (6–16 % MAPE) is larger than the ~3 % smallest worthwhile change. Without `hrv_7`, tonight alone.
* Normal-range state (`hrvRange`): `hrv_7` against centre ± `HRV_RANGE_SCALE` = 0.5·scale gives below / within / above, the band the HRV-guided training trials used.
* Source: Plews' weekly-average work: a 7-day rolling mean of lnRMSSD is a steadier marker than a single morning (Plews et al., [AUT](https://openrepository.aut.ac.nz/items/d7b41d29-e92e-4acf-90ea-ed68d84d6c73)). Plews et al. 2012 (Eur J Appl Physiol, [PubMed](https://pubmed.ncbi.nlm.nih.gov/22367011/)) is only a case study of 2 athletes. In the HRV-guided training trials, Vesterinen et al. 2016 used **raw** RMSSD from a 4-minute supine morning chest-belt recording, with a band of mean ± 0.5 SD over 4 baseline weeks ([JYX](https://jyx.jyu.fi/handle/123456789/50625)); effects were small (ES 0.42).
* Why log: RMSSD is right-skewed, and ln makes the SD meaningful.
* Why nightly: whole-night averages are the most accurate wearable HRV (Oura vs ECG, JMIR 2022, PMC8808342).
* Alert: z(tonight) ≤ −1.5.

**Resting heart rate** (weight 0.22)
* Input: `rhr_n`, the night's lowest 30-minute average HR, the window needing ≥ `RHR_WINDOW_MIN_MINUTES` = 20 minutes of data (Garmin's definition). The sleep mean (`rhr_mean_sleep`) and 10th percentile (`rhr_p10`) are logged too, for the review to pick the least noisy one.
* Score: z against baseline, inverted so lower is better, through the dead zone.
* Source: Buchheit 2014 (Front Physiol 5:73): judge HR changes against measurement noise and the smallest worthwhile change, not absolute thresholds.
* Alert: ≥ max(5 bpm, 2 SD) above baseline. Overnight resting HR is the best-tested early-warning signal: in Mishra et al. 2020 ([medRxiv](https://www.medrxiv.org/content/10.1101/2020.07.06.20147512v1)) it rose before symptoms in 32 COVID-19 cases, 63 % detectably in real time. That study says nothing about other conditions.

**Last night's sleep** (weight 0.16)
* The strap's own sleep score when present.
* Otherwise our own score, `sleepScore`: duration vs 8 h (50%), efficiency (25%), and deep + REM share vs 45% (25%).

**Sleep balance** (weight 0.12)
* Input: weighted mean nightly shortfall against 8 h over the last 14 nights. Weights halve every 4 nights, and a long night repays at most 1 h of debt.
* Score: −`SLEEP_DEBT_POINTS_PER_HOUR` = 25 points per hour short, so 1 h short ≈ 75 and 2 h ≈ 50.
* Source: Van Dongen et al. 2003 (Sleep 26:117): 6 h nights for 14 days caused deficits that built up night by night, equal to one or two nights of total deprivation, which subjects didn't notice.

**Recovery index** (weight 0.03)
* Input: where the night's lowest 10-minute HR falls.
* Score: 100 when it falls in the first half of the night, then sliding down to 40 at the very end.
* Source: Oura's recovery index (a late nadir suggests late meals, alcohol or late exercise).

**Body temperature** (weight 0.06)
* Input: mean skin temperature during sleep (≥ 30 readings).
* Score: `scoreFromZ`(−|z_eff|): a change in either direction beyond the dead zone counts.
* Source: TemPredict (Oura); adding temperature raised a multimodal model's AUC from 0.770 to 0.819 (Mason et al. 2022, [PMC8891385](https://pmc.ncbi.nlm.nih.gov/articles/PMC8891385)). The luteal-phase rise is about +0.3 °C at the finger.
* Alert: ≥ +0.5 °C.

**Breathing rate** (weight 0.04)
* Input: mean breathing rate during sleep.
* Score: `scoreFromZ`(−|z_eff|), either direction. Nightly breathing rate is very stable, so its floor is 0.5 /min.
* Alert: ≥ +2 breaths/min.

**Blood oxygen** (weight 0.03)
* Input: mean sleep SpO2 (strap type 38) during the night, from at least 5 readings.
* Score: −`SPO2_POINTS_PER_PERCENT` = 25 points per % below the baseline centre, beyond a 1 % dead band. A night above the usual level scores 100; higher oxygen isn't better. 2 % down ≈ 75, 3 % down ≈ 50.
* Source: healthy adults hold a mean nightly SpO2 of about 96 %, steady night to night (Gries & Brooks 1996, Chest 110:1489). A sustained fall can come with altitude, disturbed breathing or a respiratory problem.
* Alert: ≥ `SPO2_ALERT_DROP` = 2 % below baseline.

**Breathing disturbances** (not part of Readiness: the Breathing card on the Sleep screen)
* Input: the strap's nightly sleep-apnea summary (type 74). We use only its count of valid desaturation events, divided by hours asleep, because the units of its own ODI fields are unverified. Needs at least 2 h asleep.
* Shown (`breathingDisturbances`) as events per hour against the median of the user's own nights (from 7 nights). Moved out of Readiness because it is a stable trait, so inside the score it only acted as a fixed offset.
* Zepp's 5 / 15 / 30 break points (`bn50.java`) are apnea severity bands applied to the strap's desaturation counts. They are never shown: naming them (normal / mild / moderate / severe) would breach the 2026 wellness guidance. It's a wellness estimate, not a diagnosis.

**Previous day's load** (weight 0.04)
* Input: yesterday's TRIMP divided by the chronic EWMA load. Above 1.5× the score falls by 25 points per unit, to a floor of 30.

**Training load balance** (not part of Readiness: the Load panel on the Activity screen, labelled "contested")
* Input: acute:chronic ratio from EWMA, 7 vs 28 days (`acwr`), needing ≥ 14 days of load.
* Out of Readiness because the evidence is against scoring with it: the coupling critique is accepted by its own proponents (BJSM 53/15/921), ACWR rescales acute load (Impellizzeri 2021), and the 2025 meta-analysis for 0.8–1.3 has a CI of 0.14–0.94 (Qin et al. 2025).

Weights (a design choice, to be checked with the backtest) add up to 1: autonomic (HRV + resting HR) 52 %, sleep 28 %, other body signals 13 %, previous day's load 4 %, recovery index 3 %. They're renormalised over the contributors present, so only their ratios matter. Polar's only public hint is that heart rate has "the biggest influence"; bounding sleep's share keeps one short night from carrying the score.

### The strap's own nightly HRV and resting HR

The strap's method is unknown and may change with firmware, and a baseline that mixes two definitions measures the switch, not the body. So:

1. Before 7 valid own nights (and on a withheld day), the strap's Readiness and its parts show as "Strap's estimate". They never enter OpenStrap's score.
2. On a night where our own value fails the validity rule, the strap's `sleepHrv` / `sleepRestingHr` show (and feed the alerts), flagged "strap fallback", and stay out of the baseline.
3. A data-quality note appears when own and strap differ by more than `HRV_QA_FRACTION` = 15 % (HRV) or `RHR_QA_BPM` = 3 bpm (resting HR).

### Combining contributors

* **Weighting:** weights are renormalised over the contributors that have data. Without HRV or resting HR there is no score (see "Withheld").
* **Red-flag cap:** if any physiological contributor (HRV, resting HR, temperature, breathing) is below `RED_FLAG` = 40, readiness can't exceed that contributor + 30. One collapsed body signal shouldn't be averaged away by a good sleep score.
* **Body signals off baseline:** two or more alert conditions at once, for example resting HR up with temperature up, set `strainAlert` and cap readiness at 55. Requiring several signals is plausible as a guard against one noisy signal, but no study has measured a rule-based conjunction, so the benefit is unquantified.
* **Wording** (FDA General Wellness guidance, revised 6 January 2026): the feature is called "Body signals off baseline" and never names illness, infection, fever or COVID. Copy lives in `BodySignalsCopy`; the push text is the evidence review's: "Several of your overnight signals (…) are outside your usual range for the second night. This can happen with heavy training, alcohol, travel, poor sleep, or when your body is fighting something off. Consider an easier day. If you feel unwell, checking in with a healthcare professional may be helpful. This is a wellness estimate, not a medical assessment." No screen shows a temperature threshold.

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
| Stress | strap stress ≥ your threshold *T* (below) | −`BASE_DRAIN` − 0.007·(stress − *T*) |
| Relax | stress < 25 and still | −`BASE_DRAIN` + ¼ of light-sleep recharge |
| Rest | otherwise | −`BASE_DRAIN` (0.04, about 2.4/h) |
| No data | strap off the wrist | no change: we can't tell rest from effort, so the value holds |

### Personal stress threshold

The strap's stress score is the same one Zepp shows. Zepp labels it relaxed 1–39, normal 40–59, medium 60–79 and high 80–100. Where stress starts differs between people, so *T* comes from the user's own readings (`stressThreshold`).

* Input: the strap's stress readings from the last `STRESS_BASELINE_DAYS` = 14 days that were calm. Calm means awake, fewer than 10 steps that minute, and heart rate below 20% of reserve.
* Calm level: the median of those readings.
* *T* = calm level + `STRESS_MARGIN` (10), kept within 50–70. A calm level of 50 gives 60, where Zepp's "medium" band starts.
* *T* is 60 until there are `MIN_STRESS_DAYS` = 3 days and at least 60 calm readings.
* The Recovery screen states *T* and the calm level it came from.

Zepp does the same job differently. It computes a stress threshold on the phone or in its cloud and writes it to the strap (Readiness module 0x49, opcode `0E`; `j560.java`). The strap only stores what Zepp gives it. A strap used only with OpenStrap returns no records (`0C` → `0d 00 00`, verified on hardware), so there is nothing to read back.

### Calibration targets (checked by unit tests)

* 7.5 h of normal sleep takes charge from 30 to about 90.
* A restless night (high HR, low HRV) recharges clearly less.
* A quiet waking day drains about 2.4 per hour.
* An hour at 60% of reserve drains 20–32.
* A post-workout cool-down never counts as stress.
* A seated day in Zepp's "normal" stress band (40–59) drains like rest, not stress. Zepp labels the strap's score relaxed 1–39, normal 40–59, medium 60–79 and high 80–100; counting from 40 had most waking minutes drain as stress.

### What the screen shows

* The charge at waking and now.
* What was recharged, and what was drained by movement, by stress, and by simply being awake. This is our version of Zepp's physical/mental split.

## Training load (TRIMP)

* Formula (Banister 1991): TRIMP per minute = x·0.64·e^(1.92x) for men and x·0.86·e^(1.67x) for women, where x is HRR. Unknown sex uses the men's curve.
* Only awake minutes with x ≥ 0.2 count.
* Days with fewer than 8 h of heart-rate data are left out of the load history.
* Live heart rate is stored several times a minute; the engine averages it to one value per minute first.

## Inputs from the profile (Device → Your profile)

* **Max HR:** Tanaka et al. 2001 (JACC): 208 − 0.7·age. Without an age, the strap's own max HR (from the Zepp profile) is used, else 190. It's raised to the highest HR the user has actually reached, by at most +15 bpm. "Reached" is the second-highest value over 60 days, so one artefact can't set it, taken from both the per-minute heart rate and the strap's daily maximum exercise HR (type 61). Max HR sets heart-rate reserve, so it changes both training load and Charge drain.
* **Sex:** selects the TRIMP curve.
* **Weight and height:** stored for future energy-expenditure work; not used yet.
* **Resting HR (daytime):** median of the strap's daily resting HR over 14 days, else the median nightly lowest + 3 bpm.

## Limits

* These are wellness estimates, not medical advice. The alert wording says so ("This is a wellness estimate, not a medical assessment.").
* HRV here is the strap's own RMSSD samples; we don't have beat-to-beat data. Readiness accuracy depends on the strap being worn every night.
* Charge constants are calibrated to typical published body-battery behaviour and the targets above, not fitted to lab data.
