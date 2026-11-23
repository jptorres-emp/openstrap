# Halo type scale

Read from the boards HaloSystem, HaloTodayMidday, HaloRecovery, HaloDetailRecovery, HaloDetailSteps, HaloReadyRun,
HaloLiveRun, HaloSummaryRun, HaloProfile, HaloQuietHoursSheet, HaloGoalMoment and HaloAllBadges. Sizes are px on the
boards and sp in code. The boards set no letter spacing anywhere. Greys: note #9AA0AA, footnote #6E7480.

Families: Funnel Display for titles, sentences and figures, Funnel Sans for the rest. Theme.kt already carries Display
300 to 700 and Sans 400 to 700, so the boards' 300, 400, 500 and 600 need no font change. The one big number is DotSpec,
not the boards' Doto.

| Role | Family, weight | Size on line | Colour | GlowType |
|---|---|---|---|---|
| Top bar and sheet header title | Display 600 | 17 | white | pageTitle (was 400, 24 on 28) |
| Header date | Display 600 | 20 | white | headerDate (was 500) |
| Sheet question | Display 600 | 22 | white | sheetTitle (new) |
| Verdict heading | Display 600 | 24 on 1.1 | metric colour | verdict (new) |
| Moment headline | Display 600 | 26 | white | momentTitle (new) |
| Hero sentence | Display 300 | 21 on 1.28 | white | sentence (was 20 on 24.4, tracked) |
| Detail sentence | Display 300 | 19 on 1.35 | white | detailSentence (new) |
| Card title | Sans 600 | 15 | white | cardTitle (was 500, 17 on 22) |
| Hero title row | Sans 600 | 16 | white | heroTitle (new) |
| Row name, body | Sans 400 | 15 | white | body (same) |
| Row value | Sans 400 | 15 | white 0.8 | rowValue (was 500, 20 on 24), ROW_VALUE_ALPHA |
| Section label, note | Sans 400 | 13 | #9AA0AA | secondary, alpha 0.72 to 0.62, noteColor |
| Small note, badge name | Sans 400 | 12 on 1.3 | #9AA0AA or white | noteSmall (new) |
| Footnote | Sans 400 | 12 on 1.45 | #6E7480 | footnote (new), footnoteColor |
| Caption, axis, time | Sans 400 | 11 | #9AA0AA | caption (was 500), alpha 0.5 to 0.62 |
| Primary button | Sans 600 | 16 | black or white | button (new) |
| Small button | Sans 600 | 14 | black or white | buttonSmall (new) |
| Figure | Display 300 | 28 on 1 | white | figure (new), smallValue is the same object (was 34) |
| Large figure | Display 300 | 40 on 1 | white | figureLarge (new) |
| Inline number, live | Display 300 | 20 | white | figureInline (new) |
| Wheel far, near, chosen | Display 300 | 24, 32, 48 on 1.2 | white 0.18, 0.4, 1 | wheelFar, wheelNear, wheelChosen (new) |
| Stepper value | Display 300 | 44 | white | goalValue (same, rewards rules) |
| One big number | DotSpec | Doto 76, 72, 68, 64, 52, 44, 40 | white or tint | BigNumberText with BigNumber |

## The one big number

`BigNumberText(text, BigNumber.X)` in GlowType.kt draws DotSpec dots sized to the board's Doto size. The matrix is 9.28
dots tall and a Doto digit is about 0.7 em tall (an estimate, no Doto file is in the repo), so dot = 0.7 x Doto / 9.28.

| BigNumber | Doto | Dot | Screens |
|---|---|---|---|
| Live | 76 | 5.7 dp | live workouts (HaloLiveRun and the other Live boards) |
| Detail | 72 | 5.4 dp | detail headers (HaloDetailSteps), summaries (HaloSummaryRun) |
| Hero | 68 | 5.1 dp | Today hero (HaloTodayMidday) |
| Feature | 64 | 4.8 dp | Recovery hero, goal moment, ready screens |
| Second | 52 | 3.9 dp | score in a detail's arc, second card |
| Tile | 44 | 3.3 dp | lit tile, "3 of 4" beside a ring |
| Small | 40 | 3.0 dp | small card |

The old DotSize values (Edge 3, Second 3.6, Hero 4.5, Detail 6, Live 9) stay for the widgets. Screens move to
BigNumberText when they are ported.
