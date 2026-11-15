# Halo build: decisions made during waves 2 to 4

Written 2026-11-15, when the waves were finished without stopping for review. Each decision below was a call where a board, a rule and the code disagreed, or where a board was silent. Where the board won, it says so. Anything marked **Check** is worth a look on the phone.

## Rules applied everywhere

- Where a screen board and the HaloCharts list disagree, the screen board wins.
- **Check:** the detail pages show two white things, the picked day pill and the picked range segment, because every detail board draws both. This is the one exception to "one white thing per screen".
- The page card gap stays 12, as the tab boards draw it. Rewards pages space their sections themselves.
- Strings that the restyle left unused (about 85) are removed in every language, since lint fails on unused resources.

## Today and the tabs

- Stress is no longer in the default Today moments, since no board shows it. Edit Today can still add it.
- The Recovery Small, in every moment, is the score plus a 24-dot ring (progress to a goal, so dots are allowed).
- Breathing disturbances stays dotted with its band, as on HaloSleep, though HaloCharts doesn't list it.
- **Check:** the Load Small on WorkoutTime and After is lit violet from below. Its level is today's Load against the top of your usual band, clamped to 0.15 to 1, and 0.5 when there is no band. The board's 0.82 is only an example.
- There is one Energy ruler with a dot count: 20 on the Midday hero, 22 on the Evening Wide card, 16 on the Night Small and the Recovery card. Its ink is develop's Energy colour (#F66C28), not the board's #FF9A3C, since the plan keeps develop's metric colours.
- The day-peek labels and the All metrics button stay, though the boards leave them out, because removing them changes behaviour. The off-normal rim stays removed, since wave 1 dropped rims.
- The day calendar's picked day is dark with a white ring, so the sheet keeps one white thing.

## Details

- Week and Month on signal details are columns from your normal with the band behind them. The boards draw low-to-high capsules, but only a daily average is stored.
- The Info sheet keeps its scale bars and "Why it might be off" as a fourth section, so nothing is lost. "How it's calculated" became "How it's worked out" (board wording).
- Habit Age uses the detail frame and no longer repeats its card.
- The Zone and Max HR sheets now apply edits only on Save (settings rules), and Max HR has the board's stepper.
- Details have their own private chart and row parts (DetailPicture.kt, DetailSleepPictures.kt). The tab charts are unchanged.

## Starting a workout and Live

- Add a workout is a sheet with Cancel and Save (open call 2). The sport defaults to your most recent sport.
- Ready follows the boards: the goal is the hero's big number, and the plan card is inline (place toggle, kind chips, presets). Tapping the hero opens a sheet only to type a custom value. A "last time" row shows figures from the last workout of that sport. Rounds and kg lifted are left out because a finished workout doesn't store them.
- One white tap on Live: Pause is white unless the screen has its own next step (Set done, Start set N, Start rounds, Start 2nd half). Then that step is white and Pause dark. On the paused screen Resume is white.
- Hold to stop (open call 3) fills a white ring over the existing 2 s hold, and letting go early empties it. "Hold to end" is now "Hold to stop".
- The big moving-time block is gone. Total time sits under the sport name.
- On HIIT rest, "+10 s" is the side action (board) and "Skip rest" is a small dark button in the rest hero.
- **Check:** the workout page behind Summary, Share and Celebrations is now black with no top glow. Family colours and light angles follow the board tokens everywhere.
- Sports are grouped by family on Choose, Change sport and Search: Distance, Indoor cardio, Strength and intervals (HIIT last), Mind and body, Games, Other. The "More sports" row and page are gone, since the boards show all 26 sports in sections.

## After a workout and sharing

