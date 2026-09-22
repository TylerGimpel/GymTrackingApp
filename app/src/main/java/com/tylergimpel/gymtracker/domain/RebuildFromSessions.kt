package com.tylergimpel.gymtracker.domain

/**
 * A routine day recovered from the log: its name, and what the session it came from was asked for.
 *
 * Not a [RoutineDay], because nothing here has an id yet — it is a shape read back out of history,
 * waiting to be created.
 */
data class RecoveredDay(
    val name: String,
    val exercises: List<PlannedExercise>,
)

/**
 * The routine a run of sessions was following, read back out of their **Plan** snapshots.
 *
 * **No new storage, and that is the whole argument.** A session snapshots what its routine day
 * held when it was trained, which ADR 0006 leans on to justify not versioning routines at all — so
 * every routine day a lifter ever followed is already in the log, and getting one back is a read
 * and a create rather than a feature.
 *
 * Keyed by the routine day name the session recorded, because that is what a session copies: the
 * day it realised, by name, with no constraint back to a routine that may not exist any more.
 * Sessions that named no day are skipped — training with no routine is ordinary, and there is no
 * rotation to recover from it.
 *
 * **The most recent session for each day wins.** A lifter rebuilding from last spring wants what
 * they were doing by the end of it, not the first version they tried; and where the same day was
 * trained repeatedly, the last one is the one that stuck.
 *
 * Ordered by when each day was last trained, which is the closest thing the log holds to the
 * order they sat in. A rotation recovered in a different order is still the same training.
 */
fun rebuildFrom(sessions: List<LoggedSession>): List<RecoveredDay> = sessions
    .filter { it.session.routineDayName != null && !it.plan.isEmpty }
    .sortedBy { it.session.date }
    .associateBy { it.session.routineDayName!! }
    .entries
    .sortedBy { (_, session) -> session.session.date }
    .map { (name, session) -> RecoveredDay(name, session.plan.exercises) }
