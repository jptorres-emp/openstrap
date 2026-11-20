# Rewards rules and Settings rules (user approved 2026-11-15)

Two more families, each set from what its screens are for. They don't extend the workout or detail rules, but they
share the basics:
- 390 wide, squircles, no glass
- Funnel Display, Funnel Sans and Doto
- no dotted charts
- no ·, ; or em dash in visible text

---------------------------------------------------------------------------------------------------------------------

# A. Rewards: moments and collections

What they are for: telling you that you did something, then keeping it. There are three kinds of screen.

## A1. The token: one shape per kind of reward
- **Best (personal best):** a rosette, a 12-point scalloped circle. Its colour is the sport colour of what it measures:
  - Distance #C6F55A
  - Indoor #52ACFC
  - Strength #FF8410
  - HIIT #FF5A5F
  - Mind #B67AE8
  - Games #6E8FB8
- **Badge:** a medal, a squircle tile with a ribbon notch at the foot. Develop's groups are Firsts, Milestones and
  Something new. A badge takes the colour of what it counts: lime #C6F55A for counts, distance and goals, and the sport
  colour for a sport's first.
- **Weekly goal:** a ring of N solid arcs for N workouts, in Activity lime #C6F55A.
- **Earned:** filled with its colour (a radial from the top at 0.95 to 0.55), the icon in black, and a 1 px rim at 0.3.
- **Not yet earned:** a #121317 fill, a 1 px #24262C rim, the icon in #4A4E57, and a 4 px progress bar under it (its
  colour on #1C1D22). Never hidden or blurred.
- **Sizes:** 160 on a moment, 96 on a sheet, 72 in a grid.

## A2. Moment: full screen, once (best card, goal moment, history badges)
- **Page:** black, with a radial glow of the token's colour behind the token only, about 300 px across at 0.45, fading
  to nothing. This is the one place light spreads. It sits behind the token and never reaches the edges.
- **Top:** a 44 px Close on the right. Nothing else.
- **Body, centred:**
  1. the token at 160
  2. one Doto figure, 64 (the time, "4/4", the count)
  3. the name, Funnel Display 26/600
  4. one line of how it compares, 15 px in a tint of the colour ("1:12 faster than your last best")
  5. the date and what it came from, 13 px #9AA0AA
- **Goal moment:** also the seven days as solid day pills.
- **Bottom bar:** Share (dark, half) and Continue (white, half), 24 px from the bottom. Continue is the one white thing.
- **History badges** (several at once): the tokens in a grid of 3 at 72, the line "6 badges from your past workouts",
  and Continue.

## A3. Collection: pages that keep them (Goals and bests, All badges, Weekly recap)
- **Header:** the detail header (back, title, (i) when it has a method). No wash: rewards keep their colour in the tokens.
- **First block, "This week":** a solid card with:
  - the weekly goal ring at 72 on the left, with "3 of 4" in Doto 44 beside it
  - "workouts this week" under it
  - the weeks-in-a-row line
  This is the page's one Doto number.
- **Then sections**, each with a 15/600 title and a 12 px note on the right ("6 of 18"):
  - Bests, as a grid of 3 tokens at 72 with the name (12) and figure (13) under each
  - Next badge, as one solid row with its token at 56, the name, and how far along with a bar
  - Badges, as a grid of 3
- **Grids:** 3 to a row, 16 gap. Earned first, then not yet earned, by how close.
- **Weekly recap:**
  - the week card, then the days as 7 solid pills (worked out = lime, today ringed)
  - then the bests set that week (tokens)
  - then a figures row: Time, Distance, Active kcal
  - then "Next week's goal" as a settings value row (B3)
- **Ways on:** a plain panel of nav rows (B3) at the end: Weekly goal, Celebrations.

## A4. Item sheet: one token (badge sheet, best sheet)
- **Sheet:** a grabber, then:
  1. the token at 96, centred
  2. the group in 13 px #9AA0AA
  3. the name in Funnel Display 22
  4. how it's earned in 15 px
  5. the status: earned "Earned 9 Nov, Outdoor run", or a progress bar with "3 of 5"
- **Bottom:** a dark Close pill. A best sheet adds "What it measures".

---------------------------------------------------------------------------------------------------------------------

# B. Settings: lists of choices

What they are for: changing how the app and strap behave, rarely, calmly. The You page, the Strap page and everything
under them, plus the utility sheets (Edit Today, search, sleep times, Add a workout).

## B1. The page
- Plain black. No wash, no lit card, no Doto, no light.
- **Header:** 96 / 1fr / 96 grid, 48 high.
  - Left: back (44 squircle #17181C).
  - Middle: the title, Funnel Display 17/600.
  - Right: empty, or Save as a text pill when the page edits and has changes. That pill is the page's one white thing,
    otherwise dark and disabled.
- **Top status banner** (only when there's news: an update ready, a backup that failed, the strap away): one plain
  panel with a 4 px left bar in the status colour (good #3DDC97, warn #FFB547, problem #FF5A5F), a 15/600 line, a
  13 px note and one action pill on the right.

## B2. Groups
- A group is one panel: #0C0D10, radius 27, rows separated by 1 px #1C1D22.
- The group heading sits above the panel: 13 px #9AA0AA, 4 px in, 8 px gap.
- Any footnote sits under the panel: 12 px #6E7480, 4 px in.
- Groups are 24 apart. Destructive actions sit in their own group, last.

## B3. Row types, no others
1. **Nav row** opens a page:
   - a 32 px icon tile (#17181C, icon #C9CED6)
   - the name (15)
   - the current value under it (13 #9AA0AA)
   - a chevron
2. **Switch row:**
   - the name (15)
   - a note under it (13 #9AA0AA)
   - a switch: on is white with a black knob, off is #2A2C33 with a grey knob
3. **Value row** opens a picker sheet: the name (15) on the left, the value (15, white 0.8) and a small chevron on the
   right.
4. **Action row** does something now:
   - an icon in white and the label in white, or both in #FF5A5F for destructive
   - no chevron
   - while working, a spinner replaces the icon
5. **Info row** is read only: the name on the left, the value on the right in #9AA0AA, with no chevron.
6. **Item row** is a thing you own (a deleted night, an alarm): two lines (the name at 15, details at 13 #9AA0AA) and
   one action pill on the right (#17181C, 36 high), or a switch.
7. **Text panel** is for paragraphs (release notes): a plain panel with a 15/600 title and 14 px text, no rows.

**Picked state.** A chip or option that is picked but isn't the next tap (a Pin chip, a picked preset) is #34363E
with a 1.5 px white ring and white text. Only the next tap is filled white.

**Icon tiles are neutral.** Settings have no colour of their own. A sport's tile in search keeps its sport colour. Colour appears only where the value is itself
coloured: heart zone rows show their zone colour as a 10 px bar before the name, and the strap battery shows its level
bar.

## B4. Picker sheets (one value at a time)
- **Sheet:** grabber, title (Funnel Display 22), the picker, a 13 px note on what it changes, then Cancel (dark) and
  Save (white) half and half.
- **Pickers:**
  - numbers: a stepper with the value in Funnel Display Light 44 (not Doto), with − and + 44 squircles
  - one of a few: a segmented control (track #111215, picked white)
  - times: two wheels
  - one of many: radio rows
- Utility sheets (Edit Today, Sleep times, Add a workout) follow this too: grabber, title, content, Cancel and Save.

## B5. Search
- **Header:** back and a 48 px search field (#17181C, radius 16, search icon).
- **Results:** a count line ("4 sports"), then one panel of nav rows.
- **Before typing:** everything A to Z in panels under letter headings.
