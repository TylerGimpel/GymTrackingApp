package com.tylergimpel.gymtracker.domain

import kotlin.math.ceil

/** What one counted set contributes to a muscle the exercise primarily targets. */
const val DIRECT_CONTRIBUTION = 1.0

/**
 * What one counted set contributes to each muscle the exercise works secondarily.
 *
 * Half, and the halving is a convention rather than a measurement — but it is a matched pair with
 * the volume targets this project has chosen (ADR 0005). Counting secondaries in full alongside
 * targets that assume fractional counting would over-prescribe by roughly a third, invisibly.
 */
const val INDIRECT_CONTRIBUTION = 0.5

/**
 * Sets per muscle, counted directly and indirectly, over whatever was performed.
 *
 * The generator's working data rather than something to show a lifter (ADR 0008): it is the input
 * to a decision the app makes, not a dashboard.
 *
 * Counting sets is counting the sets, because a set is one object however many rows recorded it:
 * a drop set carries its drops and a per-side set holds two efforts, and neither is two sets. That
 * is the whole reason identity is positional rather than per-row (ADR 0003).
 */
fun volumeByMuscle(performed: List<SessionExercise>): Map<String, Double> =
    volumeOf(performed.map { it.exercise to it.sets.count { set -> set.type.countsTowardVolume } })

/**
 * Sets per muscle a routine **plans**, counted the same way as the sets it recorded.
 *
 * The generator's side of the same ledger: what a cycle asks for, rather than what happened. It
 * has to be the same arithmetic, because a target compared against one convention and a routine
 * built against the other is exactly the 30% error ADR 0005 exists to prevent.
 *
 * Per cycle, not per week: [weeklyVolumeByMuscle] translates it, and needs the cadence to do so.
 *
 * **Cardio days carry none of it.** A cardio position carries no resistance work by definition,
 * and crediting what a treadmill row happens to name would put leg volume into a week that had
 * none. An exercise nothing prescribed sets for contributes nothing either: what is not asked for
 * is not planned.
 */
fun plannedVolumeByMuscle(days: List<RoutineDay>): Map<String, Double> = volumeOf(
    days.filter { it.kind == RoutineDayKind.LIFTING }
        .flatMap { day -> day.exercises.map { it.exercise to (it.sets ?: 0) } },
)

/**
 * Sets per muscle, from exercises and how many of their sets counted.
 *
 * One function for the planned and the performed side, so the two can never drift into different
 * conventions — which is the failure this is all guarding against.
 */
private fun volumeOf(counted: List<Pair<Exercise, Int>>): Map<String, Double> {
    val volume = mutableMapOf<String, Double>()

    counted.forEach { (exercise, sets) ->
        if (sets == 0) return@forEach

        val primary = exercise.primaryMuscles.toSet()

        // Every primary muscle takes full credit. A handful of exercises name two, and a movement
        // that genuinely targets two muscles primarily works both of them — splitting the credit
        // would invent a rule, and taking only the first would lose one of them entirely.
        primary.forEach { muscle ->
            volume.merge(muscle, sets * DIRECT_CONTRIBUTION, Double::plus)
        }

        // A muscle already counted in full is not also counted at half. Nine shipped bank rows list
        // the same muscle in both places — Barbell Step Ups names quadriceps twice — and stacking
        // the two would quietly credit 1.5 sets for one set of work.
        exercise.secondaryMuscles
            .filterNot { it in primary }
            .toSet()
            .forEach { muscle ->
                volume.merge(muscle, sets * INDIRECT_CONTRIBUTION, Double::plus)
            }
    }

    return volume
}

/**
 * Where a muscle's weekly set count sits in **Pelland's efficiency tiers**.
 *
 * The evidence half of ADR 0005's matched pair: these boundaries are stated in *fractional* sets,
 * so they only mean anything alongside counting that halves indirect work. Pairing them with
 * Baz-Valle's 12-20, which assumes direct counting, over-prescribes by roughly a third — and
 * pairing direct counting with these under-prescribes by the same.
 *
 * From `docs/research/splits-and-volume.md`: 67 studies, 2058 participants, minimum effective dose
 * around four, and **no clear plateau** — the tiers name diminishing efficiency, never a ceiling
 * on what works.
 */
enum class VolumeTier {
    /** Under the minimum effective dose. Below this, the evidence does not promise growth. */
    BELOW_MINIMUM,

    /** The minimum effective dose: about four fractional sets a week. */
    MINIMUM_EFFECTIVE,

    /** 5–10. The most growth per set spent. */
    HIGHER_EFFICIENCY,

