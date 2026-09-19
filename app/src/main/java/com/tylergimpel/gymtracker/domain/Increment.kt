package com.tylergimpel.gymtracker.domain

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * One pair of plates the lifter owns, and how many of that pair there are.
 *
 * Pairs rather than plates, because a bar loaded on one end is not a lift. Counting them this way
 * means nothing downstream has to remember to halve anything, and a gym with an odd plate simply
 * cannot use it — which is true.
 */
data class PlatePair(val weight: Kilograms, val pairs: Int)

/**
 * The smallest weight step actually achievable on an exercise, set by the equipment.
 *
 * Distinct from [WeightUnit.step], which is what a stepper moves by and is a round number in
 * whatever unit the lifter is reading — 2.5 kg or 5 lb. This is a fact about the rack, the stack or
 * the plates in the corner of somebody's garage, and it is what a recommendation has to land on: a
 * weight nobody can build is worse than no weight at all, because it asks for arithmetic between
 * sets, which is what this app is for.
 *
 * Three tiers, because three kinds of equipment round differently:
 *
 * - [Plates] — a barbell, which is the bar plus whatever pairs are in the room.
 * - [Step] — a dumbbell rack or anything else that moves by a fixed amount, and the default.
 * - [Stack] — a machine, which is only ever the notches it has been *seen* at.
 *
 * Every one of them rounds **down in signed terms**, which is what makes one rule cover both
 * directions: a barbell rounds to the lighter plate, and an assisted machine — whose load is
 * negative, because help is weight taken off the lifter — rounds to *more* help. Both are the
 * conservative answer, and neither needs a reader to remember which way an assisted exercise
 * runs (ADR 0004).
 */
sealed interface Increment {

    /** [load] rounded down to something this equipment can actually be set to. */
    fun roundDown(load: Kilograms): Kilograms

    /**
     * One step harder than [load] — what an advance moves by.
     *
     * Harder rather than heavier, and the same arithmetic for both: a step up the bar, and a step
     * *off* an assisted machine's help, which is a load counting upward from below zero.
     */
    fun oneStepAbove(load: Kilograms): Kilograms

    /**
     * Anything that moves by a fixed amount: a dumbbell rack, and whatever nobody has described.
     *
     * The tier that assumes rather than knows. It is right about dumbbells, which really do come
     * in even steps, and approximately right about everything the lifter has not told the app
     * about — close enough to be corrected rather than resented.
     */
    data class Step(val size: Kilograms) : Increment {

        override fun roundDown(load: Kilograms): Kilograms =
            Kilograms(floor(load.value / size.value + FLOAT_DUST) * size.value)

        override fun oneStepAbove(load: Kilograms): Kilograms =
            Kilograms(load.value + size.value)
    }

    /**
     * A barbell: the bar, plus pairs from the one inventory the lifter keeps.
     *
     * **What can be built rather than what divides evenly.** A gym with nothing lighter than a
     * twenty jumps 60 to 100 and there is no 80 in it; an increment of 2.5 would cheerfully offer
     * one. The same inventory is read by the plate line under the prescription, so the number and
     * the instructions for building it can never disagree — they are one answer asked twice.
     */
    data class Plates(val bar: Kilograms, val plates: List<PlatePair>) : Increment {

        /**
         * Every total this bar and these plates can be loaded to, lightest first.
         *
         * Computed once per inventory and in whole grams, because a sum of 1.25s in binary
         * floating point does not land on the number a lifter reads off the plate.
         */
        private val buildable: List<Long> by lazy {
            val perSide = achievablePerSide(sides)
            perSide.map { barGrams + 2 * it }
        }

        override fun roundDown(load: Kilograms): Kilograms {
            // Below the bar there is nothing left to take off, and an empty bar is a real set-up.
            val wanted = load.grams()
            val under = buildable.lastOrNull { it <= wanted } ?: return bar
            return under.kilograms()
        }

        override fun oneStepAbove(load: Kilograms): Kilograms {
            val wanted = load.grams()
            // Nothing above it means the plates have run out, and the honest answer is the
            // heaviest thing in the room rather than a weight that is not there.
            return (buildable.firstOrNull { it > wanted } ?: buildable.lastOrNull() ?: barGrams)
                .kilograms()
        }

