# Text sweep: Recovery, Sleep, Activity tabs, details, All metrics, info sheet, workouts

Scope: `openstrap-halo/app/src/main/`, English strings only. Widths are estimates for a ~360 dp phone at the sizes in
`GlowType.kt` (13 sp about 6.6 dp a character, 12 sp about 6.1, 15 sp about 8). "FLAG" marks a string that is cut, can
be cut, is a sentence where a word belongs, or is worded two ways. Not covered: Today tab (only cross-references),
widgets, notifications, other languages, `workout_glance_*` (live notification).

## 1. Slot catalog (the slot types that exist)

Most text goes through a few shared components. Rules on lines and overflow are set once there, so they are the place to
fix.

| Slot | Component (file) | Style | Lines / overflow | Width | Rough capacity |
|---|---|---|---|---|---|
| Card title | `CardTitle` (GlowLayout) | cardTitle 15 | 2, ellipsis | half or full card, less 40 dp with chevron | half 14 per line, 2 lines |
| Title-row note (right of title) | `NoteText` (GlowLayout) | secondary 13, 62% white | 1, **clip** (no ellipsis); drops under title when it can't share | rest of the row | about 18 |
| Title tag | `NewsPill` / `TitleMark` | caption 11 | 1 | pill | about 14 |
| Small card status (under number) | `SmallCard`, `StatusLine`, `ChargeSmall` | secondary 13 | **2**, ellipsis | half card | 18 to 20 per line, 36 to 40 total |
| Small card status, Sleep tab | `SleepStatus` (GlowSleepScreen) | secondary 13 | **1**, ellipsis | half or wide card | half 18 to 20, wide 38 |
| Small card word instead of number | `SmallCard` (no value) | body 15 | 2, ellipsis | half card | 16 per line |
| Small card unit beside dots | `TaggedNumber`, `SleepNumber` | secondary 13 | none (shares a FlowRow with the tag pill) | after the digits | about 8 |
| Wide card status (Load, Heart rate, Habit Age) | `StatusLine`, Habit Age Text | secondary 13 | 2, ellipsis | full card | 40 per line, 80 total |
| Hero sentence | `HeroSentence` (TodayCards) | sentence 21 Display light | none (docs say 3 at most) | full card | about 28 per line |
| Hero figure label | `HeroFigure` | secondary 13 | 1 (Energy passes 2) | under the dots | 18 to 20 |
| Hero note line (Night hero, Ready hero) | `HeroNumber` lines, `HeroLine` | secondary 13 | 1 (`HeroNumber`), none (`HeroLine`) | full card | 40 |
| Caveat line | `CaveatLine` (TabParts) | secondary 13, 80% white | none | full card less 14 | 38 |
| List row name | `RowName` | body 15 | 1, ellipsis | row less icon (48), value, caret (30) | about 22 |
| List row status line (Today-style `ListRow`) | `RowLine` (GlowList) | secondary 13 | **none, wraps** | same column as name | 22 to 28 per line |
| List row off tag | `RowTag` | noteSmall 12 | 1 | same column | about 24 |
| List row value | `ListRow` | rowValue 15 tabular | 1 | value column, up to ~90 dp | about 10 |
| Detail row status (`DetailRow`) | DetailPicture.kt | noteSmall 12 | 2, ellipsis | row less tile (44), value, chevron | 28 per line |
| Panel title / panel note | `ListPanel`, `DetailPanel`, `ListNote` | cardTitle 15 / secondary 13 or noteSmall 12 | 1 | title and note share one row | note about 12 |
| Detail verdict (word beside gauge) | `DetailTemplate` | verdict 24 | 2 | beside gauge, about 150 dp | 12 per line |
| Detail verdict with no value ("No reading") | `DetailTemplate` | verdict 24 | none | full | 24 |
| Detail status word under number | `DetailTemplate` | cardTitle 15 | 1, clip | full less heroSide | 35 |
| Detail context line under verdict | `DetailTemplate` (from `DetailHeadline.parts`) | secondary 13, 60% white | none | full | 40 |
| Detail unit beside dots | `DetailTemplate` | body 15 | none | after digits | 8 |
| Detail sentence | `DetailSentence` | detailSentence 19 Display light | none | full | 30 per line |
| Detail figure value / label (row of three) | `DetailFigures` | figure 28 / noteSmall 12 | **1 / 1**, clip | one third, 80 to 96 dp | value 7, label **about 13** |
| Detail empty line | `DetailEmpty` | secondary 13 | none | full | any |
| Detail driver name / reading / note | `DetailDrivers` | body 15 / body / secondary | **1 each, clip, name is weight(1) but note is not** | one row | see flag A6 |
| Detail heading and its note | `DetailHeading` | cardTitle / noteSmall | 1 | row | note 12 |
| Week strip day | `WeekStrip` | caption | 1 | one of 7 | 3 |
| Info sheet name / blocks | `InfoSheet` | sheetTitle 22 (2 lines) / body 15 on 22.5 | none | full | paragraphs are fine |
| Info sheet scale words | `InfoSheet` `fitWords` | secondary, shrinks to caption | 1 | cell of 3 or 4 | auto-fit |
| Live figure label (row of 2 to 3) | `LiveStat` | noteSmall 12 | 1 | one third | about 14 |
| Live figure value | `LiveValue` | figure 28, unit secondary | 1, softWrap off | one third | 8 |
| Live top bar title / status | `LiveTopBar` | cardTitle ellipsis / noteSmall | 1 | shared | title about 16 |
| Ready hero title / note / number unit / line | `HeroTitle`, `HeroNumber`, `HeroLine` | heroTitle / secondary / body / secondary | 1 ellipsis / 1 / 1 / none | full | line wraps |
| Ready goal note (`GoalNote`) | ReadyCardio | secondary 13, centred, 60% | none | full less 40 | wraps |
| Plan choice name / note | `PlanOption` pill and sheet | body / secondary | unbounded in the sheet | sheet row | wraps |
| Sport tile label | `SportTile` | secondary 13, hyphens auto | 2, ellipsis | 80 dp | 12 per line |
| Summary figure label | `SummaryHero` figures | noteSmall / secondary | 1 | one third | 14 |
| Share card stat label | `ShareCards` | secondary 13 | 1 | one of 3 on a 1080 card | 14 |
| Share card title | `ShareCards` | storyTitle 30 | 1, ellipsis | full | 24 |

## 2. Recovery tab (`GlowRecoveryScreen`)

