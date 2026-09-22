package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import com.tylergimpel.gymtracker.domain.RoutineGoal.HYPERTROPHY
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filling the days, which is the half of generation that cannot be templated.
 *
 * **Every test here asserts a property of the routine that comes back, never which exercises were
 * chosen.** Pinning the choices would tie these to today's bank and today's tie-breaking, and they
 * would fail every time either improved while proving nothing about whether the routine is sound.
 */
class GeneratorTest {

    @Test
    fun `every muscle a day asks for is trained in it`() {
        val filled = fillDays(pushPullLegs, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        filled.forEach { day ->
            day.shape.muscles.forEach { muscle ->
                // Directly. Letting secondary work answer for a muscle collapses an upper day
                // into three exercises and leaves half the muscles it exists for with no
                // exercise of their own — and an assertion that accepted it would be a copy of
                // the rule rather than a check on it.
                assertTrue(
                    "${day.shape.name} asks for $muscle and nothing in it trains that directly",
                    day.chosen.any { muscle in it.primaryMuscles },
                )
            }
        }
    }

    @Test
    fun `nothing the lifter cannot do is ever chosen`() {
        val filled = fillDays(
            pushPullLegs,
            bank,
            equipment = setOf("dumbbell", "body only"),
            goal = HYPERTROPHY,
            weeklyFrequency = 3,
        )

        // Equipment filters the pool. It does not limit what the routine can achieve — machines,
        // free weights and bands are equivalent for hypertrophy — which is why a sparse gym is
        // not a worse routine, only a different one.
        assertTrue(
            filled.flatMap { it.chosen }
                .all { it.equipment == null || it.equipment in setOf("dumbbell", "body only") },
        )
    }

    @Test
    fun `a sparse gym still trains everything the days ask for`() {
        val filled = fillDays(
            pushPullLegs,
            bank,
            equipment = setOf("dumbbell", "body only"),
            goal = HYPERTROPHY,
            weeklyFrequency = 3,
        )

        // The same coverage check as a fully equipped gym. Training at home is not second-class.
        filled.forEach { day ->
            day.shape.muscles.forEach { muscle ->
                assertTrue(
                    "${day.shape.name} asks for $muscle and a sparse gym left it untrained",
                    day.chosen.any { muscle in it.primaryMuscles },
                )
            }
        }
    }

    @Test
    fun `an exercise the lifter banned is never chosen`() {
        val banned = bank.map { if (it.id == "Squat") it.copy(isDisliked = true) else it }

        val filled = fillDays(pushPullLegs, banned, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        // A hard constraint, respected here as everywhere. The alternative — including it because
        // what was left scored worse — would break the only promise a dislike makes.
        assertTrue(filled.flatMap { it.chosen }.none { it.id == "Squat" })
    }

    @Test
    fun `banning something does not leave the muscle it worked untrained`() {
        val banned = bank.map { if (it.id == "Squat") it.copy(isDisliked = true) else it }

        val filled = fillDays(pushPullLegs, banned, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        assertTrue(
            filled.flatMap { it.chosen }.any { "quadriceps" in it.primaryMuscles },
        )
    }

    @Test
    fun `a dropped exercise is never chosen`() {
        val dropped = bank.map { if (it.id == "Squat") it.copy(isTombstoned = true) else it }

        assertTrue(
            fillDays(pushPullLegs, dropped, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)
                .flatMap { it.chosen }.none { it.id == "Squat" },
        )
    }

    @Test
    fun `an exercise the lifter added is chosen on the same terms as a shipped one`() {
        // Only their own machine works this muscle, so a generator that treated user-authored
        // rows as second-class would leave the day short and never say why.
        val filled = fillDays(neck, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        assertTrue(filled.single().chosen.any { it.source == ExerciseSource.USER })
    }

    @Test
    fun `the same answers produce the same routine`() {
        val once = fillDays(pushPullLegs, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)
        val again = fillDays(pushPullLegs, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        // A generator nobody can reproduce is miserable to debug and worse to live with: a lifter
        // who regenerates, gets something worse and cannot get back is justifiably annoyed.
        assertEquals(
            once.map { day -> day.chosen.map(Exercise::id) },
            again.map { day -> day.chosen.map(Exercise::id) },
        )
    }

    @Test
    fun `asking for variety gives something else, and asking again gives the same something else`() {
        val first = fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, 3, seed = 0)
        val second = fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, 3, seed = 1)
        val secondAgain = fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, 3, seed = 1)

        // Different, because they asked. Reproducible, because a lifter who regenerates, gets
        // something worse and wants the old one back has to be able to get there.
        assertNotEquals(
            first.map { it.chosen.map(Exercise::id) },
            second.map { it.chosen.map(Exercise::id) },
        )
        assertEquals(
            second.map { it.chosen.map(Exercise::id) },
            secondAgain.map { it.chosen.map(Exercise::id) },
        )
    }

    @Test
    fun `variety still trains everything the day asks for`() {
        (0..4).forEach { seed ->
            val filled = fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, 3, seed = seed)
            filled.forEach { day ->
                day.shape.muscles.forEach { muscle ->
                    assertTrue(
                        "seed $seed left $muscle untrained on ${day.shape.name}",
                        day.chosen.any { muscle in it.primaryMuscles },
                    )
                }
            }
        }
    }

    @Test
    fun `a pinned exercise survives regeneration and is not chosen for twice`() {
        val kept = bank.single { it.id == "Bench" }
        val pinned = mapOf("Push" to listOf(PlannedExercise(kept, 4)))

        val filled = fillDays(
            pushPullLegs,
            bank,
            allEquipment,
            HYPERTROPHY,
            weeklyFrequency = 3,
            pinned = pinned,
            seed = 3,
        )

        val push = filled.single { it.shape.name == "Push" }
        assertTrue(push.chosen.any { it.id == "Bench" })
        // And it answered for chest, so the generator did not put a second press beside it.
        assertEquals(1, push.chosen.count { "chest" in it.primaryMuscles })
    }

    @Test
    fun `the order the bank happens to arrive in changes nothing`() {
        val shuffled = bank.reversed()

        assertEquals(
            fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, weeklyFrequency = 3).map { it.chosen.map(Exercise::id) },
            fillDays(pushPullLegs, shuffled, allEquipment, HYPERTROPHY, weeklyFrequency = 3).map { it.chosen.map(Exercise::id) },
        )
    }

