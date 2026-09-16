package gymtracker.buildlogic

/**
 * The closed vocabularies the exercise bank is allowed to use, transcribed from `data/README.md`.
 *
 * Any value outside these sets is a build failure. The point is that a typo in a hand-authored row
 * cannot reach the APK and quietly break muscle filtering or the gym equipment checklist, where it
 * would look like a missing exercise rather than a bad value.
 */
object Vocabulary {

    /** The 17 upstream muscles, used for both primary and secondary. */
    val MUSCLES = setOf(
        "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms",
        "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps",
        "shoulders", "traps", "triceps",
    )

    /** Upstream's equipment values. */
    val UPSTREAM_EQUIPMENT = setOf(
        "barbell", "dumbbell", "cable", "machine", "body only", "kettlebells", "bands",
        "medicine ball", "exercise ball", "foam roll", "e-z curl bar", "other",
    )

    /**
     * The six cardio equipment values added by this project. Upstream tags every cardio machine as
     * the generic `machine`, which cannot drive the gym equipment checklist.
     */
    val CARDIO_EQUIPMENT = setOf(
        "treadmill", "stationary bike", "rower", "elliptical", "stair climber", "outdoor",
    )

    val EQUIPMENT = UPSTREAM_EQUIPMENT + CARDIO_EQUIPMENT

    val MECHANIC = setOf("compound", "isolation")

    val FORCE = setOf("push", "pull", "static")

    val CATEGORY = setOf(
        "strength", "powerlifting", "olympic weightlifting", "strongman",
        "stretching", "plyometrics", "cardio",
    )

    val LEVEL = setOf("beginner", "intermediate", "expert")

    val LOGGING_TYPE = setOf("weight_reps", "bodyweight_reps", "assisted", "time", "distance_time")

    val PROGRESSION_MODEL = setOf("double_progression", "none")
}
