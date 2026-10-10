package com.tylergimpel.traintracks.domain

import java.time.Duration

/** One position of a shape, with what the generator put in it and how much of each. */
data class FilledDay(
    val shape: SplitDay,
    val exercises: List<PlannedExercise>,
)

/**
 * Fills a **Split shape**'s days from the bank, against the lifter's gym and their exclusions.
 *
 * The half of generation that cannot be templated. **Each day follows its Day recipe** (ADR
 * 0013): slots by movement pattern, taken from the programs people actually follow, each filled
 * with the best-ranked **Common lift** the gym can do. The structure comes from proven routines and
 * the lifts from the lifter's own gym. A slot with no common lift here falls back to any other lift
 * for its muscle outside the never-generated groups, beginner-tagged first.
 *
 * **Coverage is still the rule, and it is coverage by direct work.** Every muscle a day names
 * gets an exercise that works it *primarily*, which every recipe's always-filled slots give it.
 * Secondary work is emphatically not coverage: letting it count collapses an upper day into three
 * exercises and leaves the muscles the day exists for with half-credit work.
 *
 * **How much of each** comes from the set budget: a muscle's weekly target divided by how often
 * this shape trains it, less what the rest of the cycle already gives it as secondary work (see
 * [settled]). A recipe's optional slots are added only as that budget needs them, and **dropped
 * last-first when the day would run more than 10% past the session length**. Always-filled slots
 * never drop; if those alone run long, the Muscles page says so.
 *
 * **The recipe's order is the training order, for every goal**, because every surveyed program
 * puts its compounds first whatever it is for.
 *
 * **Equipment filters the pool and limits nothing else**, and **experience only widens it**: an
 * advanced lifter's Build it again may reach a second tier of common lifts, but nobody's first
 * routine does.
 *
 * **Deterministic.** Candidates are totally ordered, so the same answers produce the same routine
 * however the bank happened to arrive. Variety is something the lifter asks for (#75), never
 * something that happens to them.
 *
 * A muscle nothing in the gym can train is left untrained rather than papered over with something
 * that does not work it. What to do about that is a conversation for the validation (#76).
 */
fun fillDays(
    shape: SplitShape,
    bank: List<Exercise>,
    equipment: Set<String>,
    goal: RoutineGoal,
    /**
     * How often the lifter trains, which is the only thing that turns a cycle into a week.
     *
     * **No default**, because every plausible one is wrong somewhere: assuming the cycle comes
     * round weekly asks a once-a-week full-body day for a whole week's volume in one session, and
     * the budget dutifully produces a twenty-exercise day. The lifter has already answered this
     * question at setup; nothing here should be guessing at it.
     */
    cadence: Int,
    /**
     * Exercises the lifter marked to keep, which regeneration treats as fixed.
     *
     * Keyed by the day they were pinned in, because a pin is about where an exercise sits in the
     * shape of the routine and not about the exercise itself (#75).
     */
    pinned: Map<String, List<PlannedExercise>> = emptyMap(),
    /**
     * Which of the equally good candidates to take, so that asking again gives something else.
     *
     * Zero is the first answer and the one a routine is generated with. **Nothing reseeds on its
     * own**: routines do not self-rotate exercises across cycles, so a different number here only
     * ever arrives because the lifter asked for variety (#75). Same seed, same routine — which is
     * what keeps "regenerate" reproducible rather than a slot machine.
     */
    seed: Int = 0,
    /** Advanced widens what Build it again can reach (ADR 0013); it never changes a first routine. */
    experience: Experience = Experience.TRAINED,
    /** How long a session may run, which optional slots are dropped to stay within. */
    sessionMinutes: Int = Routine.USUAL_SESSION_MINUTES,
    /** Cardio after lifting is time in the same session, so it counts against [sessionMinutes]. */
    cardio: CardioPlacement = CardioPlacement.NONE,
): List<FilledDay> {
    // Sorted here rather than at the point of choosing, so that the order the bank arrived in
    // cannot reach the result. The same rule the picker offers by, so a lifter is never generated
    // something the app would not have suggested them.
    val pool = bank.worthOffering(equipment).sortedBy(Exercise::id)
    // Never in a first routine, so asking again is the only way a second-tier lift arrives.
    val widened = experience == Experience.ADVANCED && seed != 0

    val chosen = shape.days.map { day ->
        // Nothing here knows what belongs in a cardio position, and a resistance exercise in one
        // would put volume into a day that is meant to carry none.
        if (day.kind == RoutineDayKind.CARDIO) {
            DayPlan(mutableListOf(), emptyList())
        } else {
            pool.buildDay(day, shape, goal, cadence, pinned[day.name].orEmpty(), seed, widened)
        }
    }

    val ceiling = Duration.ofMinutes(sessionMinutes.toLong())
    return shape.days.zip(chosen.settled(shape, goal, cadence)) { day, plan ->
        FilledDay(day, plan.trimmedTo(day, ceiling, cardio))
    }
}

