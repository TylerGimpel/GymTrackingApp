package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first session on an exercise, where the app has nothing to go on.
 *
 * It does not predict a working weight, because it cannot: the best anthropometric estimate of an
 * untrained bench press is wrong by about 20%, and measuring costs one session and is three times
 * more precise (`docs/research/cold-start-weights.md`). So it starts light and ramps, and every
 * seed here is biased low on purpose — a too-light first session is still training, and a too-heavy
 * one is how a novice gets hurt.
 */
class CalibrationRampTest {

    @Test
    fun `a barbell lift starts at the empty bar`() {
        val ramp = CalibrationRamp.of(exercise("barbell"))!!

        assertEquals(20.0, ramp.rung(0)!!.value, TOLERANCE)
    }

    @Test
    fun `the ramp climbs, and its steps shrink as the weight rises`() {
        val ramp = CalibrationRamp.of(exercise("barbell"))!!

        // Big steps while the weight is trivial, small ones as it starts to mean something: the
        // ramp is looking for the load at which the lifter stops, and stepping past it in tens is
        // how a first session gets heavy by accident.
        assertEquals(
            listOf(20.0, 30.0, 40.0, 45.0, 50.0, 55.0),
            (0..5).map { ramp.rung(it)!!.value },
        )
    }

    @Test
    fun `the ramp runs out rather than running all evening`() {
        val ramp = CalibrationRamp.of(exercise("barbell"))!!

        // Capped, because a first session that takes forty minutes to find a weight is a first
        // session somebody abandons. Whatever it reached becomes the exercise's first data point,
        // and beating the range substantially re-anchors from there in one session (#50).
        assertNull(ramp.rung(6))
    }

    @Test
    fun `a dumbbell starts lighter than a barbell, because the rack does`() {
        assertEquals(5.0, CalibrationRamp.of(exercise("dumbbell"))!!.rung(0)!!.value, TOLERANCE)
    }

    @Test
    fun `a machine starts at a low pin`() {
        // The stack number is a gym-local scale with no physical meaning, so this is not a weight
        // so much as a notch near the top of the stack. Nothing can be inferred across machines.
        assertEquals(10.0, CalibrationRamp.of(exercise("machine"))!!.rung(0)!!.value, TOLERANCE)
    }

    @Test
    fun `every rung is a weight that can actually be loaded`() {
        val ramp = CalibrationRamp.of(exercise("barbell"), Increment(Kilograms(20.0)))!!

        // A home gym with nothing but twenties. A ramp that asks for 45 there is a ramp asking for
        // arithmetic at the rack, and the seed itself is rounded like everything else.
        assertTrue((0..5).mapNotNull { ramp.rung(it)?.value }.all { it % 20.0 == 0.0 })
    }

    @Test
    fun `an assisted machine starts with generous help and takes it away`() {
        val ramp = CalibrationRamp.of(exercise("machine", LoggingType.ASSISTED))!!

        // The load is negative because help is weight taken off the lifter, so the ramp still
        // climbs — every rung is harder than the one before, which on this machine means less
        // help rather than more weight (ADR 0004).
        assertEquals(
            listOf(-40.0, -30.0, -20.0, -15.0, -10.0),
            (0..4).map { ramp.rung(it)!!.value },
        )
    }

    @Test
    fun `an assisted ramp stops before it reaches no help at all`() {
        val ramp = CalibrationRamp.of(exercise("machine", LoggingType.ASSISTED))!!

        // Help of nothing is not an assisted set: it is a pull-up, and the honest way to find out
        // whether the lifter has one is to let them log one rather than to prescribe it.
        assertTrue((0..4).mapNotNull { ramp.rung(it)?.value }.all { it < 0.0 })
        assertNull(ramp.rung(5))
    }

