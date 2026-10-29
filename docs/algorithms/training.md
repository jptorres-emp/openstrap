# Training

The Training tab: what to do with the numbers the other tabs show, for whatever sports you do. It leads with the day's
Load target in your own sports, then the last week's Load against your usual (a guide to injury risk), the calories you
burn week by week (for fat loss), and the weekly and monthly reports ([reports.md](reports.md)). **Status: implemented
(2026-10-29)** in `data/Training.kt` (pure, tested in `TrainingTest`), `ui/glow/TrainingScreen.kt` with
`ui/dashboard/TrainingCopy.kt` (tested in `TrainingCopyTest`).

OpenStrap is a wellness app. These are estimates for personal insight, not medical advice.

## Contents

1. [One report, one answer](#1-one-report-one-answer)
2. [The day](#2-the-day)
3. [Load and injury risk](#3-load-and-injury-risk)
4. [Calories burned](#4-calories-burned)
5. [What each card leaves to the others](#5-what-each-card-leaves-to-the-others)
6. [Known problems](#6-known-problems)

---

## 1. One report, one answer

Everything on the tab comes from the report the app works out for the day shown (`Reports.build`), once, in
`ReportViewModel`: the Training tab, the Reports screen and Today's bedtime all read it, so they can't disagree. It's
worked out off the main thread when the day, the profile, the workouts or the synced data change. The first at once,
later ones after 3 s of quiet, so a sync's batches make one report; coming back to the app with nothing new keeps the
report it has (`HealthRepository.dataStamp`).

## 2. The day

The hero: the kind of day the morning's Recovery made it and the Load target for it (reports.md, section 3), with the
Load so far, the Recovery behind it and, today, tonight's bedtime (reports.md, section 4). The sentence says it in your
own sports:

* **Your sports** (`Training.sports`): each sport you started yourself on at least 2 days of the last 6 weeks with no
  other sport that day, the most done first. A day that holds only that sport makes its Load stand for the sport, and a
  sport's usual day is the median of those days. A day with two sports counts as a day of each but stands for neither.
* **What fits** (`Training.fit`): a sport's usual day, rounded as shown, is under, within or over the target. Within:
  "A usual day of CrossFit fits (≈112)", with a second sport when one fits too. Over: keep it lighter. Under: room to go
  harder. Without sports of your own yet, the kind of day in words.
* **A finished day** says where it landed against its target instead.

CrossFit is a sport of its own (sport type 130, the strap's cross-training): in the everyday sports, and found by
"cross", "WOD" and "functional". Futsal is found under Soccer.

## 3. Load and injury risk

The last week's Load against your usual, as the day began: fatigue (ATL, the 7-day exponentially weighted Load) over
fitness (CTL, 42 days), the acute to chronic workload ratio, in percent.

| Ratio | Zone | Says |
|---|---|---|
| under 75% | Easing off | fine for a lighter week, fitness fades if it lasts |
| 75 to 125% | Steady | the safe zone, build from here little by little |
| 125 to 150% | Building | rising fast, keep the next days steady |
| over 150% | Spike | the zone tied most to injuries, ease off |

Steady and its edges are Activity's Load card's (`TabCopy.loadBalance`, the ±25% band), so the two say the same word
for the same week, Spike being the top of Building. The chart is the last 30 days of the ratio against the steady band.

## 4. Calories burned

The app's own energy estimate (`EnergyExpenditure`, as the Calories detail works out a day: Mifflin-St Jeor at rest,
plus steps, heart rate and the strap's workout calories), for each of the last 28 days (`RecoveryEngine.reportDays`,
`Reports.Day.kcal`). The card shows the average a day over the last 7 finished days against the 3 weeks before, and the
four weeks as bars. A day under way is left out. Food isn't counted, so the card says what the number is for: to lose
fat, burn more than you eat. Without age, weight and height there's no estimate, and a row to add them.

## 4b. Weight

Fat loss's result, beside the calories that feed it. You log a weight (`WeightEntry`, one a day: a second the same day
replaces it) from the card or the profile; logging also makes it the profile's weight, which the energy estimate uses.
The card shows the latest in dots, the last 8 weeks' averages as a line (`Training.weightWeeks`), and the trend
(`Training.weightTrend`): the last 7 days' average against the first logged week in the 4 weeks before them, so a
morning's water or salt doesn't read as a trend. The reports carry the average weight of the week or month.

## 5. What each card leaves to the others

* Today's Recovery, the day's Load and the time to recover stay on Today and Activity: the tab says what to do with
  them, with the Recovery behind the target as a figure only.
* The weekly and monthly reports look back (and open from the tab); the day's target and tonight's bedtime are here,
  not in the reports.
* Tonight's bedtime is the one Today's evening and night count down to, and the Moment widget, which reads it from
  settings (`SettingsStore.plannedBedtime`) for the day it was planned.
* The Calories detail shows a day; the weeks are the tab's.
* Each card's (i) has the same four folds (how it helps, when to look, how it's worked out, why it might be off). The
  Reports screen's (i) explains the reports only and points here for the target.

## 6. Known problems

* **Load comes from heart rate**, so strength work with long rests reads lower than it feels. CrossFit with heavy lifts
  may get a lower usual day than its effort.
* **The ratio is a guide, not a diagnosis.** Studies disagree on how well the acute to chronic ratio predicts injuries;
  it's shown as a way to avoid sudden jumps.
* **Days without the strap** count as no Load and no calories, so a week off it reads as easing off.
* **Energy estimates** are often 10 to 20% off; the trend from week to week is more reliable than the number.
