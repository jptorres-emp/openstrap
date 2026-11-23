# Text sweep: Today tab, home screen widgets, notifications

Sources read: `ui/glow/GlowTodayScreen.kt`, `TodayCards.kt`, `ui/dashboard/TodayPlan.kt`, `TodayLooks.kt`, `presentation/TodayCopy.kt`, `TabCopy.kt`, `ui/dashboard/SyncStates.kt`, `DataStates.kt`, `LatestReading.kt`, `Tags.kt`, `GlowLayout.kt` (CardTitle, TitleRow, NoteText), `GlowList.kt` (ListRow, ListPanel), `GlowHealthspan.kt`, `GlowWorkoutMode.kt` (suggestion), `StrapPill.kt`, `widget/*`, `device/*Notif*`, and the string files `strings_core_today.xml`, `strings_glow_today.xml`, `strings_day_today.xml`, `strings_system_widgets.xml`, `strings_system_notifications.xml`, `strings_core_notification.xml` (468 visible strings, TalkBack-only ones left out of the counts).

Nothing was run on a device. Widths are estimates from the rule you gave (13 px: half card about 18 to 20 characters a line, full card about 40, hero card about 28 at 21 sp).

Flag codes used in the tables:
- FIT = cannot fit or is at risk of being cut where it lands
- SENT = a sentence sits where a word or short phrase belongs
- DUP = the same idea is worded differently somewhere else (group id Gn, listed in the patterns section)
- OK = checked, fits

Text styles in play (from GlowType): `cardTitle` 15 sp semibold, `secondary` 13 sp (status lines, notes, hero side lines), `sentence` 21 sp Display Light (hero sentence), `caption` 11 sp (pills, markers, tags), `noteSmall` 12 sp (strap pill, row tag), `body` 15 sp (row names), `rowValue` 15 sp, `button` 16 sp, `heroTitle` 16 sp. Numbers are dot matrix (`HaloNumber`, not text).

## Slot types found

1. Hero title row: title + note on the right (note drops under the title when they cannot share a line), plus a sync marker or neutral tag
2. Hero sentence (21 sp, no maxLines, wraps freely)
3. Hero number with 1 to 2 side lines (13 sp, maxLines 1, clipped, beside dot digits)
4. Hero button labels (16 sp, sport name inside)
5. Card title (15 sp, 2 lines, chevron circle takes 42 dp) with marker or tag beside it
6. Title row note on a Wide (13 sp, 1 line, drops under the title)
7. Number unit (dots plus a unit word)
8. News tag pill (11 sp, 1 line)
9. Small status line (13 sp, 1 line when there is a number, 2 lines when there is none, ellipsis)
10. Wide status line (13 sp, 2 lines, ellipsis)
11. Strip title + detail (one line)
12. Strip body lines (no limit)
13. List row: name (1 line, ellipsis), status line (no limit), value column, tag
14. List panel title + note
15. Sheet title, note, row labels, chips, buttons
16. Strap pill (12 sp, 1 line, no wrap)
17. Widget one-line options (the layout picks the longest that fits)
18. Widget sentence (2 lines, dropped when it does not fit)
19. Notification title (1 line), text (2 lines collapsed), sub text, action button (a word or two)
20. TalkBack-only text (ignored here)

## Today: header

| Where / part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| Header title | `headerDate` 20 sp, 1 line | Moment name: "Waking up", "Midday", "Evening", "Night", "Workout time", "After your run", "After your workout", "Running", "Workout"; past day: "Yesterday", "Tuesday", "Tue 21 Oct" | OK |
| Strap pill | `noteSmall` 12 sp, 1 line, no wrap, about 100 dp | "Not paired", "Not connected", "Not synced", "18 min ago", "Just now", "3 h ago", "64%" (low or normal battery), "64" (charging, the number only), empty while connecting | OK. Charging shows a bare "64", the "Charging 64%" string is unused for the visible pill (only TalkBack) |

## Today: heroes

