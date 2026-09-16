package gymtracker.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The build step is the only thing standing between a typo in the source data and a broken bank
 * inside a shipped APK, so its rejections are tested as behaviour: given bad data, does the build
 * stop, and does it say why.
 */
class ExerciseBankBuilderTest {

    @Test
    fun `merges upstream and authored rows`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = upstream(row(id = "Bench_Press", name = "Bench Press")),
            authoredJson = authored(row(id = "Belt_Squat", name = "Belt Squat")),
            bankPatchesJson = NO_PATCHES,
            aliasesJson = NO_ALIASES,
        )

        assertEquals(listOf("Belt Squat", "Bench Press"), bank.exercises.map { it.name })
        assertEquals(ExerciseBankBuilder.SOURCE_UPSTREAM, bank.exercises.single { it.id == "Bench_Press" }.source)
        assertEquals(ExerciseBankBuilder.SOURCE_AUTHORED, bank.exercises.single { it.id == "Belt_Squat" }.source)
    }

    @Test
    fun `rejects a muscle outside the vocabulary`() {
        val problems = problemsFrom(
            upstreamJson = upstream(row(primaryMuscles = listOf("pecs"))),
        )

        assertTrue(problems.toString(), problems.single().contains("`pecs`"))
    }

    @Test
    fun `rejects every other out-of-vocabulary value`() {
        listOf(
            "equipment" to "smith machine",
            "mechanic" to "hybrid",
            "force" to "twist",
            "category" to "mobility",
            "level" to "advanced",
        ).forEach { (field, value) ->
            val problems = problemsFrom(upstreamJson = upstream(row(extra = mapOf(field to value))))
            assertTrue(
                "expected `$field` = `$value` to be rejected, got $problems",
                problems.any { it.contains("`$field`") && it.contains("`$value`") },
            )
        }
    }

    @Test
    fun `rejects a logging type outside the vocabulary`() {
        val problems = problemsFrom(
            authoredJson = authored(row(extra = mapOf("loggingType" to "reps_only"))),
        )

        assertTrue(problems.toString(), problems.any { it.contains("`reps_only`") })
    }

    @Test
    fun `rejects an authored id colliding with upstream regardless of case`() {
        val problems = problemsFrom(
            upstreamJson = upstream(row(id = "Air_Bike", name = "Air Bike")),
            authoredJson = authored(row(id = "AIR_BIKE", name = "Fan Bike")),
        )

        assertTrue(problems.toString(), problems.single().contains("collides with upstream"))
    }

    @Test
    fun `rejects an authored name colliding with upstream regardless of case`() {
        val problems = problemsFrom(
            upstreamJson = upstream(row(id = "Air_Bike", name = "Air Bike")),
            authoredJson = authored(row(id = "Fan_Bike", name = "air bike")),
        )

        assertTrue(problems.toString(), problems.single().contains("collides with upstream"))
    }

    @Test
    fun `rejects duplicates within the authored rows`() {
        val problems = problemsFrom(
            authoredJson = authored(
                row(id = "Belt_Squat", name = "Belt Squat"),
                row(id = "belt_squat", name = "Belt Squat"),
            ),
        )

        assertEquals(problems.toString(), 2, problems.size)
        assertTrue(problems.toString(), problems.all { it.contains("duplicate") })
    }

    @Test
    fun `rejects instructions and images`() {
        val problems = problemsFrom(
            upstreamJson = upstream(row(extra = mapOf("instructions" to listOf("Lie on the bench.")))),
            authoredJson = authored(row(id = "Belt_Squat", name = "Belt Squat", extra = mapOf("images" to listOf("a.jpg")))),
        )

        assertEquals(problems.toString(), 2, problems.size)
        assertTrue(problems.toString(), problems.any { it.contains("`instructions`") })
        assertTrue(problems.toString(), problems.any { it.contains("`images`") })
    }

    @Test
    fun `rejects a cardio patch that names no upstream exercise`() {
        val problems = problemsFrom(
            bankPatchesJson = patches("""{"Ski_Erg": {"equipment": "rower"}}"""),
        )

        assertTrue(problems.toString(), problems.single().contains("`Ski_Erg`"))
    }

    @Test
    fun `rejects an alias that names no exercise`() {
        // A silently broken alias is the worst failure mode for search: the shorthand simply finds
        // nothing, and nothing anywhere says why.
        val problems = problemsFrom(
            upstreamJson = upstream(row(id = "Butterfly", name = "Butterfly")),
            aliasesJson = """{"pec deck": ["Butterfly"], "hacks": ["Hack Squat"]}""",
        )

        assertTrue(problems.toString(), problems.single().contains("Hack Squat"))
    }

    @Test
    fun `an alias may name an authored exercise`() {
        val problems = problemsFrom(
            authoredJson = authored(row(id = "Belt_Squat", name = "Belt Squat")),
            aliasesJson = """{"belt": ["Belt Squat"]}""",
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `an alias target matches an exercise name regardless of case`() {
        val problems = problemsFrom(
            upstreamJson = upstream(row(id = "Butterfly", name = "Butterfly")),
            aliasesJson = """{"pec deck": ["butterfly"]}""",
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `an alias may name several exercises`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = upstream(
                row(id = "A", name = "Barbell Curl"),
                row(id = "B", name = "Dumbbell Bicep Curl"),
            ),
            authoredJson = "[]",
            bankPatchesJson = NO_PATCHES,
            aliasesJson = """{"bicep curl": ["Barbell Curl", "Dumbbell Bicep Curl"]}""",
        )

        assertEquals(
            mapOf("bicep curl" to listOf("Barbell Curl", "Dumbbell Bicep Curl")),
            bank.aliases,
        )
    }

    @Test
    fun `reports every problem at once`() {
        val problems = problemsFrom(
            upstreamJson = upstream(
                row(id = "A", name = "A", primaryMuscles = listOf("pecs")),
                row(id = "B", name = "B", extra = mapOf("level" to "advanced")),
            ),
        )

        assertEquals(problems.toString(), 2, problems.size)
    }

    @Test
    fun `applies cardio patches to upstream rows`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = upstream(
                row(id = "Running_Treadmill", name = "Running, Treadmill", extra = mapOf("category" to "cardio", "equipment" to "machine")),
            ),
            authoredJson = "[]",
            bankPatchesJson = patches(
                """{"Running_Treadmill": {"equipment": "treadmill", "loggingType": "distance_time"}}""",
            ),
            aliasesJson = NO_ALIASES,
        )

        val treadmill = bank.exercises.single()
        assertEquals("treadmill", treadmill.equipment)
        assertEquals("distance_time", treadmill.loggingType)
    }

    @Test
    fun `authored rows keep their stated logging type rather than a derived one`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = "[]",
            authoredJson = authored(
                row(
                    id = "Assisted_Pull_Up_Machine",
                    name = "Assisted Pull-Up Machine",
                    extra = mapOf("equipment" to "machine", "loggingType" to "assisted"),
                ),
            ),
            bankPatchesJson = NO_PATCHES,
            aliasesJson = NO_ALIASES,
        )

        assertEquals("assisted", bank.exercises.single().loggingType)
    }

    @Test
    fun `derives logging type from category and equipment`() {
        val derive = ExerciseBankBuilder::deriveLoggingType

        assertEquals("time", derive("stretching", "foam roll", null))
        assertEquals("time", derive("stretching", "body only", null))
        assertEquals("bodyweight_reps", derive("strength", "body only", null))
        assertEquals("bodyweight_reps", derive("plyometrics", null, null))
        assertEquals("weight_reps", derive("strength", "barbell", null))
        assertEquals("weight_reps", derive("strongman", "other", null))
        assertEquals("distance_time", derive("cardio", "machine", null))
    }

    @Test
    fun `a held exercise is measured in time, whatever upstream files it under`() {
        val derive = ExerciseBankBuilder::deriveLoggingType

        // The plank, the side bridge and the isometric neck work are all `strength` upstream, and
        // the Crucifix is `strongman`. Nothing moves in any of them, so there is nothing to count.
        assertEquals("time", derive("strength", "body only", "static"))
        assertEquals("time", derive("strongman", "other", "static"))
        assertEquals("time", derive("plyometrics", "body only", "static"))
        // A movement with a direction is still counted in reps.
        assertEquals("bodyweight_reps", derive("strength", "body only", "push"))
    }

    @Test
    fun `a superseded row is dropped, and its name still finds what replaced it`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = """
                [
                  {"id":"Pullups","name":"Pullups","primaryMuscles":["lats"],"secondaryMuscles":[],
                   "equipment":"body only","category":"strength","level":"beginner"},
                  {"id":"Weighted_Pull_Ups","name":"Weighted Pull Ups","primaryMuscles":["lats"],
                   "secondaryMuscles":[],"equipment":"other","category":"strength","level":"beginner"}
                ]
            """.trimIndent(),
            authoredJson = "[]",
            bankPatchesJson = """{"superseded": {"Weighted_Pull_Ups": "Pullups"}}""",
            aliasesJson = """{"weighted pull up": "Weighted Pull Ups"}""",
        )

        // One movement, one row: a lifter cannot split their history by picking the other one.
        assertEquals(listOf("Pullups"), bank.exercises.map { it.id })
        // And both the dropped row's own name and the shorthand that named it find the replacement.
        assertEquals(listOf("Pullups"), bank.aliases.getValue("weighted pull ups"))
        assertEquals(listOf("Pullups"), bank.aliases.getValue("weighted pull up"))
    }

    @Test
    fun `a supersession naming a row that is not there fails the build`() {
        val problems = assertThrows(ExerciseBankException::class.java) {
            ExerciseBankBuilder.build(
                upstreamJson = """
                    [{"id":"Pullups","name":"Pullups","primaryMuscles":["lats"],
                      "secondaryMuscles":[],"equipment":"body only","category":"strength",
                      "level":"beginner"}]
                """.trimIndent(),
                authoredJson = "[]",
                bankPatchesJson = """{"superseded": {"Weighted_Pull_Ups": "Pullups"}}""",
                aliasesJson = "{}",
            )
        }

        // Dropping an exercise silently is the failure this check exists to prevent.
        assertTrue(problems.message.orEmpty(), problems.message.orEmpty().contains("Weighted_Pull_Ups"))
    }

    @Test
    fun `an exercise with a load advances by double progression, and one without is never prescribed`() {
        val derive = ExerciseBankBuilder::deriveProgressionModel

        // ADR 0004 settles what the engine does for anything with a load.
        assertEquals("double_progression", derive("weight_reps"))
        assertEquals("double_progression", derive("bodyweight_reps"))
        assertEquals("double_progression", derive("assisted"))
        // A hold and a run are recorded and never prescribed: no load for a recommendation to move.
        assertEquals("none", derive("time"))
        assertEquals("none", derive("distance_time"))
    }

    @Test
    fun `every logging type in the vocabulary derives a model in the vocabulary`() {
        Vocabulary.LOGGING_TYPE.forEach { loggingType ->
            assertTrue(
                loggingType,
                ExerciseBankBuilder.deriveProgressionModel(loggingType) in Vocabulary.PROGRESSION_MODEL,
            )
        }
    }

    @Test
    fun `derives unilateral from explicit one-side wording only`() {
        val derive = ExerciseBankBuilder::deriveUnilateral

        assertTrue(derive("One-Arm Dumbbell Row"))
        assertTrue(derive("Single-Leg Leg Press"))
        assertTrue(derive("Kettlebell One-Legged Deadlift"))
        assertFalse(derive("Alternate Heel Touchers"))
        assertFalse(derive("Bulgarian Split Squat"))
        assertFalse(derive("Barbell Bench Press"))
    }

    @Test
    fun `an authored row may override a derived unilateral flag`() {
        val bank = ExerciseBankBuilder.build(
            upstreamJson = "[]",
            authoredJson = authored(
                row(id = "Bulgarian_Split_Squat", name = "Bulgarian Split Squat", extra = mapOf("unilateral" to true)),
            ),
            bankPatchesJson = NO_PATCHES,
            aliasesJson = NO_ALIASES,
        )

        assertTrue(bank.exercises.single().unilateral)
    }

    // --- fixtures ----------------------------------------------------------------------------

    private fun problemsFrom(
        upstreamJson: String = "[]",
        authoredJson: String = "[]",
        bankPatchesJson: String = NO_PATCHES,
        aliasesJson: String = NO_ALIASES,
    ): List<String> = try {
        ExerciseBankBuilder.build(upstreamJson, authoredJson, bankPatchesJson, aliasesJson)
        emptyList()
    } catch (e: ExerciseBankException) {
        e.problems
    }

    private fun upstream(vararg rows: String) = rows.joinToString(", ", "[", "]")

    private fun authored(vararg rows: String) = rows.joinToString(", ", "[", "]")

    private fun patches(body: String) = """{"patches": $body}"""

    private fun row(
        id: String = "Bench_Press",
        name: String = "Bench Press",
        primaryMuscles: List<String> = listOf("chest"),
        extra: Map<String, Any> = emptyMap(),
    ): String {
        val fields = linkedMapOf<String, Any>(
            "id" to id,
            "name" to name,
            "primaryMuscles" to primaryMuscles,
            "secondaryMuscles" to listOf("triceps"),
            "equipment" to "barbell",
            "mechanic" to "compound",
            "force" to "push",
            "category" to "strength",
            "level" to "beginner",
            "loggingType" to "weight_reps",
        )
        fields.putAll(extra)
        return fields.entries.joinToString(", ", "{", "}") { (key, value) ->
            "\"$key\": ${value.toJson()}"
        }
    }

    private fun Any.toJson(): String = when (this) {
        is String -> "\"$this\""
        is Boolean -> toString()
        is List<*> -> joinToString(", ", "[", "]") { it!!.toJson() }
        else -> error("Unsupported fixture value: $this")
    }

    private companion object {
        const val NO_PATCHES = """{"patches": {}}"""
        const val NO_ALIASES = "{}"
    }
}