    @Test
    fun `a day is as long as its budget, not as long as its anatomy`() {
        val filled = fillDays(
            fullBody,
            bank,
            equipment = allEquipment,
            goal = HYPERTROPHY,
            weeklyFrequency = 3,
        )

        // At least one movement for every muscle the day is for, and more where the budget says
        // that muscle's sets should not all sit on one exercise. What keeps it a session rather
        // than an afternoon is the shape naming what the day is *for* rather than an anatomy
        // list, and the budget shrinking as a muscle is trained more often.
        val day = filled.single()
        assertTrue(day.chosen.size >= fullBody.days.single().muscles.size)
        assertTrue("a full-body day came back as ${day.chosen.size} exercises", day.chosen.size <= 12)
    }

    @Test
    fun `a muscle trained more often gets fewer sets each time`() {
        val once = fillDays(fullBody, bank, allEquipment, HYPERTROPHY, weeklyFrequency = 1)
        val thrice = fillDays(fullBody, bank, allEquipment, HYPERTROPHY, weeklyFrequency = 3)

        // The whole point of the budget being weekly: the same target spread over three sessions
        // is a third of the work in each. Frequency is distribution, never extra volume.
        assertTrue(
            once.single().exercises.sumOf { it.sets ?: 0 } >
                thrice.single().exercises.sumOf { it.sets ?: 0 },
        )
    }

    @Test
    fun `every exercise comes back with sets to do`() {
        val filled = fillDays(pushPullLegs, bank, allEquipment, HYPERTROPHY, weeklyFrequency = 3)

        // A prescription of nought sets is not a prescription.
        assertTrue(filled.flatMap { it.exercises }.all { (it.sets ?: 0) >= 1 })
    }

    @Test
    fun `training for strength trains the compounds first`() {
        val filled = fillDays(pushPullLegs, bank, allEquipment, RoutineGoal.STRENGTH, 3)

        filled.filter { it.chosen.isNotEmpty() }.forEach { day ->
            val compounds = day.chosen.map { it.isCompound }
            assertEquals(
                "${day.shape.name} trained an isolation movement before a compound",
                compounds.sortedByDescending { it },
                compounds,
            )
        }
    }

