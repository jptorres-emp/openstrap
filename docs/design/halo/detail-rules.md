# Detail screen rules (approved 2026-11-15)

These are the pages behind the Today, Recovery, Sleep and Activity tabs: a metric's detail, a score's detail, a count's
detail, the workouts list, Habit Age, All metrics and the info sheet. They are separate from the workout rules.

The idea: **a tab is cards, a detail is a page.**
- On a tab, each number lives on a lit card.
- On a detail, the number owns the whole screen. So there are no cards and no rims. A wash of the metric's colour falls
  from the top, and everything sits on the page.
- Every detail shares one frame. What changes is the picture, and the picture follows the **kind of data**.

Boards are 390 wide. Fonts: Doto for the one big number, Funnel Display for titles and sentences, Funnel Sans for the
rest. Squircles. No glass. No dotted charts (the dot rule). No ·, ; or em dash in visible text.

## Metric colours (develop's MetricColors)
- Heart #FF5A5F
- Steps #C3F53C
- HRV #2DD4BF
- SpO2 #38BDF8
- Stress #F062C0
- Skin temperature #FF8A3D
- Recovery #7CF2C9
- Energy (charge) #F66C28
- Calories #FFD23F
- Load #A78BFA
- Habit Age #D86BEA
- Sleep #4C6FFF
- Heart points and active minutes use Activity lime #C6F55A

## 1. One frame for every detail
1. **Wash.** A radial of the metric colour from the top centre, about 0.55 strong, fading to black by about 380 px.
   Full bleed. No card, no rim, no light direction: a page isn't lit, its colour just falls in.
2. **Header**, 48 high, in a 96 / 1fr / 96 grid:
   - left: back, a 44 px squircle in rgba(0,0,0,0.35)
   - middle: the title, Funnel Display 17/600, with a caret when it opens the switcher to siblings
   - right: (i), a 44 px squircle that opens the info sheet
3. **Headline**, left aligned, 20 px in:
   - the value in Doto 72 with its unit beside it (15 px, white at 0.7)
   - under it, the status word in a light tint of the metric colour (15/600)
   - under that, one line of context at 13 px, white 0.6 ("Since 06:12", "Last night", "Day average")
   - with no value, the state word stands large in its place ("Learning") and the context says why. Never a lone dash.
4. **Day strip**, seven days. Each day shows its letter (11 px) over its date (15 px). The day shown sits on a white
   pill with black text. Days with data get a 3 px wide, 12 px long bar under them. Days without data get nothing. No dots.
5. **Range control**: Day, Week, Month, Year as a segmented control (track #111215, the picked one white with black
   text). Only the ranges the data has. Leave it out when there's only one.
6. **The picture**: full width, about 200 tall, no card. Value labels sit in a 30 px gutter on the right that data never enters. Axis labels 11 px #6E7480, at most 4 gridlines in #16171B. The
   day or bar shown is white. The rest is the metric colour. Your normal is a band in white 0.07 with "Your normal" at
   its right end.
7. **The sentence**: one sentence in Funnel Display Light 19, line 1.35, about the shown day, aware of the time of day.
8. **Figures row**: three figures, plain, value over label.
   - value: Funnel Display Light 28
   - label: 12 px #9AA0AA
   - 1 px #24262C rules between, 16 px padding after each rule
9. **Related**: a plain panel (#0C0D10, radius 27) of rows that open other details. Each row has a 32 px squircle
   tile in that metric's colour at 0.16 with its icon, then the name (15), then the day's value right-aligned (15,
   white 0.8), then a chevron. 56 px rows, 1 px #1C1D22 between them.

**Score headline:** the gauge sits on the left, with the status word and context stacked to its right.
**Labels inside a chart:** when bars fill the width, the label for your normal or a goal line goes in the chart's
title note ("The band is your normal"), never over the bars.
**Scrubbing:** a white 1 px line follows the finger and the headline shows the reading under it. Nothing else moves.

## 2. The kind decides the picture

| Kind | What it is | Examples | Headline | Day picture | Week and Month picture | Figures |
|---|---|---|---|---|---|---|
| Score | a judgement out of 100 | Recovery, Energy, Sleep score | the score in a thin arc gauge (metric colour, open at the bottom), with Doto in the arc | **What drove it**: diverging bars from your normal, better to the right in the metric colour, worse to the left in white 0.35, each with its reading. A score that moves through the day (Energy) adds its line, small, under them | one column per day, height by score, the normal band behind | Your normal, 7 day average, vs yesterday |
| Signal | moves all day | Heart rate, Stress, SpO2 | the latest (Live with the strap) or the day's average | a line through the day, the low to high band of each hour shaded at 0.18, a ring at the latest reading | a range bar per day (low to high capsule at 0.35, average tick in white) | Low, Average, High |
| Vital | one reading a night, read against your normal | Resting heart rate, HRV, Skin temperature, Breathing | last night's value, status word "Within your normal" or "Above" or "Below" | the night's readings as a line (when kept), else the Week picture | one column per night from your normal line, up or down, the normal band behind | Your normal, 7 night average, vs normal |
| Count | adds up toward a goal | Steps, Heart points, Active minutes, Calories, Distance | the total, then a solid goal bar (6 px, metric colour, white end mark at the goal) and "of 8,000" | bars by hour, the hour now in white | bars by day with a dashed goal line | Goal, Best day, Daily average |
| Record | things that happened | Workouts, naps | none: the title says what it is | a list under day headers, each row a sport tile, name, time and the main figure | the same list with a week summary line on top | none |
| Estimate | a slow read of habits | Habit Age | years, with "vs 34 actual" | the pace on a solid scale from −1x to 3x, then each habit as a row with its effect on the same scale | none | none |

(Habit Age's −1x is written "minus 1x" where the minus sign can't be shown.)

## 3. Kind-independent parts
- **Info sheet**, from (i): a sheet with a grabber.
  - title in Funnel Display 22
  - "What it is", "How it's worked out" and "What's normal for you" as three short sections, each with a 13 px #9AA0AA heading
  - the metric's colour only on a 4 px bar beside the title
  - a dark "Close" pill at the foot
- **All metrics**, from the grid button: a full sheet.
  - title "All metrics"
  - groups with 13 px headings: Scores, Heart, Body, Sleep, Activity, Workouts
  - each group is one plain panel of Related-style rows
  - search pill at the top
- **Empty and learning**: the headline's state word ("Learning, 5 of 7 nights") with the picture showing what's
  there, never a fake chart.

## 4. What differs from develop
- No 7-day dots under the days: short bars.
- No dotted month of Heart points: solid columns.
- The wash replaces the tinted hero box. Details have no lit card at all.
- The kind table decides the picture, so two Signal details always look alike, and so do two Counts.
