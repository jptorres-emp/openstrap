# Evidence review

What the published evidence says about each part of OpenStrap's health model, and what we will change. The current model is in [recovery.md](recovery.md); constant names below match `Recovery.kt`.

**Status: implementation steps 1–12 done on branch `evidence-review` (2026-10-18), including the FRIEND 2022 band and fitness age (Kaminsky 2022 Table 3; bands at the reported 30th/50th/70th/90th percentiles); [recovery.md](recovery.md) describes the model as built.** Not done: step 13 (personal weights, needs ≥ 6 weeks of check-ins), VO2max phase 2 (phone GPS), the optional personal sleep need, and BioCharge logging (not readable from the strap). The new weights have not been checked against a real-data backtest yet. Constants marked for review are settled at the strap calibration review on **2026-10-30** ([readiness-calibration.md](readiness-calibration.md)).

## The short version

* The evidence is in the signals, not in any composite score. A 2025 review of 14 composite scores from 10 manufacturers found no disclosed formulas and little validation ([Doherty et al. 2025](https://www.degruyterbrill.com/document/doi/10.1515/teb-2025-0001/html)).
* So every weight and curve in Readiness, Strain and Charge is a design choice. What we test them against matters more than the formula.
* Readiness is "how far last night sat from your own normal", not a predictor. Even HRV-guided training, the best-tested use of nightly HRV, shows small effects: ES 0.42 in Vesterinen 2016 ([JYX](https://jyx.jyu.fi/handle/123456789/50625)) and no significant group difference in Javaloyes 2019 ([UMH](https://dspace.umh.es/handle/11000/30650?locale=en)).
* The first job is the backtest harness and a morning check-in, so the 2026-10-30 review has something to decide with.

Grades: **Established** = consistent independent studies or consensus; **Probable** = one or two decent studies, or strong reasoning; **Contested** = conflicting or vendor-only evidence; **Design choice** = no evidence either way. Size: **S** = constants or a small function, **M** = new state or computation, **L** = new subsystem.

| Area | Today | Decision | Evidence | Size |
|---|---|---|---|---|
| Readiness weights | 11 contributors, HRV ≈ 18 % effective | Autonomic block (HRV + RHR) 52 %; withhold without HRV or RHR | Design choice (weights), Probable (structure) | M |
| HRV input | ln mean RMSSD, 0.6 night + 0.4 week | Gated samples, 0.4 night + 0.6 week, normal-range band | Established (ln, rolling), Probable (blend) | S |
| Resting HR input | lowest 30-min average | Keep; also log sleep mean and 10th percentile | Probable | S |
| Baselines | mean/SD, min 4 nights | Median + 1.4826·MAD, show at 7, calibrated at 28 | Probable | S |
| Strap HRV/RHR | stored, unused | Placeholder, flagged fallback, QA check | Probable | S |
| Sleep score | duration 50 / efficiency 25 / stages 25 | Duration 70 / continuity 30; drop stage target | Established (staging weak) | S |
| Sleep need | 8 h flat | 480 min (18–64), 450 min (≥ 65), user override | Established | S |
| Sleep debt | half-life 4, repay ≤ 1 h | Half-life 7, credit min(0.5·surplus, 60 min) | Established direction, Contested formula | S |
| Sleep regularity | none | SRI, ≥ 120 h valid pairs | Probable | M |
| Body-signal alerts | single night, ≥ 2 of 5 signals | Persistence rule anchored on resting HR, distinct signal families | Probable | M |
| Training load | Banister on HRR ≥ 0.20 | Keep; 3-of-5-minute filter, 14-day daytime RHR | Probable | S |
| ACWR | 5 % of Readiness | Out of Readiness; descriptive, labelled contested | Established (against scoring) | S |
| Strain 0–21 | none | 21·(1 − e^(−TRIMP/180)) plus "vs your usual" | Design choice | S |
| Charge and stress | Firstbeat-style states | Keep; 3-minute stress gate, new label | Contested | S |
| Energy expenditure | none | Mifflin BMR-anchored per-minute model | Established (BMR), Contested (rest) | L |
| MVPA, steps, distance | none | Cadence 100/130, WHO ×2, step length from height | Established (thresholds), Contested (distance) | M |
| VO2max, fitness age | none | Jurca 2005 as a band; fitness age optional; no biological age | Established (Jurca), design choice (fitness age) | M |

## Readiness

### Inputs

**HRV** (Established for ln and rolling; Probable for the blend)

* A night is valid if main sleep ≥ 3 h, ≥ 6 RMSSD samples in 5–200 ms, and ≥ 120 HR minutes in 30–220 bpm.
* `hrv_n` = ln(mean of the gated RMSSD samples during sleep). Never change this definition inside a baseline.
* `hrv_7` = mean of `hrv_n` over the last 7 nights. Needs ≥ 3 valid nights (Plews, [AUT](https://openrepository.aut.ac.nz/items/d7b41d29-e92e-4acf-90ea-ed68d84d6c73)); flag "thin" below 5 (5 of 7 nights were needed in 21,000+ wearable users, [VU Amsterdam](https://research.vu.nl/en/publications/heart-rate-variability-coefficient-of-variation-during-sleep-as-a/)).
* Score input: **0.4·z(tonight) + 0.6·z(7-night)**, flipped from today's 0.6/0.4. Nightly wearable HRV error is 6 % (Oura Gen 4) to 16 % (Polar) MAPE ([Dial et al. 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12367097/)), larger than the ~3 % smallest worthwhile change ([Buchheit 2014](https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2014.00073/full)). The Helio PPG is unvalidated.
* The single night stays as the acute alert channel.
* Also show a normal-range state: `hrv_7` against centre ± 0.5·scale gives below / within / above. This is the band the HRV-guided training trials used.

**Resting HR** (Probable)

* Keep `rhr_n` = lowest 30-minute window mean within sleep, window needing ≥ 20 valid minutes. Garmin uses the same definition.
* No study compares nightly definitions head to head. Minimum sleep HR varied by about 8 bpm within people while average sleep HR stayed stable ([J Sports Sci Med 2003](https://jssm.org/02-4-133.p_d_f)), so a lowest 5-minute bucket would be noisier.
* Also log `rhr_mean_sleep` and `rhr_p10` (10th percentile of per-minute sleep HR). The review picks the least noisy one.

### Baselines (Probable)

Illness, alcohol and artefact nights are the outliers that inflate a mean/SD baseline, so a poor stretch quickly becomes "normal" ([HRV4Training](https://www.hrv4training.com/blog2/determining-your-normal-range)). Published windows run from 28 nights ([Polar](https://support.polar.com/uk-en/nightly-recharge-recovery-measurement)) to 60 (HRV4Training); the trials used 4 weeks.

* Centre = median over the prior ≤ `BASELINE_NIGHTS` = 60 valid nights, excluding nights in Watch or Alert state.
* Scale = max(1.4826·MAD, floor). HRV uses ln values.
* Floors: ln HRV 0.05 (`HRV_LN_SD_FLOOR`), RHR 2.0 bpm, breathing 0.5 /min, skin temperature 0.15 °C, SpO2 0.5 %. RHR's floor sits above device error (MAE about 1–1.8 bpm, Dial 2025).
* Phases replace `MIN_BASELINE_NIGHTS` = 4 and `CALIBRATED_NIGHTS` = 14:

| Valid nights | HRV and RHR contributors |
|---|---|
| < 7 | withheld; strap's values shown as a placeholder |
| 7–13 | scored, labelled "learning" |
| 14–27 | scored |
| ≥ 28 | labelled "calibrated" |

### Mapping and weights

Keep the logistic curve, `100 / (1 + e^−(1.386 + 1.2·z))` (80 at z = 0, 93 at +1, 55 at −1, 27 at −2), but add a dead zone at the smallest worthwhile change:

* z_eff = sign(z)·max(0, |z| − 0.5). Changes under 0.5 SD are noise (Buchheit, Plews).
* Applies to the HRV, resting HR, breathing and temperature contributors.

New weights (design choice), renormalised over contributors present:

| Contributor | Today | New |
|---|---|---|
| HRV balance | 0.25 | 0.30 |
| Resting HR | 0.15 | 0.22 |
| Last night's sleep | 0.18 | 0.16 |
| Sleep balance | 0.10 | 0.12 |
| Body temperature | 0.10 | 0.06 |
| Breathing rate | 0.03 | 0.04 |
| Previous day's load | 0.07 | 0.04 |
| Blood oxygen | 0.05 | 0.03 |
| Recovery index | 0.07 | 0.03 |
| Breathing disturbances | 0.04 | moved to a Breathing card |
| Training load balance | 0.05 | moved to the Load screen |

* Autonomic 52 %, sleep 28 %, other body signals 13 %. Polar's only hint is that HR has "the biggest influence".
* **Withhold Readiness** unless an HRV or resting HR contributor is present. On 2026-10-16, sleep alone produced 52 against the strap's 91. Under this rule that day shows a Sleep score and the strap's placeholder instead.
* Breathing disturbances move out because they are a stable trait, so inside Readiness they only act as a fixed offset.
* Keep the red-flag cap (`RED_FLAG` = 40, readiness ≤ contributor + 30) and the alert cap of 55. Neither constant has a source; both are reasonable guards.
* Sleep was the main driver of a reconstructed Oura Readiness (R² 0.71 from Oura's own inputs; questionnaires R² 0.29, [De Bot et al. 2025](https://www.scitepress.org/PublishedPapers/2025/137331)). Bounding sleep's share keeps one short night from carrying the score. The weights are the first thing to backtest.

### The strap's own nightly HRV and resting HR (Probable)

Vendors define these differently and often don't say how (Garmin's window timing is hidden, WHOOP weights the last slow-wave stage, Polar uses the first 4 h), and those choices change agreement with ECG (Dial 2025). The strap's method is unknown and could change with firmware. A baseline that mixes two definitions measures the switch, not the body.

1. Before 7 valid own nights, show the strap's values and sub-scores, labelled "Strap's estimate". They never enter OpenStrap's score.
2. On a night where the own value fails the validity rule, use the strap's value for display and alerts, flagged "strap fallback", and keep it out of the baseline.
3. Raise a data-quality flag when own and strap differ by more than 15 % (HRV) or 3 bpm (RHR).
4. At the 2026-10-30 review, compare the night-to-night robust SD of own and strap values, and how each responds to tagged events. Switch to the strap's definitions only if they are clearly less noisy with the same response; then switch completely and rebuild the baseline.

## Sleep

### Score (Established that staging is weak)

* Drop the "deep + REM vs 45 %" target. Four-stage kappa across six devices was 0.21–0.53, deep sleep was classified correctly only 47–70 % of the time, and wake specificity was 29–52 % ([Schyvens et al. 2025](https://academic.oup.com/sleepadvances/article/6/2/zpaf021/8090472)).
* Low wake specificity inflates wearable efficiency by 2–10 points, so an 85 % time-in-bed threshold applies to the wrong denominator.
* Last-night sleep contributor = the strap's sleep score when present, else:
  * `durationScore` = 100·min(1, TST/need)², so 7 of 8 h ≈ 77 and 6 h ≈ 56.
  * `continuityScore` = clamp(100 − (WASO − 30 min)·(100/90), 0, 100), with WASO inside first sleep → final wake.
  * sleep = 0.7·duration + 0.3·continuity.
* Stages are shown descriptively only, at most against the user's own baseline.

### Need (Established)

* 480 min for ages 18–64, 450 min for 65+. NSF ranges are 7–9 h and 7–8 h ([Hirshkowitz 2015 summary](https://www.hospimedica.com/critical-care/articles/294757126/panel-of-experts-recommend-new-sleep-durations.html)), above the AASM ≥ 7 h floor ([AASM](https://www.aasm.org/resources/pdf/adultsleepdurationconsensus.pdf)).
* User override 420–540 min. Individual optimal sleep ranged 7.3–9.3 h in the lab ([Kitamura et al. 2016](https://pmc.ncbi.nlm.nih.gov/articles/PMC5075948/)).
* Optional personal need after ≥ 14 nights (design choice): median TST of unconstrained nights (wake ≥ 60 min later than the weekday median, excluding the night after a ≥ 1 h short night), clamped 420–540, blended 50/50 with the default until there are ≥ 6 such nights.

### Debt (direction Established, formula Contested)

Deficits build near-linearly over 14 days without people noticing ([Van Dongen 2003](https://ntrs.nasa.gov/citations/20040087752)). One 10 h recovery night is incomplete ([Banks et al. 2010](https://pmc.ncbi.nlm.nih.gov/articles/PMC2910531)), and about 1 h of debt took 4 nights to clear (Kitamura). Today's 4-night half-life discounts nights 8–14 to 25 % or less, which undercounts the 14-day build-up.

* Iterate the last 14 nights oldest → newest; missing nights are skipped; naps count toward TST.
* λ = 0.5^(1/7) ≈ 0.906 (half-life 7 nights). d = need − TST.
* d ≥ 0: debt = debt·λ + d. d < 0: debt = max(0, debt·λ − min(0.5·|d|, 60 min)).
* Score = clamp(100 − 25·debt hours, 0, 100), keeping `SLEEP_DEBT_POINTS_PER_HOUR` = 25. Withhold with fewer than 7 of 14 nights.

### Regularity (Probable)

SRI predicted all-cause mortality better than duration in 60,977 UK Biobank participants. Risk fell most between the bottom quintile (SRI < 71.65) and the next (HR 0.80), and a valid SRI needed only ≥ 120 h of paired epochs ([Windred et al. 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC10782501/)).

* Grid: 1440 one-minute epochs per local day, starting at 12:00. Sleep = any deep, light or REM minute including naps; awake = wake; strap off = missing.
* Over the last 7 day-pairs, count only pairs where both minutes are valid ([GGIR](https://www.rdocumentation.org/packages/GGIR/versions/3.3-8/topics/CalcSleepRegularityIndex) does the same). SRI = −100 + 200·matches / validPairs.
* Withhold below 7200 valid pairs (120 h).
* Bands (method-dependent, descriptive): < 72 irregular, 72–79 fair, 79–87 regular, ≥ 87 very regular.
* Fallback when withheld but there are ≥ 4 main sleeps in 7 days: SD of sleep midpoint, "good" under 60 min.

### Sleep-health count (Probable)

A count of good dimensions predicted cardiometabolic morbidity (OR 0.901 per point), while most single dimensions did not ([Brindle et al. 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC7458275/)). Shown on the Sleep screen, not in Readiness. Score 0–3:

* Duration: 420 ≤ TST ≤ 540 min (≥ 65: ≤ 510).
* Continuity: WASO ≤ 30 min, or sleep-period efficiency ≥ 90 %.
* Regularity: SRI ≥ 72.
* Timing is informational only: the midpoint is shown and flagged outside 01:30–04:30, never scored. In Brindle it marked 57.5 % of people "poor" and had no independent association.

## Body-signal alerts

Today's rule fires on one night with two or more conditions. That has the right core signal (resting HR) but the wrong structure.

* **Resting HR is the best-validated early-warning signal.** NightSignal compared overnight mean RHR with a streaming median: +3 bpm a warning, ≥ +4 bpm on 2 consecutive nights an alert. It reached 80 % sensitivity, a median 3-day lead and 87.7 % per-night specificity, yet still fired 1.1–1.3 times per 21 days in non-COVID users, set off by alcohol, exercise, travel and vaccination ([Alavi et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC8799466)).
* **Persistence controls false alarms.** Needing ≥ 3 anomalous nights in 7 instead of ≥ 1 cut the healthy-period hit rate for breathing rate from 13.4 % to 0.88 % ([Natarajan et al. 2021](https://pmc.ncbi.nlm.nih.gov/articles/PMC8443549)).
* **Breathing rate alone is specific but insensitive:** 36.4 % of symptomatic cases had a ≥ +3 /min night, against 4.3 % of healthy people (same source). So +2 /min alone over-fires.
* **Temperature adds value:** it raised a multimodal model's AUC from 0.770 to 0.819 ([Mason et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC8891385)). The luteal-phase rise is about +0.3 °C at the finger ([Maijala et al. 2019](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6883568/)), so +0.5 °C is the lowest threshold that won't fire every cycle.
* **Only different signal families vote.** Cardiac, thermal and respiratory count as independent. HRV + RHR is closer to one signal counted twice. SpO2 has no early-warning evidence. No study tests a rule-based conjunction directly, so this part is reasoning, not a measured result.

### Rule (Probable)

Baseline per signal: last 28 valid nights, excluding the 2 most recent and any Watch or Alert nights; median + 1.4826·MAD; floors as Readiness. No alerts before 14 valid nights. A 28-night window spans a whole menstrual cycle.

RHR here is the overnight mean sleep HR (the NightSignal metric). Readiness keeps the lowest 30-minute definition.

| Flag | Condition | Role |
|---|---|---|
| `RHR_FLAG` | rhr − c ≥ max(4 bpm, 2·s) | core |
| `RHR_WATCH` | rhr − c ≥ 3 bpm | watch only |
| `TEMP_FLAG` | temp − c ≥ 0.5 °C and ≥ 2·s (sleep mean, ≥ 30 readings) | core |
| `RR_FLAG` | rr − c ≥ 2.0 /min and ≥ 2·s | core |
| `HRV_SUPPORT` | z(`hrv_n`) ≤ −2.0 | support, never a core vote |

SpO2 and desaturations are excluded; they stay in Readiness and the Breathing card. `SPO2_ALERT_DROP` no longer raises an alert.

* **Watch** (in-app, no push): any single core flag, or `RHR_WATCH` + `HRV_SUPPORT`.
* **Alert** (push; Readiness capped at 55), any of:
  * `RHR_FLAG` on 2 consecutive nights;
  * ≥ 2 different core flags on the same night;
  * any core flag on ≥ 3 of the last 7 nights;
  * rr − c ≥ 3.0 /min on its own.
* One missing night is bridged; two or more reset persistence. The alert clears after 2 consecutive nights with no core flag.
* Likely causes are annotated, not suppressed: user tags (alcohol, travel, vaccination, hard session) or previous-day TRIMP > 1.5× chronic.

### Wording

Under the FDA's General Wellness guidance revised on 6 January 2026, a wellness product may show HRV, SpO2 and similar values and may say professional evaluation "may be helpful". It stops being a wellness product if it names diseases, calls values abnormal, shows diagnostic thresholds, or uses alerts to prompt clinical action ([Covington](https://cov.com/en/news-and-insights/insights/2026/01/fda-issues-revised-guidance-on-general-wellness-products); [Loeb & Loeb](https://www.loeb.com/en/insights/passle/2026/01/fda-updates-its-general-wellness-device-frameworkwhat-companies-need-to-know)).

* Call the feature "Body signals off baseline". Never illness, infection, fever or COVID.
* Push copy: "Several of your overnight signals (resting heart rate, skin temperature) are outside your usual range for the second night. This can happen with heavy training, alcohol, travel, poor sleep, or when your body is fighting something off. Consider an easier day. If you feel unwell, checking in with a healthcare professional may be helpful. This is a wellness estimate, not a medical assessment."
* Breathing card: show events per hour against the user's own history, without normal / mild / moderate / severe labels.
* No screen shows "+0.5 °C" as a fever line.
* Not reviewed: EU MDR and UK MHRA framing, and whether an open-source, non-commercial app counts as a "manufacturer".

## Training load and Strain

No HR-load method has been validated on free-living waking minutes. Even in structured sessions, Banister TRIMP's test–retest CV is 15.6 % ([Wallace et al. 2014](https://researchgate.net/profile/Franco-Impellizzeri/publication/261067400_Establishing_the_Criterion_Validity_and_Reliability_of_Common_Methods_for_Quantifying_Training_Load/links/5caee628a6fdcc1d498c2081/Establishing-the-Criterion-Validity-and-Reliability-of-Common-Methods-for-Quantifying-Training-Load.pdf)). Banister is still the least-bad choice: Lucia needs lab ventilatory thresholds, and Edwards' zones start at 50 % HRmax, which walking and sitting after coffee cross.

### TRIMP (Probable as least-bad; design choice for free-living)

* Keep the formula: x·0.64·e^(1.92x) (male or unknown), x·0.86·e^(1.67x) (female), x = HRR. Days with < 8 h of HR stay excluded.
* New filter: a minute counts only when awake, x ≥ 0.20, and ≥ 3 of the last 5 minutes have x ≥ 0.20. Isolated PPG spikes no longer count.
* RHR for HRR = 14-day median of the strap's daily resting HR, else median(nightly lowest + offset). Offset = median(daily RHR − nightly lowest) over 14 days when both exist, else 3 bpm. Karvonen and Swain define resting HR as awake rest; a nightly minimum would inflate HRR for every waking minute.

### Max HR (Probable)

* Default Tanaka 208 − 0.7·age, from 18,712 subjects ([Tanaka et al. 2001](https://doi.org/10.1016/S0735-1097(00)01054-8)). A user-entered test value overrides it.
* Any equation's individual SD is about 7–11 bpm, so raising is sound and lowering is not.
* Keep raising to the observed maximum by at most +15 bpm, using the second-highest artefact-filtered 1-minute HR over 60–90 days. New: it needs two separate days within 3 bpm of each other.

### Load screen (descriptive)

* ACWR leaves Readiness (Established against scoring). The coupling critique is accepted by its own proponents ([BJSM 53/15/921](https://bjsm.bmj.com/content/53/15/921)), ACWR rescales acute load ([Impellizzeri 2021](https://iris.univr.it/retrieve/e34cfb98-e922-4c2f-aab5-18583ab7e31b/Impellizzeri_What%20Role%20Do%20Chronic%20Workloads%20Play_SportMed_2021.pdf)), and the 2025 meta-analysis for 0.8–1.3 has a CI of 0.14–0.94 in team-sport athletes ([Qin et al. 2025](https://pubmed.ncbi.nlm.nih.gov/41029871/)).
* Fitness and fatigue trend, standard 42/7 convention:
  * CTL_t = CTL_{t−1} + (TRIMP_t − CTL_{t−1})·(1 − e^(−1/42)); ATL likewise with 7.
  * Form = CTL_{t−1} − ATL_{t−1}.
  * Personal time constants are not fitted: the parameters are poorly identifiable ([Imbach et al. 2025](https://hal.inrae.fr/hal-04999009v1)).
* Optional ACWR, labelled "contested": EWMA acute 7 / chronic 28, chronic excluding the current 7 days (uncoupled).
* Readiness keeps "previous day's load" (weight 0.04): 100 if TRIMP_yesterday / CTL ≤ 1.5, else −25 per unit, floor 30.

### Strain (design choice)

* Strain = 21·(1 − e^(−TRIMP_day/180)). About 7 for 1 h at 60 % HRR (male), about 15 for 2 h at 75 %.
* A fixed absolute curve keeps today's 12 comparable with last year's. WHOOP's 0–21 Strain has no published validation ([WHOOP](https://www.whoop.com/thelocker/how-does-whoop-strain-work-101/)).
* Shown separately: "vs your usual" = percentile of today's TRIMP within the last 90 days.

## Charge and stress (Contested)

Charge stays. Firstbeat's method is the best publicly documented template: activity above 30 % of VO2max, 20–30 % as daily activity, and falling post-exercise EPOC never counted as stress ([Firstbeat white paper](https://www.firstbeat.com/wp-content/uploads/2015/10/Stress-and-recovery_white-paper_20145.pdf)). Its only support is manufacturer-curated (r ≈ 0.40–0.50 with waking cortisol). Consumer stress scores separate rest from lab stress tasks but barely track self-reported daily stress ([Rosenbach et al. 2025](https://www.biorxiv.org/content/10.1101/2025.01.06.630177v1); [JMIR 2026](https://www.jmir.org/2026/1/e78244)).

* Keep every current constant, including the personal threshold *T* (`STRESS_MARGIN`, 50–70 clamp).
* New: stress drain starts only after ≥ 3 consecutive minutes at or above *T*.
* New label: "physiological stress (estimate)", not "mental".
* New calibration target: an hour at stress 80 with *T* = 60 (0.007·20·60 = 8.4, plus 2.4 base) stays at or below a third of an hour at 60 % HRR (20–32).
* If the strap can report Zepp's BioCharge `totalCharge`, log it like Readiness and fit `SLEEP_K`, `BASE_DRAIN` and the drain coefficients to it. That calibrates to Zepp's black box, not to physiology.
* Deferred until there is a calibration target: a full EPOC model and separate physical and mental pools. Without ground truth they add parameters, not accuracy.

## Energy and activity

### Energy expenditure (Established for BMR; Contested for the rest)

Mifflin-St Jeor came within 10 % of measured resting metabolic rate in more people than other equations ([Frankenfield et al. 2005](https://pure.psu.edu/en/publications/comparison-of-predictive-equations-for-resting-metabolic-rate-in-/)). Against doubly labelled water, group-calibrated HR-only estimates were less precise than acceleration (RMSE 37 vs 24 kJ/day/kg), and adding HR to motion removed bias without improving precision ([Brage et al. 2015](https://pmc.ncbi.nlm.nih.gov/articles/PMC4562631/)). We have per-minute steps, not acceleration counts, so the model is motion first with a narrow, labelled HR branch.

* BMR = 10·W + 6.25·H − 5·A + (male +5, female −161, unset −78) kcal/day; b = BMR/1440 per minute.
* net(MET) = (MET − 1)·3.5·W/200 kcal/min. Anchoring on the user's own BMR avoids counting rest twice.
* Per minute with data (no row = missing, never zero):

| Minute | kcal/min |
|---|---|
| In a device workout | device kcal, pro-rated |
| Asleep | 0.95·b |
| ≥ 60 steps | b + net(cadence MET) |
| Some steps, or a neighbouring minute stepped | 1.3·b |
| Awake, sedentary | 1.15·b |

* Cadence MET (CADENCE-Adults, [Tudor-Locke et al. 2019](https://ijbnpa.biomedcentral.com/track/pdf/10.1186/s12966-019-0769-6)): up to 60 y, 60 → 2.0, 100 → 3, 130 → 6, linear between; sustained ≥ ~145 uses ACSM running. Age ≥ 61: 100 → 3, 110 → 4, 120 → 5. A fixed-stride ACSM walking equation gives about 3.7 METs at 130 spm, far below the measured 6.
* HR branch, labelled "HR-assisted": awake, not in a workout, < 20 steps, ≥ 10 consecutive minutes with x ≥ 0.40 → max(EE, b + net(min(6, 1 + x·(VO2max_est/3.5 − 1)))).
* Daily total is "partial" below 20 h of wear. Flag PAL = TEE/BMR outside 1.2–2.5. No thermic effect of food unless stated.
* 0.95, 1.15 and 1.3 are conventions, not 2024 Compendium values (sleeping and sitting quietly are both 1.0 there, [pacompendium.com](https://pacompendium.com/inactivity)).

### MVPA (Established thresholds)

* Moderate at cadence ≥ 100 (optionally ≥ 105 for age ≥ 61, [McAvoy 2023](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10688086/)), vigorous at ≥ 130 ([Tudor-Locke](https://pmc.ncbi.nlm.nih.gov/articles/PMC6029645)).
* A minute counts only inside a run of ≥ 2 consecutive qualifying minutes (design choice).
* HR-MVPA: awake, ≥ 10 consecutive minutes with x ≥ 0.40 (vigorous ≥ 0.60), and < 100 steps or in a workout. Never double-counted.
* MVPA-equivalent = moderate + 2·vigorous against 150 min/week (WHO 2020). Raw minutes shown too.

### Steps and distance (Contested)

* Steps: the strap's daily counter, else the sum of per-minute steps. Flag a divergence > 5 %.
* Default goal 7,000 (≥ 60 y) or 8,000–9,000 (< 60 y), framed as "where most of the benefit is reached". Mortality plateaus at 6,000–8,000 at ≥ 60 and 8,000–10,000 below ([Paluch et al. 2022](https://doaj.org/article/c3639c468b4348958202e52793ab979d)).
* Distance ≈ steps·L: L = 0.414·H below 120 spm, linear to 0.45·H at 130. Running uses workout or GPS distance. A rolling median of GPS distance / steps from phone-GPS walks overrides L. Always shown with "≈".
* No step or cadence validation exists for a chest- or arm-worn strap.

## VO2max and fitness age

### VO2max (Established for Jurca; Probable for phase 2)

Jurca 2005 needs only sex, age, BMI, resting HR and one activity question. Its SEE is 1.45–1.97 METs, with no bias in an independent older-adult sample ([Mailey et al. 2010](https://pmc.ncbi.nlm.nih.gov/articles/PMC2831835/)). Exercise-based wearable estimates do better on average but still have limits of agreement around ±10 ml/kg/min ([Molina-Garcia 2022](https://link.springer.com/10.1007/s40279-021-01639-y)).

* VO2max (METs) = 2.77·sex (m = 1, f = 0) − 0.10·age − 0.17·BMI − 0.03·RHR + SRPA (0–4) + 18.07; × 3.5 for ml/kg/min.
* RHR = median of the last 7 nightly values (≥ 5 in 14 days). A sleeping RHR runs a few bpm below a seated one; at −0.03 METs/bpm that is small next to the SEE, so it's documented, not corrected.
* Show ±1 SEE (≈ ±5 ml/kg/min) and a FRIEND 2022 percentile band, < 25 / 25–50 / 50–75 / 75–97.7 / ≥ 97.7 ([Kaminsky et al. 2022, Mayo Clin Proc 97(2):285–293](https://doi.org/10.1016/j.mayocp.2021.08.020), open access). Never a decimal. Recompute weekly. *As built:* the paper's Table 3 gives only the 10th–90th percentiles, so the bands use the nearest reported cuts, < 30 / 30–50 / 50–70 / 70–90 / ≥ 90 (treadmill, RER ≥ 1.0).
* Withhold: < 5 nightly RHR in 14 days; height or weight missing or > 90 days old; activity question unanswered in 180 days; age outside 20–79; beta-blocker or pregnancy flag.
* Before coding: verify the sex coding and SRPA weights against the Am J Prev Med 2005 full text (the weights may be non-integer: 0 / 0.32 / 1.06 / 1.76 / 3.03), and transcribe the FRIEND 2022 tables from the primary paper.
* Phase 2, phone GPS: flat, steady 1-minute epochs (speed 1.0–4.5 m/s, accuracy ≤ 10 m, HR SD < 3 bpm over 3 min after ≥ 3 min warm-up). VO2 = 3.5 + 0.1·v (walk) or 0.2·v (run), v in m/min. Fit VO2 = a + b·HR (span ≥ 15 bpm, r² ≥ 0.7), extrapolate to HRmax, take the median of ≥ 3 sessions in 28 days. If it differs from Jurca by > 2 SEE, show both.

### Fitness age (design choice, optional)

* The age at which the FRIEND 2022 sex-specific 50th percentile equals the estimate, shown as a range (about ±10 y), clamped 20–80. Withheld with VO2max.
* Wording: "typical of a [sex] aged about X–Y". No mortality language and no sleep term.

### Biological age: dropped

Summing fitness and sleep hazard ratios that weren't adjusted for each other gives a falsely precise number. The sleep ratios come from self-reported sleep, and the long-sleep side probably reflects reverse causation ([Yin et al. 2017](https://pmc.ncbi.nlm.nih.gov/articles/PMC5634263)). Jurca's SEE alone adds ±2.3–2.7 "years" of noise.

## Corrections to recovery.md

| recovery.md says | What the source shows |
|---|---|
| Vesterinen 2016 guided training with 7-day lnRMSSD | He used **raw** RMSSD from a 4-minute supine morning chest-belt recording; the band was mean ± 0.5 SD of 4 baseline weeks ([JYX](https://jyx.jyu.fi/handle/123456789/50625)) |
| Plews 2012 as the basis for the HRV rule | A case study of 2 athletes ([PubMed](https://pubmed.ncbi.nlm.nih.gov/22367011/)). Cite Plews' later weekly-average work and the trials |
| Elevated resting HR precedes symptoms "in most infections" (Mishra 2020) | 32 COVID-19 cases; 63 % detectable in real time ([medRxiv](https://www.medrxiv.org/content/10.1101/2020.07.06.20147512v1)). Nothing on "most infections" |
| Breathing-disturbance bands are "the usual clinical cut-offs" | They are apnea severity bands applied to strap desaturation counts, and naming them breaches the 2026 wellness guidance |
| Max-HR raising is "like Firstbeat" | No Firstbeat document confirming the rule was found |
| Several signals together "cuts false alarms" | Plausible but unquantified; no study measured a rule-based conjunction |

## Open questions that need strap data

The Helio Strap has no published validation for nightly RMSSD, resting HR, skin temperature, sleep staging or step counting.

**Inputs**

* Own or strap HRV and RHR: which has the lower night-to-night robust SD with the same response to tagged events? (Settles the strap-input switch.)
* Which RHR definition is least noisy for this user: lowest 30-minute, sleep mean or 10th percentile?
* Skin-temperature resolution and night-to-night SD. (Sets the 0.15 °C floor; tests whether +0.5 °C is reachable.)
* Typical gap between daily RHR and the nightly minimum. (Replaces the 3 bpm offset.)

**Protocol**

* Does the strap's "awake" stage include sleep latency or a time-in-bed boundary? (WASO vs time-in-bed efficiency.)
* How often does it log sleep SpO2? Does the type-74 apnea summary land on the right morning?
* Does any channel expose beat-to-beat RR intervals, for artefact-corrected HRV?
* Can a strap used only with OpenStrap report BioCharge `totalCharge`?
* Do chest- and arm-worn step counts track cadence well enough for the 100/130 thresholds?

**Validity**

* Agreement with the strap's Readiness is agreement with another unvalidated score. Only the morning check-in makes it a within-user test. In one such study, Oura RHR tracked subjective illness in only 6 of 20 athletes individually ([Spetz et al. 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696214/)).
* Open datasets (PMData, LifeSnaps, MMASH) come from other devices: they can test the logic, not the constants.

## Implementation order

1. **Measurement first (M):** backtest harness that replays the engine per day over an exported database; 1–5 morning check-in; tags (alcohol, travel, vaccination, hard session, sick); log `hrv_n`, `rhr_n`, `rhr_mean_sleep` and `rhr_p10` next to the strap's values. Needed before 2026-10-30.
2. **Wording (S):** rename the alert, add the disclaimer copy, remove breathing-disturbance severity labels and on-screen temperature thresholds; fix the citations above.
3. **Readiness statistics (S–M):** median/MAD, floors, 7/14/28 phases, withhold without HRV or RHR, strap placeholder/fallback/QA, 0.4/0.6 blend, dead zone.
4. **Readiness weights (S):** new weights; ACWR to the Load screen; breathing disturbances to the Breathing card. Check with the backtest first.
5. **Alert state machine (M):** Watch/Alert, persistence, family voting, 28-night baseline.
6. **Sleep (S):** drop the stage target, age-based need, new debt formula, continuity score.
7. **Regularity (M):** SRI and the sleep-health count.
8. **Load (S):** 14-day daytime RHR, 3-of-5 filter, max-HR agreement rule, CTL/ATL/Form, Strain.
9. **Charge (S):** 3-minute stress gate, labels; BioCharge logging if readable.
10. **Activity (M):** step divergence, MVPA, distance.
11. **Energy expenditure (L).**
12. **VO2max (M):** Jurca band after verifying coefficients; optional fitness age; GPS phase 2 later.
13. **Personal weights (M):** fit Readiness weights from check-ins, shrunk toward the defaults, only after ≥ 6 weeks of check-ins.
