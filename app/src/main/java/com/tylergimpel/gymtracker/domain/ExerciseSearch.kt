package com.tylergimpel.gymtracker.domain

/**
 * The shorthand a lifter might type, resolved to the exercise names it means.
 *
 * Shipped alongside the bank and validated at build time, so every target here names an exercise
 * that exists — an alias pointing at nothing would fail silently, finding no results and saying
 * nothing about why.
 */
typealias ExerciseAliases = Map<String, List<String>>

/**
 * Finds exercises by what someone types.
 *
 * Matching is normalised and token-based: every word typed has to match the start of some word in
 * the target, in any order. That is what lets "press bench" find a bench press while "barbell
 * squat" does not return every barbell movement.
 *
 * An empty query is not a failed search — it is the whole bank, which is what the screen shows
 * before anyone types anything.
 */
fun List<Exercise>.search(
    query: String,
    aliases: ExerciseAliases = emptyMap(),
): List<Exercise> {
    val typed = query.tokenise()
    if (typed.isEmpty()) return this

    // The same rule matches names and shorthand, so "pec deck" reaches Butterfly by exactly the
    // route "bench press" reaches a bench press.
    val namedByAlias = aliases
        .filterKeys { alias -> typed.allMatch(alias.tokenise()) }
        .values
        .flatten()
        .mapTo(mutableSetOf(), String::lowercase)

    return filter { exercise ->
        typed.allMatch(exercise.name.tokenise()) ||
            // What the bank calls it, too: a lifter who renamed Butterfly to "Pec Deck" should
            // still find it by typing either, and the shorthand targets the bank's names — so
            // resolving an alias against a renamed exercise has to look at the name it shipped
            // with, or renaming one would silently break the shorthand pointing at it.
            typed.allMatch(exercise.bankName.tokenise()) ||
            exercise.bankName.lowercase() in namedByAlias
    }
}

/**
 * The exercises that work [muscle], those targeting it primarily first.
 *
 * Secondary work is included because an exercise that works a muscle does work it — but it comes
 * after, so "something for triceps" is answered by triceps exercises rather than by every press
 * that happens to involve them.
 */
fun List<Exercise>.workingMuscle(muscle: String): List<Exercise> {
    val (primary, secondary) = filter {
        muscle in it.primaryMuscles || muscle in it.secondaryMuscles
    }.partition { muscle in it.primaryMuscles }
    return primary + secondary
}

/**
 * Whether every word typed matches the start of some word in [target].
 *
 * Prefix rather than equality, because a lifter types their way toward a name and should see it
 * before they have finished spelling it. Named for its direction: the words typed are matched
 * against the target, and swapping the two would quietly mean something else.
 */
private fun List<String>.allMatch(target: List<String>): Boolean =
    all { typed -> target.any { it.startsWith(typed) } }

/** Lowercased words, with anything that is not a letter or digit treated as a space. */
private fun String.tokenise(): List<String> =
    lowercase().split(NON_WORD).filter(String::isNotEmpty)

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/**
 * What the picker offers, wherever it was opened from.
 *
 * One rule for swapping mid-session and for filling a routine day, because both ask the same
 * question and a lifter should not have to learn two lists. What differs between the two callers
 * is what they rank in front of these, never what is in them.
 *
 * Four filters, in the order that keeps the ordering meaningful:
 *
 * - **A dropped exercise is never offered.** A tombstone exists for the history pointing at it.
 * - **A Dislike is a hard constraint on what is *suggested*** — but typing a name is not the app
 *   suggesting anything, it is the lifter asking for that one. Refusing to find it would be the
 *   app arguing with them; what should happen next is a warning, which is the caller's business.
 * - **The gym's equipment**, unless [ownEquipmentOnly] is off. An empty checklist means nobody
 *   has described the gym yet, so everything is offered rather than nothing.
 * - **What was typed, then the muscle**, in that order, because filtering by muscle is what puts
 *   exercises that target it in front of ones that merely involve it.
 *
 * A **User-authored exercise** passes through all four exactly as a shipped row does.
 */
fun List<Exercise>.offeredFor(
    equipment: Set<String>,
    query: String = "",
    muscle: String? = null,
    ownEquipmentOnly: Boolean = true,
    aliases: ExerciseAliases = emptyMap(),
): List<Exercise> = worthOffering(
    equipment = if (ownEquipmentOnly) equipment else emptySet(),
    // Typing a name is the lifter asking for that one rather than the app suggesting anything,
    // and refusing to find it would be the app arguing with them.
    includeDisliked = query.isNotBlank(),
)
    .search(query, aliases)
    .let { if (muscle == null) it else it.workingMuscle(muscle) }

/**
 * The exercises worth putting in front of somebody, before anything is typed or filtered.
 *
 * One rule, because there are two callers and they must not drift: the picker offers these, and
 * the generator chooses from them. Extracted for the reason the volume counting was — a rule
 * written twice becomes two rules the first time one of them is corrected.
 *
 * An empty [equipment] set means nobody has described the gym yet, so everything is offered
 * rather than nothing; an exercise needing no equipment is never excluded by a checklist.
 */
fun List<Exercise>.worthOffering(
    equipment: Set<String>,
    includeDisliked: Boolean = false,
): List<Exercise> = filter {
    // A dropped exercise exists for the history pointing at it, and is not something to offer.
    !it.isTombstoned &&
        // A **Dislike** is a hard constraint on what the app puts forward of its own accord.
        (includeDisliked || !it.isDisliked) &&
        // A **User-authored exercise** passes this exactly as a shipped row does.
        (equipment.isEmpty() || it.equipment == null || it.equipment in equipment)
}

/**
 * Every exercise the lifter has excluded, by name, so each can be looked at and taken back (#93).
 *
 * A dropped row is left out even if it was excluded: it is there for the history pointing at it
 * and can never be offered again, so there is nothing to take back.
 */
fun List<Exercise>.excluded(): List<Exercise> =
    filter { it.isDisliked && !it.isTombstoned }.sortedBy { it.name.lowercase() }
