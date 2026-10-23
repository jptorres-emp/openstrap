# OpenStrap UI inventory (design v2, step 1)

Source: branch `design-v2`, read 2026-10-23. Paths are relative to `app/src/main/kotlin/org/openstrap/app/` unless they start with `docs/`. "MA" means `MainActivity.kt`. Line numbers are from the current tree.

Notes before reading:

- `ui/dashboard/ChargeCard.kt` and `ui/dashboard/NapsPanel.kt` are dead code (the glow screens use their own private `ChargeCard` and `NapsPanel`). Only the constant `STRAP_OFF_OVERNIGHT` (ChargeCard.kt:64) is still used. They are left out of the counts.
- `docs/screenshots/*.png` date from 2026-10-18 and are stale. `today.png` shows "Good sleep" (now "Good night"), no morning check-in at 10:15, and the Sleep card second, before What changed. Retake before any before/after comparison.
- Phone layout is described. From 600 dp wide the tab bar becomes a left rail (MA:429). From 720 dp details open in a right pane beside the list (`ui/components/Adaptive.kt:20-63`). Glow screens split into left/right columns on wide screens (`ui/glow/GlowLayout.kt:96-125`), so the phone order below is the declaration order.

## Counts

| What | Count |
|---|---|
| Full screens | 18 (5 tabs, 9 pushed details, 4 workout overlays) plus 3 variants (capsule, tabletop heart, fold workout glance) |
| Sheets and in-app dialogs | 13 |
| System pickers and permission prompts | 8 |
| User-facing string literals (ui/ + MA, live code) | 1209 |
| Literals breaking at least one rule | 223 |

---

## 1. Navigation map

### 1.1 Tabs (MA:188-194)

Today, Recovery, Sleep, Activity, You. Floating glass bar at the bottom (`FloatingNavBar`, `ui/components/Insights.kt:59`), rail when wide (`Insights.kt:110`). Tabs are pages of one `HorizontalPager` (MA:490), so you can also swipe between them. Swipe is off while any sub-screen is open (MA:492). All five tabs stay composed (MA:492).

During a recording the bar takes the zone colour and the Activity tab gets a dot (`accent`, `marked`, MA:439).

### 1.2 Screens, how they are reached, back behaviour

| Screen | File | Reached from | How | Back |
|---|---|---|---|---|
| Today | ui/glow/GlowTodayScreen.kt | Tab 1, app start | Tab | Past day selected: back returns to today (MA:385). Otherwise leaves app |
| Recovery | ui/glow/GlowRecoveryScreen.kt | Tab 2. Today Overview card and its score bar | Tab, or tab switch from Today with scroll to section (MA:536) | Goes to Today tab (MA:382) |
| Sleep | ui/glow/GlowSleepScreen.kt | Tab 3. Today score bar "Sleep", Today Sleep card | Tab, or tab switch (MA:530) | Goes to Today tab |
| Activity | ui/glow/GlowActivityScreen.kt | Tab 4. Today Activity card. Auto-detect notification | Tab, or tab switch (MA:541, MA:279-280) | Goes to Today tab |
| You | ui/device/YouScreen.kt | Tab 5. Strap chip in any date header (lands on Your strap) | Tab, or tab switch (MA:386) | Goes to Today tab |
| Metric detail (8 metrics) | ui/glow/GlowMetricDetail.kt:108 | Today: What changed tiles, Heart rate card, Strap alerts rows. Recovery: Body signals tiles. Sleep: Sleep vitals tiles. Activity: Steps card. Any metric detail: Related rows | Push on the current tab's stack (MA:513, 569, 590, 621) | Pops one (`BackHandler` GlowMetricDetail.kt:120) |
| Strap workout detail | ui/glow/GlowWorkoutScreen.kt:49 | Today Workouts rows, Activity Workouts rows, All workouts | Push (MA:538, 638, 625) | Pops one |
| Phone workout summary | ui/glow/GlowWorkoutMode.kt:1119 | Same rows when the workout was recorded on the phone (MA:833). Also shown full screen right after a workout ends (MA:778) | Push, or full-screen overlay when just ended | Pops. Just ended: back or Done dismisses (no back arrow shown, :1135) |
| All workouts | ui/glow/GlowWorkoutScreen.kt:209 | Activity Workouts panel "All workouts" row, only when there are more than today's plus 20 recent | Push (MA:639) | Pops one |
| Habit Age (Healthspan) | ui/glow/GlowHealthspanScreen.kt:47 | Today Habit Age card only | Push on Today (MA:542) | Pops one |
| Your profile | ui/device/ProfileScreen.kt:72 | You "Your profile". Today Habit Age card when birth year or sex missing. Activity Energy row and Training load "Max HR is estimated" row | Push on that tab (`profileTab`, MA:234, 444) | Asks "Discard changes?" if edited (:119-128) |
| Your strap | ui/device/DeviceScreen.kt:78 | You strap card. Strap chip in every date header | Push on You. From the chip it is a tab switch to You (MA:386) | Pops to You hub |
| Alarms & alerts | ui/alarms/AlarmsScreen.kt:60 | Your strap, "Alarms & alerts" row | Push (depth 2) | Pops to Your strap |
| Firmware | ui/firmware/FirmwareScreen.kt:60 | Your strap, "Firmware update" row | Push (depth 2) | Pops, blocked while installing (:68) |
| Ready screen | GlowWorkoutMode.kt:456 | Today "Start workout" pill (picks your top sport), Activity sport bubbles and "All sports", launcher shortcuts, Quick Settings tile, ongoing-notification "Start" action | Full-screen overlay sliding in from right (`PushOverlay`, MA:789) | Close X or back closes |
| Sport picker | GlowWorkoutMode.kt:600 | Ready screen sport pill, Activity "All sports" (opens straight on it), Add workout "Choose the sport", Save suggestion | Replaces the screen it came from (no animation) | Back to caller. Opened via "All sports": back closes the ready screen too (:478) |
| Live workout | GlowWorkoutMode.kt:650 | Start on ready screen, Record on an auto-detect offer, ongoing notification, capsule tap | Full-screen overlay over the whole app (MA:735) | Back minimises it into the capsule (:672) |
| Workout capsule | GlowWorkoutMode.kt:1046 | Minimising the live screen | Pill above the tab bar, morphs (MA:771) | Tap reopens live screen |
| Add a workout | GlowWorkoutMode.kt:345 | Activity Workouts "Add a workout you didn't record" | Full-screen box over everything, no transition (MA:674) | Back closes |
| Tabletop heart / workout glance | ui/glow/GlowTabletop.kt:28, GlowWorkoutMode.kt:843 | Foldable half open (MA:410-419, 759) | Automatic | n/a |

### 1.3 Sheets and dialogs

| Sheet or dialog | File:line | Opened by |
|---|---|---|
| Day calendar (sheet) | ui/dashboard/DayCalendar.kt, opened in ScreenParts.kt:110 | Tapping the day name in any tab's date header |
| What changed shows (tile chooser) | ui/glow/GlowTodayExtras.kt:135 | Today What changed "Edit" pill |
| Your goals | ui/device/GoalsDialog.kt:22 | You "Your goals" only (MA:447) |
| Sign in with Zepp (full-screen dialog with web view) | ui/zepp/ZeppSignIn.kt:89 | Your strap pair panel, Firmware Zepp account |
| Enter the key yourself | ui/device/DeviceScreen.kt:458 | Pair panel "Have the key already?" |
| Disconnect / Restart / Forget the strap | DeviceScreen.kt:102, 124, 141 | Your strap buttons |
| Install firmware confirm | ui/firmware/FirmwareScreen.kt:281 | Install button |
| Delete all alarms | ui/alarms/AlarmsScreen.kt:70 | Alarms "Delete all" |
| New / Edit alarm | AlarmsScreen.kt:204 | Add alarm, tap an alarm |
| Discard changes? | ui/device/ProfileScreen.kt:121 | Back from an edited profile |
| Delete this workout? | GlowWorkoutMode.kt:1313 | Phone workout "Delete workout" |
| Toasts (all messages) | MA:293, 300, 307, 323, 337-356, PullToSync.kt:110 | Device VM messages, permissions, export/import results |
| System: Bluetooth, location, Health Connect read and write permissions, CSV save, CSV open, GPX save, firmware file open | MA:298-361, FirmwareScreen.kt:71 | Various |

All in-app dialogs are glass popups over a blur (`ui/components/Glass.kt:149`). Back dismisses them.

### 1.4 Deep links (intent extras, MA:111-131)

| Source | Extra | Result |
|---|---|---|
| Auto-detected workout notification (`device/AutoDetectNotifier.kt:31`) | `open_workout` = start | Activity stack replaced with that workout, tab switches to Activity (MA:277-283). Other tabs keep their stacks |
| Quick Settings tile (`device/WorkoutTileService.kt:59-62`) | `show_workout` when recording, else `ready_sport` | Live screen un-minimised, or ready screen with last sport |
| Ongoing service notification (`device/StrapService.kt:185, 251`) | `show_workout`, action `ready_sport` | Same as tile |
| Launcher shortcuts (`res/xml/shortcuts.xml`) | `ready_sport` 1 (run), 52 (strength) | Ready screen. Ignored while a workout records (MA:272) |
| Health alert and body-signal notifications (`device/HealthAlertNotifier.kt:39, 60`) | none | Opens app wherever it was. No route to the alert, the metric or Recovery |
| Off-body reminder (`device/WearReminder.kt:57`) | none | Opens app |
| Debug only (MA:133-185) | `demo_data`, `probe_endpoint`, `strap_sessions`, `buzz`, `simulate_hr`, `dump_raw_days` | Dev tools |