| Where | Slot | Strings that can land there | FLAG |
|---|---|---|---|
| Hero, title | cardTitle | `glow_recovery_title` Recovery | |
| Hero, title-row note | NoteText, 1 line | `core_tab_vs_yesterday_up` "+3 vs yesterday", `_down` "−3 vs yesterday", `core_tab_same_as_yesterday` "Same as yesterday" (17). Missing without yesterday | none (fits with tag and info button) |
| Hero, tag | NewsPill | `day_first_score` First score | |
| Hero, sentence | sentence 21, unlimited | `core_tab_held_back_by` joined to `core_today_meaning_*`: "Recovered enough for your usual training. Mostly held back by HRV." Empty and learning: `core_tab_recovery_waiting` "Recovery shows once last night's sleep syncs.", `core_tab_no_sleep_score` "No sleep recorded last night, so there's no score today.", `core_tab_deleted_sleep_score` "You deleted last night's sleep, so there's no score today.", `core_today_recovery_first_night` "Recovery starts after your first night with the strap.", `core_today_no_score_learning` "unstrap is still learning your nights, 3 of 7.", `core_today_no_score_heart` "Not enough heart data last night for a Recovery score.", `glow_recovery_scored_each_morning` "Recovery is scored each morning from the night's sleep." | Sentence slot, so length is fine. FLAG wording: the learning state is said 4 ways (sentence here, "Learning" + "3 of 7 nights" in detail, "Learning, 4 of 7 nights" caveat, "Learning, 3 of 7 nights" row). FLAG naming: `core_today_no_score_learning` names unstrap. FLAG: "Recovery shows once last night's sleep syncs." (hero) vs "Scored each morning from the night before" (detail) vs "Recovery is scored each morning from the night's sleep." (hero fallback): 3 wordings for waiting. |
| Hero, caveat | CaveatLine | `core_tab_learning_nights` "Learning, 4 of 7 nights" | fits |
| Hero, figure label | HeroFigure, 1 line | `core_today_recovery_*` word via `RecoveryBands.word`: "Take it easy", "Moderate", "Ready", "Ready for more"; `core_today_strap_estimate` "Strap's estimate" | |
| Hero, "last 7 days" under dots | secondary | `core_tab_last_7_days` "Last 7 days" | |
| Failed hero | sentence, figure label, button | `day_recovery_failed` "Couldn't calculate today's score.", `glow_recovery_yesterday` Yesterday, `day_try_again` "Try again" | |
| What drove it, title / note | ListPanel title, ListNote | `glow_recovery_what_drove` "What drove it", `glow_recovery_drivers_all` "All 9" | |
| What drove it, row name | DriverRow, 1 line | `core_tab_factor_*`: HRV, Resting heart rate, Last night's sleep, Sleep over recent nights, Skin temperature, Breathing rate, Yesterday's Load, Blood oxygen, When your heart settled | longest 24 chars, fits |
| What drove it, row word | secondary 13, 1 line, no ellipsis, beside the bar | `core_tab_contributor_*`: Helping, Usual, "Holding back a little", Holding back | "Holding back a little" is 21 chars (140 dp) next to a bar, fits but the bar shrinks; a word of the same set ("Holding back") says the same |
| What drove it, empty | ListRow | `glow_recovery_waiting_night` "Waiting for a night" + `glow_recovery_drivers_waiting` "What drove Recovery shows here each morning" (43) | FLAG: a sentence in a list row status (wraps to 2 lines) |
| Energy (Tall, half card), title | CardTitle | `glow_recovery_energy` Energy | |
| Energy, figure label | HeroFigure 2 lines | `glow_recovery_charge_at_waking` "82 at waking", `_at_start` "82 at start", `glow_recovery_now` Now, `core_first_night_after` "After your first night" (22), `day_failed_short` "Couldn't calculate it" (21) | ok on 2 lines |
| Recharged / Drained (Small), title | CardTitle | `glow_recovery_recharged` Recharged, `glow_recovery_drained` Drained | FLAG wording: the strings in `TabCopy.chargeRows` call the same thing "Filled" (`core_tab_charge_recharged`) |
| Recharged / Drained, status | secondary, 2 lines | `glow_recovery_overnight` "Overnight, 34 to 72" (19), `core_tab_charge_sleep_rest` "Sleep and calm rest" (19), `core_tab_mostly_movement` "Mostly movement", `core_tab_mostly_stress`, `core_tab_mostly_awake` "Mostly being awake" (18), `core_tab_nothing_yet` "Nothing yet", AFTER_FIRST_NIGHT, FAILED_SHORT | at the limit but 2 lines hold it |
| Skin temperature (Wide), title | CardTitle | `glow_recovery_skin_temperature` | |
| Skin temperature, line under number | secondary 2 lines, column is 56% of card (about 20 per line) | `glow_recovery_temp_change` "+0.3 °C against your normal" (27), `core_tab_learning_nights`/`day_sleep_vital_learning` "Learning, 4 of 7 nights" (23), `core_today_normal_learning` "Learning your normal" (20), waiting text `glow_no_reading_yet` "No reading yet" or "After your first night" | fits on 2 lines. FLAG wording: the same change is "+0.3 °C against your normal" here, "+0.3 °C" (All metrics row), "+0.3 °C, above your normal" (detail), "{change} °C vs usual" (driver note) |
| Skin temperature, tag | NewsPill | `glow_tag_above_normal` Above normal, `glow_tag_below_normal` Below normal | |
| Body signals, title / note | ListPanel | `glow_recovery_body_signals`, `glow_recovery_against_normal` "Against your normal" (19) | note competes with title in one row, fits |
| Body signals, name | RowName | Breathing rate, Blood oxygen, Stress | |
| Body signals, status | RowLine, wraps | `core_today_normal_*` "Above your normal", "Below your normal", "Usual", "Learning your normal"; learning `day_sleep_vital_learning` "Learning, 4 of 7 nights" (23); `glow_today_spo2_night_average` "Night average"; `glow_today_spo2_asleep_at` "While asleep, 05:10", `_daytime_at` "Daytime, 14:05"; stress `glow_today_latest_at` "Latest at 14:05", `glow_today_stress_level_at` "Medium, 14:05"; waiting `glow_no_reading_yet` "No reading yet" | 23-char lines wrap to 2 lines, rows of unequal height. FLAG wording: "Night average" (here) = "Average asleep" (All metrics) = "While asleep" (Night vitals). |
| Body signals, value | rowValue | "14.2", "96%", "42" | |
| Habit Age (Wide), title-row | CardTitle + InfoButton | `glow_healthspan_title` Habit Age | |
| Habit Age, line under title | secondary 1 | `core_tab_habit_age_updated` "Updated Sun 18 Oct" | |
| Habit Age, unit / tag | secondary / pill | `glow_healthspan_years` years, `glow_tag_older` Older, `glow_tag_younger` Younger | |
| Habit Age, status | secondary 2 lines, 40 per line | `core_tab_habit_age_likely` "Likely 27 to 31", `core_tab_habit_age_younger` "1 year younger than last week" (29), `_same` "Same as last week", `core_tab_first_estimate` "First estimate Sunday 22 Nov", `core_tab_age_for_now_waiting` "Your age for now. Needs 30 days of wear before a first estimate." (65), `glow_healthspan_profile_both` "Add your birth year and sex to your profile to see your Habit Age." (66) | 65 of 80 fits, but no room for a longer reason. FLAG sentence where a phrase belongs: the profile sentence in a card status slot (All metrics says "Needs birth year and sex" for the same state). |