| Hero / part | Style, lines, width | Strings that can land (example filled) | Flags |
|---|---|---|---|
| Waking, title | `cardTitle`, with tag "First score" | "Recovery" | OK |
| Waking, title note | `secondary` 1 line, drops under the title | "+3 vs yesterday", "-3 vs yesterday", "Same as yesterday" | OK |
| Waking, sentence | `sentence`, unlimited, full card (about 28 chars a line, so 3 to 4 lines) | "Slept 7 h 40." plus one of: "Some overnight signals are off your normal. An easy day may help." / "Well recovered. A good day for a hard session." / "Recovered enough for your usual training." / "Not fully recovered. Keep the effort moderate." / "Still catching up. An easy day helps." / learning "unstrap is still learning your nights, 5 of 7." / "Not enough heart data last night for a Recovery score." First night: "Recovery starts after your first night with the strap." No sleep: "No sleep recorded last night. Was the strap on?" / "No sleep recorded that night." Deleted: "You deleted last night's sleep." | SENT (long, up to 3 sentences, 85 chars). Learning string names the app and starts a sentence with lowercase "unstrap" right after "Slept 7 h 40." G1 G15 |
| Waking, number side lines | `secondary`, maxLines 1 clip, about 200 dp beside the dots | Line 1: "Take it easy", "Moderate", "Ready", "Ready for more", "Strap's estimate", or waiting "After your first night" / "Waiting for last night" / "No reading yet". Line 2: "Energy at waking 82" | OK (19 chars fits beside 2 digits). G2 |
| Energy (midday, no-sleep), title note | `secondary` 1 line | "82 at waking", "82 at start" (strap off overnight) | DUP G2 (three wordings of the same figure) |
| Energy, marker or tag by title | `caption` | "Up to 09:40", "Partial day" | tight next to "Energy" and the note, FlowRow wraps under it |
| Energy, sentence | `sentence`, unlimited | morning "The morning drained 22. Plenty left for this evening." / day "The day has drained 40 so far. Enough left for an easy evening." / "Nothing drained since waking. Running low. An early night helps." / first night "Energy starts after your first night. Sleep is what fills it." / no sleep "No sleep recorded last night. Was the strap on?" / failed "Couldn't calculate today's Energy." / deleted "You deleted last night's sleep." | DUP G8 (the same drained figure appears again in the side line "Drained 38"), G3 |
| Energy, strap-off line | `secondary` 0.7 alpha, 2 lines, tappable with no visual cue | "Strap off overnight, so today started from an estimate." (55) | SENT (a sentence caveat where the title note "82 at start" already says it), looks like a caption though it opens a sheet |
| Energy, number side lines | `secondary` maxLines 1, right-aligned beside the ruler | "Recharged 64", "Drained 38", "Now" | OK |
| Workout window, title | `cardTitle` 2 lines | "Your usual run", "Your usual workout" or, with no usual time: "Ready for effort", "Fine for a steady session", "An easy session suits today", "Your usual workout time" | DUP G7 |
| Workout window, title note | `secondary` | "Usually around 17:30" (20 chars) | drops under a long title, OK |
| Workout window, sentence | `sentence` | "Recovered and ready for effort. Energy is 58, plenty left." / "Fine for a steady session. Energy is 30, enough for something easy." / "An easy session suits today. Energy is 20, running low." | DUP G7 (identical words to the title when there is no usual time), G3 |
| Workout window, lines beside Recovery figure | `secondary` maxLines 1 clip, about 180 dp | "Resting 60 bpm", "Last run yesterday", "Last run 2 days ago", "Last workout 12 days ago" | OK. DUP G10 (resting with bpm here, without bpm on heart cards) |
| Workout window, buttons | `button` 16 sp, 1 line, start button about 140 dp for text after the icon (about 16 chars) | "Start run", "Start outdoor run" (17), "Start strength training" (23), "Start outdoor cycling" (21), "Other" | FIT for sport names over about 16 chars. DUP G18 (lowercase sport here, capitalised "Outdoor run" in the Start strip) |
| After workout, title and note | `cardTitle`, note `secondary` | sport name "Outdoor run"; note "25 min" | note has no "ago" (it is time since the end). G17 |
| After workout, sentence | `sentence` | "Heart rate after your workout shows once the strap sends it." / "Heart rate 74 now." / "Heart rate back near resting." / "Heart rate settling." | OK |
| After workout, number side lines | `secondary` maxLines 1 clip, beside a 3 to 5 char dot number (about 130 to 150 dp left) | "42 min", "Average 141 bpm, mostly zone 3" (30 chars), "Average 141 bpm", "Mostly zone 3" | FIT: the 30 char line clips beside "42:10" |
| Evening and night, title and note | `cardTitle` "Today", note `secondary` | "Bedtime in 1 h 10", marker "Up to 09:40", tag "Partial day" | OK |
| Evening and night, sentence | `sentence` | "A full day. A run, past your step goal and a Load above your usual." / "A quiet day. Well short of your step goal." / "A steady day. A workout and close to your step goal." (stepCount false in the app) | DUP G16 (grammar: a noun phrase, an adjective phrase and another noun phrase in one list; the widget says "13,860 steps" instead) |
| Evening and night, number and line | dots "13 860" plus word; `secondary` maxLines 1 | "steps"; "Energy 34 left" (only when the Energy card is hidden) | DUP G3, G19 |
| During workout | `heroTitle`, `secondary`, glance strings | built by `LiveGlance` (workout area, not read here) | not swept |
| Pairing, title | `cardTitle` | "Pair your Helio Strap" | DUP G13 |
| Pairing, sentence | `sentence` | "Put the strap on its charger, close to this phone." / "Looking for your strap nearby." / "Found a strap nearby. Pick it to pair." / "Connecting to your strap. Keep it close." / "Connected. The first sync starts now." / "Couldn't connect yet. Keep the strap on its charger, near the phone." / "The strap refused to pair. Open the Strap page to pair again." | long but hero, OK |
| Pairing, button | `button` | "Pair strap" | DUP G13 |
| Stuck sync, title | `cardTitle`, note "Last tried 07:42" | "Last night", "Not synced" | OK |
| Stuck sync, sentence and line | `sentence`, `secondary` | "Last night is still on the strap." / "Your data since 09:40 is still on the strap." / "Your data is still on the strap."; line "Keep it near your phone and it will sync." | OK |
| Stuck sync, button | `button` | "Sync now" | OK |
| Recovery failed hero | `sentence`, label, button | "Couldn't calculate today's score."; "Yesterday" under the dimmed number; "Try again" | OK. DUP G14 |

## Today: strips

