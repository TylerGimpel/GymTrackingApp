package com.tylergimpel.gymtracker.domain

import java.time.LocalDate

/** What training a day on the calendar shows (#118). */
enum class DayTraining { LIFTING, CARDIO_ONLY }

/** One marked day: what was trained on it, if anything, and whether the lifter weighed in. */
data class CalendarDay(val date: LocalDate, val training: DayTraining?, val weighedIn: Boolean)

/** One session as the calendar reads it: its day, and every exercise with a set logged in it. */
data class CalendarSession(val date: LocalDate, val trained: List<Exercise>)

/**
 * The marked days among [sessions] and [weighIns] (#118).
 *
 * **What was done, never what was not** (ADR 0008). A day is marked for a session that logged
 * something, and for a weigh-in; nothing else. A routine day the lifter skipped is recorded in a
 * table of its own that this never reads, and a session opened for it and left empty trained
 * nothing — so a skipped day is blank, exactly like a rest day, which to the calendar it is.
 *
 * A **cardio-only** day is marked differently from a lifting one, because a run is not a leg day
 * and the calendar should not say it was. A day with both is a lifting day.
 *
 * Unmarked days are absent rather than present and empty.
 */
fun calendarDays(sessions: List<CalendarSession>, weighIns: Collection<LocalDate>): Map<LocalDate, CalendarDay> {
    val training = sessions.filter { it.trained.isNotEmpty() }
        .groupBy(CalendarSession::date)
        .mapValues { (_, day) ->
            if (day.any { session -> session.trained.any { !it.isCardio } }) DayTraining.LIFTING else DayTraining.CARDIO_ONLY
        }
    val weighed = weighIns.toSet()
    return (training.keys + weighed).associateWith { CalendarDay(it, training[it], weighedIn = it in weighed) }
}
