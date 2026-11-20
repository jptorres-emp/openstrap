# Wave 3: sports grouped by family

Boards: HaloChooseSport, HaloSwitchSport, HaloSearchSports, HaloMoreSports. Plan: plan.md section 4, first row.

## Grouping
`data/SportGroups.kt`, pure, no Android: `SportGroups.of(type)` and `SportGroups.sections(types)`.
- Sections follow `Workouts.family()`: Distance, Indoor cardio, Mind and body, Games.
- The boards merge Intervals into Strength: one section "Strength and intervals", HIIT last in it.
- Other (free training 5, 16, and the strap's Activity) has its own section, not Indoor cardio.
- Order of sections is the boards': Distance, Indoor cardio, Strength and intervals, Mind and body, Games, Other. Empty ones are left out.
- Order inside a section is the order given. Choose and Change sport give the offered sports as everyday first, then the rest A to Z, which reproduces the boards (Run, Walk, Cycling; Cross-training, Swim, Rowing, Elliptical, Boxing, Dance, Jump rope; and so on).

## Screens
- Choose: strap line, usual, search, Recent chips, then the six sections, each a 4 by 28 colour bar, title, note, count and four-wide tiles tinted by family. Unfolded: the sections fill the right column.
- Change sport (picker): Now card, search, Recent as chips (were tiles), sections. The sport you are on is tinted 30 percent with a white ring.
- Search page: before typing, every sport under its section as rows (A to Z inside). Typed: rows by relevance, each with its family under the name.
- More sports: the boards' list is now the sections themselves. Every sport is a tile, so the More sports row and page go, with their strings. Decision: the HaloChooseSport board shows all 26 in sections and no More row.

## Strings
Six titles, six notes (en and zh-rCN). The count reuses `glow_choose_sports_count`.

## Tests
`SportGroupsTest`: HIIT in Strength and intervals and last; Other is not Indoor cardio; section order and empty sections; every offered sport in exactly one section; Choose matches the board's lists.