### 1.5 Places a tap switches tabs or loses your place

| Where | What happens | File:line |
|---|---|---|
| Today Overview card body | Switches to Recovery tab and scrolls to the leading score. Clears Recovery's stack | GlowTodayScreen.kt:309, MA:536 |
| Today Overview score bars (Charge or Recovery) | Switches to Recovery tab | GlowTodayScreen.kt:331 |
| Today Overview "Sleep" bar | Switches to Sleep tab | GlowTodayScreen.kt:332, MA:530 |
| Today Sleep card | Switches to Sleep tab | GlowTodayScreen.kt:196, 217 |
| Today Activity card | Switches to Activity tab (while the Heart rate card next to it pushes a detail) | GlowTodayScreen.kt:208, MA:541 |
| Strap chip in every date header | Switches to You and opens Your strap. Back goes to You hub, then to Today, never to where you were | ScreenParts.kt:126-129, MA:386 |
| Tapping any tab in the bar, including the current one | Clears every tab's stack and closes profile, strap, alarms, firmware pages | MA:431-436 |
| Back on Recovery, Sleep, Activity or You root | Jumps to Today, not to the tab you came from | MA:382 |
| Auto-detect notification | Replaces Activity's stack and switches tab | MA:279 |
| Selecting a day on any tab | Changes the day on all four dashboards (one shared `DayViewModel`) | MA:209, 251 |
| Ready screen opened via "All sports" | Back from the picker closes the whole ready screen | GlowWorkoutMode.kt:478 |
| Live workout "back" | Minimises instead of going back | GlowWorkoutMode.kt:672 |

---

## 2. Per screen, in on-screen order

Conventions: "today" means the selected date is the current date. "rec" is the day's RecoveryResult. All four dashboards start with the same date header.

### 2.0 Date header (Today, Recovery, Sleep, Activity) `ui/dashboard/ScreenParts.kt:101`

| Element | Shows | Condition | Tap | Line |
|---|---|---|---|---|
| Wordmark | "OpenStrap." with red dot | always | none | 113-124 |
| Strap chip | Battery % (with ⚡ when charging), watch icon with coloured dot | Battery shown only when connected | Opens You > Your strap (tab switch) | 126-148 |
| Pull-to-sync gap | Strap icon turning, "Pull to sync strap", "Release to sync strap", "Syncing strap…" | While pulling or any sync runs | Pull down | 151, PullToSync.kt:129-166 |
| Day row | ‹ Today/Yesterday/EEE d MMM › | always. Next disabled on today | Day name opens calendar sheet | 152-171 |

### 2.1 Today `ui/glow/GlowTodayScreen.kt:158-218`

| # | Card | Shows | Shown when | Tap goes to | Line |
|---|---|---|---|---|---|
| 1 | Overview (glow, breathing) | Lead score in arc gauge with dot-matrix number, verdict ("Primed", "Ready", "Fully charged", "Signals off baseline"...), one-line state note ("Learning 3/14 nights", "No sleep yet", "Needs HRV data", "Strap's estimate…"). Change vs yesterday / since waking. Two score bars underneath: the other score and Sleep | always | Card: Recovery tab at lead section. Bars: Recovery tab at that section, or Sleep tab | 182, 272-336 |
| 2 | Morning check-in (dot panel) | "How do you feel this morning?" 1 to 5 pills | today and before 12:00 | Tap a pill saves, tap again clears | 185-186, GlowCheckIn.kt:24 |
| 3 | Strap alerts (dot panel, amber warning icon) | Each alert "High heart rate: 140 bpm" with time, then disclaimer | Any health alert stored for the day | Row opens Heart rate, Stress or Blood oxygen detail | 189, GlowTodayExtras.kt:51 |
| 4 | Your week (dot panel) | Steps a day, Recovery avg, Asleep a night, Workouts, change vs week before | Monday only (any Monday you browse to) | none | 192, GlowTodayExtras.kt:74, 126 |
| 5 | Sleep (glow, midnight blue) | Window, time asleep in dot matrix, "Score N" and headline, hypnogram, naps line | Sleep or naps recorded, and morning | Sleep tab | 196, 557-585 |
| 6 | What changed (dot panel) | Readiness advice sentence, then 2-up tiles: Resting HR, HRV, Blood oxygen, Skin temp, Stress, Breathing with value, unit, dot arrow, "usually N". "Edit" pill | Any tile has value or baseline, or advice exists. Tiles filtered by user choice | Tile opens metric detail (push). Edit opens chooser | 199, 482-517 |
| 7 | Habit Age (glow, orchid, drifting) | Age range, Pace, verdict ("3 years younger"), age scale, "Helping most: …". Or profile reminder, or starting point | Healthspan outcome exists | Habit Age detail, or Profile when birth year or sex missing | 202, GlowHealthspan.kt:117 |
| 8 | Heart rate (glow, beating) | Latest or live bpm, "Resting N", day curve (5-min buckets). Live switch when connected | always | Heart rate metric detail (push). Switch toggles live | 205, 435-466 |
| 9 | Activity (glow, stride) | Steps, "% of goal" or "Goal reached", "Active N of M h", "PAI N", hourly dot bars | always | Activity tab | 208, 535-554 |
| 10 | Looks like a workout (offer) | Effort under way nobody started. Record / Not now | today and an offer exists | Record starts live workout from its start | 211, GlowWorkoutMode.kt:323 |
| 11 | Looks like you worked out (suggestion) | Past stretch. Save as workout / Not a workout | today and a suggestion exists (hidden while recording, MA:263) | Save opens sport picker | 212, GlowWorkoutMode.kt:303 |
| 12 | Workouts (dot panel) | White "Start workout" pill (or running workout shortcut with pulse dot and clock), then the day's last 3 workouts | Workouts that day, or today | Pill: ready screen with top sport. Row: workout detail (push) | 215, 225-245 |
| 13 | Sleep (same card as 5) | as 5 | Sleep or naps, and not morning | Sleep tab | 217 |

Time and context logic on Today:

- `morning` = today and hour < 12 (line 185): adds check-in, puts Sleep card at position 5 instead of 13.
- `chargeLeads` (ScreenParts.kt:183-189): Charge becomes the Overview lead only on today, once Charge has a value, and 3 h after waking (or from 12:00 if no sleep). Past days always lead with Recovery.
- Monday recap (GlowTodayExtras.kt:126).
- Offer, suggestion and Start workout only on today (210-215).
- Overview colour by lead score: teal 70+, amber 50-69 or strain, coral under 50, muted teal with no score (426-432).
- No evening, night, usual-workout-time or after-workout logic anywhere.

### 2.2 Recovery `ui/glow/GlowRecoveryScreen.kt:57-97`

| # | Card | Shows | Shown when | Tap | Line |
|---|---|---|---|---|---|
| 0 | "Calculating…" panel | Only this, nothing else | rec is null (still loading, or the computation threw) | none | 79-82 |
| 1 | Body signals off baseline (dot panel, amber icon on Alert) | Signals list, causes and advice or "keeping an eye on it", likely cause, disclaimer. Trailing "Watching" on Watch | body signals state not Normal | none | 88, 175-188 |
| 2 | Recovery (glow, breathing) | Score / 100, headline, readiness sentence, 7-day trend. Trailing "Learning n/N nights", "Calibrated · +3 vs yesterday". Without a score: why, sleep note, strap's estimate with parts, strap fallback notes | always | Not tappable | 89, 104-169 |
| 3 | What drove it (dot panel, folds to top 3) | Groups BODY, SLEEP, LOAD with rating word (Optimal, Good, Fair, Pay attention), detail, bar. Notes: capped, 7-night HRV range, strap fallback, "Check: …" | contributors exist | Whole panel toggles fold | 90, 297-339 |
| 4 | Charge (glow, ember, breathing) | Now / 100, headline, "+recharged · −drained", day curve 0-100. Trailing "N at waking" or "~N at start" | always (hint text before first night or before a few hours of HR) | Not tappable | 91, 191-218 |
| 5 | Where your charge went (dot panel) | 4 flow rows with bars, highest/lowest, stress threshold sentence | Charge has a value | none | 92, 238-250 |
| 6 | Body signals (dot panel) | 2-up tiles: Resting HR, HRV, Skin temp (as deviation), Breathing, Blood oxygen, Stress, with status pill (Normal, Higher, Lower, Faster, Slower) and 7-day sparkline | always | Tile: metric detail (push) | 93, 390-433 |
| 7 | How it works (folded) | Method text, Charge battery text, strap's own score vs ours, disclaimer | always, closed by default | Toggle | 94, 471-502 |

Opened from Today, the list auto-scrolls to Recovery or Charge (`focus`, GlowLayout.kt:111-113).

### 2.3 Sleep `ui/glow/GlowSleepScreen.kt:71-108`

