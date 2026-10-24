# Design v2: review

Step 10 of the [spec](spec.md#81-order). It checks the build against the brief's measures in
[How we judge it](brief.md#how-we-judge-it): the morning answer, every screen's purpose in one sentence, and no acronym
without an explanation one tap away. Before and after screenshots live in `docs/screenshots/`. Paths are relative to
`app/src/main/kotlin/org/openstrap/app/`.

---

## 1. Screens and their purpose

### Tabs

| Screen | Purpose |
|---|---|
| Today | Answers "how am I right now?" for the moment of the day, with the one card that matters most on top. |
| Recovery | Shows how your body is coping: Recovery, Charge, the body signals against your normal, and Habit Age. |
| Sleep | Shows how last night went and how regular your sleep is. |
| Activity | Shows how much you moved and trained, and starts or lists workouts. |

### Details (pushed on the current tab)

| Screen | Purpose |
|---|---|
| Metric detail | One number over time against your normal (heart rate, resting heart rate, HRV, blood oxygen, stress, skin temperature, breathing rate, steps). |
| Score detail | The same template for a score: Recovery, Charge, Sleep score, Sleep regularity, Naps, Heart points, Active minutes, Load, VO2 max, Time to recover, Energy, Distance. |
| Recovery and Sleep screens | The Recovery or Sleep tab's screen, opened from a card on another tab so no card switches tab. |
| Week story | Last Monday to Sunday day by day, opened from Your week on Today. |
| Habit Age | The weekly Habit Age estimate, its pace, and which habits move it, or why it's withheld. |
| Workout detail | One workout: its heart rate, zones, route, splits and effect, with trim and undo for phone workouts. |
| All workouts | Every workout, newest first, grouped by day. |

### Workout screens

| Screen | Purpose |
|---|---|
| Ready | Confirms the sport and the strap before Start. |
| Sport picker | Picks the sport, searchable, for a new workout or one saved after the fact. |
| Live | Shows the workout as it records: heart rate in its zone, or the map, with time, distance and the controls. |
| Fold glance | The live heart card on the standing half of a half-open foldable, readable from a step away. |
| Right after (calm down) | Shows what the workout did and how fast your heart rate is dropping. |
| Add a workout | Saves a stretch nobody started, by dragging its start and end on the day's heart rate. |
| Notification | Time, distance, pace and heart rate during a workout, with Pause and Lap, outside the app. |

### You, Strap and the pages under them

| Screen | Purpose |
|---|---|
| You page | Who you are and what happens to your data: profile, goals, copies, Health Connect, diagnostics. |
| Profile | Age, sex, weight and height, which every effort calculation uses. |
| Goals | Steps, active hours and sleep to aim for each day. |
| Diagnostics | The strap's protocol log, to copy into a bug report. |
| Strap page | The strap's state, Sync now and Find strap, and every strap setting. With no strap paired it becomes the pairing steps (see below), so its purpose fits one sentence per state but not one for both. |
| Alarms | The strap's alarms, each with its days and a switch. |
| Phone alerts | Which calls and notifications buzz the strap. |
| Health alerts | How often the strap measures heart rate, and its heart rate and blood oxygen alerts. |
| Firmware | What the strap runs, and checking for or installing an update. |
| Zepp account | The Zepp sign-in the firmware check uses: sign in, paste a token, or remove it. |

### Pairing

| Screen | Purpose |
|---|---|
| Pairing steps | Gets a new strap paired: the Strap page's scan and Zepp sign-in, with the same steps shown as Today's hero until it's done. |
| Zepp sign-in | Fetches your strap's key from your Zepp account. |
| Enter the key yourself | Pairs with an address and key you already have. |

### Sheets and dialogs

| Screen | Purpose |
|---|---|
| Info sheets (group) | From (i) on every card and detail: what the number means, where you are on its scale, then how it's worked out, what moves it and why it might be off. |
| All metrics sheet | Every metric for the day in one list, each opening its detail. Built, but its header button is hidden for now (spec 3.1). |
| Edit Today | Hides Today's cards and pins up to two under the hero. |
| Calendar | Picks a day, with days that have data lit. |
| Metric switcher | Moves between sibling details in the same group. |
| Partial day | Explains the "Partial day" tag: how much wear the day's figures cover and what that lowers. |
| Confirmations | Asks before something that can't be undone, like deleting a workout or forgetting the strap. |

---

## 2. Acronyms

Every short form a user can see, where it shows, and where it's explained. "Sheet" means the (i) info sheet of that
card or detail, in `ui/dashboard/InfoCopy.kt` unless noted.

| Short form | Where it shows | Where it's explained |
|---|---|---|
| HRV | Today (Your body this morning, Right now), Recovery tab body signals, Sleep tab vitals, Recovery's What drove it, its own detail | HRV sheet: "Heart rate variability, the small changes in time between heartbeats". Recovery sheet now says "HRV (heart rate variability)" too. |
| VO2 max | Activity tab, its detail, workout detail, Habit Age | VO2 max sheet ("The most oxygen your body can use in hard effort"). Workout detail spells it out in place. Habit Age's How it works now says it too. |
| ml/kg/min | VO2 max detail, Habit Age | VO2 max sheet now spells it out. Workout detail says "ml per kg per minute" in place. |
| PAI | Heart points sheet only | Spelled out there: Personal Activity Intelligence. Cards say Heart points. |
| EPOC | Workout detail Load line, Load and Time to recover sheets | Explained in place each time: "the extra oxygen your body uses afterwards to recover". |
| TRIMP | Load sheet only | Spelled out there: Banister's training impulse. |
| Sleep Regularity Index | Sleep regularity sheet only | Spelled out. Cards say Sleep regularity, never SRI. |
| REM | Sleep score detail, stage list | Sleep score sheet now says "REM (rapid eye movement), when most dreams happen". |
| bpm | Every heart rate figure, workouts, notification | Heart rate sheet now says "in beats per minute (bpm)". |
| ms | HRV figures | HRV sheet now says "in milliseconds (ms)". |
| kcal | Energy card and detail, workouts | Energy sheet now says "in kilocalories (kcal)". |
| br/min, /min | Breathing rate | Breathing rate sheet: "How many breaths you take a minute". |
| km, m, kg, cm, lb, °C, %, h, min | Throughout | Everyday units. |
| y | Habit Age scale ("−1½ y younger") | Sits beside "younger" and "older", and Habit Age is in years. |
| GPS | Workout Ready and Live screens, Distance sheet | Everyday word. The Ready screen's (i) explains what GPS changes. |
| dBm | Enter the key yourself, beside each strap found | Now explained in the dialog's own text: "its signal, and nearer zero is closer". |
| HIIT, HYROX | Sport names in the sport picker | Names of the sports as people know them. |
| UK Biobank, FRIEND, Jurca, Mifflin-St Jeor | Info sheets only | Study and method names, cited where the method is described. |
| BMI, WHO | Were in the VO2 max and Active minutes sheets | Now spelled out: "body mass index", "World Health Organization". |
| HR | Was in Recovery's What drove it ("Lowest HR ... before waking") | Now "Lowest heart rate". |
| VO2max | Was Habit Age's habit name "Fitness (VO2max)" | Now "Fitness (VO2 max)", per spec 7.2. |
| Auth key, ID, KB | Pairing dialogs, Zepp account, Firmware | Left as is: the strap's "auth key" is the term every guide uses, and these pages are for people setting the strap up. |

---

## 3. Morning answer

The question is "how am I?", answered in under 3 seconds on waking.

**What decides it.** `MomentResolver.resolve` (`data/MomentResolver.kt`) returns `Moment.Waking` from the end of
today's main sleep (05:00 if none) for `WAKING_HOURS` = 3 h, unless a workout is recording, ended under 2 h ago, or
it's your usual workout window. The header says "Waking up". The app opens on Today.

**What's on top.** `todayCards` (`ui/dashboard/TodayPlan.kt`) puts the hero first, as a Large card (two grid units,
332 dp), so it fills the top of a phone with nothing above it but the header. For `Moment.Waking` the hero is
`WakingHero` (`ui/glow/GlowTodayScreen.kt`), a glow card lit in Recovery's colour:

1. **Recovery**, with the change since yesterday ("+4 vs yesterday").
2. **One sentence**, meaning first: "Slept 7 h 40. Recovered enough for your usual training." (`TodayCopy.waking`).
3. **The score, big**, with its word ("78, Ready"), and **Charge at waking** beside it.

Under it come only things that need an answer (workout offer, a workout to save, From your strap), then the
check-in strip and Your body this morning (resting heart rate, HRV, blood oxygen, breathing rate).

**Why it answers without scrolling or tapping.** The answer is the first and largest thing on the first screen, it's a
sentence before it's a number, and the sentence already says what the number means for the day. The rest of Today
backs it up, it doesn't have to be read to get the answer.

**When there's no score**, the hero still answers in the same place:

- No sleep recorded last night: Charge leads with "No sleep recorded last night. Was the strap on?".
- Before the first night: "Recovery starts after your first night with the strap."
- Still learning: the strap's own score, labelled "Strap's estimate", with "OpenStrap is still learning your nights,
  5 of 7."
- The score failed: "Couldn't work out today's score.", yesterday's score dimmed, and Try again.
- Still loading: a morning with no moment yet lays out as waking, so nothing jumps when it arrives.

**Measured on hardware** (Android 16 phone, cold start, waking moment, 24 Oct 2026): the first frame draws in about
0.6 s (`am start -W` TotalTime 583 to 606 ms over four runs). From a screen recording at 10 frames a second, the
hero's sentence and score are on screen about 1.1 s after the launch. That's under the 3 seconds.

The recording also caught two things that this step fixed. Before the day's rows loaded, the hero read the empty
state as a night without sleep and showed "No sleep recorded last night. Was the strap on?" for about 0.8 s before
Recovery replaced it. The header's strap chip said "Not paired" for its first frames too. Now the day state carries
`loaded`, so the no-sleep hero and sentences wait for it (Today, Recovery and Sleep tabs), and the chip takes the
same "paired until the store says otherwise" value as the pairing hero.

---

## 4. Known gaps

Still open after step 10:

- Fastest 1 km or 5 km personal best is not built.
- Phone workouts don't store their heart rate drop. Needs a database migration.
- Swiping on the workout map pans the map instead of scrolling the page.
- ScoreDetail shows the waiting line when Recovery has failed.
- The sync record lives in memory only, so it's lost when the app closes.
- The app ignores the phone's 12 h setting.
- The no-sleep hero can show before the first sync.
- The Recovery tab's sentence can name a driver that the card under it doesn't list. With the demo data it says
  "Mostly held back by sleep over recent nights" while What drove it shows HRV, resting heart rate and last night's
  sleep, all Usual. The card lists the three heaviest drivers, the sentence the weakest.
- Charge's glow should follow its level. Deferred.
- Not verified on hardware:
  - the live workout screen during a real workout
  - the notification's Pause, Resume and Lap
  - a strap workout's detail page
  - the "First score" tag
  - animations-off mode
  - Back after the app was closed in the background
