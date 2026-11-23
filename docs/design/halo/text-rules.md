# Halo text rules

Approved 17 Nov 2026. Board HaloTextRules on the Halo canvas. Written from a sweep of every text slot in the
app (about 3,100 English strings, about 270 flagged). Settles group 2 of the board audit (commas, full stops, names,
numbers).

The idea: every place text is drawn is one of three kinds of slot, with a size budget. Words go where there is no room,
sentences only where there is. When text doesn't fit its slot, the words change, never the slot.

## T1. Three kinds of slot

| Kind | What goes in | Ends with | Slots |
|---|---|---|---|
| Word | a name, a value or one state word, at most 3 words | nothing | card title, small card status, chip, tag, figure label, row value, button, tab, segment |
| Phrase | one fact, at most about 6 words, parts joined by a comma | nothing | row subtitle, title-row note, hero side line, wide card status, banner note, notification text, ready hero line |
| Sentence | one or two whole sentences | a full stop | hero sentence, detail sentence, footnote, info sheet, sheet note, empty page, confirm dialog |

A sentence never goes in a word or phrase slot. When the reason matters, the slot gets the word and the sentence moves
to where the card opens: the detail headline's context line, then its sentence.

## T2. Size budgets

Characters at the default font size on a 360 dp phone. A slot never ellipsizes words: text that doesn't fit is
rewritten. Titles are the one exception that wraps: a name is a name, and other languages run longer, so a title may
take a second line (Active minutes does in English).

| Slot | Lines | Budget |
|---|---|---|
| Card title, half card | up to 2 | 12 a line, written to fit one |
| Card title, full card | up to 2 | 24 a line, written to fit one |
| Small card status, under a number | 1 | 18 |
| Small card status, no number | 2 | 18 a line |
| Wide card status, row subtitle | 1 | 36 |
| Title-row note (right) | 1 | 20 |
| Hero side line | 1 | 20 |
| Figure label, row of three | 1 | 12 |
| Row value beside a name | 1 | 16 |
| Chip, tag | 1 | 14 |
| Button | 1 | 20, a verb first |
| Banner title / note | 1 / 1 | 24 / 40 |
| Sheet title | 1 | 28 |
| Hero and detail sentence | up to 3 | 110 |
| Footnote, sheet note | up to 2 | 100. Longer goes to the info sheet |

## T3. One state, one wording

Each state has a word form, a phrase form and a sentence form. Every slot takes the form of its kind.

| State | Word | Phrase | Sentence |
|---|---|---|---|
| Learning | Learning | Learning, 5 of 7 nights | Still learning your nights, 5 of 7. Your score starts after 7. |
| Nothing yet today | No reading yet | No reading yet | No reading yet today. |
| No sleep last night | No sleep | No sleep recorded | No sleep recorded last night. Was the strap on? |
| Waits for something | After a night | After your first night, After a workout | Shows after your first night with the strap. |
| Couldn't work it out | Unavailable | Couldn't work it out | Couldn't work out today's Recovery. Try again later. |
| Strap away | Not connected | Not connected, last seen 18 min | The strap isn't connected. It was last seen 18 min ago. |
| No strap | No strap | No strap paired | Pair a strap to start. |
| Off | Off | Off | (the setting's own note) |
| Goal met | Goal met | Goal met, 9,120 steps | You met your goal. |
| Partial day | Partial day | Partial day, up to 09:40 | (detail context) |

A missing value inside a number or figure is the dim dash, never words at figure size. A detail headline with no value
shows the state word large ("Learning") with the phrase under it (detail rules).

## T4. Joining and punctuation

- Phrase slots join parts with a comma: "On, last copied 10:42", "Learning, 5 of 7 nights", "185 bpm, set by you".
  Never a full stop inside a phrase.
- Word and phrase slots end with nothing. Sentence slots end with a full stop. Two thoughts are two sentences.
- No ·, ; or em dash, no ellipsis, no colon except in times, no exclamation marks.
- Never build a line by gluing strings in code (`+ " " +`, `joinToString(". ")`). One string per line, with
  placeholders, so a translator sees the whole line.
- Tap words: say what happens ("Try again"), never "Tap to try again".

## T5. Numbers, units, times

- Text: the language's grouping ("8,432", "23,410"). Dots: up to four digits together, five and up with a thin space
  ("26 120"), as the dot rule says.
- A space before a unit ("72 kg", "58 bpm", "7 h 40"), none before %, ′ ″ or /km ("86%", "5:24/km").
- A value always carries its unit, except in a figure whose label names it ("Avg bpm").
- Counts: "5 of 7", never "5/7". Ranges: "52 to 60 bpm", never a dash.
- Durations: "7 h 40" in text, "7:40" only on a running clock or a pace.
- Times: 24 hour, "06:30". Days: "Today", "Yesterday", "Tue 10 Nov", "10 Nov 2025" in another year. With a time:
  "Yesterday 10:42", "Tue 10 Nov, 10:42".
- Labels say "Avg", lines and sentences say "Average".

## T6. Names and case

- The device is "the strap" in text and "Strap" as a label. "Helio Strap" only on pairing and the strap card.
- The app names itself only where it has to be told apart from Android or the strap: permissions ("unstrap needs
  Nearby devices"), updates, privacy, and who worked a figure out (unstrap or the strap). Never in a status, a note or
  a score's line: "Still learning your nights", not "unstrap is still learning". Always lower case, "unstrap".
- Scores and features are names, capitalised everywhere: Recovery, Energy, Load, Habit Age, Sleep score. Measures are
  plain words, lower case in a sentence: heart rate, steps, heart points, active minutes.
- A sport's name is capitalised as a name ("Outdoor run") and lower case inside a phrase ("Start outdoor run").
- Zepp is "Zepp" for the account and service, "the Zepp app" for the app.
- Sentence case for every label and button. "Done", never "OK". British spelling.

## T7. Where the explanation goes

1. Small card: the word ("Learning").
2. Wide card and row: the phrase ("Learning, 5 of 7 nights").
3. Detail headline: the word large, the phrase under it.
4. Detail sentence: why, and what happens next.
5. Info sheet: how it works.

Notifications follow the same ladder: the title names what happened, the text is one phrase, the action is a verb.

## T8. Checked by a test

A copy test reads the English strings by slot (from their name prefix and a slot list) and fails on a string over its
budget, a sentence in a word or phrase slot, a phrase ending in a full stop, a sentence without one, or "/" in a count.

## Settled

1. Titles may wrap to two lines. "Active minutes" keeps its name.
2. British spelling: the sport is Football, not Soccer.