| # | Card | Shows | Shown when | Tap | Line |
|---|---|---|---|---|---|
| 1 | Sleep night card (glow) | Window, score in dot matrix inside horizon arc (or time asleep vs goal), "Sleep score · Good", headline, "7 h asleep · 7 h 40 in bed · woke 3×", hypnogram (scrub for readout) | always (empty text when no night) | Not tappable | 90, 127-181 |
| 2 | Stages | Awake, REM, Light, Deep minutes and % with bars | night recorded and breakdown exists | none | 92, 203-229 |
| 3 | What shaped the score | Total sleep, Continuity rating rows, guidance note | night has a score | none | 97, 232-247 |
| 4 | Sleep vitals | Tiles: Lowest heart rate, Average heart rate, HRV, Blood oxygen, Breathing, Skin temperature, each with "Higher than usual" pill or "Learning n/7 nights" | night recorded and any vital | Tile: metric detail (push). Both HR tiles open the day HR detail, not the night | 99, 251-267 |
| 5 | Night charts | Heart rate line (lowest, avg), Breathing line | night recorded and data | none (scrub) | 100, 271-294 |
| 6 | Sleep health | Regularity value and band, note, 3 dimensions Good / Not quite, midpoint | rec.regularity exists | none | 102, 413-439 |
| 7 | Breathing (disturbances) | Disturbances /h vs your usual, note | rec has breathing disturbances | none | 103, 393-406 |
| 8 | Naps | Each nap row, expands to stages and HR. Lone nap starts open | naps that day | Row toggles | 104, 310-345 |
| 9 | This week | 7-night bed/wake chart, Avg. asleep, Avg. score, Reached goal n of m, Regularity again with band and usual fall-asleep/wake times | always | none | 105, 352-386 |

No time-of-day logic. Day shown is the night ending on the selected date.

### 2.4 Activity `ui/glow/GlowActivityScreen.kt:79-131`

| # | Card | Shows | Shown when | Tap | Line |
|---|---|---|---|---|---|
| 1 | Looks like a workout (offer) | as Today | today and offer | Record / Not now | 119 |
| 2 | Start a workout | Top 3 sports as round bubbles, "All sports", Auto-detect workouts switch | today only | Bubble: ready screen. All sports: sport picker | 120, GlowWorkoutMode.kt:266 |
| 3 | Looks like you worked out (suggestion) | as Today | today and suggestion | Save / dismiss | 121 |
| 4 | Steps (glow, stride) | Steps, % of goal, hourly bars, Active hours, Distance ≈, Active kcal, divergence note, Paluch note | always | Steps metric detail (push) | 122, 137-164 |
| 5 | Active minutes | This week n / 150, Moderate, Vigorous, bar, method note | rec.activity exists | none | 123, 197-213 |
| 6 | Energy | Total ≈ kcal, At rest, Activity level (PAL), method note. "Partial day" trailing | always. Without profile: amber row "Add age, weight and height…" | Row opens Profile (push) | 124, 168-193 |
| 7 | PAI | 7-day total / 100, today +n, stacked bar per day, day labels, drop-off and to-go note | always (note only before first sync) | none | 125, 220-266 |
| 8 | Training load | Strain, Load today, vs your usual (percentile), breakdown, Fitness/Fatigue/Form, method note, Acute:chronic with "Contested", This week (strap EPOC) with Productive word and bar, "Max HR is estimated" row | always (contents depend on rec, ACWR, strap sport effect) | Max HR row opens Profile | 126, 307-371 |
| 9 | Fitness | VO2 max, Recovery (time left), Max HR, VO2 sparkline, Estimated fitness range with method note | always | none | 127, 375-413 |
| 10 | Workouts | Day's workouts, RECENT (up to 20), scrolling inside a 4-row window. "Add a workout you didn't record". "All workouts" | always | Row: workout detail. Add: Add workout screen. All: list | 128, 420-459 |

Context logic: rows 1-3 today only. PAI "drops off tomorrow" only today (254). Nothing by time of day.

### 2.5 You `ui/device/YouScreen.kt:43-132`

| # | Element | Shows | Tap | Line |
|---|---|---|---|---|
| 1 | Title "You" | | | 63 |
| 2 | Your profile | Summary "34 years, male, 72 kg, 178 cm" or prompt | Profile page | 67-78 |
| 3 | Your goals | "%,d steps · … sleep · n active hours" | Goals dialog | 81-92 |
| 4 | Device: strap card | Name, status, battery or "Charging n%", "Synced d MMM HH:mm". Or "Pair your Helio Strap" | Your strap | 94-95, 139-179 |
| 5 | Your data | Privacy line, Export CSV, Import CSV, Health Connect, Restore from Health Connect | System pickers or permission prompts | 97-113 |
| 6 | Support: Diagnostics | Line count, expands inline to log with copy | Toggle | 115-116, DeviceScreen.kt:555 |
| 7 | Load sample data (debug builds) | | Loads data | 118-120 |
| 8 | Version line | "OpenStrap x · local-first, no cloud" | none | 122-130 |

Uses Material panels (`ui/components/Cards.kt`), not the glow dot panels. Same for Your strap, Alarms, Firmware, Profile.

### 2.6 Pushed details

**Metric detail** `GlowMetricDetail.kt:108-148`. Metrics: Heart rate, Resting heart rate, Heart-rate variability, Blood oxygen, Stress, Skin temperature, Breathing rate, Steps (`ui/dashboard/Routes.kt:9-17`). No detail exists for Recovery, Charge, Sleep score, PAI, Training load, VO2 max, Active minutes, Energy, Regularity, Habit Age (own screen).

| # | Element | Shows | Condition | Line |
|---|---|---|---|---|
| 1 | Header | Title, day name, back | | 131 |
| 2 | Day / Week / Month / Year segments | | | 132 |
| 3a | Day card (glow) | Now/Latest/Resting/Total/Day average, value, "Usually N", Above/Below/Within your normal (HR: "Low n · high n", average), Live switch, chart | Day range. Live switch only for HR on today when connected | 177-257 |
| 3b | History card + Lowest/Highest | Daily average, days with data, normal band chart | Week, Month, Year | 261-344 |
| 4 | Your normal | Usual, range, 30 days, or progress to 7 days | always | 348-362 |
| 5 | About … | What it is, what moves it (MetricInfo.kt:38-71) | always | 365-372 |
| 6 | Related | Rows to other metric details | per RELATED map | 375-389 |

**Strap workout detail** `GlowWorkoutScreen.kt:49-67`: header (sport, date), Auto-detected pill, Heart rate card (avg, peak, duration, kcal, per-second chart, extra recording note), Summary (Active, Paused, Lowest HR, steps), Recovery heart rate (rating, 1 and 2 min, note, only if recovery60), Heart-rate zones (if any zone time), Training effect (Aerobic, Anaerobic, EPOC, VO₂ max, Recovery time).

**Phone workout summary** `GlowWorkoutMode.kt:1119-1321`: header ("Walk saved" when just ended, time range), time card (active time, distance, Avg HR, Max HR, Time, Paused with deltas vs your usual), Training effect worked out by OpenStrap (when load exists), From the strap (when strap also recorded), Laps (more than 1), Sets (strength), Map (route kept, "Start and end hidden when you share"), Heart rate zone line, Time in zones with your-usual marks, trim notice with Undo, Trim start and end (expands to trimmer), Export GPX / Done, Delete workout.

**All workouts** `GlowWorkoutScreen.kt:209-230`: one dot panel per day, rows.

**Habit Age** `GlowHealthspanScreen.kt:47-84`: header ("Updated Sunday · next in n days"), card (range or starting age, verdict, age scale, early note), Pace of aging (if result), What moves it (habits with effect bars), How it works (folded).

**Your strap** `DeviceScreen.kt:163-231`: header. Not paired: Pair panel with 3 steps, Sign in with Zepp, computer guide, manual key. Paired: status hero (connection label, name, address, last sync, battery ring, sync progress, firmware, failure text), key-rejected panel or Sync now / Disconnect / Cancel / Connect, Find strap (connected and supported), settings panel (Alarms & alerts, Firmware update, Keep connected in background, Restart strap, Forget this strap).

**Alarms & alerts** `AlarmsScreen.kt:60-113`: header, error panel with Try again, Phone alerts panel (Incoming calls, Notifications with buzz pattern, Off-body reminder, `PhoneAlertsPanel.kt:42`), "Reading from the strap…", Alarms list (n of max, add, delete all), Heart & oxygen (measuring interval, high HR, low HR, low SpO2 alert chips).

**Firmware** `FirmwareScreen.kt:60-117`: header, This strap (firmware, hardware, battery, update service), step panel (Check for update, Checking, Update available/Required, Downloading, Ready to install, Installing, Installed, Something went wrong), Zepp account (sign in, paste token, server, China switch), Install from a file, Privacy and safety.

**Profile** `ProfileScreen.kt:72-215`: header with Save, intro, Age, Female/Male, Units, Weight, Height, Usual activity 1-5, beta-blockers, pregnant, max HR from a test, sleep need.

### 2.7 Workout overlays

**Ready** `GlowWorkoutMode.kt:456-566`: close, sport pill, "Ready when you are", Strap status panel, GPS and keep-route switches (outdoor sports), Sets / Rest / rest-by-HR chips (strength), Target chips, Ask to end when I stop, Start (target colour).