## 3. Sleep tab (`GlowSleepScreen`, `SleepPanels`)

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Night hero, title + note | GlowHeader, note 1 line | `glow_sleep_last_night` "Last night", `core_range` "22:58 to 06:56" (14), 12 h "10:58 PM to 6:56 AM" (19) | ok |
| Night hero, sentence | sentence 21 | `core_tab_night_asleep` "7 h 40 asleep.", `_longest` "7 h 40, your longest this week.", `_shortest`, `core_tab_first_night_sleep` "Your first night with the strap shows here in the morning.", `core_tab_only_naps` "No night's sleep recorded, only naps.", `core_today_no_sleep` "No sleep recorded last night. Was the strap on?", `core_tab_no_sleep_that_night`, `core_today_deleted_last_night` "You deleted last night's sleep." | FLAG: three different "no sleep" sentences (`core_today_no_sleep`, `core_tab_no_sleep_that_night`, `day_sleep_no_night_last_night` "No sleep recorded last night." without the strap question). |
| Night hero, lines beside score | HeroNumber lines, 1 line | `core_tab_sleep_score_good` "Sleep score, good", `glow_sleep_score_adjusted` "Sleep score, good, adjusted" (27), `core_tab_no_strap_score` "No score from the strap" (23), `core_tab_woke` "Woke 3 times" / "Woke once" / "Slept through" | the lines sit beside the dots (about 200 dp, 30 chars), fine. FLAG: "Sleep score, good, adjusted" is two comma joins deep. |
| Night hero, stage legend | Legend flow | `core_tab_stage_total` "Deep 1 h 32", "Awake 18 min" | |
| Night hero, restore | PageButton | `glow_sleep_restore` Restore | |
| Night vitals, title / note | ListPanel | `glow_sleep_vitals_title` "Night vitals", `glow_sleep_vitals_note` "Against your normal" | |
| Night vitals, name | RowName | `day_sleep_vital_lowest_heart` "Lowest heart rate", HRV, Breathing rate, Blood oxygen, Skin temperature, `day_sleep_vital_average_heart` "Average heart rate" | |
| Night vitals, status | RowLine wraps | `day_sleep_vital_lowest_at` "At 03:10", `day_sleep_vital_while_asleep` "While asleep", `core_today_normal_*`, "+0.3 °C", `day_sleep_vital_learning` "Learning, 4 of 7 nights", `glow_today_spo2_night_average` "Night average"; missing: `core_first_night_after` "After your first night", `day_sleep_vital_no_sleep` "No sleep recorded", `day_sleep_vital_no_reading` "No reading" | FLAG wording: "No reading" (vitals) / "No reading yet" (Recovery rows) / "Nothing for this night". |
| Regularity (Wide), title + note | EdgeTitle | `glow_sleep_regularity_title` "Sleep regularity", `glow_sleep_last_7_nights` "Last 7 nights" | |
| Regularity, status | SleepStatus 1 line, wide (38) | `core_tab_regularity_needs` "Needs about 5 days of wear", `_steady` "Steady bedtimes this week", `_moved` "Bedtimes moved this week", SRI band labels (Very regular etc.), `glow_sleep_regularity_none` "No sleep recorded in the last 7 nights" (38), AFTER_FIRST_NIGHT | FLAG: "No sleep recorded in the last 7 nights" (38) is at the 1-line limit on narrow phones; a sentence where a phrase ("No nights yet") belongs. |
| Regularity, usual times | SleepStatus 1 line | `core_tab_usual_times` "Usually asleep by 23:10, up at 06:50." (37, with full stop) | At the limit. FLAG: the only status line in a card that ends with a full stop. |
| Regularity, tag | NewsPill | Later, Earlier | |
| Breathing disturbances (Small, half card), title | CardTitle 2 lines | `glow_sleep_breathing_title` "Breathing disturbances" (22) | wraps to 2 lines, ok |
| Breathing, unit | secondary beside dots, shares FlowRow with the tag | `glow_sleep_an_hour` "an hour" | FLAG: unit is a phrase; with a tag pill the pill drops under the number |
| Breathing, status | SleepStatus **1 line, ellipsis**, half card (18 to 20) | `core_tab_breathing_short_learning` "Learning, 3 of 7 nights" (23), `core_tab_breathing_short_usual` "Usual 2.1, early estimate" (25), AFTER_FIRST_NIGHT "After your first night" (22) | **FLAG, cut (3 strings).** |
| Naps (Small), title | CardTitle | `core_tab_naps_title` Nap / Naps | |
| Naps, value | HaloNumber | clockDuration "1:30" (no unit) | FLAG: "1:30" with no unit beside "7 h 40" elsewhere; reads as a clock time. |
| Naps, status | SleepStatus 1 line, half card | `core_tab_nap_at` "At 14:10", `core_tab_naps_first_at` "2 naps, first at 14:10" (22) | **FLAG, cut.** |

## 4. Activity tab (`GlowActivityScreen`)

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Start a workout (Wide) | EdgeTitle; SportTile | `glow_activity_start_workout`, sport names (up to "Strength training", "Open water swim", "Rowing machine"), `glow_activity_all_sports` "All sports" | tile handles 2 lines with hyphens; ok |
| Steps hero, title note | NoteText 1 | `glow_activity_goal` "Goal 8,000" | |
| Steps hero, tag | pill | "Partial day", goal tags (`glow_tag_*`: "Goal met", "+2,140 past goal", "3 days in a row", "Personal best", "Best this month", "Double goal") | "+2,140 past goal" is 16 chars in a pill beside the number; drops under on narrow |
| Steps hero, label | secondary, 2 lines, full | `core_activity_line_*` "62% of your goal, fewer than your usual day" (43), `core_activity_goal_met` "Goal met", hour pick `core_activity_hour_label` "steps, 09:00 to 10:00" | fits |
| Active minutes (Small), title | CardTitle 2 lines | "Active minutes" | |
| Active minutes, unit | secondary | `glow_unit_min` min | |
| Active minutes, status | StatusLine 2 lines | `core_tab_active_goal` "Aim for 150", `core_tab_active_reached` "Goal of 150 met", `glow_no_reading_yet` "No reading yet" | ok |
| Heart points (Small), status | StatusLine 2 lines | `core_tab_points_aim` "Aim for 100", `core_tab_points_reached` "Goal of 100 met", `glow_after_next_sync` "After the strap's next sync" (27) | 27 uses both lines of a half card. FLAG: "Aim for 100" and "Goal of 100 met" in the same slot read like two phrasings of one goal. |
| Load (Wide), title note | NoteText | `core_tab_balance_*` Building / Easing off / Steady | |
| Load, unit | secondary | `glow_activity_load_today` today, `_that_day` "that day" | FLAG: unit is a word after the number ("85 today") |
| Load, status | StatusLine 2 lines | `core_activity_usual_band` "Your usual, 40 to 55", `core_tab_load_usual` "Your usual is 40", `core_tab_load_learning` "Learning your usual", `glow_no_reading_yet` | FLAG wording: "Your usual, 40 to 55" vs "Your usual is 40": two forms of one fact |
| Heart rate (Wide), title note | NoteText 1 | `glow_today_resting` "Resting 58", `glow_today_resting_estimated` "Resting 58 est." | FLAG: "est." abbreviation; elsewhere "estimated" is spelled out ("For the day, estimated") |
| Heart rate, status | StatusLine 2 | `core_today_heart_average_from` "10 min average from 20:52", `core_today_heart_at` "At 20:52", `core_today_heart_live` "Live now", `core_today_heart_waiting` "Waiting for the strap" | |
| Today list (title "Today", note "So far") | ListPanel | `glow_activity_today`, `glow_activity_so_far` | |
| Today list, VO2 max row | RowName, RowLine wraps, value | value "38" or "40 to 45"; status `glow_activity_vo2max_from_workouts` "From your workouts", `glow_activity_vo2max_band` "Good for your age", `glow_activity_vo2max_estimated` "Estimated from your profile" (27), `glow_activity_vo2max_after_workout` "After a workout the strap rates" (31) | FLAG: "After a workout the strap rates" reads as cut off (rates what?). Same text for Time to recover (`glow_activity_recover_after_workout`). Two string keys, same words. |
| Today list, Time to recover row | value hours, status | `core_today_recover_short_by`-style via `recoverText`: "Hours left, ready by 21:30", "Hour left, ready tomorrow", "Recovered"; `glow_activity_no_recent_workout` "No recent workout" | "Hours left, ready by 21:30" (26) wraps |
| Today list, Calories row | value, status, tag | `day_calories_row_line` "Active of 500. 800 total" (24), `_met` "Active, goal met. Total 800" (27), `glow_activity_add_profile_to_see` "Add age, weight and height to see it" (36) | FLAG: full-stop join in a status line ("Active of 500. 800 total") while neighbours use commas; "Add age, weight and height to see it" is a sentence in a status slot (wraps to 2); `glow_add_profile` says "Add age, weight and height" and `glow_metrics_needs_profile` "Needs birth year and sex" for the same prompt |
| Today list, Distance row | status | `core_activity_distance_from_steps` "km, from your steps", `_from_strap` "km, from the strap", `glow_no_reading_yet` | "km" repeated in status and value |
| Workouts list, title note | ListNote | `glow_activity_this_day` "2 on this day" | |
| Workouts list, day header | secondary 12, 50% white | `core_today_...` "Today", "Yesterday", "Tuesday", "Wed 21 Oct" | |
| Workout row | name body 1 ellipsis, auto tag, line secondary 1 ellipsis, value rowValue | sport names; `core_tab_auto_detected` "Auto-detected"; line `core_tab_workout_line` "5.2 km, 5:24/km, 17:35"? (fact is "5.2 km, 5:24/km" then joined with the time: "5.2 km, 5:24/km, 17:35"), `core_tab_fact_average_bpm` "Average 128 bpm, 17:35", `core_tab_fact_sets` "14 sets, 17:35" | line holds about 40; "5.2 km, 5:24/km, 17:35" 22 ok; FLAG: a fact and a time joined by the same comma as the pace |
| Suggestion row | ListRow status wraps | `glow_activity_suggestion` "Looks like a workout", `glow_activity_suggestion_line` "07:10 to 07:45, mostly zone 3, 132 bpm on average" (47) | 3 clauses, wraps to 2 lines; `glow_workout_suggestion_*` (history) names the same card "Looks like you worked out" and "Save as workout" vs "Save" + "Not a workout": FLAG wording |
| Empty workouts | ListRow | `glow_activity_no_workouts` "No workouts yet" + `glow_activity_no_workouts_note` "Workouts you record show up here" | History says "Workouts you start in unstrap show up here, and the strap's own after a sync." FLAG: two wordings, the second names the app |
| Rows | PageRow | `glow_activity_add_workout` "Add a workout you didn't record", `glow_activity_all_workouts` | |

