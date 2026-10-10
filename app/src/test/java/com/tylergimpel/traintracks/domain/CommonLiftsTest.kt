package com.tylergimpel.traintracks.domain

import com.tylergimpel.traintracks.domain.RoutineGoal.HYPERTROPHY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Day recipes and the ranked common lifts (ADR 0013, #150).
 *
 * The data is hand-authored, so the first tests hold it to the rules nothing else would catch: a
 * ranked id that is not in the bank fills nothing and says nothing, and a recipe that forgets a
 * muscle its day names breaks coverage for every routine built from it.
 */
class CommonLiftsTest {

    @Test
    fun `every ranked lift is a row the bank ships`() {
        val shipped = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(File("../data/upstream-exercises.json").readText() + File("../data/authored-exercises.json").readText())
            .map { it.groupValues[1] }
            .toSet()

        val missing = SLOTS.flatMap { slot -> (slot.ranked + slot.secondTier).filterNot { it in shipped }.map { "${slot.pattern}: $it" } }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every muscle a day names has an always-filled slot of its own`() {
        SPLIT_SHAPES.flatMap { it.days }.filter { it.kind == RoutineDayKind.LIFTING }.forEach { day ->
            assertTrue("${day.name} has no recipe", day.recipe.isNotEmpty())
            day.muscles.forEach { muscle ->
                assertTrue(
                    "${day.name} names $muscle but no always-filled slot trains it",
                    day.recipe.any { it.always && it.slot.muscle == muscle },
                )
            }
        }
    }

    @Test
    fun `no slot trains a muscle its day is not for`() {
        // Volume for a muscle the day does not name is volume no budget asked for (ADR 0005).
        SPLIT_SHAPES.flatMap { it.days }.forEach { day ->
            day.recipe.forEach { assertTrue("${day.name}: ${it.slot.pattern}", it.slot.muscle in day.muscles) }
        }
    }

    @Test
    fun `a slot takes the best-ranked lift the gym has`() {
        val push = fillDays(pushPullLegs, bank, FULL_GYM, HYPERTROPHY, cadence = 3).first()
        val atHome = fillDays(pushPullLegs, bank, setOf("dumbbell", "body only"), HYPERTROPHY, cadence = 3).first()

        assertEquals("Barbell_Bench_Press_-_Medium_Grip", push.chosen.first().id)
        assertEquals("Dumbbell_Bench_Press", atHome.chosen.first().id)
    }

    @Test
    fun `with no common lift a slot falls back to an ordinary one, beginner first`() {
        val chestOnly = listOf(
            exercise("Around_The_Worlds", "chest", name = "Around The Worlds", equipment = "dumbbell"),
            exercise("Plyo_Push-up", "chest", category = "plyometrics", equipment = "body only"),
            exercise("Expert_Press", "chest", level = "expert"),
            exercise("Intermediate_Press", "chest", level = "intermediate"),
            exercise("Beginner_Press", "chest"),
        )
        val chestDay = SplitShape("chest", "Chest", listOf(SplitDay("Chest", listOf("chest"), recipe = listOf(RecipeSlot(BENCH, always = true)))), rotation = 1)

        val filled = fillDays(chestDay, chestOnly, emptySet(), HYPERTROPHY, cadence = 3).single()

        assertEquals(listOf("Beginner_Press"), filled.chosen.map(Exercise::id))
    }

    @Test
    fun `nothing never-generated is ever generated`() {
        val filled = fillDays(pushPullLegs, bank + oddities, FULL_GYM, HYPERTROPHY, cadence = 3, seed = 3)

        assertTrue(filled.flatMap { it.chosen }.none { it.isNeverGenerated })
    }

    @Test
    fun `a day running long drops its optional slots last-first, and never an always one`() {
        val unhurried = fillDays(pushPullLegs, bank, FULL_GYM, HYPERTROPHY, cadence = 3, sessionMinutes = 600).first()
        val hurried = fillDays(pushPullLegs, bank, FULL_GYM, HYPERTROPHY, cadence = 3, sessionMinutes = 20).first()

        // Push's always-filled slots: a flat press, an overhead press and triceps.
        assertEquals(
            listOf("Barbell_Bench_Press_-_Medium_Grip", "Standing_Military_Press", "Triceps_Pushdown"),
            hurried.chosen.map(Exercise::id),
        )
        assertTrue(unhurried.chosen.size > hurried.chosen.size)
        // What survives is a prefix of the recipe's order, less the dropped slots.
        assertEquals(unhurried.chosen.filter { it in hurried.chosen }, hurried.chosen)
    }

    @Test
    fun `a day trimmed for time fits its session, or holds nothing but what is always there`() {
        val filled = fillDays(upperLower, bank, FULL_GYM, HYPERTROPHY, cadence = 3, sessionMinutes = 75)
        val leanest = fillDays(upperLower, bank, FULL_GYM, HYPERTROPHY, cadence = 3, sessionMinutes = 1)
        val routine = Routine(
            id = 1, name = "UL", goal = HYPERTROPHY, cadence = 3, equipment = FULL_GYM,
            repPreference = RepPreference.MODERATE, shape = upperLower, sessionMinutes = 75,
            days = filled.mapIndexed { index, day -> RoutineDay(index + 1L, index + 1, day.shape.name, exercises = day.exercises) },
        )

        // Still long is what the Muscles page says Runs long about: only the always-filled slots
        // are left, and those never drop.
        bindingOn(routine).filterIsInstance<Binding.SessionTooLong>().forEach { long ->
            val index = filled.indexOfFirst { it.shape.name == long.day }
            assertEquals(long.day, leanest[index].chosen, filled[index].chosen)
        }
    }

    @Test
    fun `the second tier is only for an advanced lifter who asks for something else`() {
        val tiers = SLOTS.flatMap { it.secondTier }.toSet()
        fun generated(experience: Experience, seed: Int) = (0..seed).flatMap { s ->
            fillDays(pushPullLegs, bank, FULL_GYM, HYPERTROPHY, cadence = 3, seed = s, experience = experience)
                .flatMap { it.chosen.map(Exercise::id) }
        }.toSet()

        // Nobody's first routine, however experienced.
        assertTrue(generated(Experience.ADVANCED, seed = 0).none { it in tiers })
        // New and Some never see it, however often they ask.
        assertTrue(generated(Experience.TRAINED, seed = 12).none { it in tiers })
        assertTrue(generated(Experience.NOVICE, seed = 12).none { it in tiers })
        // Lots does, once they ask.
        assertTrue(generated(Experience.ADVANCED, seed = 12).any { it in tiers })
    }

    @Test
    fun `build it again on one lift gives the next lift for its slot`() {
        val bench = bank.single { it.id == "Barbell_Bench_Press_-_Medium_Grip" }

        val next = nextFor(bench, inDay = setOf(bench.id), bank, FULL_GYM, Experience.TRAINED)
        // Skipping what the day already holds.
        val skipping = nextFor(bench, inDay = setOf(bench.id, "Dumbbell_Bench_Press"), bank, FULL_GYM, Experience.TRAINED)

        assertEquals("Dumbbell_Bench_Press", next?.id)
        assertEquals("Machine_Bench_Press", skipping?.id)
    }

    @Test
    fun `build it again on one lift goes through every lift for its slot and comes round again`() {
        val bench = bank.single { it.id == "Barbell_Bench_Press_-_Medium_Grip" }
        val chest = bank.filter { "chest" in it.primaryMuscles && it.id !in SLOTS.flatMap(Slot::secondTier) }

        val seen = generateSequence(bench) { nextFor(it, setOf(it.id), bank, FULL_GYM, Experience.TRAINED) }
            .take(chest.size + 1)
            .toList()

        // Asking keeps giving something, and every lift for the muscle a lifter of theirs could be
        // offered comes up once a round: not the second tier's incline barbell bench.
        assertEquals(chest.map(Exercise::id).toSet(), seen.dropLast(1).map(Exercise::id).toSet())
        assertEquals(bench, seen.last())
    }

    @Test
    fun `the picker's same muscle list is the ranking, with nothing never-generated in it`() {
        val chest = (bank + oddities).rankedFor("chest", Experience.TRAINED).map(Exercise::id)

        assertEquals("Barbell_Bench_Press_-_Medium_Grip", chest.first())
        assertFalse(chest.contains("Around_The_Worlds"))
        assertTrue(chest.indexOf("Dumbbell_Bench_Press") < chest.indexOf("Butterfly"))
    }

    @Test
    fun `the picker's whole list puts common lifts first`() {
        val listed = (oddities + bank.reversed()).commonFirst(Experience.TRAINED).map(Exercise::id)

        assertEquals("Barbell_Squat", listed.first())
        // Every common lift comes before anything that is not one.
        val ranked = SLOTS.flatMap { it.ranked }.toSet()
        assertTrue(listed.toString(), listed.dropWhile { it in ranked }.none { it in ranked })
    }

    @Test
    fun `a kept lift stays in the slot it is ranked in`() {
        val fly = bank.single { it.id == "Butterfly" }
        val push = fillDays(
            pushPullLegs, bank, FULL_GYM, HYPERTROPHY, cadence = 3, sessionMinutes = 600,
            pinned = mapOf("Push" to listOf(PlannedExercise(fly, 3, pinned = true))),
        ).first()

        // A pec deck kept is still a fly, after the presses, never the day's first lift.
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", push.chosen.first().id)
        assertTrue(push.chosen.indexOf(fly) > push.chosen.indexOfFirst { it.id == "Standing_Military_Press" })
    }

    @Test
    fun `full body days that differ hold different lifts`() {
        val filled = fillDays(SPLIT_SHAPES.single { it.id == "full-body-3" }, bank, FULL_GYM, HYPERTROPHY, cadence = 3)

        assertNotEquals(filled[0].chosen, filled[2].chosen)
    }

    @Test
    fun `a front squat's clean grip is a grip, not a clean`() {
        assertFalse(exercise("Front_Squat_Clean_Grip", "quadriceps", name = "Front Squat (Clean Grip)").isNeverGenerated)
        assertTrue(exercise("Power_Clean", "hamstrings", name = "Power Clean").isNeverGenerated)
    }

    private val FilledDay.chosen: List<Exercise> get() = exercises.map(PlannedExercise::exercise)

    private companion object {
        val FULL_GYM = emptySet<String>()

        fun exercise(
            id: String,
            muscle: String,
            secondary: List<String> = emptyList(),
            name: String = id.replace('_', ' '),
            equipment: String? = "barbell",
            mechanic: String = "compound",
            category: String = "strength",
            level: String = "beginner",
        ) = Exercise(
            id = id,
            bankName = name,
            primaryMuscles = listOf(muscle),
            secondaryMuscles = secondary,
            equipment = equipment,
            mechanic = mechanic,
            force = "push",
            category = category,
            level = level,
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        /** A small bank of real rows: the ids and equipment the shipped bank gives them. */
        val bank = listOf(
            exercise("Barbell_Bench_Press_-_Medium_Grip", "chest", listOf("triceps", "shoulders")),
            exercise("Dumbbell_Bench_Press", "chest", listOf("triceps"), equipment = "dumbbell"),
            exercise("Machine_Bench_Press", "chest", equipment = "machine"),
            exercise("Pushups", "chest", listOf("triceps"), equipment = "body only"),
            exercise("Incline_Dumbbell_Press", "chest", equipment = "dumbbell"),
            exercise("Barbell_Incline_Bench_Press_-_Medium_Grip", "chest"),
            exercise("Butterfly", "chest", equipment = "machine", mechanic = "isolation"),
            exercise("Standing_Military_Press", "shoulders", listOf("triceps")),
            exercise("Dumbbell_Shoulder_Press", "shoulders", equipment = "dumbbell", level = "intermediate"),
            exercise("Side_Lateral_Raise", "shoulders", equipment = "dumbbell", mechanic = "isolation"),
            exercise("Face_Pull", "shoulders", equipment = "cable"),
            exercise("Triceps_Pushdown", "triceps", equipment = "cable", mechanic = "isolation"),
            exercise("Cable_Rope_Overhead_Triceps_Extension", "triceps", equipment = "cable", mechanic = "isolation"),
            exercise("Bench_Dips", "triceps", equipment = "body only"),
            exercise("Close-Grip_Barbell_Bench_Press", "triceps"),
            exercise("Wide-Grip_Lat_Pulldown", "lats", listOf("biceps"), equipment = "cable"),
            exercise("Pullups", "lats", listOf("biceps"), equipment = "body only"),
            exercise("Underhand_Cable_Pulldowns", "lats", equipment = "cable"),
            exercise("Bent_Over_Barbell_Row", "middle back", listOf("lats", "biceps")),
            exercise("Seated_Cable_Rows", "middle back", equipment = "cable"),
            exercise("One-Arm_Dumbbell_Row", "middle back", equipment = "dumbbell"),
            exercise("Pendlay_Row", "middle back", level = "intermediate"),
            exercise("T-Bar_Row_with_Handle", "middle back"),
            exercise("Barbell_Shrug", "traps", mechanic = "isolation"),
            exercise("Dumbbell_Shrug", "traps", equipment = "dumbbell", mechanic = "isolation"),
            exercise("Dumbbell_Bicep_Curl", "biceps", equipment = "dumbbell", mechanic = "isolation"),
            exercise("Barbell_Curl", "biceps", mechanic = "isolation"),
            exercise("Hammer_Curls", "biceps", equipment = "dumbbell", mechanic = "isolation"),
            exercise("Barbell_Squat", "quadriceps", listOf("glutes")),
            exercise("Leg_Press", "quadriceps", equipment = "machine"),
            exercise("Goblet_Squat", "quadriceps", equipment = "kettlebells"),
            exercise("Split_Squat_with_Dumbbells", "quadriceps", equipment = "dumbbell"),
            exercise("Front_Squat_Clean_Grip", "quadriceps", name = "Front Squat (Clean Grip)", level = "intermediate"),
            exercise("Hack_Squat", "quadriceps", equipment = "machine"),
            exercise("Leg_Extensions", "quadriceps", equipment = "machine", mechanic = "isolation"),
            exercise("Romanian_Deadlift", "hamstrings", listOf("glutes"), level = "intermediate"),
            exercise("Stiff-Legged_Dumbbell_Deadlift", "hamstrings", equipment = "dumbbell"),
            exercise("Lying_Leg_Curls", "hamstrings", equipment = "machine", mechanic = "isolation"),
            exercise("Barbell_Hip_Thrust", "glutes", level = "intermediate"),
            exercise("Hyperextensions_Back_Extensions", "lower back", equipment = "other", mechanic = "isolation"),
            exercise("Barbell_Deadlift", "lower back", listOf("hamstrings"), level = "intermediate"),
            exercise("Standing_Calf_Raises", "calves", equipment = "machine", mechanic = "isolation"),
            exercise("Crunches", "abdominals", equipment = "body only", mechanic = "isolation"),
        )

        /** Rows the research says no program uses: they may be in the bank and are never generated. */
        val oddities = listOf(
            exercise("Around_The_Worlds", "chest", name = "Around The Worlds", equipment = "dumbbell"),
            exercise("Power_Clean", "hamstrings", name = "Power Clean"),
            exercise("Box_Jump", "quadriceps", category = "plyometrics", equipment = "body only"),
            exercise("Upright_Cable_Row", "traps", name = "Upright Cable Row", equipment = "cable"),
        )

        val pushPullLegs = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val upperLower = SPLIT_SHAPES.single { it.id == "upper-lower-4" }
    }
}