| Strip / part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| Check-in, title | `cardTitle`, 1 line, clip | "How do you feel?" | OK |
| Check-in, answer | `secondary` right | "Poor", "Low", "OK", "Good", "Great" | OK (overlaps the sleep words Poor, Good, Great) |
| Last night strip, title and detail | `cardTitle` plus `secondary` 1 line (strip is 68 dp high) | title "Last night"; detail "Score 83, 7 h 40", "7 h 40", "After your first night", "Waiting for last night", "No sleep recorded" | DUP G6, G15. "Last night" next to "Waiting for last night" says it twice |
| Start a workout, title | `cardTitle` 1 line | "Start a workout" | OK |
| Start a workout, line | `secondary` 1 line clip, about 250 dp (40 chars) | "Your usual, Outdoor run", "Your usual, Strength training" | OK. DUP G18 (capitalised sport mid-line) |
| Alerts, title and detail | `cardTitle`, `secondary` | "From your strap"; "14:05" or "2 more today" | OK |
| Alerts, line 2 | `secondary` 0.85, no limit | "High heart rate, 142 bpm at 14:05." / "High heart rate, 142 bpm." / "High stress, 82." / "Low blood oxygen, 88%." / "Irregular heart rhythm." / "A reading the strap flagged." | SENT (full stop on a status fact). DUP G12 (the notification says "Your heart rate reached 165 bpm.") |
| Alerts, line 3 | `secondary` 0.5, no limit | "This is a wellness estimate, not a medical assessment." (54) | FIT: 2 lines inside a strip, so the strip is no longer a strip |
| Workout to save (suggestion), title | `cardTitle` 1 line | "Looks like you worked out" | DUP G5 (Edit Today calls this card "Workout to save") |
| Workout to save, text | `PageNote`, no limit | "Today, 18:02 to 18:44, mostly zone 3, 141 bpm on average. It wasn't recorded." (77) | OK (2 lines) |
| Workout to save, buttons | `WorkoutButton`, half width each | "Save as workout", "Not a workout" | OK |

## Today: small cards (pairs)

Which Smalls exist: Recovery + Last night (midday, during), Heart rate + Steps (waking), Load + To recover (window, after), Energy + Heart rate (night), Steps + Last night (after). A lone Small becomes a Wide. Half card inner width is about 140 dp: titles about 12 chars a line (chevron takes 42 dp), 13 sp lines about 18 to 20 chars.

| Card / part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| All Smalls, title | `cardTitle` 2 lines + chevron; when title plus chevron (and marker or tag) exceed the width the chevron drops to a line of its own (`TitleRow`) | "Recovery", "Last night", "Heart rate", "Steps", "Energy", "Load", "Time to recover" | FIT: "Time to recover" (15 chars) probably pushes the chevron onto a second line. Not checked on a device. DUP G5 (Edit Today says "To recover" and "Load today") |
| All Smalls, marker or tag beside the title | `caption` | "Up to 09:40", "Partial day" | FIT on Energy, Steps and Heart rate Smalls: "Energy" + 62 dp marker + chevron is over 140 dp |
| Recovery Small, status | `secondary`, 1 line with a number (ellipsis), 2 lines without | with a score: "Take it easy", "Moderate", "Ready", "Ready for more", "Strap's estimate". Without: "unstrap is still learning your nights, 5 of 7." (47), "Not enough heart data last night for a Recovery score." (54), "After your first night", "Waiting for last night", "No reading yet". Failed: "Couldn't calculate it" (21, 2 lines) | FIT and SENT: the two long ones need 3 lines and cut at "unstrap is still learning your nig..." (the case you saw). G1, G15 |
| Last night Small, number and unit | dots + `secondary` | "83" + "score", or "7:40" + "h"; tag "Short" or "Long" drops under the number and replaces the status | OK. DUP G6 |
| Last night Small, status | `secondary` 1 line (2 with no number) | with a score: "7 h 20"; none: "After your first night", "Waiting for last night", "No sleep recorded" | OK |
| Heart rate Small, status | `secondary` | "Live now", "At 20:52", "Waiting for the strap" (21, 2 lines, no number); hidden when the "Up to" marker shows | OK |
| Steps Small, number, tag, status | dots; pill; `secondary` 1 line | "13 860"; tags "Goal met", "+2,140 past goal", "Personal best", "Best this month", "Double goal", "3 days in a row" (pill about 110 dp, drops under the number); status "62% of your goal" or "Goal met" (hidden while a tag shows) | OK |
| Energy Small, status | `secondary` 2 lines (EdgeValue) | "Sleep refills it" (16), "Steady since waking" (19), "Drained 38", "After your first night", "Waiting for last night", "No reading yet", failed "Couldn't calculate today's Energy." (34) | FIT: failed string is 2 full lines at best. SENT. DUP G8 |
| Load Small (level look), tag + status | pill; `secondary` 1 line ellipsis | tag "Above usual", "Below usual"; status "Above your usual", "Usual", "Below your usual", "So far today", "Learning your usual" (19) | FIT borderline for "Learning your usual". DUP G4: tag and status both say it when off ("Above usual" pill over "Above your usual") |
| Load Small, no value | `secondary` 2 lines | "Couldn't calculate it", "After your first night" | OK |
| To recover Small, number, status | dots + "h"; `secondary` | "14" + "h" with "Ready by 20:30", "Ready tomorrow", "Ready Friday", "Ready Wednesday" (15); "Recovered" (no number); "No estimate from the strap" (26, 2 lines) | FIT borderline: the 26 char line is 2 lines. G15 |

