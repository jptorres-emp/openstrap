# Halo direction: build plan

Written 2026-11-15. The design is the Halo canvas, https://claude.ai/artifact/NkCKFjNzH8k4CZNZ2hxdyT (version 38, 112 boards in 12 sections). Where a board and this plan disagree, the board wins, and the gap goes into "Open calls" below.

The rules the boards follow are approved and are part of this plan:

- `zepp-alt/mockups/workout-rules.md` (Ready, Live, Summary, Share)
- `zepp-alt/mockups/detail-rules.md` (detail pages)
- `zepp-alt/mockups/rewards-settings-rules.md` (moments, collections, settings pages, picker sheets)

Copy them into `docs/design/halo/` on the branch so the code and its rules travel together.

## 1. What the direction keeps and drops

| Keep | Drop |
|---|---|
| develop's hero card look, lit from one edge | Glow × Glass glass: see-through cards, white inner rims, top highlight lines, outer glow |
| Sun-path light (the hero's lit edge follows the time of day), with no light outside the card | Light spilling onto the page |
| develop's `MetricColors` (detail-rules.md lists them) | The Glow × Glass two-stop blends |
| The blurred tab bar | Any other blur |
| Funnel Display, Funnel Sans, and dotted numbers for the one big figure | Dotted charts outside the HaloCharts list |

New in this direction:

- **Squircle corners everywhere:** cards 36, tiles and panels 27, icon tiles at about 0.75 of their parent.
- **One white thing per screen:** the main tap. Everything else is dark.
- **Settings and pickers are calm:** black pages, grouped panels, seven row types, and sheets with Cancel and Save.
- **Dots only on the HaloCharts list:** counts and progress to a goal on cards. None on details or live screens.

## 2. Where to start the branch

Cut `design/halo` from develop, not from the Glow × Glass branches. The direction keeps develop's hero and colours, which both Glow × Glass branches replaced.

Two pieces of `design/glow-glass-v2` (worktree `openstrap-ggv2/`) are worth porting by hand, without their glass:

- the squircle-shape 5.4.0 dependency and its shape helpers
- the layout tokens in `ui/kit/Space.kt`, plus the list row structure in `ui/kit/Lists.kt`

Leave the rest: `Glass.kt`, blends and spill.

## 3. Screens by section

| Section | Boards | Notes |
|---|---|---|
| Getting started | 8 | Pairing hero is the white strap card, the same component as the Strap page |
| Today and its sheets | 10 | Seven moments, Edit Today, day calendar, Partial day |
| Tabs and their sheets | 6 | Recovery, Sleep, Activity, Sleep times, Add a workout, Delete sleep |
| Details | 13 | One frame for every detail (detail-rules.md section 1) |
| Starting a workout | 10 | One chooser screen, a Ready per family, change sport, search, save intervals |
| During a workout | 14 | Live per family, paused, reps, intervals, exercise sheet |
| After a workout | 12 | Summaries per family, all workouts, past workout, trim, share |
| Rewards | 11 | Moments, Goals and bests, badges, weekly recap, their sheets |
| You and settings | 17 | Settings pages and their picker sheets |
| Strap | 6 | Strap card states, firmware, app updates, Zepp account |

## 4. Restyle or new work

Checked against develop 3.0.7 on 2026-11-15 for 27 features the boards show. 24 already exist with their data and behaviour, so for them the work is only a restyle. These need more:

| Feature | On develop | New work |
|---|---|---|
| Sports grouped by family on Choose and Change sport | "All sports" plus an A to Z list (`ui/glow/ChooseScreen.kt:195,360`). `Workouts.family()` only picks colours | A grouping function in data, and the section layout on both screens |
| "Most rounds" personal best (Goals and bests board) | Not a kind in `data/Rewards.kt` | A new best kind, its calculation from HIIT and interval rounds, and copy in `presentation/RewardsCopy.kt` |
| Search on All metrics | A grouped index with no search (`ui/glow/AllMetricsScreen.kt`) | A search field and filtering over the groups. Small |

Two smaller differences:

- **Load on summaries:** the boards print "Load, usual 80 to 120". Develop compares Load with a single usual value and a band, and shows no range. Either show the band's edges, a small data change, or change the board to the words develop already uses.
- **Badge groups:** develop has four groups (Firsts, Milestones, Weekly goal, Something new). All badges draws three, with the weekly goal badges inside Milestones. The fix is to add a Weekly goal section to the board.

These three items and the Load range go in wave 3. Everything else is a restyle.

## 5. Waves

All five waves were built on 2026-11-15. The calls made along the way are in decisions.md.

The workflow is the one from earlier redesigns:

- Each step is built by a subagent in its own worktree branch (`design/halo-w<n>-<part>`).
- Branches merge into `design/halo`, and tests, detekt and lint run once after the merge.
- Run one Gradle build at a time, through `.claude/tools/gradle-queue.sh`.
- Check every screen against its board on the emulator first, then on the Fold. Back up the phone's database before installing, and never install during a workout.

### Wave 0: foundations (coordinator alone, then gate 0 with you)

- Squircle shapes and radius tokens (36, 27, the icon tile ratio).
- `hero(colour, angle)`: develop's hero lit from the sun-path angle, kept inside the card.
- The button set:
  - the white main tap
  - dark secondaries
  - the dark destructive pill with red text
  - the round icon buttons
- Type scale, and the one-big-number style for Doto (or `DotSpec`, see Open calls).

**Gate 0:** you look at a single test screen with the hero, a card, a row, a sheet and the buttons before anything else is built on them.

### Wave 1: shared parts (4 agents in parallel)

1. **Cards and heroes:** solid card, plain panel, lit hero, the white strap card with its status light.
2. **Charts:** the HaloCharts board. Solid by default, with dots only on the listed counts.
3. **Lists and settings:**
   - the seven row types
   - grouped panels and status banners
   - switches (white track on, never a sport colour)
   - picked chips (dark with a white ring)
4. **Headers and sheets:**
   - the 96 / 1fr / 96 header
   - the detail header with its metric wash
   - sheets: grabber, title, content, note, then Cancel and Save
   - the 0.62 dim behind a sheet

### Wave 2: screens (6 agents in parallel)

1. Today, the tabs and their sheets
2. Details
3. Starting a workout and Live
4. After a workout and sharing
5. Rewards
6. You, settings, Strap and pairing

Each agent keeps its screens' behaviour as it is and changes only the look, except where section 4 says the work is new.

### Wave 3: new behaviour (one PR each, after the restyle lands)

These are the features from section 4 that need more than UI. Each gets its own short plan, reviewed with you before it starts.

### Wave 4: clean-up (coordinator)

- Rewrite the design-rule tests to these rules, each test citing its board.
- Fix new detekt and lint findings.
- Do a TalkBack pass, a font scale 1.3 pass, and a frame-time check on a mid-range phone.
- Refresh the baseline profile.

## 6. Open calls

1. **Dotted numbers:** Doto 700 as on the boards, or keep `DotSpec`, which the widgets share. Glow × Glass kept `DotSpec`, so it can't drift from the widgets. The recommendation is the same here.
2. **Add a workout:** the board draws a page with a header Save. The settings rules list it as a sheet with Cancel and Save. The recommendation is the sheet.
3. **Hold to stop:** add a white ring that fills while held. It isn't drawn yet.
4. **Load on summaries:** show the usual band's edges ("usual 80 to 120"), or the single usual value develop has now. The recommendation is the band's edges, since the boards and the Load detail both read better with a range.
5. **Badge groups:** add develop's Weekly goal group to the All badges board. That is the recommendation, rather than folding it into Milestones in code.
6. **The Glow × Glass branches** (`design/glow-glass`, `design/glow-glass-v2` and their wave branches): keep them for reference or delete them after the port.
