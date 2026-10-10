package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic under "this treadmill is labelled in miles and the one at my other gym is not".
 *
 * The same rule weight already follows: what is stored is one canonical unit, and a unit is only how
 * a machine is labelled. A history that stored whichever the machine showed would put a step change
 * in it the week the lifter changed gyms — the same false jump the kilogram storage exists to keep
 * out of a progress graph.
 */
class DistanceUnitTest {

    @Test
    fun `a distance in kilometres reads as itself`() {
        assertEquals(5.0, DistanceUnit.KILOMETRES.fromMetres(Metres(5000.0)), 0.0)
    }

    @Test
    fun `a distance read in kilometres is stored as the metres it actually covers`() {
        assertEquals(5000.0, DistanceUnit.KILOMETRES.toMetres(5.0).value, 0.0)
    }

    @Test
    fun `a mile is the international mile, exactly`() {
        // 1609.344 metres and nothing else: a rounded constant would put a small error into every
        // run a lifter ever reads off an imperial treadmill.
        assertEquals(1609.344, DistanceUnit.MILES.toMetres(1.0).value, 0.0)
        assertEquals(1.0, DistanceUnit.MILES.fromMetres(Metres(1609.344)), 0.0)
    }

    @Test
    fun `a run survives a round trip through the other unit`() {
        val stored = DistanceUnit.MILES.toMetres(3.1)

        val throughKilometres = DistanceUnit.KILOMETRES.toMetres(DistanceUnit.KILOMETRES.fromMetres(stored))

        assertEquals(3.1, DistanceUnit.MILES.fromMetres(throughKilometres), 0.01)
    }

    @Test
    fun `a reading is rounded to the hundredth, which is finer than any treadmill shows`() {
        // 5 km is 3.10685... miles. Showing all of that would be precision the machine does not
        // have; rounding harder would move a corrected run off the distance it stored.
        assertEquals(3.11, DistanceUnit.MILES.fromMetres(Metres(5000.0)), 0.0)
    }

    @Test
    fun `a lifter reads distance in whatever their weights are read in`() {
        // Not a second setting to keep in step: somebody reading pounds is not reading kilometres,
        // and the two coming apart is exactly the confusion one preferred unit exists to prevent.
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.alongside(WeightUnit.KILOGRAMS))
        assertEquals(DistanceUnit.MILES, DistanceUnit.alongside(WeightUnit.POUNDS))
    }

    @Test
    fun `a distance is written as a lifter would write it`() {
        assertEquals("5", Metres(5000.0).asTextIn(DistanceUnit.KILOMETRES))
        assertEquals("5.25", Metres(5250.0).asTextIn(DistanceUnit.KILOMETRES))
        assertEquals("3.11", Metres(5000.0).asTextIn(DistanceUnit.MILES))
    }
}
