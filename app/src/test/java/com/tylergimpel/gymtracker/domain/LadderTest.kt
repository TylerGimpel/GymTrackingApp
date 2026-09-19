package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the app does about a lifter who beats what it asked, and about one who does not.
 *
 * Both halves are dangerous in opposite directions, and the ladder exists because of it: advancing
 * eagerly walks somebody up to a weight they cannot lift, and treating one bad set as failure
 * demotes somebody who slept badly. So beating the range twice running earns one increment, and a
 * miss costs nothing until it repeats (ADR 0004).
 *
 * Every scenario here is a run of sessions and the single weight the next one is asked for.
 */
class LadderTest {

    @Test
    fun `beating the top of the range once changes nothing`() {
        val history = trained(session(asked(60.0, reps = 6, did = 9)))

        // Nine reps in a 6 - 8 range is a session that cleared it, and one session is a good day
        // rather than a trend. The weight it was cleared at comes back unchanged.
        assertLoad(60.0, history.next())
    }

    @Test
    fun `beating it twice running adds one increment`() {
        val history = trained(
            session(asked(60.0, reps = 6, did = 9)),
            session(asked(60.0, reps = 9, did = 9)),
        )

        // One increment, not a jump: the smallest step the equipment can make is also the one a
        // lifter is most likely to make again next week.
        assertLoad(62.5, history.next())
    }

    @Test
    fun `a weight that has just gone up asks for the bottom of the range again`() {
        val history = trained(
            session(asked(60.0, reps = 6, did = 9)),
            session(asked(60.0, reps = 9, did = 9)),
        )

        // Double progression stated whole: the weight steps and the reps start again at the bottom,
        // which is what the lifter then climbs.
        assertEquals(6, history.next().prescription.reps)
    }

    @Test
    fun `the weight holds while the reps climb through the range`() {
        val history = trained(
            session(asked(60.0, reps = 6, did = 6)),
            session(asked(60.0, reps = 6, did = 7)),
        )

        // Seven is progress inside the range rather than past it, so the bar does not move and the
        // lifter is asked for the seven they managed.
        assertLoad(60.0, history.next())
        assertEquals(7, history.next().prescription.reps)
    }

    @Test
    fun `beating the target substantially re-anchors rather than stepping`() {
        val history = trained(
            session(asked(40.0, reps = 6, did = 6)),
            // Offered 40 and did 80 for eight: the app's estimate was not slightly low, it was
            // wrong, and stepping 2.5 kg a session would take a month to admit it.
            session(overridden(offered = 40.0, took = 80.0, did = 8)),
        )

        val next = history.next()

        assertTrue(next.prescription.load!!.value > 80.0 * 0.9)
        assertEquals(6, next.prescription.reps)
    }

    @Test
    fun `a single missed session costs nothing at all`() {
        val history = trained(
            session(asked(60.0, reps = 8, did = 8)),
            session(asked(60.0, reps = 8, did = 5)),
        )

        // A poor night's sleep is not a demotion. The same weight comes back, and the app finds
        // out whether last week meant anything.
        assertLoad(60.0, history.next())
    }

    @Test
    fun `two missed sessions running back the weight off`() {
        val history = trained(
            session(asked(60.0, reps = 8, did = 5)),
            session(asked(60.0, reps = 8, did = 5)),
        )

        // About a tenth off, rounded down to something loadable: 54 is not a weight, 52.5 is.
        assertLoad(52.5, history.next())
    }

    @Test
    fun `three hold the weight and cut the work instead`() {
        val history = trained(
            session(asked(60.0, reps = 8, did = 5)),
            session(asked(60.0, reps = 8, did = 5)),
            session(asked(52.5, reps = 8, did = 5)),
        )

        val next = history.next()

        // The weight already came down once and taking it down again would chase the lifter to the
        // floor. Intensity holds; the volume gives.
        assertTrue(next is Recommendation.CuttingVolume)
        assertLoad(52.5, next)
    }

    @Test
    fun `persistent failure stops the app prescribing at all`() {
        val history = trained(
            *Array(5) { session(asked(60.0, reps = 8, did = 4)) },
        )

        // Five sessions short of the same lift is not a weight problem the app can fix by guessing
        // again, and guessing forever is how a recommendation stops being worth reading.
        assertEquals(Recommendation.Stopped, history.next())
    }

