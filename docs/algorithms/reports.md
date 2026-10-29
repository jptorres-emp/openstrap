# Reports

The weekly and monthly reports, in the spirit of WHOOP's Weekly and Monthly Performance Assessments, worked out on the phone from the data OpenStrap already has. Recovery and effort lead: the header holds Recovery's average with the Load a day beside it, then Recovery day by day against your normal and each day's Load against its target. The week adds the day itself (its Recovery, Load target and tonight's bedtime) and the week against the 3 before; the month adds how the mornings spread, the month against the one before, the days of the week, your sports with their heart rate zones, and what your habits do to the next morning. Rows open the details behind them. **Status: implemented (2026-10-29)** in `data/Reports.kt` (everything below, pure and unit-tested in `ReportsTest`), `RecoveryEngine.reportDays` (gathering the days) and `ui/glow/ReportsDetail.kt` with `ui/dashboard/ReportCopy.kt` (the screen and its words, tested in `ReportCopyTest`). The screen opens from the chart button in the header, on any tab, for the day shown.

OpenStrap is a wellness app. These are estimates for personal insight, not medical advice.

## Contents

1. [The days](#1-the-days)
2. [Week and month](#2-week-and-month)
3. [Load target](#3-load-target)
4. [Tonight's bedtime](#4-tonights-bedtime)
5. [Habits](#5-habits)
6. [Known problems](#6-known-problems)

---

## 1. The days

`reportDays` reads the 60 days ending on the day shown (`Reports.DAYS`): the month shown and the month before it.

* **Each morning is scored as it was that day.** For every morning the engine runs `Recovery.readiness` on the nights from `BASELINE_NIGHTS + 1` days before it and the loads from `LOAD_DAYS` before it, exactly the windows `RecoveryEngine.compute` reads for that day. On a 90-day test export all 60 mornings matched `compute` to the point.
* **Loads use the resting and maximum heart rate of the day shown**, as `RecoveryResult.dailyLoads` (the Load detail's week and month) already do. Today's Load is so far (`Day.partial`) and is left out of averages and of the target's verdict.
* **HRV against its baseline** (`Day.hrvVsUsual`, in percent) uses Readiness's own baseline: earlier valid nights not in Watch or Alert, the last 60.
* **Heart rate is read a month at a time** and reduced to minutes as it's read, so about five months of samples (60 days plus the 90 before for loads) are never held at once. A night's readings are cut to its own span by binary search.

## 2. Week and month

* **Week**: the last 7 days against the 3 weeks before them as one average week (totals scaled to 7 days). No comparison until a full week before it is on record.
* **Month**: the last 30 days against the 30 before. No comparison with under 14 days before it.
* **Figures** (`Reports.Figures`): average Recovery and the days at 70 or more, HRV and resting heart rate a night (valid nights), asleep and deep plus REM a night, the median bedtime and wake time and how far each moves from one night to the next (the mean absolute change between consecutive nights), Load a day, your usual Load, workouts and time training (the week story's count: every workout with a sport), and steps a day.
* **Changes** are worked out from the values as shown (rounded first), so "+13 min vs 7 h 30" adds up to the 7:43 beside it. A row lights when it moved the wrong way by enough to matter: Recovery down 5, HRV down 5%, resting heart rate up 2 bpm, sleep down 20 min, deep and REM down 15 min, a sleep time moving 15 min more a night, steps down 20%. Load and workouts never light: more or less isn't better or worse.
* **Your normal** on the charts is the mean ± one standard deviation of the last 30 days, as `History.normal` draws a metric's.
* **The month's spread**: days in each of Recovery's bands, shown as Ready or better (70 and up), Moderate (55 to 69) and Take it easy (under 55). **Days of the week** average all 60 days, since four of each in a month are too few; the lowest is pointed out when it's 5 or more under the average.
* **Load against the target**: each finished day's Load is within, below or above the target its morning set (section 3), and the stretch is summed up as days within, below and above, with the stretch before it alongside (an average week of the 3 before, or the month before). Today is under way and isn't counted. The card lights from the top when the days went over their targets more than under them, at least twice: below a target is often just a rest day, over it on a day meant for recovery is the thing to notice. Its note is Activity's training balance (Building, Steady, Easing off: the last week's Load against your usual).

## 3. Load target

What the morning's Recovery says the day is for, as a range of your own Load.

| Morning | Kind of day |
|---|---|
| An alert, or Recovery under 55 | Recover |
| Recovery under 70, or HRV below its range | Moderate |
| Recovery 85 or more | Push |
| Otherwise, or no score at all | Usual |

While Recovery is learning (its first 7 nights) the strap's own readiness stands in, as Today shows it.

**The range** comes from your training days (a workout you started, not one the strap found by itself) among the worn days of the last 6 weeks, once there are at least 4:

| Kind | Range of your training-day Loads |
|---|---|
| Recover | 0 up to half an easy session (half the 25th percentile), or a usual rest day's Load if that's more |
| Moderate | half an easy session to a middling one (the median) |
| Usual | an easy session to a hard one (25th to 75th percentile) |
| Push | a middling session to your hardest tenth (median to 90th percentile) |

Before that it is built round your usual daily Load (fitness, CTL), with Today's ±25% band for a usual day: Recover 0 to 50%, Moderate 50 to 100%, Usual 75 to 125%, Push 100 to 175%. The range is rounded out to steps of 5 and is at least 20 wide. A finished day's Load is within, below or above it as it reads (rounded). WHOOP's own model is not published; this one says in plain numbers what it asks for and why.

## 4. Tonight's bedtime

* **How long**: your sleep need, plus the week's average shortfall a night (need less sleep, earlier days' naps included, rounded to 5 min, at most 45), plus 15 min after a hard day (`Recovery.hardDay`, Load above 1.5 times your usual) or 30 min after one at twice your usual, less today's naps (at most an hour of the night).
* **When**: counted back from your usual wake time, the median of the last 28 mornings of tomorrow's kind (a weekday or the weekend; all of them with fewer than 3 of that kind), divided by your sleep efficiency (the median share of time in bed spent asleep over the last 14 nights, 0.9 without any).

## 5. Habits

The app keeps no journal, so habits are found in the data. Each is about the day before the morning, except the night's own late bedtime and short night:

* a workout, and the two sports you do most when you do more than one;
* a late workout, one that started at 20:00 or later;
* a hard day, a Load above 1.5 times your usual;
* a late bedtime, 45 min or more after your usual;
* a short night, under 7 h asleep;
* a weekend night, Friday or Saturday;
* an active day, steps in your top quarter.

**Held against each other.** A habit that only keeps company with another would take its effect on a plain comparison: in the tests, CrossFit mornings that never follow a late football game look several points better without doing anything. So the effects come from one least-squares fit of the next morning's Recovery (and, separately, HRV against its baseline) on all the habits at once, each coefficient with its t-test. A habit that's a copy of another (late workouts that are all football) can't be told apart from it: it gets the other's effect, and each lists the other as "often with". "A workout" is the union of the sports and late workouts, so it stays out of the fit and keeps its plain difference, tested by permutation (2000 shuffles, a fixed seed so the same days always give the same answer). Without 20 scored mornings, or with fewer than 10 degrees of freedom left, every habit falls back to the plain difference.

**Verdicts.** Clear: p under 0.05 and at least 3 points (or 3% for HRV). Likely: p under 0.1 and at least 2. Under 4 mornings after a habit, or without it, it's too few to tell. A habit's value on screen is Recovery's effect when that's clear or likely, else HRV's when that is, else none.

## 6. Known problems

* **Several habits are tested at once**, so one may look clear by chance. The info sheet says so, and a pattern isn't proof of cause.
* **Sleep counts toward Recovery**, so short nights and late bedtimes move it partly by definition. HRV is the cleaner sign there, which is why its effect is shown too.
* **Sleep balance saturates early.** With an 8 h need, 7 h 30 every night is 4 h of debt and 0 for that contributor (12% of Recovery); see calculations.md. The reports read Recovery as the app scores it, so a habit of slightly short nights costs more in the reports than its HRV effect suggests.
* **60 days is short for habits.** A habit that comes once a week has about 8 mornings behind it. The verdicts are honest about it, but most habits will read "no clear effect" for the first months.
