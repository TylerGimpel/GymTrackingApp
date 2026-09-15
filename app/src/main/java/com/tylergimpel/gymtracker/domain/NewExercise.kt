package com.tylergimpel.gymtracker.domain

/**
 * An exercise part-way through being added: the machine a gym has that the app has never heard of.
 *
 * Carries the same fields as a shipped row and draws on the same closed vocabularies, because a
 * lifter's own exercise is first-class — it appears in search, in filtering and in a routine exactly
 * as a shipped one does, and anything it left blank would be a hole in all three.
 *
 * The vocabularies themselves are not restated here. They come from the bank, which is the one
 * place they are written down and the one place the build step checks.
 */
data class NewExerciseDraft(
    val name: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    /** Null is a real answer: some shipped rows name no equipment either. */
    val equipment: String? = null,
    val mechanic: String? = null,
    val force: String? = null,
    /**
     * Defaults chosen for the case this exists to serve — the machine in the corner of a gym.
     *
     * A lifter adding one has an opinion about what it trains and very little about whether it is
     * `strength` or `powerlifting`, so these are answers rather than questions.
     */
    val category: String = "strength",
    val level: String = "beginner",
    val loggingType: LoggingType = LoggingType.WEIGHT_REPS,
    val unilateral: Boolean = false,
) {
    /**
     * Whether this describes an exercise yet.
     *
     * A name and at least one primary muscle, because those are the two fields everything
     * downstream reads: without a name it cannot be found, and without a primary muscle it counts
     * toward nothing and is suggested for nothing.
     */
    val isComplete: Boolean get() = name.isNotBlank() && primaryMuscles.isNotEmpty()

    /** Whether [muscle] is already claimed, primarily or otherwise. */
    fun claims(muscle: String): Boolean = muscle in primaryMuscles || muscle in secondaryMuscles

    /**
     * Adds or removes [muscle] as a primary one, taking it out of the secondaries if it was there.
     *
     * A muscle cannot be both: volume counts a primary in full and a secondary at half, and a row
     * naming one twice would be asking to be credited one and a half times for one set of work.
     */
    fun togglePrimary(muscle: String): NewExerciseDraft = copy(
        primaryMuscles = primaryMuscles.toggling(muscle),
        secondaryMuscles = secondaryMuscles - muscle,
    )

    /** Adds or removes [muscle] as a secondary one, taking it out of the primaries if it was there. */
    fun toggleSecondary(muscle: String): NewExerciseDraft = copy(
        primaryMuscles = primaryMuscles - muscle,
        secondaryMuscles = secondaryMuscles.toggling(muscle),
    )
}

private fun List<String>.toggling(value: String): List<String> =
    if (value in this) this - value else this + value

/**
 * Whether [name] is already taken, ignoring case.
 *
 * The build step refuses a shipped row whose name collides with another (`data/README.md`, rule 1),
 * and the reason applies just as well to a lifter's own: the search shorthand resolves to names, so
 * a second "Butterfly" makes "pec deck" mean two different machines.
 */
fun List<Exercise>.hasNameLike(name: String): Boolean =
    any { it.name.equals(name.trim(), ignoreCase = true) }
