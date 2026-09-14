# FitNotes export: verified against a real file

Resolves the open items from [#6](https://github.com/TylerGimpel/GymTrackingApp/issues/6) using the author's own export, obtained via [#8](https://github.com/TylerGimpel/GymTrackingApp/issues/8). Everything below was read off the actual files, not inferred.

**Source files are personal training data and are deliberately not committed to this repo.** They live outside it; this document records only format facts.

## CSV — Gen 2 confirmed

```
Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
```

| Property | Verified value |
|---|---|
| Generation | **Gen 2** (`Weight` + `Weight Unit`, not `Weight (kgs)`) |
| `Comment` column | **Present** |
| BOM | **None** — file begins `Date,` |
| Line endings | **LF** (`\n`), not CRLF |
| Date format | `YYYY-MM-DD`, date-only |
| Weight format | Decimal point, one place (`8.0`, `22.5`) |
| `Weight Unit` spelling | **`kgs`** (lowercase, plural) |
| Empty `Comment` | Rendered as `""` — a quoted empty string |
| Other empty fields | Bare empty, no quotes |
| Granularity | One row per set |

## Two research predictions were wrong

Both came from a 2016-era export and do **not** hold for current FitNotes:

1. **Bodyweight sentinel is `0.0`, not `1.0`.** Zero rows carry weight `1.0`. Unweighted movements (`Plank`, `Crunch`, `Lunges`, `Bulgarian Split Squat`) store `0.0`.
2. **Timed exercises do not hide seconds in the reps column.** They use the dedicated `Time` column in `H:MM:SS` form (`0:00:40`), with `Reps` left **empty**. The database backs this with a real `duration_seconds` integer. `Reps` and `Time` are mutually exclusive.

An importer built to the old predictions would mis-read every timed set and invent a phantom 1kg load.

## `.fitnotes` backup — plain SQLite, and strictly richer

Standard `SQLite format 3`, `user_version = 22`. 24 tables. `training_log` holds one row per set and matches the CSV's row count exactly, so **no sets are lost either way** — but a great deal of structure is.

### What the CSV drops, measured on this file

| Data | In DB | In CSV |
|---|---|---|
| **Supersets** (`WorkoutGroup` / `WorkoutGroupExercise`) | **98 groups** — 88 pairs, 7 triples, 2 quads, 1 quintuple | **Gone** |
| `is_personal_record` | **114 sets** flagged | Gone |
| `is_complete` | 98 sets | Gone |
| `timer_auto_start` | 340 sets | Gone |
| Per-exercise `weight_increment`, `default_rest_time`, `notes`, `weight_unit_id` | Columns exist | Gone |
| Plate calculator config (`Barbell`, `Plate`) | Populated | Gone |
| `exercise_type_id` | 3 values | Gone |

Supersets and PR history are the losses that matter. Both are irrecoverable from the CSV.

### Parsing traps

- **`metric_weight` is declared `INTEGER` but stores `REAL`.** SQLite's dynamic typing lets `22.5` sit in an `INTEGER` column; `typeof()` returns both `integer` and `real` across the table. A strictly-typed reader will break on half-kilo plates. Values are **plain kilograms**, unscaled (range 0–190) — there is no grams/centi-kg encoding to undo.
- **`training_log.unit` is not the weight unit.** Every row is `0` here. The per-exercise weight unit lives on `exercise.weight_unit_id`. Confusing the two silently corrupts every load.
- **`Comment` count differs between formats** — 136 rows in the DB, 123 in the CSV. The gap is exactly 13 comments whose `owner_id` references a deleted `training_log` row. Orphans, not a lossy export.

### `exercise_type_id` maps to logging type

| Value | Count | Exercises |
|---|---|---|
| `0` | 116 | Standard weight × reps |
| `1` | 8 | `Cycling`, `Walking`, `Rowing Machine`, `Stationary Bike`, `Swimming`, `Running (Treadmill)`, `Running (Outdoor)`, `Elliptical Trainer` |
| `3` | 2 | `Plank`, `Side Plank` |

FitNotes has three logging types; this app's five are a superset, so the mapping is lossless inbound.

## Still unverified — and now known to be unverifiable from this file

**Cardio rendering.** The author has eight cardio exercises *defined* but has never logged one: `Distance` and `Distance Unit` are empty across all 2,234 CSV rows, and `distance > 0` matches zero rows in the database. So the exact `Distance Unit` cell values remain unknown. Also unverified: the `lbs` spelling of `Weight Unit`, since every row here is `kgs`.

These need a different export, or a deliberate throwaway cardio entry logged in FitNotes to generate one row.

## Incidental findings

- **The plate calculator config is directly reusable.** Barbells 20kg and 45lb; plates 20/15/10/5/2.5/1.25 kg and 45/35/25/10/5/2.5/1.25 lb, 20 of each. This is real data for the "round to a loadable weight" problem.
- **The author's exercise list is machine-heavy** — 26 machine or brand-named entries including five Hammer Strength and five Smith Machine variants, plus a Hip Thrust Machine. Independent empirical confirmation of the machine-coverage gap identified in the exercise domain model, from actual training behaviour rather than from counting dataset rows.
- `Routine`, `RoutineSection`, `BodyWeight`, `WorkoutComment` and `WorkoutTime` are all **empty** — routines and bodyweight tracking were never used, so this file cannot exercise an importer's handling of them.
