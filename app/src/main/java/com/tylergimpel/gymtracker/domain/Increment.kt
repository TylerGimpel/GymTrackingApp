package com.tylergimpel.gymtracker.domain

import kotlin.math.floor

/**
 * The smallest weight step actually achievable on an exercise, set by the equipment.
 *
 * Distinct from [WeightUnit.step], which is what a stepper moves by and is a round number in
 * whatever unit the lifter is reading — 2.5 kg or 5 lb. This is a fact about the rack, the stack or
 * the plates in the corner of somebody's garage, and it is what a recommendation has to land on: a
 * weight nobody can build is worse than no weight at all, because it asks for arithmetic between
 * sets, which is what this app is for.
 *
 * One value for now. The three tiers the spec settles — a barbell built from the plate inventory, a
 * dumbbell rack's own step, and the stack positions a machine has actually been seen at — each
 * arrive with their own ticket, and each will answer this question differently.
 */
@JvmInline
value class Increment(val size: Kilograms) {

    /**
     * [load] rounded down to something this increment can build.
     *
     * **Down in signed terms**, which is what makes one rule cover both directions: a barbell
     * rounds to the lighter plate, and an assisted machine — whose load is negative, because help
     * is weight taken off the lifter — rounds to *more* help. Both are the conservative answer, and
     * neither needs a reader to remember which way an assisted exercise runs (ADR 0004).
     */
    fun roundDown(load: Kilograms): Kilograms =
        Kilograms(floor(load.value / size.value + FLOAT_DUST) * size.value)

    companion object {
        /**
         * What a weight is rounded to until the equipment says otherwise.
         *
         * The smallest pair of plates in an ordinary gym, which is the one answer that is never
         * unbuildable on a barbell and is close enough on everything else to be corrected rather
         * than resented.
         */
        val DEFAULT = Increment(Kilograms(2.5))

        /**
         * A hair of tolerance, counted in increments rather than kilograms.
         *
         * A load that arrives a billionth under an exact multiple — which is what dividing by 1.2
         * and reading back in pounds does — must not round down a whole plate pair on the strength
         * of binary floating point. Far below any weight anybody can load, and far above the dust.
         */
        private const val FLOAT_DUST = 1e-9
    }
}
