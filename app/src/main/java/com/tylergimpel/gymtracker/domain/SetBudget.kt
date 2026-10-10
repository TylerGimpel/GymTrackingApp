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
 * How far above the bottom of the target band the set budget aims, in fractional sets a week.
 *
 * **Invented**, like the constants above. One set is enough headroom that whole-set rounding does
 * not carry a muscle under the band in any routine measured, and no more, because every set above
 * the floor is time in a session the ceiling has to find room for (#84). Where rounding does carry
 * one under — a muscle trained on many days loses up to half a set each time — the band finding
 * says so.
 */
const val AIM_ABOVE_FLOOR = 1.0

/**
 * How many sets of [muscle] one session should hold, for a routine of this shape.
 *
 * The weekly target divided by how often the muscle is actually trained, then held under the
 * per-session cap — which is what turns "eighteen sets a week" into something a day can carry.
 *
 * **Aimed [AIM_ABOVE_FLOOR] above the bottom of the band, not at its middle.** The band is a tier
 * and every point in it is a legitimate place to be, so where in it to aim is a question of what
 * fits. Aimed at the middle, twelve muscles' worth made an ordinary four-day upper/lower week run
 * past the session ceiling on every upper day (#84). Aimed at the floor itself, whole sets round
 * some muscles just under it and the app flags its own routine as under-trained.
 *
 * This is the whole target, and all of it fractional. What the rest of the cycle already gives a
 * muscle as secondary work is taken off it before any direct work is budgeted — see the
 * generator's settling — so a muscle second in every press does not land above the band.
 */
fun setsPerSessionFor(
    muscle: String,
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
    /**
     * What a session of this muscle already gets from movements chosen for other muscles, in
     * fractional sets. Taken off before the cap is applied, so a muscle the cap holds short is not
     * then also docked its whole credit.
     */
    credited: Double = 0.0,
): Double {
    val lifting = shape.days.filter { it.kind == RoutineDayKind.LIFTING }
    val daysTrainingIt = lifting.count { muscle in it.muscles }
    if (daysTrainingIt == 0 || shape.days.isEmpty()) return 0.0

    // How often the cycle comes round, which is the only thing that turns a cycle into a week.
    val cyclesPerWeek = cadence.toDouble() / shape.days.size
    val sessionsPerWeek = daysTrainingIt * cyclesPerWeek
    if (sessionsPerWeek <= 0.0) return 0.0

    val target = weeklyVolumeTargetFor(goal)
    val aim = target.start + AIM_ABOVE_FLOOR
    return (aim / sessionsPerWeek - credited).coerceIn(0.0, PER_SESSION_CAP)
}
