package com.tylergimpel.gymtracker.domain

/**
 * One position in a split: what it is called, and which muscles it is for.
 *
 * The muscles are what the generator fills against (#73). They are named in the bank's own closed
 * vocabulary (`data/README.md`), because a day asking for a muscle the bank has never heard of is
 * a day nothing can fill.
 */
data class SplitDay(
    val name: String,
    val muscles: List<String>,
    val kind: RoutineDayKind = RoutineDayKind.LIFTING,
)

/**
 * A hand-authored day structure: the shape of a rotation, before anything is in it.
 *
 * **The hand-authored half of the hybrid.** Day structures can be written down because the
 * divisibility rule collapses the space to something small — full body at any length, upper/lower
 * at even lengths, push/pull/legs at multiples of three, body-part at five. What cannot be written
 * down is which exercises go in them, because that is where the variance between gyms lives, and
 * that is the other half (#73).
 *
 * **Divisibility holds by construction.** `N mod R == 0` is not checked when a lifter picks one;
 * it is true of every shape here because none was authored that breaks it, and a test asserts that
 * of the whole list rather than of any one use. A gap in the grid is a prompt to author a variant,
 * not a wall.
 */
data class SplitShape(
    val id: String,
    /** What the lifter is choosing: a shape they recognise, never a position count. */
    val description: String,
    val days: List<SplitDay>,
    /**
     * The rotation this shape repeats on — full body 1, upper/lower 2, push/pull/legs 3, and 5
     * for a body-part split.
     *
     * What divisibility is stated against, and the reason a five-position upper/lower is not
     * offered while a five-position cycle that happens to end in a full-body day is.
     */
    val rotation: Int,
) {
    /** Positions carrying resistance work, which is what divisibility counts. */
    val liftingPositions: Int get() = days.count { it.kind == RoutineDayKind.LIFTING }

    /**
     * Whether this shape's lifting positions divide by its rotation.
     *
     * True of everything authored below. It exists so a test can say so of the list, and so a
     * variant added later cannot quietly introduce the permanent imbalance the rule prevents: a
     * three-position `Upper / Lower / Upper` is not alternating, it is 2:1 forever.
     */
    val isBalanced: Boolean get() = liftingPositions % rotation == 0

    /** The same shape with a cardio position on the end, which counts toward neither rule. */
    fun withCardioDay(): SplitShape = copy(
        id = "$id-cardio",
        days = days + SplitDay(CARDIO_DAY, emptyList(), RoutineDayKind.CARDIO),
    )

    companion object {
        const val CARDIO_DAY = "Cardio"
    }
}

/** Everything above the waist that pushes or pulls. */
private val UPPER = listOf("chest", "lats", "middle back", "shoulders", "biceps", "triceps")

/** Everything below it, plus the lower back that holds it all up. */
private val LOWER = listOf("quadriceps", "hamstrings", "glutes", "calves", "lower back")

private val PUSH = listOf("chest", "shoulders", "triceps")
private val PULL = listOf("lats", "middle back", "biceps", "traps")
private val LEGS = LOWER

private val WHOLE_BODY = UPPER + LOWER + listOf("abdominals")

/**
 * Every day structure this app knows how to lay out.
 *
 * Thirteen, which is what the divisibility rule leaves for cycles up to seven positions. They are
 * offered by **shape** — "alternate upper and lower" — because that is a thing anyone can choose,
 * where "a two-position cycle" is not.
 *
 * **None of them is better than another.** Every volume-equated split trial found no difference,
 * so nothing here ranks them on quality and nothing about the wording claims one is superior.
 */
