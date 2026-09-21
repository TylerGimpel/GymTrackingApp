package com.tylergimpel.gymtracker.domain

import kotlin.math.abs
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
 * - [Fixed] — a dumbbell rack or anything else that moves by a fixed amount, and the default.
 * - [Stack] — a machine, which is only ever the notches it has been *seen* at.
 *
 * [Fixed] and [Stack] round **down in signed terms**, which is what makes one rule cover both
 * directions: a dumbbell rounds to the lighter one, and an assisted machine — whose load is
 * negative, because help is weight taken off the lifter — rounds to *more* help. Both are the
 * conservative answer, and neither needs a reader to remember which way an assisted exercise
 * runs (ADR 0004). [Plates] is unsigned, and nothing is lost by that: a bar cannot help you.
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
    data class Fixed(val size: Kilograms) : Increment {

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
         * Whole grams, because a sum of 1.25s in binary floating point does not land on the
         * number a lifter reads off the plate. Computed once per instance rather than once per
         * inventory — the repository builds a fresh one per read, and a sieve over a handful of
         * denominations is cheaper than a cache that has to be invalidated when a plate is
         * added.
         */
        private val buildable: List<Long> by lazy {
            achievablePerSide(sides).map { barWeight + 2 * it }
        }

        override fun roundDown(load: Kilograms): Kilograms {
            // Below the bar there is nothing left to take off, and an empty bar is a real set-up.
            // The one answer here that is not below what was asked for.
            val wanted = load.milligrams()
            val under = buildable.lastOrNull { it <= wanted + A_CRUMB } ?: return bar
            return under.kilograms()
        }

        override fun oneStepAbove(load: Kilograms): Kilograms {
            val wanted = load.milligrams()
            // Nothing above it means the plates have run out, and the honest answer is the
            // heaviest thing in the room rather than a weight that is not there.
            return (buildable.firstOrNull { it > wanted + A_CRUMB }
                ?: buildable.lastOrNull()
                ?: barWeight)
                .kilograms()
        }

        /**
         * What goes on one end of the bar to make [total], or null where these plates cannot.
         *
         * The plate line, and the reason the rounding above is worth having: a lifter reads this
         * and loads the bar without doing any arithmetic. Heaviest plate first, which is the
         * order somebody loads in — not necessarily the fewest plates, because preferring the
         * heaviest that fits is what a person does and second-guessing it to save one 1.25 would
         * read as arithmetic rather than instructions.
         *
         * **Null and empty are different answers.** Empty is a bar with nothing on it, which is
         * a real set-up; null is a weight these plates cannot build, which a caller must not
         * render as an empty bar under a number that is not one. The membership check is what
         * makes the difference cheap — and what keeps the search below off a target it would
         * explore every combination of before admitting defeat.
         */
        fun perSide(total: Kilograms): List<Kilograms>? {
            val wanted = total.milligrams()
            // Matched against what the plates actually make rather than against the number that
            // came in: a total converted straight out of pounds and the same total summed from
            // plates converted one at a time differ by a crumb, and comparing them exactly
            // would refuse to explain a weight the plates had just built.
            val built = buildable.firstOrNull { abs(it - wanted) <= A_CRUMB } ?: return null

            val each = (built - barWeight) / 2
            if (each == 0L) return emptyList()

            val chosen = mutableListOf<Long>()
            return if (load(each, 0, chosen)) chosen.map { it.kilograms() } else null
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

        private val barWeight: Long get() = bar.milligrams()

        /** One entry per plate that can go on one end, heaviest first. */
        private val sides: List<Long> by lazy {
            plates.flatMap { pair -> List(pair.pairs) { pair.weight.milligrams() } }
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
        // Read from the log rather than from the machine, which is the only source there is. It
        // is as honest as the logging: a lifter who accepts a number without checking the pin
        // teaches the app a notch that was never there.

        private val notches: List<Double> = positions.map(Kilograms::value).distinct().sorted()

        override fun roundDown(load: Kilograms): Kilograms {
            val under = notches.lastOrNull { it <= load.value + FLOAT_DUST }
            // Not below what was asked for, like an empty bar is not. Nothing lighter has ever
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
        operator fun invoke(size: Kilograms): Increment = Fixed(size)

        /**
         * What a weight is rounded to until the equipment says otherwise.
         *
         * The smallest pair of plates in an ordinary gym, which is the one answer that is never
         * unbuildable on a barbell and is close enough on everything else to be corrected rather
         * than resented.
         */
        val DEFAULT: Increment = Fixed(Kilograms(2.5))

        /**
         * A hair of tolerance, added wherever a comparison would otherwise turn on binary dust.
         *
         * A load that arrives a billionth under an exact multiple — which is what dividing by 1.2
         * and reading back in pounds does — must not round down a whole plate pair on the strength
         * of binary floating point. Far below any weight anybody can load, and far above the dust.
         *
         * Counted in **increments** where the load has already been divided by one ([Fixed]), and
         * in **kilograms** where notches are compared against the load as it stands ([Stack]). At
         * a billionth both are far beneath anything loadable, so what differs is where the
         * arithmetic happens rather than how much slack it buys.
         */
        private const val FLOAT_DUST = 1e-9

        /**
         * Whole milligrams, which is finer than any plate ever cast and free of binary dust.
         *
         * Milligrams rather than grams because a rack cast in pounds does not land on grams: 45
         * lb is 20.41165665 kg, a third of a milligram off a whole one. Rounding there is under
         * a gram a plate, but it accumulates one way across a bar and eight plates into 345.01
         * lb on screen — precisely the fractional nonsense this was meant to end.
         */
        private fun Kilograms.milligrams(): Long = (value * MILLIGRAMS_PER_KILOGRAM).roundToLong()

        private fun Long.kilograms(): Kilograms = Kilograms(this / MILLIGRAMS_PER_KILOGRAM)

        private const val MILLIGRAMS_PER_KILOGRAM = 1_000_000.0

        /**
         * A gram of slack, which is what rounding a rack cast in pounds leaves behind.
         *
         * Each imperial plate lands a fraction of a milligram off a whole one and the fractions
         * accumulate one way, so a total summed plate by plate misses the same total converted
         * in one go. A gram is thousands of times larger than that drift and thousands of times
         * smaller than anything anybody can load, so nothing real hides inside it.
         */
        private const val A_CRUMB = 1_000L

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
