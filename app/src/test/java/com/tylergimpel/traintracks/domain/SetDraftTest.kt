package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * What the logging screen has typed so far, and what it means.
 *
 * The screen holds text; the set that reaches the database holds a weight in canonical kilograms
 * and a rep count. This is where one becomes the other, so it is also where junk is stopped — and
 * where the unit a lifter is reading turns a typed number into a weight.
 */
class SetDraftTest {

    @Test
    fun `a whole number of kilograms is read as typed`() {
        assertEquals(60.0, SetDraft("60", "5").weight?.value)
    }

    @Test
    fun `a fractional weight survives, because plates come in halves`() {
        assertEquals(62.5, SetDraft("62.5", "5").weight?.value)
    }

    @Test
    fun `a comma is accepted as a decimal point`() {
        // Several locales put a comma on the number pad, and a lifter typing 62,5 means 62.5 rather
        // than sixty-two.
        assertEquals(62.5, SetDraft("62,5", "5").weight?.value)
    }

    @Test
    fun `surrounding whitespace does not change the weight`() {
        assertEquals(62.5, SetDraft("  62.5 ", "5").weight?.value)
    }

    @Test
    fun `a bodyweight set may weigh nothing`() {
        assertEquals(0.0, SetDraft("0", "8").weight?.value)
        assertTrue(SetDraft("0", "8").isLoggable)
    }

    @Test
    fun `a weight that is not a number is not a weight`() {
        assertNull(SetDraft("", "5").weight)
        assertNull(SetDraft("heavy", "5").weight)
        assertNull(SetDraft("-5", "5").weight)
    }

    @Test
    fun `reps are whole and positive`() {
        assertEquals(5, SetDraft("60", "5").reps)
        assertNull(SetDraft("60", "0").reps)
        assertNull(SetDraft("60", "-3").reps)
        assertNull(SetDraft("60", "3.5").reps)
        assertNull(SetDraft("60", "").reps)
    }

    @Test
    fun `a set is loggable only once both halves read`() {
        assertTrue(SetDraft("60", "5").isLoggable)
        assertFalse(SetDraft("60", "").isLoggable)
        assertFalse(SetDraft("", "5").isLoggable)
        assertFalse(SetDraft("", "").isLoggable)
    }

    @Test
    fun `a weight typed in pounds is a weight in kilograms`() {
        val draft = SetDraft("225", "5", displayUnit = WeightUnit.POUNDS)

        assertEquals(102.05828325, draft.weight?.value!!, 1e-9)
    }

    @Test
    fun `reading the same draft in another unit rewrites the number, not the weight`() {
        val kilograms = SetDraft("100", "5")

        val pounds = kilograms.readIn(WeightUnit.POUNDS)

        assertEquals("220.46", pounds.weightText)
        // What the lifter has in their hands has not moved, so neither has what would be stored.
        assertEquals(100.0, pounds.weight?.value!!, 0.01)
    }

    @Test
    fun `a blank weight stays blank when the unit changes`() {
        // Nothing has been typed, so there is nothing to convert — putting a 0 in the field here
        // would be the app entering a weight on the lifter's behalf.
        assertEquals("", SetDraft("", "5").readIn(WeightUnit.POUNDS).weightText)
    }

    @Test
    fun `reading in the unit already chosen leaves the draft exactly as typed`() {
        // Untouched text matters: 62,50 is what someone half way through typing has, and rewriting
        // it to 62.5 under them moves the cursor.
        val draft = SetDraft("62,50", "5")

        assertEquals(draft, draft.readIn(WeightUnit.KILOGRAMS))
    }

    @Test
    fun `stepping moves by the display unit's own step`() {
        assertEquals("62.5", SetDraft("60", "5").stepWeight(1).weightText)
        assertEquals("230", SetDraft("225", "5", displayUnit = WeightUnit.POUNDS).stepWeight(1).weightText)
    }

    @Test
    fun `a step in pounds is five pounds, not a converted two and a half kilograms`() {
        val stepped = SetDraft("225", "5", displayUnit = WeightUnit.POUNDS).stepWeight(1)

        // 5 lb is 2.27 kg. A stepper that moved by 2.5 kg would show 230.51 lb and be useless at a
        // machine whose pins are five pounds apart.
        assertEquals(230.0, WeightUnit.POUNDS.fromKilograms(stepped.weight!!), 0.0)
    }

    @Test
    fun `stepping down stops at nothing rather than going negative`() {
        assertEquals("0", SetDraft("2", "5").stepWeight(-1).weightText)
    }

    @Test
    fun `stepping an empty field starts from nothing`() {
        assertEquals("2.5", SetDraft("", "5").stepWeight(1).weightText)
        assertEquals("0", SetDraft("", "5").stepWeight(-1).weightText)
    }

