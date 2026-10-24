# Design v2: spec

Step 5 of the [brief](brief.md). This is what step 6 builds. It turns the approved [pattern board](patterns.md),
[critique](critique.md) and [mockups](mockups/) into rules and components that a Compose implementation can follow
without guessing. Where this spec and an older doc disagree, this spec wins.

Mockups: the canvas "OpenStrap v2 mockups" (claude.ai artifact, private) and its source in [mockups/](mockups/).
Numbers in the mockups are made up but consistent across the day.

Non-goals, unchanged from the brief: no new features, no changes to how any score is calculated, no strap protocol
changes, existing deep links keep working.

---

## 1. Principles

1. **Meaning first.** Every card leads with one sentence or one number that answers its question. Method, formula and
   caveats sit one tap away (info sheet).
2. **The screen follows the day.** Today has one hero slot whose content changes by moment. Other cards keep a stable
   set and change order only when the moment changes.
3. **Light means something.** Glow, edge light and colour each carry a meaning. Nothing glows, moves or is coloured
   only to decorate.
4. **One home per thing, one name per thing.** No metric appears twice on one screen. No name means two things.
5. **Nothing moves when idle.** Only live data moves (the live heart rate, the live workout).

---

## 2. Foundation

### 2.1 Colour

Keep `ui/theme/Theme.kt` and `MetricColors` as they are. Black background, white text. Each metric keeps its hue:

| Metric | Colour | Metric | Colour |
|---|---|---|---|
| Recovery | `readiness` / `readinessDeep` | Heart rate, Heart points | `heart` |
| Charge | `chargeScale` (by value), Ember orange on glow | Steps, Activity, Active minutes | `steps` |
| Sleep | `sleepDeep`, stages `sleepLight` `sleepRem` `sleepAwake` | HRV | `hrv` |
| Blood oxygen | `spo2` | Stress | `stress` |
| Skin temperature | `temperature` | Breathing rate | `sleepLight` |
| Load | violet `#A78BFA` (new constant `MetricColors.load`) | Habit Age | Orchid `#D86BEA` |

Status colours: `good`, `warn` stay for strap and sync states only, never for health values. Off-normal health values
use their own metric colour, never red or amber.

### 2.2 Type

- **Dot-matrix digits** (`DotMatrixText`, Doto) for the main number of a card, list values and live numbers.
- **Outfit** for everything else. Sentence on a hero: 20 sp like the card title over it, never bigger, line height 1.22. Card title 15 sp medium. Secondary
  12.5 sp at 72% white.
- **Dots are for numbers.** Nothing else in the app is drawn from dots: no dotted borders, no dot-matrix bars. The
  fine grain inside glow cards stays.

### 2.3 Grid and sizes

Two columns, 16 dp side gutter, 12 dp gap. Unit width = (screen width minus 32 minus 12) / 2.

| Size | Shape | Use |
|---|---|---|
| Large | full width, as high as its content | The hero of the moment, with no empty band under it |
| Wide | 2x1 | A number with a trend, a strip or a short chart |
| Small | 1x1 | A number and a status word |
| Tall | 1x2 | Charge as a battery, when it sits beside smalls |
| Strip | full width, 68 dp | A state or an action (check-in, Start sports, last night collapsed) |

Rows are one of: `Full(Large | Wide | Strip | List)`, `Pair(Small, Small)`, `TallPair(Tall, Small over Small)`. A lone
Small becomes a Wide. Cards never resize by user choice. A Small is a number and a word only, no chart (the nap's
stage strip is the one exception). Smalls and Talls are a unit high (160 dp) and two units plus the gap.

Where sizes are in use ([mock](../../../build/v2-shots/size-mocks/sizes.html)): Today after a workout (Load and Time
to recover, a Small pair), Recovery (Charge as a Tall glow battery beside Recharged over Drained), Sleep (Breathing
disturbances and Naps, a Small pair when both are there) and Activity (Active minutes and Heart points).

On wide screens (600 dp and up) the existing two-column split in `GlowLayout` stays. Each grid row is one block, so a
Pair or TallPair stays together in one column, and the row types fill each column.

### 2.4 The three card tiers

Every card on every screen is exactly one of these.

| Tier | Component | What it is for | Per screen |
|---|---|---|---|
| **Glow card** | `GlowCard` (existing, extended) | The main card of the screen | 1, plus at most 1 more |
| **Edge light card** | `EdgeCard` (new) | Mid-importance cards with a shape of their own: last night, heart rate, activity, load, regularity, Habit Age | Any |
| **Grouped list** | `ListPanel` + `ListRow` (new) | Small facts: body signals, time to recover, energy, distance and the like | Usually 1, at most 2 |

