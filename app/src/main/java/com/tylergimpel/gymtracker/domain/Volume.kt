package com.tylergimpel.gymtracker.domain

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
 * Counts **distinct set indices**, not rows. One set may be recorded by more than one row — a
 * per-side set writes one per side — and counting rows instead would report it as two, blowing
 * through the per-session cap while the app believed the lifter was undertrained (ADR 0003).
 */
fun volumeByMuscle(performed: List<SessionExercise>): Map<String, Double> {
    val volume = mutableMapOf<String, Double>()

    performed.forEach { sessionExercise ->
        val counted = sessionExercise.sets
            .filter { it.type.countsTowardVolume }
            .distinctBy(LoggedSet::setIndex)
            .size
        if (counted == 0) return@forEach

        val primary = sessionExercise.exercise.primaryMuscles.toSet()

        // Every primary muscle takes full credit. A handful of exercises name two, and a movement
        // that genuinely targets two muscles primarily works both of them — splitting the credit
        // would invent a rule, and taking only the first would lose one of them entirely.
        primary.forEach { muscle ->
            volume.merge(muscle, counted * DIRECT_CONTRIBUTION, Double::plus)
        }

        // A muscle already counted in full is not also counted at half. Nine shipped bank rows list
        // the same muscle in both places — Barbell Step Ups names quadriceps twice — and stacking
        // the two would quietly credit 1.5 sets for one set of work.
        sessionExercise.exercise.secondaryMuscles
            .filterNot { it in primary }
            .toSet()
            .forEach { muscle ->
                volume.merge(muscle, counted * INDIRECT_CONTRIBUTION, Double::plus)
            }
    }

    return volume
}