    @Test
    fun `a pull-up has no weight to ramp, so every set asks the same thing`() {
        val ramp = CalibrationRamp.of(exercise("body only", LoggingType.BODYWEIGHT_REPS))!!

        // The load is the lifter's own body and nothing can make it lighter, so there is no ramp
        // to climb — only a rep count to find out. The suggestion stands for the whole session
        // rather than disappearing after one set.
        assertEquals(0.0, ramp.rung(0)!!.value, TOLERANCE)
        assertEquals(0.0, ramp.rung(3)!!.value, TOLERANCE)
    }

    @Test
    fun `a first set of pull-ups is work, not a measurement`() {
        val ramp = CalibrationRamp.of(exercise("body only", LoggingType.BODYWEIGHT_REPS))!!

        // The app is still guessing — at how many, which is why the mode is calibrating — but the
        // set itself is the only load there is. Filing it as a measurement would delete a set the
        // lifter really did from their volume, which a barbell ramp's empty bar never was.
        assertEquals(SetType.WORKING, ramp.setType)
        assertEquals(SetType.WORKING, ramp.nextSet(setsToday = 0)!!.setType)
    }

    @Test
    fun `a light seed never offers the same weight twice`() {
        val ramp = CalibrationRamp.of(exercise("dumbbell"))!!

        // Stepping a 5 kg seed by 1 and by 1.25 both land on 10 kg once the rounding is done, and
        // a ramp that offers the same set twice has wasted one of the six it gets.
        assertEquals(listOf(5.0, 7.5, 10.0, 12.5), ramp.rungs.map { it.value })
    }

    @Test
    fun `coarse equipment gets a shorter ramp rather than invented weights`() {
        val ramp = CalibrationRamp.of(exercise("barbell"), Increment(Kilograms(20.0)))!!

        // A gym with nothing lighter than twenties cannot ramp finely, and the honest answer is
        // fewer rungs — not rungs above anything the steps asked for.
        assertEquals(listOf(20.0, 40.0), ramp.rungs.map { it.value })
    }

    @Test
    fun `every rung is harder than the one before it`() {
        listOf(
            CalibrationRamp.of(exercise("barbell"))!!,
            CalibrationRamp.of(exercise("dumbbell"))!!,
            CalibrationRamp.of(exercise("machine"))!!,
            CalibrationRamp.of(exercise("machine", LoggingType.ASSISTED))!!,
        ).forEach { ramp ->
            // Harder means up in signed terms on every one of them: more weight on a bar, less
            // help on a machine (ADR 0004).
            val loads = ramp.rungs.map { it.value }
            assertEquals(loads.sorted(), loads)
            assertEquals(loads.distinct(), loads)
        }
    }

    @Test
    fun `a hold is not calibrated at all`() {
        // Nothing to find: a plank records a time, and the app has nothing to say about what to do
        // on one either way.
        assertNull(CalibrationRamp.of(exercise("body only", LoggingType.TIME)))
        assertNull(CalibrationRamp.of(exercise("treadmill", LoggingType.DISTANCE_TIME)))
    }

    @Test
    fun `a ramp set asks for a plain rep count rather than a range`() {
        val ramp = CalibrationRamp.of(exercise("barbell"))!!

        val first = ramp.nextSet(setsToday = 0)!!

        assertEquals(20.0, first.prescription.load!!.value, TOLERANCE)
        assertEquals(CalibrationRamp.RAMP_REPS, first.prescription.reps)
        // Not training, and it must not be counted as any: a session spent finding out what
        // somebody can lift would otherwise corrupt the history it is establishing.
        assertEquals(SetType.CALIBRATION, first.setType)
    }

    @Test
    fun `the next rung follows the sets already done today`() {
        val ramp = CalibrationRamp.of(exercise("barbell"))!!

        assertEquals(40.0, ramp.nextSet(setsToday = 2)!!.prescription.load!!.value, TOLERANCE)
        assertNull(ramp.nextSet(setsToday = 6))
    }

    @Test
    fun `a lifter who has said nothing about themselves still gets a ramp`() {
        // The spec's most important line, as a test: nothing is required (#29). If this fails,
        // somebody has made the profile load-bearing without noticing.
        val ramp = CalibrationRamp.of(squat, body = null)!!

        assertEquals(20.0, ramp.rung(0)!!.value, TOLERANCE)
        assertTrue(ramp.rungs.isNotEmpty())
    }

