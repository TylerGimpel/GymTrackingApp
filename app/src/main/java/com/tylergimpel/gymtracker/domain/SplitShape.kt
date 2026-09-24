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
     * The rotation this shape repeats on — full body 1, upper/lower 2, push/pull/legs 3,
     * body-part 5 — or **null where it does not repeat on one**.
     *
     * Null is the case ADR 0005 calls a named variant: *"gaps in the divisibility grid are a
     * prompt to author a variant, not a wall"*, and `U/L/U/L/Full` is exactly that — a five
     * position cycle that is not a five-fold upper/lower and was never derived from a rotation at
     * all. Declaring an invented rotation equal to its own length would make [isBalanced] say yes
     * to anything, which is worse than admitting the rule does not reach it.
     */
    val rotation: Int?,
) {
    /** Positions carrying resistance work, which is what divisibility counts. */
    val liftingPositions: Int get() = days.count { it.kind == RoutineDayKind.LIFTING }

    /**
     * Whether this shape's lifting positions divide by the rotation it repeats on.
     *
     * **This checks the arithmetic, and only the arithmetic.** It catches the case the rule
     * is named for — a three-position `Upper / Lower / Upper` is not alternating, it is 2:1
     * forever — for any shape that says which rotation it repeats on. It cannot check a named
     * variant, which repeats on nothing, and it does not pretend to: those are balanced because
     * a person authored them that way, and the honest statement of that is [rotation] being null
     * rather than a number chosen to make this pass.
     */
    val isBalanced: Boolean get() = rotation == null || liftingPositions % rotation == 0

    /** The same shape with a cardio position on the end, which counts toward neither rule. */
    fun withCardioDay(): SplitShape = copy(
        id = "$id$CARDIO_SUFFIX",
        days = days + SplitDay(CARDIO_DAY, emptyList(), RoutineDayKind.CARDIO),
    )

    companion object {
        const val CARDIO_DAY = "Cardio"
    }
}

/*
 * What each kind of day is *for*, which since coverage means direct work is also how many
 * exercises it comes back with. These are movement targets rather than an anatomy list: a day
 * naming seven muscles is a seven-exercise day, and one naming seventeen is not a day.
 *
 * `forearms` is deliberately absent from everything but an arms day. Almost nobody trains it
 * directly, it is secondary work in every pull in the bank, and naming it on an upper day would
 * add a wrist curl to every routine the app generates.
 */

/** Everything above the waist that pushes or pulls, traps included. */
private val UPPER =
    listOf("chest", "lats", "middle back", "shoulders", "traps", "biceps", "triceps")

/** Everything below it, plus the lower back that holds it all up. */
private val LOWER = listOf("quadriceps", "hamstrings", "glutes", "calves", "lower back")

/**
 * Every muscle a routine should train directly somewhere in its cycle.
 *
 * **What upper and lower reach between them**, taken from the same lists the shapes are built
 * from rather than restated, so the shapes and the check that reads this share one idea of what
 * "everything" is. Every shape offered reaches all of it but one — "full body, every session",
 * which the check reports on like any routine (see [FULL_BODY_A]'s comment for why).
 *
 * Abdominals and forearms are not in it: forearms are named only on an arms day, for the reason
 * above, and abdominals on one full-body day and no upper or lower one. Neither is a gap worth
 * telling a lifter about.
 */
val MAIN_MUSCLES: List<String> = UPPER + LOWER

private val PUSH = listOf("chest", "shoulders", "triceps")
private val PULL = listOf("lats", "middle back", "traps", "biceps")
private val LEGS = LOWER

/*
 * Full-body days, and there are three of them because **they have to differ**. Selection is
 * deterministic, so two days naming the same muscles come back holding the same exercises — which
 * would make "full body, two sessions that differ" a lie told twice. What differs is what each
 * day is for.
 *
 * **A and B between them reach every one of the [MAIN_MUSCLES]**, so any cycle holding both
 * covers everything, as the coverage check holds every routine to. The three the pair used to
 * miss — biceps, traps and the lower back — were added to B rather than A, because A is also the
 * whole of "full body, every session": naming all twelve on its one day measured three hours
 * rather than eighty minutes at three times a week, and a gap the lifter is told about is better
 * than a session nobody can finish.
 * C only has to differ, because it is never offered without A and B beside it.
 */
