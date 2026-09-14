package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Volume is sets per muscle, counted directly and indirectly.
 *
 * Two conventions travel together and must not be mixed (ADR 0005): a set counts 1.0 toward the
 * muscle it primarily targets and 0.5 toward each secondary. Counting secondaries at full weight
 * would let a chest day satisfy a triceps target it never earned.
 */
class VolumeTest {

    private val bench = exercise(
        id = "Barbell_Bench_Press",
        primary = listOf("chest"),
        secondary = listOf("shoulders", "triceps"),
    )

    @Test
    fun `a working set counts in full toward the muscle it targets`() {
        val logged = performed(bench, working(1))

        assertEquals(mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5), volumeByMuscle(logged))
    }

    @Test
    fun `back-off and amrap sets are work and count like any other`() {
        assertEquals(
            mapOf("chest" to 3.0, "shoulders" to 1.5, "triceps" to 1.5),
            volumeByMuscle(
                performed(
                    bench,
                    set(1, SetType.WORKING),
                    set(2, SetType.BACKOFF),
                    set(3, SetType.AMRAP),
                ),
            ),
        )
    }

    @Test
    fun `a warm-up earns nothing, because it is not the work`() {
        assertEquals(emptyMap<String, Double>(), volumeByMuscle(performed(bench, set(1, SetType.WARMUP))))
    }

    @Test
    fun `a calibration set earns nothing, because it is finding out rather than training`() {
        assertEquals(
            emptyMap<String, Double>(),
            volumeByMuscle(performed(bench, set(1, SetType.CALIBRATION))),
        )
    }

    @Test
    fun `warm-ups alongside work leave only the work counted`() {
        val logged = performed(
            bench,
            set(1, SetType.WARMUP),
            set(2, SetType.WARMUP),
            set(3, SetType.WORKING),
            set(4, SetType.WORKING),
        )

        assertEquals(mapOf("chest" to 2.0, "shoulders" to 1.0, "triceps" to 1.0), volumeByMuscle(logged))
    }

    @Test
    fun `a set recorded by two rows is still one set`() {
        // Two rows sharing one set index — what a per-side set will look like. Counting rows here
        // instead of positions is what would report a drop set as three (ADR 0003).
        val logged = performed(
            bench,
            set(1, SetType.WORKING, id = 10),
            set(1, SetType.WORKING, id = 11),
        )

        assertEquals(mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5), volumeByMuscle(logged))
    }

    @Test
    fun `volume from different exercises adds up per muscle`() {
        val flye = exercise("Dumbbell_Flye", primary = listOf("chest"), secondary = listOf("shoulders"))
        val logged = performed(bench, working(1), working(2)) +
            listOf(SessionExercise(id = 2, exercise = flye, orderIndex = 2, sets = listOf(working(1))))

        assertEquals(
            // chest: 2 direct from bench + 1 direct from the flye.
            // shoulders: 2 indirect from bench + 1 indirect from the flye.
            mapOf("chest" to 3.0, "shoulders" to 1.5, "triceps" to 1.0),
            volumeByMuscle(logged),
        )
    }

    @Test
    fun `an exercise with no counted sets contributes no muscles at all`() {
        val logged = performed(bench, set(1, SetType.WARMUP)) +
            listOf(SessionExercise(id = 2, exercise = bench, orderIndex = 2, sets = emptyList()))

        assertEquals(emptyMap<String, Double>(), volumeByMuscle(logged))
    }

    @Test
    fun `a type is never inferred from what the set weighs or where it sits`() {
        // The heaviest set of the day, logged last, explicitly a warm-up. Nothing about position or
        // load may override what the lifter said it was.
        val logged = performed(
            bench,
            set(1, SetType.WORKING).copy(weight = Kilograms(60.0)),
            set(2, SetType.WARMUP).copy(weight = Kilograms(100.0)),
        )

        assertEquals(mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5), volumeByMuscle(logged))
    }

    private fun exercise(id: String, primary: List<String>, secondary: List<String>) = Exercise(
        id = id,
        name = id.replace('_', ' '),
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )

    private fun performed(exercise: Exercise, vararg sets: LoggedSet) = listOf(
        SessionExercise(id = 1, exercise = exercise, orderIndex = 1, sets = sets.toList()),
    )

    private fun working(setIndex: Int) = set(setIndex, SetType.WORKING)

    private fun set(setIndex: Int, type: SetType, id: Long = setIndex.toLong()) = LoggedSet(
        id = id,
        setIndex = setIndex,
        type = type,
        weight = Kilograms(60.0),
        reps = 5,
        note = null,
    )
}
