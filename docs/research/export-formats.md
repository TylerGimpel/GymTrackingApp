# Workout tracker export formats

Research for [issue #6](https://github.com/TylerGimpel/GymTrackingApp/issues/6) — what a FitNotes export
actually contains, and what an importer in this app could recover from it.

Covers FitNotes (Android CSV + `.fitnotes` SQLite backup), Strong, Hevy, Boostcamp, and whether any
neutral interchange format exists.

## How to read the confidence markers

Every non-obvious claim below carries one of these:

- **[real file]** — read out of an actual export committed to a public repo, or an actual export's
  header quoted in a project's test fixture.
- **[vendor doc]** — stated on the app vendor's own documentation or help site.
- **[derived]** — read out of the source of a working third-party importer/exporter that was written
  against real exports. Strong evidence of shape, but one author's reading.
- **[needs confirming]** — plausible and widely repeated, but not established by any of the above.
  Treat as a question for the real export, not a fact.

The companion task ticket produces the author's own FitNotes export. Everything marked
**[needs confirming]** should be checked against that file before any parser is written against it.

---

## Bottom line

1. **The FitNotes CSV cannot express warm-up sets, drop sets, supersets, RPE, or per-set
   completion — because FitNotes itself has no concept of the first three at all.** Warm-ups and
   drop sets are not "flattened away" by the CSV; they are not modelled by the app in the first
   place. Supersets *are* modelled (as dated exercise groups), and *are* dropped by the CSV.
   Whatever the importer does, it cannot reconstruct set intent from a FitNotes CSV. The most it can
   honestly do is import every row as a plain working set.
2. **The `.fitnotes` backup is a plain SQLite database and is strictly richer than the CSV.** It
   carries supersets, per-set comments, per-date workout comments, workout start/end times,
   personal-record flags, set-complete flags, per-exercise weight units, exercise types, routines,
   and the body tracker. For the author's own history — a one-off migration off his own device —
   reading the SQLite backup is the higher-fidelity path, and it needs no third-party library on
   Android.
3. **No interchange format exists.** Every app rolled its own. Garmin FIT can carry strength sets
   but is a binary activity format, and Health Connect's exercise records have no load field at all.
4. **One importer does not generalise, but one *normalised model* does.** The four formats disagree
   on delimiter, header names, date format, unit convention, and set-type encoding. Every working
   importer surveyed uses header-name lookup with aliases plus delimiter sniffing, never column
   positions. Plan for a shared internal session→exercise→set model with a thin per-app parser in
   front of it.
5. **Exercise names are the real failure surface.** FitNotes names put the modifier first
   (`Flat Barbell Bench Press`); Strong and Hevy put equipment in a trailing parenthesis
   (`Bench Press (Barbell)`). Neither convention survives exact-match lookup against the other, and
   real user data contains custom names, abbreviations and personal nicknames that match nothing.

---

## FitNotes (Android)

The app is `com.github.jamesgay.fitnotes` by James Gay; docs at `fitnotesapp.com`.

**Note:** "FitNotes 2 / FitNotes iOS" (`getfitnotes.com`, App Store id 1538896016) is a *different*
app by a different vendor (Ginger Technologies Pte Ltd) that is "based on" the Android app and can
read its files. Its documented CSV layout is **its own import format**, not the Android export —
see [FitNotes iOS](#fitnotes-ios-a-different-app-with-a-different-csv) below. Several third-party
descriptions on the web conflate the two.

### What the app offers [vendor doc]

Settings gives two distinct outputs:

| Output | File | Round-trips? |
| --- | --- | --- |
| Backup | `FitNotes_Backup.fitnotes` (optionally timestamped) | Yes — Restore reads it, and **overwrites** everything currently on the device |
| Spreadsheet export | CSV, separately for **Workout** data and **Body Tracker** data | **No** — the vendor states CSV cannot be restored into FitNotes |

Since v24 the user can choose the folder and customise the file name for both, so **do not rely on
the file name** to identify the file. Historical names seen in the wild: `FitNotes_Export_2016_03_13_22_34_28.csv`,
`FitNotes_Export_2020_10_04.csv` [real file].

### Workout CSV — column shape

One row per set. No workout-name and no workout-time columns: FitNotes organises purely by calendar
date, so **a day is the workout**.

There are two header generations, and the change is dated.

**Generation 1 (pre-~Sept 2024) — unit is baked into the header:**

```
Date,Exercise,Category,Weight (kgs),Reps,Distance,Distance Unit,Time,Comment
2015-08-22,Jerk,Legs,50.0,5,,,,""
```
[real file — `drobotnik/fitnotes`, export dated 2016-03-13; the `Weight (kgs)` spelling is
independently confirmed by `linem/mjewelplot`, which keys on that exact column name]

An lbs-configured export of the same generation, with **only eight columns — no `Comment`**:

```
Date,Exercise,Category,Weight (lbs),Reps,Distance,Distance Unit,Time
2020-09-13,Barbell Squat,Legs,45.0,10,,,
```
[real file — `moferg/fitnotes-data-viz`, export dated 2020-10-04]

Whether that 8-column file is an older/newer app build, an app that omits `Comment` when no set
comments exist, or a file the repo author trimmed, cannot be determined from the file alone
**[needs confirming]**. Either way: **the trailing `Comment` column is not guaranteed to be
present.** A parser must not require it and must not index by position.

**Generation 2 (current) — unit moves into its own column:**

```
Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
```

Evidence this is a real change and not a rumour: the `fitnotes2fit` converter changed its CSV
binding from `Weight (kgs)` to `Weight` **plus a new `Weight Unit` field** in commit `c093557`
(2024-09-25), with the message that it was updating the parser to follow FitNotes' CSV changes
[derived]. That matches the app's v24 release note introducing **per-exercise kg/lbs** [vendor doc]:
once a single history can mix units, a unit in the header is no longer expressible, so a per-row
column is the only correct encoding. The same 10-column header is what essentially every current
third-party importer expects.

`Weight Unit` cell values are `kgs` / `lbs` [derived — LiftLog's importer maps exactly those two
strings] **[needs confirming against a real Gen-2 export]**.

**Column semantics** (combining the real files, the vendor docs, and the DB schema):

| Column | Shape | Notes |
| --- | --- | --- |
| `Date` | `YYYY-MM-DD` | Date only — no time of day. Rows appear grouped by exercise within a date, dates ascending [real file] |
| `Exercise` | free text | The user's exercise name, default-bank or custom |
| `Category` | free text | Muscle group (`Legs`, `Back`, `Cardio`, …), user-customisable. A weak but real hint for name matching |
| `Weight` / `Weight (kgs)` / `Weight (lbs)` | decimal, e.g. `45.0`, `1.0` | Empty for exercise types with no weight. The DB stores kg always; the CSV is in **display** units |
| `Weight Unit` | `kgs` / `lbs` | Gen-2 only |
| `Reps` | integer | Empty for types with no reps |
| `Distance` | decimal | Empty for non-distance types |
| `Distance Unit` | short unit string | Expected `m` / `km` / `ft` / `mi` (the DB's distance-unit set) **[needs confirming]** |
| `Time` | duration | Expected `HH:MM:SS`, possibly `MM:SS` **[needs confirming — no cardio rows in either real sample]** |
| `Comment` | free text, quoted | **Per-set** comment. Empty comments rendered as `""` in the 2016 file [real file]. Absent entirely from the 2020 file |

Neither real sample contains a single cardio row, so **the exact rendering of `Distance`,
`Distance Unit` and `Time` is unverified.** If the author's history has cardio in it, that is the
first thing to look at.

### What the CSV cannot express — the bounding finding

| Concept | In the app? | In the DB? | In the CSV? |
| --- | --- | --- | --- |
| Warm-up set | **No** | **No** | No — nothing to export |
| Drop set | **No** | **No** | No — nothing to export |
| Superset / exercise group | **Yes** (named, coloured, auto-jump) | Yes (`WorkoutGroup` + `WorkoutGroupExercise`, keyed by date+exercise) | **No** |
| RPE / RIR | No | No | No |
| Set marked complete | Yes (optional checkbox) | Yes (`training_log.is_complete`) | **No** |
| Personal-record flag | Yes | Yes (`is_personal_record`) | **No** — recomputable anyway |
| Per-set comment | Yes | Yes (`Comment` table) | Yes, when the column is present |
| Per-workout (per-date) comment | Yes | Yes (`WorkoutComment`) | **No** |
| Per-exercise notes | Yes | Yes (`exercise.notes`) | **No** |
| Workout start/end time, duration | Yes (workout timer) | Yes (`WorkoutTime`) | **No** |
| Exercise type (which fields are meaningful) | Yes | Yes (`exercise.exercise_type_id`) | **No** — must be inferred from which cells are populated |
| Per-exercise weight unit | Yes (v24+) | Yes (`exercise.weight_unit_id`) | Gen-2 only |
| Routine the set came from | Yes | Yes (`routine_section_exercise_set_id`) | **No** |
| Bodyweight / body fat | Yes (Body Tracker) | Yes (`MeasurementRecord`) | **Separate CSV**, not in the workout CSV |
| Multiple sessions on one day | Not distinguished | Not distinguished (date is the key) | No |

So the honest ceiling for a FitNotes CSV import is: **date → exercise → ordered plain sets with
weight/reps/distance/time and optional per-set comment.** An importer that claims to recover
warm-ups or supersets from this file is inventing them.

### Cardio and bodyweight specifically

FitNotes has no "cardio" or "bodyweight" *flag*. It has **exercise types**, chosen per exercise
[vendor doc]:

- Default: **Weight and Reps**, **Distance and Time**.
- Supporter (paid) adds: Weight and Distance, Weight and Time, Reps and Distance, Reps and Time,
  Weight only, Reps only, Distance only, Time only.

The DB encodes these as `exercise_type_id` 0–9 [derived]. The CSV does not carry the type, so the
importer must infer it from which columns are populated — which is exactly what every third-party
importer does, and is unreliable for a single row.

Bodyweight movements have no special representation. In the 2016 real export, `Pull Up` rows are
logged as Weight-and-Reps with **weight `1.0`**, and `Plank` / `Scissors` are logged as
Weight-and-Reps with **weight `1.0` and "reps" `60` / `30` — i.e. reps standing in for seconds**
[real file]. Nothing in the file distinguishes that from a genuine 60-rep set of a 1 kg exercise.
This is not a format quirk to work around; it is the ceiling on what any importer can know. The
practical consequence: **import faithfully, show the user what arrived, and let them fix it** —
do not guess that `1.0 × 60` means a plank.

### Body Tracker CSV

A separate export. Expected header `Date,Time,Measurement,Value,Unit,Comment` with `Time` as
`HH:MM:SS` [derived — a FitNotes-compatible reimplementation emits exactly this]
**[needs confirming]**. Default measurements: Bodyweight and Body Fat enabled; Neck, Shoulders,
Chest, Waist, Hips, Upper Arm L/R, Forearm L/R, Thigh L/R, Calf L/R available.

### The `.fitnotes` backup (SQLite)

`FitNotes_Backup.fitnotes` is the app's `database.db` copied verbatim — an ordinary SQLite file,
openable with Android's built-in `SQLiteDatabase` with no extra dependency. Schema below is
**[derived]** from a FitNotes-compatible reimplementation whose author documents it as read out of
FitNotes 25.1 (`SQLiteOpenHelper` version 22), cross-checked against the app's help site.

Tables that matter for import:

| Table | Purpose |
| --- | --- |
| `training_log` | **One row per set.** `_id, exercise_id, date (YYYY-MM-DD text), metric_weight, reps, unit, routine_section_exercise_set_id, timer_auto_start, is_personal_record, is_personal_record_first, is_complete, is_pending_update, distance, duration_seconds` |
| `exercise` | name, `category_id`, `exercise_type_id`, `weight_unit_id`, `is_favourite`, `weight_increment`, `default_rest_time`, `notes` |
| `Category` | muscle groups, `colour` (signed ARGB int), `sort_order` |
| `Comment` | per-set comments (`owner_type_id` 1 = a `training_log` set, `owner_id` = set id) |
| `WorkoutComment` | one comment per workout **date** |
| `WorkoutGroup` / `WorkoutGroupExercise` | **supersets**, membership by `(date, exercise_id)` |
| `WorkoutTime` | `workout_date`, `start_date_time`, `end_date_time` (`YYYY-MM-DD HH:MM:SS`) |
| `Routine`, `RoutineSection`, `RoutineSectionExercise`, `RoutineSectionExerciseSet` | routines / days / planned sets |
| `Measurement`, `MeasurementUnit`, `MeasurementRecord` | body tracker (`BodyWeight` is the migrated legacy table) |
| `Goal` | per-exercise goals |
| `settings` | preferences, rewritten at backup time (`metric` 1 = kg, 0 = lbs) |

Two important storage facts [derived]:

- **`metric_weight` is always kilograms.** Display unit comes from `exercise.weight_unit_id`
  (0 = follow global, 1 = kg, 2 = lbs). Changing an exercise's unit with "Just change unit" rescales
  the stored kg so the displayed number is unchanged — so stored kg is authoritative and the CSV's
  numbers are a rendering of it.
- **`training_log.unit` is the *distance* unit, not the weight unit** (2 = m, 3 = km, 4 = ft,
  5 = mi; 0 = unset on weight-only sets). This is an easy and expensive misread.

`distance` is stored in metres, `duration_seconds` in seconds. Exercise order within a day is the
order of each exercise's lowest set `_id` — there is no explicit ordering column.

Exercise types `exercise_type_id`: 0 Weight+Reps, 1 Distance+Time, 2 Weight+Distance,
3 Weight+Time, 4 Reps+Distance, 5 Reps+Time, 6 Weight, 7 Reps, 8 Distance, 9 Time.

### CSV vs backup — which to import

| | Workout CSV | `.fitnotes` backup |
| --- | --- | --- |
| Parsing cost | Trivial | Trivial on Android (it *is* SQLite) |
| Version stability | **Poor** — header changed at least once, in 2024 | Additive `ALTER TABLE` upgrades; `user_version` says what you have |
| Supersets, workout comments, workout times, set-complete, PR flags, exercise notes, per-exercise units, routines, body tracker | Lost | Present |
| Works for users of *other* apps | Yes (a generic CSV path serves Strong/Hevy too) | No |
| Vendor's own position | Explicitly not restorable | The supported restore path |

**Recommendation:** support both, but treat them as different features. The `.fitnotes` reader is the
high-fidelity path for the author's own migration and is cheap on Android. The CSV reader is the
general path that also covers Strong and Hevy users. If only one ships first, the backup gives the
author more of his history back.

### FitNotes iOS — a different app with a different CSV

`FitNotes 2` (iOS, Ginger Technologies) documents an import CSV whose **required** header is
[vendor doc]:

```
Date,Exercise,Category,Weight (kg),Weight (lbs),Reps,Distance,Distance Unit,Time,Notes,Kind
```

with `Date` as `YYYY-mm-dd` (ascending, sets for an exercise grouped), weights to at most 2 decimal
places, `Distance Unit` one of `m, km, cm, in, ft, yd, mi`, `Time` as `HH:MM:ss` or `MM:ss`, and
`Kind` as a combination of the letters `w`, `r`, `d`, `t` (e.g. `wr` = weight and reps) naming which
fields are meaningful. It accepts imports from Strong, FitBod, IronGAINS, HeavySet, Hevy, Liftin,
WeightFit, FitNotes Android, and itself, and can read the Android `.fitnotes` DB directly.

Note what the `Kind` column is: it is the missing exercise-type field, added by a *third party*
because the Android CSV's omission of it is a real problem. **Do not expect `Kind` or the dual
weight columns in an Android export.** If this app ever defines its own export CSV, `Kind` is a good
idea worth copying.

---

## Strong

Export from the app's settings; a single CSV of completed workouts. The vendor states exported
files **cannot be imported back into Strong** [vendor doc]. Routines/templates are not in the
export; body measurements are a separate file [derived].

One row per set, with workout-level values repeated on every row.

**Variants actually observed** — this format is the least stable of the three:

```
# comma, real export dated 2020-12-30
Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE
2020-12-30 18:51:52,"Evening Workout",2h 38m,"Snatch (Barbell)",1,40.0,3,0,0,"","",
```
[real file — `AlexandrosKyriakakis/StrongAppAnalytics`]

```
# semicolon
Date;Workout Name;Duration;Exercise Name;Set Order;Weight (kg);Reps;Distance;Seconds;Notes;Workout Notes;RPE

# semicolon, RPE moved, explicit units
Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;Distance (meters);Seconds;Notes;Workout Notes
```
[real file — header fixtures in `enzo-prism/marble` and `Aebel-Shajan/year-in-data`]

Key facts:

- **Delimiter varies: `;` on Android, `,` on iOS** [derived — the Ryot importer sniffs the first line
  for exactly this reason]. Sniff it; never hard-code.
- **Column names vary**: `Duration` vs `Duration (sec)`; `Weight` vs `Weight (kg)`; `Distance` vs
  `Distance (m)` / `Distance (meters)`. A trailing `Workout Duration` column is also reported
  **[needs confirming]**. **Column order varies too** (RPE is 7th in one variant, last in another) —
  which is the concrete reason positional parsing is wrong.
- `Date` is `YYYY-MM-DD HH:MM:SS` — a real timestamp, unlike FitNotes.
- `Duration` is either an integer number of seconds or a human string like `2h 38m` — both must be
  handled [real file + derived].
- **Weight unit is absent from the plain `Weight` variant.** `100` could be kg or lb; it follows the
  user's in-app setting and is unrecoverable from the file. The `Weight (kg)` variant is explicit.
- **`Set Order` is overloaded and is where set type lives**: normally `1, 2, 3…`, but `W` = warm-up,
  `D` = drop set, `F` = failure [derived, corroborated by two independent importers]. It also carries
  **non-set rows**: `Rest Timer` and `Note`, which must be skipped, not parsed as sets.
- **No superset column.** Strong supports supersets in-app; the CSV drops them.

So Strong is the one mainstream format that *does* express warm-up and drop sets — inside a column
whose name suggests it is an integer.

---

## Hevy

Export via profile → settings → export; the file arrives by email from the app or downloads directly
from the web app. Reports that free vs paid tiers get `.csv` vs tab-separated `.tsv` exist
**[needs confirming]**.

One row per set. Real header [real file — `matanabudy/workout-data-sync`]:

```
"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
"Morning workout","22 Dec 2025, 08:00","22 Dec 2025, 08:37","","Pull Up (Assisted)",,"",0,"normal",21,10,,0,8.5
```

- **Unit-dependent column *names*:** an lbs/miles account exports `weight_lbs` and `distance_miles`
  in place of `weight_kg` / `distance_km` [real file — a second real export uses the lbs/miles
  spelling; the Ryot importer aliases both pairs onto one field]. The unit is therefore recoverable —
  from the header, not the data.
- **`set_type`: `normal` | `warmup` | `dropset` | `failure`** [derived, two independent importers].
  This is the only surveyed format with a first-class set-type column.
- **`superset_id`** groups exercises within a workout; empty when not supersetted. The only surveyed
  format that exports supersets at all.
- `start_time` / `end_time` are **localised human strings** like `22 Dec 2025, 08:00` — naive local
  time, with month names that change with the exporting user's app language. ISO timestamps are also
  reported **[needs confirming]**. This is a genuine parsing hazard: a fully-offline importer must
  either accept several date shapes or tell the user it cannot read theirs.
- `set_index` is 0-based and **resets per exercise**, so `(start_time, end_time)` identifies the
  workout and `exercise_title` groups within it.
- Carries `rpe`, per-workout `description`, and per-exercise `exercise_notes`.

Hevy is the richest of the three and is the closest thing to a de-facto model worth copying for this
app's own export.

---

## Boostcamp

**There is no official export** [needs confirming as a negative, but: Boostcamp's own support
responses say export has been requested and is not yet available, and no export documentation
exists]. What people actually use:

- A third-party Chrome extension ("Boostcamp History → CSV", ~283 users, published by an individual)
  that downloads history from the web app.
- Copying the JSON payload out of the browser's network tab on `boostcamp.app/history` and flattening
  it — the approach taken by `blaekhossa/boostc_2_dataframe`, which describes the payload as nested
  session → exercise → set and flattens it to one row per set, preserving reps, weight and a
  "skipped" flag. That repo's own README warns it is lightly tested.

**Conclusion: Boostcamp should not be a launch target.** There is no stable artefact to parse, no
vendor contract, and any format would be reverse-engineered from a private JSON endpoint that can
change without notice. If Boostcamp support is ever wanted, the realistic path is the generic CSV
importer plus a documented column mapping the user fills in.

---

## Is there an interchange format?

No. Nothing neutral exists for strength training, and the near-misses fail for specific reasons:

- **Garmin FIT** can encode strength sets (reps, weight, exercise category/name) and is what
  `fitnotes2fit` targets to push FitNotes history into Garmin Connect / Strava. But it is a binary
  activity-file format, its strength encoding is under-documented (community threads and unofficial
  repos fill the gap), and it requires mapping every exercise onto Garmin's fixed enum — which is
  precisely why `fitnotes2fit` ships a user-supplied name-mapping flag. Not a sane import target for
  a local Android app.
- **Health Connect** (`androidx.health.connect`) models an `ExerciseSessionRecord` containing laps
  and `ExerciseSegment`s, where a segment carries start/end time, `segmentType` and `repetitions` —
  **and no weight or load field** [vendor doc]. It cannot represent "80 kg × 5", so it is not a
  history-transfer mechanism for a lifting app.
- **TCX / GPX** are cardio/route formats; no sets.
- The nearest thing to a convention is **Hevy's column set**, which at least two importers treat as
  their canonical shape and which one importer documents as its "recommended" layout.

Practical read: define this app's own export, make it round-trip losslessly, and document it. The
Hevy column set plus a FitNotes-iOS-style `Kind`/type column is a good starting point.

---

## One importer, or several?

**Several parsers, one model.** The formats are not close enough to share a parser:

| | FitNotes CSV | Strong | Hevy |
| --- | --- | --- | --- |
| Delimiter | `,` | `;` (Android) or `,` (iOS) | `,` (`.tsv` reported) |
| Row identity of a workout | calendar date | `Date` timestamp | `(start_time, end_time)` |
| Date format | `YYYY-MM-DD` | `YYYY-MM-DD HH:MM:SS` | `22 Dec 2025, 08:00` (localised) |
| Weight unit | header suffix (Gen 1) or `Weight Unit` column (Gen 2) | absent, or `(kg)` in the header | encoded in the column name |
| Set type | none | letters in `Set Order` | `set_type` column |
| Supersets | none | none | `superset_id` |
| Non-set rows | none | `Rest Timer`, `Note` | none |

What every working importer surveyed has in common, and what this app should copy:

1. **Sniff the delimiter** from the first line.
2. **Look columns up by header name, with aliases** (`Weight` / `Weight (kg)` / `Weight (kgs)` /
   `weight_kg` / `weight_lbs`). Never by index — Strong alone reorders columns between variants.
3. **Tolerate missing optional columns** (FitNotes `Comment` is absent from a real export).
4. **Skip rows that are not sets** (Strong's `Rest Timer` / `Note`).
5. **Normalise into one internal model** — session (date/time, name?, notes?) → exercise (name,
   notes?, superset group?) → set (type, weight + unit, reps, distance + unit, duration, RPE, notes)
   — and let each parser fill in only what its format can express, leaving the rest genuinely absent
   rather than defaulted.
6. **Decide deliberately what an absent value means.** A FitNotes import has *no* warm-ups, not
   "zero warm-ups found"; the UI should not imply the user's warm-ups were dropped by this app.

Two more traits worth stealing from mature importers: make re-importing the same file idempotent
(derive a stable session id from the grouping key, skip ids already present), and report per-row
failures as a list the user can see rather than failing the whole file.

---

## Exercise name reconciliation — where importers actually break

An import references exercises as free text. This app matches that text against a preloaded bank.
The conventions do not agree.

**FitNotes default bank** (modifier first, equipment inline, no parentheses except for cardio
variants) [derived from the shipped default list]:

```
Flat Barbell Bench Press    Incline Dumbbell Bench Press    Close Grip Barbell Bench Press
Barbell Squat               Barbell Front Squat             Romanian Deadlift
Lat Pulldown                Seated Cable Row                T-Bar Row
EZ-Bar Skullcrusher         Dumbbell Hammer Curl            Hammer Strength Shoulder Press
Running (Treadmill)         Running (Outdoor)               Rowing Machine
```

**Strong and Hevy** (movement first, equipment in a trailing parenthesis) [real files]:

```
Snatch (Barbell)      Clean (Barbell)        Bench Press (Barbell)
Pull Up (Assisted)    Leg Press (Machine)    Crunch (Weighted)     Seated Shoulder Press (Machine)
```

So `Flat Barbell Bench Press` ↔ `Bench Press (Barbell)` is a **rename plus a reordering plus a
qualifier that exists on only one side** (`Flat`). Exact match returns nothing; naive
suffix-stripping turns `Pull Up (Assisted)`, `Pull Up (Weighted)` and `Pull Up (Band)` into one
exercise, which is wrong — they are different loading schemes and at least one importer has an
explicit regression test that different equipment suffixes must stay separate.

**Real user data is worse than either bank.** From the 2016 real FitNotes export [real file]:

```
Clean And Jerk Cnj     Overhead Squat Ohs     Press In Clean     Press In Snatch
Snatch Lift Off        Clean Lift Off         Tall Clean         Tall Snatch
Tommys                 Scissors               Ab Twister         Standing Plate Twist
```

and from the 2020 one:

```
V-Grip Seated Cable Row (Pulley)    Face Pull (Pulley)    Lat Pulldown (Pulley)
```

Note `Tommys` (a personal nickname), `Cnj`/`Ohs` (abbreviations appended to the name), and
`(Pulley)` — a *user-invented* parenthetical convention that collides with Strong/Hevy's equipment
parenthetical but means something else. `fitnotes2fit` ships a command-line exercise-mapping option
precisely because its built-in map does not even cover all of FitNotes' own defaults.

Design consequences:

- **Match in tiers**: exact → case/punctuation/whitespace-normalised → normalised with the
  parenthetical split off and treated as an equipment hint → fuzzy suggestion. Stop at the first
  confident tier.
- **Never silently coerce.** An unmatched name should become a user-owned custom exercise carrying
  its original text, with a "map to…" affordance — not a dropped row and not a wrong match. This is
  the behaviour a couple of the surveyed importers explicitly document.
- **Persist the mapping** so a later import of the same history reuses the user's decisions.
- **Use `Category` as a tiebreaker** for FitNotes imports (`Legs`, `Back`, `Cardio`) when two bank
  entries are equally close.
- **Exercise identity is (name, measurement type).** A `Plank` logged as weight+reps in FitNotes and
  a `Plank` in this app's bank as a timed exercise are not interchangeable; merging them silently
  corrupts history.

---

## Checklist for the author's real export

Confirm these against the actual file before writing a parser; each corresponds to a
**[needs confirming]** above.

1. The exact header line, verbatim — which generation (`Weight (kgs)` vs `Weight` + `Weight Unit`),
   and whether `Comment` is present.
2. `Weight Unit` cell spelling (`kgs`/`lbs`?), and whether it varies row to row (per-exercise units
   in use).
3. A cardio row: the exact `Distance`, `Distance Unit` and `Time` cell formats.
4. A row with a per-set comment: quoting/escaping when the comment contains a comma, a quote or a
   newline; how an empty comment is rendered (`""` vs empty).
5. File mechanics: UTF-8 BOM?, CRLF vs LF, trailing newline, decimal separator (`.` vs locale `,`).
6. Whether anything precedes the header (preamble lines).
7. Whether a Body Tracker CSV was produced too, and its header.
8. From the `.fitnotes` backup: `PRAGMA user_version`, which tables are present, and whether
   `WorkoutGroup` has any rows (i.e. whether the author actually used supersets — which decides
   whether the backup path is worth building first).

---

## Sources

Vendor documentation:

- FitNotes (Android) — Settings / backup / export: <http://www.fitnotesapp.com/settings/>
- FitNotes — exercise types and categories: <http://www.fitnotesapp.com/exercises/>
- FitNotes — workout tracking, set comments, exercise groups, rest timer: <http://www.fitnotesapp.com/workout_tracking/>
- FitNotes — version 24 release notes (per-exercise kg/lbs, export file naming): <http://www.fitnotesapp.com/release_24/>
- FitNotes — version 25 release notes: <http://www.fitnotesapp.com/release_25/>
- FitNotes iOS (Ginger Technologies) — CSV import format: <https://www.getfitnotes.com/docs/migrate-from-other-apps.html>
- FitNotes iOS — migrating from FitNotes Android: <https://getfitnotes.com/docs/migrate-from-android.html>
- FitNotes iOS — backup & export: <https://www.getfitnotes.com/docs/backup-export.html>
- Strong — can I export my workout data: <https://help.strongapp.io/article/235-export-workout-data>
- Health Connect — `ExerciseSessionRecord` / `ExerciseSegment`: <https://developer.android.com/reference/androidx/health/connect/client/records/ExerciseSessionRecord>

Real export files and fixtures:

- FitNotes CSV, exported 2016-03-13 (`Weight (kgs)`, 9 columns): <https://github.com/drobotnik/fitnotes>
- FitNotes CSV, exported 2020-10-04 (`Weight (lbs)`, 8 columns, no `Comment`): <https://github.com/moferg/fitnotes-data-viz>
- Strong CSV, comma-delimited, exported 2020-12-30: <https://github.com/AlexandrosKyriakakis/StrongAppAnalytics>
- Hevy CSV sample (`weight_kg` variant): <https://github.com/matanabudy/workout-data-sync>
- Strong semicolon/`Weight (kg)` header fixtures: <https://github.com/enzo-prism/marble>, <https://github.com/Aebel-Shajan/year-in-data>

Third-party implementations read as evidence:

- `tihawk/fitnotes2fit` — FitNotes CSV → Garmin FIT; commit `c093557` (2024-09-25) moves the binding from `Weight (kgs)` to `Weight` + `Weight Unit`; exercise-name mapping flag: <https://github.com/tihawk/fitnotes2fit>
- `gueladjo/workout-notes` — FitNotes-compatible PWA; `doc/fitnotes-format.md` documents the `.fitnotes` SQLite schema read out of FitNotes 25.1 (DB version 22), and `src/backup/csv.ts` reimplements the CSV export: <https://github.com/gueladjo/workout-notes>
- `IgnisDa/ryot` — Strong importer (delimiter sniffing, `Set Order` = `W`/`D`/`F`, `Rest Timer`/`Note` rows, duration parsing) and Hevy importer (`weight_kg`/`weight_lbs` aliases, `set_type` mapping): <https://github.com/IgnisDa/ryot>
- `LiamMorrow/LiftLog` — FitNotes importer keyed on `Weight` + `Weight Unit` (`kgs`/`lbs`), documents grouping and idempotent re-import: <https://github.com/LiamMorrow/LiftLog>
- `TraceApps/lifttrace` — FitNotes importer detecting the unit from the `Weight (…)` header: <https://github.com/TraceApps/lifttrace>
- `linem/mjewelplot` — keys on the `Weight (kgs)` column: <https://github.com/linem/mjewelplot>
- `blaekhossa/boostc_2_dataframe` — Boostcamp history via the browser network tab: <https://github.com/blaekhossa/boostc_2_dataframe>

Third-party format documentation (secondary, used for corroboration only):

- Kinoku import formats (Strong / Hevy / FitNotes variants, set types): <https://kinoku.app/import-format>
- openweight migration notes (Hevy `set_type` values, Strong unit ambiguity): <https://openweight.dev/migrate/hevy.html>, <https://openweight.dev/migrate/strong.html>
- Gript import-from-Strong help (`W`/`D`/`F` markers, rest-timer rows skipped): <https://griptapp.com/help/import-from-strong>
- A developer's write-up of a real Hevy export (`weight_lbs` / `distance_miles`, `28 Mar 2025, 17:29` timestamps): <https://blog.ayjc.net/posts/migrate-strong-hevy-app/>
- Boostcamp History → CSV Chrome extension: <https://chromewebstore.google.com/detail/boostcamp-history-%E2%86%92-csv/ndldfeicfhokabalcjmjhhdhinjilllo>

### A note on the secondary literature

A large number of GitHub projects state the FitNotes header as
`Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment` in identical
copied comments. That header is almost certainly right for current versions — but the reason to
believe it is the dated 2024 commit in `fitnotes2fit` that changed a working parser in response to a
real format change, not the number of repetitions of the claim. Several of those same projects also
describe FitNotes as having warm-up or superset columns, which the app's own data model contradicts.
Treat the volume of agreement online as worth exactly nothing; treat real files and dated changes as
evidence.
