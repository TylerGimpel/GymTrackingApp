package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * What the app asked for, read against what happened.
 *
 * The distinction this exists to make is between a lifter overriding a recommendation and a lifter
 * missing it. They look identical in a log that records only what was done, and they want opposite
 * responses: one says the recommendation was wrong, the other says it was too much.
 */
class PrescriptionTest {

    private val prescribed = Prescription(
        load = Kilograms(60.0),
        reps = 8,
        rest = Duration.ofMinutes(2),
    )

    @Test
    fun `a set that did what it was asked was followed`() {
        val performed = Performance.WeightAndReps(Kilograms(60.0), reps = 8)

        assertEquals(SetOutcome.FOLLOWED, prescribed.outcomeOf(performed))
    }

    @Test
    fun `beating the reps asked for is still following it`() {
        // An amrap set asked for eight and given eleven has not departed from anything: the
        // prescription is a floor, and clearing it is the point.
        val performed = Performance.WeightAndReps(Kilograms(60.0), reps = 11)

        assertEquals(SetOutcome.FOLLOWED, prescribed.outcomeOf(performed))
    }

    @Test
    fun `a different weight is an override, whatever the reps did`() {
        // The lifter changed what they were doing rather than failing at it, so this must never
        // read as a miss: an engine that treats it as one would back off a weight nobody failed.
        assertEquals(
            SetOutcome.OVERRIDDEN,
            prescribed.outcomeOf(Performance.WeightAndReps(Kilograms(65.0), reps = 8)),
        )
        assertEquals(
            SetOutcome.OVERRIDDEN,
            prescribed.outcomeOf(Performance.WeightAndReps(Kilograms(50.0), reps = 3)),
        )
    }

    @Test
    fun `the weight asked for and fewer reps than asked is falling short`() {
        val performed = Performance.WeightAndReps(Kilograms(60.0), reps = 5)

        assertEquals(SetOutcome.FELL_SHORT, prescribed.outcomeOf(performed))
    }

    @Test
    fun `a weight entered in pounds still counts as the weight that was asked for`() {
        // A reading is rounded to a hundredth of a pound, so a weight that went out through pounds
        // comes back a few thousandths of a kilogram from where it started. Every one of these is a
        // lifter who loaded precisely what they were told to, and none may read as an override.
        listOf(60.0, 62.5, 100.0, 102.5).forEach { kilograms ->
            val prescription = Prescription(load = Kilograms(kilograms), reps = 5)
            val asRead =
                WeightUnit.POUNDS.toKilograms(WeightUnit.POUNDS.fromKilograms(Kilograms(kilograms)))

            assertEquals(
                "$kilograms kg read through pounds",
                SetOutcome.FOLLOWED,
                prescription.outcomeOf(Performance.WeightAndReps(asRead, reps = 5)),
            )
        }
    }

    @Test
    fun `a weight a plate away from what was asked is an override`() {
        // The tolerance forgives rounding and nothing else: the smallest plate anybody owns is
        // fifty times it.
        val prescription = Prescription(load = Kilograms(60.0), reps = 5)

        assertEquals(
            SetOutcome.OVERRIDDEN,
            prescription.outcomeOf(Performance.WeightAndReps(Kilograms(60.25), reps = 5)),
        )
    }

    @Test
    fun `a set nothing prescribed answered nothing`() {
        // Every set logged before there was an engine to prescribe one.
        val performed = Performance.WeightAndReps(Kilograms(60.0), reps = 8)

        assertNull(Prescription().outcomeOf(performed))
        assertTrue(Prescription().isEmpty)
    }

    @Test
    fun `a prescription of rest alone asks nothing of the work`() {
        val restOnly = Prescription(rest = Duration.ofMinutes(2))

        assertNull(restOnly.outcomeOf(Performance.WeightAndReps(Kilograms(60.0), reps = 8)))
    }

    @Test
    fun `an assisted set is judged by the help it needed, not by a weight it never lifted`() {
        // The load is signed, so a prescription of "20 kg of help" is met by 20 kg of help and
        // beaten by less — without this comparison knowing anything about assistance.
        val prescription = Prescription(load = Kilograms(-20.0), reps = 8)

        assertEquals(
            SetOutcome.FOLLOWED,
            prescription.outcomeOf(Performance.Assisted(Kilograms(20.0), reps = 8)),
        )
        assertEquals(
            SetOutcome.OVERRIDDEN,
            prescription.outcomeOf(Performance.Assisted(Kilograms(10.0), reps = 8)),
        )
    }
}
