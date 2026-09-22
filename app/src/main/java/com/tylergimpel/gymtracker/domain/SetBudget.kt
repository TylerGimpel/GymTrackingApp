package com.tylergimpel.gymtracker.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The most sets of one muscle worth giving a single movement in a session.
 *
 * **Invented, like the two below it.** The research has dose-response data for *sets per muscle*
 * and none whatsoever for how many movements those sets should be spread over, so this is a
 * judgement call in the shape of a constant and must go on looking like one. Three is chosen
 * because nine sets of one exercise is the thing the spec says a day must never be, and three
 * makes that true without splitting a small budget for the sake of it.
 */
const val MOST_SETS_PER_EXERCISE = 3

/**
 * The most movements one muscle gets in one session.
 *
 * **Invented.** Past four the app would be prescribing an exercise for its own sake, and there is
 * no evidence to argue otherwise in either direction.
 */
const val MOST_EXERCISES_PER_MUSCLE = 4

/**
 * How many movements [sets] of one muscle should be spread over in a session.
 *
 * Derived from the budget rather than chosen: a muscle with three sets to spend gets one exercise,
 * and one with nine gets three, because **a day is never one exercise for nine sets**. Both ends
 * are judgement calls ([MOST_SETS_PER_EXERCISE], [MOST_EXERCISES_PER_MUSCLE]) and neither is
 * derived from anything — what the evidence settles is the weekly volume, and this is only how it
 * is arranged once it is known.
 */
fun exercisesForSets(sets: Double): Int =
    ceil(sets / MOST_SETS_PER_EXERCISE).toInt().coerceIn(1, MOST_EXERCISES_PER_MUSCLE)

/**
 * [sets] shared out across [exercises], as whole sets.
 *
 * Evenly, with the remainder going to the movements that come first — eight across three is 3/3/2
 * rather than 2/2/2 with two sets quietly dropped. Nothing ever gets nought: a prescription of no
 * sets is not a prescription, it is an exercise that should not have been offered.
 */
fun setsAcross(exercises: Int, sets: Double): List<Int> {
    if (exercises <= 0) return emptyList()

    val whole = sets.roundToInt().coerceAtLeast(exercises)
    val each = whole / exercises
    val remainder = whole % exercises
    return List(exercises) { index -> each + if (index < remainder) 1 else 0 }
}

/**
 * These exercises in the order they should be trained, given what the lifter is training for.
 *
 * **Compound before isolation, and only for strength.** A lifter training for strength wants the
 * priority lift while they are freshest, and that is the one goal the order is known to matter to.
 * Ordering has no hypertrophy effect, so applying it everywhere would be inventing a rule the
 * evidence does not support — every other goal trains the day in the order it was built.
 *
 * Stable within each group, because nothing justifies re-sorting beyond the one distinction.
 */
fun List<Exercise>.orderedFor(goal: RoutineGoal): List<Exercise> = when (goal) {
    RoutineGoal.STRENGTH -> sortedByDescending { it.isCompound }
    RoutineGoal.HYPERTROPHY, RoutineGoal.ENDURANCE -> this
}

/**
 * How many sets of [muscle] one session should hold, for a routine of this shape.
 *
 * The weekly target divided by how often the muscle is actually trained, then held under the
 * per-session cap — which is what turns "eighteen sets a week" into something a day can carry.
 * The middle of the target band is what is aimed at rather than either end, because the band is a
 * tier rather than a tolerance and its middle is the least arbitrary point in it.
 *
 * **This budgets direct work only, and is honest about being an approximation.** The targets it
 * reads are stated in fractional sets, which count secondary work at a half — so a muscle that
 * gets a lot of incidental work will land above the band once the routine exists. Closing that
 * loop properly means choosing exercises, measuring what they contribute and choosing again; what
 * happens instead is that the report says where every muscle actually landed (#70) and the
 * validation says when that is outside the band (#76). An honest loop with the lifter in it beats
 * a hidden one.
 */
fun setsPerSessionFor(
    muscle: String,
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
): Double {
    val lifting = shape.days.filter { it.kind == RoutineDayKind.LIFTING }
    val daysTrainingIt = lifting.count { muscle in it.muscles }
    if (daysTrainingIt == 0 || shape.days.isEmpty()) return 0.0

    // How often the cycle comes round, which is the only thing that turns a cycle into a week.
    val cyclesPerWeek = cadence.toDouble() / shape.days.size
    val sessionsPerWeek = daysTrainingIt * cyclesPerWeek
    if (sessionsPerWeek <= 0.0) return 0.0

    val target = weeklyVolumeTargetFor(goal)
    val aim = (target.start + target.endInclusive) / 2
    return (aim / sessionsPerWeek).coerceAtMost(PER_SESSION_CAP)
}