/**
 * The lift to put in [current]'s place when the lifter asks for another (Build it again on one
 * lift, ADR 0013).
 *
 * The next one in its muscle's ranking, the same list the picker's *Same muscle* shows: common
 * lifts first, then every other lift that could be generated. Walked from where [current] sits, so
 * a fly asked again gives the next fly before it gives a press, and round to the top after the
 * last, so asking keeps giving something. Never something the day already holds; null where
 * nothing else could take its place.
 */
fun nextFor(
    current: Exercise,
    inDay: Set<String>,
    bank: List<Exercise>,
    equipment: Set<String>,
    experience: Experience,
): Exercise? {
    val muscle = current.primaryMuscles.firstOrNull() ?: return null
    val ranked = bank.worthOffering(equipment).sortedBy(Exercise::id).rankedFor(muscle, experience)
    val from = ranked.indexOfFirst { it.id == current.id }
    return (ranked.drop(from + 1) + ranked.take(from.coerceAtLeast(0)))
        .firstOrNull { it.id != current.id && it.id !in inDay }
}

/**
 * The day's exercises in recipe order, with optional slots dropped last-first while the day
 * would run more than 10% past [ceiling], the margin the Muscles page flags a day at (#136).
 *
 * Dropped with their sets rather than handing those to what stays, because the sets are the time.
 */
private fun DayPlan.trimmedTo(
    day: SplitDay,
    ceiling: Duration,
    cardio: CardioPlacement,
): List<PlannedExercise> {
    val kept = exercises.sortedBy { position[it.exercise.id] ?: Int.MAX_VALUE }.toMutableList()
    val limit = ceiling.multipliedBy(11).dividedBy(10)
    fun length() = dayLength(RoutineDay(0, 0, day.name, exercises = kept), cardio, RepRange.DEFAULT)
    while (length() > limit) {
        val last = kept.lastOrNull { it.exercise.id in droppable && !it.pinned } ?: break
        kept.remove(last)
    }
    return kept
}

/**
 * The movements a day chose for one muscle it names, and how many sets each has now.
 *
 * [candidates] is what was chosen against the muscle's whole target, which is the most it could
 * ever want; settling only ever takes fewer of them, never more.
 */
private data class MuscleWork(
    val muscle: String,
    val candidates: List<Exercise>,
    val pinned: Set<String>,
    /** What fills the muscle's always-filled slots, which the budget never takes away. */
    val always: Set<String>,
    /** Every muscle the day names, which is what decides whether a movement's coverage matters. */
    val named: Set<String>,
    val planned: List<PlannedExercise>,
)

/** A day's work, one entry per muscle it budgets, plus pins for muscles it does not name. */
private data class DayPlan(
    val work: MutableList<MuscleWork>,
    val unnamedPins: List<PlannedExercise>,
    /** Each chosen exercise's place in the recipe, which is the order the day is trained in. */
    val position: Map<String, Int> = emptyMap(),
    /** What fills an optional slot, which is what goes first when the day runs long. */
    val droppable: Set<String> = emptySet(),
) {
    val exercises: List<PlannedExercise> get() = work.flatMap { it.planned } + unnamedPins
}

/**
 * The set counts, settled against what the whole cycle already gives each muscle.
 *
 * **Fractional on both sides of the sum.** The targets are stated in fractional sets, so a press
 * that works triceps at a half counts toward triceps' target (ADR 0005), and only what is left is
 * budgeted as triceps' own direct work. Budgeting the whole target as direct work on top of every
 * press and row overshot each muscle that sits second in a compound, and it was most of why an
 * ordinary upper day ran past two hours.
 *
 * **Across the cycle, and in no particular order.** The credit is everything anywhere in the
 * rotation, on any day and chosen before or after, because a lat pulldown's work does not care
 * that the rows were picked second, and triceps trained on one day still had every press on the
 * other. Spread over the days that train the muscle directly, as its own budget is. Counted by
 * [creditFor], the same rule the reports count by, so what the budget assumed and what the routine
 * is then said to train are one number.
 *
 * **Held under the per-session cap day by day**, not only on average: a day already carrying a lot
 * of a muscle's secondary work gets less of its direct work, so no one session goes past the cap
 * because the average session does not.
 *
 * Settled because the credits lean on each other — fewer triceps sets means less for chest from
 * the dips — so the counts are revised one muscle at a time, in the order the days name them,
 * until a round changes nothing. The lists are updated in place on purpose: each muscle is budgeted
 * against the latest answer for every other, which settles sooner than revising them all at once.
 * The order and the cap on rounds are fixed, so the same answers still give the same routine.
 *
 * **Credit changes how much and never whether.** A muscle a day names keeps a movement of its
 * own, because secondary work is not coverage; with nothing left to budget, that movement gets the
 * one set a prescription cannot go below. A muscle's own work on another day is never credited,
 * because its per-session budget already accounts for it.
 */
