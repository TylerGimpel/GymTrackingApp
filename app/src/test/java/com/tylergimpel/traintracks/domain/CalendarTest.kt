package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * The calendar records what was done, never what was not (#118, ADR 0008).
 */
class CalendarTest {

    @Test
    fun `a lifting day, a cardio-only day and a weigh-in are each marked, and differently`() {
        val days = calendarDays(
            sessions = listOf(
                CalendarSession(MONDAY, trained = listOf(squat)),
                CalendarSession(TUESDAY, trained = listOf(run)),
            ),
            weighIns = listOf(WEDNESDAY),
        )

        assertEquals(CalendarDay(MONDAY, DayTraining.LIFTING, weighedIn = false), days[MONDAY])
        assertEquals(CalendarDay(TUESDAY, DayTraining.CARDIO_ONLY, weighedIn = false), days[TUESDAY])
        assertEquals(CalendarDay(WEDNESDAY, training = null, weighedIn = true), days[WEDNESDAY])
    }

    @Test
    fun `a day with lifting and a run on it is a lifting day`() {
        val days = calendarDays(
            sessions = listOf(CalendarSession(MONDAY, trained = listOf(run)), CalendarSession(MONDAY, trained = listOf(squat))),
            weighIns = listOf(MONDAY),
        )

        assertEquals(CalendarDay(MONDAY, DayTraining.LIFTING, weighedIn = true), days[MONDAY])
    }

    @Test
    fun `a day the routine expected and the lifter skipped carries no mark, even where a session was opened for it`() {
        // The shape a skip leaves when it reaches the calendar at all: a session started for the
        // routine's day and left with nothing logged. The skip itself is a row of its own that the
        // calendar never reads. Either way the day is blank — the calendar is not a scoreboard.
        val days = calendarDays(
            sessions = listOf(CalendarSession(MONDAY, trained = listOf(squat)), CalendarSession(TUESDAY, trained = emptyList())),
            weighIns = emptyList(),
        )

        assertFalse(TUESDAY in days)
        assertEquals(setOf(MONDAY), days.keys)
    }

    @Test
    fun `a day counts working sets per muscle, direct as one and indirect as a half, and carries its routine day`() {
        val bench = squat.copy(id = "bench", primaryMuscles = listOf("chest"), secondaryMuscles = listOf("triceps"))
        val days = calendarDays(
            sessions = listOf(CalendarSession(MONDAY, trained = listOf(bench), sets = mapOf("bench" to 4), name = "Push")),
            weighIns = emptyList(),
        )

        val monday = days.getValue(MONDAY)
        assertEquals("Push", monday.name)
        assertEquals(mapOf("chest" to 4.0, "triceps" to 2.0), monday.muscleSets)
    }

    private val squat = exercise("Barbell Squat", cardio = false)
    private val run = exercise("Running, Treadmill", cardio = true)

    private fun exercise(name: String, cardio: Boolean) = Exercise(
        id = name.replace(' ', '_'),
        bankName = name,
        primaryMuscles = listOf("quadriceps"),
        secondaryMuscles = emptyList(),
        equipment = null,
        mechanic = null,
        force = null,
        category = if (cardio) "cardio" else "strength",
        level = "beginner",
        loggingType = if (cardio) LoggingType.DISTANCE_TIME else LoggingType.WEIGHT_REPS,
        unilateral = false,
    )

    private companion object {
        val MONDAY: LocalDate = LocalDate.parse("2026-09-21")
        val TUESDAY: LocalDate = MONDAY.plusDays(1)
        val WEDNESDAY: LocalDate = MONDAY.plusDays(2)
    }
}