    @Test
    fun `a set taken at a different weight is never read as a miss`() {
        val history = trained(
            session(overridden(offered = 60.0, took = 50.0, did = 8)),
            session(overridden(offered = 60.0, took = 50.0, did = 8)),
        )

        // Going lighter is a decision, and backing off a weight nobody failed is its own kind of
        // wrong. The offer stands.
        assertLoad(60.0, history.next())
    }

    @Test
    fun `one good session resets the ladder`() {
        val history = trained(
            session(asked(60.0, reps = 8, did = 5)),
            session(asked(60.0, reps = 8, did = 8)),
        )

        // The miss is answered rather than carried: a run of two has to be a run.
        assertLoad(60.0, history.next())
        assertTrue(history.next() is Recommendation.Prescribing)
    }

    @Test
    fun `an exercise the app has never prescribed for is left to the estimate`() {
        // Imported history, and every set of it a weight the lifter chose. There is no ladder to
        // climb without a prescription to answer, so the weight comes from the estimate as before.
        val history = TrainingHistory(
            listOf(TrainedSession(LocalDate.of(2026, 9, 1), listOf(logged(60.0, 6)))),
        )

        assertLoad(60.0, history.next())
    }

    @Test
    fun `advancing an assisted machine takes help away rather than adding it`() {
        val history = trained(
            session(askedAssisted(30.0, reps = 6, did = 9)),
            session(askedAssisted(30.0, reps = 9, did = 9)),
        )

        // Less help is the harder set, so the advance moves toward zero: 27.5 kg of assistance
        // rather than 32.5. Getting this backwards would coach somebody to get weaker (ADR 0004).
        assertLoad(-27.5, history.next())
    }

    @Test
    fun `backing an assisted machine off gives more help rather than less`() {
        val history = trained(
            session(askedAssisted(30.0, reps = 8, did = 4)),
            session(askedAssisted(30.0, reps = 8, did = 4)),
        )

        // Ten percent more help is 33 kg, and 33 is not a setting: the machine rounds to the
        // notch that helps more, which is the same conservative direction a barbell rounds down in.
        assertLoad(-35.0, history.next())
    }

    @Test
    fun `a calibration ramp does not become the weight the ladder holds`() {
        val history = trained(
            // A first session's ramp: three rungs, each of them asked for and done.
            session(rung(20.0, did = 5), rung(30.0, did = 5), rung(40.0, did = 5)),
        )

        // The rungs are how the app found out what the lifter can lift rather than a weight to
        // hold them at, so the estimate they produced answers: 40 for five makes a 46.7 kg single,
        // which is 37.5 at the bottom of the range. Holding a rung instead would park somebody at
        // whichever one the session happened to start or end on.
        assertLoad(37.5, history.next())
    }

    @Test
    fun `stopping a ramp early is not a missed session`() {
        val history = trained(
            session(rung(20.0, did = 5), rung(30.0, did = 3)),
            session(rung(20.0, did = 5), rung(30.0, did = 3)),
        )

        // Stopping when it gets hard is what a ramp is for, and two of them running is a lifter
        // finding their weight twice rather than failing twice.
        assertTrue(history.next() is Recommendation.Prescribing)
    }


    @Test
    fun `drifting down inside the range is not a miss`() {
        val history = trained(
            session(asked(60.0, reps = 8, did = 10)),
            session(asked(60.0, reps = 10, did = 9)),
            session(asked(60.0, reps = 9, did = 8)),
        )

        // Ten, nine, eight in an 8 - 12 range is a lifter working inside the band they are meant
        // to be in. Each session is short of what was *asked* — the ask is last session's count —
        // and none of them is short of the range, so nothing has failed and nothing comes off the
        // bar. Reading it the other way demotes somebody for having an ordinary fortnight.
        assertLoad(60.0, history.next(EIGHT_TO_TWELVE))
        assertTrue(history.next(EIGHT_TO_TWELVE) is Recommendation.Prescribing)
    }

    @Test
    fun `blowing through the weight in reps re-anchors from what was lifted`() {
        val history = trained(
            session(asked(40.0, reps = 8, did = 8)),
            session(asked(40.0, reps = 8, did = 20)),
        )

        // Twenty at a weight asked for eight is not a good session, it is the wrong weight: the
        // seed was far too light and stepping it 2.5 kg a fortnight would take a season.
        //
        // The estimate cannot see this on its own — Epley stops reading reps at a dozen, so that
        // set contributes nothing to it and the anchor sits where it was. That is exactly why the
        // rule is stated in reps (spec #25), and why the weight it produces comes from the set
        // rather than from the estimate.
        assertLoad(52.5, history.next(EIGHT_TO_TWELVE))
    }

