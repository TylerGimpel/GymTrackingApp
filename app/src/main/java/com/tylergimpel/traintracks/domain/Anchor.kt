package com.tylergimpel.traintracks.domain

import kotlin.math.min
import kotlin.math.sign

/**
 * The app's current estimate of what a lifter can do for one rep on one exercise.
 *
 * Built only from that exercise's own logged sets and **never inferred from a different one**: a
 * bench press does not decide a dumbbell press, because cross-exercise strength ratios are folklore
 * (ADR 0004). An exercise with nothing logged has no anchor at all rather than a borrowed number,
 * and finding one out is calibration's job.
 *
 * Signed exactly as a **Performance**'s load is, so an assisted machine's estimate is negative and
 * every comparison built on it points the right way by arithmetic.
 */
@JvmInline
value class Anchor(val estimated1rm: Kilograms) {

    /**
     * The load to work at to land at the bottom of [range] — Epley run backwards.
     *
     * The bottom rather than the middle or the top, because the bottom is where a lifter lands the
     * session after the weight goes up, and prescribing for anywhere else would ask them to start
     * a new weight already part-way through the range.
     *
     * **Epley is trusted exactly as far here as it is on the way in.** A rep count beyond
     * [MAX_REPS_ESTIMATED_FROM] is out where the formula stops meaning anything, and reading an
     * estimate back down through one is worse than reading a set up through one: an endurance
     * range bottoming out at twenty would divide an assisted machine's help by a third and
     * prescribe three times as much of it. So the same cutoff caps both directions.
     */
    fun workingLoadFor(range: RepRange): Kilograms = Kilograms(
        estimated1rm.value /
            epleyFactor(estimated1rm.value, min(range.bottom, MAX_REPS_ESTIMATED_FROM)),
    )

    /**
     * Whether [best] is so far past this estimate that the estimate was simply wrong.
     *
     * The escape hatch smoothing needs. A cold start is deliberately light, so the first session
     * a lifter takes seriously can land 50% or more above what the app thought — and creeping
     * toward it a session at a time would take a month to admit a number that was never right.
     * Ordinary progress never looks like this: climbing a rep range from its bottom to its top
     * moves an estimate by 6% on a 6 - 8 range and 11% on an 8 - 12 one, and a set past the top
     * moves it by nothing at all, because the reps are out where the estimate stops reading them.
     *
     * Signed, like everything else here: a quarter *past* the estimate is less help on an assisted
     * machine and more weight on a bar.
     */
    fun isSubstantiallyBeatenBy(best: Kilograms): Boolean =
        best.value > estimated1rm.shiftedBy(SUBSTANTIAL_BEAT_FRACTION).value

    companion object {
        /**
         * How far past the estimate a session has to land before the estimate follows it outright.
         *
         * **An invented, tuneable number.** A quarter is comfortably beyond anything ordinary
         * progress produces and comfortably inside what a wrong cold start produces, which is the
         * only property it needs. It is not derived from anything and must not read as though it
         * were.
         */
        const val SUBSTANTIAL_BEAT_FRACTION = 0.25

        /**
         * How far the estimate moves toward a session that disagrees with it.
         *
         * **An invented, tuneable number.** Nothing in the literature gives a smoothing rate, and
         * this one is chosen for behaviour rather than derived: a third of the way per session
         * absorbs a miscounted rep or a set that went unusually well, and still closes most of a
         * real gap inside three sessions. It must never be presented as a finding.
         *
         * Smoothing is what stops the estimate chasing each new best. The cost is that a genuine
         * jump takes a few sessions to arrive, which is why beating the range substantially
         * re-anchors from what was actually lifted instead of creeping (#50) — the fix for a slow
         * climb is that rule, not a larger number here.
         */
        const val SMOOTHING = 0.3

        /**
         * The most reps a set can carry and still say anything about a one-rep maximum.
         *
         * Every rep formula degrades as the reps climb, and Epley's linearity makes a 20-rep set
         * read as a maximum nobody has. ADR 0004 draws the line here rather than trusting the
         * arithmetic out where it stops meaning anything.
         */
        const val MAX_REPS_ESTIMATED_FROM = 12

        /**
         * The app's estimate after [sessions], oldest first, or null where nothing informs one.
         *
         * A fold rather than a maximum: each session contributes its best set, and the estimate
         * moves part of the way toward it. That is also what makes the estimate drawable as a line
         * across a history (ADR 0008) rather than a number with no working shown.
         */
        fun from(sessions: List<TrainedSession>): Anchor? =
            series(sessions).lastOrNull()?.let { Anchor(it.anchor) }

        /**
         * The same fold with its working shown: each informing session's best, and the estimate
         * just after it (#122). **One fold**, so the line a chart draws is the number the engine
         * prescribes from, at every point along it, rather than a second calculation that could
         * drift from the first.
         */
        fun series(sessions: List<TrainedSession>): List<StrengthPoint> {
            var anchor: Anchor? = null
            return sessions.sortedBy(TrainedSession::date).mapNotNull { session ->
                session.estimated1rm?.let { best ->
                    val moved = anchor.smoothedTo(best).also { anchor = it }
                    StrengthPoint(session.date, best = best, anchor = moved.estimated1rm)
                }
            }
        }

        /**
         * The first session to say anything is taken at its word; every later one moves it.
         *
         * A session that beats the estimate substantially is taken at its word too. Smoothing is
         * there to absorb noise, and a session half again as good as anything before it is not
         * noise — it is the estimate being wrong, which is a different problem and wants the
         * opposite treatment.
         */
        private fun Anchor?.smoothedTo(best: Kilograms): Anchor = when {
            this == null -> Anchor(best)
            isSubstantiallyBeatenBy(best) -> Anchor(best)
            else -> Anchor(
                Kilograms(estimated1rm.value + SMOOTHING * (best.value - estimated1rm.value)),
            )
        }
    }
}

