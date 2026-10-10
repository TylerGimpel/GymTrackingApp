package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * A record registers at the time it is set, not only in a chart afterwards (#124).
 *
 * Stated as histories and the set about to be logged, because the whole question is what the
 * standing record was at the moment of logging.
 */
class RecordAnnouncementTest {

    @Test
    fun `a working set that beats the standing record at its rep count sets one`() {
        val history = TrainingHistory(listOf(session(LAST_WEEK, lift(100.0, 5))))

        assertEquals(PersonalRecord(5, Kilograms(105.0)), history.recordSetBy(SetType.WORKING, lifted(105.0, 5), TODAY))
        assertNull(history.recordSetBy(SetType.WORKING, lifted(100.0, 5), TODAY))
    }

    @Test
    fun `a record set earlier in the same session is the one to beat`() {
        // The first 105 was the record; a second 105 ten minutes later is not another one.
        val history = TrainingHistory(listOf(session(LAST_WEEK, lift(100.0, 5)), session(TODAY, lift(105.0, 5))))

        assertNull(history.recordSetBy(SetType.WORKING, lifted(105.0, 5), TODAY))
    }

    @Test
    fun `a warm-up or a calibration set is never a record, however heavy`() {
        val history = TrainingHistory(listOf(session(LAST_WEEK, lift(100.0, 5))))

        assertNull(history.recordSetBy(SetType.WARMUP, lifted(140.0, 5), TODAY))
        assertNull(history.recordSetBy(SetType.CALIBRATION, lifted(140.0, 5), TODAY))
    }

    @Test
    fun `a first session is not a wall of records`() {
        // Nothing before today, so every set would "beat" nothing: none of them is announced.
        val today = TrainingHistory(listOf(session(TODAY, lift(60.0, 8))))

        assertNull(TrainingHistory(emptyList()).recordSetBy(SetType.WORKING, lifted(60.0, 8), TODAY))
        assertNull(today.recordSetBy(SetType.WORKING, lifted(70.0, 8), TODAY))
    }

    @Test
    fun `a rep count never done before is a record once there is history to set it against`() {
        val history = TrainingHistory(listOf(session(LAST_WEEK, lift(100.0, 5))))

        assertEquals(PersonalRecord(12, Kilograms(60.0)), history.recordSetBy(SetType.WORKING, lifted(60.0, 12), TODAY))
    }

    @Test
    fun `on an assisted machine the record is less help`() {
        val history = TrainingHistory(listOf(session(LAST_WEEK, TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(30.0), 8)))))

        assertEquals(
            PersonalRecord(8, Kilograms(-25.0)),
            history.recordSetBy(SetType.WORKING, Performance.Assisted(Kilograms(25.0), 8), TODAY),
        )
        assertNull(history.recordSetBy(SetType.WORKING, Performance.Assisted(Kilograms(35.0), 8), TODAY))
    }

    private fun lifted(kg: Double, reps: Int) = Performance.WeightAndReps(Kilograms(kg), reps)

    private fun lift(kg: Double, reps: Int) = TrainedSet(SetType.WORKING, lifted(kg, reps))

    private fun session(date: LocalDate, vararg sets: TrainedSet) = TrainedSession(date, sets.toList())

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-09-24")
        val LAST_WEEK: LocalDate = TODAY.minusDays(7)
    }
}