private val FULL_BODY_A =
    listOf("quadriceps", "chest", "middle back", "shoulders", "abdominals")
private val FULL_BODY_B = listOf(
    "hamstrings", "lats", "glutes", "triceps", "calves", "biceps", "traps", "lower back",
)
private val FULL_BODY_C =
    listOf("quadriceps", "chest", "lats", "biceps", "lower back")

/**
 * Every day structure this app knows how to lay out.
 *
 * Thirteen, which is what the divisibility rule leaves for cycles up to seven positions. They
 * are offered by **shape** — "alternate upper and lower" — because that is a thing anyone can
 * choose, where "a two-position cycle" is not.
 *
 * **None of them is better than another.** Every volume-equated split trial found no difference,
 * so nothing here ranks them on quality and nothing about the wording claims one is superior.
 */
val SPLIT_SHAPES: List<SplitShape> = listOf(
    SplitShape(
        id = "full-body-1",
        description = "Full body, every session",
        rotation = 1,
        days = listOf(SplitDay("Full body", FULL_BODY_A)),
    ),
    SplitShape(
        id = "full-body-2",
        description = "Full body, two sessions that differ",
        rotation = 1,
        days = listOf(
            SplitDay("Full body A", FULL_BODY_A),
            SplitDay("Full body B", FULL_BODY_B),
        ),
    ),
    SplitShape(
        id = "full-body-3",
        description = "Full body, three sessions that differ",
        rotation = 1,
        days = listOf(
            SplitDay("Full body A", FULL_BODY_A),
            SplitDay("Full body B", FULL_BODY_B),
            SplitDay("Full body C", FULL_BODY_C),
        ),
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
        rotation = null,
        days = listOf(
            SplitDay("Upper A", UPPER),
            SplitDay("Lower A", LOWER),
            SplitDay("Upper B", UPPER),
            SplitDay("Lower B", LOWER),
            SplitDay("Full body", FULL_BODY_A),
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
        rotation = null,
        days = listOf(
            SplitDay("Upper", UPPER),
            SplitDay("Lower", LOWER),
            SplitDay("Arms", listOf("biceps", "triceps", "forearms", "shoulders")),
        ),
    ),
    SplitShape(
        id = "full-body-upper-lower-4",
        description = "Full body, upper, full body, lower",
        rotation = null,
        days = listOf(
            SplitDay("Full body A", FULL_BODY_A),
            SplitDay("Upper", UPPER),
            SplitDay("Full body B", FULL_BODY_B),
            SplitDay("Lower", LOWER),
        ),
    ),
    SplitShape(
        id = "push-pull-legs-upper-lower-5",
        description = "Push, pull, legs, upper, lower",
        rotation = null,
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
 * The shapes to offer a lifter who trains [cadence] times a week.
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
    cadence: Int,
    cardio: CardioPlacement = CardioPlacement.NONE,
): List<SplitShape> = SPLIT_SHAPES
    .map { if (cardio == CardioPlacement.OWN_DAY) it.withCardioDay() else it }
    .sortedWith(
        compareBy<SplitShape> { kotlin.math.abs(it.liftingPositions - cadence) }
            .thenBy { it.liftingPositions },
    )

/**
 * The shape with this id, or null where nothing answers to it.
 *
 * Covers the cardio variants too, which are the base shapes with a position on the end rather
 * than entries in the list — so a routine laid out from one can still be found by what it stored.
 */
fun shapeOf(id: String): SplitShape? = when {
    id.endsWith(CARDIO_SUFFIX) ->
        SPLIT_SHAPES.firstOrNull { it.id == id.removeSuffix(CARDIO_SUFFIX) }?.withCardioDay()

    else -> SPLIT_SHAPES.firstOrNull { it.id == id }
}

/** What [SplitShape.withCardioDay] appends to an id, so the pair can be read back apart. */
private const val CARDIO_SUFFIX = "-cardio"
