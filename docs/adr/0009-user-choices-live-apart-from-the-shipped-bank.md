# What a lifter decides about an exercise is stored apart from the exercise, and a set records only what it departed from

The `exercise` table ships inside the APK and is opened with Room's `createFromAsset()`. A bank update replaces those rows wholesale, matching on upstream id (ADR 0002). So **nothing a lifter chooses may live in that table**: the display unit an exercise is read in, and the persistent note and progression override that follow it, go in `exercise_preference`, keyed by exercise id.

A row exists only where something has actually been chosen. Absence is the answer "nothing yet", not missing data — which is why the shipped asset carries the table empty and why 896 rows need no maintenance to mean "kilograms".

## Why not a column on `exercise`

A `display_unit` column would work for exactly as long as the bank never updated. The first release that improved a row would overwrite the lifter's choice on it, and the failure would be invisible: the app would keep working, in the wrong unit, with nothing to point at. The user story is explicit that improving the app must never cost the lifter their data (spec #24, story 11).

It would also cost a build step. The generator fills every column the Room schema declares and fails the build on one it does not recognise, so the column would force a decision about what value ships in it for every exercise — a choice that only the lifter can make.

## Why a set's unit is null unless it departed

A set records a display unit only where the lifter overrode the exercise's — the one machine in the corner labelled in pounds at a gym of kilogram ones. Everywhere else it is null, meaning "reads as its exercise does".

Storing the unit on every set would freeze each one's display at the moment it was logged. Switching an exercise to pounds would then leave a trail of kilogram sets behind it, and the history a lifter is meant to compare would be written in two units at once. Null is what lets one toggle move a whole history's display without touching a single stored weight.

The consequence is that **promoting a unit to the exercise clears the overrides that now agree with it**. A set logged in pounds before the exercise read in pounds is no longer a departure from anything, and leaving the override on it would reintroduce exactly the trail this shape exists to prevent.

## Consequences

- **The join is the read path.** `ExerciseWithPreference` carries the bank row and the choice together, so nothing downstream has to remember to look a preference up, and an exercise with none is the ordinary case.
- **Weights are untouched by all of it.** `weight_kg` is canonical kilograms and every unit here is a reading of it. Nothing in this decision can change a number in the log.
- **The bank-update path inherits a rule rather than a problem.** When updates arrive (#40) they match on upstream id and rewrite `exercise` rows freely, because there is nothing of the lifter's in them.