## Today: wide cards

Wide inner width is about 300 to 340 dp: 13 sp lines hold about 40 to 50 chars, status allows 2 lines then ellipsis. The title row note shares the line (title + note + chevron) or drops under the title.

| Card / part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| Last night Wide, title note | `secondary` 1 line | "22:58 to 06:56" | OK |
| Last night Wide, status | `secondary` 2 lines | "Good, 7 h 20 asleep", "Great, 8 h 5 asleep"; none-night "After your first night" / "Waiting for last night" / "No sleep recorded"; none when no score | DUP G6 |
| Heart rate Wide, title note | `secondary` | "Resting 58", "Resting 58 est." | DUP G10 |
| Heart rate Wide, status | `secondary` | "Live now", "At 20:52", "Waiting for the strap" | OK |
| Stress Wide (pin only), status and tag | `secondary`; pill | "Relaxed", "Normal", "Medium", "Normal, 13:10" (stale), "Latest at 13:10" (with High tag stale), tag "High", waiting line | OK |
| Steps Wide, title note, unit, status | `secondary`; "steps"; 2 lines | note "Goal 8,000"; status "62% of your goal, more than usual by now" (40), "Goal met, about your usual day" (30); pill "Goal met" | DUP G11 (tag "Goal met" and status "Goal met, about your usual day" on the same card). DUP G19 (note "8,000" with comma, dots "8 225" with thin space) |
| Recovery Wide, unit, tag, status | "Ready" (word beside dots); pill "Above usual" / "Below usual"; status 2 lines | meaning sentences (see Waking hero), estimate "unstrap's own score isn't ready yet." (36), failed "Couldn't calculate today's score." + "Try again" | SENT: five full sentences in a status slot (the other Wide statuses are phrases). FIT: "Some overnight signals are off your normal. An easy day may help." is 65 chars, 2 lines, ellipsis risk at 360 dp |
| Recovery Wide, title note | `secondary` | "This morning" | OK |
| Load Wide, status | `secondary` | "Above your usual", "Usual", "Below your usual", "So far today", "Learning your usual" | DUP G4 |
| To recover Wide | as Small | as Small | as Small |
| Energy Wide (plain or second glow), title note and status | `secondary` | note "82 at waking" (only when anchored); status as the Energy Small | DUP G2, G8 |
| Your week, title note | `secondary` 1 line | "4 of 7 days at goal", "1 of 1 day at goal" | OK |
| Your week, day letters | `caption` | "M T W T F S S" | OK |
| Habit Age, title, update line | `cardTitle`, `secondary` 1 line | "Habit Age"; "Updated Sun 18 Oct" | OK |
| Habit Age, number, tag | dots + "years"; pill | "Older", "Younger" | OK |
| Habit Age, status | `secondary` 2 lines ellipsis, full card (about 80 chars max) | with an estimate: "Likely 27 to 31", "1 year younger than last week", "2 years older than last week", "Same as last week". Before the first estimate: "Your age for now. Needs 3 more nights of wear." (46), "First estimate Sunday 22 Nov"; profile: "Add your birth year to your profile to see your Habit Age." (58), "Add your birth year and sex to your profile to see your Habit Age." (66); withheld reasons: "Habit Age is for ages 20 to 79.", "Add your sex in your profile: the typical values differ for women and men." (74), "Not shown while taking beta-blockers or during pregnancy: resting heart rate doesn't reflect fitness then." (107), "Needs 30 days of wear before a first estimate." (46), "Needs resting heart rate on about half of recent nights." (56), "Needs about half of recent days worn 10 hours or more." (54), "Needs more weeks of sleep regularity.", "Needs sleep on about half of recent nights." | FIT: the 107 char reason is cut after 2 lines, the 74 char one is borderline, and "Your age for now. " in front of a reason adds 18 more. SENT for all of the reasons. DUP G9 ("to your profile" in one string, "in your profile" in another) |
| Habit Age, age scale labels | `caption` 1 line | "You, 34", "27 to 31" | OK |
| The workout card (`TodayCard.Workout`) | not in any moment today | "Average 141 bpm, mostly zone 3" etc. | unreachable, not swept |

## Today: body list ("Your body this morning")

| Part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| Panel title | `cardTitle` 1 line, ellipsis | "Your body this morning", "Your body today" (bare Today) | DUP G5 (two string names, same text) |
| Panel note | `secondary` 1 line, drops under the title if it cannot share | "Against your normal" | FIT at about 360 dp: title (22 chars) + note (19) + marker "Up to 09:40" do not share a line, the note drops down |
| Row name | `body` 1 line, ellipsis, about 200 dp | "Resting heart rate", "HRV", "Blood oxygen", "Breathing rate" | OK |
| Row status | `secondary`, wraps, no limit | "Usual", "Above your normal", "Below your normal", "Learning your normal", "Learning, 4 of 7 nights", "Daytime", "While asleep", "Daytime, 12:50", "While asleep, 05:10", "Estimated", "Usual, estimated" (28 for "Above your normal, estimated"), waiting "After your first night" / "Waiting for last night" / "No reading yet" | DUP G1 (resting heart rate says "Learning your normal" while HRV and breathing say "Learning, 4 of 7 nights"). Example text of `glow_today_body_estimated` says "Within normal" but the word that fills it is "Usual" |
| Row tag (replaces the status when off) | `noteSmall` 1 line, row colour lifted | "Above normal", "Below normal" | DUP G4 |
| Row value | `rowValue` 15 sp, 1 line, own column | "58", "42", "97%", dash | no unit on the number (G20): resting heart rate is "58" here but "58 bpm" elsewhere |