**Live** `GlowWorkoutMode.kt:650-780`: minimise caret, sport pill, Paused/Strap/Reconnecting with dot. Heart card (beating, 10-min trace, zone, range, zone bar, time in zone, target) or with GPS a Heart / Map / Splits pager. Sets panel (strength). Stats row (Avg or Distance, Moving time, Max or Pace or Avg). Lap line. Disconnected note. Stopped / in a car prompt. Controls: Lap, Pause/Resume, hold-to-end square, "Hold the square to end".

**Add a workout** `GlowWorkoutMode.kt:345-394`: header, When was it? range chart with drag handles, Choose the sport.

---

## 3. States

| State | What the app shows today | Where |
|---|---|---|
| First run, no data | No onboarding. Today: Overview with "-" and maybe "Learning 0/14 nights", Heart rate "No heart-rate data yet. Sync your strap.", Activity 0 steps, What changed and Sleep hidden. Recovery: "Calculating…" while loading, then "OpenStrap scores Recovery once it has N good nights…" and "Charge starts after your first night…". Sleep: "No sleep recorded for this night…", "No sleep recorded in the last 7 nights." Activity: PAI, Fitness, Workouts empty notes, Energy asks for profile. Metric detail: "No … for this day yet", "Your normal shows after 7 days of data" | GlowTodayScreen.kt:459, GlowRecoveryScreen.kt:80, 134-137, 199, GlowSleepScreen.kt:136, 359, GlowActivityScreen.kt:173, 225, 410, 427, GlowMetricDetail.kt:227, 352 |
| No strap paired | Header chip shows a bare watch icon (same as disconnected). You: "Pair your Helio Strap / Scan, or sign in with Zepp…". Your strap: pair panel. Pull-to-sync: toast "The strap isn't connected" and Reject haptic. Nothing on Today points to pairing | ScreenParts.kt:133-147, YouScreen.kt:145-147, DeviceScreen.kt:172-173, 377-429, PullToSync.kt:107-110 |
| Strap disconnected | Header: no battery, no dot. Live switches hidden. You and Your strap: "Not connected" / "Connection failed" with reason and retry advice, key rejected panel. Ready: "Not connected" plus "You can start anyway…". Live: "Reconnecting", red dot, note. Capsule: "Strap reconnecting" | YouScreen.kt:151-155, DeviceScreen.kt:315-370, 541-551, GlowWorkoutMode.kt:502-510, 688, 752, 1076 |
| Strap charging | Header chip "⚡n%" in battery colour. You "Charging n%". Your strap ring with lightning and "Charging". No explanation that wear data pauses | ScreenParts.kt:135-137, YouScreen.kt:163, DeviceScreen.kt:342-352 |
| Syncing | Header gap with spinning strap icon and "Syncing strap…" on the four dashboards (every sync, pulled or not). You "Syncing…". Your strap linear progress and step text, Sync now disabled | PullToSync.kt:94-166, YouScreen.kt:165, DeviceScreen.kt:183, 331, 355-358 |
| Sync failed | Only on Your strap: "Sync failed: reason" in red. Dashboards show nothing. Pull-to-sync gives the same completion tick for failure as for success | DeviceScreen.kt:371, PullToSync.kt:86 |
| Workout in progress | Live overlay, or capsule above tab bar. Tab bar and rail take the zone colour, Activity tab dotted. Today Workouts pill becomes the running shortcut (or "Resume …" when paused). Suggestions hidden, ready requests ignored. Fold: heart glance on the standing half | MA:263, 272, 287, 439, 735-777, GlowTodayScreen.kt:232, GlowWorkoutMode.kt:1518 |
| No sleep last night | Today: Sleep card hidden, Overview note "No sleep yet", Sleep bar "-". Charge leads from noon. Recovery card: "Recovery is scored each morning from the night's sleep. Wear the strap to bed." Sleep tab: "No sleep recorded for this night…" or "No night sleep recorded, only naps." | GlowTodayScreen.kt:195, 357, ScreenParts.kt:188, GlowRecoveryScreen.kt:134, GlowSleepScreen.kt:136-137 |
| Partial day (little wear) | Only in places: Energy "Partial day" and "Covers … of wear" note. Charge "~n at start" and "Strap off overnight, Charge estimated." Steps divergence note. Steps, active hours, HR chart, What changed: not handled (gaps only) | GlowActivityScreen.kt:170, 186, 157-160, GlowRecoveryScreen.kt:197, 210 |
| Errors | Recovery computation exception returns null, so Recovery shows "Calculating…" forever and Overview is blank (DayViewModel.kt:142-144). Habit Age failure hides the card. Firmware "Something went wrong" + message. Alarms error panel with Try again. Zepp sign-in errors. Scan error text. Export/import/HC results as toasts. Health alerts and body signals use amber warning icons | FirmwareScreen.kt:162-169, AlarmsScreen.kt:94-101, ZeppSignIn.kt:277-282, DeviceScreen.kt:482-484, DeviceViewModel.kt:134-162 |

---

## 4. Motion and haptics

Timing constants: `Motion.QUICK` 140 ms, `Motion.SWIFT` 240 ms, FastOutSlowIn easing (`ui/components/Motion.kt:23-29`). Everything uses tweens except the switch knob. There is no shared-element "card opens into its detail" transition and no value count-up after sync.

### 4.1 Animations

| Animation | File:line | Spec | What it signals | Fits brief? |
|---|---|---|---|---|
| Screen push/pop (`PushContent`) | components/Motion.kt:36-47 | slide 1/4 width + fade, SWIFT tween in, QUICK fade out | Depth, forward or back | Partly. Generic slide, not card-to-detail |
| Detail pane appear (wide) | components/Adaptive.kt:56 | AnimatedVisibility default | Pane opened | Default spec |
| Tab change on tap | MA:367 | pager scroll, SWIFT tween | Tab switch | Neutral |
| Nav pill slide, accent colour, tint, icon scale 1.08 | components/Insights.kt:85, 132, 156, 167-171 | SWIFT / QUICK tweens | Selected tab, workout colour | Neutral |
| Press shrink on every clickable (0.97 to 0.98) | components/Motion.kt:51-59 | QUICK tween | Touch feedback | Neutral |
| Glow card core: Drift, Breath, Heartbeat, Stride loops | glow/GlowCard.kt:112-125, 374-410, GlowStyle.kt:21-39 | Frame loop, pauses while scrolling, off-page, or animator scale 0 | Card personality | Breaks "nothing else idles or loops" (see 4.2) |
| PulseDot in capsule and running shortcut | glow/GlowWorkoutMode.kt:1096-1106 | Infinite keyframes at bpm (scale 1.5) | Live heart rate | Fits |
| Live workout heart card Heartbeat(bpm), Still when paused | GlowWorkoutMode.kt:800 | core loop | Live heart rate | Fits |
| Sleep horizon arc sweep from 0 | glow/GlowCharts.kt:420-425 | 700 ms tween on every appear | Score fill | Breaks "not from zero on every open" |
| Glow switch | glow/GlowParts.kt:150-155 | colour tweens, knob spring damping 0.6 stiffness 500 | On/off | Breaks "no bounce" |
| Folding caret rotate | glow/GlowQuiet.kt:102, GlowSleepScreen.kt:325 | default spring | Open/closed | Fine |
| Recovery auto-scroll to section | glow/GlowLayout.kt:111-113 | animateScrollToItem | Where Today's tap landed | Useful but crosses tabs |
| Glass dialog and sheet entrance | components/Glass.kt:152-163 | 220 ms fade + rise (16 dp dialog, 120 dp sheet). No exit animation | Popup | Fine |
| Calendar month change | dashboard/DayCalendar.kt:99-105 | 220 ms slide 1/4 + fade | Month direction | Fine |
| Pull-to-sync icon turn and spin | components/PullToSync.kt:131-148 | Turns with pull, infinite 1100 ms linear spin while syncing, alpha with pull | Sync progress | Spin loops, but it is a status |
| Pull-to-sync label crossfade | PullToSync.kt:157 | default fade | Pull state | Fine |
| Panel border colour | components/Cards.kt:44-46 | 450 ms tween | Highlight change (alarms error) | Fine |
| Live workout pager page change | GlowWorkoutMode.kt:704 | animateScrollToPage | Heart / Map / Splits | Fine |
| Hold-to-end ring | GlowWorkoutMode.kt:1006-1032 | 1000 ms linear, release 200 ms back | Hold progress | Fits |
| Live screen to capsule morph | GlowWorkoutMode.kt:1489-1511 | slide 1/4 (screen) and 1/2 (capsule) + fade, SWIFT | Workout tucked away | Fits "where it came from" |
| Ready screen overlay | GlowWorkoutMode.kt:1541-1556 | slide 1/4 + fade, cut when a workout starts | Overlay | Fine |
| Indeterminate progress bars | DeviceScreen.kt:356, 478 | Material | Sync and scan running | Material look |
| No animation | Add workout and Save-suggestion overlays (MA:674-693), sport picker swap (GlowWorkoutMode.kt:477, 360) | instant | | Inconsistent with other overlays |

### 4.2 Idle loops that carry no live signal