val SPLIT_SHAPES: List<SplitShape> = listOf(
    SplitShape(
        id = "full-body-1",
        description = "Full body, every session",
        rotation = 1,
        days = listOf(SplitDay("Full body", WHOLE_BODY)),
    ),
    SplitShape(
        id = "full-body-2",
        description = "Full body, two sessions that differ",
        rotation = 1,
        days = listOf(SplitDay("Full body A", WHOLE_BODY), SplitDay("Full body B", WHOLE_BODY)),
    ),
    SplitShape(
        id = "full-body-3",
        description = "Full body, three sessions that differ",
        rotation = 1,
        days = List(3) { SplitDay("Full body ${'A' + it}", WHOLE_BODY) },
    ),
    SplitShape(
        id = "upper-lower-2",
        description = "Alternate upper and lower",
        rotation = 2,
        days = listOf(SplitDay("Upper", UPPER), SplitDay("Lower", LOWER)),
    ),
    SplitShape(
        id = "upper-lower-4",
        description = "Upper and lower, twice round",
        rotation = 2,
        days = listOf(
            SplitDay("Upper A", UPPER),
            SplitDay("Lower A", LOWER),
            SplitDay("Upper B", UPPER),
            SplitDay("Lower B", LOWER),
        ),
    ),
    SplitShape(
        id = "upper-lower-6",
        description = "Upper and lower, three times round",
        rotation = 2,
        days = (1..3).flatMap {
            listOf(SplitDay("Upper $it", UPPER), SplitDay("Lower $it", LOWER))
        },
    ),
    SplitShape(
        id = "upper-lower-full-5",
        description = "Upper, lower, upper, lower, full body",
        rotation = 5,
        days = listOf(
            SplitDay("Upper A", UPPER),
            SplitDay("Lower A", LOWER),
            SplitDay("Upper B", UPPER),
            SplitDay("Lower B", LOWER),
            SplitDay("Full body", WHOLE_BODY),
        ),
    ),
    SplitShape(
        id = "push-pull-legs-3",
        description = "Push, pull, legs",
        rotation = 3,
        days = listOf(SplitDay("Push", PUSH), SplitDay("Pull", PULL), SplitDay("Legs", LEGS)),
    ),
    SplitShape(
        id = "push-pull-legs-6",
        description = "Push, pull, legs, twice round",
        rotation = 3,
        days = (1..2).flatMap {
            listOf(
                SplitDay("Push $it", PUSH),
                SplitDay("Pull $it", PULL),
                SplitDay("Legs $it", LEGS),
            )
        },
    ),
    SplitShape(
        id = "body-part-5",
        description = "A body part a session",
        rotation = 5,
        days = listOf(
            SplitDay("Chest", listOf("chest", "triceps")),
            SplitDay("Back", listOf("lats", "middle back", "traps")),
            SplitDay("Shoulders", listOf("shoulders", "traps")),
            SplitDay("Arms", listOf("biceps", "triceps", "forearms")),
            SplitDay("Legs", LEGS),
        ),
    ),
    SplitShape(
        id = "upper-lower-arms-3",
        description = "Upper, lower, arms",
        rotation = 3,
        days = listOf(
            SplitDay("Upper", UPPER),
            SplitDay("Lower", LOWER),
            SplitDay("Arms", listOf("biceps", "triceps", "forearms", "shoulders")),
        ),
    ),
    SplitShape(
        id = "full-body-upper-lower-4",
        description = "Full body, upper, full body, lower",
        rotation = 4,
        days = listOf(
            SplitDay("Full body A", WHOLE_BODY),
            SplitDay("Upper", UPPER),
            SplitDay("Full body B", WHOLE_BODY),
            SplitDay("Lower", LOWER),
        ),
    ),
    SplitShape(
        id = "push-pull-legs-upper-lower-5",
        description = "Push, pull, legs, upper, lower",
        rotation = 5,
        days = listOf(
            SplitDay("Push", PUSH),
            SplitDay("Pull", PULL),
            SplitDay("Legs", LEGS),
            SplitDay("Upper", UPPER),
            SplitDay("Lower", LOWER),
        ),
    ),
)

/**
 * The shapes to offer a lifter who trains [weeklyFrequency] times a week.
 *
 * **Every shape is offered.** Weekly frequency does not constrain cycle length and never has —
 * that is the whole of ADR 0005's second half, and a lifter training five times a week on a
 * two-position upper/lower is running a perfectly sound routine that produces `U/L/U/L/U` then
 * `L/U/L/U/L` with no two-week template and no special case.
 *
 * What frequency does is decide the **order**, so the likeliest answer is near the top: somebody
 * training four times a week most often wants a cycle that comes round about weekly. That is a
 * convenience and **not a claim that any shape is better than another**, which nothing in the
 * evidence would support.
 *
 * A lifter whose cardio has a day of its own gets each shape with a cardio position on the end.
 * `Upper / Lower / Cardio` is offered despite three not dividing by two, because divisibility
 * counts only positions carrying resistance work.
 */
fun splitShapesFor(
    weeklyFrequency: Int,
    cardio: CardioPlacement = CardioPlacement.NONE,
): List<SplitShape> = SPLIT_SHAPES
    .map { if (cardio == CardioPlacement.OWN_DAY) it.withCardioDay() else it }
    .sortedWith(
        compareBy<SplitShape> { kotlin.math.abs(it.liftingPositions - weeklyFrequency) }
            .thenBy { it.liftingPositions },
    )
