package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * On an assisted machine, progress runs backwards, and everything the app says about it has to
 * run backwards too.
 *
 * **Getting this wrong is the worst failure available to this engine**: the app would coach
 * somebody to get weaker while showing them progress, and both halves of that are invisible. It
 * looks like success.
 *
 * Every assertion here is deliberately stated in the direction that looks wrong at a glance —
 * *less is better*, *the smaller number is the record*, *advancing subtracts*. That is the point.
 * A future reader who "corrects" one of the comparisons these cover will fail this file loudly
 * rather than ship a quiet lie, which is the only protection a rule this counter-intuitive can
 * have.
 *
 * The arithmetic that makes it true is a **signed load** (ADR 0004): help is weight taken off the
 * lifter, so it counts downward from nothing and needing none at all outranks any amount of it.
 * Nothing below special-cases anything; it all falls out of that one sign.
 */
class AssistedInversionTest {

    @Test
    fun `needing no help at all outranks any amount of it`() {
        val unassisted = Performance.BodyweightReps(addedWeight = null, reps = 8)
        val helped = Performance.Assisted(Kilograms(20.0), reps = 8)

        // The comparison every other one here is built on.
        assertTrue(unassisted.loadOrNull!!.value > helped.loadOrNull!!.value)
    }

    @Test
    fun `less help is the better estimate`() {
        val lots = Anchor.from(sessions(assisted(40.0, 8)))!!
        val little = Anchor.from(sessions(assisted(20.0, 8)))!!

        assertTrue(little.estimated1rm.value > lots.estimated1rm.value)
    }

    @Test
    fun `beating the range takes help away rather than adding it`() {
        val history = TrainingHistory(
            trained(
                sets(askedAssisted(30.0, reps = 6, did = 9)),
                sets(askedAssisted(30.0, reps = 9, did = 9)),
            ),
        )

        val next = history.next()

        // 27.5 kg of help rather than 32.5. A lifter reading this sees a smaller number and is
        // right to read it as harder.
        assertEquals(-27.5, next.load!!.value, TOLERANCE)
        assertTrue(next.load!!.value > -30.0)
    }

    @Test
    fun `falling short gives more help rather than less`() {
        val history = TrainingHistory(
            trained(
                sets(askedAssisted(30.0, reps = 8, did = 3)),
                sets(askedAssisted(30.0, reps = 8, did = 3)),
            ),
        )

        val next = history.next()

        // The graded ladder pointed the other way: backing off on this machine means the machine
        // taking more of the lifter's weight.
        assertTrue(next.load!!.value < -30.0)
    }

    @Test
    fun `rounding a prescription leaves more help on the machine, never less`() {
        val history = TrainingHistory(trained(sets(askedAssisted(30.0, reps = 7, did = 7))))

        // Rounding down in signed terms is the lighter plate on a bar and the bigger pin on a
        // machine. Both are the conservative answer; one rule covers them.
        assertTrue(history.next().load!!.value <= -30.0)
    }

    @Test
    fun `a record on an assisted machine is a smaller number than the one it beat`() {
        val history = TrainingHistory(
            trained(sets(assisted(30.0, 8)), sets(assisted(25.0, 8))),
        )

        val record = personalRecords(history).single { it.reps == 8 }

        // 25 kg of help beats 30 kg of help, so the record reads as the smaller figure. A lifter
        // looking at it sees their assistance coming down, which is exactly what progress is here.
        assertEquals(-25.0, record.load.value, TOLERANCE)
        assertTrue(history.beatsRecord(PersonalRecord(8, Kilograms(-20.0))))
        assertFalse(history.beatsRecord(PersonalRecord(8, Kilograms(-30.0))))
    }

    @Test
    fun `nothing reads more help as progress`() {
        val history = TrainingHistory(trained(sets(assisted(20.0, 8))))

        // Taking more help than the record is not a record, the estimate does not rise for it,
        // and the prescription that follows does not get easier. Three ways of asking the same
        // question, because a single one of them reading backwards is enough to do the damage.
        assertFalse(history.beatsRecord(PersonalRecord(8, Kilograms(-30.0))))
        assertTrue(
            Anchor.from(sessions(assisted(30.0, 8)))!!.estimated1rm.value <
                Anchor.from(sessions(assisted(20.0, 8)))!!.estimated1rm.value,
        )
    }

    @Test
    fun `a calibration ramp on an assisted machine takes help away as it climbs`() {
        val ramp = CalibrationRamp.of(assistedMachine)!!

        // Every rung harder than the last, which on this machine is less help each time — and it
        // stops short of none at all, because help of nothing is a pull-up rather than a setting.
        val rungs = ramp.rungs.map { it.value }
        assertEquals(rungs.sorted(), rungs)
        assertTrue(rungs.all { it < 0.0 })
    }

    private fun TrainingHistory.next(): Prescription =
        nextSetOf(progressIn(SIX_TO_EIGHT)!!)

    private companion object {
        val SIX_TO_EIGHT = RepRange(6, 8)
        const val TOLERANCE = 0.0001

        val assistedMachine = Exercise(
            id = "Assisted_Pull_Up_Machine",
            bankName = "Assisted Pull-Up Machine",
            primaryMuscles = listOf("lats"),
            secondaryMuscles = emptyList(),
            equipment = "machine",
            mechanic = "compound",
            force = "pull",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.ASSISTED,
            unilateral = false,
        )

        fun trained(vararg sessions: List<TrainedSet>): List<TrainedSession> =
            sessions.mapIndexed { index, sets ->
                TrainedSession(LocalDate.of(2026, 9, index + 1), sets)
            }

        fun sessions(vararg sets: TrainedSet) = trained(sets.toList())

        fun sets(vararg sets: TrainedSet): List<TrainedSet> = sets.toList()

        fun assisted(help: Double, reps: Int) =
            TrainedSet(SetType.WORKING, Performance.Assisted(Kilograms(help), reps))

        fun askedAssisted(help: Double, reps: Int, did: Int) = TrainedSet(
            type = SetType.WORKING,
            performance = Performance.Assisted(Kilograms(help), did),
            prescription = Prescription(load = Kilograms(-help), reps = reps),
        )
    }
}