## 5. Metric detail (`GlowMetricDetail`, `DetailCopy`, `SleepCopy`)

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Top bar | pageTitle + info label | metric title; `glow_metrics_about` "About Heart rate"; `glow_metrics_show_day` "Show Wed" | |
| Week strip | caption | weekday short | |
| Hero, no value: verdict | verdict 24, unlimited | `glow_no_reading` "No reading", `glow_loading` "Loading…", `day_sleep_no_sleep` "No sleep" | |
| Hero, no value: label | secondary, unlimited | `core_detail_none_*` "Measured during sleep", "For last night", "For that night", "So far today", "For that day", "In the last 7 days", "In the last 30 days", "In the last 12 months"; `day_sleep_none_last_night` "None recorded last night", `day_sleep_not_measured_last_night` "Not measured last night" | FLAG wording: "For last night" vs "None recorded last night" vs "Nothing for this night" for the same empty. |
| Hero: unit + context | body unit; secondary context from `parts()` | label strings: `core_detail_label_*` "bpm now", "bpm, latest", "ms, day average", "steps so far today", "{unit}, daytime, latest at 14:05", "{unit}, while asleep, latest at 05:10", "High, latest at 14:05", "steps a day, last 7 days", "Average of the last 7 days"; `day_sleep_night_label_*` "{unit}, average of the last 7 nights", "{unit}, nights of the last 12 months" | the label is parsed on its first comma into unit and context, so a missing comma (or unit longer than 8 chars) changes the layout. FLAG: "{unit}, last 30 days, day and night" (`day_sleep_night_label_month_days`) is long for a context line (35) but wraps. |
| Hero: status word | cardTitle 15, **1 line clip** | `core_detail_status_above` "Above your normal", `_below`, `_usual` "Usual"; skin `core_detail_skin_above` "+0.7 °C, above your normal" (26), `_within` "+0.2 °C, within your normal" (27) | fits full width, but a heroSide would shrink it. FLAG wording: "Above your normal" (word) vs "Above normal" (tag) vs "Above usual". |
| Hero: goal bar | DetailGoalBar | `glow_detail_of_goal` "of 8,000" | |
| Scrub readout | context | `glow_metrics_unit_day` "bpm, Wed 21", `glow_metrics_unit_average_from_time` "bpm, 10 min average from 22:30", `glow_metrics_steps_hours` "steps, 09:00 to 10:00", `glow_sleep_scrub_at` "bpm at 03:10", `glow_sleep_scrubbed` "62 bpm at 03:10" | |
| Range segments | GlowSegments | Day, Week, Month, Year | |
| Sentence under chart | detailSentence 19 | `core_detail_day_*` "Last night, within your normal.", "Today's average so far, above your normal.", "That day, within your normal."; `core_detail_run_*` "Below your normal for three nights."; `core_detail_every_night*` "Within your normal every night with data in the last 30 days." (62); `core_detail_inside_*` "Within your normal on 5 of 7 nights."; `core_detail_year*` "Over the last 12 months, weekly averages ran from 48 to 61 ms."; `core_detail_learning_*_sentence` "Learning your normal, 4 of 7 nights."; `core_detail_heart_day_today` "Between 52 and 138 bpm so far today."; `day_sleep_heart_lowest_last_night` | sentence slot, fits. FLAG wording: the learning state here is "Learning your normal, 4 of 7 nights." with a stop and "Learning your normal, 4 of 7 days" without (`core_detail_learning_days`). |
| Empty chart | DetailEmpty | `glow_metrics_none_last_7_nights` "Nothing in the last 7 nights yet.", `glow_metrics_none_in_range` "Nothing in this range yet.", `glow_metrics_none_asleep` "No readings while asleep.", `glow_metrics_none_this_day` "Nothing for this day yet.", `glow_metrics_breathing_in_sleep` "Breathing rate is measured during sleep." | FLAG: 4 ways to say nothing, see section 12 |
| Figures (row of three), values | figure 28, 1 line clip | "62 bpm", "−12.3 ms", `glow_no_reading` **"No reading"** when missing (default `missing`) | **FLAG A7: "No reading" at 28 sp is about 130 dp in a 80 to 96 dp cell.** Shown for Your normal, 7 day average and vs normal while the normal is still forming. |
| Figures, labels | noteSmall 12, 1 line clip, third | `glow_detail_low` Low, `glow_detail_average` Average, `glow_detail_high` High, `glow_recovery_lowest` Lowest, `glow_detail_your_normal` "Your normal" (11), `glow_detail_seven_night_average` "7 night average" (15), `glow_detail_seven_day_average` "7 day average" (13), `glow_detail_vs_normal` "vs normal", `glow_detail_vs_yesterday` "vs yesterday" (12) | **FLAG: "7 night average" (15) and "7 day average" (13)** over the 12 rule; "7 night" has no hyphen but `glow_activity_week_total_on` says "7-day total". |
| Band label on chart | `DetailCopy.bandLabel` / `glow_detail_band_note` | "Your normal, 44 to 58", "The band is your normal" (23) | noteSmall under the chart, fine |
| Related panel | DetailPanel title Related; DetailRow | `glow_metrics_related`; status `day_*`, "Night average", "Learning, 4 of 7 nights", status word, `glow_no_value` "Nothing for this day" | |
| Switcher sheet | row | titles, `glow_metrics_showing` "Showing" | |
| Info | InfoSheet | see section 9 | |

