# Design v2: screen critique

Step 3 of the [brief](brief.md). Each screen is checked against the [pattern board](patterns.md) (pattern IDs like
A1, G2a) and the [inventory](inventory.md) (findability problems F1 to F20). For each screen: its purpose in one
sentence, what to keep, what's wrong, and the new layout that the mockups in step 4 will draw.

Sizes: **L** Large 2x2, **W** Wide 2x1, **S** Small 1x1, **T** Tall 1x2, **Strip** full width, half height.

## 0. The frame around every screen

**Now.** Five labelled tabs in a floating glass bar. A wordmark, a watch icon and a day row with chevrons on four
tabs. A tab tap wipes every stack (F2). Back from any tab root jumps to Today. Strap state is a bare icon that looks
the same for "not paired" and "disconnected" (F12).

**New.**

- **Header:** avatar on the left (opens You as a sheet, B5). Moment name and day in the middle, swipeable, yesterday
  peeking in dimmed (B8): "Midday, Thu 23 Oct". Strap chip on the right with battery, dot and words when they matter
  (B6, E2).
- **Sync strip** under the header, only while syncing or after a failure: "Catching up. Synced to 03:10." Amber in
  place on failure (E1). Replaces the pull gap's spinner text. Pull to sync stays, without the ratchet ticks.
- **Tab bar:** pill with four labelled tabs (Today, Recovery, Sleep, Activity), the active capsule tinted with its
  section colour. Detached circle on the right holds the moment's action (B4): Start workout in the workout window,
  live heart rate while recording, "All metrics" otherwise (decision 3).
- **Tab rules (B3):** a tap on the current tab scrolls to the top, a second tap pops to its root. Other tabs keep
  their stacks. Back returns to the previous tab, not always Today.
- **Card rule (B1):** every card opens its own detail, grown out of the card (F1m). No card ever switches tab.
- **Mood (G1e):** Day, Dim in the evening, Night after your usual bedtime. Applies to all tabs.

## 1. Today

**Purpose:** how am I right now, and what's next.

