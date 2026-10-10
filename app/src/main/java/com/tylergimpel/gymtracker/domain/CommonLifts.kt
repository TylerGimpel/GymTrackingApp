package com.tylergimpel.gymtracker.domain

/**
 * One place in a **Day recipe**, named by movement pattern rather than by exercise (ADR 0013).
 *
 * [ranked] is the **Common lifts** that fill it, best first: ranked by how many of the ten surveyed
 * programs use them (`docs/research/common-routines.md` §3 on `research/common-routines`), by bank
 * id because the id is what never changes. One row per movement: where the bank has the same lift
 * twice, only the row the research named is ranked, and the other stays for history and search.
 *
 * [secondTier] is the lifts intermediate programs add, offered only to an advanced lifter and only
 * once they ask for something else — never in anybody's first routine.
 */
data class Slot(
    /** What the slot is for, as a lifter would say it. */
    val pattern: String,
    /** The muscle the slot trains directly, which is the muscle it budgets and covers. */
    val muscle: String,
    val ranked: List<String>,
    val secondTier: List<String> = emptyList(),
)

/** A [Slot] in a recipe, and whether the day always has it or only as its set budget needs. */
data class RecipeSlot(val slot: Slot, val always: Boolean)

/** A **Day recipe**: its slots in the order they are trained, which is the same for every goal. */
typealias DayRecipe = List<RecipeSlot>

private fun always(slot: Slot) = RecipeSlot(slot, always = true)
private fun ifNeeded(slot: Slot) = RecipeSlot(slot, always = false)

/*
 * The slots. Each list is the research's ranking for that movement, with the bodyweight version
 * last where there is one, so a gym with nothing but a floor and a bar to hang from still gets the
 * lift programs use rather than whatever sorts first.
 */