| Card | Motion | File:line |
|---|---|---|
| Today Overview | Breath(5) | GlowTodayScreen.kt:307 |
| Today and Sleep tab sleep card | Breath(5) | GlowSleepScreen.kt:113-117 |
| Today Habit Age card | Drift | GlowHealthspan.kt:34 |
| Today Heart rate card | Heartbeat at latest stored reading, even hours old | GlowTodayScreen.kt:437 |
| Today Activity, Activity Steps | Stride scaled by goal progress | GlowTodayScreen.kt:538, GlowActivityScreen.kt:140 |
| Recovery card, Charge card | Breath(5), Breath(4) | GlowRecoveryScreen.kt:113, 194 |
| Metric detail day card, history card | Heartbeat (HR) or Breath(5), Breath(6) | GlowMetricDetail.kt:196, 264 |
| Strap workout detail heart card | Heartbeat at the past workout's average | GlowWorkoutScreen.kt:72 |
| Habit Age screen | Breath(5) | GlowHealthspanScreen.kt:53 |
| Phone workout summary card | Drift | GlowWorkoutMode.kt:1150 |
| Tabletop heart | Heartbeat at latest or live | GlowTabletop.kt:36 |

### 4.3 Haptics (phone)

| Call | File:line | Type | Signals | Fits brief? |
|---|---|---|---|---|
| Pull ratchet | PullToSync.kt:98 | SegmentFrequentTick each quarter, ContextClick at threshold | Pull distance | Ordinary gesture feedback, brief says nothing for ordinary taps |
| Sync starts | PullToSync.kt:112 | Confirm | Pull accepted | Same |
| Not connected | PullToSync.kt:109 | Reject | Can't sync | OK |
| Sync finished | PullToSync.kt:86 | SegmentTick | Gap closes, success or failure alike | Ambiguous |
| Lap | GlowWorkoutMode.kt:763 | Confirm | Lap marked | OK-ish |
| Workout ended by hold | GlowWorkoutMode.kt:1016 | LongPress | End | Matches "pulse on end", but weak type |
| Missing | n/a | n/a | Workout start, zone change, set done on the phone. These cues exist only as strap buzzes (`device/WorkoutSession.kt:288, 319, 367, 715`) | Not handled on phone |

---

## 5. Copy audit

Scope: every user-facing string literal in `ui/` and MA (debug intents, log text, file names, MIME types, keys and icon data excluded). Template parts are shown as code. Multi-line sentences built from several literals count once per literal. The `" · "` literals are separators passed to `joinToString`, so each produces interpuncts at runtime.

Rules: interpunct, semicolon, em dash (inline), em dash as empty value, health acronym unexplained on the spot, technical jargon, method or formula name on a card, alarming word, exclamation mark.

