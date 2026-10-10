package com.tylergimpel.traintracks.domain

import kotlin.math.round

/**
 * A distance in metres, the canonical unit every distance is stored in.
 *
 * Canonical for the reason weight is: a treadmill labelled in miles and a running track measured in
 * kilometres are the same running, and storing whichever the machine showed would put a step change
 * in the history the week a lifter changed gyms. What they read is a separate question, answered by
 * [DistanceUnit].
 */
@JvmInline
value class Metres(val value: Double)

/** The international mile, exactly. Defined rather than approximated, as the pound is. */
private const val METRES_PER_MILE = 1609.344

private const val METRES_PER_KILOMETRE = 1000.0

/**
 * How a distance is read, as opposed to how far it is.
 *
 * There is no per-exercise setting here, deliberately. A **Display unit** belongs to a machine,
 * because the gym's lb lat pulldown is labelled in pounds whoever walks up to it; a distance belongs
 * to the person reading it, and somebody who thinks in pounds does not think in kilometres. So this
 * is chosen once, [alongside] the lifter's **Preferred unit**, and cannot drift out of step with it.
 */
enum class DistanceUnit(
    /** What one of this unit covers, which is what makes the conversion one multiplication. */
    private val metresEach: Double,
) {
    KILOMETRES(METRES_PER_KILOMETRE),
    MILES(METRES_PER_MILE),
    ;

    /** What [distance] reads as on a machine labelled in this unit. */
    fun fromMetres(distance: Metres): Double = readable(distance.value / metresEach)

    /** How far an [amount] read in this unit actually is. */
    fun toMetres(amount: Double): Metres = Metres(amount * metresEach)

    /**
     * A reading rounded to the hundredth of this unit.
     *
     * Ten metres in kilometres and sixteen in miles — finer than any treadmill's own display, so
     * nothing a lifter can read off one is lost, and coarse enough that 5 km does not come back as
     * 3.1068559611866697 miles.
     */
    private fun readable(amount: Double): Double = round(amount * HUNDREDTHS) / HUNDREDTHS

    companion object {
        private const val HUNDREDTHS = 100.0

        /**
         * The distance unit that belongs with [weights].
         *
         * One choice rather than two: a lifter who reads pounds runs in miles, and letting the two
         * be set apart would mean a screen showing 102 kg and 5 km to somebody who asked for
         * neither. This is why there is no `stored` form of this enum and nothing writes one: a
         * distance unit is derived wherever it is needed, so there is nothing to keep in step.
         */
        fun alongside(weights: WeightUnit): DistanceUnit = when (weights) {
            WeightUnit.KILOGRAMS -> KILOMETRES
            WeightUnit.POUNDS -> MILES
        }
    }
}

/**
 * A distance as a lifter would write it in [unit]: 5 rather than 5.0, but 5.25 kept whole.
 *
 * The same rule a weight is written by, and used in the same two places — showing a logged run, and
 * seeding the field that corrects one — so a distance does not change appearance the moment it is
 * opened for correction.
 */
fun Metres.asTextIn(unit: DistanceUnit): String = unit.fromMetres(this).asAmountText()