## 6. Score details (`ScoreDetail`, `SleepDetail`, `ActivityDetail`)

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Recovery hero verdict + context | verdict 24 (2 lines) beside gauge; context secondary | `core_detail_recovery_waiting` "Waiting" + "For last night's sleep"; `_learning` "Learning" + "6 of 7 nights"; `_no_score` "No score" + "Not enough heart data that night" (32) or "Scored each morning from the night before"; score word + "this morning" / "that morning" | the best pattern in the app: a word and a short phrase. FLAG: "Not enough heart data that night" (32) wraps beside the gauge (150 dp is about 22 chars) to 2 lines. |
| Recovery, strap estimate | context | `glow_recovery_strap_estimate` "The strap's estimate" | FLAG: `core_today_strap_estimate` "Strap's estimate", same idea, different article |
| What drove it, heading + note | DetailHeading | "What drove it", "Against your normal" | |
| What drove it, name / note | DetailDrivers name body 1 clip, note secondary 1 clip, **note not weighted** | name `core_tab_factor_*`; note `core_data_recovery_*`: "62 ms, +12% vs usual", "48 bpm, −3 vs usual", "7 h 40 asleep", "5 h 20 of sleep debt", "Lowest heart rate 25 min before waking" (38), "+0.3 °C vs usual", "+0.4/min vs usual", "96%, −1 vs usual", "Load 40, usual 38" | **FLAG A6: the note is measured first. A 38-char note takes about 250 dp of 320 and squeezes the name ("Sleep over recent nights", 24 chars) to about 60 dp, clipped with no ellipsis.** |
| Drivers axis | caption | `glow_detail_worse` Worse, `glow_detail_your_normal`, `glow_detail_better` Better | |
| Recovery week | DetailEmpty / sentence | `glow_recovery_none_week` "No Recovery scores in the last 7 days yet.", `core_detail_score_week` "Averaged 74 over the last 7 days, from 61 to 84." | |
| Energy hero | verdict + context | "Waiting" + `glow_recovery_after_first_night` "After your first night"; "No reading" + `glow_recovery_needs_heart_rate` "Needs a few hours of heart rate"; score word (`day_info_scale_charge_*` Running low, Some left, Good, Full) + "now" / "at 14:30" / "at the end of the day" | FLAG: `glow_recovery_after_first_night` and `core_first_night_after` are two keys with the same text |
| Energy, rows | DetailRow | `core_tab_charge_*` "Sleep and calm rest" "Filled", "Movement" "Drained", "Stress", "Being awake"; `glow_detail_at_waking_note` "82 at waking"; `glow_detail_since_woke` "Since you woke"; `glow_detail_through_day` "Through the day" | FLAG: "Filled" vs card "Recharged" |
| Energy, sentence | detailSentence | `core_today_energy_*` "The morning drained 12. Plenty left for this evening." | |
| Sleep score hero | verdict + context | word Poor/Fair/Good/Great + "last night" / "that night" / "last night, adjusted" (`day_sleep_score_label_adjusted`); "No sleep" + "None recorded last night"; "No score" + "The strap didn't score last night" (33) | 33 chars wraps beside gauge (2 lines). FLAG: "None recorded last night" vs "No sleep recorded" |
| Sleep, what shaped it | DetailDrivers-like rows | `day_sleep_factor_*` "Time asleep" "7 h 40 of 8 h", "Staying asleep" "12 min awake after falling asleep" (30); words "Helping", "On track", "Holding it back a little", "Holding it back" | FLAG: "Holding back a little" (Recovery) vs "Holding it back a little" (Sleep): same word set, different wording |
| Sleep, actions | buttons / sheet | `glow_sleep_adjust_times` "Adjust times", `glow_delete`, `day_sleep_delete_title_*` "Delete last night's sleep?", `day_sleep_delete_what`, `day_sleep_delete_restore`, `glow_sleep_use_strap_times` "Use the strap's times", `day_sleep_times_hint` | sentences in a sheet, fine |
| Regularity detail | verdict + context | `day_sleep_regularity_learning` "Learning" + "Needs about 5 days of wear"; band label; `day_sleep_regularity_*` "Very regular this week. Your days kept nearly the same sleep times." | |
| Naps detail | | `day_sleep_naps_one` "One nap, 45 min.", `_many` "Three naps, 1 h 20 in all.", `day_sleep_nap_heart`, `day_sleep_nap_no_stages`, `glow_sleep_no_naps_today` "No naps recorded today." | |
| Heart points detail | verdict + context | "{n}" + `glow_activity_points_week_average`/"points, last 7 days", `glow_activity_no_points` "No points" + "In the last 30 days", "No points yet" | |
| Active minutes detail | | "No reading" + "Needs the strap's steps and heart rate" (38) | wraps; fits as context line |
| Load detail | figures | `glow_activity_from_workouts` "From workouts" (13), `glow_activity_moving_about` "Moving about" (12), `glow_activity_strain` **"Vs usual"** | **FLAG: capital "Vs" while the others are "vs normal" and "vs yesterday"; label meaning is also vague** |
| Load detail | rows / lines | `glow_activity_this_week` "This week", `glow_activity_your_usual` "Your usual" + `glow_activity_your_usual_line` "Daily average, last 6 weeks", "Last 7 days" + "Daily average", `glow_activity_strap_weekly_load` "The strap's weekly Load", `glow_activity_load_you_are_here` "You are here. Your usual is 85 a day." | |
| VO2 max detail | | `glow_activity_vo2max_unit` "ml/kg/min", "Good for your age", "You are here: Good for your age." (`glow_activity_vo2max_you_are_here`), `glow_activity_no_estimate` "No estimate" + "After a workout the strap rates", `core_data_vo2_needs_*` "Needs an age between 20 and 79." | FLAG: "You are here. Your usual is..." (full stop) vs "You are here: Good for your age." (colon): one phrase, two joins |
| Time to recover | | `glow_activity_recovered` "Recovered" + "After your run, yesterday at 18:40", `glow_activity_hours_to_go` "hours to go", `glow_activity_recover_ready` "Ready" | |
| Calories detail | figures | `glow_activity_total_kcal` "Total kcal" (10), `glow_activity_at_rest` "At rest", `glow_activity_activity_level` **"Activity level" (14)**, `glow_activity_goal_label` "Goal" | **FLAG: 14 chars** |
| Steps week | figures | `glow_activity_active_minutes` **"Active minutes" (14)**, "Workouts", `glow_activity_asleep_a_night` **"Asleep a night" (14)** | **FLAG** |
| Misc | | `glow_activity_total_a_day` "Total a day", `glow_activity_active_kcal_in_all` "Active kcal in all", `glow_activity_by_the_hour` "By the hour", `glow_activity_legend_walking` "Walking" / "Everyday" / "The rest" | |

## 7. All metrics (`AllMetricsScreen`)

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Group titles | panel title (muted secondary) | Recovery, Sleep, Heart, Body signals, Activity, Workouts | |
| Row name | RowName | Recovery, Energy, Habit Age, Sleep, Steps, Heart points, Active kcal, Load, Workouts, Heart rate, Resting heart rate, HRV, Blood oxygen, Skin temperature, Breathing rate, Stress | group and row both "Recovery", both "Sleep", both "Workouts" |
| Row status | DetailRow status, noteSmall, 2 lines, ellipsis (about 28 per line) | `glow_metrics_score_for_day` "Score for the day", `_for_night` "Score for the night", `glow_metrics_learning_nights` "Learning, 4 of 7 nights", `glow_metrics_latest` Latest, `glow_metrics_end_of_day` "End of day", `glow_metrics_updated_weekly` "Updated weekly", `glow_metrics_starting_point` "Starting point", `glow_metrics_needs_profile` "Needs birth year and sex", `glow_metrics_not_worked_out` "No estimate yet", `glow_metrics_for_the_day` "For the day", `_for_the_day_estimated` "For the day, estimated", `glow_metrics_last_7_days` "Last 7 days", `glow_metrics_day_average` "Day average", `glow_metrics_night_average` "Average asleep", `glow_metrics_latest_reading` "Latest reading", "+0.3 °C", "Daytime, 14:05", "Latest at 14:05", waiting `glow_no_value` "Nothing for this day", AFTER_FIRST_NIGHT | all fit. FLAG wording: "Nothing for this day" here, "No reading yet" on tabs, "No reading" in details; "Average asleep" vs "Night average"; "Latest" vs "Latest reading" vs "Latest at 14:05". |
| Row value | rowValue | numbers | |
| Search | body | `glow_metrics_search` "Search metrics", `glow_metrics_no_match` "No metric called %1$s" (no quotes, no full stop) | FLAG: `glow_choose_no_match` is 'No sport matches "curling".' (quotes + full stop): same job, different punctuation |

## 8. Info sheet (`InfoSheet`, `InfoCopy`)

| Slot | Strings | FLAG |
|---|---|---|
| Name, sheetTitle 22, 2 lines | metric or score name, "Load and training effect", "Habit Age" | |
| Meaning, how, moves, off blocks (body 15 on 22.5, unlimited) | the 30 `day_info_*_meaning/_how/_moves/_off` paragraphs, 1 to 3 sentences each, ending with full stops | paragraphs, so fine. They name unstrap (9 strings: Recovery how, Regularity how, Recover how...) and use "the strap" and "unstrap" for the actor inconsistently ("Each morning unstrap compares" vs "The strap calculates them itself"). |
| Scale words, 3 or 4 cells | `day_info_scale_normal_*` "Below your normal", "Usual", "Above your normal"; `day_info_scale_charge_*` "Running low", "Some left", "Good", "Full"; Recovery and Sleep bands | auto-fit shrinks to caption; "Above your normal" in a third at 12 sp is the tightest |
| "You are here" | `day_info_here` "You are here." and `day_info_normal_line_*` "You are here. Your normal is 44 to 58, from 21 nights." | |
| Notes | `day_info_*_note_*` | sentences |