private fun List<DayPlan>.settled(
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
): List<DayPlan> {
    val plans = map { it.copy(work = it.work.toMutableList()) }
    val lifting = shape.days.filter { it.kind == RoutineDayKind.LIFTING }

    repeat(MOST_SETTLING_ROUNDS) {
        var changed = false
        plans.forEach { today ->
            val work = today.work
            work.indices.forEach { index ->
                val muscle = work[index].muscle
                val fromOthers = { plan: DayPlan ->
                    plan.work.filter { it.muscle != muscle }.flatMap { it.planned }
                        .plus(plan.unnamedPins)
                        .sumOf { (it.sets ?: 0) * it.exercise.creditFor(muscle) }
                }
                val days = lifting.count { muscle in it.muscles }.coerceAtLeast(1)
                val onAverage = setsPerSessionFor(
                    muscle, shape, goal, cadence, credited = plans.sumOf(fromOthers) / days,
                )
                val budget = onAverage.coerceAtMost(
                    (PER_SESSION_CAP - fromOthers(today)).coerceAtLeast(0.0),
                )
                val next = work[index].budgetedAt(budget)
                if (next != work[index]) {
                    work[index] = next
                    changed = true
                }
            }
        }
        if (!changed) return plans
    }
    return plans
}

/**
 * This muscle's work at [budget] direct sets: as few of its movements as that many sets want.
 *
 * Always-filled slots and pins stay whatever the budget, and so does any movement that is also the
 * direct work for another muscle the day names — trimming that would take away coverage the
 * credit never gave. Optional slots come in recipe order as the budget asks for them.
 */
private fun MuscleWork.budgetedAt(budget: Double): MuscleWork {
    val essential = candidates.filter { candidate ->
        candidate.id in pinned || candidate.id in always ||
            candidate.primaryMuscles.any { it != muscle && it in named }
    }
    val optional = candidates.filterNot { it in essential }
        .take((exercisesForSets(budget) - essential.size).coerceAtLeast(0))
    // In the order they were chosen, which is the order the recipe trains them in.
    val keep = candidates.filter { it in essential || it in optional }
    return copy(
        planned = keep.zip(setsAcross(keep.size, budget)) { exercise, count ->
            PlannedExercise(exercise, count, pinned = exercise.id in pinned)
        },
    )
}

/**
 * How many rounds settling gets before it stops where it is.
 *
 * Invented, and generous: each credit is at most half of work that is itself being reduced, so
 * the counts close in quickly. The cap exists so that whole-set rounding flickering between two
 * answers cannot keep going, and so what comes back is still a function of the answers alone.
 */
private const val MOST_SETTLING_ROUNDS = 8

/**
 * The same candidates, starting from a different one.
 *
 * A rotation rather than a shuffle, because the list is already in the order the app would
 * recommend: rotating keeps every candidate reachable and keeps the answer reproducible from the
 * seed alone, where a shuffle would need its own random source threaded through everything.
 */
private fun List<Exercise>.rotatedBy(seed: Int): List<Exercise> = when {
    isEmpty() || seed % size == 0 -> this
    else -> {
        val from = ((seed % size) + size) % size
        drop(from) + take(from)
    }
}

/**
 * The day, chosen: a lift for every slot its recipe has, as many as the whole target could want.
 *
 * Chosen against the whole target, before any credit, so that settling ([settled]) only ever
 * takes movements away. The counts here are a first answer and are revised there.
 *
 * **Slot by slot, muscle by muscle.** Each slot takes the first of its candidates the day does not
 * hold yet, so a Pull day's second pulldown is a different pulldown. A muscle another slot's lift
 * already trains primarily is not answered again; a day naming chest and triceps gets a press and a
 * pushdown, and never loses the pushdown because the press happens to involve triceps.
 *
 * **A pin constrains the day; it does not satisfy it.** An exercise the lifter kept takes the slot
 * it is ranked in, or else one for its muscle, and the rest are still filled around it. Treating a
 * pin as an answer would collapse a muscle budgeted eleven sets across four movements down to the
 * one pinned movement — a silent under-prescription, the invisible volume error ADR 0005 exists to
 * prevent.
 *
 * A day with no recipe is filled muscle by muscle from the same ranking: one slot each that is
 * always filled, and as many more as the budget could ask for.
 *
 * A muscle nothing in the pool trains directly is skipped rather than filled with something that
 * does not train it. The day comes back honestly short, and what to do about that is a
 * conversation for the validation (#76).
 */