    @Test
    fun `a draft reads in kilograms until it is told otherwise`() {
        assertEquals(WeightUnit.DEFAULT, SetDraft().displayUnit)
    }

    @Test
    fun `a timed set is loggable on a duration alone`() {
        // A plank has no weight and no reps to wait for, so requiring them would make it
        // unloggable — which is exactly what it is today.
        val draft = SetDraft(loggingType = LoggingType.TIME, durationText = "90")

        assertTrue(draft.isLoggable)
        assertEquals(Performance.Timed(Duration.ofSeconds(90)), draft.performance)
    }

    @Test
    fun `a timed set is not loggable until the duration reads as a length of time`() {
        assertFalse(SetDraft(loggingType = LoggingType.TIME).isLoggable)
        assertFalse(SetDraft(loggingType = LoggingType.TIME, durationText = "a while").isLoggable)
        // Nobody held a plank for no time at all.
        assertFalse(SetDraft(loggingType = LoggingType.TIME, durationText = "0").isLoggable)
    }

    @Test
    fun `a weight typed against a timed exercise is not recorded`() {
        // The controls do not offer a weight field for a plank, but a draft carried over from the
        // previous exercise must not smuggle one into the set that is logged.
        val draft = SetDraft(
            loggingType = LoggingType.TIME,
            weightText = "60",
            repsText = "5",
            durationText = "60",
        )

        assertEquals(Performance.Timed(Duration.ofSeconds(60)), draft.performance)
    }

    @Test
    fun `a weight-and-reps set still needs both halves`() {
        val draft = SetDraft(weightText = "60", repsText = "5")

        assertEquals(Performance.WeightAndReps(Kilograms(60.0), 5), draft.performance)
        assertNull(SetDraft(weightText = "60").performance)
        assertNull(SetDraft(repsText = "5").performance)
    }

    @Test
    fun `a draft records weight and reps until it is told which exercise it is for`() {
        assertEquals(LoggingType.WEIGHT_REPS, SetDraft().loggingType)
    }

