# Healthspan age (proposal)

An estimate of how old your habits look, built only from data OpenStrap already has. **Status: proposal, not scheduled.** [evidence-review.md](evidence-review.md) dropped biological age on 2026-10-18 for three reasons; this document answers each one and lists what is still open. That section of evidence-review.md is now "Healthspan age: proposed".

OpenStrap is a wellness app. This is an estimate for personal insight, not a medical measurement.

## Contents

1. [The formula](#1-the-formula)
2. [Inputs and references](#2-inputs-and-references)
3. [The six terms](#3-the-six-terms)
4. [Pace of aging](#4-pace-of-aging)
5. [Uncertainty and display](#5-uncertainty-and-display)
6. [Withholding rules](#6-withholding-rules)
7. [Worked examples](#7-worked-examples)
8. [The three objections in evidence-review.md](#8-the-three-objections-in-evidence-reviewmd)
9. [Known problems](#9-known-problems)
10. [Sources](#10-sources)

---

## 1. The formula

```
HealthspanAge = Age + clamp(S · (c_fit + c_sri + c_sleep + c_strength), −5, +5)

c_fit = clamp(c_rhr + c_steps + c_vo2, −5, +5)      fitness group: shared limit (§3.7)
cᵢ    = ln HRᵢ(xᵢ vs refᵢ) / G                       years, one term per metric, each with its own limits (§3)
G     = 0.09165                                      PhenoAge's Gompertz age slope (risk → years)
S     = 0.6                                          shrinkage, because the terms weren't adjusted for each other
```

* **Each term is scored against the population median** for your age and sex, where the source gives one (§2 lists them). Someone at the median on every term reads their own age; better habits read younger. That is what "age" means: the death rate at any age is the whole population's. PhenoAge works the same way. (WHOOP scores against targets instead, so meeting every target reads as your own age and a typical user reads older.)
* **The typical user reads about +0.3 y, not 0** (simulated 35-year-old men, §9). Short sleep can only add years, the SRI curve adds more below the median than it takes off above, and the under-60 step term runs from +1.5 to only −0.86.
* **Targets** (8,000–10,000 steps, 40 min/week of strength, at least 6.5 h measured sleep) appear in the coaching (§5). They don't set the age.
* **Windows:** 180 days for every input, except VO2max (90 days). Pace uses 30 days.
* **A missing term** (for example no VO2max) is left out, which is the same as assuming the median.
* **The shown offset is capped at ±5 y.** Li et al. 2018 found HR 0.26 for 5 vs 0 low-risk factors (smoking, BMI, alcohol, diet, exercise), about 14.7 y on this scale. ±5 y allows a 10 y span, two-thirds of that, and OpenStrap scores none of smoking, BMI, alcohol or diet. It is a loose ceiling, not a calibration.
* **Design choices:** S = 0.6, the ±5 y cap, the ±5 y fitness-group limit, the per-term limits, the 0.5 weight on VO2max, and how each curve is drawn between published points.

## 2. Inputs and references

| Input | Source in OpenStrap | Window rule | Reference |
|---|---|---|---|
| Age | `UserProfile.age()` (birth year only, so ±1 y) | 20–79 | — |
| Sex | `UserProfile.sex` | required | — |
| Resting HR | `night_metrics.rhrLowest30`: lowest 30-min mean during sleep (`Recovery.Night.restingHr`) | median of nightly values | NHANES seated pulse P50 by sex, joined linearly between age-band midpoints (Ostchega 2011), after adding 10 bpm to the sleeping value |
| Steps | the strap's daily counter, else the sum of `activity_minute.steps` (calculations.md §10) | mean of valid days | Paluch 2022 median of the cohort medians: 7,803 (< 60 y), 5,649 (≥ 60 y), blended from 55 to 65 |
| Sleep regularity | `SleepRegularity.sri()` over 8 grid days (7 day-pairs) | mean of valid weekly SRIs | Windred 2024 UK Biobank median, 81.0 |
| Sleep duration | main sleeps (`sleep_session.nap = false`), asleep = deep + light + REM (`RecoveryEngine.asleepMinutes`) | mean minutes per night | about 6.8 h (means: Windred 6.77 h, Yuan 6.8 h; no median reported); 6.5 h and over scores 0 |
| Strength training | `workout` rows with `sportType` 52 (Strength training) or 50 (Core training), `exerciseSeconds` | minutes per week | 0 min/week: 57.8 % of US adults do none (Bennie 2018) |
| VO2max | the strap's exercise estimate: `workout.vo2max`, else `sport_effect.vo2max` | median, ≥ 3 workouts in 90 days | `Vo2max.Friend` P50 for age and sex (lab-tested, apparently healthy adults), joined linearly between decade midpoints as `fitnessAge` does |

**Not used:** HRV (no mortality dose–response at this sampling), SpO₂, skin temperature, stress, PAI, the strap's sleep score, and the Jurca VO2max (§8).

## 3. The six terms

### 3.1 Resting HR (Established; limits ±4; fitness group)

Risk rises 9 % per 10 bpm (RR 1.09, 95 % CI 1.07–1.12), linearly from 45 bpm ([Zhang et al. 2016](https://pubmed.ncbi.nlm.nih.gov/26598376/)). The studies measured seated or lying pulse at rest.

```
seated   = RHR_sleep + 10
c_rhr    = 0.0940 · (max(seated, 45) − ref(age, sex))

ref (bpm, NHANES seated P50)     age 30   age 50   age 70     (band midpoints of 20–39, 40–59, 60–79)
  men                              69       68       67
  women                            74       71       70
joined linearly between the midpoints, flat below 30 and above 70
```

* **Why +10 bpm:** office HR is 10.4 bpm above mean night HR in 56,901 ambulatory recordings (75.4 ± 11.5 vs 65.0 ± 9.7 bpm; Böhm 2020), and about 12 bpm in 32,569 (Vinyoles 2014). OpenStrap uses the lowest 30 minutes, which is below the night mean, so +10 is the low end and the term leans slightly young (§9).
* Böhm's night HR was a cuff reading every 30 minutes, in hypertensive patients (mean age 58, 15 % on beta-blockers), with no lowest value reported. Night HR predicted death better than office HR, with risk rising above about 70 bpm at night.
* **Cross-check:** Fitbit's daily resting HR in 92,457 users (Quer 2020, read from a figure) is about 60–65 bpm for men and 64–68 for women over ages 20–80. Fitbit estimates lying-still HR just after waking, mostly from sleep data, not a minimum. The sleeping medians implied here (NHANES − 10: men 57–59, women 60–64) are a few bpm lower. That fits +10 bpm but doesn't prove it.
* NHANES excludes people with conditions or medicines that affect pulse, so its median is for fairly healthy adults.
* **No jump between age bands:** joining the band midpoints means a woman's reference falls from 74 to 71 bpm over ages 30–50, instead of on her 40th birthday (which added 0.17 shown years overnight).
* Sex is required for the reference.
* **Band:** the slope at both ends of Zhang's CI, 0.0738–0.1237 per bpm.

### 3.2 Steps (Contested; limits ±1.5 under 55, ±2 from 65; fitness group)

Against the lowest quartile, HR 0.60 (0.51–0.71), 0.55 (0.49–0.62) and 0.47 (0.39–0.57) for quartiles 2–4 ([Paluch et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9289978/)), at quartile medians 3,553 / 5,801 / 7,842 / 10,901 steps. We use the appendix's age-specific curves (Figure S2c, HR vs 3,500 steps), **read from the figure (±0.02)**:

```
HR vs 3,500 steps     3k    4k    5k    6k    7k    8k    9k    10k   12k   14k   16k
  < 60 y             1.12  0.89  0.71  0.56  0.46  0.40  0.38  0.38
  ≥ 60 y             1.12  0.89  0.71  0.61  0.56  0.54  0.52  0.50  0.47  0.44  0.41

L(x) = ln HR / G, joined linearly between the points, flat outside them

c_young = clamp(L_<60(steps) − L_<60(7,803), −1.5, +1.5)
c_old   = clamp(L_≥60(steps) − L_≥60(5,649), −2,   +2)
w       = clamp((age − 55) / 10, 0, 1)
c_steps = (1 − w) · c_young + w · c_old
```

* **Blended from 55 to 65 (design choice).** Switching at exactly 60 made the term jump: 7,000 steps scored +1.22 y at 59 and −1.51 y at 60. Blending spreads that over ten years, about 0.27 y per year of age at 7,000 steps.

  | Steps | 55 | 57 | 59 | 60 | 61 | 63 | 65 |
  |---|---|---|---|---|---|---|---|
  | 5,000 | +1.50 | +1.41 | +1.33 | +1.29 | +1.24 | +1.16 | +1.07 |
  | 7,000 | +1.22 | +0.68 | +0.13 | −0.14 | −0.42 | −0.97 | −1.51 |
  | 9,000 | −0.86 | −1.09 | −1.32 | −1.43 | −1.54 | −1.77 | −2.00 |

* **Under 60:** the curve bottoms out at 9,000–10,000 and rises slightly after (0.40 at 12k), so the line stops at 10,000. Best value −0.86 y. The +1.5 limit binds below about 6,900 steps.
* **60 and over:** risk keeps falling past 8,000, so there's no 8,000 cap. The −2 limit binds at about 8,200 steps, the +2 limit below about 4,600.
* **Why ±1.5 under 55:** the under-60 curve is steep near its median (about 1.5 y per 1,000 steps between 7,000 and 8,000, before shrinkage) and rests on thin data (7 studies, 359 deaths). Its CI is wide (about 0.21–0.70 at 9,000), and under-60 Q4 (0.60) wasn't lower than Q3 (0.51); the age difference at Q4 had p = 0.08. The limit caps how far a low or miscounted step total can push the estimate. It doesn't flatten the slope between about 6,900 and 7,800 steps.
* The limits (not ±4) are design choices: strap step counts aren't validated for chest or arm wear (evidence-review.md, "Steps and distance").
* A valid day has ≥ 10 h worn (design choice).
* **Band:** ±50 % of the term after its limits are applied (design choice), then clamped to the limits. The published CIs are all against the lowest quartile, so they can't bound a contrast against the median: the two HRs share a reference group and their covariance isn't published.

### 3.3 Sleep regularity (Probable; limits −0.57 / +2.71)

All-cause mortality by SRI quintile in UK Biobank, fully adjusted (n = 46,721), against the bottom quintile ([Windred et al. 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC10782501/), supplement Table S2.4):

| Model | Q2 | Q3 | Q4 | Q5 |
|---|---|---|---|---|
| SRI alone | 0.80 (0.69–0.93) | 0.75 (0.64–0.88) | 0.72 (0.61–0.84) | 0.70 (0.59–0.83) |
| **SRI + sleep-duration quintiles (used)** | 0.83 (0.71–0.96) | 0.78 (0.66–0.92) | 0.75 (0.64–0.89) | 0.74 (0.62–0.89) |

Median SRI 81.0 (IQR 73.8–86.3). Quintile cuts 71.65 / 78.56 / 83.17 / 87.32.

```
c_sri: ln HR re-based to the middle quintile, placed at quintile midpoints, joined linearly, flat outside

  SRI     68.2     75.1     80.9     85.2     89.4
  c_sri  +2.71    +0.68     0       −0.43    −0.57
  quint.   Q1       Q2       Q3       Q4       Q5
```

* **Why the model with duration:** the score also has a sleep-duration term (§3.4), so using SRI ratios adjusted for duration avoids counting sleep twice. The adjustment barely weakens SRI, and SRI and duration correlate weakly (adjusted R² 0.124, Table S2.3).
* Q3's midpoint, 80.9, stands in for the median (81.0 scores −0.01 y). Q1 and Q5 have no outer edge, so their points sit half a neighbouring quintile beyond the cut (design choice). The paper gives quintiles only, no continuous curve.
* The curve has the plateau built in: most of the risk is in the bottom fifth, and there's little benefit above the median. It replaces an earlier linear 0.237 y per point, taken from a 2022 conference abstract with no CI.
* The fully adjusted model is the cautious choice. The minimal model (age, sex, ethnicity) gives larger effects: SRI alone 0.72 / 0.62 / 0.54 / 0.52.
* Windred's SRI: GGIR sleep/wake from wrist accelerometry, each epoch compared with the one 24 h later, at least 120 h of valid pairs.
* SRI is the main sleep term: it's objective (accelerometry, not self-report) and predicted mortality better than duration.
* **Band:** ±50 % of the term after its limits are applied (design choice), then clamped to the limits, for the same reason as steps.
* **Reference problem:** 81 comes from wrist actigraphy. OpenStrap's SRI comes from strap sleep staging plus worn minutes, with GGIR-style valid pairs. Two open-source tools disagreed on the same data ([Sleep 2025](https://academic.oup.com/sleep/article/8273793)). See §9.

### 3.4 Short sleep only (Probable; limits 0 / +4)

All-cause mortality by device-measured sleep in 62,214 UK Biobank participants with a wrist accelerometer, against 7–7.9 h ([Yuan et al. 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11106264/), Supplementary Fig. 26; HRs printed, CIs measured off the plot):

| Sleep | < 5 h | 5–5.9 h | 6–6.9 h | 7–7.9 h | 8–8.9 h | 9–9.9 h | 10+ h |
|---|---|---|---|---|---|---|---|
| HR | 2.48 (1.89–3.25) | 1.30 (1.06–1.61) | 1.07 (0.96–1.21) | 1.00 | 1.02 (0.92–1.13) | 1.02 (0.82–1.35) | 1.01 (0.62–1.72) |
| Deaths | 53 | 93 | 354 | 662 | 379 | 84 | 17 |

```
c_sleep: ln HR re-based to 6–6.9 h (where the central value sits), placed at band midpoints, joined linearly

  asleep (min)    270      330      390+
  c_sleep        +9.17    +2.12      0          then limited to 0 … +4 (the +4 limit binds below 315 min)
```

* **Penalty only.** Above 6.5 h the curve is flat: 7–7.9 h is only 0.74 y below 6–6.9 h, and the 6–6.9 h CI includes 1.00. Device data shows no long-sleep risk, and the self-reported long-sleep risk is likely caused by illness, not the other way round ([Yin et al. 2017](https://pmc.ncbi.nlm.nih.gov/articles/PMC5634263)).
* The +4 limit is a design choice: the < 5 h group is small (53 deaths) and probably includes people who were already ill.
* About 6.8 h of measured sleep (the studies' mean) scores 0.
* **Band:** ±50 % of the term after its limits are applied (design choice), then clamped to the limits. The figure's CIs are floated (the 7–7.9 h reference has its own interval), so they aren't contrasts against 7–7.9 h and can't be re-based.
* This replaces Cappuccio 2010, whose ratios came from self-report. Self-report runs 48–66 min above measured sleep (Lauderdale 2008, Jackson 2018), most of all for short sleepers.
* **Yuan's device vs polysomnography:** the Bland–Altman plots are "true − predicted", so the positive bias (+9.9 min internal, +48.2 min external in the text; +8.9 and +34.7 on the plots) means the device reads *below* polysomnography. The paper says it underestimates short sleep and overestimates long sleep, so Yuan's short sleepers probably slept a little more than measured. How strap minutes compare is unknown (§9). A correction (2024-07-01) fixed training-data cleaning; the core findings are unaffected.

### 3.5 Strength training (Probable; limits −2.03 / 0)

Any muscle-strengthening activity vs none: RR 0.85 (0.79–0.93) for all-cause mortality. On the dose–response curve, risk is lowest at 40 min/week (RR 0.83, 0.79–0.86) and stays below 1.00 up to about 140 min/week ([Momma et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9209691/)). The curve exists only as a figure (Figure 4, all-cause panel), so the values are **read from the figure (±0.01)**, except 40 min, which is in the text:

```
min/week     10    20    30    40    50    60    70     80    90     100   110   120   130   140
RR          0.94  0.89  0.85  0.83  0.83  0.84  0.845  0.85  0.865  0.89  0.92  0.95  0.97  1.00
c (y)      −0.68 −1.27 −1.77 −2.03 −2.03 −1.90 −1.84  −1.77 −1.58  −1.27 −0.91 −0.56 −0.33   0

c_strength = ln RR(minutesPerWeek) / G, joined linearly; 0 at 0 min and from 140 min
```

* **Credit only:** the reference is 0 min/week, the population median, so this term can only take years off.
* No opt-in is needed. An unrecorded gym session earns no credit, but it can't add years either.
* Not in the fitness group: strength is a separate exposure in Momma, and none of the fitness terms measures it.
* **Above 140 min/week** the curve rises above 1.00, and from about 160 min its CI no longer includes 1.00 (read from the figure). This isn't scored: the term stays at 0 rather than adding years on the strength of one figure (§9).
* **Band:** the curve's own CI at your minutes, read from the figure (for example 0.82–0.88 at 30 min, 0.79–0.86 at 40, 0.85–0.93 at 100), clamped to the limits. The reference is the curve's own reference, so the CI applies directly.

### 3.6 VO2max (Established for the ratio, Contested for the input; optional; limits ±2; fitness group)

Each 1 MET higher: RR 0.87 (0.84–0.90) for all-cause mortality ([Kodama et al. 2009](https://bioblast.at/index.php/Kodama_2009_JAMA)).

```
c_vo2 = 0.5 · (−1.520) · (VO2 − VO2_ref(age, sex)) / 3.5
```

* Only the strap's **exercise-based** estimate. Never Jurca (§8).
* Half weight because wearable estimates have limits of agreement around ±10 ml/kg/min (evidence-review.md, Molina-Garcia 2022). At full weight that's ±4.3 y of noise; at half weight, ±2.2 y.
* **Band:** the slope at both ends of Kodama's CI (RR 0.84–0.90).

### 3.7 Fitness group (design choice; limit ±5)

```
c_fit = clamp(c_rhr + c_steps + c_vo2, −5, +5)
```

* Resting HR, steps and VO2max all track cardiorespiratory fitness, and the strap's VO2max is itself worked out from heart rate. None of the three source ratios is adjusted for the others (Zhang 2016 not for fitness, Kodama not for activity, Paluch not for fitness), so adding them up counts fitness more than once.
* **An outlier guard, not the overlap fix.** The shared ±5 y limit (±3 y after S) binds for about 1–5 % of users in a simulated population of 35-year-old men (§9): only those far from the median on several fitness terms at once (Example C). For everyone else, the overlap is handled only by S = 0.6, which also shrinks the sleep and strength terms that don't overlap.
* On their own, the three terms could reach −5.1 y (a man under 55) to −6.4 y (a woman of 70+) and +7.5 to +8 y. Under 55 the −5 side almost never binds, so in practice the limit acts on the penalty side.
* **Why not tighter:** a ±3 limit bound for 19–36 % of users, so it would have been a hidden extra shrink on the fitness terms. That would fit the evidence better: adding plain activity volume removes about two-thirds to four-fifths of activity clocks' predictive power (Pyrkov 2018), so the overlap is probably large. ±5 was chosen to keep the fitness terms' spread. The overlap is a known weakness (§9).
* **Band:** the summed term bands, clamped to ±5.

## 4. Pace of aging

WHOOP projects the last 30 days over five months, on a scale of −1x to 3x.

```
offset_w = clamp(S · (c_fit + c_sri + c_sleep + c_strength)(window w), −5, +5)
Δ        = offset_30 − offset_180
σ        = SD of offset over the earlier non-overlapping 30-day blocks in the 180 days (days 31–60, 61–90 …)
Pace     = 1                                       if |Δ| < max(2σ, 0.25 y)
         = clamp(1 + Δ / (5/12), −1, 3)            otherwise
```

* 1x = in step with the calendar; below 1 = habits improving; above 1 = slipping.
* **Why the noise gate:** 0.42 y of Δ moves Pace by a whole 1x. That's a few SRI points near the bottom quintile, or about 7 bpm of 30-day resting HR. Without the gate, Pace mostly tracks noise.
* offset_180 includes the last 30 days, so Δ understates a real change by about a sixth.
* The 5/12 divisor follows WHOOP's five-month projection (design choice), as do the 2σ threshold and the 0.25 y floor. σ comes from your own history, so users with steady habits see smaller real changes.
* **Show** to the nearest 0.5x.
* Needs ≥ 21 valid days and ≥ 2 valid weekly SRIs in the 30-day window, and ≥ 3 earlier blocks meeting the same rule for σ. Otherwise Pace is withheld.

## 5. Uncertainty and display

* **Band:** work out every term at both ends of its band (§3), clamp each to that term's limits and the fitness group to ±5, add up the low ends and the high ends separately, then apply S and the ±5 y cap. Widen the result to at least ±2 y around the estimate, because the bands don't include uncertainty in S, measurement error or the reference problems in §9.
* **In practice the shown range is the estimate ±2 y**, in whole years: the summed bands come out narrower than ±2 y in all three worked examples. It's a display convention, not a confidence interval, and the app should never call it one.
* **Whole years only**, never a decimal (as for VO2max).
* **Per-term breakdown:** show each term as "adds about 1 year" or "takes off about 2 years", so the user sees what to change. Next to each, show the target (8,000–10,000 steps, 40 min/week of strength, at least 6.5 h measured sleep) as coaching, separate from the median the age is scored against. When the fitness limit binds, say so ("fitness terms capped").
* **Wording:** no mortality language, as for fitness age. The name is still open: "healthspan estimate", "habits age".
* **Recompute** weekly (Sunday), like WHOOP and like VO2max.
* **Where it shows (decided 2026-10-20, revised the same day):**
  * **Today tab: its own glow card in the list**, after Recovery and What changed and before Heart rate. An earlier version of this decision made the top card a swipeable stack of Recovery, Healthspan and Sleep. It was dropped: the Today tab already swipes between tabs, so a swipeable card inside it fights that gesture; a card behind a swipe is easy to miss; and a number that changes weekly doesn't need the hero slot Recovery has each morning. The card shows:
    * "Weekly" in the header;
    * the range in the dot-matrix numerals ("30-34"; the font has no en dash), with pace ("Steady") beside it;
    * "3 years younger" ("older" when it applies) in plain text, with no colour: the dot on the scale carries the good-or-not signal;
    * an age scale of your age ±7 (28–42 for a 35-year-old), the widest the range can reach: a dotted track, a solid white line over the range with a soft halo, a green dot at its middle (amber if the estimate is older than you) and a tick at your age ("you're 35");
    * one row naming the biggest lever ("Helping most: strength training") with a chevron; the whole card opens the Healthspan screen.
  * **Healthspan screen**, opened from the card: the range, "3 years younger" and the same age scale in a glow card; pace of aging on a −1x to 3x scale with one line of explanation; "What moves it", one row per term against the typical person your age, on one shared scale (−1½ y to +1½ y, a bar at either end meaning that or more): a green line left of the middle for years taken off, amber right for years added, ending in a dot, with matching wording; and a folded "How it works" with the not-a-medical-measurement line.
  * Not on the Activity tab, which keeps VO2max and fitness age.

## 6. Withholding rules

Checked in this order (design choices, mirroring `Vo2max.Withheld`):

1. Age outside 20–79, or no sex.
2. Beta-blocker or pregnancy flag: resting HR doesn't reflect fitness then.
3. Fewer than 90 nights of resting HR in 180 days.
4. Fewer than 90 valid step days in 180 days.
5. Fewer than 8 valid weekly SRIs in 180 days.
6. Fewer than 90 main sleeps in 180 days.

**Early estimate:** after 30 days, with the same rules scaled to the window, shown with at least ±4 y and labelled "early". VO2max and strength are never required.

## 7. Worked examples

Use all three as unit tests. Band ends are clamped to each term's limits.

**A. Close to average.** Man, 35. Over 180 days: sleeping RHR 58, 7,000 steps/day, SRI 75, 400 min asleep, no strength sessions, strap VO2max 44 (FRIEND P50 for 30–39: 39.7).

| Term | cᵢ (y) | Band |
|---|---|---|
| Resting HR (68 seated vs 68.75) | −0.07 | −0.09 … −0.06 |
| Steps (vs 7,803) | +1.22 | +0.61 … +1.50 |
| VO2max | −0.93 | −1.17 … −0.71 |
| *Fitness group* | *+0.22* | *−0.65 … +0.74* |
| SRI | +0.71 | +0.35 … +1.06 |
| Short sleep | 0 | 0 |
| Strength | 0 | 0 |
| **Σ** | **+0.93** | **−0.30 … +1.80** |

`35 + 0.6 × 0.93 = 35.6`; band 34.8–36.1, widened to ±2 → shown as **about 34–38**. Biggest levers: steps (7,000 → 9,000 would move the term from +1.22 to −0.86 y) and strength training (up to −2.0 y).

**B. Healthy.** Man, 35: sleeping RHR 55, 10,000 steps/day, SRI 87, 450 min asleep, 40 min/week of strength, strap VO2max 46.4 (FRIEND P70).

| Term | cᵢ (y) | Band |
|---|---|---|
| Resting HR (65 seated vs 68.75) | −0.35 | −0.46 … −0.28 |
| Steps | −0.86 | −1.29 … −0.43 |
| VO2max | −1.45 | −1.82 … −1.10 |
| *Fitness group* | *−2.67* | *−3.57 … −1.81* |
| SRI | −0.49 | −0.57 … −0.25 |
| Short sleep | 0 | 0 |
| Strength | −2.03 | −2.03 … −1.65 |
| **Σ** | **−5.19** | **−6.18 … −3.70** |

`35 + 0.6 × (−5.19) = 31.9`; band 31.3–32.8, widened to ±2 → shown as **about 30–34**.

**C. Unfit (fitness limit binds).** Man, 35: sleeping RHR 80, 4,000 steps/day, SRI 75, 400 min asleep, no strength sessions, strap VO2max 30.

| Term | cᵢ (y) | Band |
|---|---|---|
| Resting HR (90 seated vs 68.75) | +2.00 | +1.57 … +2.63 |
| Steps (vs 7,803) | +1.50 (limit) | +1.50 … +1.50 |
| VO2max | +2.00 (limit) | +1.59 … +2.00 |
| *Fitness group* | *+5.50 → +5.00* | *+4.66 … +5.00* |
| SRI | +0.71 | +0.35 … +1.06 |
| Short sleep | 0 | 0 |
| Strength | 0 | 0 |
| **Σ** | **+5.71** | **+5.02 … +6.06** |

`35 + 0.6 × 5.71 = 38.4`; band 38.0–38.6, widened to ±2 → shown as **about 36–40**. Biggest levers (change in the shown estimate): 40 min/week of strength −1.2 y, 10,000 steps −1.1 y, median steps (7,803) −0.6 y, sleeping RHR 10 bpm lower only −0.3 y. Fitness gains count for less here because the limit absorbs the first 0.5 y of them.

A user exactly at every median gets 35.0.

## 8. The three objections in evidence-review.md

| Objection | What this proposal does | Resolved? |
|---|---|---|
| Summing hazard ratios that weren't adjusted for each other gives a falsely precise number | Sleep terms use mutually adjusted ratios (§3.3). The three fitness terms share a ±5 y outlier limit (§3.7), so for most users their overlap is handled only by shrinkage S = 0.6. Per-term limits, a ±5 y cap, a ±2 y minimum display range, whole years only | **Partly.** S and the fitness limit are design choices; there's no outcome data to fit them |
| The sleep ratios come from self-report, and the long-sleep risk is likely reverse causation | Both sleep terms now come from accelerometer studies (Windred 2024, Yuan 2024). No long-sleep term | **Mostly.** Strap sleep isn't the same measurement as wrist accelerometry (§9) |
| Jurca's SEE adds ±2.3–2.7 "years" (as originally stated; an SEE of 1.45–1.97 METs actually works out to ±2.2–3.0 y) | Jurca isn't used; its RHR term would also count resting HR twice. Only the strap's exercise estimate, at half weight, optional, inside the fitness limit | **Mostly.** The strap estimate still carries about ±2.2 y of noise at half weight, and there's no VO2max term for users without workouts |

## 9. Known problems

* **S isn't fitted.** That would need outcome data OpenStrap will never have, so it stays a design choice. Evidence on how much the terms overlap is partial:
  * Sleep vs sleep: adding sleep duration barely weakens SRI (Windred, §3.3).
  * Sleep vs activity: in 6,673 NHANES adults with wrist accelerometers (Zhang Y 2024), an irregular sleep pattern carried HR 1.44 (1.09–1.90) before and 1.42 (1.01–1.99) after adjusting for activity volume and sleep duration. That pattern is a k-means cluster, not SRI, so it isn't scored.
  * Within fitness (resting HR, steps, VO2max): no mutually adjusted source, and the activity-clock evidence (Pyrkov 2018) suggests a large overlap. The ±5 y shared limit (§3.7) only catches outliers, so for most users this overlap is handled by S alone. A lower S for the fitness group, or a tighter limit, would address it; neither is chosen (design choice).
* **Strap values are taken as they are (decision).** No side-by-side check against a wrist accelerometer is planned. The strap's sleep staging, asleep minutes, steps and lowest-30-minute HR are scored directly against references from other devices. The ±2 y minimum range, whole-year display and the ±1.5 / ±2 step limits absorb that offset.
* **Strap vs wrist accelerometry.** The SRI median (81) and the sleep-duration studies use wrist accelerometers and GGIR-style processing. OpenStrap's SRI and asleep minutes come from strap sleep staging, so both sleep terms may be off by an unknown amount. Option: once enough users opt in to share anonymised SRI and sleep minutes, re-centre on OpenStrap's own medians.
* **Sleeping vs seated RHR.** +10 bpm is the night-mean gap; the lowest 30 minutes is lower still, so the term leans young. No study compares a wearable's overnight minimum with clinic seated HR directly. Fitbit's population figures (Quer 2020) are the closest wearable reference, but they're read from a figure and use a different, proprietary resting HR.
* **Curves read off figures.** The age-specific step curves (Paluch Figure S2c) and the strength curve (Momma Figure 4) are read off published figures, to about ±0.01–0.02. Replace them if the authors publish tables.
* **Strength above 140 min/week** carries higher risk on Momma's curve (CI above 1.00 from about 160 min). It isn't scored: heavy lifters get no credit, but no penalty.
* **Strap steps aren't validated**, hence the ±1.5 / ±2 limits.
* **Mixed references.** Each reference comes from a different population and statistic: NHANES P50 (US, healthy reference sample), Paluch medians of cohort medians (15 cohorts), UK Biobank median SRI and mean sleep (healthier than the UK average), FRIEND P50 (US, lab-tested, apparently healthy), BRFSS (US). An average OpenStrap user won't land exactly on 0.
* **Windows differ from the studies.** The studies used about one week of wear; OpenStrap scores 180-day averages. A 180-day value varies less than a one-week value, so OpenStrap users will cluster nearer the middle than the studies' quintiles and bands suggest.
* **The typical user reads slightly older.** A user at every median reads their own age, but the population doesn't centre on 0. In a simulated population of 35-year-old men the median shown offset is +0.3 y. Three terms push it older: short sleep (penalty-only; mean +0.32 y), SRI (bigger penalty below the median than credit above; +0.28 y) and steps (the under-60 term runs +1.5 to −0.86; +0.17 y). Strength pulls the other way (credit-only; −0.39 y); without it the median would be +0.7 y.
* **Comparison with wearable ageing clocks validated against mortality.** A 200,000-draw simulation of 35-year-old men (inputs from this document's own sources; fitness inputs independent, or correlated at ρ = 0.4) gives a shown offset with SD 1.7–1.9 y, 5th–95th percentile −2.3 to +3.2 y (independent) or −2.7 to +3.6 y (ρ = 0.4), and median +0.3 y. The fitness limit binds for 1–5 % and the ±5 y cap for under 1 %. Published clocks:
  * **CosinorAge** (Shim et al. 2024; UK Biobank wrist accelerometry, built like PhenoAge): HR 1.08 (1.06–1.11) per year of age gap in the UK Biobank validation set (868 deaths), 1.12 (1.05–1.18) in NHANES (199 deaths). The paper doesn't report how widely the gap spreads.
  * **Pyrkov et al. 2018** (NHANES hip accelerometry, ages 18–85, 701 deaths): HR up to 1.45–1.53 per SD of gap for the two best models (1.05–1.31 for the others), about 4.1–4.6 y per SD on this scale. Adding activity volume removes most of it (HR 1.07–1.15).
  * Clocks trained to predict age rather than death (PPG: Nie 2025; sleep studies: Brink-Kjaer 2022) carry only HR 1.02–1.03 per year.
  * **What it means:** this formula spreads users less than the clocks validated against death. Most of those come from older cohorts that include sick people (CosinorAge median age 55–58, Nie mean 56–61, Brink-Kjaer 66; Pyrkov's NHANES sample spans 18–85), so a narrower spread for healthy younger users is expected, but probably not this much narrower. One shown year here implies HR exp(G/S) ≈ 1.17, above the clocks' 1.08–1.12 and calendar age's 1.10. That's the cost of S = 0.6. Nothing in this literature measures overlap across activity, fitness and sleep well enough to fit S; anything from 0.6 to 0.8 is defensible, so S stays 0.6.
  * The ±5 y cap binds for under 1 % of users. It's a guard against extreme inputs, not something that shapes typical results.
* **Paluch's quartiles were study-specific.** Each cohort split its own sample, and no pooled cut-points exist (checked in the appendix). The age-specific HRs use whole-cohort quartiles, not age-specific ones.
* **Missing terms.** A missing term is treated as the median. The range should widen when a term is missing (open).
* **Cohort ages.** Most of the hazard ratios come from middle-aged and older cohorts, and the Gompertz conversion assumes the ratios hold at every age. The estimate is least supported for users in their 20s.
* **No MVPA term.** WHOOP scores heart-rate zone time, and OpenStrap already computes MVPA-equivalent minutes (`Activity.mvpa`), but a sourced dose–response hazard ratio is needed before adding it. It would join the fitness group.
* **Not read in full text:** Kodama 2009, Vinyoles 2014, Bennie 2018 and Jackson 2018 (abstracts only).

## 10. Sources

* Zhang D et al. Resting heart rate and all-cause and cardiovascular mortality in the general population: a meta-analysis. CMAJ 2016;188(3):E53-63. [PubMed](https://pubmed.ncbi.nlm.nih.gov/26598376/)
* Ostchega Y et al. Resting pulse rate reference data for children, adolescents, and adults: United States, 1999–2008. National Health Statistics Reports no. 41, 2011. [Archived PDF](https://web.archive.org/web/2020/https://www.cdc.gov/nchs/data/nhsr/nhsr041.pdf)
* Böhm M et al. Association of clinic and ambulatory heart rate parameters with mortality in hypertension. J Hypertens 2020;38(12):2416-26 (Spanish ABPM Registry; office vs night HR). [DOI](https://doi.org/10.1097/HJH.0000000000002565)
* Quer G et al. Inter- and intraindividual variability in daily resting heart rate and its associations with age, sex, sleep, BMI, and time of year: retrospective, longitudinal cohort study of 92,457 adults. PLoS One 2020;15(2):e0227709. [PMC7001906](https://pmc.ncbi.nlm.nih.gov/articles/PMC7001906/)
* Vinyoles et al. J Hypertens 2014 (Spanish ABPM Registry; office vs night HR; abstract only).
* Paluch AE et al. Daily steps and all-cause mortality: a meta-analysis of 15 international cohorts. Lancet Public Health 2022;7(3):e219-28. [PMC9289978](https://pmc.ncbi.nlm.nih.gov/articles/PMC9289978/) (appendix Figures S1c, S2a, S2c used)
* Zhang Y et al. Diurnal patterns of accelerometer-measured physical activity and sleep and risk of all-cause mortality: a follow-up of the National Health and Nutrition Examination Surveys (NHANES). Int J Behav Nutr Phys Act 2024;21:120. [DOI](https://doi.org/10.1186/s12966-024-01673-9) (§9 only)
* Windred DP et al. Sleep regularity is a stronger predictor of mortality risk than sleep duration. Sleep 2024;47(1):zsad253. [PMC10782501](https://pmc.ncbi.nlm.nih.gov/articles/PMC10782501/) (supplement Tables S2.3, S2.4 used)
* Yuan et al. npj Digit Med 2024;7:86 (wrist-accelerometer sleep duration, efficiency and mortality in UK Biobank). [PMC11106264](https://pmc.ncbi.nlm.nih.gov/articles/PMC11106264/) (Supplementary Figs 5, 6, 26 used; correction 2024-07-01 checked)
* Yin et al. 2017, sleep duration and mortality meta-analysis (long-sleep reverse causation). [PMC5634263](https://pmc.ncbi.nlm.nih.gov/articles/PMC5634263)
* Lauderdale DS et al. Self-reported and measured sleep duration: how similar are they? Epidemiology 2008;19(6):838-45. [PMC2785092](https://pmc.ncbi.nlm.nih.gov/articles/PMC2785092/)
* Jackson et al. Sleep 2018;41(6) (self-reported vs actigraphy and polysomnography sleep duration in MESA; abstract only). [PMC5995218](https://pmc.ncbi.nlm.nih.gov/articles/PMC5995218/)
* Momma H et al. Muscle-strengthening activities are associated with lower risk and mortality in major non-communicable diseases. Br J Sports Med 2022;56(13):755-63. [PMC9209691](https://pmc.ncbi.nlm.nih.gov/articles/PMC9209691/)
* Bennie JA et al. Am J Prev Med 2018;55(6):864 (muscle-strengthening exercise prevalence, BRFSS 2015, n = 397,423; abstract only). [DOI](https://doi.org/10.1016/j.amepre.2018.07.022)
* Kodama S et al. Cardiorespiratory fitness as a quantitative predictor of all-cause mortality and cardiovascular events. JAMA 2009;301(19):2024-35. [Summary](https://bioblast.at/index.php/Kodama_2009_JAMA)
* Levine ME et al. 2018 / Liu Z et al. 2018, PhenoAge Gompertz transformation (G = 0.09165, as printed in the correction). [Correction, PLOS Med 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6388911)
* Kaminsky LA et al. FRIEND 2022 reference standards. Mayo Clin Proc 2022;97(2):285-93 (already in `Vo2max.Friend`).
* Li Y et al. Impact of healthy lifestyle factors on life expectancies in the US population. Circulation 2018;138(4):345-55. [PMC6207481](https://pmc.ncbi.nlm.nih.gov/articles/PMC6207481/)
* Shim, Fleisch, Barata 2024. npj Digit Med (CosinorAge: wrist-accelerometer biological age, UK Biobank and NHANES, validated against mortality). [PMC11150246](https://pmc.ncbi.nlm.nih.gov/articles/PMC11150246/) (§9 only)
* Pyrkov TV et al. 2018. Sci Rep (activity-based biological age from NHANES accelerometry, validated against mortality). [PMC5980076](https://pmc.ncbi.nlm.nih.gov/articles/PMC5980076/) (§3.7, §9)
* Nie et al. 2025. Commun Med (PPG-derived age in UK Biobank). [PMC12630966](https://pmc.ncbi.nlm.nih.gov/articles/PMC12630966/) (§9 only)
* Brink-Kjaer A et al. 2022. npj Digit Med (sleep-study-derived age and mortality). [PMC9307657](https://pmc.ncbi.nlm.nih.gov/articles/PMC9307657/) (§9 only)
* WHOOP Healthspan (target-based WHOOP Age, 30-day Pace, −1x to 3x). [WHOOP](https://www.whoop.com//thelocker/how-healthspan-on-whoop-helps-you-optimize-longevity)