private fun List<Exercise>.buildDay(
    day: SplitDay,
    shape: SplitShape,
    goal: RoutineGoal,
    cadence: Int,
    kept: List<PlannedExercise>,
    seed: Int,
    widened: Boolean,
): DayPlan {
    val recipe = day.recipe.ifEmpty { genericRecipeFor(day.muscles) }
    val chosen = mutableListOf<MuscleWork>()
    val answered = mutableSetOf<String>()
    val keptIds = kept.mapTo(mutableSetOf()) { it.exercise.id }
    val taken = keptIds.toMutableSet()
    val position = mutableMapOf<String, Int>()
    val droppable = mutableSetOf<String>()

    // In the order the recipe first reaches each muscle, then any it never reaches, so a muscle
    // the day names is never left out because nobody wrote a slot for it.
    val muscles = (recipe.map { it.slot.muscle } + day.muscles).distinct().filter { it in day.muscles }

    muscles.forEach { muscle ->
        if (muscle in answered) return@forEach
        val slots = recipe.withIndex().filter { it.value.slot.muscle == muscle }
            .ifEmpty { genericRecipeFor(listOf(muscle)).map { IndexedValue(recipe.size, it) } }

        val unplaced = kept.map { it.exercise }
            .filter { muscle in it.primaryMuscles && chosen.none { work -> it in work.candidates } }
            .toMutableList()
        val picks = mutableListOf<Pair<Exercise, IndexedValue<RecipeSlot>>>()
        slots.forEachIndexed { at, indexed ->
            val candidates = indexed.value.slot.candidatesIn(this, widened)
            // A pin takes the slot it is ranked in; one ranked nowhere takes the last slots left,
            // so the slots it is no better a fit for are still filled from the ranking.
            val slot = indexed.value.slot
            val rankedHere = slot.ranked + if (widened) slot.secondTier else emptyList()
            val pin = unplaced.firstOrNull { it.id in rankedHere }
                ?: unplaced.firstOrNull { pin -> slots.none { pin.id in it.value.slot.ranked + it.value.slot.secondTier } }
                    ?.takeIf { slots.size - at <= unplaced.size }
            val pick = pin ?: candidates.rotatedBy(seed).firstOrNull { it.id !in taken }
            if (pick != null) {
                unplaced.remove(pick)
                taken += pick.id
                picks += pick to indexed
            }
        }
        // More pins for this muscle than it has slots: they are still the lifter's, after the rest.
        unplaced.forEach { pin -> picks += pin to slots.last() }
        if (picks.isEmpty()) return@forEach

        val forThisMuscle = picks.map { it.first }
        picks.forEach { (exercise, indexed) ->
            position.putIfAbsent(exercise.id, indexed.index)
            if (!indexed.value.always) droppable += exercise.id
        }
        // Everything these train primarily now has direct work, so nothing else is chosen for it.
        answered += forThisMuscle.flatMap(Exercise::primaryMuscles)
        chosen += MuscleWork(
            muscle = muscle,
            candidates = forThisMuscle,
            pinned = forThisMuscle.map(Exercise::id).filterTo(mutableSetOf()) { it in keptIds },
            always = picks.filter { it.second.value.always }.mapTo(mutableSetOf()) { it.first.id },
            named = day.muscles.toSet(),
            planned = emptyList(),
        ).budgetedAt(setsPerSessionFor(muscle, shape, goal, cadence))
    }

    // A pin for something the day does not name is still the lifter's: they put it there, and a
    // regeneration that quietly dropped it would not be keeping what it promised to keep.
    val placed = chosen.flatMapTo(mutableSetOf()) { work -> work.candidates.map(Exercise::id) }
    return DayPlan(
        work = chosen,
        unnamedPins = kept.filterNot { it.exercise.id in placed }.map { it.copy(pinned = true) },
        position = position,
        droppable = droppable,
    )
}

/**
 * A recipe for a day no program describes: per muscle, one slot always filled and as many more as
 * a budget could ask for, each ranked like any other slot for that muscle.
 */
private fun genericRecipeFor(muscles: List<String>): DayRecipe = muscles.flatMap { muscle ->
    val slot = Slot(pattern = muscle, muscle = muscle, ranked = emptyList())
    List(MOST_EXERCISES_PER_MUSCLE) { index -> RecipeSlot(slot, always = index == 0) }
}