| String (verbatim) | File:line | Rule broken |
|---|---|---|
| `The strap vibrates when an alert goes off. OpenStrap isn't a medical device.` | alarms/AlarmsScreen.kt:183 | alarming (medical device) |
| `Movement, stress, caffeine and heat raise it; rest and sleep bring it down.` | dashboard/MetricInfo.kt:41 | semicolon |
| `Pressure, caffeine and too little sleep raise it; calm breathing, rest and time outdoors bring it down.` | dashboard/MetricInfo.kt:57 | semicolon |
| `The temperature of your skin under the strap. The level depends on fit and room; what matters is the change from your normal.` | dashboard/MetricInfo.kt:60 | semicolon |
| `Short walks spread across the day add up; most people aim for 7,000–10,000.` | dashboard/MetricInfo.kt:69 | semicolon |
| `Pay attention` | dashboard/Narrative.kt:18 | alarming (Pay attention) |
| `Prioritise recovery` | dashboard/Narrative.kt:157 | alarming (Prioritise) |
| `; ${strongest.factor.label.replaceFirstChar { it.lowercase() }} is a strength.` | dashboard/Narrative.kt:177 | semicolon |
| `Running low` | dashboard/Narrative.kt:186 | alarming (Running low) |
| `Depleted` | dashboard/Narrative.kt:187 | alarming (Depleted) |
| `With the strap off overnight, today started from an estimated ${it.toInt()}; you've used $used since` | dashboard/Narrative.kt:206 | semicolon |
| `REM` | dashboard/ScreenParts.kt:62 | acronym REM |
| `HRV` | dashboard/SleepPanels.kt:68 | acronym HRV |
| `How alike your sleep and wake were, minute by minute, from one day to the next over the last 7 days ` | dashboard/SleepPanels.kt:112 | method/formula (minute by minute) |
| `(${it.validPairs / 60} h compared). 100 is the same every day; half of UK Biobank adults scored 74–86.` | dashboard/SleepPanels.kt:113 | semicolon, method/formula (UK Biobank) |
| `Restarts the strap; OpenStrap reconnects by itself` | device/DeviceScreen.kt:219 | semicolon |
| `Connection failed` | device/DeviceScreen.kt:319 | alarming (failed) |
| `Sync failed: ${sync.reason}` | device/DeviceScreen.kt:371 | alarming (failed) |
| `Force-stop or uninstall Zepp so it lets go of the strap. Don't unlink the strap in Zepp: that wipes its key.` | device/DeviceScreen.kt:410 | alarming (wipes) |
| `${d.rssi} dBm` | device/DeviceScreen.kt:506 | jargon dBm |
| `Export failed: ${it.message}` | device/DeviceViewModel.kt:135 | alarming (failed) |
| `Restored %,d records from before the strap's history; left out %,d from days it has.` | device/DeviceViewModel.kt:144 | semicolon |
| `Import failed: ${it.message}` | device/DeviceViewModel.kt:146 | alarming (failed) |
| `Health Connect import failed: ${it.message}` | device/DeviceViewModel.kt:154 | alarming (failed) |
| `Health Connect export failed: ${it.message}` | device/DeviceViewModel.kt:162 | alarming (failed) |
| `Export CSV` | device/YouScreen.kt:105 | jargon CSV |
| `Import CSV` | device/YouScreen.kt:107 | jargon CSV |
| `Restore a zip made by Export CSV, for days before the strap's history` | device/YouScreen.kt:107 | jargon CSV |
| `OpenStrap ${BuildConfig.VERSION_NAME} · local-first, no cloud` | device/YouScreen.kt:124 | interpunct |
| `Connection failed` | device/YouScreen.kt:154 | alarming (failed) |
| ` · ` | device/YouScreen.kt:169 | interpunct |
| `Happens on Zepp's own page. OpenStrap keeps only the app token and user ID, and fetches your device list once.` | firmware/FirmwareScreen.kt:110 | jargon ID |
| `Sends the update server your token, user ID and the strap's firmware, hardware and serial number. Nothing else.` | firmware/FirmwareScreen.kt:111 | jargon ID |
| `The strap is restarting. OpenStrap reconnects by itself; check again afterwards in case there are more parts to install.` | firmware/FirmwareScreen.kt:157 | semicolon |
| `Something went wrong` | firmware/FirmwareScreen.kt:165 | alarming (went wrong) |
| `Required update` | firmware/FirmwareScreen.kt:186 | alarming (Required update) |
| `${it.version} · ${"%,d".format(it.length / 1024)} KB` | firmware/FirmwareScreen.kt:187 | interpunct, jargon KB |
| `Zepp only answers update checks for signed-in users. Sign in, or paste your own app token and user ID; they're stored on this phone only.` | firmware/FirmwareScreen.kt:228 | semicolon, jargon ID |
| `User ID` | firmware/FirmwareScreen.kt:258 | jargon ID |
| `Update check failed` | firmware/FirmwareViewModel.kt:104 | alarming (failed) |
| `Download failed` | firmware/FirmwareViewModel.kt:132 | alarming (failed) |
| `The file has no HMFW signature block. It's probably not a strap firmware image.` | firmware/FirmwareViewModel.kt:146 | jargon HMFW, method/formula (HMFW / signature block) |
| `Battery level unknown — reconnect the strap.` | firmware/FirmwareViewModel.kt:168 | em dash |
| `Install failed` | firmware/FirmwareViewModel.kt:189 | alarming (failed) |
| `%02d:00–%02d:00 · %,d steps` | glow/GlowActivityScreen.kt:148 | interpunct |
| `Most of the benefit is reached by about ${"%,d".format(it.stepGoal)} steps a day (Paluch et al. 2022).` | glow/GlowActivityScreen.kt:161 | method/formula (Paluch) |
| `From your resting metabolism (Mifflin-St Jeor), your steps each minute and, for effort without steps, your heart rate.` | glow/GlowActivityScreen.kt:185 | method/formula (Mifflin / resting metabolism) |
| `That's an unusual total for a full day; check your profile and how the strap was worn.` | glow/GlowActivityScreen.kt:187 | semicolon |
| `${e.hrAssistedMinutes} min HR-assisted.` | glow/GlowActivityScreen.kt:188 | acronym HR |
| ` (${a.mvpa.fromHeartRate} min today, HR-assisted).` | glow/GlowActivityScreen.kt:210 | acronym HR |
| `PAI` | glow/GlowActivityScreen.kt:223 | acronym PAI |
| `PAI shows after the strap's next sync. It counts minutes your heart rate is raised, harder minutes counting more.` | glow/GlowActivityScreen.kt:225 | acronym PAI |
| `You're at or above $PAI_TARGET. Keeping it there week after week is what PAI is built around.` | glow/GlowActivityScreen.kt:261 | acronym PAI |
| `${PAI_TARGET - total.roundToInt()} to go. Brisk walking adds a little; anything that gets you out of breath adds more.` | glow/GlowActivityScreen.kt:262 | semicolon |
| `${it}th pct` | glow/GlowActivityScreen.kt:317 | acronym pct |
| `${fromWorkouts + everyday} today · $fromWorkouts workouts · $everyday everyday` | glow/GlowActivityScreen.kt:319 | interpunct |
| `Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue.` | glow/GlowActivityScreen.kt:326 | semicolon, method/formula (42- and 7-day) |
| `(${r.maxHr.toInt()} bpm${if (r.maxHrTested) ", from your test" else ""}). Every workout minute counts; outside workouts, ` | glow/GlowActivityScreen.kt:330 | semicolon |
| `only moving minutes at 35 % of reserve or more, or brisk walking (60+ steps a minute) from 20 %. Strain runs 0–21 on a fixed curve: an hour at 60 % of reserve is about 7.` | glow/GlowActivityScreen.kt:331 | method/formula (0–21 / fixed curve / of reserve) |
| `Acute:chronic` | glow/GlowActivityScreen.kt:337 | method/formula (Acute:chronic) |
| `Contested` | glow/GlowActivityScreen.kt:339 | alarming (Contested) |
| `This week (strap EPOC)` | glow/GlowActivityScreen.kt:349 | acronym EPOC, method/formula (EPOC) |
| `Below productive` | glow/GlowActivityScreen.kt:352 | alarming (Below productive) |
| `Above productive` | glow/GlowActivityScreen.kt:353 | alarming (Above productive) |
| `Productive range $low–$high, from the strap (updated ${shortDate(effect.end)}). That is EPOC, on a different scale from today's load.` | glow/GlowActivityScreen.kt:359 | acronym EPOC, method/formula (EPOC) |
| `Max HR is estimated. Add your age for accurate effort numbers.` | glow/GlowActivityScreen.kt:364 | acronym HR |
| `VO2 max` | glow/GlowActivityScreen.kt:383 | acronym VO2 |
| `Max HR` | glow/GlowActivityScreen.kt:389 | acronym HR |
| `Estimated fitness (VO2 max)` | glow/GlowActivityScreen.kt:396 | acronym VO2 |
| `From your age, sex, BMI, resting heart rate (${est.restingHr.toInt()} bpm, while asleep) and usual activity ` | glow/GlowActivityScreen.kt:398 | acronym BMI, method/formula (BMI) |
| `(Jurca et al. 2005). Typical error is about ±5 ml/kg/min. Updated weekly.` | glow/GlowActivityScreen.kt:399 | method/formula (Jurca / ml/kg) |
| ` ${it.label} for your age and sex (FRIEND 2022, treadmill).` | glow/GlowActivityScreen.kt:400 | method/formula (FRIEND 20) |
| `VO2 max appears after a workout the strap can estimate it from.` | glow/GlowActivityScreen.kt:407 | acronym VO2 |
| `Record a workout on the strap to see VO2 max and recovery time.` | glow/GlowActivityScreen.kt:410 | acronym VO2 |
| ` · ` | glow/GlowActivityScreen.kt:479 | interpunct |
| `No VO2max from workouts yet · counted as typical` | glow/GlowHealthspan.kt:73 | interpunct, acronym VO2max |
| `Not enough data yet · counted as typical` | glow/GlowHealthspan.kt:73 | interpunct |
| ` · most people your age do none` | glow/GlowHealthspan.kt:77 | interpunct |
| ` · aim for ${Healthspan.STRENGTH_TARGET_MINUTES}` | glow/GlowHealthspan.kt:78 | interpunct |
| `${v.roundToInt()} ml/kg/min · typical at $age: ${ref?.roundToInt()}` | glow/GlowHealthspan.kt:80 | interpunct, method/formula (ml/kg) |
| `${"%,d".format(v.roundToInt())} a day · typical: ${"%,d".format(((ref ?: 0f) / 100).roundToInt() * 100)}` | glow/GlowHealthspan.kt:81 | interpunct |
| ` · aim for 8,000–10,000` | glow/GlowHealthspan.kt:82 | interpunct |
| `${v.roundToInt()} · typical: ${ref?.roundToInt()}` | glow/GlowHealthspan.kt:83 | interpunct |
| `${v.roundToInt()} bpm asleep · typical: about ${ref?.roundToInt()}` | glow/GlowHealthspan.kt:84 | interpunct |
| `${m / 60} h ${m % 60} m · counts only below 6 h 30 m` | glow/GlowHealthspan.kt:85 | interpunct |
| `Holding back most: ` | glow/GlowHealthspan.kt:92 | alarming (Holding back) |
| `Updated today · next Sunday` | glow/GlowHealthspanScreen.kt:39 | interpunct |
| `Updated Sunday · next tomorrow` | glow/GlowHealthspanScreen.kt:40 | interpunct |
| `Updated Sunday · next in $next days` | glow/GlowHealthspanScreen.kt:41 | interpunct |
| `${paceText(it)} · ${paceLabel(it)}` | glow/GlowHealthspanScreen.kt:90 | interpunct |
| `Fitness terms capped: resting heart rate, steps and VO2max together count for at most 3 years either way.` | glow/GlowHealthspanScreen.kt:155 | acronym VO2max |
| `Low ${s.minOf { it.y }.toInt()} · high ${s.maxOf { it.y }.toInt()}` | glow/GlowMetricDetail.kt:220 | interpunct |
| `%02d:00–%02d:00 · %,d steps` | glow/GlowMetricDetail.kt:237 | interpunct |
| `${metric.format(v)} ${metric.unit} · ${clock(window.first + i * bucket)}` | glow/GlowMetricDetail.kt:251 | interpunct |
| `${metric.format(v)} ${metric.unit} · $label` | glow/GlowMetricDetail.kt:295 | interpunct |
| `From ${normal.days} days. Most of your days fall inside this range; outside it is worth a look, not a worry.` | glow/GlowMetricDetail.kt:360 | semicolon |
| `Calibrated · ` | glow/GlowRecoveryScreen.kt:122 | interpunct |
| `OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate.` | glow/GlowRecoveryScreen.kt:136 | acronym HRV |
| `Last night's HRV and resting heart rate didn't have enough clean readings, so there's no Recovery score today. Sleep alone isn't readiness.` | glow/GlowRecoveryScreen.kt:137 | acronym HRV |
| `Sleep ${sleep.score} · ${sleep.detail}` | glow/GlowRecoveryScreen.kt:141 | interpunct |
| `Strap's estimate · ${Narrative.zeppReadinessRating(strapScore).label}` | glow/GlowRecoveryScreen.kt:146 | interpunct |
| `HRV` | glow/GlowRecoveryScreen.kt:148 | acronym HRV |
| `resting HR` | glow/GlowRecoveryScreen.kt:148 | acronym HR |
| ` · ` | glow/GlowRecoveryScreen.kt:151 | interpunct |
| `. Shown for reference; it never enters OpenStrap's score.` | glow/GlowRecoveryScreen.kt:151 | semicolon |
| `HRV ${it.toInt()} ms (strap fallback)` | glow/GlowRecoveryScreen.kt:153 | acronym HRV, method/formula (strap fallback) |
| `Resting HR ${it.toInt()} bpm (strap fallback)` | glow/GlowRecoveryScreen.kt:154 | acronym HR, method/formula (strap fallback) |
| `${signed(c.recharged, +1)} · ${signed(drained.toFloat(), -1)}` | glow/GlowRecoveryScreen.kt:209 | interpunct |
| `${v.toInt()} · ${clock(day.first + i * 15 * 60L)}` | glow/GlowRecoveryScreen.kt:214 | interpunct |
| `Highest ${c.highest.toInt()} · lowest ${c.lowest.toInt()} this day` | glow/GlowRecoveryScreen.kt:247 | interpunct |
| `Capped: one body signal is far from your normal, so good sleep can't fully make up for it.` | glow/GlowRecoveryScreen.kt:330 | alarming (Capped) |
| `7-night HRV is $word your normal range` | glow/GlowRecoveryScreen.kt:333 | acronym HRV |
| `HRV ${it.toInt()} ms (strap fallback): our own reading wasn't clean enough last night.` | glow/GlowRecoveryScreen.kt:335 | acronym HRV, method/formula (strap fallback) |
| `Resting HR ${it.toInt()} bpm (strap fallback): our own reading wasn't clean enough last night.` | glow/GlowRecoveryScreen.kt:336 | acronym HR, method/formula (strap fallback) |
| `Resting HR` | glow/GlowRecoveryScreen.kt:377 | acronym HR |
| `HRV` | glow/GlowRecoveryScreen.kt:378 | acronym HRV |
| `Your normal builds over the first week of wear; until then each signal shows its week.` | glow/GlowRecoveryScreen.kt:395 | semicolon |
| `Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV, resting heart ` | glow/GlowRecoveryScreen.kt:478 | acronym HRV |
| `the previous days were. The score is a weighted mix; one signal far from normal caps it.` | glow/GlowRecoveryScreen.kt:480 | semicolon, method/formula (weighted mix) |
| `The strap's own score · ${Narrative.zeppReadinessRating(strapScore).label}` | glow/GlowRecoveryScreen.kt:491 | interpunct |
| `Sleep score · ` | glow/GlowSleepScreen.kt:156 | interpunct |
| `asleep · goal ${formatDuration(goal)}` | glow/GlowSleepScreen.kt:158 | interpunct |
| ` · ` | glow/GlowSleepScreen.kt:171 | interpunct |
| `${names[st.row]} · ${at(st.from)}–${at(st.to)} · $mins min` | glow/GlowSleepScreen.kt:197 | interpunct |
| `Lowest ${hr.minOf { it.y }.toInt()} · avg ${hr.map { it.y }.average().toInt()} bpm` | glow/GlowSleepScreen.kt:279 | interpunct |
| `${v.toInt()} bpm · ${clock(window.first + i * 5 * 60L)}` | glow/GlowSleepScreen.kt:282 | interpunct |
| `${v.toInt()} br/min · ${clock(window.first + i * 5 * 60L)}` | glow/GlowSleepScreen.kt:290 | interpunct |
| `${clock(nap.start)} – ${clock(nap.end)} · ${formatDuration(nap.end - nap.start)}` | glow/GlowSleepScreen.kt:322 | interpunct |
| `Heart rate · lowest ${hr.minOf { it.y }.toInt()} bpm` | glow/GlowSleepScreen.kt:339 | interpunct |
| `How alike your sleep and wake times were, minute by minute, across the last 7 days (${reg.validPairs / 60} h compared).` | glow/GlowSleepScreen.kt:422 | method/formula (minute by minute) |
| `Not enough wear for the regularity index yet, so this is how much your sleep's midpoint moved this week.` | glow/GlowSleepScreen.kt:423 | method/formula (regularity index) |
| `Not quite` | glow/GlowSleepScreen.kt:429 | alarming (Not quite) |
| `, outside 01:30–04:30. Shown for interest; it isn't counted.` | glow/GlowSleepScreen.kt:435 | semicolon |
| `High heart rate: ${a.value} bpm` | glow/GlowTodayExtras.kt:41 | alarming (High heart rate) |
| `Low heart rate: ${a.value} bpm` | glow/GlowTodayExtras.kt:42 | alarming (Low heart rate) |
| `High stress: ${a.value}` | glow/GlowTodayExtras.kt:43 | alarming (High stress) |
| `Low blood oxygen: ${a.value}%` | glow/GlowTodayExtras.kt:44 | alarming (Low blood oxygen) |
| `Irregular heart rhythm` | glow/GlowTodayExtras.kt:45 | alarming (Irregular heart) |
| `Alert from the strap` | glow/GlowTodayExtras.kt:46 | alarming (Alert from) |
| `Strap alerts` | glow/GlowTodayExtras.kt:53 | alarming (Strap alerts) |
| `If you feel unwell, checking in with a healthcare professional may be helpful. ${org.openstrap.app.data.BodySignalsCopy.DISCLAIMER}` | glow/GlowTodayExtras.kt:61 | alarming (healthcare professional / unwell) |
| `Needs HRV data` | glow/GlowTodayScreen.kt:359 | acronym HRV |
| `Strap's estimate, no HRV yet` | glow/GlowTodayScreen.kt:366 | acronym HRV |
| `${v.toInt()} bpm · ${clock(state.dayStart + i * 5 * 60L)}` | glow/GlowTodayScreen.kt:462 | interpunct |
| `Resting HR` | glow/GlowTodayScreen.kt:485 | acronym HR |
| `HRV` | glow/GlowTodayScreen.kt:486 | acronym HRV |
| `PAI ${it.total.roundToInt()}` | glow/GlowTodayScreen.kt:546 | acronym PAI |
| `%02d:00–%02d:00 · %,d steps` | glow/GlowTodayScreen.kt:550 | interpunct |
| `${if (state.naps.size == 1) "1 nap" else "${state.naps.size} naps"} · ${formatDuration(total)} · ` | glow/GlowTodayScreen.kt:578 | interpunct |
| ` · ` | glow/GlowWorkoutMode.kt:503 | interpunct |
| `Only during this workout, for distance, pace and the map. Map tiles come from OpenFreeMap, which sees roughly where the map is; its Route only button loads none.` | glow/GlowWorkoutMode.kt:515 | semicolon |
| `Off · distance from the strap's steps (≈)` | glow/GlowWorkoutMode.kt:515 | interpunct |
| `Off · no distance without it` | glow/GlowWorkoutMode.kt:515 | interpunct |
| `Off · only totals are kept, the route is discarded` | glow/GlowWorkoutMode.kt:520 | interpunct |
| `Rest · the strap buzzes when it's over` | glow/GlowWorkoutMode.kt:529 | interpunct |
| `Target · the strap buzzes if you drift out` | glow/GlowWorkoutMode.kt:535 | interpunct |
| `Lap ${r.workout.lapMarks.size + 1} · ${workoutClock(lap)}` | glow/GlowWorkoutMode.kt:746 | interpunct |
| `bpm · last 10 min behind` | glow/GlowWorkoutMode.kt:813 | interpunct |
| `Zone ${it + 1} · ${ZONE_NAMES[it]}` | glow/GlowWorkoutMode.kt:819 | interpunct |
| `Target ${t.label.removePrefix("Zone ")} · strap buzzes if you drift` | glow/GlowWorkoutMode.kt:829 | interpunct |
| `Set ${r.sets.size} · ${workoutClock(now - r.sets.last().start)}` | glow/GlowWorkoutMode.kt:871 | interpunct |
| `Resting after set $done · ${workoutClock((r.restUntil!! - now).coerceAtLeast(0))}` | glow/GlowWorkoutMode.kt:872 | interpunct |
| `Rest over · set ${done + 1} next` | glow/GlowWorkoutMode.kt:874 | interpunct |
| `${Workouts.sportName(r.workout.sportType)} · ${workoutClock(active)}` | glow/GlowWorkoutMode.kt:1069 | interpunct |
| `Zone ${zone + 1} · ${ZONE_NAMES[zone]}` | glow/GlowWorkoutMode.kt:1074 | interpunct |
| `Avg HR` | glow/GlowWorkoutMode.kt:1164 | acronym HR |
| `Max HR` | glow/GlowWorkoutMode.kt:1165 | acronym HR |
| `From your heart rate, on the strap's 0–5 scale; EPOC (ml/kg) as the strap reports it, not daily load. ` | glow/GlowWorkoutMode.kt:1181 | semicolon, acronym EPOC, method/formula (0–5 scale / EPOC / ml/kg) |
| `VO₂ max needs 10 minutes of steady running with GPS.` | glow/GlowWorkoutMode.kt:1182 | acronym VO₂ |
| `VO₂ max is worked out for outdoor runs only.` | glow/GlowWorkoutMode.kt:1183 | acronym VO₂ |
| `The strap recorded this workout too; training effect and recovery are its own figures.` | glow/GlowWorkoutMode.kt:1190 | semicolon |
| `Export GPX` | glow/GlowWorkoutMode.kt:1303 | jargon GPX |
| `${clock(from)} – ${clock(to)} · ${workoutClock(to - from)}` | glow/GlowWorkoutMode.kt:1367 | interpunct |
| `Resume ${sport.lowercase()} · $clock` | glow/GlowWorkoutMode.kt:1530 | interpunct |
| `$sport · $clock` | glow/GlowWorkoutMode.kt:1530 | interpunct |
| ` · ` | glow/GlowWorkoutScreen.kt:78 | interpunct |
| `${v.toInt()} bpm · ${clock(first + i * step)}` | glow/GlowWorkoutScreen.kt:102 | interpunct |
| `Lowest HR` | glow/GlowWorkoutScreen.kt:121 | acronym HR |
| `Low` | glow/GlowWorkoutScreen.kt:136 | alarming (Low) |
| `fitter heart; under 12 bpm in the first minute is on the low side. Compare it across similar workouts.` | glow/GlowWorkoutScreen.kt:148 | semicolon |
| `You finished at ${a.endHeartRate ?: "—"} bpm, close to resting, so there was little to recover from. ` | glow/GlowWorkoutScreen.kt:150 | em dash |
| `Training effect runs 0–5 for each system; EPOC (ml/kg) is the strap's measure of what the workout took, not daily load. VO₂ max shows up once the strap has enough data to estimate it.` | glow/GlowWorkoutScreen.kt:186 | semicolon, acronym EPOC VO₂, method/formula (EPOC / ml/kg / runs 0–5) |
| `EPOC` | glow/GlowWorkoutScreen.kt:199 | acronym EPOC, method/formula (EPOC) |
| `VO₂ max` | glow/GlowWorkoutScreen.kt:202 | acronym VO₂ |
| `user ID, then asks Zepp once for your devices.` | zepp/ZeppSignIn.kt:131 | jargon ID |
| `Keep the strap on its charger near the phone. Don't unlink it in the Zepp app: that wipes this key from the strap.` | zepp/ZeppSignIn.kt:230 | alarming (wipes) |
| `The app token and user ID stay on this phone only, for firmware checks.` | zepp/ZeppSignIn.kt:235 | jargon ID |
| `${e.message}. Zepp may be having trouble; try again in a few minutes.` | zepp/ZeppSignIn.kt:280 | semicolon |