`DotPanel` is removed. Lists of rows (workouts, contributors, settings) use `ListPanel` with a plain hairline.

Never two cards in a row with the same design. A card's design is its tier, the edge its light comes in from, and
its shape: a Small and a Tall each count as a design of their own (`CardLook.shape`), so a Small never matches a Wide.
Colour (the metric's, or the neutral light, hsl 220 10% 55%) doesn't count: two glows in a row, or two edge cards lit
from the same side, look the same whatever their colours. Neighbours are cards one after the other in reading
order, the two Smalls of a Pair side by side, a Tall with each of its Smalls, every card of a row with the row over
and under it, and on two columns also cards side by side or one over the other. On two columns a full-width card
touches the top card of each column under it and the last of each column over it, on Today as on the tabs. Two
Smalls side by side differ by their edge (one lit from below, one from above, or from the left). When two neighbours would look the same,
the later one flips its edge first. If that still clashes it takes another tier the spec gives it (Last night as a
strip, a list on a neutral edge card, the second glow as its edge card) or the neutral light. Only then does a card move, and only one whose place is
open: a pin, Your week or Habit Age, to the nearest place it fits, never splitting a Small pair. `varyLooks` applies
this to Today after Edit Today's pins and hides.

#### Glow card

Keep the existing recipe: rim light, dark core, tone band, grain, hairline. Changes:

- `GlowMotion` loses `Drift`, `Breath` and `Stride`. Only `Heartbeat` (live heart rate only) and `Still` remain.
  Everything that used another motion becomes `Still`.
- New `GlowStyle.lightAngle` (degrees, counterclockwise from the right edge) replaces `LightFrom` for heroes. 270 is from below, 90 from above, 225 lower
  left, 45 upper right. Named constants in `LightAngle`. The core sits on the opposite side.
- New `GlowStyle.lightLevel` (0..1, nullable): the height the light reaches, for Charge and other levels. Core Y and
  height follow it. Floor of 0.12 so an empty card still glows faintly. The edge between dark and light stays soft.
- New `GlowStyle.second` flag for the second glow card on a screen: about 60% of the rim strength and a shorter
  gradient, so the hero clearly leads.
- Locked on every glow card: corner radius 36 dp, grain amount, hairline, the black the core fades to, text colour
  and position (top left). Free per card: hue, light angle and level, strength.

#### Edge light card

Near black surface (`#0A0A0B`), 28 dp radius, 1 dp hairline at 7% white. A soft glow comes in from one edge in the
metric colour: inset 30 dp deep, about 50% of the metric colour at the edge, fading to nothing. **No bright line on
the border.**

Where the glow sits carries meaning:

| Case | Edge |
|---|---|
| Usual value | Bottom, soft |
| Above your normal | Top, stronger |
| Below your normal | Bottom, stronger |
| Progress toward a goal or your usual | Bottom, soft, plus a 4 dp progress bar inside the card |
| A level, like Charge as an edge card, or Load toward your usual on Activity | The light fills from the left across to the value, no bar. Its own design under the 2.4 rule |
| Live, now (heart rate today) | Right |
| About earlier (Recovery after the morning, Habit Age) | Left |
| Sleep (last night, regularity) | Top, like a night sky. Breathing disturbances is a value (bottom), so it never matches regularity above it (5.2) |

The hairline stays plain on every edge card. Off normal shows through the stronger glow and the coloured value and status, never a coloured border. Static. The edge only changes when the data changes.

#### Grouped list

`ListPanel`: `#0F0F11`, 28 dp radius, 1 dp hairline at 6% white, a title row (title left, optional note right).

`ListRow`: an 8 dp dot in the metric colour, the name with its status in 12.5 sp under it, and the value in
dot-matrix digits right-aligned in its own column. The value and status take the metric colour only when off your
normal. Each row opens that metric's detail. Waiting rows show a dim dash and "After your first night" or similar.

### 2.5 Light with meaning on glow cards

| Card | Rule |
|---|---|
| Today hero | `lightAngle` by moment: waking 225 (lower left), midday 90, workout window 45 (upper right) and stronger, after a workout 270 and calm, evening 315 and dim, night 270 and almost unlit |
| Charge | `lightLevel` = Charge / 100, light from below |
| Recovery (tab hero, detail header) | The number sits in a dark pool: core behind the number, light all round |
| Workout summary | Glow size by effort: an easy walk leaves most of the card dark, a hard run floods it |
| Live workout heart card | Light height follows the current heart rate zone. Live signal, so it may move |

### 2.6 Moods

A screen-level `GlowMood` applied on top of every card's style:

| Mood | When | Effect |
|---|---|---|
| Day | Default | None |
| Dim | Evening moment | Glow and edge strength x 0.6, tone band off |
| Night | Night moment, and the Sleep tab always | Glow x 0.4, light from below only, hairlines kept at full strength |

### 2.7 Charts

Every chart kind in the app, drawn in this style ([chart kit](mockups/ChartKit.dc.html)):

| Kind | Tier | Where | Touch |
|---|---|---|---|
| Line with scrub | Edge, detail | Heart rate day, night heart rate and breathing | Hold to scrub |
| Ghost line of your usual | Edge | Steps, Load and Charge so far today | Read only |
| Charge curve | Glow | Charge card and detail | Hold to scrub |
| Hourly pill bars | Glow | Steps card | Tap an hour |
| Trend dots | Edge, detail, Recovery hero | Recovery over the last 7 days beside the hero score, Habit Age over days | Tap a day |
| Arc gauge | Glow | Recovery detail header | None |
| Horizon arc | Glow | Sleep score | None |
| Progress ring | List row | Goals | None |
| Hypnogram | Glow | Last night, naps | Hold to scrub |
| Stage bars | Detail | Sleep detail | None |
| Stacked day bars | Detail | Heart points over 7 days | Tap a day |
| Bed and wake bars | Edge | Sleep regularity | Tap a night |
| Zone arc | Workout | Live workout Heart view only | None |
| Zone bar | Workout | Workout summary and detail, thin under each workout row and on Today's workout card | Tap a zone |
| Age scale | Edge | Habit Age | None |
| Contributor bars | List row | What drove Recovery, where Charge went | Tap a row |
| Route by zone | Workout | GPS workouts | Pan |
| Range with handles | Detail | Add a workout, trim a workout | Drag |

Rules for every chart:

- **Your normal** is a dim band in the metric colour behind the data. Points inside it stay dim, points outside get
  the full colour. Before there is enough data, show "Learning your normal, 4 of 14 nights" instead of a band.
- **Low confidence** points are hollow, lines through gaps are dashed.
- **Compare by pace**: anything "so far today" is compared with your usual by this time, drawn as a faint dashed ghost
  line. Never against a full day.
- **Scrub moves the headline.** No floating tooltip. The big number becomes the point under the finger, the date
  replaces "Today", release snaps back.
- **Zone arc vs zone bar.** The arc answers "where am I now" and is only on the live workout. The bar answers "how
  was my time spent" and is only after. Never both on one screen. The ongoing notification uses the arc laid flat
  (five segments, the current one lit).

---

## 3. Frame

### 3.1 Header

`ScreenParts.DateHeader` is replaced by `MomentHeader`:

- **Left:** avatar button, opens the You page.
- **Centre:** the moment on Today ("Waking up", "Midday", "Workout time", "After your run", "Evening", "Night") or the
  tab name on other tabs, over the day ("Thu 23 Oct"). Never wraps. Yesterday's short name peeks in dimmed on the
  left. Swipe the header to change day. Tapping the date opens the calendar sheet, which marks days with data.
- **Right:** the All metrics button (a round bar chart icon, opens the All metrics sheet, on every tab), then the
  strap chip. **Hidden for now:** the button is off until All metrics earns its place. The sheet stays built.
  The strap chip shows battery and a dot when all is well. Words only when they matter: "Charging 64%", "Not
  synced" (amber dot), "18 min ago" (amber, last seen), "Not paired". Tapping it opens the Strap page from any tab.

Chevrons go. Changing the day still changes it on all four tabs (shared `DayViewModel`), as now.

### 3.2 Tab bar

Four tabs in a pill, centred, and nothing else: Today, Recovery, Sleep, Activity. The active capsule is tinted with its
section colour (white on Today). The bar only ever switches tabs. Nothing beside it does an action or changes with
the moment.

- **All metrics** is the grid button in the header (3.1).
- **Start workout** is the Start button in Today's workout-window hero (4).
- **During a workout** the live capsule above the bar shows the heart rate in the zone colour and opens the live
  screen.

**All metrics sheet.** A modal sheet over the current tab, in the style of the sheets over the app: every metric grouped by section,
each row with its value for the day. Tapping a row closes the sheet, then opens that metric's detail on the current
tab. Recovery and Sleep rows switch to their tab at its root, and Charge to the Recovery tab with its card in view.
Back or a swipe down closes it. The tab bar under it does nothing while it's open.

### 3.3 Navigation rules

- A card opens its own detail, grown out of the card (3.5). **No card ever switches tab.** `FocusTarget` and the tab
  jumps in `MainActivity` (inventory 1.5) are removed.
- Tapping the current tab scrolls to top. Tapping it again pops to its root. Tapping another tab keeps every stack.
- Back from a tab root goes to the previous tab, then Today, then leaves.
- The You page, the Strap page and the All metrics sheet open over the current tab and close back to it. Pages
  opened from them stack on top, so Back from Profile returns to You and Back from Alarms returns to the Strap page.
- Deep links keep working: `open_workout`, `show_workout`, `ready_sport`. Health alert and body-signal notifications
  gain a route to their metric detail (`open_metric`).

### 3.4 You page and Strap page

**You page** (from the avatar, a page like the Strap page, with the same round back button): Profile, Goals (steps, active hours, sleep goal, and your sleep need shown
with it), Your data (Save a copy, Restore from a copy, Health Connect), Diagnostics, About. Every row shows its current
value as a second line. It was a sheet, but most rows open pages of their own, and a sheet that closes to open a
page loses its way back.

**Strap page**: hero glow card with the strap, state line with relative time and battery. Sync now and Find strap.
Rows with values: Alarms, Phone alerts, Health alerts, Off-body reminder, Auto-detect workouts (moved from Activity),
Stay connected in background, Firmware, Zepp account (moved out of Firmware). Then Restart and Forget. Alarms,
Firmware and Profile pages move to the same edge and list styling (no Material panels).

### 3.5 Motion

Motion is signal. Specs use Compose springs with no bounce (`dampingRatio = 1f`).

| Motion | Spec | Signals |
|---|---|---|
| Card to detail | Shared bounds transform: the card's surface grows into the detail, its number becomes the header. Spring stiffness 500 in, 300 out | Where it came from |
| Value change | Animate from the old to the new value over about 450 ms, only after a sync or a live update. Never from zero on open | What changed |
| Moment change | Hero content crossfades over 300 ms. Rows reorder only at a moment boundary, never while the screen is visible | The day moved on |
| Live heart rate | `Heartbeat` at the real bpm | Alive |
| Collapsing summary | When the hero scrolls off, a slim strip with Recovery, Charge and Sleep pins under the header, tracking scroll exactly | Keeps the answer in view |
| Workout | Stiffness 800 | Snappy |
| Evening and night | Stiffness 250 | Calm |

Removed: idle core motion on every card, the sleep arc sweep from zero, the bouncy switch knob, the generic slide
for details. When the system's animator scale is 0, everything jumps to its end state.

### 3.6 Haptics

| Event | `HapticFeedbackType` |
|---|---|
| Scrub lands on a new point (day, hour, stage) | `SegmentFrequentTick`, at most every 40 ms |
| Scrub crosses into or out of your normal, or hits the highest or lowest point | `SegmentTick` |
| Heart rate zone changes (at most every 10 s), a strength set is done, a km split during a workout | `SegmentTick` |
| Workout starts, workout ends (hold completes), pairing succeeds | `Confirm` |
| A manual sync can't start or fails | `Reject` |
| A range handle passes the detected start or end | `SegmentTick` |
| Ordinary taps, pull to sync ratchet | None |

---

## 4. Today

### 4.1 Moments

`MomentResolver` (new, pure Kotlin, unit tested) returns one moment for "now". It only reads data the app already
has. It computes nothing about the body.

| Moment | Starts | Ends |
|---|---|---|
| During a workout | A recording is running | It ends |
| After a workout | A workout ended (phone or strap) | 2 h later |
| Workout window | 45 min before your usual workout time | 45 min after it |
| Waking up | Main sleep ends today (05:00 if none) | 3 h later (the current `chargeLeads` rule) |
| Night | 45 min before your usual bedtime | 04:00 |
| Evening | 3 h before your usual bedtime (19:00 if unknown) | Night starts |
| Midday | Otherwise | |

Priority when they overlap, top wins: during, after, workout window, waking, night, evening, midday.

- **Usual workout time:** the median start time of workouts in the last 28 days, per weekday when that weekday has 3
  or more, otherwise across all days when there are 3 or more. No workout window until then.
- **Usual bedtime:** from `RecoveryEngine.usualSleep`.
- Past days always show the waking layout.

### 4.2 Layout per moment

Rows in order. Sizes per 2.3, tiers per 2.4.

| Moment | Hero (glow) | Then |
|---|---|---|
| Waking up | Recovery: "Slept 7 h 40. Recovered enough for your usual training." Score, status word, "Charge at waking 84" | Check-in strip. List "Your body this morning" (resting HR, HRV, blood oxygen, breathing rate). Edge: Last night (with stage strip), Heart rate, Activity |
| Midday | Charge: sentence, value with `lightLevel`, recharged and drained | Strip: Last night collapsed. Edge: Activity (ghost line), Heart rate. List "Right now" (stress, HRV). Edge: Recovery (left edge) |
| Workout window | "Ready for effort. You usually run around now." Start button, Other sport | Second glow: Charge (wide). List "Before you start" (Load today, time to recover). Edge: Activity. Strip: Last night |
| After a workout | "Since your run, 40 min. Heart rate settling." Drop curve into your resting range (a dim band), calm glow | Edge: the workout (left, with its zone bar). Small pair: Load (toward your usual, from below) and To recover (flips to the top). Level: Charge, with its line so far. Edge: Activity |
| Evening | "A full day. A run, 13,860 steps and a Load above your usual." Steps, Load, Charge left. "Bedtime in 1 h 10". Dim mood | Level: Charge. Edge: Activity (line so far), Heart rate (the day's line, flips to the top under Activity unless live). Strip: Last night |
| Night | As evening, Night mood | As evening |
| During a workout | The workout, live heart rate | As midday without Heart rate (the hero has it) |

Heart rate shows at waking, midday, the evening and night, never in the workout window, during or after a workout.
Its card is the line with scrub: the day's heart rate with your resting range (your normal resting heart rate, mean
± one standard deviation over 30 days) as a dim band, the big number and its time following the finger. Activity's
card draws today's steps so far as a line in place of the progress bar. The ghost of your usual by this time waits
for hourly steps from past days (step 6). Charge's level card draws its line so far until the evening.

Present in every moment when they apply:

- Workout offer and suggestion (strip), from today only.
- Health alerts from the strap (strip, "From your strap"), opening the metric.
- "Your week" (wide edge card, neutral light from the bottom) Monday through Sunday, opening the week story.
- Habit Age (edge, left, the same card as on Recovery) only when there's a new estimate: from the Sunday it updates
  until it has been opened or the Monday ends. Pinned in Edit Today it shows every day. Never before the first
  estimate, pinned or not.
- "Edit Today" at the end: hide cards, pin up to two under the hero. Replaces the What changed tile chooser.

### 4.3 States

| State | Today shows |
|---|---|
| No strap paired | Hero is the pairing stage: strap render, dotted line to the phone, one sentence per step, "Find my strap". Below, waiting cards dimmed |
| First nights | Cards keep their shape, value as a dim dash, "After your first night" |
| No sleep last night | Waking hero: "No sleep recorded last night. Was the strap on?" Charge leads from then on |
| Sync stopped | Chip turns amber "Not synced". Stale cards get an "Up to 09:40" marker. The app retries in the background. No bar |
| Sync stuck when it matters | Still failing after 3 h, or in the morning when last night is on the strap: the hero becomes "Last night is still on the strap" with Sync now and "Last tried 07:42". Lists show "Waiting for last night" |
| Strap disconnected | Chip shows "18 min ago" in amber. Nothing else changes until data is stale |
| Strap charging | Chip "Charging 64%". The Strap page says wear data pauses while charging |
| Partial day | One "Partial day" marker next to the affected card's title, explained on tap |
| Recovery failed to compute | The Recovery card keeps yesterday's value dimmed with "Couldn't work out today's score. Try again", never "Calculating" forever |

---

## 5. Recovery, Sleep, Activity

### 5.1 Recovery tab

Each tab keeps one set of cards whatever the day, so its edges are fixed by meaning and chosen so no two neighbours
ever look the same (2.4). `TabPlans.kt` holds the order, looks, sizes and wide-screen columns, and a test goes through
every optional card on a phone and on two columns. On two columns a full-width card also counts as touching the top
card of each column under it.

1. Glow hero: sentence ("Recovered enough for your usual training. Mostly held back by HRV."), score in a dark pool,
   status word, "+3 vs yesterday", (i), and trend dots for the last 7 days beside the score, today the only full dot.
   "Learning, 9 of 14 nights" marker while calibrating. The normal band behind the dots waits for a normal of the
   score (step 6).
2. List "What drove it": the three contributors that weigh most, as contributor bars (name, bar, word, colour when
   off usual). "Top 3 of 9" in the title until the Recovery detail exists to open (step 6).
3. TallPair: Charge as a Tall glow battery (`lightLevel`, the light rising to the value), the Charge curve (hold to
   scrub, the number follows), the number and "84 at waking". Beside it, Recharged ("+44", "Sleep and calm rest",
   lit from below) over Drained ("-36", "Mostly being awake", lit from above).
4. List "Body signals": resting heart rate, HRV, skin temperature as deviation, breathing rate, blood oxygen, stress.
   A plain list again: the Smalls sit between it and What drove it.
5. Edge: Habit Age (left edge), always last. See 5.4.

Wide: the hero spans both columns. What drove it and Habit Age on the left, the Charge battery and Body signals on the
right, so no two lists sit side by side.

The off-baseline panel, How it works and the strap's own score are gone from the screen. The strap's score and the
method move to the Recovery info sheet. The body-signal Watch and Alert panel is gone too: an alert reads in the
hero's sentence, and an off-normal signal shows in its row's colour.

### 5.2 Sleep tab

Night mood always.

1. Glow hero: "7 h 40, your longest this week." (longest or shortest of the week's nights once there are 3 others to
   compare, else "7 h 40 asleep."). "Longest" rather than "best": more sleep isn't always better. The score as a
   horizon arc with the number under its crown, "Sleep score, good", bedtime to wake, "Woke 3 times", hypnogram to
   scrub, stage totals.
2. List "Night vitals": lowest heart rate, HRV, breathing rate, blood oxygen, skin temperature, average heart rate.
   Each opens its detail; scoped to the night in step 6.
3. Edge: Sleep regularity (top edge), score, status word, usual bedtime and wake, the week's bed and wake bars against
   dim bands at your usual times, last night in white.
4. Small pair, when there are naps: Breathing disturbances (bottom edge, a value against your usual, "Usual 2.1,
   early estimate") and Naps (left edge, earlier in the day: time napped, the latest nap's stage strip, "At 14:10").
   With no naps Breathing disturbances is a Wide. With naps but no breathing summary, Naps is.

Wide: the night spans both columns, vitals and the Small pair (or lone naps) on the left, regularity and a lone
breathing card on the right.

Gone from the screen: the stages panel (totals sit in the hero), What shaped the score and the night's heart rate
and breathing charts (Sleep detail, step 6), Sleep health and the week's averages (regularity's one home now), and
each nap's heart rate (the naps in the Sleep detail, step 6).

### 5.3 Activity tab

1. Strip: the three sports you do most and All sports, lit from below. Today only.
2. Glow: Steps, lit to the share of the goal, value, "62% of your goal", hourly pill bars (hours with no steps and
   those still to come are faint stubs). Opens the Steps detail. "About usual for midday" waits for usual steps by
   the hour from past days, which the app doesn't load yet.
3. Small pair: Active minutes ("This week, goal 150", from below) and Heart points ("Last 7 days, aim for 100", from
   above).
4. Level: Load, its light filling across toward your usual, "12 today", "Your usual is 40", balance word ("Building",
   "Steady", "Easing off") from the last 7 days against the last 6 weeks, with Today's 25% band for usual. No bar.
   The ghost line of your usual Load by now waits for Load by the hour (step 6). Strain lives in its detail.
5. Edge: Heart rate, the day's line with your resting range as a dim band, hold to scrub (the number follows). Lit
   from the right while live, otherwise from below like any usual value.
6. List "Today", "So far": VO2 max, time to recover, energy, distance.
7. Edge, neutral light from the top: "Workouts", the day's and the latest before it (5 in all), under small dim day
   headers ("Today", "Yesterday", "Wed 21 Oct") instead of a date on each row, a pending suggestion as a row with
   Save, "Add a workout you didn't record", "All workouts" always. A list right after another list, so it sits on
   the edge card 2.4 allows. Each row (version A of the workout mocks):
   - **Effort light:** the sport's toned white icon on a small dark tile (38 dp, 12 dp corners) with a soft
     heart-red glow behind it, as strong as the workout's Load (its EPOC, the strap's or OpenStrap's) against your
     usual workout's: the median of the last 20 with one, a usual one at half, twice it at full. Under 3 workouts
     with a Load, 150 is full. No Load keeps a faint minimum. Not the day's Load, which is TRIMP on another scale.
   - **One fact per sport,** then the start time: distance and pace for runs ("5.2 km, 5:24 a km, 21:28"),
     distance for walks, sets for strength ("14 sets, 07:10"), average heart rate otherwise ("Average 128 bpm,
     09:04"). Each falls back to the next the workout has. Distance (GPS or "≈" from steps, no pace from steps)
     and sets come from phone workouts only. The strap's workouts carry neither, so theirs say heart rate.
   - **Found by the strap:** a quiet outlined tag beside the name of a workout the strap detected or filed as
     "Activity".
   - The duration in dots on the right and the thin zone bar under the row, lined up with the name.
   - A personal best would be one warm line after the fact. The app doesn't detect them yet.
   All workouts uses the same row inside its day panels.

Wide: the strip and Steps span both columns. The Small pair and Load on the left, Heart rate, the list and Workouts on
the right.

Gone from the screen: the offer card (Today only), active hours, the step counter mismatch note and every method
note (Paluch, Mifflin-St Jeor, Jurca, FRIEND, acute:chronic, the strap's weekly EPOC range), which belong in the info
sheets.

### 5.4 Habit Age

One design wherever it shows: an edge card lit from the left (about the weeks behind you) with the title,
"Updated Sun 18 Oct", the age in dots, one status line and the age scale (the range as an orchid band, a white dot
at the estimate, a hairline at your age, "27 to 31" under the band and "You, 34" over the hairline). The glow card
and How it works are gone.

- **Status line:** "1 year younger than last week", "2 years older than last week" or "Same as last week", in whole
  years against last week's estimate. Until two weeks exist it says the range, "Likely 27 to 31". Orchid only when
  the number moved.
- **Last week:** each Sunday's estimate is stored as shown (`habit_age_week`, database version 18) when it's worked
  out, and the next week compares with the latest one from the 8 days before.
- **Before the first estimate:** "First estimate Sunday 22 Nov" and no number. Never on Today.
- **No birth year or sex:** says what's needed and opens the profile.
- **Where:** always at the bottom of Recovery. On Today only while new (4.2), or pinned.

---

## 6. Details

### 6.1 Metric detail template

One template for every number, including Recovery, Charge, Sleep score, Heart points, Load, VO2 max, active minutes,
energy, regularity and steps. Habit Age keeps its own screen in the same style.

1. Hero area tinted with the metric colour, fading to black. Back, metric switcher (siblings in the same group),
   (i).
2. Value in dot-matrix with its status word ("41 ms, Below your normal"), the word in the metric colour.
3. A seven-day strip with a dot under days that have data.
4. Day, Week, Month, Year pills. The value and the sentence change with the range.
5. Chart per 2.7 with your normal as a band and off-normal points lit.
6. One time-aware sentence.
7. Related rows (list).

### 6.2 Info sheet

From (i): bold one-line meaning, the scale as coloured bars with "You are here", then folded rows: How it's worked
out, What moves it, Why it might be off. Every method name, formula, citation and caveat in the app moves here (24
strings on cards today).

### 6.3 Workouts

- **Ready:** one line confirms the strap ("Strap connected. 72 bpm."). GPS and route explanations behind (i).
- **Live:** two views. Heart: zone arc with bpm, zone name and range, "9 min in this zone". Map: the map with a bpm
  chip in the zone colour. Swipe with two dots, the last view is remembered. Stats row unchanged. After each km a line
  shows for 10 s ("Km 3 in 5:42, avg 152") with one `SegmentTick`. Paused drops the card to an outline. Hold to end
  unchanged.
- **Notification:** time, distance and pace, bpm in the zone colour, the flat zone strip, Pause and Lap. Collapsed:
  sport, time, bpm.
- **Right after (calm down):** glow hero with the live heart rate drop ("Down 34 in the first minute. A quick drop for
  you."). Edge: the workout (time, distance, average). Edge: Load from this run (top edge when above your usual).
  List: Time in zones (zone bar with minutes). List: Splits. A personal best is a single line on the Load card
  ("Fastest 5 km since March") and a short glow swell, no modal.
- **Workout detail (later):** one template for strap and phone workouts: sentence first ("Hard run, mostly in zone
  4."), heart rate line coloured by zone, zone bar, splits, training effect behind (i). "Recovery heart rate" becomes
  "Heart rate drop", "Recovery time" becomes "Time to recover".

---

## 7. Copy

### 7.1 Rules

From the brief, enforced in code review and by a test (8.2):

1. Meaning first, then the number, then how (one tap away).
2. Calm, sharp friend: short, direct, specific. No cheering, emoji, exclamation marks, invented empathy or "you
   should".
3. Sentence first in the morning and evening, number first during workouts and live readings.
4. One caveat marker per card, explained on tap.
5. Time-aware: "so far today", "this morning", "since your run", "tonight".
6. **No interpunct (·), semicolon (;) or em dash (—) in any user-facing string.** Separate parts are separate text
   elements, or a full stop or comma. En dashes in time ranges are fine. The empty value is a dim dash drawn by the
   UI, not a character in a string.

### 7.2 Names

| Use | Never |
|---|---|
| Recovery (the score and the tab) | Readiness, recovered (as a score name) |
| Charge | Battery, energy score |
| Load | Strain (on cards), training load, TRIMP |
| Heart points | PAI (named once, in its info sheet) |
| Sleep regularity | SRI, regularity index |
| HRV | Heart-rate variability on cards (spelled out in its info sheet) |
| VO2 max | VO₂max, VO2max |
| Time to recover | Recovery time |
| Heart rate drop | Recovery heart rate |
| Breathing rate, Breathing disturbances | Breathing (alone) |
| Habit Age | Healthspan |
| Above your normal, Usual, Below your normal | Pay attention, Optimal, Higher, Lower, off baseline |
| From your strap | Strap alerts, alert |
| Save a copy, Restore from a copy | Export CSV, Import CSV |

The full list of 223 strings to rewrite is in [inventory 5](inventory.md#5-copy-audit), with examples in
[critique 9](critique.md#9-copy-worst-first).

---

## 8. Build

### 8.1 Order

Each step is one or a few commits on `design-v2`, with tests and before-and-after screenshots over adb.

1. **Foundation:** `GlowStyle` additions (`lightAngle`, `lightLevel`, `second`), motion reduced to `Heartbeat` and
   `Still`, `GlowMood`, `EdgeCard`, `ListPanel` and `ListRow`, the grid row types, `MetricColors.load`, a haptics helper
   with the 3.6 table.
2. **Frame:** `MomentHeader`, four-tab bar, the All metrics button in the header, the navigation rules (removing `FocusTarget` and
   tab jumps), You page, Strap page and its moved settings.
3. **Moments:** `MomentResolver` with unit tests for every boundary and overlap, including usual workout time.
4. **Today:** layouts per moment, states, Edit Today.
5. **Tabs:** Recovery, Sleep, Activity.
6. **Details:** the metric detail template for every score, info sheets, the card to detail transform, scrub haptics.
7. **Workouts:** two live views, the split line, calm down, zone bar, notification.
8. **States:** sync quiet and stuck, pairing stage, waiting cards, errors in place.
9. **Copy:** every string per section 7, the punctuation test turned on.
10. **Review:** retake `docs/screenshots/`, check the brief's measures (morning answer under 3 seconds, every screen's
    purpose in one sentence, no unexplained acronym).

### 8.2 Tests

- `MomentResolver`: each moment, each overlap, no-sleep and no-workout fallbacks, past days.
- Usual workout time: weekday rule, all-days fallback, fewer than 3 workouts.
- Edge placement: a function from (status, kind) to edge, tested for every row of the 2.4 table.
- Punctuation: a unit test that scans user-facing string literals under `ui/` and `data/*Copy*.kt` for `·`, `;` and
  `—` and fails with file and line.
- Existing tests keep passing. No algorithm test changes, since no algorithm changes.

### 8.3 Removed

`DotPanel`, `DotHeader`, `GlowMotion.Drift`, `.Breath`, `.Stride`, `FocusTarget`, the You tab, the Heart rate card's
duplicate live switch on Today (it stays in the Heart rate detail), the dead `dashboard/ChargeCard.kt` and
`dashboard/NapsPanel.kt`.

---

## 9. Open questions for hardware

These don't block the build but need a real strap:

- Breathing disturbances units (keep the "Early estimate" marker until confirmed).
- Whether the strap's charging state is reliable enough to drive the chip text.
- How long a stuck morning sync usually lasts, to tune the 3 h escalation.
