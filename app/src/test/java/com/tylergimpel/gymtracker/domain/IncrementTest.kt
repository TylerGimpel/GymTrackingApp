package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a weight can actually be, on the equipment in front of the lifter.
 *
 * Three tiers because three kinds of equipment round differently, and one rule across all of
 * them: **never offer a weight nobody can build**. A recommendation of 63.7 kg asks for
 * arithmetic between sets, which is the job this app exists to take away.
 */
class IncrementTest {

    @Test
    fun `a step rounds down to a multiple of itself`() {
        val dumbbells = Increment(Kilograms(2.5))

        assertKilograms(22.5, dumbbells.roundDown(Kilograms(23.9)))
        assertKilograms(22.5, dumbbells.roundDown(Kilograms(22.5)))
    }

    @Test
    fun `a bar with no plates on it is a weight like any other`() {
        val bar = platesOf(bar = 20.0)

        // Under the bar there is nothing to take off. The bar is the floor of a barbell exercise
        // and offering less would be offering something that cannot be set up.
        assertKilograms(20.0, bar.roundDown(Kilograms(15.0)))
        assertKilograms(20.0, bar.roundDown(Kilograms(20.0)))
    }

    @Test
    fun `a barbell is only ever what the plates in the room can build`() {
        // A gym with nothing lighter than a twenty, which is a real and annoying gym.
        val heavy = platesOf(bar = 20.0, 20.0 to 2)

        // 60 and 100 are buildable; everything between them is not, and asking for 90 gets 60
        // rather than a number the lifter has to discover is impossible.
        assertKilograms(60.0, heavy.roundDown(Kilograms(90.0)))
        assertKilograms(100.0, heavy.roundDown(Kilograms(100.0)))
    }

    @Test
    fun `plates are counted in pairs, because a bar loaded one side is not a lift`() {
        val one = platesOf(bar = 20.0, 10.0 to 1)

        // One pair of tens: 20, or 40. Never 30, which would be a ten on one end.
        assertKilograms(40.0, one.roundDown(Kilograms(55.0)))
        assertKilograms(20.0, one.roundDown(Kilograms(39.0)))
    }

    @Test
    fun `an ordinary rack builds an ordinary weight`() {
        val rack = ordinaryRack()

        assertKilograms(100.0, rack.roundDown(Kilograms(101.2)))
        assertKilograms(102.5, rack.roundDown(Kilograms(102.5)))
        // Plates go on in pairs, so a total moves by twice the smallest one: 2.5 kg apart, even
        // though there is a 1.25 in the room. Half a kilo short of 102.5 is 100.
        assertKilograms(100.0, rack.roundDown(Kilograms(102.4)))
    }

    @Test
    fun `the bar is shown loaded, heaviest plate first, per side`() {
        val rack = ordinaryRack()

        // What goes on one end, heaviest first, in the order somebody actually loads it — and
        // as few plates as will do it, because every one is a trip to the rack.
        assertEquals(
            listOf(20.0, 15.0),
            rack.perSide(Kilograms(90.0)).map { it.value },
        )
    }

    @Test
    fun `nothing is asked of a lifter that the plate line cannot show`() {
        val rack = ordinaryRack()
        val awkward = rack.roundDown(Kilograms(77.3))

        // The rounding and the plate line read the same inventory, so the line always adds up to
        // the weight above it. Two answers from one source is the point of it being one source.
        val built = rack.bar.value + 2 * rack.perSide(awkward).sumOf { it.value }
        assertKilograms(awkward.value, Kilograms(built))
    }

    @Test
    fun `a machine offers only a notch it has been seen at`() {
        // An irregular stack, which is what most of them are: the numbers on the plates are a
        // gym-local scale and the gaps between them are not even.
        val machine = Increment.Stack(kilograms(20.0, 35.0, 45.0, 60.0))

        assertKilograms(45.0, machine.roundDown(Kilograms(52.0)))
        assertKilograms(35.0, machine.roundDown(Kilograms(35.0)))
    }

    @Test
    fun `a machine advances to the next notch rather than by an amount`() {
        val machine = Increment.Stack(kilograms(20.0, 35.0, 45.0, 60.0))

        // Fifteen, then ten: the stack decides, not arithmetic.
        assertKilograms(35.0, machine.oneStepAbove(Kilograms(20.0)))
        assertKilograms(45.0, machine.oneStepAbove(Kilograms(35.0)))
    }

    @Test
    fun `at the top of what a machine has been seen at, it stops guessing`() {
        val machine = Increment.Stack(kilograms(20.0, 35.0))

        // The app has never seen the pin below 35 and has no idea what is under it. Offering a
        // number would be inventing a notch; holding is what lets the lifter pick the next one
        // themselves, which is how the app finds out it exists.
        assertKilograms(35.0, machine.oneStepAbove(Kilograms(35.0)))
    }

    @Test
    fun `a target under everything a machine has been seen at gets the lightest notch`() {
        val machine = Increment.Stack(kilograms(20.0, 35.0))

        // The one place the answer is not below what was asked for, because the alternative is
        // inventing a lighter notch. Twenty is the lightest pin the app knows to exist, and a
        // lifter who wants less can select it and teach the app it is there.
        assertKilograms(20.0, machine.roundDown(Kilograms(12.0)))
    }

    @Test
    fun `an assisted machine rounds toward more help`() {
        // Signed, like everything else: help is weight taken off the lifter, so the stack runs
        // backwards and "down" means the machine doing more of the work.
        val assisted = Increment.Stack(kilograms(-40.0, -30.0, -20.0))

        assertKilograms(-30.0, assisted.roundDown(Kilograms(-25.0)))
        // And a step harder is less help, which is the conservative direction inverted once and
        // never thought about again.
        assertKilograms(-20.0, assisted.oneStepAbove(Kilograms(-30.0)))
    }

    private companion object {
        fun assertKilograms(expected: Double, actual: Kilograms) =
            assertEquals(expected, actual.value, 0.0001)

        fun kilograms(vararg values: Double) = values.map(::Kilograms)

        fun platesOf(bar: Double, vararg pairs: Pair<Double, Int>) = Increment.Plates(
            bar = Kilograms(bar),
            plates = pairs.map { (weight, count) -> PlatePair(Kilograms(weight), count) },
        )

        /** A bar and one pair of everything, which is what most gyms have. */
        fun ordinaryRack() = platesOf(
            bar = 20.0,
            20.0 to 2,
            15.0 to 1,
            10.0 to 1,
            5.0 to 1,
            2.5 to 1,
            1.25 to 1,
        )
    }
}
