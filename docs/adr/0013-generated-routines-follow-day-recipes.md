# Generated routines follow day recipes from proven programs, filled with ranked common lifts

The generator used to fill each **Split shape** day muscle by muscle, taking any exercise that trained the muscle primarily, compounds first and then alphabetically by id. The id order was only ever meant as a tie-break, but in an 896-row bank it decided almost everything: Around The Worlds for chest, a plyometric bound for quads, a clean for hamstrings, Knee Circles for calves. The fix chosen is that **a generated day follows a Day recipe taken from real programs, and each Slot in it is filled with the best-ranked Common lift the gym can do.** The structure comes from proven routines, and the lifts from the lifter's own gym.

## What was rejected

**Filtering by the bank's own tags.** `level` and `category` look made for this and aren't. Deadlift, Romanian deadlift, face pull and hip thrust are tagged intermediate, front squat is tagged expert, and oddities like the Anti-Gravity Press are tagged beginner. Several Olympic lifts are tagged `strength`, and the barbell hip thrust carries `powerlifting`. Any rule built on those tags would drop staples and keep oddities. The ranking has to be written by hand, from what programs actually use (`docs/research/common-routines.md` on `research/common-routines`).

**Ranking lifts per muscle, and nothing else.** That fixes which lift, but not what a day looks like. Filling by muscle alone can give a pull day three rows and no pulldown, because the exercise model has no movement pattern and "back" is satisfied either way. Real programs agree on the shape of a day: a Pull day is a vertical pull, a horizontal row, a rear-delt lift, then biceps. The recipe is what carries that.

**Shipping named programs as templates** (Reddit PPL, GZCLP) with their exact lifts. It only fits a lifter whose split, days a week and gym all match one program. Everyone else would need un-templating, and so would every Build it again and every equipment change. That's the reason the day structure was already a **Split shape** rather than a template: equipment varies at every gym.

## What was decided

- **A Day recipe per kind of day**, as ordered **Slots** named by movement pattern. The first few are always filled. The rest are added only as the day's set budget needs them (ADR 0005 still decides how much), and are dropped last-first when the day would run more than 10% past the session length. The recipe's order is the training order for every goal, because every surveyed program puts compounds first whatever it is for.
- **Common lifts are ranked by how many of the ten surveyed programs use them.** A slot takes the best-ranked one the gym has. If none fits, it falls back to any other lift for the muscle outside the never-generated groups, beginner-tagged first. If nothing fits at all, the muscle is left without a lift of its own and the lifter is told, as before.
- **Olympic lifts, plyometrics, strongman, stretches, unstable-surface work and specialty lifts are never generated or suggested, for anyone.** Search still finds them, because typing a name is the lifter choosing it.
- **Experience only widens the choice.** An advanced lifter is offered a second tier of common lifts (front squat, Pendlay row and the like) in Build it again and in the picker's same-muscle list. It never changes anyone's first routine. Capping lifts per day for new lifters, as novice programs do, was rejected: it would cut the weekly volume ADR 0005 budgets, and the beginner programs use the same core lifts as the rest.
- **The picker orders by the same ranking.** The picker and generator already share one rule for what's worth offering so that they can't drift apart, and the ranking follows that rule.
- **Pins and Build it again work per slot.**

## Consequences

- A slot's lift is only as good as the ranking and the recipes. Both are hand-authored data, and both need revisiting if the bank gains a lift that programs commonly use.
- Routines already built the old way are offered one rebuild, with Undo. Hand-built routines are never touched. A rebuild changes lifts, not days, so it is not a **Revision** and the cycle carries on (ADR 0006).
- The ~45 bank rows the research mapped are renamed to plain names in the shipped bank data, with the old names kept as search aliases, so a lift has one name everywhere rather than a plain one only when the generator chose it. Being shipped data, a rename is replaced like any bank row on update (ADR 0002), and whatever the lifter has chosen about an exercise is kept apart from it (ADR 0009).
