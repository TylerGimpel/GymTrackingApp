# The analytics deliberately omit two things: volume before the import seam, and any tally of what the user didn't do

Both omissions look like gaps. Both are load-bearing, and both are the kind of thing a later change would "fix" into something worse.

## Volume charts start at the import seam; strength charts don't

Imported FitNotes history records every set's weight and reps accurately, but **not what the set was for** — FitNotes never had warm-up or drop sets, so an unknown number of warm-ups are sitting in the history typed as working sets.

That splits the two chart families:

- **Strength history spans the whole record.** The estimated 1RM anchor reads top sets, and a top set is a top set regardless of what the sets around it were. The author's history goes back to February 2024 with no caveat.
- **Volume history begins where reliable data begins**, with one line explaining why.

The tempting "fix" is to plot volume across everything. Don't: the imported portion is inflated by an unknown amount, so the chart would **step down on the day the user switched apps** and read as a training collapse that never happened. There is no correction available — the missing information was never recorded. Drawing nothing is the only honest option.

Note this is a property of **the source**, not of importing. A Hevy import would carry real set types and its volume would be trustworthy, so the boundary is "where unreliable data ends", not "where the import happened".

## No metric counts what the user failed to do

The app knows a lot about skipped work: skipped routine days, skipped exercises within a session, how often a recommendation was overridden. **None of it is surfaced as a statistic.**

The test applied throughout is what a number is *for*:

- *"Legs: 12 days ago"* helps decide what to train next. **Kept.**
- *"You've skipped legs 4 times this month"* is a scoreboard of failures. **Omitted.**
- *"You beat the recommendation 62% of the time"* is unactionable. **Omitted**, though the same data drives an actionable prompt — *"you've overridden this every session for a month, reset the estimate?"* — which is the engine admitting it is wrong rather than grading the user.

This is the third decision to land the same way, and the consistency is the point: the cycle model rejected catch-up logic and guilt UI, the swap machinery suggests rather than acts on repeated skips, and the calendar shows what the user *did* — sessions and cardio days — never a mark for a day they skipped.

A training log that tallies failures is a different product from one that records training, and it is not this one. The erosion happens one reasonable-sounding feature at a time: a streak counter, an adherence percentage, a missed-workout badge. Each is individually defensible and collectively turns the app into something the author did not ask for.

## Consequences

- Per-muscle volume remains the **generator's working data**, not a user-facing dashboard. It is the input to a decision the app already makes.
- Relative strength (strength per bodyweight) is used internally for cold-start estimation and is not shown; it is a vanity metric with no action attached.
- Session duration mattered **live** — "how long am I here for" — and is not kept as a trend.
- Personal records are **recomputed from imported sets** rather than importing FitNotes' own flags, so one definition of "best" applies across the whole history instead of two incompatible ones meeting at the seam.
