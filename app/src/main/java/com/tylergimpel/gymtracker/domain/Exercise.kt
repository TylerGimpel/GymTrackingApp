package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * Where a row of the exercise bank came from.
 *
 * The distinction the update rules turn on is not upstream against authored — both of those ship
 * inside the APK and are the bank's to correct — but shipped against [USER], which is the lifter's
 * and is never an update's to touch.
 */
enum class ExerciseSource(val stored: String) {
    /** From the public dataset the bank is built on. */
    UPSTREAM("upstream"),

    /** Hand-written for this project, filling a gap the dataset has. Still shipped. */
    AUTHORED("authored"),

    /** Added by the lifter, for a machine their gym has and the app has never heard of. */
    USER("user"),
    ;

    companion object {
        private val BY_STORED = entries.associateBy(ExerciseSource::stored)

        fun fromStored(value: String): ExerciseSource = requireNotNull(BY_STORED[value]) {
            "Unknown exercise source `$value`"
        }
    }
}

/**
 * One movement that can be logged, such as "Barbell Bench Press".
 *
 * This is the unit progression is tracked against: history follows the exercise, not the routine it
 * appeared in. Muscles and equipment are plain strings drawn from closed vocabularies that the build
 * step enforces — see `data/README.md`.
 *
 * Carries both what the bank shipped and what the lifter has decided, because almost everything
 * wants them together. They are stored apart (ADR 0009), which is what lets a bank update correct
 * the one without reaching the other.
 */
data class Exercise(
    val id: String,
    /**
     * What the bank calls this movement.
     *
     * Kept even after a rename, because it is what aliases resolve against: `aliases.json` targets
     * names, and a lifter renaming Butterfly would otherwise silently break "pec deck" — the exact
     * failure the build-time alias check exists to prevent.
     */
    val bankName: String,
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
    /**
     * What the bank derived this exercise advances by, from what it measures.
     *
     * The guess, not the answer: [progressionModel] is what anything reading this should ask, since
     * a lifter who has said otherwise has said it.
     */
    val bankProgressionModel: ProgressionModel = ProgressionModel.DOUBLE_PROGRESSION,
    /** What the lifter chose instead, where they chose anything. */
    val chosenProgressionModel: ProgressionModel? = null,
    /**
     * How long the lifter rests on this exercise, where they have said.
     *
     * Overrules what the app derives from the set. Null is the ordinary case and means the derived
     * answer stands — not that they chose nothing in particular.
     */
    val chosenRest: Duration? = null,
    /**
     * Whether the lifter never wants this suggested again.
     *
     * A single hard ban rather than a ranking penalty: an exercise somebody has ruled out is
     * ruled out, and a suggestion engine that offered it anyway because the alternatives scored
     * worse would have broken the only promise this setting makes. Global, reversible, and never
     * a statement about history — the sets already logged against it stay exactly where they are.
     */
    val isDisliked: Boolean = false,
    /** What the lifter calls this, where they have renamed it. */
    val userName: String? = null,
    /** A standing note — "seat notch 3, use the long bar" — shown wherever the exercise is used. */
    val note: String? = null,
    val source: ExerciseSource = ExerciseSource.UPSTREAM,
    /**
     * Whether a bank update dropped this exercise while a lifter had already trained it.
     *
     * Stops it being suggested and leaves every set logged against it exactly where it was. The
     * alternative is deleting a row that history points at, which is not an option a training log
     * gets to take.
     */
    val isTombstoned: Boolean = false,
) {
    /**
     * What to call this exercise: the lifter's name for it where they have given one.
     *
     * Derived rather than stored, so that nothing can show the bank's name to someone who has
     * renamed it by reading the wrong field.
     */
    val name: String get() = userName ?: bankName

    /**
     * How this exercise's recommendation advances: the lifter's choice where they made one, and
     * what the bank derived otherwise.
     *
     * Derived rather than stored, so that nothing can prescribe against a model the lifter has
     * overridden by reading the wrong field — the same move [name] makes for a rename.
     */
    val progressionModel: ProgressionModel get() = chosenProgressionModel ?: bankProgressionModel

    /** Added by the lifter rather than shipped. Never an update's to modify or remove. */
    val isUserAuthored: Boolean get() = source == ExerciseSource.USER

    /**
     * The muscle to list this exercise under, which is the first of its primary muscles.
     *
     * For display only. Volume counts every primary muscle in full and must not use this — a
     * movement that names two works both of them, and showing one is a shorter label rather than a
     * statement about what it trains.
     */
    val primaryMuscle: String? get() = primaryMuscles.firstOrNull()

    /**
     * This exercise as the bank now has it, carrying forward everything [chosen] decided about it.
     *
     * Those fields are stored apart and an update never writes them, so this is not what keeps them
     * safe — it is what makes "has this row actually changed" answerable, by comparing like with
     * like rather than reporting a correction every time a lifter had renamed something.
     */
    fun keepingChoicesOf(chosen: Exercise): Exercise = copy(
        displayUnit = chosen.displayUnit,
        userName = chosen.userName,
        note = chosen.note,
        chosenProgressionModel = chosen.chosenProgressionModel,
        chosenRest = chosen.chosenRest,
        isDisliked = chosen.isDisliked,
    )
}
