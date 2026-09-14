# The exercise bank ships structured fields from free-exercise-db, and deliberately omits its prose and images

The preloaded bank is built from [`yuhonas/free-exercise-db`](https://github.com/yuhonas/free-exercise-db) (Unlicense, 876 rows), taking **only the structured fields** — name, muscles, equipment, mechanic, force, category, level. Its **images and instruction prose are deliberately excluded**, and the gaps are filled by hand-authoring rather than by merging another dataset.

## Why the omissions

The images are confirmed scraped: the upstream project states in writing that they were taken from the internet and advises against commercial use, and the repo owner has said he does not know their origin. The Unlicense is only as good as the uploader's right to grant it, and that right was demonstrably absent for part of the repo — which contaminates confidence in the **prose** too, since it came down the same pipeline and could not be ruled out as scraped.

Structured fields are different: exercise names, which muscle a movement works, what equipment it needs, compound versus isolation. These are facts and short phrases, which copyright does not protect. The prose is expressive writing and is the only part carrying real risk.

**Consequence: the app shows no how-to text for an exercise.** This is intentional, not an oversight and not a TODO. Anyone tempted to "fix" it by pulling in the upstream `instructions` field would be reintroducing the exact risk this decision exists to avoid. Hand-authored text is fine; scraped text is not.

## Why not merge another dataset

RepDB has better coverage and cleanly licensed images, but its licence is a bespoke vendor contract with a no-derived-dataset term. Deriving shipped metadata from it while claiming the Unlicense would not be honest, so using it means taking it under its own licence and attributing it — losing the clean public-domain position for the whole bank. Hand-authoring the gaps is legally unambiguous and keeps one licence covering everything.

## Consequences

- **Machine coverage is the real gap, and it is filled by hand.** The dataset is barbell-and-dumbbell-heavy: 67 rows tagged `machine` against 170 barbell and 123 dumbbell, with zero entries for Nautilus, Cybex, Life Fitness, Precor, pec deck or belt squat. It also predates the glute-training era — 22 exercises with glutes as primary, 3 hip thrust or glute bridge variants. Roughly 40–60 generic machine entries and 15–25 cardio entries are authored to compensate.
- **No brand names in the bank.** "Hammer Strength Iso-Lateral Row" is wrong at every gym with a different manufacturer. Generic names plus user-added exercises handle brand specifics.
- **Movement pattern is not carried.** Routine balance is checked by muscle coverage instead, which runs on the one field at 100% fill.
- **Goal fit is derived, not stored**, so it can be corrected globally rather than across 678 hand-tagged rows.
