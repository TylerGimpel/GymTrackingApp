package com.tylergimpel.traintracks.domain

import java.time.LocalDate

/**
 * A session and the **Plan** it was trained against, which is everything a rebuild reads.
 *
 * Narrower than a [LoggedSession] on purpose. What a rebuild wants out of the log is the plan
 * snapshots, and the window has to reach back far enough to find a routine from last spring —
 * so reading every set and every drop of two years of training to look at the plans would be
 * paying for the whole log to answer a question about a small corner of it.
 */
data class PlannedSession(
    val session: Session,
    val plan: SessionPlan,
)

/**
 * A routine day recovered from the log: its name, and what the session it came from was asked for.
 *
 * Not a [RoutineDay], because nothing here has an id yet — it is a shape read back out of
 * history, waiting to be created.
 */
data class RecoveredDay(
    val name: String,
    val exercises: List<PlannedExercise>,
)

/**
 * A whole routine recovered from the log, named as the sessions that followed it named it.
 *
 * Not a [Routine] for the same reason a [RecoveredDay] is not a [RoutineDay]: nothing here has an
 * id, a goal or a cadence. What the log holds is the rotation and the training that filled it;
 * everything else about a routine is a property the lifter says, not something history records.
 */
data class RecoveredRoutine(
    val name: String,
    /**
     * The last day of it that was trained — how a lifter tells one of these from another.
     *
     * The seasonal case this exists for is *"the routine I ran last spring"*, and a name alone
     * does not answer it for somebody who has called two of them "My routine".
     */
    val lastTrained: LocalDate,
    val days: List<RecoveredDay>,
)

/**
 * The routines a run of sessions was following, read back out of their **Plan** snapshots.
 *
 * **No new storage, and that is the whole argument.** A session snapshots what its routine
 * day held when it was trained, which ADR 0006 leans on to justify not versioning routines at
 * all — so every routine day a lifter ever followed is already in the log, and getting one
 * back is a read and a create rather than a feature.
 *
 * **Grouped by the routine, and only then by the day.** Anybody who has trained for a few months
 * has changed routines inside any window worth reading, so keying on the day name alone hands
 * back one rotation assembled out of two — an upper/lower day next to a push day, with nothing
 * saying it happened. The log already carries the routine each session realised, which turns
 * that blend into a choice between the routines that are actually in there. Grouped by unbroken
 * run rather than by name alone, because names repeat — see [runsOfOneRoutine].
 *
 * Both names are stamped on a session together, at the one place a session records the day it
 * realised, so a session carrying a day and no routine has never existed. Sessions that named
 * neither are skipped — training with no routine is ordinary, and there is no rotation to
 * recover from it.
 *
 * **The most recent session for each day wins.** A lifter rebuilding from last spring wants what
 * they were doing by the end of it, not the first version they tried; and where the same day was
 * trained repeatedly, the last one is the one that stuck.
 *
 * Days come back in the order each was last trained, which is the closest thing the log holds to
 * the order they sat in. A rotation recovered in a different order is still the same training.
 * Routines come back most recently trained first, because that is the one most likely to be meant.
 */
fun rebuildFrom(sessions: List<PlannedSession>): List<RecoveredRoutine> = sessions
    .filter { it.session.routineName != null && it.session.routineDayName != null }
    .filter { !it.plan.isEmpty }
    .sortedBy { it.session.date }
    .runsOfOneRoutine()
    .map { following ->
        val name = following.first().session.routineName!!
        RecoveredRoutine(
            name = name,
            lastTrained = following.last().session.date,
            days = following
                .associateBy { it.session.routineDayName!! }
                .entries
                .sortedBy { (_, session) -> session.session.date }
                .map { (dayName, session) -> RecoveredDay(dayName, session.plan.exercises) },
        )
    }
    .sortedByDescending(RecoveredRoutine::lastTrained)

/**
 * Splits sessions, already in date order, wherever the routine they followed changes.
 *
 * **A run rather than a name,** because a name is all a session copies and names repeat: every
 * routine built by hand starts out as "My routine". Somebody else's routine in between is the one
 * thing the log holds that says two runs under one name were two routines — so a lifter who moved
 * from one "My routine" to a push/pull/legs and on to another "My routine" is offered three.
 *
 * What this cannot see is one routine replaced by another of the same name with nothing trained
 * in between. The log records nothing that tells those apart, and ADR 0006 keeps it that way.
 */
private fun List<PlannedSession>.runsOfOneRoutine(): List<List<PlannedSession>> =
    fold(mutableListOf<MutableList<PlannedSession>>()) { runs, next ->
        val current = runs.lastOrNull()
        if (current?.last()?.session?.routineName == next.session.routineName) {
            current!!.add(next)
        } else {
            runs.add(mutableListOf(next))
        }
        runs
    }
