# Component audit: one shared system for unstrap

Audit date 2026-11-17. Read only. Code at `openstrap-halo/app/src/main/kotlin/org/openstrap/app/ui/`, boards at
`scratchpad/canvas/project/*.dc.html` (117 files). Colour churn is ignored: where a fill is quoted it is only to name
the surface. All code paths below are relative to `ui/` and all line numbers are current as of this audit.

How the numbers were made:
- Code counts are a grep over every `.kt` file under `ui/` for literal `N.dp` values. Named constants
  (for example `RowPadding`) are counted once where they are declared, not at their call sites. So the true usage of 14 and 20
  is higher than the counts shown.
- Call-site counts are `Name(` hits outside the declaring line.
- Board numbers are a parse of every inline `style` in all 117 boards (padding, gap, border-radius, height, font-size).
- "Deliberate" means a real different need. "Drift" means the same job drawn two ways.

## Contents

1. The headline
2. Foundations: spacing, corners, sizes, type, greys, dividers
3. The kinds, one section each (3.1 to 3.27)
4. Proposed component list and the order to migrate
5. Code against boards: where they disagree

---------------------------------------------------------------------------------------------------------------------

## 1. The headline

- 60 composables in `PageParts.kt` and `GlowList.kt` and `DetailPicture.kt` do the job of about 14 components. The app has
  roughly 605 named composables under `ui/`, and at least 27 hand-built grey panels, 8 list-row families, 3 figure rows,
  4 title rows and 7 ways to draw a round icon button.
- The worst drift is the list row. `PageRow`, `ListRow`, `DetailRow`, `ContributorRow`, `DriverRow`, `ZoneRow`, `RadioRow`,
  `FoundRow`, `PlaceRow`, `WorkoutRow` and `PageItemRow` are all "icon tile, name, line under it, value, chevron", with
  three different tile sizes (32, 36, 38, 44), four different tile tints, three different hairline colours and four
  different side insets.
- The second worst is the grey panel. Panels get 12, 14, 16, 18, 19, 20 and 25.6 dp of padding depending on the file
  that drew them, and two different smoothings (65 for `CardShape` and `TileShape`, 55 for `CardTuning.solidShape`).
- Third is the round icon button: `RoundIconButton` (24 uses, default 38), `RoundIcon` in `ReadyScreen.kt:412`,
  `FlatButton` in `live/LiveFrame.kt:525`, the 60 dp `SideAction` in the same file, `MapButton`, `TrimButton`, and
  `CardButton` in `device/DeviceScreen.kt:591`.
- Spacing is not on a scale. Literal values in use: 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20, 22, 24, 28. The three
  most common (8, 4, 6) are fine, but 10 and 14 (together 220 uses) sit between steps and 18 and 20 (115 uses) are
  neighbours of 16 and 24.
- Sheets are already one implementation (`PageSheet`, 30 call sites over a single `GlassSheet`). Switches are one
  implementation. Segmented controls are one. Those three are the model for the rest.

---------------------------------------------------------------------------------------------------------------------

## 2. Foundations

### 2.1 Spacing in use

Counts of literal dp in code (all contexts, then `padding(...)` arguments only, then `spacedBy` and `Spacer` only).

| dp | all | in padding() | in spacedBy | in Spacer width/height | notes |
|---|---|---|---|---|---|
| 1 | 54 | 2 | 2 | 0 | hairlines and borders |
| 2 | 78 | 13 | 12 | 1 | line gap under a row name (`RowLineGap`), bar rules |
| 3 | 54 | 4 | 5 | 0 | list row name to status gap (`GlowList.kt:174`), figure label gap |
| 4 | 169 | 82 | 22 | 4 | in from a section label, tiny nudges |
| 5 | 18 | 2 | 0 | 0 | nudges |
| 6 | 145 | 38 | 45 | 13 | chip gaps, card sub-gaps |
| 7 | 11 | 1 | 2 | 0 | status dot size |
| 8 | 183 | 75 | 41 | 19 | the workhorse |
| 9 | 7 | 2 | 0 | 0 | NewsPill, GlowSegments vertical |
| 10 | 124 | 22 | 49 | 19 | the most common `spacedBy` |
| 12 | 134 | 39 | 37 | 30 | icon to text, row gaps |
| 14 | 96 | 28 | 24 | 7 | `RowPadding`, `TitleGap`, `QuietPanel` block gap |
| 16 | 118 | 52 | 12 | 8 | panel row inset, sheet side, many buttons |
| 18 | 73 | 17 | 3 | 7 | button side, list tile side, `ReadyRows` side |
| 19 | 3 | 2 | 0 | 0 | `StatusBanner` side, `SleepGoalSheet` card, `StartPadding` start |
| 20 | 42 | 11 | 0 | 1 | `CardInset`, `Gutter`, sheet side |
| 22 | 14 | 2 | 0 | 2 | button side (`Main`), `OutlineCard` section |
| 24 | 35 | 5 | 3 | 5 | group gap, bottom pad |
| 28 | 21 | 3 | 0 | 1 | sheet bottom, nav bar side |

Where the same number means a different job (this is why a plain scale will not do on its own):
- 16 is `PanelRowInset` (`PageParts.kt:234`), `PageRow` end-minus-two, the page gutter on the boards and in `GlowGrid.Gutter`
  (`GlowGrid.kt:34`), the sheet block gap (`PageSheet`, `PageParts.kt:699`), the grid gap of tokens (`RewardParts.kt:126`)
  and `InnerShape`'s radius.
- 20 is `GlowLayout.Gutter` (`GlowLayout.kt:58`) and `GlowLayout.CardInset` (`:107`) and the default sheet side
  (`PageParts.kt:693`) and `OutlineCard` padding (`GlowQuiet.kt:52`).
- 14 is `RowPadding` (`GlowLayout.kt:110`), `TitleGap` (`:131`), the `QuietPanel` block gap (`GlowQuiet.kt:31`), `WorkoutPage`
  block gap (`GlowWorkoutMode.kt:196`) and `StartWorkoutStrip` vertical.

Screen-level spacing in code against boards:

| Job | Code | Boards (390 wide) | Verdict |
|---|---|---|---|
| Side gutter on a tab | `GlowLayout.dashboardGutter`: 20 on a phone 400 dp or wider, 16 under 400 (`GlowLayout.kt:150`) | 16 on all 117 boards (`padding: 16px 16px 40px`) | Drift on any phone 400 dp or wider, including the Fold cover |
| Side gutter on a page over a tab | `ScreenList` default 16 (`dashboard/ScreenParts.kt:35`), `WORKOUT_GUTTER` 16 (`GlowWorkoutMode.kt:147`) | 16 | Matches |
| Gap between cards on a tab | `ScreenList` `spacedBy(12)` (`ScreenParts.kt:53`), `GlowGrid.Gap` 12 | 12 | Matches |
| Gap between blocks on a workout page | `WorkoutPage` `spacedBy(14)` | 12 (workout rules: "gap between blocks 12") | Drift, 2 dp |
| Gap between blocks on a settings page | `PageGroup` groups are placed by each screen, 24 in the screens read | 24 | Matches where checked |
| Gap between blocks on a detail page | per file | 16 (HaloDetailHeart: `gap: 16px`) | Not checked in every file |
| Gap in a sheet | `PageSheet` 16 (`PageParts.kt:699`) | 16 (HaloAlarmSheet) | Matches |
| Top inset under the status bar | `ScreenList` `top = 4`, `WorkoutPage` `top = 8` | board page padding top 16 including the 48 header | Not comparable |

### 2.2 Spacing on boards

Gap values across all boards (count): 12 (544), 6 (450), 10 (403), 8 (376), 4 (323), 2 (266), 3 (165), 16 (149), 14 (116),
24 (57), 1 (37), 7 (28), 18 (17), 20 (4).

Padding values on boards (count of each px in any padding): 4 (387), 16 (359), 26 (225), 12 (207), 10 (182), 9 (156),
25 (114), 8 (65), 40 (64), 14 (62), 6 (50), 19 (44), 20 (43), 2 (42), 22 (39), 24 (31), 28 (27), 18 (24).

The 22, 24, 25, 26 values are not free choices. The HaloSystem board derives a card's padding from its corner:
`padding = 16 + 0.15 * corner`, corner `= min(shorter side * 0.36, 64)`. A 64 corner gives 25.6, a 62 gives 25.3, a 41 gives
22.2. The code has the same formula (`CardTuning.paddingFor`, `CardTuning.kt:55`, `cornerPadding` at `:84`) but only some
panels use it.

### 2.3 Spacing scale (adopted, 17 Nov 2026)

Eight steps plus one derived value, in `ui/theme/Scale.kt`. The designer added 20 to the seven proposed.

| Token | dp | Replaced | Use |
|---|---|---|---|
| `Space.S2` | 2 | 1 (not hairlines), 2, 3 | line gap under a name |
| `Space.S4` | 4 | 4, 5 | in from a section label, bar to bar, label padding |
| `Space.S8` | 8 | 6, 7, 8, 9, 10 (side by side) | chip gap, icon to caption, group heading to panel |
| `Space.S12` | 12 | 10 (stacked), 11, 12, 13, 14 | icon to text, button pair gap, row gap, block gap on tabs and workouts |
| `Space.S16` | 16 | 14 (button sides), 15, 16, 17, 18 | page gutter, row inset, sheet block gap, detail block gap |
| `Space.S20` | 20 | 19, 20, 21, 22 | sheet side, `CardInset` |
| `Space.S24` | 24 | 22 (stacked), 23, 24, 25, 26, 28 (sides) | settings group gap, hero bottom |
| `Space.S32` | 32 | 28 (stacked), 30 | sheet bottom and page foot only |
| `Inset.card(corner)` | derived, 16 + 15% of corner, 22 to 26 | | the padding of every card, panel and list (and the side inset of its rows) |