Em dash used alone as the empty-value placeholder (one row each, same string `—`): dashboard/SleepPanels.kt:132, device/DeviceScreen.kt:332, device/DeviceScreen.kt:350, glow/GlowActivityScreen.kt:154, glow/GlowActivityScreen.kt:155, glow/GlowActivityScreen.kt:181, glow/GlowActivityScreen.kt:317, glow/GlowActivityScreen.kt:383, glow/GlowActivityScreen.kt:385, glow/GlowActivityScreen.kt:389, glow/GlowQuiet.kt:234, glow/GlowRecoveryScreen.kt:417, glow/GlowSleepScreen.kt:373, glow/GlowSleepScreen.kt:398, glow/GlowSleepScreen.kt:417, glow/GlowSleepScreen.kt:418, glow/GlowSleepScreen.kt:429, glow/GlowTodayExtras.kt:100, glow/GlowTodayExtras.kt:101, glow/GlowTodayExtras.kt:104, glow/GlowWorkoutMode.kt:727, glow/GlowWorkoutMode.kt:738, glow/GlowWorkoutMode.kt:739, glow/GlowWorkoutMode.kt:933, glow/GlowWorkoutMode.kt:1164, glow/GlowWorkoutMode.kt:1165, glow/GlowWorkoutMode.kt:1200, glow/GlowWorkoutScreen.kt:119, glow/GlowWorkoutScreen.kt:120, glow/GlowWorkoutScreen.kt:121, glow/GlowWorkoutScreen.kt:197, glow/GlowWorkoutScreen.kt:198, glow/GlowWorkoutScreen.kt:199, glow/GlowWorkoutScreen.kt:202, glow/GlowWorkoutScreen.kt:203