## 9. Workouts: history, past workout summary

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| History title / count | pageTitle, secondary | `glow_workout_list_title` Workouts, `glow_workout_list_recorded` "24 recorded" | |
| History rows | `WorkoutRow` (same as Activity) | see section 4 | |
| Empty | ListRow | `glow_workout_list_empty` "No workouts yet", `glow_workout_list_empty_text` "Workouts you start in unstrap show up here, and the strap's own after a sync." (77) | FLAG: sentence, names app; Activity tab empty note differs |
| Past summary, top bar | pageTitle | `glow_workout_untitled` Workout, sport name | |
| Past summary, date line | secondary | `glow_workout_day_times` "Yesterday, 18:02 to 18:44" | |
| Summary hero sentence | detailSentence | `workout_detail_sentence_*` "Easy run, mostly in zone 2.", "Short walk.", "Hard strength training, mostly in zone 4." | |
| Hero number + figure labels (row of three) | BigNumber + noteSmall label | `glow_summary_figure_time` Time, `_avg_bpm` "Avg bpm", `_peak_bpm` "Peak bpm", `_active_kcal` "Active kcal", `_avg_pace` "Avg pace", `_load` Load, `glow_summary_hero_avg_bpm` "Average heart rate", units "min", "kg lifted", "reps done", "rounds done", "min played" | labels abbreviate ("Avg") where lines spell out ("Average 128 bpm", "Average heart rate"): 14 strings use Avg/Avg bpm. FLAG: "Average heart rate" (18) as a hero title in a row of three if it lands there. |
| Heart rate block | cardTitle, figureLarge, secondary | `glow_summary_heart_rate`, `glow_workout_heart_by_zone` "Coloured by zone", `glow_workout_heart_at` "At 18:20", `glow_workout_heart_max_lowest` "Highest 172, lowest 98 bpm" (26), `glow_workout_unit_bpm_average` "bpm average", `glow_workout_heart_drop_after` "Drop after 18:44", `glow_workout_heart_dimmed` "Dimmed: 18:02 to 18:10 came from the strap's history." (colon) | "bpm average" vs "Average 148 bpm" vs "Avg bpm" |
| Heart rate drop | cardTitle, figure, noteSmall | `glow_summary_drop_title` "Heart rate drop", `glow_summary_drop_unit` "bpm in a minute", `workout_summary_drop_*` "Down 31 in the first minute. A quick drop for you.", `workout_summary_watching`, `workout_summary_no_drop_data` "No heart rate after the end, so no drop this time." | FLAG: `workout_detail_drop*` ("Heart rate drop of 31 bpm in the first minute, quick for you.") says the same as `workout_summary_drop_*` in a different frame |
| Load block | | `workout_summary_load_above` "Above your usual of 85", `_usual` "About your usual", `workout_summary_load_learning` "Learning your usual", `glow_summary_load_tag_usual` "About usual", `workout_summary_time_to_recover` "Time to recover 14 h" | |
| Zones | secondary 1 line row | `glow_summary_zone` "Zone 3", `glow_summary_zone_minutes` "Zone 4, 27 min", `glow_summary_zones_max` "Max 190 bpm", `glow_workout_zone_range` "134 to 152 bpm", `glow_workout_zone_under` "under 115 bpm" | |
| Splits / laps / sets | rows | `glow_summary_split_average` "Average 148 bpm", `glow_summary_lap` "Lap 2", `glow_summary_set` "Set 2", `glow_summary_set_rest` "Rest 1:30", `glow_summary_exercise_total` "2,620 kg", `workout_summary_lap_distance_average` "1.00 km, average 148 bpm" | |
| vs last time | secondary | `glow_summary_vs_last` **"vs last time: "** (string includes colon and space, joined in code) | FLAG: punctuation in the string; "vs" lowercase here, "Vs usual" capital in Load |
| Trim | | `glow_workout_trim_*` "Trimmed to start at 18:05. Nothing is deleted.", "Ended at 18:44. 6 min of driving was left out.", `workout_summary_trim_hint` | |
| Delete | | `glow_workout_delete` "Delete this workout", `glow_workout_delete_confirm`, `glow_workout_delete_phone` / `_strap` (name unstrap) | |
| Buttons | | Share, Done, Trim, Save, Cancel, Undo | |

## 10. Workouts: choose sport, ready, live, summaries, share