val SQUAT = Slot(
    "Squat", "quadriceps",
    ranked = listOf("Barbell_Squat", "Leg_Press", "Goblet_Squat", "Split_Squat_with_Dumbbells", "Dumbbell_Lunges", "Bodyweight_Squat"),
    secondTier = listOf("Front_Squat_Clean_Grip", "Hack_Squat", "Bulgarian_Split_Squat"),
)
val SECOND_QUADS = Slot(
    "Leg press or lunge", "quadriceps",
    ranked = listOf("Leg_Press", "Leg_Extensions", "Dumbbell_Lunges", "Barbell_Walking_Lunge", "Split_Squat_with_Dumbbells", "Bodyweight_Walking_Lunge", "Goblet_Squat"),
    secondTier = listOf("Bulgarian_Split_Squat", "Hack_Squat"),
)
val HINGE = Slot(
    "Hip hinge", "hamstrings",
    ranked = listOf("Romanian_Deadlift", "Stiff-Legged_Dumbbell_Deadlift"),
)
val LEG_CURL = Slot(
    "Leg curl", "hamstrings",
    ranked = listOf("Lying_Leg_Curls", "Seated_Leg_Curl", "Romanian_Deadlift", "Stiff-Legged_Dumbbell_Deadlift"),
)
val DEADLIFT = Slot(
    "Deadlift", "lower back",
    ranked = listOf("Barbell_Deadlift", "Hyperextensions_Back_Extensions"),
)
val BACK_EXTENSION = Slot(
    "Back extension", "lower back",
    ranked = listOf("Hyperextensions_Back_Extensions", "Barbell_Deadlift"),
)
val HIP_THRUST = Slot(
    "Hip thrust", "glutes",
    ranked = listOf("Barbell_Hip_Thrust", "Hip_Thrust_Machine", "Barbell_Glute_Bridge"),
)
val CALF_RAISE = Slot(
    "Calf raise", "calves",
    ranked = listOf("Standing_Calf_Raises", "Seated_Calf_Raise", "Calf_Press_On_The_Leg_Press_Machine"),
)
val BENCH = Slot(
    "Flat press", "chest",
    ranked = listOf("Barbell_Bench_Press_-_Medium_Grip", "Dumbbell_Bench_Press", "Machine_Bench_Press", "Pushups"),
)
val INCLINE = Slot(
    "Incline press or dip", "chest",
    ranked = listOf("Incline_Dumbbell_Press", "Machine_Incline_Chest_Press", "Dips_-_Chest_Version", "Dumbbell_Bench_Press", "Pushups"),
    secondTier = listOf("Barbell_Incline_Bench_Press_-_Medium_Grip"),
)
val CHEST_FLY = Slot(
    "Chest fly", "chest",
    ranked = listOf("Butterfly", "Cable_Crossover", "Dumbbell_Flyes"),
)
val OVERHEAD_PRESS = Slot(
    "Overhead press", "shoulders",
    ranked = listOf("Standing_Military_Press", "Dumbbell_Shoulder_Press", "Machine_Shoulder_Military_Press"),
)
val LATERAL_RAISE = Slot(
    "Lateral raise", "shoulders",
    ranked = listOf("Side_Lateral_Raise", "Cable_Seated_Lateral_Raise", "Machine_Lateral_Raise"),
)
val REAR_DELT = Slot(
    "Rear delt", "shoulders",
    ranked = listOf("Face_Pull", "Reverse_Machine_Flyes", "Reverse_Flyes"),
)
val PULLDOWN = Slot(
    "Vertical pull", "lats",
    ranked = listOf("Wide-Grip_Lat_Pulldown", "Pullups", "Chin-Up", "Assisted_Pull_Up_Machine", "Underhand_Cable_Pulldowns"),
)
val SECOND_PULLDOWN = Slot(
    "Second pulldown", "lats",
    ranked = listOf("Underhand_Cable_Pulldowns", "Close-Grip_Front_Lat_Pulldown", "Chin-Up", "Pullups", "One_Arm_Lat_Pulldown"),
)
val ROW = Slot(
    "Row", "middle back",
    ranked = listOf("Bent_Over_Barbell_Row", "Seated_Cable_Rows", "One-Arm_Dumbbell_Row", "Chest_Supported_Row_Machine", "Inverted_Row"),
    secondTier = listOf("Pendlay_Row", "T-Bar_Row_with_Handle"),
)
val SECOND_ROW = Slot(
    "Second row", "middle back",
    ranked = listOf("Seated_Cable_Rows", "One-Arm_Dumbbell_Row", "Chest_Supported_Row_Machine", "Bent_Over_Barbell_Row", "Inverted_Row"),
    secondTier = listOf("T-Bar_Row_with_Handle", "Pendlay_Row"),
)
val SHRUG = Slot(
    "Shrug", "traps",
    ranked = listOf("Barbell_Shrug", "Dumbbell_Shrug", "Leverage_Shrug"),
)
val CURL = Slot(
    "Biceps curl", "biceps",
    ranked = listOf("Dumbbell_Bicep_Curl", "Barbell_Curl", "EZ-Bar_Curl", "Hammer_Curls", "Machine_Preacher_Curls"),
)
val SECOND_CURL = Slot(
    "Second curl", "biceps",
    ranked = listOf("Hammer_Curls", "Incline_Dumbbell_Curl", "Preacher_Curl", "Machine_Preacher_Curls", "EZ-Bar_Curl", "Barbell_Curl"),
)
val TRICEPS = Slot(
    "Triceps", "triceps",
    ranked = listOf("Triceps_Pushdown", "Cable_Rope_Overhead_Triceps_Extension", "EZ-Bar_Skullcrusher", "Dips_-_Triceps_Version", "Bench_Dips"),
    secondTier = listOf("Close-Grip_Barbell_Bench_Press"),
)
val SECOND_TRICEPS = Slot(
    "Second triceps", "triceps",
    ranked = listOf("Cable_Rope_Overhead_Triceps_Extension", "EZ-Bar_Skullcrusher", "Dips_-_Triceps_Version", "Triceps_Pushdown", "Bench_Dips"),
    secondTier = listOf("Close-Grip_Barbell_Bench_Press"),
)
val ABS = Slot(
    "Abs", "abdominals",
    ranked = listOf("Crunches", "Plank", "Cable_Crunch", "Decline_Crunch"),
)
val WRIST_CURL = Slot(
    "Wrist curl", "forearms",
    ranked = listOf("Seated_Dumbbell_Palms-Up_Wrist_Curl", "Palms-Up_Barbell_Wrist_Curl_Over_A_Bench"),
)

/** Every slot, in the order their lifts rank against each other wherever one list is wanted. */
val SLOTS: List<Slot> = listOf(
    SQUAT, BENCH, PULLDOWN, ROW, OVERHEAD_PRESS, HINGE, DEADLIFT, HIP_THRUST, CURL, TRICEPS,
    INCLINE, SECOND_QUADS, LEG_CURL, LATERAL_RAISE, CALF_RAISE, SHRUG, REAR_DELT, CHEST_FLY,
    SECOND_PULLDOWN, SECOND_ROW, SECOND_CURL, SECOND_TRICEPS, BACK_EXTENSION, ABS, WRIST_CURL,
)

