package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * What one effort amounted to, which is not the same question for every exercise.
 *
 * A bench press effort is a weight and a rep count. A plank is a length of time and nothing else —
 * it has no weight to record, and a zero standing in for one is a number the progression engine
 * would later read as real. So the shapes are separate types rather than a row of nullable columns,
 * and an effort that recorded nothing at all cannot be built.
 */
class PerformanceTest {

    @Test
    fun `a weight-and-reps effort is a weight and a rep count`() {
        val performance = Performance.WeightAndReps(Kilograms(60.0), reps = 5)

        assertEquals(60.0, performance.weight.value, 0.0)
        assertEquals(5, performance.reps)
    }

    @Test
    fun `a timed effort is a length of time, and has no weight to be asked for`() {
        val performance = Performance.Timed(Duration.ofSeconds(90))

        assertEquals(Duration.ofSeconds(90), performance.duration)
        // Not zero, and not a weight nobody lifted: there is no weight on this type at all.
        assertNull(performance.weightOrNull)
        assertNull(performance.repsOrNull)
    }

    @Test
    fun `a weight-and-reps effort answers for its weight and reps`() {
        val performance: Performance = Performance.WeightAndReps(Kilograms(60.0), reps = 5)

        assertEquals(60.0, performance.weightOrNull!!.value, 0.0)
        assertEquals(5, performance.repsOrNull)
    }

    @Test
    fun `a logging type says what it records, so no screen has to decide for it`() {
        assertTrue(LoggingType.WEIGHT_REPS.recordsWeight)
        assertTrue(LoggingType.WEIGHT_REPS.recordsReps)
        assertFalse(LoggingType.WEIGHT_REPS.recordsDuration)

        assertFalse(LoggingType.TIME.recordsWeight)
        assertFalse(LoggingType.TIME.recordsReps)
        assertTrue(LoggingType.TIME.recordsDuration)
    }

    @Test
    fun `every logging type records something`() {
        // A type recording nothing would leave the entry controls blank and the set unloggable,
        // which is a screen with no fields on it rather than an error anyone would notice.
        LoggingType.entries.forEach { type ->
            assertTrue(
                "$type records nothing",
                type.recordsWeight || type.recordsReps || type.recordsDuration,
            )
        }
    }

    @Test
    fun `the types whose own shape has not arrived yet still record a weight and reps`() {
        // Added weight, assistance and distance each arrive with their own ticket. Until then these
        // record what they always have, so nothing a lifter has already logged changes meaning and
        // no half-shape is written that a later ticket cannot tell apart from a hold.
        listOf(LoggingType.BODYWEIGHT_REPS, LoggingType.ASSISTED, LoggingType.DISTANCE_TIME)
            .forEach { type ->
                assertTrue("$type", type.recordsWeight && type.recordsReps)
                assertFalse("$type", type.recordsDuration)
            }
    }

    @Test
    fun `a duration reads as a clock rather than as a count of seconds`() {
        // How a lifter reads a hold: the stopwatch they were watching said 1:30, not 90.
        assertEquals("1:30", Duration.ofSeconds(90).asTimeText())
        assertEquals("0:45", Duration.ofSeconds(45).asTimeText())
        assertEquals("2:00", Duration.ofSeconds(120).asTimeText())
    }

    @Test
    fun `an hour on the clock grows an hours place rather than counting to sixty-five minutes`() {
        assertEquals("1:05:00", Duration.ofSeconds(3900).asTimeText())
    }
}
