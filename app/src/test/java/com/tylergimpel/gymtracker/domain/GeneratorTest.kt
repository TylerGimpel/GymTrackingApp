package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val filled = fillDays(pushPullLegs, bank, equipment = allEquipment)

        filled.forEach { day ->
            day.shape.muscles.forEach { muscle ->
                assertTrue(
                    "${day.shape.name} asks for $muscle and nothing in it works that",
                    day.exercises.any { muscle in it.primaryMuscles || muscle in it.secondaryMuscles },
                )
            }
        }
    }

    @Test
    fun `nothing the lifter cannot do is ever chosen`() {
        val filled = fillDays(pushPullLegs, bank, equipment = setOf("dumbbell", "body only"))

        // Equipment filters the pool. It does not limit what the routine can achieve — machines,
        // free weights and bands are equivalent for hypertrophy — which is why a sparse gym is
        // not a worse routine, only a different one.
        assertTrue(
            filled.flatMap { it.exercises }
                .all { it.equipment == null || it.equipment in setOf("dumbbell", "body only") },
        )
    }

    @Test
    fun `a sparse gym still trains everything the days ask for`() {
        val filled = fillDays(pushPullLegs, bank, equipment = setOf("dumbbell", "body only"))

        // The same coverage check as a fully equipped gym. Training at home is not second-class.
        filled.forEach { day ->
            day.shape.muscles.forEach { muscle ->
                assertTrue(
                    "${day.shape.name} asks for $muscle and a sparse gym left it untrained",
                    day.exercises.any { muscle in it.primaryMuscles || muscle in it.secondaryMuscles },
                )
            }
        }
    }

    @Test
    fun `an exercise the lifter banned is never chosen`() {
        val banned = bank.map { if (it.id == "Squat") it.copy(isDisliked = true) else it }

        val filled = fillDays(pushPullLegs, banned, equipment = allEquipment)

        // A hard constraint, respected here as everywhere. The alternative — including it because
        // what was left scored worse — would break the only promise a dislike makes.
        assertTrue(filled.flatMap { it.exercises }.none { it.id == "Squat" })
    }

    @Test
    fun `banning something does not leave the muscle it worked untrained`() {
        val banned = bank.map { if (it.id == "Squat") it.copy(isDisliked = true) else it }

        val filled = fillDays(pushPullLegs, banned, equipment = allEquipment)

        assertTrue(
            filled.flatMap { it.exercises }.any { "quadriceps" in it.primaryMuscles },
        )
    }

    @Test
    fun `a dropped exercise is never chosen`() {
        val dropped = bank.map { if (it.id == "Squat") it.copy(isTombstoned = true) else it }

        assertTrue(
            fillDays(pushPullLegs, dropped, equipment = allEquipment)
                .flatMap { it.exercises }.none { it.id == "Squat" },
        )
    }

    @Test
    fun `an exercise the lifter added is chosen on the same terms as a shipped one`() {
        // Only their own machine works this muscle, so a generator that treated user-authored
        // rows as second-class would leave the day short and never say why.
        val filled = fillDays(neck, bank, equipment = allEquipment)

        assertTrue(filled.single().exercises.any { it.source == ExerciseSource.USER })
    }

    @Test
    fun `the same answers produce the same routine`() {
        val once = fillDays(pushPullLegs, bank, equipment = allEquipment)
        val again = fillDays(pushPullLegs, bank, equipment = allEquipment)

        // A generator nobody can reproduce is miserable to debug and worse to live with: a lifter
        // who regenerates, gets something worse and cannot get back is justifiably annoyed.
        assertEquals(
            once.map { day -> day.exercises.map(Exercise::id) },
            again.map { day -> day.exercises.map(Exercise::id) },
        )
    }

    @Test
    fun `the order the bank happens to arrive in changes nothing`() {
        val shuffled = bank.reversed()

        assertEquals(
            fillDays(pushPullLegs, bank, allEquipment).map { it.exercises.map(Exercise::id) },
            fillDays(pushPullLegs, shuffled, allEquipment).map { it.exercises.map(Exercise::id) },
        )
    }

    @Test
    fun `a day is never one exercise per muscle it names`() {
        val filled = fillDays(fullBody, bank, equipment = allEquipment)

        // A movement that works several muscles covers several of them, so a full-body day is a
        // handful of compounds rather than a list as long as the muscles it names.
        assertTrue(filled.single().exercises.size < fullBody.days.single().muscles.size)
    }

    @Test
    fun `a cardio day is left alone`() {
        val withCardio = pushPullLegs.withCardioDay()

        val filled = fillDays(withCardio, bank, equipment = allEquipment)

        // Nothing here knows what to put in one, and a resistance exercise in a cardio position
        // would put volume into a day that is supposed to carry none.
        assertTrue(filled.single { it.shape.kind == RoutineDayKind.CARDIO }.exercises.isEmpty())
    }

    @Test
    fun `a muscle nothing in the gym can train is left untrained rather than faked`() {
        val filled = fillDays(neck, bank, equipment = setOf("barbell"))

        // Honest emptiness. What to do about it is a conversation the validation has with the
        // lifter (#76), not something to paper over by putting in a bench press.
        assertFalse(filled.single().exercises.any { "neck" in it.primaryMuscles })
    }

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