    @Test
    fun `a lift the app has stopped prescribing for stays stopped`() {
        val history = trained(
            *Array(5) { session(asked(60.0, reps = 8, did = 4)) },
            // The app asked nothing of this one, because it had stopped. The lifter trained it
            // anyway, at a weight of their own.
            session(logged(50.0, 8)),
        )

        // A session the app said nothing about cannot be the answer to why it stopped. Counting it
        // as one would have the app offering the failed weight again the very next session, which
        // is the guessing forever the rung exists to end.
        assertEquals(Recommendation.Stopped, history.next())
    }

    @Test
    fun `a session the app said nothing about does not break a run`() {
        val history = trained(
            session(asked(60.0, reps = 6, did = 9)),
            session(asked(60.0, reps = 9, did = 9)),
            session(logged(60.0, 5)),
        )

        // The advance was earned by the two sessions that answered, and a session the app asked
        // nothing of is not evidence against them.
        assertLoad(62.5, history.next())
    }

    @Test
    fun `a weight the lifter chose does break a run`() {
        val history = trained(
            session(asked(60.0, reps = 6, did = 9)),
            session(overridden(offered = 60.0, took = 70.0, did = 9)),
            session(asked(60.0, reps = 6, did = 9)),
        )

        // Two sessions past the top with a session of the lifter's own between them is not two
        // consecutive sessions, and the app waits to be shown twice in a row.
        assertLoad(60.0, history.next())
    }

    private fun TrainingHistory.next(): Recommendation = next(SIX_TO_EIGHT)

    private fun TrainingHistory.next(repRange: RepRange): Recommendation = recommendNextSet(
        history = this,
        progress = progressIn(repRange),
        calibration = null,
        setsToday = 0,
    )!!

    private fun assertLoad(expected: Double, recommendation: Recommendation) =
        assertEquals(expected, recommendation.prescription.load!!.value, TOLERANCE)

    private companion object {
        val SIX_TO_EIGHT = RepRange(6, 8)
        val EIGHT_TO_TWELVE = RepRange(8, 12)
        const val TOLERANCE = 0.0001

        fun trained(vararg sessions: List<TrainedSet>): TrainingHistory = TrainingHistory(
            sessions.mapIndexed { index, sets ->
                TrainedSession(LocalDate.of(2026, 9, index + 1), sets)
            },
        )

        fun session(vararg sets: TrainedSet): List<TrainedSet> = sets.toList()

        /** A set the app asked for at [load] for [reps], which the lifter did [did] of. */
        fun asked(load: Double, reps: Int, did: Int) = TrainedSet(
            type = SetType.WORKING,
            performance = Performance.WeightAndReps(Kilograms(load), did),
            prescription = Prescription(load = Kilograms(load), reps = reps),
        )

        /** The same, on a machine whose load is the help it is giving. */
        fun askedAssisted(help: Double, reps: Int, did: Int) = TrainedSet(
            type = SetType.WORKING,
            performance = Performance.Assisted(Kilograms(help), did),
            prescription = Prescription(load = Kilograms(-help), reps = reps),
        )

        /** A set the lifter took at a weight of their own, whatever the app offered. */
        fun overridden(offered: Double, took: Double, did: Int) = TrainedSet(
            type = SetType.WORKING,
            performance = Performance.WeightAndReps(Kilograms(took), did),
            prescription = Prescription(load = Kilograms(offered), reps = 6),
        )

        /** A rung of a calibration ramp: asked for, and not a working weight. */
        fun rung(load: Double, did: Int) = TrainedSet(
            type = SetType.CALIBRATION,
            performance = Performance.WeightAndReps(Kilograms(load), did),
            prescription = Prescription(load = Kilograms(load), reps = 5),
        )

        /** A set from before there was an engine: logged, with nothing asked of it. */
        fun logged(load: Double, reps: Int) = TrainedSet(
            type = SetType.WORKING,
            performance = Performance.WeightAndReps(Kilograms(load), reps),
        )
    }
}