    @Test
    fun `an exercise is never picked twice for one day`() {
        val filled = fillDays(fullBody, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        val chosen = filled.single().chosen.map(Exercise::id)
        assertEquals(chosen.distinct(), chosen)
    }

    @Test
    fun `sessions a shape promises differ actually differ`() {
        val twoDifferent = SPLIT_SHAPES.single { it.id == "full-body-2" }

        val filled = fillDays(twoDifferent, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        // Selection is deterministic, so two days naming the same muscles come back holding the
        // same exercises. A shape offering "two sessions that differ" has to differ in what its
        // days are for, or it is a lie told twice.
        assertNotEquals(
            filled[0].chosen.map(Exercise::id),
            filled[1].chosen.map(Exercise::id),
        )
    }

    @Test
    fun `a cardio day is left alone`() {
        val withCardio = pushPullLegs.withCardioDay()

        val filled = fillDays(withCardio, bank, equipment = allEquipment, goal = HYPERTROPHY, weeklyFrequency = 3)

        // Nothing here knows what to put in one, and a resistance exercise in a cardio position
        // would put volume into a day that is supposed to carry none.
        assertTrue(filled.single { it.shape.kind == RoutineDayKind.CARDIO }.chosen.isEmpty())
    }

    @Test
    fun `a muscle nothing in the gym can train is left untrained rather than faked`() {
        val filled = fillDays(neck, bank, setOf("barbell"), HYPERTROPHY, weeklyFrequency = 3)

        // Honest emptiness. What to do about it is a conversation the validation has with the
        // lifter (#76), not something to paper over by putting in a bench press.
        assertFalse(filled.single().chosen.any { "neck" in it.primaryMuscles })
    }

    /** What a day holds, without its set counts, which is what most of these are about. */
    private val FilledDay.chosen: List<Exercise> get() = exercises.map(PlannedExercise::exercise)

    private companion object {
        val allEquipment = setOf("barbell", "dumbbell", "machine", "body only")

        fun exercise(
            id: String,
            primary: List<String>,
            secondary: List<String> = emptyList(),
            equipment: String? = "barbell",
            mechanic: String = "compound",
            source: ExerciseSource = ExerciseSource.UPSTREAM,
        ) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            equipment = equipment,
            mechanic = mechanic,
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
            source = source,
        )

        val bank = listOf(
            exercise("Bench", listOf("chest"), listOf("triceps", "shoulders")),
            exercise("DbPress", listOf("chest"), listOf("triceps"), equipment = "dumbbell"),
            exercise("PushUp", listOf("chest"), listOf("triceps"), equipment = "body only"),
            exercise("Ohp", listOf("shoulders"), listOf("triceps")),
            exercise(
                "DbOhp",
                listOf("shoulders"),
                listOf("triceps"),
                equipment = "dumbbell",
            ),
            exercise("Dip", listOf("triceps"), listOf("chest"), equipment = "body only"),
            exercise("Row", listOf("middle back"), listOf("biceps", "lats")),
            exercise(
                "DbRow",
                listOf("middle back"),
                listOf("biceps", "lats"),
                equipment = "dumbbell",
            ),
            exercise("PullUp", listOf("lats"), listOf("biceps"), equipment = "body only"),
            exercise("Curl", listOf("biceps"), equipment = "dumbbell", mechanic = "isolation"),
            exercise("Shrug", listOf("traps"), equipment = "dumbbell", mechanic = "isolation"),
            exercise("Squat", listOf("quadriceps"), listOf("glutes", "lower back")),
            exercise("GobletSquat", listOf("quadriceps"), listOf("glutes"), equipment = "dumbbell"),
            exercise("Rdl", listOf("hamstrings"), listOf("glutes", "lower back")),
            exercise("HipThrust", listOf("glutes"), listOf("hamstrings")),
            exercise(
                "GluteBridge",
                listOf("glutes"),
                listOf("hamstrings"),
                equipment = "body only",
            ),
            exercise("DbRdl", listOf("hamstrings"), listOf("glutes"), equipment = "dumbbell"),
            exercise("Calf", listOf("calves"), equipment = "body only", mechanic = "isolation"),
            exercise("BackExt", listOf("lower back"), equipment = "body only"),
            exercise("Plank", listOf("abdominals"), equipment = "body only"),
            exercise("Wrist", listOf("forearms"), equipment = "dumbbell", mechanic = "isolation"),
            exercise("Abduct", listOf("abductors"), equipment = "machine", mechanic = "isolation"),
            exercise("Adduct", listOf("adductors"), equipment = "machine", mechanic = "isolation"),
            exercise(
                "MyNeckMachine",
                listOf("neck"),
                equipment = "machine",
                mechanic = "isolation",
                source = ExerciseSource.USER,
            ),
        )

        val pushPullLegs = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val fullBody = SPLIT_SHAPES.single { it.id == "full-body-1" }
        val neck = SplitShape(
            id = "neck",
            description = "Neck",
            rotation = 1,
            days = listOf(SplitDay("Neck", listOf("neck"))),
        )
    }
}
