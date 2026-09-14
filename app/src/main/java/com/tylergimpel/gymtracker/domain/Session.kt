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
) {
    val isActive: Boolean get() = finishedAt == null

    fun finish(at: Instant): Session = copy(finishedAt = at)

    /** The same session, refiled on [date]. When it was trained is untouched. */
    fun on(date: LocalDate): Session = copy(date = date)

    companion object {
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
