package com.tylergimpel.gymtracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * How an estimate ages while an exercise goes untrained.
 *
 * **The estimate decays because it has stopped being informative, not because the lifter has
 * become fragile.** The framing matters and the research is explicit about it: resistance-training
 * injury rates are low, and pre-break strength comes back within weeks of a layoff. Somebody
 * returning after two months is not in danger — the app simply no longer knows what they can do.
 *
 * So decay's real job is to **trigger re-calibration**, which means [forgetAfterWeeks] is the
 * number that matters and [perWeek] barely does. Past the threshold the honest move is to stop
 * prescribing from an estimate nobody believes and go and find out again.
 *
 * **Every number here is invented.** No usable decay curve exists in the literature. That is why
 * this is a value that gets passed in rather than a constant somewhere in the arithmetic: a test
 * states the policy it is testing, and changing the policy is changing one object rather than
 * hunting for a magic number.
 */
data class LayoffDecay(
    /**
     * How long ordinary life is free.
     *
     * A holiday, a cold, a busy fortnight at work: none of it should cost a lifter their
     * recommendation, and an app that shaved something off for a missed week would be wrong far
     * more often than it was right.
     */
    val graceWeeks: Long,
    /**
     * How much goes each week after that, as a fraction of the estimate it started with.
     *
     * Off the original rather than compounding, because nothing about this is precise enough to
     * deserve a curve: it is a straight line chosen to be modest, and a lifter reading the result
     * should be able to work out where it came from. Invented, like the rest.
     */
    val perWeek: Double,
    /**
     * When the estimate stops being worth prescribing from at all.
     *
     * The one number in here that does real work. Past it the exercise goes back to
     * **Calibrating**, which is the app saying it does not know rather than guessing quietly.
     */
    val forgetAfterWeeks: Long,
) {

    /**
     * The estimate [anchor] has aged into after [away], or null once it is not worth having.
     *
     * Signed, like everything else: a barbell estimate comes down toward nothing and an assisted
     * machine's comes down toward more help, which is the same direction written once.
     */
    /** The estimate after [weeks] away, or null once it is not worth having. */
    fun applyTo(anchor: Anchor, weeks: Long): Anchor? = when {
        weeks >= forgetAfterWeeks -> null
        weeks <= graceWeeks -> anchor
        else -> {
            val gone = perWeek * (weeks - graceWeeks)
            val value = anchor.estimated1rm.value
            Anchor(Kilograms(value - abs(value) * gone))
        }
    }

    companion object {
        /**
         * What an estimate does while nobody is training it, until somebody tunes it.
         *
         * Three weeks free, a twentieth a week after that, and past two months the app admits it
         * is guessing. Invented, all three, and chosen so that the one that matters — the point
         * at which it gives up and re-calibrates — lands where the research puts the return of
         * pre-break strength rather than anywhere it was measured.
         */
        val DEFAULT = LayoffDecay(graceWeeks = 3, perWeek = 0.05, forgetAfterWeeks = 8)
    }
}

/** How many whole weeks lie between two days. */
fun weeksBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.WEEKS.between(from, to)
