# Wave 3: Most rounds personal best

Plan section 4, second row (board HaloGoalsBests, HaloBestSheet, HaloBestMoment).

## Where rounds come from
- A phone-run HIIT or interval workout writes one `app_workout_round` row per round (`AppWorkoutRound`: work start, work end, rest end, peak). A round is finished when `workEnd` is set.
- The workout's `structure` code holds only the planned count, so the best uses finished rows, not the plan.
- Strap-recorded workouts and phone workouts from before the rounds table (version 25) have no rows. They are not counted. Nothing else can recover their rounds.
- A trim: only rounds whose work ended inside the shown start and the end count, same idea as `stretchesInside`.

## Database
None. `app_workout_round` and `HealthDao.roundsAll()` already exist. `RewardsKeeper.facts` reads them and passes a count to `Rewards.Done`.

## The kind
- `Rewards.Kind.MOST_ROUNDS = "most_rounds"`. `Done.rounds: Int = 0` (default, so existing callers compile).
- `bests()`: the workout you started with the most finished rounds (above 0). A tie keeps the first. `newBests()` needs no change, it works on any kind.
- Only structures that are not game periods count (the keeper skips `Structure.Periods`).
- Title "Most rounds", value "12 rounds" via a plural, beat note "3 more rounds", beat line "3 more rounds than your best from August."
- zh-rCN strings alongside.

## Look
- Colour: the HIIT colour `IntervalInk` (#FF5A5F, board rewards-settings-rules). Emblem `Emblems.Interval`, rosette, no label.
- Order on the bests grid: after the longest workout, before the lifts.
- Leading new best: after the longest workout (last).
- Empty state keeps its three kinds.

## Tests
- RewardsTest: best is the highest round count, tie keeps first, workouts without rounds are ignored, a new best beats the old one, a first is not a new best.
- RewardsCopyTest: title, value, beat line.
- RewardsKeeper: counts finished rounds only, in the shown span.
