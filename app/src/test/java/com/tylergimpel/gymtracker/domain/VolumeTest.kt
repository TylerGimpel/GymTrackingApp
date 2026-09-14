package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            set(1, SetType.WORKING).copy(efforts = listOf(effort(weight = 60.0))),
            set(2, SetType.WARMUP).copy(efforts = listOf(effort(weight = 100.0))),
        )

        assertEquals(mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5), volumeByMuscle(logged))
    }

    @Test
    fun `a muscle named as both primary and secondary is counted once, in full`() {
        // Nine rows in the shipped bank do this. Barbell Step Ups names quadriceps in both lists;
        // stacking direct and indirect credit would report 1.5 sets for one set of work.
        val stepUps = exercise(
            id = "Barbell_Step_Ups",
            primary = listOf("quadriceps"),
            secondary = listOf("quadriceps", "glutes", "hamstrings"),
        )

        val volume = volumeByMuscle(performed(stepUps, working(1)))

        assertEquals(1.0, volume.getValue("quadriceps"), 0.0)
        assertEquals(0.5, volume.getValue("glutes"), 0.0)
        assertEquals(0.5, volume.getValue("hamstrings"), 0.0)
    }

    @Test
    fun `an exercise that targets two muscles primarily works both of them in full`() {
        // One row in the bank names two primaries. Splitting the credit would invent a rule and
        // taking only the first would lose a muscle the movement genuinely trains.
        val halo = exercise(
            id = "Kettlebell_Halo",
            primary = listOf("shoulders", "triceps"),
            secondary = emptyList(),
        )

        assertEquals(
            mapOf("shoulders" to 1.0, "triceps" to 1.0),
            volumeByMuscle(performed(halo, working(1))),
        )
    }

    @Test
    fun `a muscle listed twice as a secondary is still only counted once`() {
        val odd = exercise(
            id = "Odd_Row",
            primary = listOf("lats"),
            secondary = listOf("biceps", "biceps"),
        )

        assertEquals(mapOf("lats" to 1.0, "biceps" to 0.5), volumeByMuscle(performed(odd, working(1))))
    }

    @Test
    fun `a drop set is one set, however many drops it carries`() {
        // A drop belongs to the set it continues and is never a set of its own. Counting them would
        // report three sets of work for one, and the per-session cap would mean nothing.
        val dropSet = working(1).copy(
            efforts = listOf(
                effort(weight = 60.0, reps = 8).copy(
                    drops = listOf(drop(1, 50.0, 4), drop(2, 40.0, 3)),
                ),
            ),
        )

        assertEquals(
            mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5),
            volumeByMuscle(performed(bench, dropSet)),
        )
    }

    @Test
    fun `a per-side set is one set, not one per side`() {
        val perSide = working(1).copy(
            efforts = listOf(
                effort(side = Side.LEFT, weight = 22.5, reps = 10),
                effort(side = Side.RIGHT, weight = 20.0, reps = 10),
            ),
        )

        assertEquals(
            mapOf("chest" to 1.0, "shoulders" to 0.5, "triceps" to 0.5),
            volumeByMuscle(performed(bench, perSide)),
        )
        // Different weights per side is the valid, expected shape: tracking an imbalance is the
        // only reason to expand a set at all.
        assertEquals(listOf(22.5, 20.0), perSide.efforts.map { it.weight.value })
        assertEquals(listOf(Side.LEFT, Side.RIGHT), perSide.efforts.map { it.side })
        assertTrue(perSide.isPerSide)
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

    private fun performed(exercise: Exercise, vararg sets: PerformedSet) = listOf(
        SessionExercise(id = 1, exercise = exercise, orderIndex = 1, sets = sets.toList()),
    )

    private fun working(setIndex: Int) = set(setIndex, SetType.WORKING)

    private fun set(setIndex: Int, type: SetType, id: Long = setIndex.toLong()) = PerformedSet(
        setIndex = setIndex,
        type = type,
        efforts = listOf(effort(id = id)),
    )

    private fun effort(
        id: Long = 1,
        side: Side? = null,
        weight: Double = 60.0,
        reps: Int = 5,
    ) = Effort(id = id, side = side, weight = Kilograms(weight), reps = reps, note = null)

    private fun drop(orderIndex: Int, weight: Double, reps: Int) =
        Drop(id = orderIndex.toLong(), orderIndex = orderIndex, weight = Kilograms(weight), reps = reps)
}