| Where | Slot | Strings | FLAG |
|---|---|---|---|
| Choose, title / search | pageTitle, secondary | `glow_choose_title` "Start a workout", `glow_choose_search` Search, `_search_all` "Search all sports", `glow_choose_recent` Recent, `glow_choose_your_usual` "Your usual" | |
| Choose, group header + note | cardTitle, noteSmall | `glow_choose_group_*` Distance, "Indoor cardio", "Strength and intervals", "Mind and body", Games, Other; notes "Route, distance and pace", "Time, heart rate and calories", "Sets, rounds and rest", "Calm heart rate and time", "Time, heart rate and effort", "Anything not listed. Time and heart rate" | FLAG: one note ("Anything not listed. Time and heart rate") has a full stop in the middle, the rest are comma-free lists |
| Choose, sport rows / tiles | body 1 ellipsis / tile 2 lines | 60 sport names | longest "Strength training", "Open water swim", "Rowing machine" fit in rows; tiles hyphenate |
| Choose, now / sheet | noteSmall | `glow_choose_now` Now, `glow_choose_now_note` "Your goal comes along when the new sport has one like it" (56), `glow_choose_change_title` "Change sport", `glow_choose_no_match` 'No sport matches "curling".', `glow_choose_sports_count` "12 sports" | FLAG: a sentence in a noteSmall slot |
| Ready, status line | noteSmall 1 line | `workout_start_strap_ready_bpm` "Strap ready, 142 bpm", `workout_live_status_strap` Strap, `_strap_battery` "Strap 64%", `workout_start_strap_connecting` "Connecting to the strap", `workout_start_strap_not_paired` "No strap paired. You can start anyway." (38), `_not_connected` "Strap not connected. You can start anyway.", `glow_ready_strap_disconnected` "Your strap is disconnected. Recording works without it, but with no live heart rate." (84) | FLAG: 3 wordings for strap-not-there (paired / connected / disconnected), two name "the strap", one "Your strap". 84 chars is a 3-line sentence in a status slot. |
| Ready, hero title / note | heroTitle, secondary tint | `workout_start_plan_title_*` Goal / Plan / Length / Match, `workout_start_ends_near` "Ends about 19:15" | |
| Ready, hero number + unit | BigNumber, body | "5" "km", "30" "min", "4" "sets, 90 s rest", "8:00" "20 / 10", `glow_ready_hero_zones` "zones", "min halves" | unit text is a phrase ("sets, 90 s rest"); fits |
| Ready, hero line | HeroLine, wraps | `workout_start_distance_note` "The strap buzzes at halfway and at 5 km." (40), `workout_start_time_kind_note` "A buzz when the time is up", `workout_start_zone_note` "Tap a zone, or a second one for a range. The strap buzzes when you leave it." (77), `workout_start_strength_status` "The strap buzzes when rest is over, so the phone can stay on the bench." (71), `workout_start_intervals_status` "The strap buzzes at every switch, so the phone can stay on the floor." (70), `workout_start_mind_status` "Quiet strap and a dim screen. One soft buzz at the end." (55), `workout_start_no_goal_note` "No goal. Time and heart rate.", `workout_start_games_note_*` "Phone in your bag? That's fine. The strap buzzes at the end of each half and break." (82) | FLAG, 7 strings: sentences (55 to 82 chars) in a slot the code calls "one 13 px line". They wrap rather than cut, so each adds 1 to 2 lines to the hero. |
| Ready, plan pill name + sheet note | PlanOption | `workout_start_kind_*` Distance, Time, Zone, Intervals; notes "A buzz at halfway and at the goal", "A buzz when the time is up", "A buzz when you leave your zone", "Reps with rest between, a buzz at each switch"; Strength "As many sets as you like, rest after each" (41), "A number of sets with rest between"; Mind "No length, end it yourself"; Games "Two halves with half time between", "No periods, the clock just runs" | the kind notes are phrases joined with commas; the matching hero notes are two sentences with full stops ("No timer. Laps mark the rounds." vs kind note "No timer, laps mark the rounds"): FLAG wording, `workout_start_hiit_free_note` vs `workout_start_preset_free_note` |
| Ready, GoalNote under value | secondary centred | `workout_start_strength_typing` "How many sets, then the rest in seconds", `hiit_typing`, `every_typing`, `games_typing`, `intervals_typing`, `tap_a_number` | |
| Ready, last time | noteSmall caption + labels | `glow_ready_last_caption` "Last time, Tue", labels Time, "Avg bpm", "Peak bpm", "Active kcal", Load, Sets, Distance, "Avg pace" | |
| Ready, GPS | | `workout_start_gps_finding_accuracy` "Finding GPS, ±180 m", `_gps_ready` "GPS ready, ±8 m"; live `core_workout_gps` "GPS ±8 m" | FLAG: "Finding GPS, ±180 m" vs "GPS ±8 m": comma vs none |
| Live, top bar | cardTitle ellipsis + noteSmall | sport name; "Strap 64%"; `workout_live_status_paused` Paused | |
| Live, figure labels (row of 2 to 3) | LiveStat noteSmall 1 line | Distance, "Avg speed", "Avg pace", "Avg bpm", "Active kcal", "Peak bpm", "Hard efforts", "m climbed", "kg lifted", "Sets done", "From steps", Rounds | "Hard efforts" (12) and "Active kcal" (11) at the limit |
| Live, hero lines | secondary | `workout_live_rep_of` "Rep 3 of 6", `workout_live_round_of` "Round 3 of 8", `workout_live_view_of` "View 1 of 2", `workout_live_done_of` "2 of 6 done", `workout_live_effort_of` "Effort 3 of 5", `workout_live_heart_of_max` "78% of max", `workout_live_time_target` "12:30 of 30 min" | consistent "of" pattern; FLAG: Ready's "20 / 10" and "40 / 20" use slash |
| Live, notes | secondary / noteSmall | `workout_live_target_zone` "Target zone 2. The strap buzzes if you drift" (no final stop), `workout_live_paused_note` "Pausing keeps this time out of your pace and averages" (no stop), `workout_live_sets_set_line` "Rest 90 s after. The strap buzzes when it's over." (stop), `workout_live_sets_next_tap_start` "Set 4 of 5 next. Tap Start when you pick up the weight." (stop), `workout_live_reps_start_hint` "Tap Skip to start the reps" (no stop), `workout_live_tap_to_light` (no stop) | FLAG: same job (a hint line), stop or no stop |
| Live, prompts | sheet | `workout_live_stopped_title` "Looks like you've stopped", `workout_live_stopped_note` "Your heart rate has been back near resting since 18:40. End the run at 18:40?", `workout_live_not_saved_note` | |
| Live, buttons | | `workout_live_hold_to_end` "Hold to stop", `workout_live_end_or_keep` "Hold the square to end, or keep going", `workout_live_minimize` "Minimise", Skip, Lap, Pause, Resume | FLAG: "stop" vs "end" for the same action; British "Minimise" |
| Live, interval phase | phaseWord 30 | Work, Rest, Done, "Warm-up", "Last round", "Round 4 next", "Then 8 rounds", "seconds left" | |
| Summary: hero etc. | see section 9 | | |
| Share, card title | storyTitle 30, 1 line | sport name | "Strength training" at 30 sp is about 250 dp of a 320 dp card: fits |
| Share, stats | secondary 1 line, one of 3 | `glow_share_stat_*` Time, "Avg bpm", "Peak bpm", "Active kcal", Pace, Sets, Load, "Bpm at start", "Bpm at end", Work; units "/km", kg, "of 8 reps", "of 8 rounds" | |
| Share, rows | body 1 ellipsis | `glow_share_row_top` "5 × 10, top 65 kg", `glow_share_row_reps` "5 × 10", `glow_share_join` "141 avg bpm, 320 kcal" | FLAG: "avg bpm" after the number here, "Avg bpm" label on the card, "Average 141 bpm" in lines: 3 word orders |
| Share screen | | `glow_share_saved` Saved, `glow_share_not_saved` "Not saved", `glow_share_add_photo` "Add a photo", `glow_share_photo_failed` "Couldn't read that photo", `glow_share_hide_ends_note` "Cuts 200 m off each end of the line" | |
| Heart zones page | | `workout_zones_*` "Heart rate zones", "Your max heart rate", `workout_zones_note` (sentence, 120), `workout_zones_source_*` "Learned from your hardest efforts, 12 Nov", "From your Zepp profile, until a hard effort shows more" (54) | FLAG: names Zepp; the source line (54) is a sentence in a row line; `glow_metrics_zone_title` "Zone 2, Aerobic" |

## 11. Worst problems (ranked)

1. **A6. Recovery detail, "What drove it": long note squeezes the row name.** `DetailDrivers` measures the unweighted note
   first. "Lowest heart rate 25 min before waking" (38 chars, about 250 dp of 320) leaves the name about 60 dp, clipped
   with no ellipsis. Also "5 h 20 of sleep debt", "62 ms, +12% vs usual". Fix: note gets weight, name keeps min width, or move the
   note to its own line.
2. **A7. Detail figures show "No reading" at 28 sp in a third-width cell** (`DetailFigures` default `missing`). About 130 dp of
   80 to 96 dp. Hits Your normal / 7 day average / vs normal on the first week of any metric.
3. **Breathing disturbances Small, status on 1 line, 3 strings cut at about 19 chars:** "Learning, 3 of 7 nights" (23),
   "Usual 2.1, early estimate" (25), "After your first night" (22). `SleepStatus` is `maxLines = 1` where the other Smalls allow 2.
4. **Naps Small: "2 naps, first at 14:10" (22) cut** by the same `SleepStatus`; the value is "1:30" with no unit.
5. **Today's Recovery sentence in a word slot** (`core_today_no_score_learning`, "unstrap is still learning your nights, 3 of
   7."): a sentence that names the app where the detail already has "Learning" + "3 of 7 nights". Also a sentence in the Recovery
   hero where the Sleep tab breathing card says "Learning, 3 of 7 nights".
6. **Learning state worded 6 ways** (see section 12): one word, "Learning, 3 of 7 nights", "Learning your normal, 3 of 7 nights",
   "Learning your usual, 3 of 7 nights", "Learning your usual", the unstrap sentence.
7. **Four ways to say "nothing here"** in the same screens: "No reading", "No reading yet", "Nothing for this day/night", "Nothing for
   this day yet.", plus "No value yet", "None recorded last night", "For last night", "Not measured last night".
8. **"After a workout the strap rates"** (VO2 max and Time to recover status, two keys): reads as a cut-off sentence.
9. **Ready hero line, 7 strings of 55 to 82 chars** in a slot the code describes as one 13 px line; they wrap and grow the hero
   (strength 71, intervals 70, zone 77, games 82, mind 55).
10. **DetailFigures labels over 12:** "7 night average" (15), "Active minutes" (14), "Asleep a night" (14), "Activity level" (14),
    "From workouts" (13), "7 day average" (13), and "Vs usual" with a capital V.

Also notable: `workout_ready_*` / `glow_ready_strap_disconnected` (84 chars) in a status line; Habit Age waiting (65 and 66 chars)
using 2 of 2 lines; Regularity "No sleep recorded in the last 7 nights" (38) on one line; Calories row "Add age, weight and height to see it".

Flag tally: about 62 strings or string families flagged: 11 cut or can be cut, 14 sentence in a word or short-phrase slot,
37 worded two or more ways for the same idea (counted per family, not per key).

## 12. Style patterns

