package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * One definition of "best" across the whole history (#123).
 */
class PersonalRecordTest {

    @Test
    fun `a heavier set at fewer reps does not displace a lighter one at more`() {
        val history = TrainingHistory(
            listOf(
                TrainedSession(DAY, listOf(lift(100.0, 5))),
                TrainedSession(DAY.plusDays(3), listOf(lift(110.0, 3), lift(90.0, 8))),
            ),
        )

        // Best at three, best at five, best at eight: three records, and all of them true.
        assertEquals(
            listOf(
                PersonalRecord(3, Kilograms(110.0)),
                PersonalRecord(5, Kilograms(100.0)),
                PersonalRecord(8, Kilograms(90.0)),
            ),
            personalRecords(history),
        )
    }

    @Test
    fun `imported sessions set records from their sets, like any other`() {
        // An import says nothing about which sets were records; the sets say it all.
        val history = TrainingHistory(
            listOf(
                TrainedSession(DAY, listOf(lift(120.0, 5)), volumeReliable = false),
                TrainedSession(DAY.plusDays(7), listOf(lift(115.0, 5))),
            ),
        )

        assertEquals(listOf(PersonalRecord(5, Kilograms(120.0))), personalRecords(history))
    }

    @Test
    fun `on an assisted machine the record is the lower number`() {
        val history = TrainingHistory(
            listOf(
                TrainedSession(DAY, listOf(TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(30.0), 8)))),
                TrainedSession(DAY.plusDays(3), listOf(TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(20.0), 8)))),
            ),
        )

        // Twenty kilograms of help, not thirty: signed, it is the larger load.
        assertEquals(listOf(PersonalRecord(8, Kilograms(-20.0))), personalRecords(history))
    }

    private fun lift(kg: Double, reps: Int) = TrainedSet(SetType.WORKING, Performance.WeightAndReps(Kilograms(kg), reps))

    private companion object {
        val DAY: LocalDate = LocalDate.parse("2026-06-01")
    }
}