/*
 * The recipes, from the days the surveyed programs actually train (research §2). Compounds come
 * first because every program puts them first, whatever it is for. Always-filled slots are the
 * ones that give each muscle the day names a lift of its own; the rest are added as the budget
 * asks and dropped, last first, when the day would run long.
 *
 * Rear delts sit on the days that name shoulders rather than on Pull, which does not: a slot for
 * a muscle the day is not for would be volume no budget asked for (ADR 0005).
 */

val PUSH_RECIPE: DayRecipe = listOf(
    always(BENCH), always(OVERHEAD_PRESS), ifNeeded(INCLINE), ifNeeded(LATERAL_RAISE),
    always(TRICEPS), ifNeeded(SECOND_TRICEPS), ifNeeded(CHEST_FLY), ifNeeded(REAR_DELT),
)

val PULL_RECIPE: DayRecipe = listOf(
    always(PULLDOWN), always(ROW), ifNeeded(SECOND_PULLDOWN), ifNeeded(SECOND_ROW),
    always(SHRUG), always(CURL), ifNeeded(SECOND_CURL),
)

val LEGS_RECIPE: DayRecipe = listOf(
    always(SQUAT), always(HINGE), ifNeeded(SECOND_QUADS), always(HIP_THRUST),
    ifNeeded(LEG_CURL), always(BACK_EXTENSION), always(CALF_RAISE),
)

val UPPER_RECIPE: DayRecipe = listOf(
    always(BENCH), always(ROW), always(OVERHEAD_PRESS), always(PULLDOWN),
    ifNeeded(INCLINE), ifNeeded(LATERAL_RAISE), always(CURL), always(TRICEPS), always(SHRUG),
    ifNeeded(SECOND_ROW), ifNeeded(SECOND_PULLDOWN), ifNeeded(REAR_DELT), ifNeeded(CHEST_FLY),
)

val FULL_BODY_A_RECIPE: DayRecipe = listOf(
    always(SQUAT), always(BENCH), always(ROW), always(OVERHEAD_PRESS),
    ifNeeded(SECOND_QUADS), ifNeeded(INCLINE), ifNeeded(LATERAL_RAISE), always(ABS),
)

val FULL_BODY_B_RECIPE: DayRecipe = listOf(
    always(DEADLIFT), always(PULLDOWN), always(HIP_THRUST), always(LEG_CURL),
    always(TRICEPS), always(CURL), always(SHRUG), always(CALF_RAISE), ifNeeded(SECOND_PULLDOWN),
)

/** Different lifts from A where both name a muscle, which is what makes the two sessions differ. */
val FULL_BODY_C_RECIPE: DayRecipe = listOf(
    always(SECOND_QUADS), always(INCLINE), always(PULLDOWN), always(BACK_EXTENSION),
    always(CURL), ifNeeded(SQUAT), ifNeeded(CHEST_FLY), ifNeeded(SECOND_CURL),
)

val CHEST_RECIPE: DayRecipe = listOf(
    always(BENCH), ifNeeded(INCLINE), ifNeeded(CHEST_FLY), always(TRICEPS), ifNeeded(SECOND_TRICEPS),
)

val BACK_RECIPE: DayRecipe = listOf(
    always(PULLDOWN), always(ROW), ifNeeded(SECOND_PULLDOWN), ifNeeded(SECOND_ROW), always(SHRUG),
)

val SHOULDERS_RECIPE: DayRecipe = listOf(
    always(OVERHEAD_PRESS), ifNeeded(LATERAL_RAISE), ifNeeded(REAR_DELT), always(SHRUG),
)

val ARMS_RECIPE: DayRecipe = listOf(
    always(CURL), always(TRICEPS), ifNeeded(SECOND_CURL), ifNeeded(SECOND_TRICEPS), always(WRIST_CURL),
)

/** The arms day of upper, lower, arms, which names shoulders too. */
val ARMS_AND_SHOULDERS_RECIPE: DayRecipe = listOf(
    always(OVERHEAD_PRESS), ifNeeded(LATERAL_RAISE), always(CURL), always(TRICEPS),
    ifNeeded(SECOND_CURL), ifNeeded(SECOND_TRICEPS), always(WRIST_CURL), ifNeeded(REAR_DELT),
)

/**
 * Lifts that are never generated or suggested, for anyone (ADR 0013): Olympic lifts, plyometrics,
 * strongman, stretches, unstable-surface work and specialty lifts. Typing a name still finds every
 * one of them, because that is the lifter choosing it.
 *
 * Read from the category where the bank's category can be trusted, and from the name where it
 * cannot: several Olympic variants are filed under `strength`, and the specialty lifts have no tag
 * of their own (research §4).
 */