    @Test
    fun `a bodyweight starts a barbell squat closer, and never below the bar`() {
        val plain = CalibrationRamp.of(squat)!!
        val profiled = CalibrationRamp.of(squat, body = BodySize(Kilograms(95.0), Sex.MALE))!!

        // The research's own example: a 95 kg man can start the squat ramp at about 40 kg rather
        // than climbing to it from the empty bar. Both are valid ramps; one takes fewer sets.
        assertTrue(profiled.rung(0)!!.value > plain.rung(0)!!.value)
        assertTrue(profiled.rung(0)!!.value <= 40.0)
        assertTrue(profiled.rungs.all { it.value >= 20.0 })
    }

    @Test
    fun `a heavier lifter is not assumed to be proportionally stronger`() {
        val lighter = CalibrationRamp.of(squat, body = BodySize(Kilograms(65.0), Sex.MALE))!!
        val heavier = CalibrationRamp.of(squat, body = BodySize(Kilograms(130.0), Sex.MALE))!!

        // Strength scales with bodyweight to about the half power, not the first (457,471 lifters,
        // exponent 0.50–0.55). Twice the bodyweight is well under twice the starting weight, which
        // is the direction a seed has to err in.
        assertTrue(heavier.rung(0)!!.value > lighter.rung(0)!!.value)
        assertTrue(heavier.rung(0)!!.value < 2 * lighter.rung(0)!!.value)
    }

    @Test
    fun `not saying your sex starts you no higher than the lower answer`() {
        val female = CalibrationRamp.of(squat, body = BodySize(Kilograms(80.0), Sex.FEMALE))!!
        val unsaid = CalibrationRamp.of(squat, body = BodySize(Kilograms(80.0), sex = null))!!
        val male = CalibrationRamp.of(squat, body = BodySize(Kilograms(80.0), Sex.MALE))!!

        // Biased low, as every seed is: without the answer, the app assumes the one that starts
        // lighter rather than guessing.
        assertEquals(female.rung(0)!!.value, unsaid.rung(0)!!.value, TOLERANCE)
        assertTrue(male.rung(0)!!.value >= female.rung(0)!!.value)
    }

    @Test
    fun `a pressing lift is raised less than a squat`() {
        val body = BodySize(Kilograms(95.0), Sex.MALE)

        val squatStart = CalibrationRamp.of(squat, body = body)!!.rung(0)!!.value
        val benchStart = CalibrationRamp.of(exercise("barbell"), body = body)!!.rung(0)!!.value

        assertTrue(benchStart <= squatStart)
    }

    @Test
    fun `a body says nothing about a machine, a dumbbell or a barbell curl`() {
        val body = BodySize(Kilograms(110.0), Sex.MALE)

        // A stack number is not a load, a dumbbell rack starts where it starts, and an isolation
        // lift has no research anchor at all. Only barbell compounds are raised.
        listOf(
            exercise("machine"),
            exercise("dumbbell"),
            exercise("barbell").copy(mechanic = "isolation", primaryMuscles = listOf("biceps")),
        ).forEach {
            assertEquals(
                CalibrationRamp.of(it)!!.rung(0)!!.value,
                CalibrationRamp.of(it, body = body)!!.rung(0)!!.value,
                TOLERANCE,
            )
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001

        val squat get() = exercise("barbell").copy(primaryMuscles = listOf("quadriceps"))

        fun exercise(equipment: String, loggingType: LoggingType = LoggingType.WEIGHT_REPS) =
            Exercise(
                id = "Test",
                bankName = "Test",
                primaryMuscles = listOf("chest"),
                secondaryMuscles = emptyList(),
                equipment = equipment,
                mechanic = "compound",
                force = "push",
                category = "strength",
                level = "beginner",
                loggingType = loggingType,
                unilateral = false,
            )
    }
}