## Today: Edit Today and sheets

| Part | Style, lines, width | Strings that can land | Flags |
|---|---|---|---|
| "Edit Today" button | `PageButton` compact | "Edit Today" | OK |
| Sheet title and note | `sheetTitle` 1 line; `secondary` wraps | "Edit Today"; "Pin up to two cards under the top card, or hide the ones you don't need." (72) | OK |
| Row labels | PageRow name | "How do you feel?", "Your body this morning", "Last night", "Heart rate", "Stress", "Steps", "Recovery", "Energy", "Load today", "To recover", "The workout", "Your week", "Habit Age", "Start a workout" | DUP G5 (names differ from the cards: "To recover" vs "Time to recover", "Load today" vs "Load"). "The workout" is a card no moment shows |
| Chips and buttons | `PageChip`, `PageButton` | "Pin", "Pinned", "Cancel", "Done" | OK |
| Partial day sheet | InfoSheet | "Partial day" name; "The strap was worn for part of the day, so its totals are low."; "The strap recorded 5 h 10 of the 9 h 30 so far today."; "What it lowers": "Steps, calories and Energy count only the time the strap was on. Charging pauses them too."; "When a day counts as partial": "When the strap was worn for under 20 h of the day. Today, under that share of the hours so far." | sheet body, long text is fine |

## Home screen widgets

Rule in the string file: no text wraps, every one-line text comes as a ladder of options and the layout takes the longest that fits; the hero sentence gets 2 lines (Sleep 3) and is left out when no option fits. So nothing is cut mid-word, but content can drop silently.

| Widget / part | Style, lines | Strings that can land | Flags |
|---|---|---|---|
| All, title | 17 sp, 1 line | "Moment", "Recovery", "Steps", "Sleep" / "Last night", "Start a workout" / "Start" (short) | OK |
| All, status words beside the round button | amber, 1 line, ladder | "Pair your strap" / "Pair strap", "Strap at 12%" / "12%", "Syncing...", "Not synced", "Bluetooth is off" / "Bluetooth off", "Synced 09:10", "Synced yesterday", "Synced Tuesday", "Synced Tue", "Synced Tue 21 Oct" / "21 Oct" | DUP G13 (a third wording of "no strap") |
| Moment, name | 1 line | the Today header title | OK |
| Moment, sentence | 2 lines, shrinks 20 sp, drops if no fit; `shorter()` splits at ". " and tries each sentence | the Today sentences (see heroes). Window: "Ready for effort. You usually run around now." | SENT: the whole sentence vanishes when even the shortest part is over 2 lines (for example the 47 char learning line on a Small). G7 |
| Moment, number words | `secondary` | "Energy left" / "left"; "min since your run" / "min since" / "min"; "min to bedtime" / "min" | DUP G3 |
| Moment, figure at right | `secondary` ladder | "Energy at waking 84", "Drained 60, recharged 38" / "Drained 60", "Load today 120" / "Load 120", "Bedtime 22:45", "Recording since 17:42" / "Since 11:58" | DUP G2, G8 |
| Recovery | 13 sp | note "This morning" / "Yesterday"; labels "Resting", "HRV", "Sleep"; values "52 bpm", "64 ms", "No reading" | OK |
| Steps | 13 sp | "Goal 10,000", "8 432 today" style "today", "1,568 to go", "Goal met", "This week", "Goal met 2 of 6 days" | DUP G11, G19 |
| Sleep | 13 sp, sentence 3 lines | note "22:58 to 06:56", "Earlier night"; "Slept through", "Woke 3 times"; sentence "7 h 40 asleep." / "7 h 40, your longest this week." / "No score from the strap" / "Your first night with the strap shows here in the morning." | OK |
| Start | 13 sp | "Your usual", "Your usual, GPS on", "Your usual, GPS off", "All sports", "No workouts yet", "None yet", "Last run", "Tuesday, 5.2 km", "2 workouts, 1 h 35", "Recording", "Paused", "Since 11:58", "Open" | DUP G18 |

## Notifications