Corpus: English strings in the prefixes of this area (`glow_recovery_`, `glow_sleep_`, `glow_activity_`, `glow_detail_`,
`glow_metrics_`, `glow_workout_`, `glow_summary_`, `glow_choose_`, `glow_ready_`, `glow_share_`, `core_tab_`, `core_detail_`,
`core_activity_`, `day_sleep_`, `day_info_`, `day_calories_`, `workout_*`, `core_data_` and a few more): 1,797 strings, 1,649 without the
info sheet, 1,572 of those 70 chars or fewer. Counts are by script, per string (plural forms counted once).

**Joining parts in one line**
- Comma join in short strings (70 chars or fewer, no stop): about 207. Examples: "Learning, 4 of 7 nights", "Overnight, 34 to 72", "Sleep score, good", "Heart rate 68, settling, down 9 since you started", "5.2 km, 5:24/km", "{times}, mostly zone 3, 132 bpm on average".
- Comma join that ends in a stop: about 79 ("Last night, within your normal.").
- Full-stop join in strings of 90 chars or fewer: about 70. Examples: "Active of 500. 800 total", "Active, goal met. Total 800" (both in
  `day_calories_*`), "No goal. Time and heart rate.", "No timer. Laps mark the rounds.", "Quiet strap and a dim screen. One soft buzz at the end.",
  "Target zone 2. The strap buzzes if you drift", "You are here. Your usual is 85 a day.", "Your age for now. Needs 30 days of wear...".
- Colon join: 32 strings, mostly in info text ("... not with other people: HRV (heart rate variability)..."); in short strings only 6 ("Dimmed: 18:02 to 18:10 came from...", "You are here: Good for your age.", "Needs your sex: the equation differs...").
- Same idea both ways: `workout_start_hiit_free_note` "No timer. Laps mark the rounds." vs `workout_start_preset_free_note` "No timer, laps mark the rounds";
  `day_calories_row_line_met` "Active, goal met. Total 800" mixes comma and stop in one string;
  `glow_activity_load_you_are_here` "You are here. ..." vs `glow_activity_vo2max_you_are_here` "You are here: ...".
- Observed rule of thumb: in rows and cards a comma separates parts of one fact ("Learning, 4 of 7 nights"); a full stop appears only when the second part is a different sentence (notes under goals, sheets, info). The exceptions above break it.

**Lines ending with a full stop**
- Whole set: 511 of 1,922 (all `core_today_`/`glow_today_` included) end with one.
- Strings of 25 chars or fewer with 2+ words: 39 of 944 (4%), so labels do not take stops.
- Strings over 25 chars: 467 of 693 (67%).
- Short strings (70 or fewer): 264 end with a stop, of which 229 are single sentences; 132 have 6+ words and no stop
  ("Add age, weight and height to see it", "Workouts you record show up here", "After a workout the strap rates",
  "Target zone 2. The strap buzzes if you drift"). So the 26 to 70 char middle has no rule. Same job, both ways: hint lines on live
  screens, Ready notes ("A buzz when the time is up" vs "The strap buzzes when rest is over, so the phone can stay on the bench."),
  empty states (`glow_metrics_none_in_range` "Nothing in this range yet." vs `glow_no_value` "Nothing for this day").
- Detail sentences always end with a stop; list/row status lines never do except `core_tab_usual_times` ("Usually asleep by 23:10, up at 06:50.").

**Naming the app and the strap**
- "unstrap": 15 strings in the area. 9 in info sheet text, 6 elsewhere: `core_today_no_score_learning`, `core_today_own_score_not_ready`,
  `workout_detail_where_phone`, `glow_workout_delete_phone`, `glow_workout_delete_strap`, `glow_workout_list_empty_text`. Info text mixes the actor: "Each morning unstrap compares..." vs "The strap calculates them itself".
- "the strap" / "The strap": 111 strings. "Your strap": 5. Bare "Strap" as a label: 26 ("Strap ready", "Strap not connected", "Strap 64%", "Strap's estimate", "Strap's productive range"). Also "Zepp" once ("From your Zepp profile").
- The strap as source in short slots ("From the strap", "The strap's estimate", "No score from the strap", "After the strap's next sync") against the same facts unnamed ("Estimated from your profile", "Score for the day").

**Numbers**
- "of": 53 strings ("4 of 7 nights", "Rep 3 of 6", "2 of 6 done", "Effort 3 of 5", "12:30 of 30 min", "5 of 7 nights"). "/": 3 only, all HIIT work and rest ("20 s / 10 s", "40 / 20"). Zero en or em dashes; ranges use "to" (23 strings: "22:58 to 06:56", "40 to 45", "Zone 2 to 3", "134 to 152 bpm"). "×" in sets ("5 × 10", "10 × 60 kg").
- Units: 127 strings put a space before the unit ("5.2 km", "128 bpm", "+0.3 °C", "7 h 40", "312 kcal", "45 s"); 0 without. Exceptions that read as units without a space: "%" ("62%", "96%"), pace "5:24/km" (`core_unit_pace`; the code comment says "5:24 /km"), "br/min", "ml/kg/min", "±8 m", "+5 min" not used.
- Times: phone clock, "22:58" or "10:58 PM". Durations: "7 h 40", "7 h 05" (minutes padded), "45 min", "45 s" (`Units.duration`); but nap value "1:30" (clockDuration), workout timer "12:34" and "1:02:03", `workout_start_hours_minutes` "1 h 30" (not padded), stage "1 h 32". Four duration formats.
- Dates: "Wed 21", "Wed 21 Oct", "Sun 18 Oct", "Sunday 22 Nov", "week of 21 Oct", "12 Nov" (`d MMM`): six shapes.
- Thousands: grouped "8,000", "2,620 kg", "+2,140 past goal" (`Units.count`), in Doto digits too.
- Ranges of days: "Last 7 days", "In the last 7 days", "last 7 days", "Average of the last 7 days", "7-day total", "7 day average", "7 night average"; hyphen present once.
- Estimated: "est." ("Resting 58 est."), "estimated" ("bpm resting, estimated", "For the day, estimated", "Estimated kcal"), "Estimated from your profile".
- Averages: "Avg" in 14 figure labels and rows ("Avg bpm", "Avg pace", "Avg speed", "141 avg bpm"), "Average" in lines ("Average 148 bpm", "Average heart rate", "Average asleep").
- Same idea, different words (count of families): learning (6), nothing here (8), night average (4), skin change (4), strap missing (4), Recharged/Filled (2), Holding back / Holding it back (2), Strap's estimate / The strap's estimate (2), stop/end (3), Above your normal / Above normal / Above usual (3), "normal" vs "usual" (two concepts chosen per metric: normal for body signals, usual for Load, steps, breathing disturbances, sleep times, workout drop).

**Capitalisation**
- Sentence case everywhere. Title Case only in 5 two-word strings, all feature names ("Habit Age", "About Recovery", "About Energy", "Edit Today").
- Feature names capitalised inside sentences: Recovery, Energy, Load, Live, Habit Age ("No Load in the last 7 days yet.", "Recovery is scored each morning"). Not consistent: "Heart points" and "Active minutes" keep the second word lowercase as titles, but the sentence "No Heart points in the last 7 days yet." capitalises Heart.
- Labels: "Vs usual" (capital V) against "vs normal", "vs yesterday", "vs last time: ".
- Initialisms in capitals: HRV, HIIT, PAI, REM, HYROX, VO2 max (lowercase "max"), TRIMP.
- Spelling: British ("Minimise", "Kilometres", "Metres", "millilitres") while sport names are US ("Soccer").
- Ellipsis: one `…` ("Loading…").

## 13. Not verified

- Widths are estimates from font size and padding; no device run. Check the five tightest on a 360 dp emulator: A6 (Recovery detail drivers), A7 ("No reading" figures), Breathing and Naps Smalls, Ready hero lines.
- `workout_glance_*`, widget, notification, Today and You tabs not read. `core_today_no_score_learning` (the example in the brief) lands in the Recovery hero and detail sentence slots, where it fits, so only the wording is flagged here.
- Strings I read through code paths only (Healthspan reasons `core_healthspan_*`, `Vo2maxCopy.reason`, `WeekStory`, `ActivityCopy.loadDay`) are sentences in sentence slots and were not individually measured.
