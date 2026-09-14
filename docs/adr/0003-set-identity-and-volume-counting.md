# A set is identified by its position, not by its row, so volume counting stays honest

Set identity is `(session_exercise, set_index)` — **not** the primary key of the row that stores it. One set may be recorded by more than one row (a per-side set writes two, one per side) or may carry child rows (a drop set has ordered drop segments). Training volume counts **distinct `set_index`**, so each of those is one set.

## Why

Training volume is the number the whole app rests on: the generator schedules against a per-session cap of roughly 10–11 sets per muscle, and the recommendation engine reads set history to decide what to prescribe next. If a drop set counted as three sets and a per-side set as two, a user doing either would silently blow through the cap while the app believed they were undertrained.

The naive alternative — one row is one set — forces drop sets and per-side sets to become several sets, which is wrong, or to be special-cased at every call site that counts volume, which is worse. Making identity positional fixes it once, in the schema, rather than in every query.

## Consequences

- **`order_index` is a separate column from the primary key**, and gaps are tolerated. Deleting set 2 of 5 leaves a gap rather than renumbering, because renumbering mid-session can corrupt a set the user is editing at that moment.
- **Drop sets store their first effort inline on the set** and further drops in an ordered child table. This is deliberately not uniform "every set has segments": over 99% of sets have no drops, and the inline values are exactly the ones progression should read — the fatigued later drops must not feed an estimated 1RM.
- **Supersets group at the exercise level, not the set level**, so they are a different mechanism from drops entirely. A drop groups *within* one set; a superset groups *across* exercises. Collapsing both into one generic grouping would make volume counting ambiguous again.
- **Set type determines whether a set counts at all.** `working`, `backoff` and `amrap` count; `warmup` and `calibration` do not. Calibration sets exist because cold start is a ramp rather than a prediction, and without a distinct type the first session on every exercise would corrupt both volume and progression history.
- The **user-visible set number is computed at render time**, not stored, so the display can show both the overall position and the working-set position at once.