val Exercise.isNeverGenerated: Boolean
    get() = category in NEVER_GENERATED_CATEGORIES ||
        equipment == "exercise ball" ||
        listOf(name, bankName).any { label -> NEVER_GENERATED_NAMES.containsMatchIn(label) }

private val NEVER_GENERATED_CATEGORIES =
    setOf("olympic weightlifting", "plyometrics", "strongman", "stretching", "cardio")

private val NEVER_GENERATED_NAMES = Regex(
    listOf(
        // Olympic variants filed under strength. A front squat's clean grip is only a grip.
        "\\bclean\\b(?! grip)", "\\bsnatch", "\\bjerk\\b", "push press",
        // Unstable surfaces.
        "bosu", "balance board", "exercise ball", "stability ball",
        // Specialty and accommodating resistance.
        "with chains", "with bands", "reverse band", "board press", "deficit", "rack pull",
        "behind the neck", "upright row", "around the worlds?", "guillotine", "renegade",
        "kettlebell swing", "good morning", "pullover", "front (dumbbell |plate |cable )?raise", "drag curl",
    ).joinToString("|"),
    RegexOption.IGNORE_CASE,
)

/**
 * The lifts this slot can offer, best first: its ranked common lifts, the second tier where the
 * lifter is advanced and [widened], then every other lift for its muscle (see [fallbackFor]).
 */
internal fun Slot.candidatesIn(pool: List<Exercise>, widened: Boolean): List<Exercise> {
    val byId = pool.associateBy(Exercise::id)
    val named = (ranked + if (widened) secondTier else emptyList()).mapNotNull(byId::get)
    return (named + pool.fallbackFor(muscle, widened)).distinctBy(Exercise::id)
}

/**
 * Every lift in [this] for [muscle] that could be generated, ranked: the common lifts for that
 * muscle first, in [SLOTS] order, then the rest beginner-tagged first, compounds before isolation,
 * then by id, which is arbitrary and total, so the answer never depends on the bank's order.
 *
 * The second tier is left out unless [widened]: a front squat is not a fallback for a lifter
 * who was never meant to be offered one.
 */
internal fun List<Exercise>.fallbackFor(muscle: String, widened: Boolean): List<Exercise> {
    val rank = commonRankFor(muscle, widened)
    return filter {
        muscle in it.primaryMuscles && !it.isNeverGenerated && it.level != "expert" &&
            (widened || it.id !in SECOND_TIER)
    }
        .sortedWith(
            compareBy<Exercise> { rank[it.id] ?: Int.MAX_VALUE }
                .thenBy { if (it.level == "beginner") 0 else 1 }
                .thenByDescending { it.isCompound }
                .thenBy { it.id },
        )
}

/** Every second-tier lift, which only an advanced lifter is offered. */
private val SECOND_TIER: Set<String> by lazy { SLOTS.flatMapTo(mutableSetOf()) { it.secondTier } - SLOTS.flatMap { it.ranked }.toSet() }

/** Each common lift for [muscle], to its place in the ranking across that muscle's slots. */
private fun commonRankFor(muscle: String, widened: Boolean): Map<String, Int> =
    SLOTS.filter { it.muscle == muscle }
        .flatMap { it.ranked + if (widened) it.secondTier else emptyList() }
        .distinct()
        .withIndex()
        .associate { (index, id) -> id to index }

/**
 * The lifts in [this] worth suggesting for [muscle], ranked the way a slot would rank them.
 *
 * What the picker's *Same muscle* list shows (ADR 0013): the generator and the picker share one
 * rule for what is worth offering, so the order follows it too. Nothing that could never be
 * generated is suggested here; the rest of the bank and search still find it.
 */
fun List<Exercise>.rankedFor(muscle: String, experience: Experience): List<Exercise> =
    fallbackFor(muscle, widened = experience == Experience.ADVANCED)

/**
 * [this] with the common lifts first, best-ranked first, and everything else after in the order
 * it came: the picker's whole-bank list, so the lifts programs use are the first ones seen.
 */
fun List<Exercise>.commonFirst(experience: Experience): List<Exercise> {
    val widened = experience == Experience.ADVANCED
    val rank = SLOTS.flatMap { it.ranked + if (widened) it.secondTier else emptyList() }
        .distinct()
        .withIndex()
        .associate { (index, id) -> id to index }
    return sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}
