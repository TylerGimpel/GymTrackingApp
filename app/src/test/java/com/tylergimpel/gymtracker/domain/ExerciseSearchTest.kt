package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Finding an exercise without scrolling.
 *
 * Formal names are not what people type: the bank calls the pec deck `Butterfly`, and nobody types
 * "Barbell Bench Press - Medium Grip". Matching is normalised and token-based so that word order,
 * case and punctuation do not stand between a lifter and the thing they are about to do.
 */
class ExerciseSearchTest {

    private val bank = listOf(
        exercise("Barbell Bench Press - Medium Grip", primary = "chest", secondary = listOf("triceps")),
        exercise("Butterfly", primary = "chest"),
        exercise("Barbell Squat", primary = "quadriceps", secondary = listOf("glutes")),
        exercise("Romanian Deadlift", primary = "hamstrings", secondary = listOf("lower back")),
        exercise("Bulgarian Split Squat", primary = "quadriceps"),
        exercise("Triceps Pushdown", primary = "triceps"),
    )

    @Test
    fun `typing part of a name finds it`() {
        assertEquals(listOf("Butterfly"), bank.found("butter"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(listOf("Butterfly"), bank.found("BUTTERFLY"))
    }

    @Test
    fun `word order does not matter`() {
        assertEquals(listOf("Barbell Bench Press - Medium Grip"), bank.found("press bench"))
    }

    @Test
    fun `punctuation does not matter`() {
        assertEquals(listOf("Barbell Bench Press - Medium Grip"), bank.found("bench-press"))
    }

    @Test
    fun `every word typed has to match something`() {
        // "barbell squat" must not return the bench press just because it is also a barbell.
        assertEquals(listOf("Barbell Squat"), bank.found("barbell squat"))
    }

    @Test
    fun `an empty query returns the whole bank`() {
        assertEquals(bank.map(Exercise::name), bank.found(""))
    }

    @Test
    fun `a query matching nothing returns nothing`() {
        assertEquals(emptyList<String>(), bank.found("zercher"))
    }

    @Test
    fun `shorthand finds the exercise the bank actually calls it`() {
        // The alias list exists because of this one: nobody searching for a pec deck types
        // "Butterfly", and the bank has no other name for it.
        assertEquals(listOf("Butterfly"), bank.found("pec deck", aliases))
    }

    @Test
    fun `an initialism finds what it stands for`() {
        assertEquals(listOf("Romanian Deadlift"), bank.found("rdl", aliases))
        assertEquals(listOf("Bulgarian Split Squat"), bank.found("bss", aliases))
    }

    @Test
    fun `an alias naming several exercises returns all of them`() {
        assertEquals(
            listOf("Barbell Bench Press - Medium Grip", "Butterfly"),
            bank.found("chest press", aliases),
        )
    }

    @Test
    fun `an alias and a name matching the same query return one result, not two`() {
        // "squat" matches two names outright and is also shorthand for one of them.
        assertEquals(listOf("Barbell Squat", "Bulgarian Split Squat"), bank.found("squat", aliases))
    }

    @Test
    fun `shorthand is reached by the same rule as a name, so it matches part-typed`() {
        assertEquals(listOf("Butterfly"), bank.found("pec", aliases))
    }

    @Test
    fun `a renamed exercise is found under the lifter's name for it`() {
        val bank = listOf(exercise("Butterfly", "Butterfly").copy(userName = "Pec Deck"))

        assertEquals(listOf("Pec Deck"), bank.search("pec deck").map { it.name })
    }

    @Test
    fun `a renamed exercise is still found under the name the bank shipped`() {
        val bank = listOf(exercise("Butterfly", "Butterfly").copy(userName = "Pec Deck"))

        assertEquals(1, bank.search("butterfly").size)
    }

    @Test
    fun `renaming an exercise does not break the shorthand pointing at it`() {
        // Aliases target the bank's names. If a rename hid that, "pec deck" would stop finding
        // Butterfly the moment someone renamed it to something else entirely — the silent failure
        // the build-time alias check exists to prevent.
        val bank = listOf(exercise("Butterfly", "Butterfly").copy(userName = "Chest Machine"))

        assertEquals(
            listOf("Chest Machine"),
            bank.search("pec deck", mapOf("pec deck" to listOf("Butterfly"))).map { it.name },
        )
    }

    @Test
    fun `filtering by muscle finds what works it, what targets it first`() {
        assertEquals(
            listOf("Triceps Pushdown", "Barbell Bench Press - Medium Grip"),
            bank.workingMuscle("triceps").map(Exercise::name),
        )
    }

    @Test
    fun `filtering by a muscle nothing works returns nothing`() {
        assertEquals(emptyList<String>(), bank.workingMuscle("neck").map(Exercise::name))
    }

    @Test
    fun `filtering and typing narrow together`() {
        assertEquals(
            listOf("Barbell Squat"),
            bank.workingMuscle("quadriceps").search("barbell").map(Exercise::name),
        )
    }

    private val aliases = mapOf(
        "pec deck" to listOf("Butterfly"),
        "rdl" to listOf("Romanian Deadlift"),
        "bss" to listOf("Bulgarian Split Squat"),
        "chest press" to listOf("Barbell Bench Press - Medium Grip", "Butterfly"),
        "squat" to listOf("Barbell Squat"),
    )

    private fun List<Exercise>.found(query: String, aliases: ExerciseAliases = emptyMap()) =
        search(query, aliases).map(Exercise::name)

    private fun exercise(
        name: String,
        primary: String,
        secondary: List<String> = emptyList(),
    ) = Exercise(
        id = name.replace(' ', '_'),
        bankName = name,
        primaryMuscles = listOf(primary),
        secondaryMuscles = secondary,
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )
}
