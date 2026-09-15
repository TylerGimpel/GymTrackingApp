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
    fun `a distance-and-time effort is how far and how long, and no weight at all`() {
        val performance = Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28))

        assertEquals(5000.0, performance.distance.value, 0.0)
        assertEquals(Duration.ofMinutes(28), performance.duration)
        assertNull(performance.weightOrNull)
        assertNull(performance.repsOrNull)
    }

    @Test
    fun `a duration is read off either shape that records one`() {
        // What a rest timer and a history screen ask for, without caring which of the two it is.
        assertEquals(
            Duration.ofSeconds(90),
            Performance.Timed(Duration.ofSeconds(90)).durationOrNull,
        )
        assertEquals(
            Duration.ofMinutes(28),
            Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28)).durationOrNull,
        )
        assertNull(Performance.WeightAndReps(Kilograms(60.0), 5).durationOrNull)
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
    fun `a distance-and-time exercise records how far and how long, and nothing else`() {
        assertTrue(LoggingType.DISTANCE_TIME.recordsDistance)
        assertTrue(LoggingType.DISTANCE_TIME.recordsDuration)
        assertFalse(LoggingType.DISTANCE_TIME.recordsWeight)
        assertFalse(LoggingType.DISTANCE_TIME.recordsReps)
        // A hold is timed and goes nowhere.
        assertFalse(LoggingType.TIME.recordsDistance)
    }

    @Test
    fun `a bodyweight exercise counts reps and may carry a belt`() {
        val unweighted = Performance.BodyweightReps(addedWeight = null, reps = 8)
        val belted = Performance.BodyweightReps(Kilograms(20.0), reps = 8)

        assertEquals(8, unweighted.repsOrNull)
        assertNull(unweighted.addedWeight)
        assertEquals(20.0, belted.addedWeight!!.value, 0.0)
        // Neither is a weight that was lifted off a rack: what was lifted is a body.
        assertNull(belted.weightOrNull)
    }

    @Test
    fun `an assisted exercise records the help it was given`() {
        val performance = Performance.Assisted(Kilograms(20.0), reps = 8)

        assertEquals(20.0, performance.assistance.value, 0.0)
        assertEquals(8, performance.repsOrNull)
        assertNull(performance.weightOrNull)
    }

    @Test
    fun `more added weight is progress, and less assistance is progress`() {
        // ADR 0004: for an assisted exercise less is more, and getting it the wrong way round means
        // confidently coaching somebody to get weaker. The load is signed so that the comparison
        // never has to be remembered — more is more, on every shape that has one.
        val unweighted = Performance.BodyweightReps(addedWeight = null, reps = 8).loadOrNull!!
        val belted = Performance.BodyweightReps(Kilograms(20.0), reps = 8).loadOrNull!!
        val heavilyAssisted = Performance.Assisted(Kilograms(30.0), reps = 8).loadOrNull!!
        val lightlyAssisted = Performance.Assisted(Kilograms(10.0), reps = 8).loadOrNull!!

        assertTrue(belted.value > unweighted.value)
        assertTrue(lightlyAssisted.value > heavilyAssisted.value)
        // And needing no help at all outranks any amount of it.
        assertTrue(unweighted.value > lightlyAssisted.value)
    }

    @Test
    fun `a barbell load is read straight off, and a hold has none to read`() {
        assertEquals(60.0, Performance.WeightAndReps(Kilograms(60.0), 5).loadOrNull!!.value, 0.0)
        assertNull(Performance.Timed(Duration.ofSeconds(90)).loadOrNull)
        assertNull(Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28)).loadOrNull)
    }

    @Test
    fun `a bodyweight exercise records reps and a belt, and an assisted one records help`() {
        assertEquals(
            setOf(Measurement.ADDED_WEIGHT, Measurement.REPS),
            LoggingType.BODYWEIGHT_REPS.records,
        )
        assertEquals(
            setOf(Measurement.ASSISTANCE, Measurement.REPS),
            LoggingType.ASSISTED.records,
        )
        // Neither is a weight lifted off a rack, so neither offers the field for one.
        assertFalse(LoggingType.BODYWEIGHT_REPS.recordsWeight)
        assertFalse(LoggingType.ASSISTED.recordsWeight)
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