- Figure labels follow the boards. "Peak" is now "Peak bpm", and Strength's third figure is Avg bpm instead of Avg rest. Every summary hero carries the effort sentence.
- The HIIT per-round "down" row, the games' hard-effort counts and the mind and body start and end figures are gone, since the boards leave them out. The route elevation profile isn't drawn (no data for it).
- The hero no longer swells for a personal best. The haptic tick stays.
- **Check:** the TrimBar restyle also changes the sleep and nap trimmers, which share it.
- Share on the personal-best moment opens the workout's share screen.
- **Load range** (open call 4): a workout's usual is the median Load of your last 20 workouts (at least 3), and the edges are that usual minus and plus 25%, rounded ("usual 75 to 125"). The board's example is 20%, but 25% matches the Above and Below usual tag, so a Load inside the edges is never tagged off usual. The Activity card now reads "Your usual, 30 to 50" too.

## Rewards

- Every badge is one medal shape, a squircle with a ribbon notch. Bests are a 12-point rosette, and the weekly goal is a ring of solid arcs. Bests with no board colour (longest workout, most steps) are lime.
- All badges keeps develop's four groups. Weekly goal gets its own section (open call 5).
- The goal figure is capped at the goal ("4 of 4"), and the real count goes in the caption.
- The Goals sheet uses steppers. Steps move by 500, sleep by 15 min, calories by 10, active hours and workouts by 1. Tapping the number still lets you type it.
- The Celebrations link stays on the goal moment, and the "Last week" recap row stays. The boards leave both out, but each is the only way to reach its page.
- The (i) button on Goals and bests is left out, since there's no method sheet to open.
- Badge and best cells lose their squircle clip, because the 35% corners cut off the ends of long names.
- **Most rounds** counts the finished rounds the phone stores for an interval or HIIT workout (no database change). Strap workouts, and phone workouts from before rounds were stored, count 0. It is red (HIIT) with the stopwatch emblem, and sits after Longest workout.

## You, settings and the strap

- Profile is value rows that open picker sheets with Cancel and Save. The pinned white Save is gone. A header Save shows only while there are edits. Each sheet has a quiet Clear (or "Use the default for my age") where a blank used to mean the default.
- Picked alarm days are dark with a white ring, not the board's white fill, to keep one white thing.
- Picker sheets commit on Save, so call buzz previews play once on Save instead of on each pick.
- Minute wheels step by 5, or by 1 when an alarm's minute isn't a multiple of 5 (Zepp can set 06:42).
- An alert's switch is its "off". Turning it on picks the strictest preset.
- The strap away state has no battery bar, since the last known level isn't stored.
- The computer-guide link moved from the Strap page to a row on the Zepp sign-in screen.
- The "keep the strap on its charger" note shows once on the Strap page.

## Search on All metrics

- The search matches metric names and group titles, ignoring case and accents. Every word typed must appear in the name. Back clears the field first.

## Clean-up (wave 4)

- The design-rule tests cite their rule or board. One Glow × Glass test is gone. New tests cover:
  - pills
  - no corner literal outside GlowLayout
  - no glass or blur outside the tab bar
  - white switch tracks
  - destructive buttons
  - BigNumberText only
  - PageSheet only
  - no dots in detail pictures
- Four corner tokens replace 23 hand-written small radii:
  - InnerShape, a 16 dp squircle
  - ChipShape, 10 dp
  - BarShape, a pill
  - PipShape, 4 dp

  A few radii move by about 2 dp.
- Every big number goes through BigNumberText. The Fold tabletop keeps its old size as BigNumber.Tabletop.
- The TalkBack check was scripted. It found no unlabelled tappable on Today, the four tabs, You, Profile, Goals and bests, Strap, Ready or Choose. It wasn't a listen-through.
- At font scale 1.3 nothing overlaps. Long captions end in "...".
- The baseline profile was re-recorded on the emulator, with the Fold data copy.

## Left for you

- **The frame-time check on a mid-range phone isn't done.** The emulator's numbers were meaningless while builds ran, and I didn't install on your phone.
- **Nothing is pushed**, and the Glow × Glass branches are untouched (open call 6).
