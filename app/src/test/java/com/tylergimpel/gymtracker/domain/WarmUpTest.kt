package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ramp up to a working weight, so that nobody works one out in their head at the rack.
 *
 * Two things decide how long it is: how heavy the work is, and how much of the session already
 * warmed the same muscle. The second is the one people forget — a ramp belongs to the muscle
 * rather than to the exercise, and the third chest movement of an evening does not need one.
 */
class WarmUpTest {

    @Test
    fun `a working weight well above an empty bar gets a ramp up to it`() {
        val ramp = warmUpsFor(Kilograms(100.0), squat)

        // Five bars' worth of weight earns the full ramp, and it climbs: roughly 40, 55, 70 and
        // 85 per cent, with the reps coming down as the weight goes up.
        assertEquals(4, ramp.size)
        assertEquals(listOf(40.0, 55.0, 70.0, 85.0), ramp.map { it.load!!.value })
        assertEquals(listOf(8, 5, 3, 2), ramp.map { it.reps })
    }

    @Test
    fun `the ramp never runs past four sets however heavy the work is`() {
        // A first set is a warm-up, not the work itself: five rungs before the real thing is how a
        // session runs long.
        assertEquals(4, warmUpsFor(Kilograms(200.0), squat).size)
        assertEquals(4, warmUpsFor(Kilograms(400.0), squat).size)
    }

    @Test
    fun `light work gets a shorter ramp`() {
        // A 20 kg set does not deserve four warm-up sets, and one barely heavier than the bar
        // has nothing to ramp through at all.
        assertEquals(0, warmUpsFor(Kilograms(20.0), squat).size)
        assertEquals(1, warmUpsFor(Kilograms(40.0), squat).size)
        assertEquals(2, warmUpsFor(Kilograms(60.0), squat).size)
    }

    @Test
    fun `light isolation work gets none at all`() {
        // A curl is not a movement anybody needs three rehearsals of.
        assertEquals(0, warmUpsFor(Kilograms(40.0), curl).size)
        assertEquals(0, warmUpsFor(Kilograms(60.0), curl).size)
        // Heavy isolation work still earns one.
        assertEquals(1, warmUpsFor(Kilograms(80.0), curl).size)
    }

    @Test
    fun `a compound on a machine ramps like isolation work, not like the barbell version`() {
        // A machine holds the lifter on its path, so a leg press asks for less rehearsal than the
        // same pattern under a bar. Spec #25 skips the ramp for "isolation and machine work", and
        // reading the mechanic alone gave a 200 kg leg press the full four rungs.
        assertEquals(2, warmUpsFor(Kilograms(200.0), legPress).size)
        assertEquals(4, warmUpsFor(Kilograms(200.0), squat).size)

        // And light machine work, like light isolation work, gets none.
        assertEquals(0, warmUpsFor(Kilograms(40.0), legPress).size)
    }

    @Test
    fun `a muscle the session already warmed gets fewer, and then none`() {
        val cold = warmUpsFor(Kilograms(100.0), squat, alreadyWorkedTheMuscle = 0)
        val second = warmUpsFor(Kilograms(100.0), squat, alreadyWorkedTheMuscle = 1)
        val third = warmUpsFor(Kilograms(100.0), squat, alreadyWorkedTheMuscle = 2)
        val fifth = warmUpsFor(Kilograms(100.0), squat, alreadyWorkedTheMuscle = 4)

        // The first two movements warmed the muscle; the third does not need to do it again.
        assertEquals(4, cold.size)
        assertEquals(3, second.size)
        assertEquals(2, third.size)
        assertEquals(0, fifth.size)
    }

    @Test
    fun `every warm-up weight is one that can be loaded`() {
        val ramp = warmUpsFor(Kilograms(102.5), squat, rounding = Increment(Kilograms(5.0)))

        // The same rule as every other prescribed weight: rounded down to something the lifter
        // can actually put on the bar, never up past it.
        assertTrue(ramp.all { it.load!!.value % 5.0 == 0.0 })
        assertTrue(ramp.all { it.load!!.value < 102.5 })
    }

    @Test
    fun `a warm-up asks for a plain rep count rather than a range`() {
        val ramp = warmUpsFor(Kilograms(100.0), squat)

        // Nothing here is progressed, so nothing here has a top to reach. A range on a warm-up
        // would invite a lifter to chase it.
        assertTrue(ramp.all { it.reps != null })
    }

    @Test
    fun `a machine that is helping has nothing to ramp`() {
        // The ramp on an assisted machine would be *more* help, which is not a warm-up. The way
        // to warm up on one is to do a set on it.
        assertEquals(0, warmUpsFor(Kilograms(-30.0), assistedMachine).size)
    }

    private companion object {
        val squat = exercise("compound")
        val curl = exercise("isolation")
        val legPress = exercise("compound", equipment = "machine")
        val assistedMachine = exercise("compound", LoggingType.ASSISTED)

        fun exercise(
            mechanic: String,
            loggingType: LoggingType = LoggingType.WEIGHT_REPS,
            equipment: String = "barbell",
        ) = Exercise(
            id = "Test",
            bankName = "Test",
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = emptyList(),
            equipment = equipment,
            mechanic = mechanic,
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = loggingType,
            unilateral = false,
        )
    }
}