| Notification / part | Style, lines | Strings that can land | Flags |
|---|---|---|---|
| Strap (ongoing), title | 1 line | "Helio Strap", "Recovery 72, Energy 58", "Recovery 72", "Energy 58", "Evening, Energy 35" | OK |
| Strap, text | 2 lines collapsed | the Today sentence for the moment (see heroes), "Connecting...", "Checking the key...", "Couldn't reach the strap", "Strap not connected", "Bluetooth is off", "Syncing heart rate (3/12)", "Strap battery 12%. Charge it tonight.", "Strap connected, battery 80%", "Strap charging, battery 80%" | OK. "(3/12)" is the only slash used for a count where the rest of the app says "3 of 12". G13 |
| Strap, sub text | 1 line | "Strap 71%", "Strap charging, 71%", "Strap 71%, synced 10:30", "Synced" | OK |
| Strap, actions | a word or two | "Retry", "Turn on", "Start workout", "Sync now" | OK |
| Workout (ongoing), title and text | 1 line; text joins parts with ". " | "Outdoor run"; "152 bpm. Zone 3, tempo. 2.40 km", "Paused", "Waiting", "Still recording", "End the workout? Tap Yes, end to save it." (41) | DUP G12-style join: full stop between facts here, comma between facts everywhere else |
| Workout actions | | "Pause", "Resume", "Lap", "End", "Yes, end", "Cancel", "Set done", "+30 s", "Start set 3" | OK |
| Workout saved | title 1 line; text 2 lines then expanded | "Outdoor run saved"; "42:10, 5.2 km, 142 bpm avg"; expanded "Ended at 18:50, when you'd stopped. The 12 min after were left out."; actions "View", "Undo" | OK. "142 bpm avg" abbreviates where the app says "Average 141 bpm" (G10) |
| Health alert | title 1 line; text 2 lines | "High heart rate" / "Your heart rate reached 165 bpm."; "Low heart rate" / "Your heart rate dropped to 38 bpm."; "High stress" / "Your stress is at 82."; "Low blood oxygen" / "Your blood oxygen was 88%."; "Irregular heart rhythm" / "The strap noticed an irregular heart rhythm at 14:05." | DUP G12 (Today says "High heart rate, 142 bpm.") |
| Auto-detected workout | title / text | "Outdoor run detected, 10:02 to 10:40" / "The strap noticed it and recorded it for you." | OK |
| Keep recording | title / text | "Keep recording your outdoor run" / "Android may stop it while unstrap is closed. Tap to keep it going." (66) | OK. Names the app |
| Looks like you stopped | title / text / chip | "Looks like you've stopped" / "Your heart rate has been back near resting since 18:50. End the run at 18:50?" (76); "Looks like you're in a car" / "You last moved at 18:50. End the run there, so the drive isn't counted?" (71); chip "End?" | text is 2 lines, OK |
| Strap is off | title / text | "Strap is off" / "Heart rate and sleep tracking are paused until you put it back on." (66) | OK |
| Weekly recap | title / text | "Your week, 20 to 26 Oct" / "2 of 4 workouts." / "4 of 4 workouts, goal met." / "4 of 4 workouts, goal met. 6 weeks in a row." | OK |
| App update | title / text | "unstrap 2.2.0 is out" / "See what's new and install it from the Updates page."; "Updated to unstrap 2.2.0" / "Tap to see what's new." | OK. Names the app |
| Channel names | settings | "Auto-detected workouts", "Health alerts", "Keep recording", "Strap connection", "Workout saved", "Workout", "Wear reminders", "Weekly recap", "App updates" | OK |

## Worst problems (ranked)

1. Recovery Small status shows a sentence: `core_today_no_score_learning` ("unstrap is still learning your nights, 5 of 7.", 47 chars) cut at "unstrap is still learning your nig...". The slot holds 2 lines of about 19 chars. It also names the app and starts lowercase. Needs a word: "Learning" (the count can live on the detail).
2. `core_today_no_score_heart` ("Not enough heart data last night for a Recovery score.", 54) in the same Small slot. Needs "No score" or "Not enough data".
3. Habit Age status takes whole sentences (9 withheld reasons plus 3 profile reminders). The beta-blocker one is 107 chars and is cut after 2 lines, the sex one (74) and "Your age for now. " plus a reason (about 64 to 74) are borderline.
4. After-workout hero side line "Average 141 bpm, mostly zone 3" (30 chars, maxLines 1, clips) beside a dot number of 3 to 5 characters.
5. The workout window hero says the same thing in its title and its sentence when there is no usual time ("Fine for a steady session" then "Fine for a steady session. Energy is 30, ..."), and the title is a sentence where a name belongs.
6. "Still learning" has six wordings: "Learning your normal" (body rows), "Learning, 4 of 7 nights" (HRV and breathing rows, so one list mixes two), "Learning your usual" (Load Small, 19 chars on one line), "Load, learning your usual", "unstrap is still learning your nights, 5 of 7", "Waiting for last night" / "After your first night" for the nearby cases.
7. Energy Small failed state `day_charge_failed` ("Couldn't calculate today's Energy.", 34) in a 2 line status; the Recovery and Load Smalls use the short "Couldn't calculate it", Energy uses the long one.
8. Alerts strip has a third line, the 54 char disclaimer, that turns a 68 dp strip into a card; alert lines end with a full stop where every other status line has none, and the notification words the same alert differently ("Your heart rate reached 165 bpm." vs "High heart rate, 142 bpm.").
9. Chevron drop on Smalls: "Time to recover" (15 chars), and any Small title plus the "Up to 09:40" marker or "Partial day" tag, likely pushes the chevron under the title (from the `TitleRow` rule; not checked on a device).
10. Start sport button: "Start " + sport name (up to "Start strength training", 23 chars) beside "Other" over a 16 sp label; and the sport is capitalised in "Your usual, Outdoor run" but lowercase here.

