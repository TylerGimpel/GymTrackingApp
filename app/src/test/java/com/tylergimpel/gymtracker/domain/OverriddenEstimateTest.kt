package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The engine admitting it is wrong, not the app grading the lifter (#128, ADR 0008).
 *
 * Stated as histories of what was asked and what was lifted, because the trigger is a fact about
 * the log and nothing else.
 */
class OverriddenEstimateTest {

    @Test
    fun `a month of sessions that all set their own weight offers to find the estimate again`() {
        val history = weekly(overrode = listOf(true, true, true, true, true))

        assertTrue(history.keepsOverriding())
    }

    @Test
    fun `one session in the month that followed the recommendation means the estimate is not wrong`() {
        val history = weekly(overrode = listOf(true, true, false, true, true))

        assertFalse(history.keepsOverriding())
    }

    @Test
    fun `a few overridden sessions short of a month are not yet a pattern`() {
        // Three weeks of it is a lifter having a stubborn stretch, not an engine that is wrong.
        assertFalse(weekly(overrode = listOf(true, true, true, true)).keepsOverriding())
    }

    @Test
    fun `a month that is only two sessions is too little to say anything`() {
        val history = TrainingHistory(
            listOf(session(START, asked = 60.0, lifted = 70.0), session(START.plusDays(30), asked = 60.0, lifted = 70.0)),
        )

        assertFalse(history.keepsOverriding())
    }

    @Test
    fun `sessions nothing prescribed for say nothing either way`() {
        // Imported history asked for nothing, so it can neither follow nor override.
        val imported = (0..6).map { TrainedSession(START.minusWeeks(10).plusWeeks(it.toLong()), listOf(lift(80.0))) }
        val history = TrainingHistory(imported + weekly(overrode = listOf(true, true, true, true, true)).sessions)

        assertTrue(history.keepsOverriding())
        assertFalse(TrainingHistory(imported).keepsOverriding())
    }

    @Test
    fun `a warm-up at a weight of its own is not an override`() {
        val sessions = (0..4).map { week ->
            TrainedSession(
                START.plusWeeks(week.toLong()),
                listOf(TrainedSet(SetType.WARMUP, lifted(40.0), Prescription(load = Kilograms(30.0))), asked(60.0, lifted = 60.0)),
            )
        }

        assertFalse(TrainingHistory(sessions).keepsOverriding())
    }

    @Test
    fun `history from a date keeps that date and everything after it`() {
        val history = weekly(overrode = listOf(true, true, true, true, true))

        assertEquals(3, history.from(START.plusWeeks(2)).sessions.size)
        assertEquals(5, history.from(null).sessions.size)
    }

    private fun weekly(overrode: List<Boolean>) = TrainingHistory(
        overrode.mapIndexed { week, own ->
            session(START.plusWeeks(week.toLong()), asked = 60.0, lifted = if (own) 70.0 else 60.0)
        },
    )

    private fun session(date: LocalDate, asked: Double, lifted: Double) = TrainedSession(date, listOf(asked(asked, lifted)))

    private fun asked(kg: Double, lifted: Double) =
        TrainedSet(SetType.WORKING, lifted(lifted), Prescription(load = Kilograms(kg), reps = 8))

    private fun lift(kg: Double) = TrainedSet(SetType.WORKING, lifted(kg))

    private fun lifted(kg: Double) = Performance.WeightAndReps(Kilograms(kg), 8)

    private companion object {
        val START: LocalDate = LocalDate.parse("2026-08-01")
    }
}
