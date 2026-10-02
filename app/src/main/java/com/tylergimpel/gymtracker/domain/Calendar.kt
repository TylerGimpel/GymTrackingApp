package com.tylergimpel.gymtracker.domain

import java.time.LocalDate

/** What training a day on the calendar shows (#118). */
enum class DayTraining { LIFTING, CARDIO_ONLY }

/**
 * One marked day: what was trained on it, if anything, and whether the lifter weighed in.
 *
 * With what the grid draws (#147): the routine day it realised, the sets each muscle got —
 * direct work counting one and indirect a half, warm-ups never — and the day's weight.
 */
data class CalendarDay(
    val date: LocalDate,
    val training: DayTraining?,
    val weighedIn: Boolean,
    val name: String? = null,
    val muscleSets: Map<String, Double> = emptyMap(),
    val weight: Kilograms? = null,
)

/**
 * One session as the calendar reads it: its day, every exercise with a set logged in it, how many
 * of each exercise's sets counted as work, and the routine day it realised, if any.
 */
data class CalendarSession(
    val date: LocalDate,
    val trained: List<Exercise>,
    val sets: Map<String, Int> = emptyMap(),
    val name: String? = null,
)

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
fun calendarDays(
    sessions: List<CalendarSession>,
    weighIns: Collection<LocalDate>,
    /** The weight each day settled on, where the calendar knows it. */
    weights: Map<LocalDate, Kilograms> = emptyMap(),
): Map<LocalDate, CalendarDay> {
    val training = sessions.filter { it.trained.isNotEmpty() }
        .groupBy(CalendarSession::date)
        .mapValues { (_, day) ->
            if (day.any { session -> session.trained.any { !it.isCardio } }) DayTraining.LIFTING else DayTraining.CARDIO_ONLY
        }
    val weighed = weighIns.toSet()
    val sessionsOn = sessions.filter { it.trained.isNotEmpty() }.groupBy(CalendarSession::date)
    return (training.keys + weighed).associateWith { date ->
        val those = sessionsOn[date].orEmpty()
        CalendarDay(
            date = date,
            training = training[date],
            weighedIn = date in weighed,
            name = those.firstNotNullOfOrNull { it.name },
            muscleSets = those.flatMap { session ->
                session.trained.flatMap { exercise ->
                    val counted = (session.sets[exercise.id] ?: 0).toDouble()
                    exercise.primaryMuscles.map { it to counted } + exercise.secondaryMuscles.map { it to counted / 2 }
                }
            }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.filterValues { it > 0 },
            weight = weights[date],
        )
    }
}