Next in line: Energy hero says the drained figure twice (sentence and side line); three wordings of "Energy at waking"; Steps Wide shows "Goal met" as a pill and again as the start of its status; Last night is worded three ways (strip "Score 83, 7 h 40", Wide "Good, 7 h 20 asleep", Small "83 score"); tag and status both say Above usual / Above your usual on Load; body list note drops under the title at 360 dp.

## Style patterns (counts over the 468 visible strings in the six Today-area files, plus code joins)

### Joining parts in one line

- Comma: about 21 distinct string slots join two facts with ", " and no capital after it: "Score 83, 7 h 40", "Good, 7 h 20 asleep", "Normal, 13:10", "Daytime, 12:50", "While asleep, 05:10", "High heart rate, 142 bpm", "Your usual, Outdoor run", "Goal met, more than your usual day", "62% of your goal, more than usual by now", "Average 141 bpm, mostly zone 3", "Usual, estimated", "Drained 60, recharged 38", "Evening, Energy 35", "Tuesday, 5.2 km", "2 workouts, 1 h 35", "Your usual, GPS on", "Outdoor run detected, 10:02 to 10:40", "42:10, 5.2 km, 142 bpm avg", "Hour left, ready by 20:30", "Recovery 78, Ready".
- Full stop: used where the second part is a separate thought or the first part is a full sentence: "Your age for now. Needs 3 more nights of wear.", "Strap battery 12%. Charge it tonight.", "Waiting for the strap. Still recording.", "4 of 4 workouts, goal met. 6 weeks in a row.", "Ready for effort. You usually run around now." (widget), "Slept 7 h 40. Well recovered." (hero). One outlier: the workout notification joins facts with ". " in code ("152 bpm. Zone 3, tempo. 2.40 km"), where every card joins facts with a comma.
- Both at once inside one string, 2 cases: the recap "4 of 4 workouts, goal met. 6 weeks in a row." and the strap off line "Strap off overnight, so today started from an estimate."-style comma clause followed by a stop; the alert line "High heart rate, 142 bpm at 14:05." mixes a comma, "at" and a stop.
- No dash, semicolon or middle dot anywhere in the area (0 found), matching the memory rule. Colons: 1 string in the area (`day_stress_counts_from_calm`, info sheet) and 2 in the Habit Age reasons ("Add your sex in your profile: ..." and the beta-blocker one).

### Full stop at the end

- 84 of 468 visible strings end with a full stop (18%): core_today 43 of 126, day_today 21 of 90, notifications 10 of 83, glow_today 8 of 86, widgets 1 of 76, core_notification 1 of 7.
- Hero sentences, pairing, partial-day sheet, notification texts: always with a stop. Labels, words, numbers with units, tags, chips, button labels: never.
- Where the two meet in one slot, the full stop shows: the Recovery card status shows "Ready" for a score and "Not enough heart data last night for a Recovery score." for no score; the Wide Recovery status is a sentence while the Load and Steps statuses beside it are phrases; the Alerts strip line has a stop, the Heart and Stress statuses do not.
- Question marks: 6 strings ("How do you feel?" x2, "Was the strap on?", three notification questions).

### Naming the app and the strap

- "unstrap": 3 visible strings in the area. Two are Today status text with the app as the subject ("unstrap is still learning your nights...", "unstrap's own score isn't ready yet."), one is a notification text ("Android may stop it while unstrap is closed.") plus the keep-recording channel description and two update notification titles outside the six files. Always lowercase, including at the start of a sentence.
- The strap: 44 visible strings say "the strap" or "your strap" or "Strap ..." (pairing 7, partial day 5, chips and TalkBack 12, notifications 14, Today status and hero text 6). "Helio Strap" 2 times ("Pair your Helio Strap", notification title). The app is never "we" or "the app" otherwise.
- Wordings for "no strap": "Pair your Helio Strap" (hero title), "Pair strap" (hero and widget), "Pair your strap" (widget), "Not paired" (pill), "Waiting for the strap" (Heart Small status, also the notification "Waiting for the strap").
- Same estimate named three ways: "Strap's estimate" (Recovery word), "No score from the strap" (Sleep widget), "unstrap's own score isn't ready yet."

### Number formats

- Duration: "7 h 40", "40 min", "7 h 05" (space between number and unit, minutes padded to 2 in h m form); dotted clock form "7:40" on dot cards; workout clock "42:10".
- Units: "bpm" always with a space ("141 bpm"); "%" never ("62%", "64%"); "km" with a space ("5.2 km"); pace "5:24/km" without. Body list rows show bare numbers ("58") where the same figure is "58 bpm" on the Heart card (G20); one place abbreviates "142 bpm avg" where Today says "Average 141 bpm".
- Thousands: text uses the language's grouping with a comma ("13,860 steps", "Goal 8,000", "+2,140 past goal", "1,568 to go"); dot-matrix digits use a thin space ("13 860", `DOT_GROUP_SPACE`). Both show on one Steps card (G19).
- Times: the phone's clock, "20:30" or "8:30 PM"; ranges are always "A to B" (`core_range`, 0 dashes): "22:58 to 06:56", "27 to 31", "20 to 26 Oct".
- "of" vs "/": "of" in 8 strings (4 of 7 days at goal, 4 of 7 nights, 5 of 7, 2 of 4 sets, Goal met 2 of 6 days); "/" in 0 strings but 1 code string ("Syncing heart rate (3/12)").
- Plus and minus: "+3 vs yesterday", "-3 vs yesterday" (a true minus sign), "+2,140 past goal".
- Dates: "Sun 18 Oct", "Tue 21 Oct", "Sunday 22 Nov" (the weekday is short in one string and full in the next).