        /**
         * What goes on one end of the bar to make [total], heaviest plate first.
         *
         * The plate line, and the reason the rounding above is worth having: a lifter reads this
         * and loads the bar without doing any arithmetic. Heaviest first and as few as will do
         * it, because every plate is a trip to the rack — and exact, because the search backs up
         * rather than taking the biggest plate that fits and getting stuck.
         */
        fun perSide(total: Kilograms): List<Kilograms> {
            val each = (total.grams() - barGrams) / 2
            if (each <= 0) return emptyList()

            val chosen = mutableListOf<Long>()
            return if (load(each, 0, chosen)) chosen.map { it.kilograms() } else emptyList()
        }

        /** Whether [remaining] can be made from [sides] at [index] or later, filling [chosen]. */
        private fun load(remaining: Long, index: Int, chosen: MutableList<Long>): Boolean {
            if (remaining == 0L) return true
            if (index >= sides.size) return false

            val plate = sides[index]
            if (plate <= remaining) {
                chosen.add(plate)
                if (load(remaining - plate, index + 1, chosen)) return true
                chosen.removeAt(chosen.lastIndex)
            }
            return load(remaining, index + 1, chosen)
        }

        private val barGrams: Long get() = bar.grams()

        /** One entry per plate that can go on one end, heaviest first. */
        private val sides: List<Long> by lazy {
            plates.flatMap { pair -> List(pair.pairs) { pair.weight.grams() } }
                .filter { it > 0 }
                .sortedDescending()
        }
    }

    /**
     * A machine, which is only ever a notch it has been seen at.
     *
     * A **Stack number** is a gym-local scale with no physical meaning: pulley ratios differ
     * between manufacturers and are not labelled, so nothing can be inferred about the gaps in a
     * stack from any other machine. The app therefore learns each one from what has been logged
     * against it rather than assuming a step, and never offers a pin position nobody has used.
     */
    data class Stack(val positions: List<Kilograms>) : Increment {

        private val notches: List<Double> = positions.map(Kilograms::value).distinct().sorted()

        override fun roundDown(load: Kilograms): Kilograms {
            val under = notches.lastOrNull { it <= load.value + FLOAT_DUST }
            // The one place the answer is not below what was asked for. Nothing lighter has ever
            // been logged here, and inventing a notch is the one thing this tier exists to avoid:
            // the lifter can select a lighter pin themselves, which is how the app learns of it.
            return Kilograms(under ?: notches.firstOrNull() ?: load.value)
        }

        override fun oneStepAbove(load: Kilograms): Kilograms {
            val above = notches.firstOrNull { it > load.value + FLOAT_DUST }
            // At the top of what has been seen, the app has no idea what the next pin is worth.
            // Holding is what lets the lifter find out and teach it.
            return Kilograms(above ?: notches.lastOrNull() ?: load.value)
        }
    }

    companion object {
        /**
         * A plain step, which is what most of this engine wants when it is not being told better.
         *
         * Kept as an operator so that the arithmetic tiers read as they always did: the three
         * tiers are a fact about equipment, and every caller that has no equipment in hand is
         * asking the same question it was before.
         */
        operator fun invoke(size: Kilograms): Increment = Step(size)

        /**
         * What a weight is rounded to until the equipment says otherwise.
         *
         * The smallest pair of plates in an ordinary gym, which is the one answer that is never
         * unbuildable on a barbell and is close enough on everything else to be corrected rather
         * than resented.
         */
        val DEFAULT: Increment = Step(Kilograms(2.5))

        /**
         * A hair of tolerance, counted in increments rather than kilograms.
         *
         * A load that arrives a billionth under an exact multiple — which is what dividing by 1.2
         * and reading back in pounds does — must not round down a whole plate pair on the strength
         * of binary floating point. Far below any weight anybody can load, and far above the dust.
         */
        private const val FLOAT_DUST = 1e-9

        /** Whole grams, which is finer than any plate ever cast and free of binary dust. */
        private fun Kilograms.grams(): Long = (value * 1000.0).roundToLong()

        private fun Long.kilograms(): Kilograms = Kilograms(this / 1000.0)

        /**
         * Every sum one end of the bar can be loaded to, including nothing, lightest first.
         *
         * A sieve rather than a search: what matters is which totals exist, and a plate either
         * lands on a sum already reachable or opens a new one. Whole grams keep it exact and
         * bounded — an inventory is a handful of denominations, not a knapsack problem.
         */
        private fun achievablePerSide(sides: List<Long>): List<Long> {
            var sums = setOf(0L)
            for (plate in sides) {
                sums = sums + sums.map { it + plate }
            }
            return sums.sorted()
        }
    }
}
