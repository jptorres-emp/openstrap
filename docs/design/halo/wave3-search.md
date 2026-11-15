# Wave 3: search on All metrics

Board: HaloAllMetrics ("Search metrics" field under the title). Field look: Choose's search field (48 dp, Control surface, search icon, clear mark).

- Matches: a row's metric name, and its group's title (Recovery, Sleep, Heart, Body signals, Activity).
- Matching: the query is trimmed, lowercased and stripped of accents (NFD, marks removed). It is split into words; a row matches when every query word is found inside some word of its name. "rate" finds Heart rate and Breathing rate, "hr" finds nothing unless a name has it, "heart" finds Heart rate and Resting heart rate.
- Group title: a query that matches a group's title (every word inside the title) shows that whole group, so "body" lists all Body signals.
- Filtering: groups keep their order; a group with no matching row is hidden. A blank query shows everything.
- Empty results: one quiet secondary line, "No metric called X" (X is the trimmed query as typed), in place of the groups. Field stays.
- Keyboard: the field is not focused when the sheet opens (the index is the main use), single line, Search action hides the keyboard. Tapping a row hides it too, since the sheet closes.
- Back: the sheet's own back closes it; a BackHandler in the sheet, enabled only while the field has text, clears the field first.
- The clear mark in the field also clears it.
- Strings: en and zh-rCN for the hint and the empty line; the clear label reuses glow_choose_clear.
- Code: pure `filterIndex(groups, query)` in AllMetricsScreen.kt; the field is a private copy of Choose's look.
- Tests (AllMetricsTest): case, accent, any word, multiple words, group title shows whole group, empty groups hidden, blank returns all, no match returns empty, Chinese name substring.