### Capitalisation

- Sentence case for every label and title (0 Title Case labels). Names kept capital: Recovery, Energy, Load (also mid-sentence: 8 strings such as "a Load above your usual", "a Recovery score", "Energy is 58"), Habit Age, Strap's, Helio Strap, Today in "Edit Today".
- Lowercase mid-sentence: heart rate, blood oxygen, stress, steps, zone, workout.
- Sport names: capitalised from `Workouts.sportName` ("Your usual, Outdoor run", After hero title "Outdoor run") but lowercased in sentences and buttons ("Start outdoor run", "You usually run around now", "Keep recording your outdoor run").
- Single words starting lowercase on purpose (units and nouns): "steps", "h", "score", "yesterday" (next to the dimmed number, while the Recovery tab uses "Yesterday").
- Words that start two ways: "Ready" (Recovery word, "Ready tomorrow") vs "ready by 20:30" (inside "Hour left, ready by 20:30"); tags "Above usual" (tag, no "your") vs "Above your usual" (status).

## Wording groups (DUP ids used above)

- G1 Still learning: `core_today_normal_learning` "Learning your normal", `core_today_load_word_learning` "Learning your usual", `core_today_load_line_learning` "Load, learning your usual", `day_sleep_vital_learning` "Learning, 4 of 7 nights", `core_today_no_score_learning` "unstrap is still learning your nights, 5 of 7."
- G2 Energy at waking: `glow_today_energy_at_waking` "Energy at waking 82", `glow_today_energy_waking` "82 at waking", `glow_today_energy_at_start` "82 at start", `system_widget_moment_energy_at_waking` "Energy at waking 84"
- G3 Energy left: "Energy 34 left", "Energy is 58, plenty left.", "Plenty left for this evening.", "Energy left", "left", "Running low. An early night helps." and "Energy is 20, running low." in the window hero (10 strings)
- G4 Above or below: tags "Above usual" / "Below usual" / "Above normal" / "Below normal", statuses "Above your usual" / "Below your usual" / "Above your normal" / "Below your normal"
- G5 Same card, two names: "To recover" (Edit Today) vs "Time to recover" (card); "Load today" vs "Load"; "Workout to save" vs "Looks like you worked out"; "Your body this morning" and "How do you feel?" and "From your strap" each exist as two string names with identical text (6 strings)
- G6 Sleep score and time asleep: strip "Score 83, 7 h 40", Wide "Good, 7 h 20 asleep", Small "83" + "score" over "7 h 20"
- G7 Window hero: three title strings repeat the three sentence strings
- G8 Drained: "The morning drained 22.", "The day has drained 40 so far.", "Drained 38"
- G9 Habit Age profile: "to your profile" (3 strings) vs "in your profile" (2 strings)
- G10 Resting heart rate: "Resting 58" (no unit), "Resting 58 est.", "Resting 60 bpm"; "Average 141 bpm" vs "142 bpm avg"
- G11 Goal met: pill "Goal met", status "Goal met, about your usual day", note "Goal 8,000", widget "Goal met 2 of 6 days"
- G12 Alerts: strip "High heart rate, 142 bpm." vs notification "Your heart rate reached 165 bpm." (5 pairs)
- G13 No strap: "Pair your Helio Strap", "Pair strap", "Pair your strap", "Not paired", "Waiting for the strap"
- G14 Yesterday: "yesterday" lowercase unit vs "Yesterday" label
- G15 Waiting for sleep: "After your first night", "Waiting for last night", "No sleep recorded", "No sleep recorded last night. Was the strap on?", "No sleep recorded that night.", "Recovery starts after your first night with the strap.", "Energy starts after your first night. Sleep is what fills it.", "Your first night with the strap shows here in the morning." (8 strings)
- G16 Steps in a day sentence: app "past your step goal" vs widget "13,860 steps" (6 strings with the goal wordings)
- G17 After hero note "25 min" with no "ago" (code)
- G18 Sport capitalisation: see above
- G19 Thousands: "13,860" text vs "13 860" dots (code)
- G20 Body rows: number without unit (code)

## Counts

- Strings checked: 468 visible (6 files), plus code-built text for the Today cards, widgets and notifications.
- Flagged FIT or at risk: 17 strings (the 2 Recovery Small statuses, Energy failed, Load learning, To recover no estimate, After hero average line, Start sport button, Habit Age medication, sex and "Your age for now" reasons, the disclaimer, the body list note, 2 markers, the "Time to recover" title, the Wide Recovery alert meaning).
- Flagged SENT (sentence where a word or phrase belongs): 26 strings (5 meaning sentences, 2 no-score, estimate not ready, strap-off line, 2 alert lines, disclaimer, Energy failed, 9 Habit Age reasons, 3 profile reminders, "Your age for now" with reason).
- Flagged DUP (in one of 20 wording groups): about 95 strings, overlapping the above.
- Distinct strings flagged at least once: about 120 of 468 (26%).