/**
 * Epley's estimated one-rep maximum for [reps] at [load]: `w(1 + r/30)`.
 *
 * Chosen for robustness rather than accuracy, and the choice is deliberate enough that ADR 0004
 * spends a section on it. Measured across 1 - 10 reps the common formulas disagree by under 2% of
 * the load — less than the rounding to the nearest plate, and a fraction of the error a lifter
 * introduces by misjudging how close to failure they were. Epley is monotone, bounded and
 * explicable; **Brzycki has a pole at 37 reps**, where it returns thousands of kilograms and then
 * goes negative. Do not substitute a better-fitting formula.
 *
 * **The factor takes the load's sign**, which is the one thing here that is not textbook Epley. On
 * an assisted machine the load is negative, because help is weight taken off the lifter, and more
 * reps at a given setting means needing *less* help for a single one — so the estimate has to move
 * toward zero as the reps climb rather than away from it. Written out, that is `L + |L|·r/30`:
 * ordinary Epley for a weight on a bar, and Epley read in the direction the exercise actually runs
 * for a machine that is helping.
 *
 * It is not a physical model of an assisted lift — that would need the lifter's bodyweight and a
 * leverage ratio the machine does not publish — and it does not need to be. What a prescription
 * asks of it is that more work reads as more progress, which is the property the sign gives it.
 */
internal fun epley(load: Kilograms, reps: Int): Kilograms =
    Kilograms(load.value * epleyFactor(load.value, reps))

/**
 * Epley's factor for [reps], pointed the way [load] runs.
 *
 * Every caller caps [reps] at [Anchor.MAX_REPS_ESTIMATED_FROM], so this stays between 0.6 and 1.4
 * and an assisted load can never be scaled through zero and out the other side.
 */
private fun epleyFactor(load: Double, reps: Int): Double =
    1.0 + sign(load) * reps / EPLEY_DIVISOR

/** Epley's constant. The whole formula, and the only number in it. */
private const val EPLEY_DIVISOR = 30.0
