package com.tylergimpel.gymtracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One logged trip to the gym.
 *
 * A session usually realises a routine day, but carries no reference to one: it can exist without a
 * routine at all, and a revision to a routine must never be able to reach logged history. The
 * routine snapshot arrives with its own ticket and will be a copy, not a constraint.
 */
data class Session(
    val id: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
    /**
     * The day this session belongs to, stamped from [startedAt] and editable afterwards.
     *
     * Held as its own value rather than derived on read, because a lifter may correct it and
     * because deriving it from [finishedAt] would file a session that ran past midnight on a day
     * its owner was not in the gym.
     */
    val date: LocalDate,
    val note: String?,
    /**
     * The routine and routine day this session realised, held as copied names rather than as a
     * reference. A session can have neither: training with no routine at all is ordinary.
     */
    val routineName: String? = null,
    val routineDayName: String? = null,
    /**
     * The import this session came from, or null for one logged in the app (ADR 0007).
     *
     * Every set in it is an imported set: provenance, not a set type.
     */
    val importBatchId: Long? = null,
) {
    val isActive: Boolean get() = finishedAt == null

    /** Whether this session was brought in from another app rather than logged in this one. */
    val isImported: Boolean get() = importBatchId != null

    /**
     * Whether a set logged at [now] belongs to this session.
     *
     * A session stays open across midnight, because a lifter who started at 23:40 is still in the
     * same session at 00:20. It does not stay open forever: an unfinished session from Tuesday must
     * not swallow Thursday's first set and file it under Tuesday, which is what makes the date on a
     * session trustworthy at all. The cutoff is the hour a training day is treated as ending.
     */
    fun accepts(now: Instant, zone: ZoneId): Boolean {
        if (!isActive) return false
        val local = now.atZone(zone)
        return when (local.toLocalDate()) {
            date -> true
            date.plusDays(1) -> local.hour < TRAINING_DAY_ENDS_AT_HOUR
            else -> false
        }
    }

    /**
     * Whether this session is filed on a day other than the one it was trained on.
     *
     * What "backdated" means to the cursor, and the only thing the cursor asks of a date: a session
     * logged days after the fact is a record of what happened, not a statement about what comes
     * next, so it never advances a cycle (ADR 0001). Importing years of history would otherwise
     * fling the cursor somewhere arbitrary.
     *
     * A session that ran past midnight is not backdated — its date is the day it started, which is
     * the midnight rule doing its job rather than anyone refiling anything.
     */
    fun isBackdated(zone: ZoneId): Boolean = date != startedAt.atZone(zone).toLocalDate()

    fun finish(at: Instant): Session = copy(finishedAt = at)

    /**
     * The same session, recorded as having realised [day] of [routine].
     *
     * Copies the names rather than taking a reference, so that nothing about this session depends
     * on the routine continuing to exist or to look the way it does now.
     */
    fun realising(routine: Routine, day: RoutineDay): Session =
        copy(routineName = routine.name, routineDayName = day.name)

    /** The same session, refiled on [date]. When it was trained is untouched. */
    fun on(date: LocalDate): Session = copy(date = date)

    companion object {
        /**
         * The hour a training day is treated as ending, so that a session running late still
         * belongs to the day it started on rather than opening a second one at midnight.
         */
        const val TRAINING_DAY_ENDS_AT_HOUR = 4

        /** A new, active session, taking the local date of the moment it began. */
        fun start(at: Instant, zone: ZoneId, id: Long = 0, note: String? = null) = Session(
            id = id,
            startedAt = at,
            finishedAt = null,
            date = at.atZone(zone).toLocalDate(),
            note = note,
        )

    }
}
