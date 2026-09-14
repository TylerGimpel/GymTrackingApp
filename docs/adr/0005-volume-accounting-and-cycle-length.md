# Volume counts secondary muscles at half, paired with Pelland's tiers — and divisibility constrains cycle length, not training frequency

Two conventions that are easy to break by accident, recorded because breaking either produces a generator that looks like it works and silently prescribes the wrong amount of training.

## Volume accounting: fractional, and its numbers travel with it

A set counts **1.0** toward its primary muscle and **0.5** toward each secondary muscle. Volume targets come from **Pelland's tiers**, not from Baz-Valle's 12–20.

These two choices are a **matched pair and must not be mixed.** The literature has two incompatible conventions: Pelland found Bayesian support for fractional counting, while Schoenfeld et al. 2019 recommends 1:1 for practitioners precisely *because* the landmark target numbers assume direct counting. The two most-cited target sets are therefore expressed in different units, and the research measured the difference at roughly **30% of every volume number**.

Taking Baz-Valle's 12–20 while counting fractionally would systematically over-prescribe. Taking Pelland's tiers while counting 1:1 would under-prescribe. Either mistake is invisible in the code and only shows up as users being persistently over- or under-trained.

Fractional was chosen because it has the better evidence and because it is more honest about what the app can observe: a bench press does work triceps, but not the way a triceps extension does. Counting indirect work at full weight would let the generator believe a chest day had already satisfied a user's triceps volume.

## Divisibility constrains cycle length, not weekly frequency

The rule `N mod R == 0` — where `R` is the rotation length (full body 1, upper/lower 2, PPL 3, body-part 5) — applies to the **number of positions in the cycle**, and to nothing else. Weekly cadence is a separate, unconstrained input that exists only to translate per-week volume targets into an unanchored cycle.

This matters because "how many days is your routine" reads as both, and conflating them makes valid routines look invalid. *"Five days a week, upper/lower"* is entirely sound: a **2-position cycle** trained at cadence 5. Because the cycle is not anchored to a calendar, that automatically produces `U/L/U/L/U` then `L/U/L/U/L` with no two-week template and no special case — the thing a week-anchored app needs explicit structure for falls out of the model here for free.

Only a literal request for a 5-*position* cycle hits the divisibility constraint.

## Consequences

- **Setup never asks for a cycle length as a number.** It asks weekly frequency, which everyone can answer and which volume translation needs, then offers split *patterns* by shape — "alternate upper and lower" versus "upper, lower, upper, lower, full". The user recognises a shape; the app derives a divisible cycle length. Invalid combinations never appear as options rather than being rejected after the fact.
- **Gaps in the divisibility grid are a prompt to author a variant**, not a wall. A genuine 5-position upper/lower need is met by a named `U/L/U/L/Full` template, which is what people actually run.
- **Exercises per muscle per session (2–4) is invented.** The research found zero dose-response data — only *sets* have been studied, never exercises-per-muscle. It is derived from how the set budget divides, and is recorded as a judgement call, like the layoff decay rate.
- **Splits are presented as preference, not prescription.** Every volume-equated split trial found no difference, so the generator must not claim any split is superior.
