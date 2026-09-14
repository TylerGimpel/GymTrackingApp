# Exercise bank source data

Build-time input for the preloaded exercise bank. A build step merges these with the upstream dataset and emits the prepopulated SQLite file that Room opens via `createFromAsset()`.

Nothing here is loaded at runtime, and nothing here is fetched over a network.

## The four files

| File | What it is |
|---|---|
| `upstream-exercises.json` | **876 upstream rows**, structured fields only |
| `authored-exercises.json` | **20 new exercises** the upstream dataset does not contain |
| `cardio-retag.json` | **14 patches** to existing upstream cardio rows |
| `aliases.json` | **133 search aliases** covering 160 alias→exercise pairs |

Upstream is [`yuhonas/free-exercise-db`](https://github.com/yuhonas/free-exercise-db) — Unlicense, 876 rows. **Take its structured fields only.** Its `instructions` and `images` are excluded on provenance grounds and must not be reintroduced; see `docs/adr/0002-exercise-bank-source-and-omissions.md`.

### Why upstream is vendored rather than downloaded

`upstream-exercises.json` is a snapshot taken from `dist/exercises.json`, carrying `id`, `name`, `primaryMuscles`, `secondaryMuscles`, `equipment`, `mechanic`, `force`, `category` and `level` — and nothing else.

Fetching it during the build would make the build need a network, make it non-reproducible, and leave the excluded prose and images arriving on every machine only to be dropped again. Stripping them once, here, means they are not in the repository to reintroduce. Refreshing the snapshot is a deliberate act with a reviewable diff, which is the right shape for data that ships inside the APK and cannot be corrected without a release.

## Why these exist

The upstream bank is better than a brand-name search suggests. It has no "Hammer Strength" or "pec deck" because it uses **generic names by convention** — the same rule this project independently adopted. `Butterfly` is the pec deck; the eight `Leverage *` rows are the plate-loaded machines. Probing for movements rather than brands, nearly every staple is already present: 10 lat pulldown variants, seated cable rows, cable crossovers, leg press, hack squat, leg curls, thigh abductor/adductor, machine preacher curl, dip machine, glute kickback, back extension, torso rotation.

What genuinely remained:

**Machines (12).** Chest-supported row, pendulum squat, belt squat, assisted pull-up and dip machines, hip thrust machine, cable hip abduction, glute kickback machine, machine lateral raise, reverse hack squat, single-leg leg press, machine incline chest press. Several are glute-focused — the dataset predates that era, carrying only 22 exercises with glutes as primary and three hip thrust or glute bridge variants.

**Free weights (5).** Bulgarian split squat, kettlebell swing (two-hand — upstream has only the one-arm version), Pendlay row, sumo squat, landmine press. These surfaced from the alias work rather than from surveying equipment: mapping what lifters *type* onto what the bank *contains* exposes missing exercises that a machine-by-machine survey structurally cannot. The Bulgarian split squat is in the author's own training history and had no upstream entry.

**Cardio (3).** Swimming, fan bike, ski erg. Note `Air Bike` upstream is the **abdominal bicycle crunch**, not the fan bike — hence the generic name here.

## The cardio re-tag matters more than the new cardio rows

Upstream tags every cardio machine as generic `machine`. That cannot drive the gym equipment checklist: someone with a rower but no treadmill is indistinguishable from someone with both, so modality suggestions would be guesses.

The patches assign the six cardio equipment values and the logging type to the 14 existing rows. Ids are upstream and stable.

## Closed vocabularies

Any value outside these is invalid and the build should reject it.

- **muscles** (primary and secondary) — `abdominals`, `abductors`, `adductors`, `biceps`, `calves`, `chest`, `forearms`, `glutes`, `hamstrings`, `lats`, `lower back`, `middle back`, `neck`, `quadriceps`, `shoulders`, `traps`, `triceps`
- **equipment** — upstream's 13 (`barbell`, `dumbbell`, `cable`, `machine`, `body only`, `kettlebells`, `bands`, `medicine ball`, `exercise ball`, `foam roll`, `e-z curl bar`, `other`) plus six cardio values added here: `treadmill`, `stationary bike`, `rower`, `elliptical`, `stair climber`, `outdoor`
- **mechanic** — `compound`, `isolation`
- **force** — `push`, `pull`, `static`
- **category** — `strength`, `powerlifting`, `olympic weightlifting`, `strongman`, `stretching`, `plyometrics`, `cardio`
- **level** — `beginner`, `intermediate`, `expert`
- **loggingType** — `weight_reps`, `bodyweight_reps`, `assisted`, `time`, `distance_time`

## Rules the build must enforce

The shipped SQLite file is generated from **Room's own exported schema**, never hand-written DDL: a prepopulated file whose `room_master_table` identity hash disagrees with Room's is a runtime crash rather than a compile error. On top of that, the merge enforces:

1. **No id or name may collide with upstream**, case-insensitively. This caught a real error during authoring — `Air Bike` was already taken by an unrelated ab exercise.
2. **Every vocabulary value must be in the closed sets above.**
3. **No row may carry `instructions` or `images`.**
4. **Every alias target must name an exercise that exists** after the merge. An alias pointing at a missing name fails silently, which is the worst kind of failure for a search feature.
5. **No brand names.** "Hammer Strength Iso-Lateral Row" is wrong at every gym with a different manufacturer. Generic names plus user-authored exercises cover gym specifics.

## Two things worth knowing when editing

**`assisted` inverts progression.** `Assisted Pull-Up Machine` and `Assisted Dip Machine` are the only rows carrying it: less weight means more progress, so every comparison — beat, missed, personal best, graph direction — flips. Getting it wrong means confidently coaching someone to get weaker.

**`unilateral` changes logging, not identity.** Five rows carry it. A unilateral exercise logs a single entry by default and expands to per-side on demand; per the set-identity rule, both still count as **one set**.
