package com.tylergimpel.gymtracker.domain

/** What one counted set contributes to the muscle an exercise primarily targets. */
const val DIRECT_SET = 1.0

/**
 * What one counted set contributes to each muscle an exercise works secondarily.
 *
 * Half, and the halving is a convention rather than a measurement — but it is a matched pair with
 * the volume targets this project uses (ADR 0005). Counting secondaries in full alongside targets
 * that assume fractional counting would over-prescribe by roughly a third, invisibly.
 */
const val INDIRECT_SET = 0.5

/**
 * Sets per muscle, counted directly and indirectly, over whatever was performed.
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

        sessionExercise.exercise.primaryMuscles.forEach { muscle ->
            volume.merge(muscle, counted * DIRECT_SET, Double::plus)
        }
        sessionExercise.exercise.secondaryMuscles.forEach { muscle ->
            volume.merge(muscle, counted * INDIRECT_SET, Double::plus)
        }
    }

    return volume
}
