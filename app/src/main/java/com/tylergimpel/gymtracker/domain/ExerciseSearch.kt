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
        .filterKeys { it.tokenise().matches(typed) }
        .values
        .flatten()
        .mapTo(mutableSetOf(), String::lowercase)

    return filter { exercise ->
        exercise.name.tokenise().matches(typed) || exercise.name.lowercase() in namedByAlias
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
 * Whether every word typed matches the start of some word here.
 *
 * Prefix rather than equality, because a lifter types their way toward a name and should see it
 * before they have finished spelling it.
 */
private fun List<String>.matches(typed: List<String>): Boolean =
    typed.all { word -> any { it.startsWith(word) } }

/** Lowercased words, with anything that is not a letter or digit treated as a space. */
private fun String.tokenise(): List<String> =
    lowercase().split(NON_WORD).filter(String::isNotEmpty)

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
