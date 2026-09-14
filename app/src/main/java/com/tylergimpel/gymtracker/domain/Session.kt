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
) {
    val isActive: Boolean get() = finishedAt == null

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
