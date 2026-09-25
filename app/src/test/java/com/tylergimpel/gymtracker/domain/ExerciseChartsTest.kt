package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What an exercise's charts plot, tested through their data rather than their pixels (#28).
 */
class ExerciseChartsTest {

    @Test
    fun `each session is one point from its best set, and the line is the engine's own estimate`() {
        val history = TrainingHistory(
            listOf(
                session(0, lift(100.0, 5), lift(90.0, 5)),
                session(1, lift(102.5, 5)),
                session(2, lift(105.0, 5), lift(80.0, 10)),
            ),
        )

        val series = history.strengthSeries()

        assertEquals(3, series.size)
        // The best set, not the first or the last: the heavy five, not the back-off.
        assertEquals(epley(Kilograms(100.0), 5).value, series[0].best.value, TOLERANCE)
        // The line is the engine's smoothing, worked by hand: the first session taken at its word,
        // and each later one moving the estimate a third of the way toward it.
        val first = epley(Kilograms(100.0), 5).value
        val second = first + Anchor.SMOOTHING * (epley(Kilograms(102.5), 5).value - first)
        val third = second + Anchor.SMOOTHING * (epley(Kilograms(105.0), 5).value - second)
        series.zip(listOf(first, second, third)).forEach { (point, expected) ->
            assertEquals(expected, point.anchor.value, TOLERANCE)
        }
        // And it is the same number the engine prescribes from.
        assertEquals(third, Anchor.from(history.sessions)!!.estimated1rm.value, TOLERANCE)
    }

    @Test
    fun `one outstanding session spikes as a point while the line only moves part of the way`() {
        val ordinary = (0..3).map { session(it, lift(100.0, 5)) }
        val outstanding = session(4, lift(110.0, 5))

        val series = TrainingHistory(ordinary + outstanding).strengthSeries()

        // The best day is visible — and the prescription did not jump to it, which is what the
        // chart is there to show. A tenth up is ordinary progress, not an estimate that was wrong.
        val last = series.last()
        val before = series[3].anchor.value
        assertEquals(epley(Kilograms(110.0), 5).value, last.best.value, TOLERANCE)
        assertTrue(last.anchor.value < last.best.value)
        assertEquals(before + Anchor.SMOOTHING * (last.best.value - before), last.anchor.value, TOLERANCE)
    }

    @Test
    fun `strength spans imported history, whatever the import could not say about set types`() {
        val imported = (0..2).map { session(it, lift(100.0, 5), volumeReliable = false) }
        val logged = (3..4).map { session(it, lift(100.0, 5)) }

        assertEquals(5, TrainingHistory(imported + logged).strengthSeries().size)
    }

    @Test
    fun `an assisted machine charts less help as higher`() {
        val series = TrainingHistory(
            listOf(session(0, helped(30.0, 8)), session(1, helped(25.0, 8))),
        ).strengthSeries()

        // Signed: less help is the larger number, so the line climbs as the help comes off.
        assertTrue(series.all { it.best.value < 0 })
        assertTrue(series[1].best.value > series[0].best.value)
        assertTrue(series[1].anchor.value > series[0].anchor.value)
    }

    @Test
    fun `a reset estimate starts the line again from the session after it`() {
        val history = TrainingHistory((0..3).map { session(it, lift(100.0, 5)) } + session(4, lift(80.0, 5)))

        val series = history.strengthSeries(resetOn = START.plusDays(3))

        // The session after the reset is taken at its word, as a first session is.
        assertEquals(series.last().best.value, series.last().anchor.value, TOLERANCE)
    }

    @Test
    fun `volume starts where set types become known, strength does not`() {
        val imported = (0..2).map { session(it, lift(100.0, 5), lift(100.0, 5), volumeReliable = false) }
        val logged = (3..4).map { session(it, lift(100.0, 5), TrainedSet(SetType.WARMUP, lifted(60.0, 5))) }

        val volume = TrainingHistory(imported + logged).volumeSeries()

        // No step down on the day the lifter switched apps: the chart begins after it.
        assertEquals(listOf(START.plusDays(3), START.plusDays(4)), volume.points.map { it.date })
        // A warm-up is not volume once it can be told apart from the work.
        assertEquals(listOf(1, 1), volume.points.map { it.sets })
        assertTrue(volume.startsAfterHistory)
        assertEquals(5, TrainingHistory(imported + logged).strengthSeries().size)
    }

    @Test
    fun `a source that said what each set was is volume from its beginning`() {
        // Imported, and reliable: the rule reads the source's word, never whether it was imported.
        val imported = (0..2).map { session(it, lift(100.0, 5), volumeReliable = true) }

        val volume = TrainingHistory(imported).volumeSeries()

        assertEquals(3, volume.points.size)
        assertFalse(volume.startsAfterHistory)
    }

    private fun session(day: Int, vararg sets: TrainedSet, volumeReliable: Boolean = true) =
        TrainedSession(START.plusDays(day.toLong()), sets.toList(), volumeReliable = volumeReliable)

    private fun lift(kg: Double, reps: Int) = TrainedSet(SetType.WORKING, lifted(kg, reps))

    private fun lifted(kg: Double, reps: Int) = Performance.WeightAndReps(Kilograms(kg), reps)

    private fun helped(kg: Double, reps: Int) = TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(kg), reps))

    private companion object {
        val START: LocalDate = LocalDate.parse("2026-06-01")
        const val TOLERANCE = 0.0001
    }
}