    /** 11–18. Where the research's own sample mean of 12.25 sits. */
    INTERMEDIATE_EFFICIENCY,

    /** 19–29. Still working, and costing about 10.75 sets per detectable increment. */
    LOWER_EFFICIENCY,

    /** 30–42. Still working, and costing about 12.5. */
    LOWEST_EFFICIENCY,

    /** 43 and up, where the data runs out. Not a warning — an absence. */
    UNCLEAR,
    ;

    companion object {
        /** Which tier [weeklySets] fractional sets a week falls in. */
        fun of(weeklySets: Double): VolumeTier = when {
            weeklySets < MINIMUM_EFFECTIVE_DOSE -> BELOW_MINIMUM
            weeklySets < HIGHER_EFFICIENCY_FROM -> MINIMUM_EFFECTIVE
            weeklySets < INTERMEDIATE_FROM -> HIGHER_EFFICIENCY
            weeklySets < LOWER_FROM -> INTERMEDIATE_EFFICIENCY
            weeklySets < LOWEST_FROM -> LOWER_EFFICIENCY
            weeklySets < UNCLEAR_FROM -> LOWEST_EFFICIENCY
            else -> UNCLEAR
        }

        /** Four fractional sets a week, under which the evidence promises nothing. */
        const val MINIMUM_EFFECTIVE_DOSE = 4.0
        private const val HIGHER_EFFICIENCY_FROM = 5.0
        private const val INTERMEDIATE_FROM = 11.0
        private const val LOWER_FROM = 19.0
        private const val LOWEST_FROM = 30.0
        private const val UNCLEAR_FROM = 43.0
    }
}

/**
 * The band the generator aims a muscle's weekly volume at.
 *
 * **The tiers are evidence; which tier a goal aims at is a judgement call**, and it should read
 * like one. Every band here is a whole tier rather than a span across two, because a target that
 * straddled a boundary would be claiming a precision the tiers do not have.
 *
 * Hypertrophy takes the intermediate tier, which is where the research's own sample mean sits.
 * Strength takes the higher-efficiency tier: detectable strength increments stop at about four
 * weekly sets, so the sets beyond that are buying the muscle underneath the lift rather than the
 * lift. Endurance takes the same as hypertrophy — **there is no separate dose-response data for
 * it**, and inventing a different band would be pretending otherwise.
 */
fun weeklyVolumeTargetFor(goal: RoutineGoal): ClosedFloatingPointRange<Double> = when (goal) {
    RoutineGoal.STRENGTH -> 5.0..10.0
    RoutineGoal.HYPERTROPHY, RoutineGoal.ENDURANCE -> 11.0..18.0
}

/**
 * The most fractional sets of one muscle worth doing in a single session.
 *
 * One of the two constraints that survive having no calendar, because it is per-session and says
 * nothing about a week. The point of undetectable outcome superiority is about **11** fractional
 * sets per muscle per session (Remmert et al.), and the IUSCA position stand independently
 * recommends capping near 10 and adding sessions rather than sets past it.
 *
 * Strength has a per-session saturation of its own — about two *direct* sets for the lift
 * being trained — which is **deliberately not this number**: it is stated in the other counting
 * convention and in exercises rather than muscles, and mixing the two is the mistake ADR 0005 is
 * about.
 */
const val PER_SESSION_CAP = 11.0

/**
 * How many lifting sessions a week this routine actually contains.
 *
 * **Not the cadence the lifter gave**, and the difference is the one piece of arithmetic in this
 * project that is wrong without producing an error. Training five times a week on
 * `Upper / Lower / Cardio` is **3.33 lifting sessions**, not five, because a third of those
 * sessions is a run. Feeding the stated figure into volume translation under-prescribes lifting by
 * 40% for anybody with a cardio day (ADR 0005).
 */
val Routine.liftingCadence: Double
    get() = if (days.isEmpty()) 0.0 else cadence * (liftingPositions.toDouble() / days.size)

/**
 * Sets per muscle a week, which is the unit every volume target in the research is stated in.
 *
 * A cycle is not a week and has no fixed duration, so the translation is how often the cycle comes
 * round: [liftingCadence] lifting sessions a week across a cycle holding that many lifting
 * positions. A routine with no lifting positions trains no muscles, which is arithmetic rather
 * than a special case.
 */
fun Routine.weeklyVolumeByMuscle(): Map<String, Double> {
    if (liftingPositions == 0) return emptyMap()

    val cyclesPerWeek = liftingCadence / liftingPositions
    return plannedVolumeByMuscle(days).mapValues { (_, perCycle) -> perCycle * cyclesPerWeek }
}
