# Wave 3: Load's usual range as two edges

Boards HaloSummaryRun and HaloPastWorkout print "Load, usual 80 to 120". Plan section 4 and open call 4.

## Rule
- The middle of the range is the usual workout Load develop already has: the median Load of the latest 20 workouts
  with one (`Workouts.usualLoad`, 3 needed).
- The edges are that usual minus and plus 25 per cent, the same share `LoadBands.BAND` gives the day Load band
  and the "Above usual" / "Below usual" tag. So a Load inside the printed edges is never tagged off usual.
- Rounded to whole numbers, low and high each to the nearest. A usual of 100 reads "usual 75 to 125".
- Pure function in data/LoadBand.kt (`WorkoutLoadBand`), unit tested. It calls `Workouts.usualLoad`, no edit to Workouts.kt.

## Too few workouts
- Under 3 workouts with a Load there is no usual and no band: the card keeps its plain "Load" label and draws no band.
- If the rounded edges meet (a tiny usual), the label shows the single number, as `LoadUsual.words` already does.

## Where it shows
- Summaries and past workouts: `LoadUsual` is filled with the two edges (label under Load, band on the bar).
- Activity card and Load detail: these are the day's Load, whose band is already two edges from `LoadBands.usualRange`
  (detail "Your usual, 30 to 50"). The boards print no range on the Activity card (it says "Learning your usual" until
  a week), so only the card's line is changed: "Your usual is 40" becomes the range, from the same band.
- en and zh-rCN strings.

## Tests
- WorkoutLoadBandTest: edges of a median, rounding, fewer than 3 loads, zero and missing loads ignored, edges
  agree with `LoadBands.of`.
