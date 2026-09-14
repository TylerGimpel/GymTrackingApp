package com.tylergimpel.gymtracker.domain

/**
 * One movement that can be logged, such as "Barbell Bench Press".
 *
 * This is the unit progression is tracked against: history follows the exercise, not the routine it
 * appeared in. Muscles and equipment are plain strings drawn from closed vocabularies that the build
 * step enforces — see `data/README.md`.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String?,
    val mechanic: String?,
    val force: String?,
    val category: String,
    val level: String,
    val loggingType: LoggingType,
    /** Trained one side at a time. Logged as a single entry by default, expandable to per-side. */
    val unilateral: Boolean,
) {
    /** The muscle the exercise primarily targets, which is what the bank lists it under. */
    val primaryMuscle: String? get() = primaryMuscles.firstOrNull()
}
