# Train Tracks

An offline-first Android workout tracker. It builds a routine around your goal, schedule and gym, tells you what to lift each set, and records what you actually lifted.

Everything stays on the phone: no account, no server, no network calls.

## What it does

- **Builds a routine for you.** Setup asks your goal, rep style, days a week, split and the equipment your gym has, then lays out days from recipes taken from proven programs, filled with the common lifts those programs use.
- **Tells you what to lift.** Each working set is prescribed by double progression inside a rep range, anchored to an Epley estimated 1RM from your own logged sets. Back-off is reactive, after missed sets, never a scheduled deload. A new exercise gets a calibration ramp rather than a number guessed from a related lift.
- **Runs the workout.** Start goes straight into the first set. Swap, add or skip exercises for tonight only, log with a tap, and rest is timed automatically with an alert that respects the phone's sound settings.
- **Shows your training.** A calendar coloured by the muscles trained, a strength chart and rep-specific records per exercise, honest volume per muscle, every set back to the first, and bodyweight over time.
- **Brings your history with you.** Imports FitNotes backups and CSV exports, keeping supersets, units and timed sets intact. Backs up and restores to a single file.

## Built with

Kotlin · Jetpack Compose (Material 3) · Room · Coroutines and Flow · a Gradle build-logic plugin that compiles the exercise bank at build time.

- **Exercise bank:** nearly 900 exercises, built at compile time from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) structured fields plus hand-authored machines and cardio, validated and shipped as a prepopulated SQLite database ([`data/README.md`](data/README.md)).
- **Tests:** JVM unit tests for the domain and view models, instrumented tests for persistence, migrations, backup and import, and build-logic tests that run the real bank data through its rules.

## How it was designed

The project keeps its reasoning next to its code:

- [`CONTEXT.md`](CONTEXT.md) is the domain language: routine, cycle, cursor, volume, tombstone and the rest, each with the terms to avoid.
- [`docs/adr/`](docs/adr/) holds the architecture decisions, including the ones that look like mistakes and are not. For example, [the recommendation engine](docs/adr/0004-recommendation-engine.md) explains why it uses Epley over more accurate formulas, why there is no scheduled deload, and why strength is never extrapolated across exercises.
- [`docs/research/`](docs/research/) records research checked against primary sources, such as the FitNotes export format verified against a real file.
- Every screen was prototyped as HTML and approved on a phone before it was built. The prototypes live on the `prototype/*` branches, and the decisions on the GitHub issues.

## Building

Requires JDK 17 and the Android SDK.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests run on a connected device or emulator with `./gradlew :app:connectedDebugAndroidTest`.

## Licence

Copyright © 2026 Tyler Gimpel. All rights reserved.

The source is public to read. It is not licensed for reuse, modification or redistribution.

Third-party assets keep their own licences, bundled in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/): the Barlow fonts (SIL Open Font License), MuscleMap muscle diagrams (MIT) and Tabler Icons (MIT). Exercise data derives from free-exercise-db (Unlicense); see [ADR 0002](docs/adr/0002-exercise-bank-source-and-omissions.md).