### 5.1 Totals

| Measure | Count |
|---|---|
| User-facing string literals, live code | 1209 |
| Literals breaking at least one rule | 223 (188 in the table above plus 35 placeholder dashes) |
| Interpunct | 64 |
| Semicolon | 26 |
| Em dash inline | 2 |
| Em dash as empty value | 35 |
| Health acronym, by literal (HR 12, HRV 12, VO2 or VO₂ 10, EPOC 5, PAI 4, BMI 1, REM 1, pct 1). A literal can hold two | 45 |
| Technical jargon (CSV, GPX, ID, KB, dBm, HMFW) | 13 |
| Method or formula name | 24 |
| Alarming word | 34 (includes failure toasts, strap alert labels, "Pay attention", "Depleted") |
| Exclamation mark | 0 |

Not in the rule list but against voice rule 3 (no cheering, no "you should"): "Primed", "Fully charged", "Great night", "Excellent", "A good day to push hard.", "Goal reached" (Narrative.kt:154, 184, 76, 164, GlowWorkoutScreen.kt:133, GlowTodayScreen.kt:543). Imperatives that read as "you should": "Consider an easier day.", "Favour rest, light movement and an early night.", "Wind down and get to bed early." (Narrative.kt:163, 167, 212).

### 5.2 Copy from `data/` that the UI displays (not counted above)

| String | File:line | Rule broken |
|---|---|---|
| `Body signals off baseline` | data/BodySignalsCopy.kt:9 | jargon ("baseline"), alarming |
| `Signals off baseline` (Today Overview verdict) | data/BodySignalsCopy.kt:10 | jargon, alarming |
| `HRV well below your normal` | data/BodySignals.kt:116 | acronym |
| `${rhr.toInt()} bpm · ${signed(delta, 0)} vs usual` | data/Recovery.kt:410 | interpunct |
| `HRV: ours ${own.toInt()} ms, the strap's ${strap.toInt()} ms` | data/Recovery.kt:420 | acronym, method detail on card |
| `Resting HR: ours ${rhr.toInt()} bpm, the strap's ${strapRhr.toInt()} bpm` | data/Recovery.kt:423 | acronym |
| `Lowest HR ${formatMinutes(after)} before waking` | data/Recovery.kt:441 | acronym |
| `Load ${yesterday.toInt()} · usual ${chronic.toInt()}` | data/Recovery.kt:478 | interpunct |
| `%,d steps · %s sleep · %d active hours` (You goals summary) | data/settings/Goals.kt:16 | interpunct |
| Factor labels `HRV balance`, `Recovery index` | data/Recovery.kt:254, 262 | acronym, unexplained term |

---

## 6. Findability problems

| # | Problem | Evidence |
|---|---|---|
| 1 | Today's cards are inconsistent about where a tap goes: Heart rate and What changed push a detail inside Today, while Overview, its score bars, Sleep and Activity switch tabs. The tab switch clears the target stack and back then goes to Today root, not to the card | GlowTodayScreen.kt:182, 196, 205, 208, MA:530-541, 382 |
| 2 | Tapping any tab, even the current one, wipes every tab's stack and closes You sub-pages. One stray tap loses a metric detail several levels deep | MA:431-436 |
| 3 | The same six body metrics appear twice with different rules: Today "What changed" (vs last 7 days, dot arrows, "usually N") and Recovery "Body signals" (vs 30-day normal, Higher/Lower/Faster/Slower pills, skin temp as deviation). Sleep vitals is a third set with "Higher than usual" | GlowTodayScreen.kt:482-517, GlowRecoveryScreen.kt:363-433, SleepPanels.kt:45-85 |
| 4 | Recovery vs Readiness, and "Recovery" used for four things: the tab and score, the strap's "Recovery" time in Fitness (Activity), "Recovery heart rate" (workout), "Recovery time" (training effect), plus factor "Recovery index". Body text still says "readiness" ("Sleep alone isn't readiness.", "Strap's estimate" of readiness, "Recovered enough…") | GlowActivityScreen.kt:384, GlowWorkoutScreen.kt:139, 203, GlowRecoveryScreen.kt:137, data/Recovery.kt:262 |
| 5 | Settings are buried in content or in the strap page: Alarms and phone alerts (calls, notifications, off-body reminder) are You > Your strap > Alarms & alerts (3 taps from You, 3 from Today via the chip). Zepp account lives inside Firmware. Auto-detect workouts switch only on Activity's Start panel, which is shown only on today. Tile choice only via Today's "Edit" | MA:450-473, PhoneAlertsPanel.kt, FirmwareScreen.kt:207, GlowActivityScreen.kt:120, GlowWorkoutMode.kt:278 |
| 6 | Two sleep targets with different names and homes: "Sleep need" (Profile, 7-9 h, drives "Total sleep" rating) and sleep goal (Goals dialog, drives the Sleep arc and "Reached n of m") | ProfileScreen.kt:210, GoalsDialog.kt:42, Narrative.kt:106, GlowSleepScreen.kt:146, 366 |
| 7 | Habit Age (code and docs call it Healthspan) has no tab home. The card is only on Today and only when computed, and its detail is only reachable from that card | GlowTodayScreen.kt:202, MA:521, 629 |
| 8 | Regularity appears twice on the Sleep tab: "Sleep health" panel and "This week" panel, with different notes and fallback (± min) | GlowSleepScreen.kt:413-439, 352-386 |
| 9 | "Breathing" names two things on Sleep: breathing rate (tile, chart) and breathing disturbances (card titled "Breathing") | GlowSleepScreen.kt:287, 395, SleepPanels.kt:74 |
| 10 | Workouts, Start workout, offer and suggestion cards are on both Today and Activity. Today shows only the last 3 of the day. "All workouts" appears only once there are more than today's plus 20 recent workouts, so most users never see a full list | GlowTodayScreen.kt:211-215, GlowActivityScreen.kt:119-128, 452 |
| 11 | No detail screen for the app's own scores (Recovery, Charge, Sleep score, PAI, Training load, VO2 max, Active minutes, Energy). Their method text sits on the dashboard cards instead, and Steps detail is reachable only from Activity, not from Today's Activity card | Routes.kt:9-17, GlowActivityScreen.kt:161, 185, 326-331 |
| 12 | Strap status is in five places with different wording: header chip, You strap card, Your strap hero, Firmware "This strap", ready screen panel. The header chip shows the same bare icon for "not paired" and "disconnected" | ScreenParts.kt:126-148, YouScreen.kt:139-179, DeviceScreen.kt:311-374, FirmwareScreen.kt:84-91, GlowWorkoutMode.kt:496-512 |
| 13 | Live heart-rate switch exists in three places (Today Heart rate card, HR metric detail on today, tabletop) | GlowTodayScreen.kt:446, GlowMetricDetail.kt:207, GlowTabletop.kt:43 |
| 14 | The strap's own readiness appears in three places (Today Overview as estimate, Recovery card when withheld, folded How it works) | GlowTodayScreen.kt:284, GlowRecoveryScreen.kt:142-152, 487-499 |
| 15 | The Profile page opens from three tabs (You, Today, Activity) but Goals only from You | MA:444, 477, 544, 640 |
| 16 | Time-gated content cannot be found later: morning check-in only before 12:00 on today, weekly recap only on Mondays, PAI drop-off only on today | GlowTodayScreen.kt:185, GlowTodayExtras.kt:126, GlowActivityScreen.kt:254 |
| 17 | Health alert and body-signal notifications open the app with no route, so the alert is found only if Today happens to be showing that day | device/HealthAlertNotifier.kt:39, 60 |
| 18 | Sleep vitals HR tiles open the whole-day HR detail, not the night | GlowSleepScreen.kt:254, SleepPanels.kt:65-66 |
| 19 | Load naming drifts: "Training load", "Strain", "Load today", "This week (strap EPOC)", "Previous day's load", "Fitness / Fatigue / Form" (Fitness is also a panel title next to it) | GlowActivityScreen.kt:309-359, 381 |
| 20 | You, Your strap, Alarms, Firmware and Profile use the Material look (Panel, outlined buttons, chips) while the dashboards use glow, so the app feels like two products at the strap boundary | YouScreen.kt, DeviceScreen.kt, Cards.kt |