    @Test
    fun `a distance-and-time set needs both halves`() {
        val draft = SetDraft(
            loggingType = LoggingType.DISTANCE_TIME,
            durationText = "1680",
            distanceText = "5",
        )

        assertTrue(draft.isLoggable)
        assertEquals(
            Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28)),
            draft.performance,
        )
        assertNull(SetDraft(loggingType = LoggingType.DISTANCE_TIME, distanceText = "5").performance)
        assertNull(SetDraft(loggingType = LoggingType.DISTANCE_TIME, durationText = "1680").performance)
    }

    @Test
    fun `a lifter who reads pounds runs in miles`() {
        val draft = SetDraft(
            loggingType = LoggingType.DISTANCE_TIME,
            durationText = "1680",
            distanceText = "3.1",
            lifterUnit = WeightUnit.POUNDS,
        )

        // One setting, so there is no second one to keep in step with it.
        assertEquals(DistanceUnit.MILES, draft.distanceUnit)
        assertEquals(4988.9664, (draft.performance as Performance.DistanceAndTime).distance.value, 1e-6)
    }

    @Test
    fun `a machine labelled in pounds does not decide what a distance is read in`() {
        // The two units answer different questions. A treadmill somebody once set to pounds — which
        // it could be, because this exercise showed a weight field before it recorded distance —
        // must not start reporting miles to a lifter who thinks in kilometres.
        val draft = SetDraft(
            loggingType = LoggingType.DISTANCE_TIME,
            durationText = "1680",
            distanceText = "5",
            displayUnit = WeightUnit.POUNDS,
            lifterUnit = WeightUnit.KILOGRAMS,
        )

        assertEquals(DistanceUnit.KILOMETRES, draft.distanceUnit)
        assertEquals(5000.0, (draft.performance as Performance.DistanceAndTime).distance.value, 0.0)
    }

    @Test
    fun `a distance that is not a number is not a distance`() {
        assertNull(
            SetDraft(loggingType = LoggingType.DISTANCE_TIME, durationText = "600", distanceText = "far")
                .performance,
        )
        // Nobody ran nowhere.
        assertNull(
            SetDraft(loggingType = LoggingType.DISTANCE_TIME, durationText = "600", distanceText = "0")
                .performance,
        )
    }

    @Test
    fun `a pull-up needs only its reps, and the belt is optional`() {
        val draft = SetDraft(loggingType = LoggingType.BODYWEIGHT_REPS, repsText = "8")

        assertTrue(draft.isLoggable)
        assertEquals(Performance.BodyweightReps(addedWeight = null, reps = 8), draft.performance)
    }

    @Test
    fun `a belt is recorded against the same exercise as an unweighted set of it`() {
        // One exercise, not two: adding a belt must not split a history in half.
        val draft = SetDraft(
            loggingType = LoggingType.BODYWEIGHT_REPS,
            repsText = "8",
            addedWeightText = "20",
        )

        assertEquals(Performance.BodyweightReps(Kilograms(20.0), reps = 8), draft.performance)
    }

    @Test
    fun `a belt typed in pounds is the kilograms it actually weighs`() {
        val draft = SetDraft(
            loggingType = LoggingType.BODYWEIGHT_REPS,
            repsText = "8",
            addedWeightText = "45",
            displayUnit = WeightUnit.POUNDS,
        )

        assertEquals(
            20.41,
            (draft.performance as Performance.BodyweightReps).addedWeight!!.value,
            0.01,
        )
    }

    @Test
    fun `an assisted set records the help it was set to`() {
        val draft = SetDraft(
            loggingType = LoggingType.ASSISTED,
            repsText = "8",
            addedWeightText = "20",
        )

        assertEquals(Performance.Assisted(Kilograms(20.0), reps = 8), draft.performance)
    }

    @Test
    fun `an assisted set is not loggable until it says how much help it had`() {
        // The machine was set to something, and how much is the whole measurement: an assisted
        // pull-up with the number missing says nothing about whether it was hard.
        assertFalse(SetDraft(loggingType = LoggingType.ASSISTED, repsText = "8").isLoggable)
    }

    @Test
    fun `a recommendation arrives in the fields, so accepting it is one tap and no typing`() {
        val draft = SetDraft().showing(prescribing(60.0, 6))

        assertEquals("60", draft.weightText)
        assertEquals("6", draft.repsText)
        assertEquals(Performance.WeightAndReps(Kilograms(60.0), reps = 6), draft.performance)
    }

    @Test
    fun `a recommendation is read in the unit the machine is labelled in`() {
        val draft = SetDraft(displayUnit = WeightUnit.POUNDS).showing(prescribing(60.0, 6))

        // The number changes and the weight does not: 60 kg is what gets logged either way.
        assertEquals("132.28", draft.weightText)
        assertEquals(60.0, draft.weight!!.value, 0.01)
    }

    @Test
    fun `a pull-up with nothing prescribed to hang off a belt leaves the field empty`() {
        val draft = SetDraft(loggingType = LoggingType.BODYWEIGHT_REPS)
            .showing(prescribing(0.0, 8))

        // Zero is not a belt, and a field reading 0 would be asking the lifter to take one off.
        assertEquals("", draft.addedWeightText)
        assertEquals("8", draft.repsText)
        assertEquals(Performance.BodyweightReps(addedWeight = null, reps = 8), draft.performance)
    }

    @Test
    fun `a prescribed belt reaches the belt field rather than the weight field`() {
        val draft = SetDraft(loggingType = LoggingType.BODYWEIGHT_REPS)
            .showing(prescribing(10.0, 6))

        assertEquals("10", draft.addedWeightText)
        assertEquals("", draft.weightText)
        assertEquals(Performance.BodyweightReps(Kilograms(10.0), reps = 6), draft.performance)
    }

    @Test
    fun `prescribed assistance is typed as the machine is set, without its sign`() {
        val draft = SetDraft(loggingType = LoggingType.ASSISTED)
            .showing(prescribing(-27.5, 6))

        // Negative is how a load says "help" in storage. Nobody reads a minus sign off a pin.
        assertEquals("27.5", draft.addedWeightText)
        assertEquals(Performance.Assisted(Kilograms(27.5), reps = 6), draft.performance)
    }

    @Test
    fun `nothing is prescribed into a hold, which records a time and no load`() {
        val draft = SetDraft(loggingType = LoggingType.TIME, durationText = "60")

        assertEquals(draft, draft.showing(prescribing(20.0, 6)))
    }

    @Test
    fun `a set the app is guessing at is a calibration set, not a working one`() {
        val ramp = Recommendation.Calibrating(Prescription(load = Kilograms(20.0), reps = 5))

        val draft = SetDraft().showing(ramp)

        // The type comes with the recommendation rather than being left to the lifter: a ramp set
        // filed as working would count toward volume and corrupt the history the ramp exists to
        // establish.
        assertEquals(SetType.CALIBRATION, draft.type)
        assertEquals("20", draft.weightText)
        assertEquals("5", draft.repsText)
    }

    @Test
    fun `a set backed by history stays a working set`() {
        assertEquals(SetType.WORKING, SetDraft().showing(prescribing(60.0, 6)).type)
    }

    private fun prescribing(load: Double, reps: Int) =
        Recommendation.Prescribing(Prescription(load = Kilograms(load), reps = reps))
}