**Keep.** The glow language. The Charge-leads-after-waking rule (it's the seed of the adaptive day). The morning
check-in. Workout offer and suggestion cards. The Start workout shortcut that turns into the running workout.

**Wrong now.**

- The answer comes last: the dial and "Ready" lead, the sentence that explains them sits at the bottom of the hero
  (A2).
- Only two moments exist (before and after noon). No workout time, after workout, evening or night (A1).
- Five cards switch tabs when tapped and lose your place (F1).
- What changed repeats Recovery's Body signals with a different rule for "normal" (F3).
- The Sleep card fills the screen at 18:00 (A4). Habit Age, Heart rate and Activity sit at the same weight as the
  hero.
- Every card breathes, drifts or beats. The heart card beats at a reading that may be hours old (F3m).
- Copy: "Primed", "Fully charged", "Signals off baseline", "Needs HRV data", "Strap alerts", "PAI 41".

**New layout.** One hero slot (A1), then rows built from the row types in G2d. The hero's light follows the sun
(G1b), every other card is still.

| Moment | Hero (L) | Rows under it, in order |
|---|---|---|
| Waking up (wake to wake + 3 h) | "Slept 7 h 40. Recovered enough for your usual training." Recovery score, Charge at waking as a fact | Strip: How do you feel? (check-in). T Charge beside S Resting HR over S HRV. W Last night. W Heart rate. Pair of pinned S tiles. W Activity |
| Midday | "62, steady for midday. Your run took 18." Charge as a light level (G1a) | W Last night, collapsed to one line (A4). W Activity with pace vs usual (C8). Pair S pinned. W Heart rate. W Recovery |
| Workout window (learned, decision 6) | "Ready for effort. You usually run around now." Start button | T Charge beside S Load over S Time to recover. W Activity |
| During a workout | Live numbers, number first: time, bpm on a zone arc (D2). Tap opens the live screen | Nothing else changes |
| After a workout (2 h) | "Since your run, 40 min. Heart rate settling." Effort and Load, glow toned down (D3) | W the workout. T Charge beside two S |
| Evening | "Today, 9,400 steps, Load 62. Bedtime in 1 h 10." Dim mood | W Charge, W Activity, W Last night collapsed |
| Night | Same as evening, Night mood, the hero almost unlit | |

Present in every moment when they apply: Strip for strap alerts (renamed, see copy), Strip for a workout offer or
suggestion, W "Your week" from Monday through Sunday (decision 5, F16), "Edit Today" at the end of the feed (G2f).

**States.** No strap paired: the hero is the pairing stage (E3) and every other card is hidden. First days: cards
keep their shape with a dash and "After your first night" (E4). No sleep: the waking hero says "No sleep recorded last
night. Was the strap on?" and Charge leads. Partial day: one caveat pill in the header (C7).

## 2. Recovery

**Purpose:** can I push today, and why.

**Keep.** The score, the contributor groups (Body, Sleep, Load), the Charge curve, Body signals tiles with
status words, calibration progress.

**Wrong now.**

- Seven panels, three of them about the same signals: the off-baseline panel, What drove it and Body signals (F3).
- "How it works" and the strap's own score sit on the screen as text (C6). The strap's score appears in three places
  (F14).
- Recovery and Charge cards are not tappable, so there's no history of either (F11).
- A failed computation shows "Calculating…" forever (E5).
- Copy: "Pay attention", "Capped", "Signals off baseline", "HRV balance", "Recovery index", "(strap fallback)",
  "Calibrated · +3 vs yesterday".

**New layout.**

| Row | Content |
|---|---|
| L Recovery | Sentence first, score in a dark pool (G1c), "+3 vs yesterday". Caveat pill while learning ("Learning, 9 of 14 nights"). Off-normal signals fold into the sentence: "Below your normal, mostly from HRV." Opens the Recovery detail |
| W What drove it | Top three contributors as rows with bar and status word (C3), "All 11" opens the detail |
| T Charge beside two S | Charge as a light level. S Recharged, S Drained. Opens the Charge detail, which takes in "Where your charge went" |
| Pairs of S | Body signals: Resting heart rate, HRV, Skin temperature, Breathing, Blood oxygen, Stress. Stronger rim when off normal (G1d) |
| W Habit Age | Its home (decision below). Updated Sundays |

The method text, the strap's own score and "why it might be off" move to the (i) sheet (C6).

## 3. Sleep

**Purpose:** how did I sleep, and is it trending right.

**Keep.** The night card with its hypnogram scrub. Stages. Sleep vitals. Naps that open in place. The week chart.

**Wrong now.**

- Nine panels. Regularity appears twice (F8). "Breathing" names two things (F9).
- Night charts sit on the screen, while the vital tiles open the whole-day detail, not the night (F18).
- "What shaped the score" is method text.
- Copy: "Sleep score · Good", "7 h asleep · 7 h 40 in bed · woke 3×", "Not quite", "UK Biobank", "minute by
  minute".

**New layout.**

| Row | Content |
|---|---|
| L Last night | "7 h 40, your best this week." Score with status word, bedtime to wake, hypnogram to scrub (F6m). Night mood always (G1e). Opens the Sleep detail with stages and what shaped the score |
| Pairs of S | Night vitals: Lowest heart rate, HRV, Breathing rate, Blood oxygen, Skin temperature, Average heart rate. Each opens its detail scoped to the night, with the night chart there |
| W Sleep regularity | One home: score, status word, the week's bed and wake bars. Takes in Sleep health and This week |
| W Breathing disturbances | Renamed. Per hour vs your usual |
| W Naps | Only when there are naps |

## 4. Activity

**Purpose:** how much did I move and train.

**Keep.** Steps with hourly bars, sport bubbles to start, Active minutes, Heart points (PAI), Training load, Fitness,
the workout list, Add a workout.

**Wrong now.**

- Ten panels, most of them dense with method text: Mifflin-St Jeor, Paluch, Jurca, FRIEND 2022, acute:chronic,
  "Contested", EPOC.
- Load naming drifts across six names (F19). "Recovery" in Fitness means time to recover (F4).
- Workouts list scrolls inside a 4 row window. All workouts only appears after 20 recent ones (F10).
- Offer, suggestion and Start workout repeat Today (F10).
- Auto-detect workouts switch hides on the Start panel, only today (F5).

**New layout.**

| Row | Content |
|---|---|
| Strip Start | Top three sports and "All sports". Today only |
| W Steps | Light level toward the goal (G1a), pace vs your usual (C8). Opens the Steps detail |
| Pair S | Active minutes (this week of 150), Heart points (7 days of 100) |
| W Load | One number for today vs your usual, the week's balance in words ("Building", "Steady", "Easing off"). Everything else in its detail |
| Pair S | VO2 max with status word, Time to recover |
| S Energy beside S | Energy, Distance |
| W Workouts | Today's and recent as rows, the pending suggestion as a row, Add a workout, "All workouts" always (B10) |

The offer card lives on Today only (and in its notification). The auto-detect switch moves to the Strap page.

## 5. You (avatar sheet)

**Purpose:** who you are and what happens to your data.

**Wrong now.** A full tab for things visited monthly. Material styling, unlike the rest (F20). Goals only here,
Profile from three places (F15). Two sleep targets in two places (F6). "Export CSV", "Import CSV".

**New.** A sheet with grouped glow rows, each with its live value (B7):

- **Profile:** "34, male, 72 kg, 178 cm". Opens Profile.
- **Goals:** "8,000 steps, 7 h 30 sleep, 8 active hours". Sleep need moves here from Profile (decision below).
- **Your data:** "Save a copy" (was Export CSV), "Restore from a copy", "Health Connect, last copy this morning".
- **Help:** Diagnostics.
- **About:** "OpenStrap 2.0. Everything stays on this phone."

## 6. Strap page

**Purpose:** is my strap working, and its settings in one place.

**Wrong now.** Reached by a tab switch to You (F1). Strap status in five places with five wordings (F12). Alarms,
phone alerts and health alerts share one page three taps deep (F5). The Zepp account sits inside Firmware. "dBm",
"HMFW", "User ID", "Connection failed".

**New.** Opens from the chip, from any tab, and back returns where you were.

| Row | Content |
|---|---|
| L Strap | Glowing render, state line with relative time ("Synced 2 min ago", "Last seen 18 min ago", "Charging 64%, full in 40 min"), battery. One fix per bad state (E2) |
| Strip actions | Sync now, Find strap |
| Rows with values | Alarms "06:40 weekdays". Phone alerts "Calls and notifications". Health alerts "High heart rate over 140". Off-body reminder "On". Auto-detect workouts "On". Keep connected "On". Firmware "Up to date". Zepp account "Signed in" |
| Bottom | Restart strap, Forget this strap |

Alarms, Firmware and Profile pages move to glow styling with the same row pattern.

## 7. Metric detail (one template for every number)

**Purpose:** what is this number, is it usual for me, and how has it moved.

**Keep.** Day, Week, Month, Year. Your normal. About. Related rows.

**Wrong now.** Exists for 8 metrics only. Recovery, Charge, Sleep score, Heart points, Load, VO2 max, Active minutes,
Energy and Regularity have none (F11). Opens with a slide from the side. Normal is a separate panel, not in the
chart. About text is always open.

**New.** Grows out of the card (F1m).

1. Value with status word ("48 ms, Below your normal"), the word glowing (C1).
2. Day strip and a metric switcher (B2).
3. D, W, M, Y pills. The headline and the sentence follow the range (C4).
4. Chart with your normal as a glow band, off-normal points lit (C2). Scrub moves the headline, with ticks (C5,
   F6m). Hollow points for low confidence (C7).
5. One sentence for now.
6. (i) opens the info sheet: bold meaning, the scale, then How it's worked out, What moves it, Why it might be off
   (C6).
7. Related.

## 8. Workout screens

**Ready.** Keep it. One line confirms the strap ("Strap connected. 72 bpm.", D1). GPS and keep-route explanations
go behind (i). Copy: "Off · no distance without it" becomes "Off. No distance without GPS."

**Live.** Keep the heart card, the stats row, Lap, Pause and hold to end. Two views, Heart and Map with a bpm chip
(D5). Splits as a line after each km. Paused drops to an outline (D1). Light height follows the zone (G1f). Haptics:
firm on start and end, tick on zone change and set done (F5m). Copy: "Zone 3 · Aerobic" becomes "Zone 3, aerobic" as
two text elements.

**Summary right after.** Leads with calm down: the heart rate drop drawing live, toned-down glow (D3). Then time,
effort and Load. Splits table here. Personal bests as a quiet glow swell (F4m).

**Workout detail (later).** One template for strap and phone workouts, sentence first ("Hard run, mostly in zone
4."), glow size by effort (G1f). "Recovery heart rate" becomes "Heart rate drop", "Recovery time" becomes "Time to
recover" (F4). EPOC and the 0 to 5 scales move to (i).

**Capsule.** Keep. It's already the right "where it came from" motion.

## 9. Copy, worst first

| Now | New |
|---|---|
| Pay attention | Below your normal |
| Signals off baseline | Some signals are off your normal |
| Primed | Ready for a hard day |
| Depleted | Low. Rest helps most now |
| A good day to push hard. | Your body is ready for effort today. |
| Favour rest, light movement and an early night. | Today looks like an easy day. An early night helps tomorrow. |
| High heart rate: 140 bpm | Heart rate went to 140 at 14:20 |
| Strap alerts | From your strap |
| Needs HRV data | Waiting for a night of HRV |
| Calibrated · +3 vs yesterday | +3 vs yesterday (two text elements) |
| Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue. | Moves to the Load info sheet |
| Sync failed: timeout | Sync stopped at 03:10. Try again |
| Export CSV | Save a copy |
| PAI 41 | Heart points 41 |
| — (empty value) | A dim dash drawn by the UI |

## Decisions (all five approved 2026-10-23)

1. **Load and Strain.** Activity shows two effort numbers: Strain (0 to 21) and Load. Recommendation: one card
   number, "Load", with Strain inside the Load detail.
2. **Sleep need and sleep goal.** Profile's "Sleep need" rates the night, the Goals' sleep goal fills the arc.
   Recommendation: keep both values but show them together under Goals, as "Sleep goal" and "Your sleep need",
   since merging them would change how nights are rated.
3. **A plain name for PAI.** Recommendation: "Heart points", with PAI named once in its info sheet.
4. **A home for Habit Age.** Recommendation: the bottom of Recovery, plus a Today card on Sundays when it updates.
5. **Strap alert wording.** The strap's own health alerts now read like alarms. Recommendation: neutral facts with a
   time ("Heart rate went to 140 at 14:20"), kept under "From your strap".

Next is step 4: mockups of Today in each moment, the frame, Recovery, Sleep, Activity, the Strap page, the metric
detail and the live workout.
