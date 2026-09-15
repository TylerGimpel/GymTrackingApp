package com.tylergimpel.gymtracker.domain

import java.util.Locale
import kotlin.math.round

/**
 * The international pound, exactly.
 *
 * Defined rather than approximated because it is the whole conversion: a rounded constant would put
 * a small error into every weight a lifter ever reads off an imperial stack.
 */
private const val KILOGRAMS_PER_POUND = 0.45359237

/**
 * How a weight is read, as opposed to what it weighs.
 *
 * A unit is a display concern and only ever a display concern: storage is canonical kilograms, and
 * everything here converts a reading into one or back out again. That is what lets the kg lat
 * pulldown and the lb lat pulldown at the same gym both be logged honestly, with no step change in
 * the progress graph the week the lifter moves between them.
 *
 * **A machine's stack number is not a unit.** Pulley ratios vary between manufacturers and are not
 * labelled, so "60" on one machine and "60" on another are not the same load and nothing here tries
 * to make them one — a stack is its own gym-local scale, converted against nothing.
 */
enum class WeightUnit(
    val stored: String,
    /** What one of this unit weighs, which is what makes the conversion one multiplication. */
    private val kilogramsEach: Double,
    /**
     * What a stepper moves by in this unit.
     *
     * Deliberately the unit's own step rather than a converted one: 5 lb is 2.27 kg, so converting
     * would offer weights no barbell can be loaded to and no stack has a pin for. This is not the
     * **Increment** of the glossary, which is what a given piece of equipment can actually do.
     */
    val step: Double,
) {
    KILOGRAMS("kg", 1.0, 2.5),
    POUNDS("lb", KILOGRAMS_PER_POUND, 5.0),
    ;

    /** What [weight] reads as on a machine labelled in this unit. */
    fun fromKilograms(weight: Kilograms): Double = readable(weight.value / kilogramsEach)

    /**
     * This unit where it departs from [exercise], and nothing where it agrees with it.
     *
     * What a set records, in other words. A set that stored its unit either way would freeze its
     * own display, and an exercise switched to pounds would leave a trail of kilogram sets behind
     * it — the opposite of the one continuous history the canonical storage is for.
     */
    fun overriding(exercise: WeightUnit): WeightUnit? = takeIf { it != exercise }

    /** What an [amount] read in this unit actually weighs. */
    fun toKilograms(amount: Double): Kilograms = Kilograms(amount * kilogramsEach)

    /**
     * [amount] moved by [steps] of this unit's own [step], never below nothing.
     *
     * Plain addition rather than a snap to the nearest multiple: a lifter who typed 61 means 61,
     * and quietly rounding it to 62.5 on the way up would rewrite a number they chose.
     */
    fun stepped(amount: Double, steps: Int): Double =
        readable((amount + steps * step).coerceAtLeast(0.0))

    /**
     * A reading rounded to the hundredth of this unit.
     *
     * Rounding at all is what makes 225 lb come back as 225 rather than 225.00000000000003. A
     * hundredth is finer than any plate or stack step, so nothing a lifter can load is lost.
     *
     * It is not lossless across units: 62.5 kg reads as 137.79 lb, and correcting that set in
     * pounds stores 62.5005 kg. Half a gram, invisible at every point it is read back, and the
     * reason the rounding is not coarser — a step to the nearest quarter would move the weight by
     * enough to see.
     */
    private fun readable(amount: Double): Double = round(amount * HUNDREDTHS) / HUNDREDTHS

    companion object {
        /**
         * What a weight reads in when nothing at all is known about the lifter.
         *
         * Kilograms because storage is kilograms, so it is the one reading that cannot be wrong.
         * It is the floor rather than the answer: what a lifter actually reads in is their own
         * setting, seeded from [forLocale] and changed by tapping a unit chip.
         */
        val DEFAULT = KILOGRAMS

        /**
         * The unit a lifter in [locale] most likely reads a scale in.
         *
         * A starting point and nothing more: it seeds the lifter's own setting, which one tap on a
         * unit chip changes for good. Getting it right for most people is worth a guess this cheap,
         * and getting it wrong costs that tap.
         *
         * The three countries that never adopted the metric system take pounds and everywhere else
         * takes kilograms. The United Kingdom is deliberately not among them: bodyweight is spoken
         * there in stones, which this app does not offer, while every barbell and stack in a
         * British gym is labelled in kilograms.
         */
        fun forLocale(locale: Locale): WeightUnit =
            if (locale.country.uppercase(Locale.ROOT) in POUND_COUNTRIES) POUNDS else KILOGRAMS

        /** The United States, Liberia and Myanmar. */
        private val POUND_COUNTRIES = setOf("US", "LR", "MM")

        /**
         * How far apart two weights can be and still be the same weight.
         *
         * A reading is rounded to a hundredth of the unit it is read in, so a weight entered in
         * pounds comes back a few thousandths of a kilogram from where it started — 60 kg round
         * trips to 60.0011. Anything comparing a stored weight against another has to allow for
         * that, or a lifter who loaded exactly what they were asked reads as having done something
         * else. Five grams: far coarser than the rounding, far finer than the smallest plate
         * anybody owns.
         */
        const val SAME_WEIGHT_TOLERANCE_KG = 0.005

        private const val HUNDREDTHS = 100.0

        private val BY_STORED = entries.associateBy(WeightUnit::stored)

        /** The unit a stored value names, or null where nothing was stored. */
        fun fromStored(value: String?): WeightUnit? = value?.let {
            requireNotNull(BY_STORED[it]) { "Unknown weight unit `$it`" }
        }
    }
}
