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
    /**
     * The unit this exercise is read in, which the lifter sets once and the app then remembers.
     *
     * A property of the exercise because it is a property of the machine: the lat pulldown at this
     * gym is labelled in pounds whoever walks up to it. It changes what is shown and never what is
     * stored, so a gym with both kinds of machine still has one continuous progress graph.
     */
    val displayUnit: WeightUnit = WeightUnit.DEFAULT,
) {
    /**
     * The muscle to list this exercise under, which is the first of its primary muscles.
     *
     * For display only. Volume counts every primary muscle in full and must not use this — a
     * movement that names two works both of them, and showing one is a shorter label rather than a
     * statement about what it trains.
     */
    val primaryMuscle: String? get() = primaryMuscles.firstOrNull()
}