How the old values were moved: 10 became 8 beside a neighbour and 12 above or below one, 14 became 12 (16 for the side of a
button), 18 became 16 (20 for a vertical gap), 22 became 20 (24 for a vertical gap), 28 and 30 became 32 for a vertical gap
and 24 for a side. 1 dp stays for hairlines and borders and is not a step. `SpaceLiteralTest` fails on a `.dp` literal inside
`padding`, `PaddingValues`, `spacedBy` or a `Spacer` anywhere under `ui/` but the theme, the drawing files and its short
`measured` list (alignments under a label column or clear of a map's chrome).

Decisions that went with it:
- `RowPadding` (14) is 12. The rest of the row (min height 56, inset from `Inset.card`) comes with the `Row` step.
- `GlowLayout.Gutter` is 16 and `dashboardGutter` gives 16 at every phone width (the `WideGutter` split is gone).
- `WorkoutPage` and `QuietPanel` block gaps are 12, `TitleGap` is 12.

Corner and size tokens live next to it: `Corner` (`Card` points at the card rule, `Sheet` 32 top only, `Inner` 16, `Chip` 10,
`Pip` 4, `Pill`) and `IconSize` (`TileS` 32, `TileM` 44, `TileL` 64, `Live` 60, `Pause` 88, glyphs 14 to 28).

### 2.4 Corners

Code (`GlowLayout.kt`, `CardTuning.kt`) and one-offs.

| Name or value | dp / rule | Where | Notes |
|---|---|---|---|
| `CardShape` / `TileShape` | 36% of shorter side, cap 64, smoothing 65 (`GlowLayout.kt:70`, `HaloSquircle` `:591`) | 20 + 29 call sites | Same object under two names |
| `CardTuning.solidShape` | 36% of shorter side, cap 64, smoothing 55 (`CardTuning.kt:34`) | `solidCard()` swaps `CardShape` for this (`CardLight.kt:35`), also `QuietPanel` press, `PictureCard`, `LogStepper` | Drift: smoothing 55 against 65 for the same visual job |
| `litShape` | same as solid (`CardTuning.kt:45`) | `GlowCard`, `LitPanel` | |
| `CardCorner` | 36 fixed | widgets, `DetailHero` bottom corners (`GlowMetricDetail.kt:428`), `GlowCard.cardShape` mapping | Only for what has no size to measure |
| `TileCorner` | 27 fixed | `GlowCard.cardShape` mapping only | Legacy: boards of 2026-11-15 used `r * 0.75` |
| `SheetCorner` | 56 fixed top corners (`GlowLayout.kt:82`) | `GlassSheet` | Board sheets draw 32 top corners (HaloAlarmSheet: `border-radius: 32px 32px 0 0`) |
| `InnerShape` | 16 (`:94`) | menu, maps, goal tile | |
| `MapShape` | 18 (`GlowWorkoutSummary.kt:300`) | route map | Drift from `InnerShape` by 2 |
| `PanelShape` in `TrimBar.kt` | 20 (`TrimBar.kt:52`) | trim panel | One-off |
| `ChipShape` | 10 (`:96`) | title tap area, tile press shape, `TapValue` | |
| `PipShape` | 4 (`:101`) | zone pip, colour swatches | |
| `BarShape` / `CircleShape` | pill | bars, buttons, chips, 141 raw `CircleShape` | |
| `IconCircle` | circle | row icon tiles, round buttons | |
| `IconShape` | squircle at the card rule | `DayChip`, `DayPills`, `DayCell`, `SummaryButtons`? no, see below | 9 uses, a squircle where the rest of the app uses a circle |
| Canvas `CornerRadius` | 1, 2, 3, 4, 5, 6, 12 dp | charts, bars | 7 at 4, 6 at 3, 4 at 2 |

Boards: percent-based. Panels draw 64 (tall lists, heroes), 62 (200 tall tiles), 59 and 58, 41 and 39 (two-row panels), 20 to 22
(single-row buttons). Buttons draw r = height / 2 (64 gives 32, 56 gives 28, 44 gives 22, 36 gives 18). Sheet top 32.
Chips 18. Segments and range pills 18. Square-ish: day chip 15 and 20, ready pill tile 14.

Proposed corner set (5 plus the rule):

| Token | Value | Replaces |
|---|---|---|
| `corner.card` | rule: 36% of shorter side, cap 64, smoothing 65 | `CardShape`, `TileShape`, `solidShape`, `litShape`, `CardCorner` (36), `TileCorner` (27) |
| `corner.sheet` | 32 top only | `SheetCorner` 56 |
| `corner.inner` | 16 | `InnerShape`, `MapCorner` 18, `PanelCorner` 20, day chips 15, goal tile |
| `corner.chip` | 10 | `ChipShape` |
| `corner.pip` | 4 | `PipShape`, canvas 3 and 5 and 6 where a block (not a bar) is drawn |
| `corner.pill` | full | `CircleShape`, `BarShape`, `IconCircle` |

Use `corner.pill` for every icon tile, icon button and day chip so that "only cards are squircles" (the 16 Nov 2026 rule in
`GlowLayout.kt:88`) is true in code. That retires `IconShape` and its 9 squircle uses.

### 2.5 Icon, tile and button sizes in use

Icon glyphs (the `Icon(... Modifier.size(N))` argument): 18 (13), 20 (7), 16 (7), 17 (5), 14 (5), 12 (5), 24 (4 plus 2 more in
sport tiles), 22, 26, 28.
Tiles and circles that hold an icon:

| Size | Where | Icon inside | Fill |
|---|---|---|---|
| 22 | `RadioRow` ring (`SettingsSheets.kt:174`) | 8 dot | white or 1.5 line |
| 26 | `OutdoorBadge` (`ChooseScreen.kt:283`) | 12 | Raised in a black ring |
| 32 | `PageRow` tile (`PageParts.kt:314`), `DetailRow` (`DetailPicture.kt:196`), `FoundRow` (`ChooseScreen.kt:516`), `InfoButton` (`GlowLayout.kt:392`), `ChevronCircle` (`:442`) | 17, 17, 17, 16, 14 | Control neutral, 16% of ink, 16% of ink, Control, Raised |
| 36 | `ListRow` tile (`GlowList.kt:219`), strap pill height | 20 | 14% of ink |
| 38 | `EffortTile` (`GlowActivityScreen.kt:635`), `RoundIconButton` default | 20 | Raised with glow, Control |
| 40 | `StatusBanner` circle (`PageParts.kt:1111`), `GoalStepper` buttons, `StartWorkoutStrip` play | 20, 18, 16 | 16% of kind, Control, Raised |
| 44 | `BackButtonSize` (`PageParts.kt:106`), `SportIconTile` (`ChooseScreen.kt:246`), `PlaceRow` tile, `RoundIcon`, `DayPills` height, `DayChip` height | 18, 24, 24, 18 | Control, 16%, 15%, Raised |
| 52 | `CardButton` (`DeviceScreen.kt:596`), stepper buttons on Ready (`ReadyCardio.kt:412`) | 18 or 20 | Control or white |
| 56 | Primary `PageButton` `Page` size | 20 | |
| 60 | live `SideAction` (`LiveFrame.kt:337`) | 22 | Control |
| 64 | `SportTile` (`ChooseScreen.kt:328`), Share (`GlowWorkoutSummary.kt:392`), `Main` button | 24, 20 | Control, 14% of family, Control |
| 88 | live Pause `FlatButton` (`LiveFrame.kt:297`) | 28 | white or Control |

Board sizes for the same jobs: 32 (330 of the 32-high elements are icon tiles at 50%), 44 (back, (i), steppers, 96 + 25 elements),
36, 40, 52, 60 (live side, 26 elements), 64 (Share, summary).

Proposed set, 5 sizes for round things that hold an icon:

| Token | dp | Glyph | Use |
|---|---|---|---|
| `icon.tile.s` | 32 | 16 | row tile in every list, chevron circle, (i) on a card |
| `icon.tile.m` | 44 | 20 | header buttons, back, (i), stepper buttons, sport tile in a row, steppers |
| `icon.tile.l` | 64 | 24 | sport tile in a grid, Share, medal at grid size is a medal, not this |
| `icon.btn.xl` | 88 | 28 | live Pause only |
| `icon.btn.live` | 60 | 22 | live side actions |

Glyph sizes: 14 (caret), 16, 20, 24, 28. The rest (17, 18, 22, 26) fold to the nearest.
The 36, 38, 40 and 52 tiles go: 36 and 38 to 32 or 44 (`ListRow` takes 32, `EffortLight` takes 44 to keep its glow, banner takes 44),
40 to 44, 52 to 44.

### 2.6 Type roles in use

Code roles (`GlowType.kt`, all Funnel): `pageTitle` 17/22 Display 600, `sheetTitle` 22/26 Display 600, `momentTitle` 26/30, `verdict`
24/26.4, `headerDate` 20/24, `cardTitle` 15/20 Sans 600, `heroTitle` 16/20 Sans 600, `sentence` 21, `detailSentence` 19,
`body` 15/20, `secondary` 13/18, `noteSmall` 12/15.6, `footnote` 12/17.4, `caption` 11/14 tnum, `button` 16/20 Sans 600,
`buttonSmall` 14/18 Sans 600, `rowValue` 15/20 tnum, `figure` 28 Display 300, `figureLarge` 40, `figureInline` 20, wheel 24, 32, 48,
`goalValue` 44, `goalValueMinor` 26. Doto: `BigNumber` 36 to 120.

Role drift that touches components:
- `heroTitle` (16) is used in only 3 places (`DeviceScreen.kt:456`, `SummarySections.kt:258`, `live/LiveHero.kt:68`). The Today and Recovery
  heroes draw `cardTitle` (15) through `CardTitle`. Boards draw hero titles at 16 (HaloTodayLoading hero: `font-size: 16px; font-weight: 600`).
- `buttonSmall.copy(fontWeight = Normal)` is rebuilt by hand at four sites (`GlowWorkoutScreen.kt:411`, `:459`, `SummarySections.kt:546` and `:550`)
  to get a 14 regular row value. That is a missing role (`rowValueSmall`).
- `GlowType.secondary.copy(fontSize = 11.5.sp)` in `ContributorRow` (`TabParts.kt:127`) and `.copy(fontSize = 12.sp)` in `WorkoutDayHeader`
  (`GlowActivityScreen.kt:631`) are literal sizes outside the role list.
- `GlowType.body.copy(lineHeight = 22.5.sp)` in `InfoSheet.kt:46`.
- Boards: 15 (967 uses), 12 (876), 13 (622), 14 (414), 11 (273), 28 (199), 16 (136), 17 (111). 14 and 16 and 17 are the button sizes
  (14 for Compact and Row, 16 for Page, 17 for Main), which the code collapses to 14 and 16 (`ButtonSize` style table
  at `PageParts.kt:572`).

### 2.7 Grey ladder in use (`theme/Surfaces.kt`)

`Page` 000, `Card` 141414, `Raised` 1E1E1E, `Control` 282828, `Picked` 323232, plus off-ladder `Line` 3D3D3D, `SplitBar` 303030.
Usage by job: Card for every grey panel, banner, text panel, row panel. Raised for divider hairlines inside panels
(`topHairline(divider, Greys.Raised)`), chevron circles, sheets (`GlassSheet` colour), tile under effort glow, `ShareBar`
track. Control for buttons, pills, switch off track, field fill, segmented track, icon tiles in settings. Picked for the picked
chip, segment and day.

Divider colour is the clearest ladder drift: `Greys.Raised` on `PageRow`, `PageSwitchRow`, `PageActionRow`, `PageItemRow`,
`PageMenuRow`, `DetailRow`, `ZoneRow`, `RadioRow`, `CardDivider`. `Ink.Faint` (`RowHairline`, `GlowList.kt:48`) on `ListRow`,
`ContributorRow`, `DriverRow`, `WorkoutRow`, `FoundRow`. `Greys.Control` on the vertical figure rule. Boards: `#1D1D1D` (a
step above the card) on settings and detail rows, `#24262C` on figure rules.

Grey on a surface (17 Nov 2026, step 6): `LocalSurface` (`theme/Surfaces.kt`) is the grey a part sits on: the page by default, Card inside
a grey card, a lit card and a sheet (a sheet's fill is Card), Raised inside a panel on a sheet. `OnSurface` gives each part its grey one step
above it, never below: `panel` (a grey card), `line` (dividers, figure rules, progress tracks, at least Raised), `track` (a segmented control,
at least Control), `control` (chips, neutral tiles, at least Control) and `picked`. `HaloCard` and `GlassSheet` set it.

### 2.8 Dividers and hairlines

| Implementation | file:line | Colour | Drawn as |
|---|---|---|---|
| `Modifier.topHairline` | `PageParts.kt:1130` | `RowHairline` default, `Greys.Raised` from rows | `drawBehind` line, 1 dp, edge to edge of the row |
| `ListRow` own draw | `GlowList.kt:156` | `RowHairline` | `drawBehind` inset by the bleed |
| `CardDivider` | `SummarySections.kt:350` | `Greys.Raised` | a 1 dp Box |
| `RadioRow` divider | `SettingsSheets.kt:164` | `Greys.Raised` | a 1 dp Box above the row |
| `FoundRow` divider | `ChooseScreen.kt:511` | `RowHairline` | a 1 dp Box above the row |
| `figureRule` | `GlowMetricDetail.kt:581` | `Greys.Control` | vertical line |
| `StatRule` | `live/LiveFrame.kt:488` | `Greys.Control` | vertical Box |
| `SummaryFigures` rule | `SummarySections.kt:317` | `Greys.Control` | vertical drawBehind |

Proposal: one `Divider(horizontal, vertical)` and one `Modifier.rowDivider()`, colour `Greys.Raised`, thickness 1, used by every row
and every figure rule. `RowHairline` and `GlowLayout.DividerAlpha` (unused) go.

---------------------------------------------------------------------------------------------------------------------

## 3. The kinds

Format for each: implementations in code, looks on boards, differences, proposal.

### 3.1 Page frame (top bar, tab header, gutters, tab bar)

Code:

| Implementation | file:line | Used by | Measurements |
|---|---|---|---|
| `PageHeader` | `PageParts.kt:172` | 16 pages and details (You, Strap, Profile, Alarms, rewards, details via `DetailNav`) | 48 high, 96 / 1fr / 96 columns, back 44 (`BackButtonSize`) with an 18 icon, title `pageTitle` 17, optional subtitle `secondary`, caret 12 with 6 gap, `buttonSurface` choice of `Greys.Control` or `OverWash` |
| `BackButton` | `PageParts.kt:119` | standalone back where no header | `RoundIconButton` 44 / 18 |
| `DetailNav` | `GlowMetricDetail.kt:462` | every detail | `PageHeader` with an (i) `RoundIconButton` 44 / 18 on `OverWash` |
| `MomentHeader` + `CentredRow` | `dashboard/MomentHeader.kt:59`, `:118` | the four tabs | 60 high, `RoundIconButton` default 38 with 20 glyph (You), `StrapPill` (36), day switcher centred with peeks |
| `ReadyTopBar` | `ReadyScreen.kt:355` | Ready | own layout: `RoundIcon` 44, sport pill, strap lines |
| `LiveTopBar`, `LiveBottomBar` | `live/LiveFrame.kt:366`, `:405` | Live | own layouts |
| Summary top | `GlowWorkoutSummary.kt:90` area, `TopPillText` | Summary | own layout: back, centred title, Trim pill |
| `ScreenList` | `dashboard/ScreenParts.kt:31` | every tab and tab-like page | side = gutter (default 16), top 4, bottom = tab bar + 24, gap 12 |
| `GlowScreenList` | `GlowLayout.kt:261` | tabs | picks gutter 16 or 20, two columns at 600 |
| `WorkoutPage` | `GlowWorkoutMode.kt:175` | Ready, Choose, summaries | side 16, top 8 under insets, gap 14, pinned buttons with fade |
| `FloatingNavBar` / `FloatingNavRail` | `components/Insights.kt:67`, `:114` | root | horizontal 28 vertical 10, max width 440, pill pad 6, cell v 8, cell gap 2, black fade |
| `PageSheet` content column | `PageParts.kt:689` | every sheet | see 3.17 |

Boards: header grid `96px minmax(0,1fr) 96px`, height 48, back 44 circle (96 boards use `#181818`, 25 draw `rgba(0,0,0,.35)` over a wash),
title Funnel Display 17 / 600, centred. Tab header row 60 (HaloToday* boards, `CentredRow` matches). Page padding `16 16 40`.
Tab bar: `border-radius: 33px`, height 66, padding `0 6px` (18 boards). Workout bars: Ready pill 40, Live 3 columns 72 / 1fr / 72, bottom bar
left 16 right 16 bottom 24 with a 120 px fade.

Differences:

| Difference | Verdict |
|---|---|
| Header height 48 on pages, 60 on tabs | Deliberate (tab header carries the strap pill and peeks) |
| Back 44 on `PageHeader`, 38 default on tab You button, 44 on `RoundIcon` | Drift: the You button should be 44 (boards: 44 in HaloToday*) |
| Gutter 16 (pages, boards), 20 (tab on a 400+ dp phone) | Drift |
| Block gap 12 (tab), 14 (`WorkoutPage`), 16 (detail), 24 (settings) | Tab 12, detail 16, settings 24 deliberate (one per family on boards). `WorkoutPage` 14 is drift |
| Three bespoke top bars (Ready, Live, Summary) | Deliberate content, but all three should be `PageHeader` slots (start, centre, end) with the same 48 high and 96 / 1fr / 96 grid. Boards say so (workout rules, section 1) |

Proposal: `PageFrame(header, content, bottomBar, gutter)` with three header variants and one list container.
- `PageHeader(start, centre, end)` slots, height 48, side columns 96, gap none, gutter 16. Variants `Page` (back, title, trailing), `Detail`
  (back and (i) over a wash), `Workout` (close or back, pill or sport name, text lines). Replaces `PageHeader`, `BackButton`,
  `DetailNav`, `ReadyTopBar`, `LiveTopBar`, the summary top row.
- `ScreenList(gutter = space.16, gap = family gap)` with `gap` = 12 tab, 16 detail, 24 settings, 12 workout. Replaces `ScreenList`,
  `WorkoutPage`'s column, `GlowScreenList`'s gutter picker.
- `TabBar` stays as it is (one implementation).


Built (17 Nov 2026, step 5), `glow/HaloPage.kt`:

- `HaloPageList(contentPadding, variant, state, gutter)` is the scrolling page with `PageSpec` (gutter 16, top 4, bottom 24 over the
  tab bar) and `PageVariant` Tab 12, Detail 16, Settings 24, Workout 12 between blocks. Settings pages (You, Strap, Profile, Alarms,
  Updates, Backup, Health Connect, Deleted sleep, Firmware, Phone alerts, Heart zones) take Settings, the metric detail and the
  rewards pages Detail, Choose's lists Workout, the rest Tab. `GlowScreenList` keeps its two columns and passes only the wide gutter.
- `HaloPageColumn(contentPadding, variant, scroll, pinned)` is the non-lazy frame of Ready, Choose, Live, Share and the summaries,
  with the pinned buttons and their fade.
- `HaloPageHeader(start, centre, end)` is the 48 high 96 / 1fr / 96 grid, and `HaloPageHeader(title, onBack, ...)` the page and
  detail header on it. `HeaderVariant` is `Page`, `Detail` (buttons over the wash) and `Workout` (side columns may grow). Ready's and
  Live's top bars are slot calls of it, the detail's (i) header is a `Detail` call. The tab header (`MomentHeader`) takes its 60 from
  `PageSpec.TabHeaderHeight` and is otherwise as it was.
- Gone: `ScreenList`, `WorkoutPage`, `WORKOUT_GUTTER`, `PageHeader`, `PageHeaderHeight`, `BackButtonSize`, `DetailNav`. Rule test:
  `HaloCardTest`.

### 3.2 Hero cards (lit)

Code:

| Implementation | file:line | Used by | Measurements |
|---|---|---|---|
| `GlowCard` | `GlowCard.kt:99` | 19 call sites: Today heroes (`GlowTodayScreen.kt:488` to `:760`), Recovery (`GlowRecoveryScreen.kt:198`, `:256`, `:351`), Sleep (`GlowSleepScreen.kt:146`), Load and Charge (`TodayCards.kt:831`, `:923`), Activity (`GlowActivityScreen.kt:292`), `SummaryHeroCard` (`SummarySections.kt:254`), Live (`live/LiveHero.kt:59`), `WorkoutCapsule.kt:172`, strap card (`DeviceScreen.kt:453`), tabletop | padding `cornerPadding` (16 + 15% of the card's own corner, so 22 to 26), corner 36% of shorter side cap 64 |
| `LitPanel` | `CardLight.kt:63` | 0 call sites (dead) | 20 / 18 fixed padding, 1 dp rim at 30% |
| `effortGlow` style | `CardLight.kt:88` | summary heroes | style only |
| `SummaryHeroCard` | `SummarySections.kt:248` | 6 summaries | wraps `GlowCard` |
| `LiveHero` | `live/LiveHero.kt:58` | live screens | wraps `GlowCard`, title `heroTitle` 16 |
| `GlowTodayScreen` `Hero` and sub-heroes | `GlowTodayScreen.kt:417` to `:757` | Today moments | wrap `GlowCard` |

Boards: `border-radius: 64px`, padding 26, inner gap 14 (21 boards), 12 (6), 16 (6), 10 (6), `hero(colour, angle)` radial background plus an
inset rim at 30%. Title row 16 / 600 with a 32 chevron circle at 25% of the colour. Sentence Display 300 20 to 21. 19 boards in the
hero group.

Differences:

| Difference | Verdict |
|---|---|
| Padding 22 to 26 via `cornerPadding` against board 26 | Match |
| Title 15 (`CardTitle`) on Today and Recovery heroes, 16 (`heroTitle`) on Live, Summary, strap card | Drift |
| Inner gap 10, 12, 14, 16 on boards | Drift on the boards: pick 12 |
| `LitPanel` unused | Delete |

Proposal: `Card(tone = Lit(colour, angle) | LitLevel(colour, level) | Grey | Outline, size = Hero | Wide | Small | Strip | Free, onClick)`.
One shape rule, one padding rule (`inset.card`), one title slot (see 3.4). Replaces `GlowCard`, `PictureCard`, `QuietPanel`, `LitPanel`,
`OutlineCard`, `SummaryHeroCard`, `LiveHero`'s wrapper. `GlowCard`'s rendering stays inside the `Lit` tones.


Built (17 Nov 2026, step 5), `glow/HaloCard.kt`:

- `HaloCard(modifier, tone, size, fill, onClick, title, verticalArrangement, horizontalAlignment, swell, content)` with `CardSpec`
  (corner 36% capped at 64, smoothing 65, gap 12, Small 200, Wide 160, Strip 64, outline 1.5, inset from the corner).
  `CardTone` is `Lit(style)` (a hero, its title 16), `LitLevel(style)`, `Grey`, `Outline`, and `CardTone.lit(style)` picks between
  the lit two. The light is drawn by `LitSurface` (the old `GlowCard` body, unchanged, only `HaloCard` calls it).
- One smoothing: `CardTuning` has one corner and one smoothing (65, `GlowLayout.SMOOTHING`) for every card, `GlowLayout.CardShape`
  and `Corner.Card` are `CardTuning.shape`. `GlowLayout.CORNER_PERCENT` is 36 (it was 35 there and 36 in `CardTuning`).
- Gone: `GlowCard`, `PictureCard`, `QuietPanel`, `LitPanel`, `OutlineCard`, `SummaryHeroCard`'s own frame (it is a `HaloCard`).

### 3.3 Tiles and small cards (grey and lit), wide cards, strips, grey panels

Code, cards on tabs:

| Implementation | file:line | Used by | Measurements |
|---|---|---|---|
| `PictureCard` | `PictureCard.kt:49` | 35 call sites | solid card, `cornerPadding`, `solidShape` (smoothing 55) |
| `SmallCard` | `TodayCards.kt:1064` | Today, Recovery, Sleep, Activity | 200 high (`GlowGrid.SmallHeight`), number `GreySmall` Doto 36 grey or `Tile` 44 lit |
| `ChargeTall`, `ChargeSmall`, `ChargeWideCard`, `LoadCard`, `RecoverCard`, `HeartCard`, `ActivityCard`, `HeartDayCard`, `StressDayCard`, `RecoveryEarlierCard`, `LastNightCard`, `WeekCard`, `WorkoutCard`, `NightHero`, `BreathingCard`, `NapsCard`, `RegularityCard`, `SkinTempCard` | `TodayCards.kt`, `GlowRecoveryScreen.kt`, `GlowSleepScreen.kt` | tabs | each wraps `PictureCard` or `GlowCard` with its own picture |
| `TodayStrip` | `TodayCards.kt:166` | `LastNightStrip`, `CheckInStrip`, `StartWorkoutStrip`, `AlertsStrip` | 68 high (`StripHeight`), padding 20 / 14 (`StripPadding`, `TodayCards.kt:158`), `StartPadding` 19 / 12 / 12 / 12 |
| `StripLine` | `TodayCards.kt:176` | strips | min 40, `TitleRow` |
| Grid | `GlowGrid.kt:34` | all tabs | gutter 16, gap 12, Small 200, Tall 280, Wide 160, Strip 68, Large 332 |

Code, grey panels that are not `PictureCard` or `QuietPanel` (hand built `clip(shape).background(Greys.Card).padding(...)`), 27 sites:

| Where | file:line | Shape | Padding |
|---|---|---|---|
| `PageRows` | `PageParts.kt:227` | `CardShape` (not `solidShape`) | none, rows carry 16 |
| `PageTextPanel` | `PageParts.kt:1074` | `CardShape` | 16 |
| `StatusBanner` | `PageParts.kt:1107` | `CardShape` | 19 h, 16 v |
| `QuietPanel` | `GlowQuiet.kt:26` | `solidCard` (smoothing 55) | `cornerPadding`, blocks 14 apart |
| `OutlineCard` | `GlowQuiet.kt:48` | `CardShape`, 1.5 dp `Ink.Line` | 20 |
| `ListPanel` | `GlowList.kt:55` | `solidCard` | side 25.6 (`CardTuning.listInset`), v 11.6 |
| `DetailPanel` | `DetailPicture.kt:155` | `TileShape` | none, rows carry 12 / 14 |
| `ReadyRows` | `ReadyPlans.kt:218` | `TileShape` | 18 h |
| `ReadyWhere` | `ReadyCardio.kt:115` | `TileShape` | 18 h |
| `ReadyPlanCard` | `ReadyCardio.kt:330` | `solidCard` | `CardInset` 20 |
| `InYourDayCard` | `GlowWorkoutScreen.kt:400` | `TileShape` | 20 h, 18 v |
| `WorkoutLoadCard` | `LoadUsual.kt:81` | `TileShape` | own |
| `NowCard` | `ChooseScreen.kt:255` | `TileShape` | 16 h, 14 v |
| `WeekCard` (goals) | `GoalsAndBests.kt:73` | `solidCard` | 20 |
| `NextBadgeCard` | `BadgeParts.kt:77` | `solidCard` | 16 h, 14 v |
| `SleepDetail` edited note | `SleepDetail.kt:404` | `solidCard(TileShape)` | 14 h, 12 v |
| Summary solid card | `GlowWorkoutSummary.kt:263` | `solidCard` | `CardPadding` 20 |
| Summary hero beats (drop) | `GlowWorkoutSummary.kt:262` | `GlowCard` | |
| `LogStepper` | `live/LiveSets.kt:423` | `solidShape` | 14 |
| Strap `InstallPanel`, `PairPanel`, updates, sleep goal | `DeviceScreen.kt:691`, `:730`, `UpdatesPage.kt:125`, `SleepGoalSheet.kt:72` | `CardShape` or `TileShape` | 12, 16 / 18, 16, 19 |
| Live map bar | `live/LiveMap.kt:161` | `TileShape` | 16 h |
| Share panel | `ShareScreen.kt:288` | `TileShape` | rows |
| Switcher sheet panel | `GlowMetricDetail.kt:515` | `TileShape` | rows |
| `ReadyCardio` place track | `ReadyCardio.kt:151` | pill | 4 |

Boards: grey card `#0A0A0A` (at the time of this audit) in these forms:
- Tile: 200 high, `r 62`, `p 25` (33 boards). A wide tile 160.
- Panel with rows: `r 64`, `p 0 26` (14 boards, tall) or `r 41`, `p 0 22` (18 boards, two or three rows) or `r 62`, `p 0 25` (8 boards).
- Panel with free content: `r 64`, `p 26`, gap 14 (21 boards), gap 16 (23), gap 12 (6), gap 10 (5).
- Strip: `r 58`, `p 25`, gap 12 or `r 54`, `p 24` (10 boards), 64 high (HaloToday* start strip: `height 64`, `r 23`, `p 0 12px 0 19px`).

Differences:

| Difference | Verdict |
|---|---|
| Padding 12, 14, 16, 18, 19, 20, 25.6 on grey panels | Drift. Boards derive it from the corner (22 to 26) |
| Shape: `CardShape` smoothing 65 against `solidShape` smoothing 55 | Drift |
| Panel clipped by rows (rows carry the side inset: `PageRows`, `DetailPanel`) against panel carries the padding (`QuietPanel`, `ListPanel`) | The pattern is deliberate (a pressed row lights edge to edge) but it is done three ways: `LocalRowInset` 16 in `PageRows`, `LocalRowBleed` 25.6 in `ListPanel`, nothing in `DetailPanel` where `DetailRow` carries 12 and 14 |
| Strip 68 high (code) against 64 (HaloToday* start strip, board) | Drift, 4 dp |
| Block gap inside a panel 14 (`QuietPanel`), 12, 10 (`OfferBox`, `LoadCard`), 8, 6 | Drift: one rule `space.12` |
| Chart-carrying tile (Small, Wide), content-sized panel, row panel, strip | Deliberate: four sizes (Small 200, Wide 160, Strip 64, Free) |
| `OutlineCard` outline 1.5 dp | Deliberate (hold or gone state), used once (`SleepDetail.kt:350`) |

Proposal: `Card` (as 3.2) with `size` variants Small, Wide, Strip, Free, and a single `inset.card`. A row panel is `Card(Free) { Rows }` where
`Rows` carries the same `inset.card` side inset as a bleed so rows light edge to edge. Replaces the 27 hand-built panels, `PictureCard`,
`QuietPanel`, `ListPanel`'s outer, `PageRows`' outer, `PagePanel`, `PageTextPanel`, `OutlineCard`, `DetailPanel`'s outer.


Built (17 Nov 2026, step 5), the rest of `HaloCard`:

- `HaloCardSize` Small, Wide, Strip, Free are least heights (a card grows when its words need it). A Small or Wide lays out its
  own picture (`Arrangement.Top`), a Free card puts its blocks 12 apart, a strip centres its words between `CardSpec.StripInset`
  sides (the inset of a 64 high card, 19.5) with 12 above and below. `GlowGrid` takes its heights and gap from `CardSpec`.
- `CardFill` is `Inset` (the card rule), `Rows` (no padding, rows carry `Inset.list` through `LocalRowInset`, a title gets the
  same sides and the inset above it) or `None`.
- Folded in: `PagePanel`, `PageTextPanel`, `PageRows`, the frames of `ListPanel` and `DetailPanel` (both now a `HaloTitle` and a
  `HaloCard` of rows), `PageGroup`'s panel, `TodayStrip` (a Strip card), the hand-built grey panels (updates notes, sleep need,
  diagnostics log, pair panel, In your day, workout load, Ready rows, Ready where, plan card, the live split on Today, goals week,
  the summary's drop card, the edited night note, the detail switcher, the share route panel, the live map's last split, the log
  stepper), `UsualRow` and `NowCard` on Choose (a card of one `HaloRow` each), `NextBadgeCard`. `solidCard` and `ListSurface` are gone.
- Kept apart: the log stepper keeps a 12 padding inside a `CardFill.None` card (two sit side by side in half a sheet, and the
  card inset leaves the 44 buttons no room). The nap list on the sleep detail stays lazy items with a hairline: each nap has a
  chart, figures and actions, and a card each would put the same card back to back.

### 3.4 Card title row (title, note, chevron circle, (i))

Code:

| Implementation | file:line | Used by | Measurements |
|---|---|---|---|
| `GlowHeader` | `GlowLayout.kt:502` | 17 | `CardTitle` (`cardTitle`, 2 lines max), note `NoteText` (`secondary`), marker amber caption or `OutlineTag`, `ChevronCircle` 32 gap 8 |
| `CardTitle` | `GlowLayout.kt:399` | 17 | own chevron circle with 10 gap when no row places it |
| `TitleRow` | `GlowLayout.kt:450` | 5 | note drops under title (2 gap) when both do not fit, `NoteGap` 12 |
| `ChevronCircle` / `CardCircle` | `GlowLayout.kt:424`, `:428` | 3 / 2 | 32, glyph 14, fill `Greys.Raised` or card colour at 25% |
| `InfoButton` | `GlowLayout.kt:373` | 3 | 32 `RoundIconButton`, glyph 16, or a `CardCircle` on lit cards |
| `EdgeTitle` | `TodayCards.kt:297` | 12 | wraps `TitleRow` with marker, tag, action |
| `CardHead` | `SummarySections.kt:336` | 14 | `Row` space-between, title `cardTitle`, note `noteSmall` (12, not 13), action gap 10 |
| `ListPanel` title | `GlowList.kt:67` | 10 | `TitleRow` top 14 bottom 6, note `secondary` or a tappable note with a caret (`ListNote`, 4 v, 6 gap) |
| `DetailPanel` title, `DetailHeading` | `DetailPicture.kt:132`, `:613` | 11, 5 | outside the panel, 4 in, `cardTitle`, note `noteSmall`, bottom-aligned |
| `SectionHead` | `RewardParts.kt:43` | rewards | outside the panel, 4 in, note `footnote` |
| `InYourDayCard` header | `GlowWorkoutScreen.kt:405` | 1 | own row, caret 14 `Ink.Tertiary` |
| `HeroTitle` | `live/LiveHero.kt:66` | live | `heroTitle` 16 |
| `SectionLabel` | `PageParts.kt:870` | 5 plus `PageGroup` | outside panel, 4 in, `secondary`, grey |
| `SectionHeader` (sport groups) | `ChooseScreen.kt:458` | 2 | 4 by 28 colour bar, `pageTitle`, note `noteSmall`, count `noteSmall` footnote colour |
| `WorkoutDayHeader` | `GlowActivityScreen.kt:628` | 3 | `secondary` 12 literal, `Ink.Tertiary`, top 12 bottom 2 |

Boards: in-card title row 15 / 600 (16 on a hero) with a 32 chevron circle at the right (`#0D0D0D` on grey, 25% of colour on lit),
gap 10. Out-of-card heading 13 `#9F9F9F` padding `0 4px` gap 8 above a panel (settings), or 15 / 600 with a 12 note at the right,
`padding 4px 4px 0` (details and rewards). Section headers on Choose draw a colour bar.

Differences:

| Difference | Verdict |
|---|---|
| In-card title (15, inside) against out-of-card heading (13 grey or 15 white, 4 in) | Deliberate: settings groups use quiet grey 13, content sections use 15 white. Two variants |
| Note style 13 (`secondary`), 12 (`noteSmall`), 12 (`footnote`) | Drift, one role: `noteSmall` 12 |
| Chevron: circle 32 on cards, plain 14 chevron in rows, `TapCaret` Tertiary after a note, `TapCaret` Disabled at row ends | Circle versus plain is deliberate (the Halo System board rule). Tertiary against Disabled is drift |
| Four title-row implementations that all do "title left, note right, drop under when it does not fit" | Drift |
| `ListPanel` title row top 14 bottom 6 on a panel that already pads | Drift: part of `inset.card` |
| Day header 12 literal | Drift |

Proposal: `Title(text, note, mark, trailing, variant)`.
- Variants: `InCard` (15 or 16 on hero, chevron circle slot at end), `OutOfCard` (15 white, 4 in), `Group` (13 grey, 4 in, 8 above panel).
- Slots: `text`, `note` (drops under at 2 when no room), `mark` (state marker or outline tag), `trailing` (info button, action, chevron).
- Replaces `GlowHeader`, `CardTitle`, `TitleRow`, `EdgeTitle`, `CardHead`, `ListPanel` title, `DetailPanel` title, `DetailHeading`,
  `SectionHead`, `SectionLabel`, `WorkoutDayHeader`, `StripLine`.
- Kept apart: `ChevronCircle` / `InfoButton` as a `CircleButton(icon, size = 32)`.


Built (17 Nov 2026, step 5), `glow/HaloTitle.kt`:

- `HaloTitle(text, modifier, variant, note, onNote, marker, tag, trailing)` with `TitleSpec` (15, 16 on a hero, note 13, 12 from
  the note, drop 2, 4 in out of a card). `TitleVariant` is `InCard`, `OutOfCard` (white 15 over a card), `Group` (grey 13 over a
  settings group). The note drops under the text when they don't share a line. In a card that opens something the chevron circle
  ends the row. A hero (`CardTone.Lit`) sets `LocalCardHero`, so its title is 16 with no call saying so.
- Gone: `GlowHeader`, `CardTitle`, `TitleRow`, `EdgeTitle`, `CardHead` (its 12 note is 13 now), `DetailHeading`, `SectionHead`,
  `SectionLabel`, `StripLine`, `WorkoutDayHeader` (a `Group` title in its rows, 13 not the 12 literal), the `ListPanel` and
  `DetailPanel` title rows.

### 3.5 Lists and rows (the big one)

Code, every row implementation:

| Row | file:line | Call sites | Frame | Tile | Gaps | Divider | Value style |
|---|---|---|---|---|---|---|---|
| `PageRow` in a panel | `PageParts.kt:248` | 60 | min 56, v 10, start 16 (12 with tile), end 14 | 32 circle `Greys.Control`, glyph 17 | tile to text 12, name to value 10, name to line 2, caret 8 | `Greys.Raised` | `rowValue` 15 white 80% (picked) or `noteColor` (info) |
| `PageRow` outside a panel | same | (inside the 60) | v 14, sides 0 | same | same | same | same |
| `PageInfoRow` | `PageParts.kt:1035` | 13 | `PageRow` with no click | | | | grey |
| `PageSwitchRow` | `PageParts.kt:494` | 23 | as `PageRow` | same | 12 before the switch (50 by 30) | `Greys.Raised` | switch |
| `PageMenuRow` | `PageParts.kt:425` | 1 | as `PageRow`, caret down | none | 10, 8 | `Greys.Raised` | `secondary` 13 (not `rowValue`) |
| `PageActionRow` | `PageParts.kt:986` | 19 | min 56, v 0, sides 16 and 16 | glyph 18, no tile | 12 | `Greys.Raised` | none, `body` Medium |
| `PageItemRow` | `PageParts.kt:1042` | 2 | min 64, v 10, start 16, end 12 | none | 12 | `Greys.Raised` | name `body`, details `secondary`, action pill 36 |
| `ListRow` | `GlowList.kt:136` | 14 | v 14, sides = bleed 25.6 | 36 circle at 14% of ink, glyph 20 (or ring 20) | 12 tile, 3 line, 12 value, 8 caret | `Ink.Faint` | `rowValue` 15, status `secondary` 13 |
| `DetailRow` | `DetailPicture.kt:166` | 17 | min 56 (60 with status), v 0 (10 with status), start 12, end 14 | 32 circle at 16% of ink, glyph 17 (ring 32 / 5) | 12, 2 line, 12 value, 8 caret | `Greys.Raised` | `rowValue` `Ink.Strong`, status `noteSmall` 12 |
| `ScoreRow` family (`ScoreRow`, `ActivityScoreRow`, `SleepScoreRow`, `RecoveryScoreRow`, `RelatedMetricRow`) | `GlowMetricDetail.kt:1136` to `:1211` | 9 plus 2 | wrappers on `DetailRow` | | | | |
| `ContributorRow` | `TabParts.kt:105` | 1 (and used for charts) | v 14 | none | name to detail 2, to bar 8, bar to word 12, caret 8 | `Ink.Faint` | bar 8 high, word `secondary`, caret `Ink.Disabled` |
| `DriverRow` | `GlowRecoveryScreen.kt:311` | 1 | v 14, a copy of `ContributorRow` with a `NewsPill` | | | | |
| `ZoneRow` | `HeartZonesScreen.kt:113` | 1 | min 56, h 16 | pip 10 `PipShape` | 10, 2, chevron | `Greys.Raised` | `rowValue` |
| `RadioRow` | `device/SettingsSheets.kt:161` | 1 | min 56, h 16 v 10 | ring 22 (1.5 line, 8 dot) | 12 | `Greys.Raised` | none |
| `FoundRow` | `ChooseScreen.kt:509` | 1 | min 56, start 12, end 14, v 10 | 32 circle at 16% of family, glyph 17 | 12, 2 | `RowHairline` | none, chevron |
| `WorkoutRow` | `GlowActivityScreen.kt:647` | 2 | v 12 plus 10 bottom for the zone bar | 38 `EffortLight` glow | 12 tile, 3 line, 12, 8, 6 around tag | `RowHairline` | `rowValue`, zone bar 4 under |
| `SuggestionRow` | `GlowActivityScreen.kt:717` | 1 | `ListRow` plus a 20-in action row | | 8 | | |
| `PlaceRow` | `ReadyCardio.kt:191` | 2 | own | 44 at 15% of tint, glyph 24 | 14, 3, 12 | none | |
| `UsualRow` | `ChooseScreen.kt:210` | 1 | `PictureCard` with 18 / 14 / 14 / 14 | 44 at 16%, glyph 24 | 14, 12 | | Start pill |
| `NowCard` | `ChooseScreen.kt:253` | 1 | 16 h 14 v | same tile | 14, 2 | | |
| `ReadyLastRow` | `ReadyGoalHero.kt:119` | 2 | figures | | | | |
| `FactRow` | `GlowWorkoutScreen.kt:449` | 1 | height 32 | none | 12 | none | `buttonSmall` regular 14 |
| `SplitLine` | `SummarySections.kt:524` | 2 | height 30 | dot 8 | 10, name 58 wide, time 44 wide | none | 14 tnum, bold on fastest |
| `GoalStepper` | `device/GoalsSheet.kt:177` | 5 | start 16, end 12, v 10 | buttons 40, number 104 by 40 | 10, 6 | `topHairline` default `Ink.Faint` | `goalValueMinor` 26 |
| `Suggestion` | `device/GoalsSheet.kt:156` | 1 | note plus `Row` pill | | 12 | | |
| `KeepConnectedRow` | `device/DeviceScreen.kt:402` | 1 | own | | | | |
| `TodayRow` | `GlowTodayScreen.kt:778` | 1 | own | | | | |
| `NextBadgeCard` | `BadgeParts.kt:71` | 1 | a card with a 56 medal | | 14, 6 | | |
| `ZonesBlock` row | `SummarySections.kt:575` | 2 | own | pip | | | |

Boards (row looks):
- Settings row: `min-height: 56px`, padding `10px 0` or `0`, gap 10 (value rows), 12 (switch rows, rows with a tile), inside a panel whose own
  side padding is 22 or 26 (so the row's content sits 22 to 26 in, not 16). Divider `1px solid #1D1D1D` as `border-top`.
- Icon tile: 32 circle (about 50% of all 32-high elements), tinted 16% of ink on details and metric lists, neutral `#181818` on settings,
  25% on the sport tile of Ready.
- Detail row: `height: 56px`, `padding: 0 26px`, gap 12, tile 32 / 16%.
- Item rows: 52 min, gap 12 (HeartZones, Firmware, PairFound: `min-height 52`).
- Workout and sport lists: 44 tile (`SportIconTile`).
- Figures: see 3.8.

Differences (a selected set, every one of them in the same family of "icon, name, line, value, chevron"):

| Property | PageRow | ListRow | DetailRow | Contributor / Driver | ZoneRow / RadioRow / FoundRow | Verdict |
|---|---|---|---|---|---|---|
| Min height | 56 | none (v 14 around 20 to 40 content) | 56 or 60 | none | 56 | Settings, detail, zone, radio, found agree on 56. `ListRow` is about 56 to 72 by content. Pick 56 min everywhere, with 64 for two-line rows |
| Vertical padding | 10 | 14 | 0 or 10 | 14 | 10 or 0 | Drift: one rule, 12 |
| Side inset | 16 (12 before tile), end 14 | 25.6 | 12, end 14 | 25.6 | 16 or 12 / 14 | Drift. Boards: the panel's `inset.card` (22 to 26) |
| Tile size | 32 | 36 | 32 | none | 32 or pip 10 | Drift: 32 |
| Tile fill | neutral `Control` | 14% of ink | 16% of ink | none | 16% or none | Deliberate in kind (settings are neutral, metrics are coloured), drift in the alpha (14 against 16 against 15) |
| Glyph | 17 | 20 | 17 | none | 17 | Drift: 16 in a 32 tile |
| Name to second line | 2 | 3 | 2 | 2 and 8 | 2 | Drift: 2 |
| Second line style | `secondary` 13 | `secondary` 13 | `noteSmall` 12 | `secondary` or `Ink.Tertiary` 11.5 | | Drift: 13 on rows with a name at 15 |
| Value gap | 10 (beside), 2 (below) | 12 | 12 | 12 | 10 | Drift: 12 beside |
| Caret gap | 8 | 8 | 8 | 8 | 2 (ZoneRow) | Drift in ZoneRow |
| Divider colour | `Greys.Raised` | `Ink.Faint` | `Greys.Raised` | `Ink.Faint` | mixed | Drift |
| Caret colour | `Ink.Tertiary` (`ChevronInk`) | `Ink.Tertiary` | `Ink.Tertiary` | `Ink.Disabled` | Tertiary | Drift in Contributor, Driver, WorkoutRow |
| Value colour | white 80% / grey | white | white 80% | grey word | white 80% | Deliberate (picked value against fact) |
| Zone strip under | none | none | none | bar | none | `WorkoutRow` zone bar: deliberate extra slot |
| Right-hand slot | value, switch, caret | value, caret | value, caret | word or tag, caret | value, ring, chevron | Deliberate: slot, not a different row |

Proposal: one `Row` with slots, all measurements from the scale.

`Row(lead, title, line, value, trail, below, onClick, divider)`:
- Frame: min height 56 (64 when `line` or `below` is set), vertical padding 12, side inset `inset.card` (the panel gives it, a row outside a panel
  gets 0), gap between slots 12.
- `lead`: none, `Tile(32 circle, glyph 16, tone = Neutral | Ink(16%) | Ring(share))`, `Pip(10)`, `Medal(56)`, `SportTile(44)`.
- `title`: `body` 15, one line.
- `line`: `secondary` 13 grey, up to 2 lines, or a `Tag` in the ink (the row tag). Gap to title 2.
- `value`: `rowValue` 15, white 80% when it opens a picker, grey when read only, waiting dash when null.
- `trail`: none, `Chevron(14, Tertiary)`, `Switch`, `Check`, `Radio(22)`, `Action(pill 36)`, `Stepper`.
- `below`: a full-width slot under the title text, aligned with it (zone strip, contributor bar, action buttons).
- `divider`: 1 `Greys.Raised` along the top, inset to the panel's side inset.

Named variants (rows are the same component with a slot set):

| Variant | Slots |
|---|---|
| `Nav` | lead tile, title, line or value, chevron (settings and detail related list, 56 or 64) |
| `Value` | title, value, chevron (opens a picker) |
| `Switch` | lead optional, title, line, switch |
| `Action` | lead glyph (no tile), title, none (red when destructive) |
| `Info` | title, value (grey), no trail |
| `Item` | title, line, action pill or switch (min 64) |
| `Metric` | lead tile in ink, title, status or tag, value, chevron (replaces `ListRow`, `DetailRow`, `RelatedMetricRow`, `ScoreRow` wrappers) |
| `Contributor` | title, `below` bar, word or tag, chevron (replaces `ContributorRow`, `DriverRow`) |
| `Zone` | lead pip, title, value, chevron (replaces `ZoneRow`) |
| `Choice` | title, radio trail (replaces `RadioRow`) |
| `Workout` | lead 44 effort tile, title with tag, line, value, `below` zone bar (replaces `WorkoutRow`, `SuggestionRow` with an action `below`) |
| `Fact` | title, value, height 32 (replaces `FactRow`) |
| `Split` | title, bar, value, dot (replaces `SplitLine`) |
| `Stepper` | title, stepper trail (replaces `GoalStepper` row frame) |

Built (17 Nov 2026, step 2): `HaloRow` in `ui/glow/HaloRow.kt`, with `RowSpec` (every measurement, from the scale), `RowLead`
(`tile`, `ring`, `pip`, `glyph`, `sport`, `custom`, `medal`) and `RowTrail` (`Chevron`, `Down`, `Switch`, `Check`, `Radio`, `Action`).
The side inset comes from `LocalRowInset`, which `ListPanel`, `PageRows` and `DetailPanel` all provide as `Inset.list`, so rows in You,
Strap settings, details and Activity sit the same distance from the card edge. Rows span the card edge to edge (a tap or a selection
lights the whole width) and the divider is inset to the content. Non-row content in a list pads itself with `Modifier.rowSides()`.

The named rows are slot combinations of `HaloRow`, not other layouts:

| Helper | Replaces | Slots |
|---|---|---|
| `NavRow` | `PageRow` (nav, value and info rows) | lead tile, title, line or value, chevron |
| `InfoRow` | `PageInfoRow` | title, grey value |
| `SwitchRow` | `PageSwitchRow` | lead optional, title, line, switch |
| `ActionRow` | `PageActionRow` | lead glyph, title |
| `ItemRow` | `PageItemRow` | title, line, action pill or switch |
| `PageMenuRow` (kept, its dropdown) | `PageMenuRow` | title, value, down caret |
| `MetricRow` | `ListRow`, `DetailRow` (`ScoreRow` and `RelatedMetricRow` stay as data wrappers on it) | lead tile or ring, title, line or tag, value, chevron |
| `ContributorRow` | `ContributorRow`, `DriverRow` | title, line, `below` bar, chevron |
| `ZoneRow` | `ZoneRow` | lead pip, title, value, chevron |
| `ChoiceRow` | `RadioRow` | title, radio |
| `FoundRow` (private, ChooseScreen) | `FoundRow` | lead tile, title, line, chevron |
| `WorkoutRow` | `WorkoutRow` | lead 44 effort tile, title with tag, line, value, `below` zone bar, chevron |
| `SuggestionRow` (private) | `SuggestionRow` | lead tile, title, line, `below` buttons |
| `PlaceRow` (private) | `PlaceRow` | lead 44 sport tile, title, line, trail |
| `FactRow` | `FactRow` | compact, title, value |
| `SplitLine` (private) | `SplitLine` | compact, fixed title, bar in `middle`, value, dot |
| `GoalStepper` (private) | its row frame | title, stepper trail, `below` note |

Also moved onto it: the Habit Age terms rows (`TermsPanel`), which sat 16 in on a panel whose rows sit 26 in.

Gone: `PageRow`, `PageInfoRow`, `PageSwitchRow`, `PageActionRow`, `PageItemRow`, `ListRow`, `DetailRow`, `DriverRow`, `RadioRow`, `RowIcon`
tile code, `rowFrame`, `PanelRowInset`, `LocalRowBleed` and its `bleed` layout. A row with a `line` or a `below` is 64 tall, any other 56,
a compact row 32. Value colour stays white for metric rows, white at 80% for a picked value and grey for a read-only one.

Not folded in: `UsualRow` and `NowCard` (ChooseScreen, cards with their own frame and a Start pill), `ReadyLastRow` (figures),
`KeepConnectedRow` and `TodayRow` (own layouts), `NextBadgeCard` (a card), the `ZonesBlock` row, the switcher sheet's rows
(`SwitcherSheet`) and a few hand-built rows inside cards (the sleep nap list, the editable day rows). They are cards or sheets, not list rows.

### 3.6 Section headers and footnotes

Covered in 3.4 for headings. Footnotes:

| Implementation | file:line | Style |
|---|---|---|
| `PageFootnote` | `PageParts.kt:947` | `footnote` 12 / 17.4, `Ink.Tertiary`, 4 in |
| `PageGroup` footnote | `PageParts.kt:968` | same, 8 under the panel |
| `PageNote` | `PageParts.kt:941` | `secondary` 13 grey, no inset (51 uses) |
| `RowNote` | `GoalsSheet.kt:122` | own |
| `CaveatLine` | `TabParts.kt:144` | dot 6 plus `secondary` `Ink.Strong`, top 10 |
| `GoalNote` | `ReadyCardio.kt:396` | own |
| `TrimNote`, `EditedNote` | `GlowWorkoutMode.kt:462`, `SleepDetail.kt:402` | own |
| `Caption`, `CaptionRow` | `SleepDetail.kt:107`, `SummarySections.kt:359` | `caption` 11 |

Boards: footnote `12px line-height 1.45 #747474 padding 0 4px`, section gap 8. Note under a picker 13 `#9F9F9F`.

Differences: `PageNote` (13 grey, no side inset) against `PageFootnote` (12 tertiary, 4 in): deliberate (an in-flow note against a group footnote).
`GoalNote`, `RowNote`, `TrimNote` are `PageNote` copies: drift.

Proposal: `Note(text, variant = Inline | Footnote | Caveat(dot))`. Replaces the 8 above.

Built (17 Nov 2026, step 6), `glow/HaloNote.kt`:
- `HaloNote(text, variant = Inline | Footnote | Caveat, color, dot, icon, textAlign)`, measurements in `NoteSpec`. Inline is 13 `noteColor`
  with no inset, Footnote 12 `Ink.Tertiary` 4 in (8 under a group), Caveat a 6 dot then 13 `Ink.Strong`, 12 under what it qualifies.
- Gone: `PageNote`, `PageFootnote`, `GoalNote` (a centred Inline), `RowNote` (a Footnote), `CaveatLine`, `EditedNote` (a grey card with an
  Inline note led by the pencil), the sleep detail's private `Caption`. `PageGroup` writes its footnote with `HaloNote`.
- Kept: `TrimNote` (its words are a `HaloNote`, but it carries the Undo button), `CaptionRow` (axis labels under a chart, not a note).

### 3.7 Buttons

Code, text buttons:

| Implementation | file:line | Call sites | Measurements |
|---|---|---|---|
| `PageButton` | `PageParts.kt:546` | 99 | five sizes (below), fill white if `primary`, `Ink.Faint` plus hairline `onCard`, else `Greys.Control`, red text when `destructive` |
| `ButtonSize.Main` | `PageParts.kt:524` | 6 explicit | 64 high, side 22, icon 18, `button` 16 semibold |
| `ButtonSize.Page` (default) | same | about 71 | 56, 18, icon 20, `button` 16 |
| `ButtonSize.Compact` | same | 4 | 44, 18, 16, `body` 15 |
| `ButtonSize.Hero` | same | 9 | 48, 18, 16, `body` 15 |
| `ButtonSize.Row` | same | 9 | 32, 14, 14, `secondary` 13, forced to 36 at three sites (`PageItemRow`, `StatusBanner`, `ReadyCardio`) |
| `PageSaveButton` | `PageParts.kt:957` | 3 | `Row` size |
| `PageTextButton` | `PageParts.kt:594` | 5 | min 44, side 16, `body`, 72% white, no fill |
| `WorkoutButton` | `GlowWorkoutMode.kt:151` | 13 | full width, 56 (or 48), circle, `ButtonText`, icon 18, gap 10 |
| `SaveCancel`, `SheetActions` | `HeartZonesScreen.kt:164`, `SettingsSheets.kt:40` | 1, 7 | two `PageButton` half and half, gap 10, top 4 |
| `SummaryButtons` | `GlowWorkoutSummary.kt:386` | 6 | `RoundIconButton` 64 plus `Main`, gap 10 |
| `HaloDeleteWorkout` | `GlowWorkoutSummary.kt:398` | 6 | `Compact` destructive centred, top 4 |
| `DeleteWorkout` | `GlowWorkoutMode.kt:399` | 1 | a full-width variant |
| `EditTodayButton` | `TodayCards.kt:1159` | 1 | `Compact` in a box with 8 v padding |
| `StrapHeldNotice` Connect, `CardButton`, `TrimButton`, `PhaseAction`, `MapButton` | `ReadyScreen.kt:443`, `DeviceScreen.kt:591`, `GlowWorkoutMode.kt:454`, `live/LiveIntervals.kt:168`, `live/LiveMap.kt:150` | 1 each | own sizes 52, 36, 48 |
| `ControlsRow` etc. | `live/LiveFrame.kt:294` | 2 | live bottom bar |
| `HoldToEnd` | `live/LiveFrame.kt:568` | 1 | ring 44 inside 60 |

Code, round icon buttons:

| Implementation | file:line | Call sites | Size |
|---|---|---|---|
| `RoundIconButton` | `PageParts.kt:135` | 24 | default 38, glyph 47% of size, circle, flat, `Greys.Control`, notice dot option |
| `BackButton` | `PageParts.kt:119` | wrapper | 44 / 18 |
| `RoundIcon` | `ReadyScreen.kt:412` | 4 | 44 / 18, `Raised` default, `pressClickable` |
| `MinusPlus` | `ReadyScreen.kt:424` | 2 | `RoundIcon` 44 or 52 with gap |
| stepper buttons | `GoalsSheet.kt:177`, `HeartZonesScreen.kt:176` | 5, 3 | `RoundIconButton` 40 and 44 |
| `FlatButton` | `live/LiveFrame.kt:525` | 3 | 88 Pause, white or `Control` |
| `SideAction` | `live/LiveFrame.kt:337` | 2 | 60 circle, glyph 22, plus a label |
| `StartWorkoutStrip` play | `TodayCards.kt:226` | 1 | 40 with a moving border |

Boards:
- Primary white: 64 high `r 32` 17 px (12 boards), 56 high `r 28` 16 px (15 boards), 60 high `r 30` 16 px (moments).
- Secondary dark: same heights, `#181818`.
- In-card glass or dark: 44 `r 22` 14 px `p 0 18px` gap 8 (13 boards), 36 `r 18` 12 to 14 px `p 0 12px` gap 6 (25 boards).
- Pills: 40 `r 20` `p 0 12px 0 7px` gap 8 15 px (Ready sport pill, 7 boards), 40 `p 0 16px` 14 px.
- Icon buttons: 44 circle (96 boards `#181818`, 25 `rgba(0,0,0,.35)`), 40 circle (GoalsSheet), 52, 60 (26 boards), 88 (12 boards, `r 44`).
- 60 and 64 squircles with `r 22` on summary and live boards (these boards pre-date the circle rule).

Differences:

| Difference | Verdict |
|---|---|
| Five `PageButton` heights 64 / 56 / 48 / 44 / 32 | Four are deliberate (main tap, page, in-card, small pill). 48 `Hero` against 44 `Compact` is drift for the same "button on a card" job (boards use 44) |
| `Row` 32 forced to 36 at three sites | Drift: boards say 36 |
| `Main` label 16, board 17. `Compact` label 15 (`body`), board 14 | Drift in type |
| `WorkoutButton` (13 uses) duplicates `PageButton` with 56 (48) and `ButtonText` | Drift: delete |
| Round icon: 38 default, 40, 44, 52, 60, 64, 88 | 44 and 88 deliberate. 38, 40, 52 drift |
| `RoundIcon` (Ready) `Raised` fill and `pressClickable`, `RoundIconButton` `Control` fill and plain clickable | Drift |
| `PageTextButton` has its own minimum 44 | Deliberate (text only) |

Proposal: `Button(label, icon, variant, size)`:
- Variants: `Primary` (white), `Secondary` (Control), `Glass` (on a lit card: `Ink.Faint` with the edge), `Destructive` (red label on Control), `Text`.
- Sizes: `Main` 64, `Page` 56, `Card` 44, `Small` 36. Labels: Main 17, Page 16, Card 14, Small 13. Side padding 22, 18, 18, 14. Icon 18, 20, 16, 14.
- `IconButton(icon, size = 32 | 44 | 60 | 88, variant = Control | OverWash | Glass | Primary)` replacing `RoundIconButton`, `RoundIcon`, `FlatButton`,
  `BackButton`, `InfoButton` (non-lit), the stepper buttons, `SideAction`'s circle.
- `ButtonPair(left, right)` for Cancel and Save, Share and Done, Save and Not a workout: gap 12 (today 10 and 8).
- Replaces `PageButton` (sizes 5 to 4), `WorkoutButton`, `PageSaveButton`, `SummaryButtons`, `SaveCancel`, `SheetActions`, `CardButton`, `TrimButton`,
  `MapButton`.

Built (17 Nov 2026), `glow/HaloButton.kt`:
- `HaloButton(label, onClick, modifier, variant, size, icon, enabled)`. Variants `Primary` (white, black label), `Secondary` (`Greys.Control`, white),
  `Glass` (`Ink.Faint` with the hairline, for a lit card), `Destructive` (Secondary fill, red label, the lighter red at Card and Small), `Text`
  (no fill, 72% white `body`, min 44 to tap). Sizes `Main` 64 / `Page` 56 / `Card` 44 / `Small` 36, label 17 / 16 / 14 / 13 semibold
  (`GlowType.buttonMain`, `button`, `buttonSmall`, new `buttonMini`), side padding 22 / 18 / 18 / 14, pill, leading icon 16 with a gap of 8.
- `HaloIconButton(icon, contentDescription, onClick, modifier, variant, size, dot, enabled, clickLabel, tint)`. Variants `Control`, `Primary` (white),
  `Glass` (`Greys.OverMap`, over a picture or map), `OverWash` (`Ink.Scrim`, over a detail's wash). Sizes `Small` 32, `Default` 44, `Live` 60, `Pause` 88,
  glyphs 16 / 20 / 22 / 28. Always a circle, press scale and ripple, role Button, the notice dot kept. `HaloIconSurface` is its bare circle.
- `ButtonPair(left, onLeft, right, onRight, ...)`: half and half, gap 12, the right one `Primary` unless told otherwise.
- All measurements are in `ButtonSpec`; `HaloButtonTest` holds them and checks that the old names are gone.

Gone: `PageButton`, `ButtonSize`, `PageSaveButton`, `PageTextButton`, `WorkoutButton` (and its `ButtonText`), `SaveCancel`, `SheetActions` (a lone
`SheetClear` text button is what was left of it), `SummaryButtons`, `HaloDeleteWorkout` (`DeleteWorkout` takes `wide`), `EditTodayButton`, `CardButton`,
`TrimButton`, `PhaseAction`, `MapButton`, `RoundIconButton`, `BackButton`, `RoundIcon`, `FlatButton`. The `PageConfirm` footer is a `ButtonPair`.
`InfoButton` stays as the card's (i) and now draws a `HaloIconButton` at 32.

Not folded in:
- `HoldToEnd` keeps its own press handling and ring (a hold is not a tap); it stands on `HaloIconSurface` at 60, so it is the same circle.
- `SideAction` keeps its name: it is the live controls row's label under a `HaloIconButton`, not a button of its own.
- `InfoButton` on a coloured card still draws the card's tinted `CardCircle` (3.4, with the title row).
- `StartWorkoutStrip` play (40 with a moving border) is part of its card (3.3).
- `IconPill`, `PageChip`, `DayChip`, `StrapPill` are chips (3.9, step 6); the Moment bottom bar (Share beside Continue) is its own row of two `HaloButton`s
  with the pair gap because Continue also stands alone.

Visible changes: 38 to 44 (calendar month arrows), 40 to 44 (goal steppers), 52 to 44 (Ready steppers), 64 to a Share text button beside Done on a
summary, 48 (`Hero`) to 44, 32 (`Row`) to 36, 52 on the strap card to 44, a header's 18 glyph to 20, Main label 16 to 17,
Card label 15 regular to 14 semibold, Small label 13 regular to semibold, pair gaps 8 and 10 to 12, Ready's `Raised` buttons to `Control`. The live side buttons were
already circles in code; only the older boards drew squircles.

### 3.8 Figures (number over label rows of three)

Code:

| Implementation | file:line | Call sites | Value | Label | Gaps |
|---|---|---|---|---|---|
| `DetailFigures` | `GlowMetricDetail.kt:552` | 10 | `figure` 28 with unit `secondary` grey | `noteSmall` 12 | value to label 3, column rule `Greys.Control` with `padding(start 16)`, row `padding 4` |
| `SummaryFigures` | `SummarySections.kt:306` | 2 | `figure` 28 with `UnitSpan` | `noteSmall` 12 | value to label 4, columns `spacedBy(16)` and rule with `padding(start 16)`, row `start 4 end 4 top 4` |
| `LiveStat` / `LiveValue` | `live/LiveFrame.kt:479`, `:496` | live | `figure` with unit `secondary` | `noteSmall` | gap 4, `StatRule` vertical Box |
| `RecapFigures` | `GoalsAndBests.kt:372` | 1 | own | | |
| `LoadFigure` | `LoadUsual.kt:127` | 1 | own | | |
| `FigureWord`, `PictureNumber` | `TodayCards.kt:420`, `:350` | card numbers | | | |
| `HeroFigure` / `HeroNumber` | `TodayCards.kt:104`, `:126`, `SummarySections.kt:274`, `live/LiveHero.kt:78` | heroes | Doto | | |
| `DotFigure`, `BigNumberText` | `RewardParts.kt:104`, `GlowType.kt:210` | 9 | Doto sizes 36 to 120 | | |
| `TaggedNumber`, `NumberWithTag` | `GlowActivityScreen.kt:490`, `NewsPill.kt:65` | | | | |

Boards: three columns, value Display 300 28 (line height 1), label 12 grey, rule `1px #24262C` with `padding-left 16`. Same on detail, summary, live.

Differences: identical on boards. In code, `DetailFigures` and `SummaryFigures` differ by 3 against 4 gap, and `spacedBy(16)` plus `padding(start 16)` in
`SummaryFigures` (32 dp between a column's content and the next rule, against 16 on boards): drift. Doto numbers are the deliberate separate
kind (the one big number per screen).

Proposal: `Figures(items: List<Figure(value, unit, label)>)`, one row, `Figure` slot: value `figure` 28, unit `secondary` grey, label `noteSmall`,
gap 4, rule `Greys.Raised` 1 dp, `padding(start 16)` after the first, no extra `spacedBy`. Replaces `DetailFigures`, `SummaryFigures`, `LiveStat` + `LiveValue`
(live uses the same component with live slots), `RecapFigures`, `LoadFigure`. `BigNumber` stays as its own kind (3.18).

Built (17 Nov 2026, step 6), `glow/HaloFigures.kt`:
- `HaloFigures(figures, modifier, variant = Static | Live, units, missing)`, measurements in `FigureSpec`: value 28 with its unit in 13 grey,
  label 12, 4 between, a 1 rule 16 before every column after the first, 4 at the row's sides. The rule is `OnSurface.line` (one step above
  the surface). A unit comes from `units` or is split from the value ("72 bpm"). Live never wraps a value or a label.
- Gone: `DetailFigures`, `SummaryFigures`, `LiveStat`, `LiveValue`, `StatRule`, `RecapFigures`, `LoadFigure`. `LiveFigures` stays as a one
  line rename of `HaloFigures(variant = Live)` for the nine live screens.

### 3.9 Chips, pills and tags

Code:

| Implementation | file:line | Call sites | Measurements |
|---|---|---|---|
| `PageChip` | `PageParts.kt:748` | 5 | min 36, side 14, circle, 1 dp `Ink.Line` outline, `secondary`. Picked: `Greys.Picked` plus 1.5 white ring |
| `IconPill` | `PageParts.kt:795` | 5 | height 44 (36 on Ready), start 12 (16 without icon), end 14, gap 8, icon 18, `body`, optional caret 14, hairline option |
| `RecentPill` | `ChooseScreen.kt:273` | 3 | `IconPill` on `Greys.Card` with hairline |
| `StrapPill` | `dashboard/StrapPill.kt:47` | 1 | 36 high in a 48 target, side 12, gap 6, dot 7, `noteSmall` |
| `OutlineTag` | `GlowLayout.kt:578` | 4 | circle, 1 dp `Ink.Line`, 8 by 2, `caption` 11 |
| `NewsPill` | `NewsPill.kt:27` | 3 | filled `NewsPillFill`, 9 by 3, `caption` |
| `RowTag` | `NewsPill.kt:42` | 2 | text only, `noteSmall`, ink lifted 55% to white |
| `StatusPill` | `BadgeSheets.kt:107` | 2 | min 34, 14 by 6, ink at 16% |
| `StateMarker` | `GlowLayout.kt:555` | | amber `caption` |
| `StatusDot` | `PageParts.kt:885` | 2 | dot 6, `secondary`, gap 8 |
| `EarnedChips` | `SummarySections.kt:949` | 1 | own |
| `DayPills`, `DayChip`, `DayCell` | `RewardParts.kt:80`, `AlarmSheet.kt:85`, `DayCalendar.kt:177` | 2, 1, calendar | 44 / 44 / 42 squircles |
| `HourPills`, `ListSegment`, `ExercisePill`, `BarPill` | `GlowActivityScreen.kt:329`, `GlowWorkoutScreen.kt:532`, `live/LiveSets.kt:339`, `WorkoutCapsule.kt:142` | | own |
| `PlanChips` | `ReadyCardio.kt:352` | 1 | `PageChip` gap 6 |
| `OptionButton` | `PageParts.kt:899` | 0 | unused |

Boards: chips 36 high `r 18` `p 0 12px` gap 6 12 px (17 boards, `#181818`), 36 high transparent 14 px (25 range tabs), 36 high white 14 px (11 picked),
`p 0 14px` 14 px (5 boards, item action). Tag: `display:inline-flex height:28 p 0 10 r 14 rgba(0,0,0,.35) 12px` (detail "Live" tag).

Differences:

| Difference | Verdict |
|---|---|
| `PageChip` 36 / 14, `IconPill` 44 / 12 or 14, board chip 36 / 12 | `IconPill` is a button pill (44) not a chip. The 36 Ready variant is drift against its own 44 default. Deliberate: choice pill (44 or 40 on Ready) against chip (36) |
| Tags: outline (1 dp line), filled (`NewsPill`), text only (`RowTag`), amber marker, status pill 34 | Five looks for "a few words about this". Deliberate in two (state marker is amber because it is about the sync, row tag is text because a list row's second line is text), drift in the rest. Reduce to Outline, Filled, Text, Marker |
| Picked chip ring 1.5 (`PickedRing`), day cell ring 1.5, `DayPills` today ring 2, `FamilyTile` selected ring 2, radio ring 1.5 | Drift: 1.5 everywhere (board spec says 1.5) |
| Day chips 44 squircle (`IconShape`), `DayCell` 42 | Drift: 44 circle or `corner.inner` |
| Strap pill 36 / 12 / 6 / 7 | Deliberate, one place |

Proposal: `Chip(label, icon, selected, variant = Outline | Filled | Choice)` and `Tag(text, variant = Outline | Filled | Text | Marker | Status(ink))`.
- `Chip` 36 high, side 14 (12 with icon), gap 8, secondary 13, picked = `Greys.Picked` + 1.5 white ring.
- `Choice` is the 44 pill (`IconPill`): icon 18, side 12 / 16, body 15, optional caret.
- Replaces `PageChip`, `IconPill`, `RecentPill`, `PlanChips` cell, `DayChip`, `DayPills` pills (as `Chip` square variant), `ListSegment` cell, `StatusPill`,
  `OutlineTag`, `NewsPill`, `RowTag`, `StateMarker`, `StrapPill` (as a `Choice` with a status dot).

Built (17 Nov 2026, step 6), `glow/HaloChip.kt`:
- `HaloChip(label, onClick, modifier, variant = Outline | Filled | Choice, size = Regular 36 | Main 44, selected, icon, tint, dot, caret,
  enabled, role, description, clickLabel)`, measurements in `ChipSpec`: sides 14 (12 after an icon or dot), gap 8, label 14 (`GlowType.chip`).
  Filled and Choice fill with `OnSurface.control`, a picked Choice is `OnSurface.picked` with the one ring, `ChipSpec.Ring` 1.5.
- `HaloTag(text, modifier, variant = Filled | Outline | Text | Marker | Status, color, onClick)`, `TagSpec`: 22 high, sides 8, 11 semibold
  (`GlowType.tag`). `NewsTagView` is a white Filled tag, `NumberWithTag` moved here.
- Gone: `PageChip`, `IconPill`, `RecentPill` (a `SportChip` helper over `HaloChip`), `DayChip`, `OutlineTag`, `NewsPill` (file deleted),
  `RowTag`, `StateMarker`, `StatusPill`, `PickedRing`, the unused `OptionButton`. `EarnedChips` and `PlanChips` lay out `HaloChip`s.
  `StrapPill` is a Filled `HaloChip` with a status dot.
- Not folded: `ListSegment` (a stretch of a list card split over lazy items, not a chip), `StatusDot`, `ExercisePill`, `HourPills`, `BarPill`.

### 3.10 Segmented controls

One implementation: `GlowSegments` (`PageParts.kt:841`, 4 call sites: SegmentSheet, Share route, Detail range (role Tab), Check-in).
Track `Greys.Control`, pad 4, segment vertical 9 so about 46 tall, white pick, `body` 15, press scale 0.97.
Boards: HaloRewardsSettingsRules B4 segmented control, track darker than the sheet, picked white. Range pills on details are drawn as 36 high
tabs (`r 18`) with a white picked pill (25 and 11 boards).
Differences: the detail range uses `GlowSegments` at about 46 high where boards draw 36 (`height: 36px; r 18; fs 14px`): drift. Check-in uses it too.
Proposal: keep `Segments(options, selected, size = Regular 44 | Compact 36)`. No replacements, add the 36 size.

Built (17 Nov 2026, step 6): `GlowSegments` moved to `glow/HaloChip.kt` with `size = SegmentSize.Regular 44 | Compact 36` (`SegmentSpec`, pad 4,
picked white with a black label). Its track is `OnSurface.track`. The detail range is Compact 36.

### 3.11 Switches

One implementation, `GlowSwitch` (`GlowParts.kt:76`, 3 direct plus 23 via `PageSwitchRow`): 50 by 30, knob 24, pad 3, white on, `Greys.Control` off,
knob black or `Ink.Tertiary`. Boards: `width 50 height 30 r 15`, knob 24, white and black. Match. Proposal: no change, trail slot of `Row`.

Built (17 Nov 2026, step 6): unchanged, `GlowSwitch` 50 by 30, the `Toggle` trail of `HaloRow`.

### 3.12 Radio rows and check

`RadioRow` (`SettingsSheets.kt:161`, 1 site): 22 ring, 8 inner dot, 1.5 `Greys.Line` outline, min 56, 16 / 10. `PageMenuRow` (`PageParts.kt:425`) uses a
tick (`Tick02`, 18) in a dropdown. Boards: radio rows for "one of many" in B4. Deliberate: radio (sheet, one of many) against menu tick (inline
dropdown). Proposal: `Row(variant = Choice)` with trail `Radio(22)` or `Check(18)`.

Built (17 Nov 2026, step 6): the radio's ring is `ChipSpec.Ring` (1.5), the one ring width.

### 3.13 Pickers and wheels

| Implementation | file:line | Measurements |
|---|---|---|
| `TimeWheels`, `Wheel` | `alarms/TimeWheels.kt:62`, `:96` | `wheelFar` 24, `wheelNear` 32, `wheelChosen` 48 (all Display 300), alphas 0.18 and 0.4, centre band 64 high `r 20` |
| `StepperSheet` + `MinusPlus` | `SettingsSheets.kt:76`, `ReadyScreen.kt:424` | value `goalValue` 44, buttons 44 |
| `GoalStepper` | `GoalsSheet.kt:177` | label `body`, buttons 40, number 104 by 40 `goalValueMinor` 26 |
| `BpmStepper` | `HeartZonesScreen.kt:176` | buttons 44, number `goalValue` 44, vertical 8 |
| `ValueStepper`, `TypedNumber`, `TapValue` | `ReadyCardio.kt:410`, `:439`, `:415` | buttons 52, `goalValue` 44, typed in place |
| `LogStepper` | `live/LiveSets.kt:421` | buttons 44, `pageTitle` value, card 14 padding |
| `PageField` / `PageNumberField` | `PageParts.kt:615`, `:920` | min 52, `TileShape`, `Greys.Control`, side 16 (4 with trailing), `body`, label 6 above |
| `MetricSearchField`, `SearchField` | `AllMetricsSearch.kt:64`, `ChooseScreen.kt:619` | own |
| `RadioSheet`, `SegmentSheet` | `SettingsSheets.kt:137`, `:116` | via `PageSheet` |
| `Calendar` (`DayCalendarSheet`, `MonthGrid`, `DayCell`) | `dashboard/DayCalendar.kt:73`, `:149`, `:177` | 42 cells |

Boards: numbers via a stepper `44 squircles` with Display Light 44, one-of-few via a segmented control, times via two wheels (24, 32, 48, 64
high band `r 20`), one-of-many via radio rows. Match the doc.

Differences: stepper buttons 40 (`GoalStepper`), 44 (`BpmStepper`, `StepperSheet`, `LogStepper`), 52 (Ready). Stepper value styles 44, 26, 20. Deliberate: Ready goal is a hero-size
value (52 buttons, 44 value), sheet stepper is 44 / 44. Drift: 40 and the goals sheet 26 (a row value, so fine) and 52.
Proposal: `Stepper(value, unit, onLess, onMore, size = Sheet | Row)`: Sheet buttons 44 value `goalValue` 44, Row buttons 44 value 26 with a 104 wide
tappable number. Drop 40 and 52. Keep `Wheel` and `Field` as they are (one implementation each) with `Field` min height 52 to 56 to match.

Built (17 Nov 2026, step 6), `glow/HaloStepper.kt`:
- `HaloStepper(less, more, onLess, onMore, modifier, size = Sheet | Row, lessEnabled, moreEnabled, gap, value)` and `StepperNumber(text, unit,
  size)`, measurements in `StepperSpec`. Both buttons are `HaloIconButton` 44. Sheet: the number Display Light 44 centred between the
  buttons. Row: the number 26 in a 104 by 44 box, buttons 8 from it. The number is a slot, so it can be typed in place.
- Gone: `MinusPlus`, `ValueStepper`, `LogStepper` (a log card with a Row number), `BpmStepper` (`BpmField`), `GoalStepper` (`GoalRow`, its 40
  buttons now 44), the stepper row of `StepperSheet`.
- Day cells: the week strip's are `ChipSpec.DayStrip` 62, the calendar's `ChipSpec.DayCalendar` 54 (or the column's width when narrower).

### 3.14 Sheets

Code:

| Implementation | file:line | Call sites | Measurements |
|---|---|---|---|
| `GlassSheet` | `components/Glass.kt:168` | 1 (inside `PageSheet`) | `Greys.Raised`, `SheetShape` top corners 56, grabber 40 by 5 `Greys.Line`, top 10, max width 600, pull to dismiss |
| `PageSheet` | `PageParts.kt:689` | 30 | side 20 (16 on AllMetrics, 20 Switcher), top 16, bottom 28, block gap 16, title `sheetTitle` 22 with note `secondary` gap 4, scrolls past 88% of screen height |
| `PageConfirm` | `PageParts.kt:718` | 9 | text `body` `Ink.Strong`, `PageButton` pair gap 10 |
| `SheetActions` | `SettingsSheets.kt:40` | 7 | Cancel and Save, top 4, gap 10 |
| `InfoSheet` | `InfoSheet.kt:59` | 22 | title 22 with a 4 by 24 bar, sections gap 6, body line height 22.5, scale bars 10 high gap 6, Close `PageButton` |
| `BadgeSheet`, `BestSheet` | `BadgeSheets.kt:47`, `:82` | 2 | `SheetColumn` gap 10, `SheetTitle` `pageTitle` 17 centred (not the 22 of every other sheet), `StatusPill` 34, token 96 |
| Settings sheets | `SettingsSheets.kt`, `GoalsSheet.kt`, `SleepGoalSheet.kt`, `QuietTimeSheet`, `LimitSheet`, `AlarmSheet`, `HeartZonesScreen.kt` `ZoneSheet`/`MaxSheet`, `ExerciseSheet`, `SaveIntervalsSheet`, `ManualPairSheet`, `TrimSheet`, `EditTodaySheet`, `AllMetricsSheet`, `CelebrationsSheet`, `WeeklyGoalSheet`, `DeleteSleepSheet`, `DayCalendarSheet`, `SwitcherSheet`, `PartialDaySheet` | about 25 | all through `PageSheet` |

Boards: sheet `r 32 32 0 0`, background one step lighter than the card, padding `10px 20px 28px`, gap 16, grabber 40 by 5 `r 3` `#3D3D3D`, title Display 22 / 600.
Button pair 56 high `r 28`. Detail: HaloBadgeSheet and HaloBestSheet board titles draw Display 22.

Differences:

| Difference | Verdict |
|---|---|
| `SheetCorner` 56 (code) against 32 (boards) | Drift (a big one: it is the most visible corner in the app) |
| Top inset 10 (boards) against 16 (`PageSheet`) after the 10 grabber space | Drift |
| Side 20 against 16 (AllMetrics) | Deliberate for a sheet of grouped lists (panels bring their own inset), documented in `PageSheet`'s kdoc |
| `SheetTitle` 17 centred (badge sheets) against `sheetTitle` 22 left | Drift |
| Block gap 16 (PageSheet), 10 (`SheetColumn`), 6 (InfoSheet sections) | Deliberate for centred token sheets and reading sheets, but 10 and 6 should be 8 and 8 on the scale |

Proposal: `Sheet(title, note, footer, side = Page | Grouped)`. Footer is a `ButtonPair` or one `Button`. Corner `corner.sheet` 32. Keep `PageConfirm`
as `Sheet(footer = ButtonPair)`, `InfoSheet` as `Sheet(variant = Info)` with named slots `Section(title, body)` and `Scale`. `BadgeSheet` and
`BestSheet` become `Sheet(variant = Token)` with a centred column and `SheetTitle` 22. The implementations that go: `SheetColumn`, `SheetTitle`, `SheetText`
(`BadgeSheets.kt:134` to `:145`), `SheetActions`, `SaveCancel`.

Built (17 Nov 2026, step 4), `glow/HaloSheet.kt`:

- `HaloSheet(onDismiss, title, note, variant, lead, accent, footer, content)` with `SheetSpec` (every measurement: grabber 36 by 4
  in `Ink.Line`, 10 from the top, sides 20 or 16, bottom 32, block gap 16, token gap 8, title to note 4, section gap 8, scrolls past
  88% of the screen). `HaloSheetVariant` is `Page` (default), `Grouped` (side 16), `Info` (title may take two lines), `Token`
  (centred, gap 8). The title is `GlowType.sheetTitle` 22 on all of them, left aligned, and centred on `Token`.
- Slots: `footer` is the sheet's one `HaloButton` or a `ButtonPair`, last in the column, 16 under the content. `lead` sits above the
  title (a token sheet's medal and its group), `accent` is an info sheet's 4 by 24 bar. `SheetSection(title, body)` and
  `SheetScale` are the info sheet's slots, `SheetTokenLine` a token sheet's centred line. `HaloConfirmSheet` is text, optional
  content and a `ButtonPair` footer (red when destructive).
- `GlassSheet` stays the low-level surface (portal, the fill `SheetSpec.Fill`, `Corner.Sheet`, grabber from `SheetSpec`, pull to dismiss,
  600 wide cap) and only `HaloSheet` calls it. `InfoSheet` stays as the content of an info sheet (22 call sites, it builds the
  sections from an `Info`) and is now a plain `HaloSheet` call.
- Gone: `PageSheet`, `PageConfirm`, `SheetColumn`, `SheetTitle`, `SheetText`, the grabber 40 by 5, the 16 top inset (it is 10 to the
  grabber, then the 16 gap), the 4 extra above a pair, the 17 centred token title. Rule test: `HaloSheetTest`.
- Kept as is: `SheetClear` (the quiet text button above a picker's pair) and three sheets whose content is its own composable with
  its own pair (the trim sheets).

Changed (17 Nov 2026, step 6, designer): the sheet's fill is `Greys.Card` (`SheetSpec.Fill`), not Raised. `GlassSheet` provides it as
`LocalSurface`, so what sits on a sheet steps up as inside a card: a panel on a sheet is Raised, a control, track or chip on that panel Control,
a picked state Picked.

### 3.15 Banners and notices

| Implementation | file:line | Call sites | Measurements |
|---|---|---|---|
| `StatusBanner` | `PageParts.kt:1097` | 3 | `CardShape`, min 56, 19 h, 16 v, circle 40 with a 20 glyph at 16% of kind colour, title `cardTitle`, note `secondary`, action `Row` pill forced 36 |
| `ErrorPanel` | `alarms/AlarmsScreen.kt:191` | 1 | `PagePanel` with a warn note and a full-width button |
| `StepPanel`, `Warning`, `InfoLine`, `Point` | `firmware/FirmwareScreen.kt:166`, `:402`, `:394`, `:382` | | own |
| `StrapHeldNotice` | `ReadyScreen.kt:443` | 1 | own |
| `NotSavedNotice`, `StoppedPrompt` | `live/LiveFrame.kt:537`, `:547` | 2 | `QuietPanel` with `GlowHeader` and `PageNote` |
| `CaveatLine` | `TabParts.kt:144` | | dot 6 and `secondary` |
| `BluetoothBlockedDialog` | `root/PermissionGates.kt:153` | 1 | a dialog |
| `NewsPill`, `OutlineTag` | see 3.9 | | |
| `PartialDaySheet` | `DataStateParts.kt:198` | 1 | an `InfoSheet` |

Boards: HaloRewardsSettingsRules B2 draws "Last backup failed / The folder can't be reached / Try again" as one plain panel. The rules doc says a 4 px left bar in
the status colour, a 15 / 600 line, 13 note and one action pill. The code draws a 40 circle instead (the HaloYou board). Doc and code do not agree.

Differences: banner on settings pages (`StatusBanner`) against live notice (`QuietPanel` + header + note + button) against Ready notice: drift, all "news with
an action". Proposal: `Banner(kind = Good | Warn | Problem | Info, title, note, action)` as a `Card(Free)` with a lead `Tile(44, kind tint)`.
Replaces `StatusBanner`, `ErrorPanel`, `NotSavedNotice`, `StrapHeldNotice`, `StoppedPrompt` frame, `Warning`, `InfoLine`. The designer decides bar or circle.


Built (17 Nov 2026, step 5), `glow/HaloBanner.kt`:

- `HaloBanner(kind, title, modifier, note, action, onAction, icon, framed, content)` with `BannerSpec`: a grey `HaloCard` with a 44
  circle tile at 16% of the kind's colour (the designer chose the circle), title 15, note 13, an action `HaloButton` at 36.
  `BannerKind` is `Good`, `Warn`, `Problem`, `Info`. `content` takes a full-width button under the words (Not saved's Try again,
  Stopped's pair), `framed = false` draws it inside a card that is already there (the firmware step's warnings).
- Gone: `StatusBanner`, `ErrorPanel` (Alarms calls the banner), the firmware `Warning` and `InfoLine` (an `InfoRow` now). The live
  `NotSavedNotice` and `StoppedPrompt` and Ready's `StrapHeldNotice` keep their names for their logic and draw a banner.

### 3.16 Empty and loading states

Code: no skeletons. A missing value is `WaitingDash` (`GlowList.kt:273`, 14 by 3 `Ink.Disabled` bar, 16 uses). Row waiting state dims the icon to 30%.
`DetailEmpty` (`GlowMetricDetail.kt:597`, `secondary` text, 24 v). Today's loading, first-day and no-strap states are the same cards at 0.45 opacity
(`TodayPlan.kt:79`, `GlowTodayScreen.kt:226`, boards HaloTodayLoading, HaloTodayFirstDay, HaloTodayNoStrap). Pairing has `PairingStage`
(`DataStateParts.kt:61`, 92 high stage with phone 30 by 52 and strap), `PairHero` and `StuckHero` (`GlowTodayScreen.kt:735`, `:757`). `HaloDetailNoData`, `HaloPartialDay`.

Boards: the dash is `28 by 6 r 3 rgba(255,255,255,.22)` (HaloTodayLoading) while the code draws 14 by 3. Differences: dash size (28 by 6 on the board, 14 by 3
in code) is drift, found only once because there is only one implementation. State word in place of a value: deliberate. Proposal: `Empty(variant = Dash | Word | Stage)`
and `Dim(0.45)` as a card modifier. Keep `WaitingDash` at one size (board: 28 by 6 on a Small, 14 by 3 in a row) as two named sizes.

### 3.17 Progress (rings, bars, dot rings, segments)

| Implementation | file:line | Measurements |
|---|---|---|
| `ProgressRing` | `GlowCharts.kt:907` | stroke 8 default, 2.5 in a row at 20 (`ListRow`) or 32 with padding 5 (`DetailRow`) |
| `WeeklyGoalRing` | `Medals.kt:286` | solid arcs |
| `WeekRings`, `CountdownRing`, `LevelFill`, `WeekOfDots` | `pictures/LevelPictures.kt:451`, `:235`, `:205`, `:157` | picture marks |
| `PageProgress` | `PageParts.kt:664` | 4 high pill, track `Ink.Edge`, white fill, indeterminate slide |
| `ShareBar` | `RewardParts.kt:67` | 4 high, track `Greys.Raised` |
| `ContributorRow` bar | `TabParts.kt:131` | 8 high, track `Ink.Faint` |
| `ZoneBar` | `GlowCharts.kt:834` | 6 default, gap 2, 4 on `WorkoutRow` |
| `SegmentBar` users (`SetSegments`, `RoundBar`, `ZoneTimeBar`) | `live/LiveSets.kt:325`, `live/LiveIntervals.kt:203`, `live/LiveIndoor.kt:105` | 6 / 4 / 3 corner 3, and 10 / 3 / 3 |
| `RoundsStrip` | `ReadyPlans.kt:223` | 10 high gap 2 |
| `LoadBar` | `LoadUsual.kt:153` | 10 high canvas |
| `EffortBar` | `live/LiveGame.kt:163` | 14 high gap 4 corner 4 |
| `BatteryBar` | `DeviceScreen.kt:485` | 150 by 8 `Ink.Edge` |
| `DetailGoalBar` | `DetailPicture.kt:593` | 6 high track, mark 3 by 14 |
| `CELL_BAR` | `BadgeParts.kt:174` | 58 wide |
| `TrimBar` | `TrimBar.kt:165` | 327 lines, own handles |

Boards: workout rules, shared pieces: "row of solid segments, height 6, gap 4, radius 3. Done in the hero's shade, current white, rest white at 0.18". Rewards: 4 px bar on
`#1C1D22`. Doto dot rings for goals on Today only.

Differences: bar height 4, 6, 8, 10, 14 for "how far along". Track colour `Ink.Edge`, `Greys.Raised`, `Ink.Faint`. Deliberate: a bar that says "how far" (4 or 6) against a bar
that is a data picture (the `LoadBar`, `EffortBar` and zone bars are charts and have their own board). Drift: 4 against 6 against 8 on the plain progress bars. Proposal: `Progress(share,
variant = Bar(6) | Segments(n, 6) | Ring(size, stroke))`, bar 6, track `Greys.Raised`. Charts stay in the chart kit.

Built (17 Nov 2026, step 6), `glow/HaloProgress.kt`:
- `HaloProgress(share, modifier, color)`: a 6 pill bar on `OnSurface.line`, a sliding third while `share` is null. `HaloProgressSegments(count,
  done, modifier, doneColor)`: 6 high, 4 apart, corner 3, through the chart kit's `SegmentBar`. `HaloProgressRing(share, color, modifier,
  stroke)`: 2.5 in a row's lead. Measurements in `ProgressSpec`.
- Gone: `PageProgress`, `ShareBar`. `SetSegments` and `RowLead.ring` use the new parts. Chart bars stay in the chart kit.

### 3.18 Big numbers (Doto)

`BigNumberText` (`GlowType.kt:210`, 9 uses) and `HaloNumber` (`TabParts.kt:171`, 10 uses), `DotFigure` (`RewardParts.kt:104`), `HeroValue` (`GlowParts.kt:115`), `HeroNumber`
(three: `TodayCards.kt:126`, `SummarySections.kt:274`, `live/LiveHero.kt:78`), `DetailScoreNumber` (`ScoreDetail.kt:439`), `DayNumber`, `SleepNumber`, `MindNumber`, `DotClock`, `PaceHero`.
Sizes are an enum, `BigNumber` (36, 40, 44, 52, 64, 68, 72, 76, 120). Boards draw Doto 36, 40, 44, 52, 64, 68, 72, 76, 80, 120 (counts above). Deliberate (rule: one per screen) but
there are six wrapper families for the same call. Proposal: `BigNumber(text, size, unit)` as the one entry, wrappers removed.

Built (17 Nov 2026, step 6), `glow/HaloBigNumber.kt`:
- `HaloBigNumber(text, size, modifier, color, unit, unitColor, unitLines, fit, wordStyle)`, measurements in `BigNumberSpec`. Digits are dots,
  a slash is display type, words between digits are small on their baseline, a unit follows 8 after, `fit` shrinks a wide number to its line.
  `cardNumberSize(dot)` picks a card's size (68, 52, 44, or 36 on a grey Small).
- Gone: `HaloNumber`, `DotFigure`, the summary's and the live screen's `HeroNumber`. Today's number with its lines is `HeroReading`.
  `BigNumberText` stays as the low-level drawing, called only by `HaloBigNumber` and the share pictures.
- The waiting dash in a card's number's place is 28 by 6 (`WaitingDash(onTile = true)`).

### 3.19 Medals, badges and tokens

`Medal` (`Medals.kt:225`, 7 uses) with `MedalLook` (rosette, medal, ring), `WeeklyGoalRing` (`Medals.kt:286`), `BadgeCell` / `BestCell` / `TokenCell` (`BadgeParts.kt:118`, `:143`,
`:165`), `BadgesPanel`, `BestsPanel`, `NextBadgeCard`, `TokenGrid` (`RewardParts.kt:54`, 3 columns gap 16), sizes `TOKEN` 72, `NEXT_MEDAL` 56, moment 160, sheet 96, `LARGE_FACE` 120
(`Medals.kt:310`). Boards: 160 / 96 / 72 and 56 for next. One implementation (`Medal`), cells are layout. Drift: a 120 `LARGE_FACE` that matches none of 160 or 96.
Proposal: `Token(look, size = 56 | 72 | 96 | 160, locked)`, `TokenGrid`. No other change.

Built (17 Nov 2026, step 6): `TokenSize` (`glow/HaloProgress.kt`) is Next 56, Grid 72, Sheet 96, Moment 160, and the badge parts,
sheets and moments take their medal sizes from it. `Medal` stays.

### 3.20 Icon tiles

Covered in 2.5. Implementations: `RowIcon` (`PageParts.kt:306`), the tile in `ListRow` (`GlowList.kt:167`), the tile in `DetailRow` (`DetailPicture.kt:195`), `SportIconTile`
(`ChooseScreen.kt:245`), `PlaceRow` tile (`ReadyCardio.kt:196`), `FoundRow` tile (`ChooseScreen.kt:516`), `EffortLight` (`GlowActivityScreen.kt:694`), `SportTile` (`:298`),
`FamilyTile` (`:480`), banner circle (`PageParts.kt:1111`), and `CardCircle`. Eleven drawings of "icon on a circle". Proposal: `Tile(icon, size = 32 | 44 | 64, tone)`, where
`tone` = Neutral (Control), Ink(colour at 16%), Selected (white, black glyph), Effort(glow).

Built (17 Nov 2026, step 6), `glow/HaloProgress.kt`:
- `HaloTile(icon, modifier, size = Small 32 | Medium 44 | Large 64, tone = Neutral | Ink(color, dim, picked) | Selected | Effort(effort),
  glyph, badge)`, glyphs 16, 20, 24, always a circle. Neutral is `OnSurface.control`, Ink the colour at 16% (30% and the one ring when picked),
  Effort one step above the surface with the heart glow.
- Now `HaloTile`: `HaloRow`'s lead tile and sport tile, `SportTile`, `FamilyTile`, the workout row's effort light, the banner's tile.
- Not folded: `CardCircle` and the chevron circle (they read the lit card's tint), `PlaceRow` and `FoundRow` tiles, the outdoor badge.

### 3.21 Dots and status dots

`NoticeDot` (`PageParts.kt:328`, 7 dp white, 11 ringed), `StatusDot` (`PageParts.kt:885`, 6), the strap pill dot (7), `CaveatLine` dot 6, `PulseDot` (`WorkoutCapsule.kt:259`),
`IconPill` dot 8, `SplitLine` dot 8, `StrapPill`. Sizes 6, 7, 8. Proposal: `Dot(size = 6 | 8, colour)`, with `NoticeDot` as a 8 white variant.

Built (17 Nov 2026, step 6): a chip's dot is `ChipSpec.Dot` 8 and a caveat's `NoteSpec.CaveatDot` 6. `NoticeDot` and `StatusDot` stay.

### 3.22 Strap pill

One implementation, `StrapPill` (`dashboard/StrapPill.kt:47`). 36 in a 48 target, `Greys.Control`, side 12, gap 6, dot 7, `noteSmall`. Boards: 36 high pill with dot (HaloToday* tab header).
Match. Proposal: as `Chip(Choice)` with a status dot slot (3.9).

Built (17 Nov 2026, step 6): `StrapPill` is a Filled `HaloChip` with its status dot, 36 in a 48 target.

### 3.23 Charts

Not audited here. Chart implementations: `glow/GlowCharts.kt` (1047 lines, `ZoneBar` `:834`, `ProgressRing` `:907`), `glow/pictures/LevelPictures.kt`, `glow/pictures/LinePictures.kt`
(`DayLine`, `DayStrip`, `TempDial`, `BedWakeBars`, `NapDay`), `glow/DetailPicture.kt` (`DetailGauge` `:230`, `DetailChart` `:287`, `DetailLineChart` `:365`, `DetailColumns` `:453`,
`DetailDrivers` `:536`, `DetailGoalBar` `:593`), `glow/DetailSleepPictures.kt` (`DetailStages`, `DetailStageShares`, `DetailSleepWindows`), `glow/SleepDetail.kt` (`NightChart`, `ScrubLine`,
`NightLines`), `glow/GlowMetricDetail.kt` (`NightChart` `:855`, `DayChart` `:917`, `HistoryChart` `:980`), `glow/ActivityDetail.kt` (`HourlyKcal`, `RangeKcal`, `StackedBars`),
`glow/TrimBar.kt`, `glow/GlowHealthspan.kt` (`AgeScale`), `glow/GlowHealthspanScreen.kt` (`PaceScale`, `ScaleLabels`, `EffortBar`), `glow/live/*` (`KmSplitBars`, `ZoneTick`),
`glow/WorkoutMap.kt`, `glow/RouteMap`. Board: HaloCharts. DetailPicture's header says it keeps "private copies of what GlowCharts and GlowList draw for cards" (so the tab and detail
charts are duplicated on purpose, and so are `ListRow` and `DetailRow`). That sentence is the design debt in one line.

### 3.24 Wheels and calendar

`DayCalendarSheet` (`dashboard/DayCalendar.kt:73`) with `MonthGrid` and `DayCell` (42 squircle, 1.5 ring when picked, 5 dot). `DaySwitcher` and `Peek` (`MomentHeader.kt:141`, `:196`).
`WeekStrip` (`GlowMetricDetail.kt:476`): seven 62-high squircles (`IconShape`), gap 4, 12 by 3 bar, letter 11 over date 15. Boards match (62 `r 20`, gap 4). Drift against
`DayChip` (44) and `DayPills` (44) and `DayCell` (42): four day-cell sizes (62, 44, 44, 42). Proposal: `DayCell(size = Strip 62 | Grid 44)` both on `corner.inner` (16).

### 3.25 Switchers and tab-like controls

`GlowSegments` (3.10), `DaySwitcher` (3.24), `FloatingNavBar` (3.1), the Detail switcher (`SwitcherSheet`, `GlowMetricDetail.kt:513`, a sheet of rows) and the Today
`EditTodayButton`. Nothing further.

### 3.26 Text panels and info

`PageTextPanel` (`PageParts.kt:1072`, 16 padding, title `cardTitle`, body `body` `Ink.Strong`, gap 10), `ReleaseNotesView` (`UpdatesPage.kt:140`, a Card with 16 padding),
`TermsPanel` (`GlowHealthspanScreen.kt:171`), `PacePanel` (`:124`). Proposal: `Card(Free) { Title, Paragraph }` where `Paragraph` is `body`, `Ink.Strong`.

### 3.27 Wide and tall tile pictures on tabs

`TodayPictureCard` (`TodayCards.kt:369`), `PictureSmall` (`GlowActivityScreen.kt:450`), `SleepPictureCard` (`GlowSleepScreen.kt:349`), `StepsGlow`, `DayLoadCard`.
All are `Card(Small | Wide)` with a picture slot: no new components, only the card frame (3.3) is missing a single name.

---------------------------------------------------------------------------------------------------------------------

## 4. Proposed component list and migration order

Twenty components cover everything above. Names and slots:

| # | Component | Variants | Slots | Scale values used | Replaces (count) |
|---|---|---|---|---|---|
| 1 | `PageFrame` (built as `HaloPageList` and `HaloPageColumn`, 17 Nov 2026) | Tab, Detail, Settings, Workout | header, content, bottom bar | gutter 16, gap 12 / 16 / 24 / 12, header 48 (60 on tabs) | `ScreenList`, `GlowScreenList` gutter, `WorkoutPage`, 4 bespoke top bars (8) |
| 2 | `PageHeader` (built as `HaloPageHeader`, 17 Nov 2026) | Page, Detail (over wash), Workout | start (44 icon button), centre, end | 48 high, 96 / 1fr / 96, 44 | `PageHeader`, `BackButton`, `DetailNav`, `ReadyTopBar`, `LiveTopBar`, summary top (6) |
| 3 | `Card` (built as `HaloCard`, 17 Nov 2026) | Lit(angle), LitLevel, Grey, Outline. Sizes Small 200, Wide 160, Strip 64, Free | title, body, footer | `inset.card` 22 to 26, `corner.card` | `GlowCard`, `PictureCard`, `QuietPanel`, `LitPanel`, `OutlineCard`, `PagePanel`, `PageTextPanel`, `ListPanel` outer, `PageRows` outer, `DetailPanel` outer, 27 hand-built panels (40) |
| 4 | `Title` (built as `HaloTitle`, 17 Nov 2026) | InCard, OutOfCard, Group | text, note, mark, trailing | 15 / 13, gap 12 note, drop 2 | `GlowHeader`, `CardTitle`, `TitleRow`, `EdgeTitle`, `CardHead`, `DetailHeading`, `SectionHead`, `SectionLabel`, `StripLine`, `WorkoutDayHeader` (10) |
| 5 | `Row` (built as `HaloRow`, 17 Nov 2026) | Nav, Value, Switch, Action, Info, Item, Metric, Contributor, Zone, Choice, Workout, Fact, Split, Stepper | lead, title, line, value, trail, below | min 56, v 12, gap 12, tile 32, divider `Greys.Raised` | 25 row composables listed in 3.5 |
| 6 | `Button` (built as `HaloButton`, 17 Nov 2026) | Primary, Secondary, Glass, Destructive, Text. Sizes 64, 56, 44, 36 | icon, label | 22 / 18 / 18 / 14 side | `PageButton`, `WorkoutButton`, `PageSaveButton`, `CardButton`, `TrimButton`, `MapButton`, `PageTextButton` (8) |
| 7 | `IconButton` (built as `HaloIconButton`, 17 Nov 2026) | Control, OverWash, Glass, Primary. Sizes 32, 44, 60, 88 | icon, notice dot | glyph 16, 20, 22, 28 | `RoundIconButton`, `RoundIcon`, `FlatButton`, `InfoButton` (non-lit), stepper buttons, `SideAction` (6) |
| 8 | `ButtonPair` (built, 17 Nov 2026) | CancelSave, ShareDone | left, right | gap 12 | `SheetActions`, `SaveCancel`, `SummaryButtons`, `PageConfirm` footer (4) |
| 9 | `Chip` (built as `HaloChip`, 17 Nov 2026) | Outline, Filled, Choice | icon, label, caret, dot | 36 / 44, 14 side | `PageChip`, `IconPill`, `RecentPill`, `StrapPill`, `DayChip`, `ListSegment` cell (6) |
| 10 | `Tag` (built as `HaloTag`, 17 Nov 2026) | Outline, Filled, Text, Marker, Status | text | 11 caption, 8 by 2 | `OutlineTag`, `NewsPill`, `RowTag`, `StateMarker`, `StatusPill` (5) |
| 11 | `Segments` (built as `GlowSegments` with `SegmentSize`, 17 Nov 2026) | Regular 44, Compact 36 | options | pad 4 | `GlowSegments` (add 36) (1) |
| 12 | `Switch` (kept as `GlowSwitch`, 17 Nov 2026) | one | | 50 by 30 | `GlowSwitch` (kept) |
| 13 | `Stepper` (built as `HaloStepper`, 17 Nov 2026) | Sheet, Row | value, unit, hint | buttons 44, number 44 or 26 | `MinusPlus`, `BpmStepper`, `GoalStepper` buttons, `ValueStepper`, `LogStepper` (5) |
| 14 | `Sheet` (built as `HaloSheet`, 17 Nov 2026) | Page, Grouped, Info, Token, Confirm | title, note, content, footer | corner 32, top 10 + grabber, side 20 / 16, gap 16 | `PageSheet`, `PageConfirm`, `InfoSheet` frame, `BadgeSheet` frame, `SheetColumn`, `SheetTitle` (6), all gone |
| 15 | `Banner` (built as `HaloBanner`, 17 Nov 2026) | Good, Warn, Problem, Info | lead, title, note, action | 44 tile, 16 / 12 | `StatusBanner`, `ErrorPanel`, `NotSavedNotice`, `StrapHeldNotice`, `Warning`, `InfoLine` (6) |
| 16 | `Note` (built as `HaloNote`, 17 Nov 2026) | Inline, Footnote, Caveat | text, dot | 13 / 12, 4 in | `PageNote`, `PageFootnote`, `GoalNote`, `RowNote`, `TrimNote`, `EditedNote`, `CaveatLine` (7) |
| 17 | `Figures` (built as `HaloFigures`, 17 Nov 2026) | Static, Live | value, unit, label | 28 / 12, gap 4, rule `Greys.Raised` | `DetailFigures`, `SummaryFigures`, `LiveStat` + `LiveValue`, `RecapFigures`, `LoadFigure` (5) |
| 18 | `Progress` (built as `HaloProgress`, `HaloProgressSegments`, `HaloProgressRing`, 17 Nov 2026) | Bar, Segments, Ring | share, colour | bar 6, track `Greys.Raised`, ring 2.5 in rows | `PageProgress`, `ShareBar`, plain bars (4) |
| 19 | `Tile` (built as `HaloTile`, 17 Nov 2026) | Neutral, Ink, Selected, Effort. Sizes 32, 44, 64 | icon, badge | glyph 16, 20, 24 | the 11 icon-on-circle drawings of 3.20 (11) |
| 20 | `Token` and `BigNumber` (built as `TokenSize` and `HaloBigNumber`, 17 Nov 2026) | Token 56 / 72 / 96 / 160. BigNumber sizes as enum | | | `Medal` (kept), `BigNumberText`, `HaloNumber`, `DotFigure`, `HeroNumber` x3 (6) |

Order of migration (each step is a visible win and removes the most drift per line changed):
1. Scales: add `Space`, `Corner`, `IconSize` tokens, then replace literals (10 and 14 first). The grey-panel padding goes to `inset.card` through `Card`.
2. `Row` (3.5): 25 composables go to 1, and it is where a screen visibly changes (tile sizes, dividers, caret colour).
3. `Button` and `IconButton`: delete `WorkoutButton`, `RoundIcon`, `FlatButton`.
4. `Sheet`: corner 32, top inset, badge sheet titles.
5. `Card`, `Title` and `Banner`: panels and headers.
6. `Figures`, `Chip`, `Tag`, `Note`, `Stepper`: small, mechanical.

---------------------------------------------------------------------------------------------------------------------

## 5. Code against boards: where they disagree

Ordered by how visible it is.

| # | Job | Code | Boards | Where |
|---|---|---|---|---|
| 1 | Sheet top corner | Fixed: `Corner.Sheet` is 32 | 32 | `Scale.kt` |
| 2 | Settings and detail panel row inset | Fixed: every row takes `Inset.list` through `LocalRowInset` | 22 to 26 (panel padding derived from its corner) | `HaloRow.kt` |
| 3 | Tab gutter on a 400 dp or wider phone | Fixed: 16 at every phone width | 16 | `GlowLayout.kt` |
| 4 | Solid card smoothing | Fixed: one smoothing, 65 (`CardSpec.SMOOTHING`), for every card | 55 on the HaloSystem board ("smoothing 55"), the code's 65 kept | `CardTuning.kt`, `HaloCard.kt` |
| 5 | Hero title | Fixed: 16 on every `CardTone.Lit` card (`TitleSpec.Hero`) | 16 | `HaloTitle.kt` |
| 6 | List row tile | Fixed: 32 at 16% (`RowSpec.Tile`, `RowSpec.TILE_TINT`) | 32 at 16% | `HaloRow.kt` |
| 7 | List row inset | Fixed: `Inset.list` at the sides, 12 above and below, 56 min height (64 with a line) | 25 to 26, 56 min height | `HaloRow.kt` |
| 8 | Strip height | Fixed: 64 (`CardSpec.StripHeight`) | 64 | `HaloCard.kt` |
| 9 | Row pill height | Fixed: `HaloButtonSize.Small` is 36, the 32 and the three forced sites are gone | 36 | `HaloButton.kt` |
| 10 | Compact button label | Fixed: `HaloButtonSize.Card` is 14 semibold (`GlowType.buttonSmall`) | 14 | `HaloButton.kt` |
| 11 | Main button label | Fixed: `HaloButtonSize.Main` is 17 semibold (`GlowType.buttonMain`) | 17 | `HaloButton.kt` |
| 12 | Waiting dash | Fixed: 28 by 6 in a card's number's place (`BigNumberSpec`, `WaitingDash(onTile = true)`), 14 by 3 in rows and figures | 28 by 6 on a tile | `GlowList.kt`, `HaloBigNumber.kt` |
| 13 | Segmented range on details | Fixed: `SegmentSize.Compact`, 36 high | 36 high, `r 18` | `HaloChip.kt`, `GlowMetricDetail.kt` |
| 14 | Workout block gap | 14 | 12 | `GlowWorkoutMode.kt:196` |
| 15 | Live side buttons and Share | Fixed: circles (`HaloIconSize.Live`, and Share is a pair button) | `r 22` squircles (older boards, which are the ones in error) | `HaloButton.kt`, `live/LiveFrame.kt` |
| 16 | Banner | Fixed: a 44 circle tile on a grey card, the designer's choice (`BannerSpec.Tile`) | 4 px left bar (rules doc B1, overruled) | `HaloBanner.kt` |
| 17 | Sheet title | Fixed: 22 on every variant, centred on a token sheet (`SheetSpec.Title`) | 22 | `HaloSheet.kt`, `BadgeSheets.kt` |
| 18 | Day cells | Fixed: 62 in the strip (`ChipSpec.DayStrip`), 54 in the calendar (`ChipSpec.DayCalendar`), the alarm's days are 44 chips | 62 (strip), 54 (calendar `a` elements at `height 54`, `r 27`) | `GlowMetricDetail.kt`, `DayCalendar.kt` |
| 19 | Divider colour | Fixed: one step above the surface (`OnSurface.line`) on every row, card divider and figure rule | one step above the card | `HaloRow.kt`, `theme/Surfaces.kt` |
| 20 | Chip rings | Fixed: 1.5 everywhere (`ChipSpec.Ring`) | 1.5 | `HaloChip.kt` |

Board-side drift (for the designer, not the code):
- Corner radii on boards are many different px (62, 64, 59, 58, 45, 41, 39, 27 ...) because they were set by hand per board from the percent rule. They are
  all one rule in code, so the boards have no mistake, only a wide spread.
- Several older boards still draw 60 and 64 buttons as `r 22` squircles (summary and live) while the newer rule (16 Nov 2026) says round.
- HaloSystem says "corner 36%, smoothing 55". `GlowLayout.SMOOTHING` is 65.
- Hero inner gap is 10, 12, 14 or 16 on boards of the same family.
- Rules doc B1 says banners have a left bar, the HaloYou board and the code use a circle.
