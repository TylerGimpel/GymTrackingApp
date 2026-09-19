# The anchor is recomputed from the log, today's sets are not part of it, and Epley's factor takes the load's sign

ADR 0004 settles what the recommendation engine computes. Building it raised three questions it does not answer: where the estimate lives, which sets it reads at any given moment, and what Epley means on an exercise whose load runs backwards. All three are decisions a future reader could plausibly reverse, and two of them would be silent if reversed wrongly.

## The anchor is stored, and recomputed from the log every time

Spec #25 asks for the anchor, the rep range and the working weight to be held as state per exercise, and `exercise_progress` holds them — out of `exercise`, which a bank update replaces wholesale (ADR 0009), and out of `exercise_preference`, which is what the lifter chose rather than what the app worked out.

What is *not* done is accumulate it. Each recommendation folds that exercise's logged sessions again and writes the result, rather than nudging the stored number with whatever was just logged. Folding costs one query; accumulating costs correctness in three places:

- **A mis-logged set heals.** A typo of 600 kg corrected an hour later leaves an accumulated estimate wrong for good, and the lifter has no way to reach it. A recomputed one is right again the moment the log is.
- **An installed copy needs no backfill.** The migration that adds the table creates it empty, because a phone carrying two years of training arrives at the same numbers as a fresh one the first time it asks.
- **The estimate stays drawable.** ADR 0008 wants the smoothed anchor plotted as a line across a history. A fold over sessions is that line; a running total is a single number with no working shown.

The stored row is therefore what the engine currently makes of an exercise, not an independent source of truth — with one exception, which is the point of storing it. The **rep range** is not derived from anything: it is a decision, seeded by the generator from the routine's goal and rep preference (#26), and the default until then.

## The session being logged into is not history

A recommendation is computed from every session an exercise has appeared in **except the one currently open**. Otherwise a good first set raises the weight for the second set of the same session, which is precisely what "advance only after the top of the range is beaten on two consecutive sessions" exists to prevent — arrived at sideways, within minutes, on the strength of one set.

The consequence is that the prescription for an exercise is stable for as long as the lifter is in the gym, which is also what the wheel (#53) needs: every set of the exercise shows the same target, and what changes is which of them are done.

## Epley's factor takes the load's sign

Epley is stated for a weight on a bar: `w(1 + r/30)`, monotonically up as the reps climb. An assisted machine's load is negative, because help is weight taken off the lifter, and more reps at a given setting means needing *less* help for a single one — so the estimate has to move toward zero rather than away from it. Multiplying a negative load by `1 + r/30` moves it the wrong way and would have the app confidently coaching somebody to get weaker, which ADR 0004 names as the worst failure available to this engine.

The formula used is `L + |L|·r/30`. For a weight on a bar it is Epley exactly. For a machine that is helping it is Epley read in the direction that exercise runs, and the same one line inverts the prescription, the rounding and every comparison built on them without anybody having to remember to flip anything.

It is **not** a physical model of an assisted lift. That would need the lifter's bodyweight and a leverage ratio the manufacturer does not publish. What a prescription needs from it is that more work reads as more progress and that the arithmetic stays monotone over the reps an estimate is ever taken from, which is what the sign gives it.

## Consequences

- **The smoothing factor is invented**, like the layoff decay rate ADR 0004 warns about. A third of the way per session is chosen for behaviour, not derived from anything, and must never be presented as a finding.
- **Rounding is signed too.** Rounding down in signed terms rounds a barbell to the lighter plate and an assisted machine to *more* help, which is the conservative answer in both directions.
- **A recommendation costs a read of one exercise's sets.** Low hundreds of rows for a heavily trained movement after years of logging, and the query is indexed by the exercise. If that ever stops being true, the fix is to cache the fold rather than to accumulate it.
