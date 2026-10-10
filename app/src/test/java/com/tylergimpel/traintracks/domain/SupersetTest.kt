package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two or more exercises performed alternately with no rest between them.
 *
 * A superset groups **across** exercises where a drop set groups **within** one set, and they are
 * deliberately different structures: collapsing them into one generic grouping is what makes volume
 * counting ambiguous again (ADR 0003). Grouping is a statement about rest, never about work.
 */
class SupersetTest {

    @Test
    fun `exercises that are not grouped read one at a time`() {
        val entries = listOf(performed(1, bench), performed(2, row)).asPositions(emptyList())

        assertEquals(listOf(bench, row), entries.flatMap { it.exercises }.map { it.exercise })
        assertTrue(entries.all { it is SessionPosition.Alone })
    }

    @Test
    fun `a pair performed alternately reads as one group`() {
        val group = superset(id = 1)

        val entries = listOf(performed(1, bench, 1), performed(2, row, 1)).asPositions(listOf(group))

        assertEquals(1, entries.size)
        val grouped = entries.single() as SessionPosition.Grouped
        assertEquals(group, grouped.superset)
        assertEquals(listOf(bench, row), grouped.exercises.map { it.exercise })
    }

    @Test
    fun `groups of three, four and five members all work`() {
        // The author's own history holds 7 triples, 2 quads and a quintuple, so anything that only
        // handles pairs is wrong about real training.
        listOf(3, 4, 5).forEach { size ->
            val members = (1..size).map { performed(it.toLong(), bench, supersetId = 1) }

            val entries = members.asPositions(listOf(superset(id = 1)))

            assertEquals(1, entries.size)
            assertEquals(size, entries.single().exercises.size)
        }
    }

    @Test
    fun `a group sits where its first member was performed`() {
        val entries = listOf(
            performed(1, curl),
            performed(2, bench, supersetId = 1),
            performed(3, row, supersetId = 1),
            performed(4, squat),
        ).asPositions(listOf(superset(id = 1)))

        assertEquals(3, entries.size)
        assertEquals(listOf(curl), entries[0].exercises.map { it.exercise })
        assertEquals(listOf(bench, row), entries[1].exercises.map { it.exercise })
        assertEquals(listOf(squat), entries[2].exercises.map { it.exercise })
    }

    @Test
    fun `two groups in one session stay apart`() {
        val entries = listOf(
            performed(1, bench, supersetId = 1),
            performed(2, row, supersetId = 1),
            performed(3, curl, supersetId = 2),
            performed(4, squat, supersetId = 2),
        ).asPositions(listOf(superset(id = 1), superset(id = 2, orderIndex = 2)))

        assertEquals(2, entries.size)
        assertEquals(listOf(bench, row), entries[0].exercises.map { it.exercise })
        assertEquals(listOf(curl, squat), entries[1].exercises.map { it.exercise })
    }

    @Test
    fun `an exercise pointing at a group that is gone reads on its own`() {
        // What ungrouping looks like from here: the group is deleted and the members are released,
        // and a member left holding a stale id must still read as an ordinary exercise rather than
        // vanish from the session.
        val entries = listOf(performed(1, bench, supersetId = 99)).asPositions(emptyList())

        assertEquals(listOf(bench), entries.single().exercises.map { it.exercise })
        assertTrue(entries.single() is SessionPosition.Alone)
    }

    @Test
    fun `a group carries an order, an auto-jump flag and its own rest`() {
        val group = Superset(id = 1, orderIndex = 2, autoJump = true, restSeconds = 120)

        assertEquals(2, group.orderIndex)
        assertTrue(group.autoJump)
        assertEquals(120, group.restSeconds)
    }

    @Test
    fun `rest belongs to the group rather than to anything between its members`() {
        val group = superset(id = 1, restSeconds = 90)
        val entries = listOf(performed(1, bench, 1), performed(2, row, 1)).asPositions(listOf(group))

        // One rest for the whole group, read off the group. Its members are plain session
        // exercises with no rest of their own to disagree with it, which is the definition rather
        // than a detail of how this is stored.
        val grouped = entries.single() as SessionPosition.Grouped
        assertEquals(90, grouped.superset.restSeconds)
        assertEquals(2, grouped.exercises.size)
    }

    @Test
    fun `grouping changes no set's contribution to volume`() {
        val apart = listOf(performed(1, bench, sets = 3), performed(2, row, sets = 2))
        val grouped = listOf(
            performed(1, bench, supersetId = 1, sets = 3),
            performed(2, row, supersetId = 1, sets = 2),
        )

        // Grouping is a statement about rest. The work is the same work.
        assertEquals(volumeByMuscle(apart), volumeByMuscle(grouped))
    }

    private fun superset(id: Long, orderIndex: Int = 1, restSeconds: Int? = null) =
        Superset(id = id, orderIndex = orderIndex, autoJump = false, restSeconds = restSeconds)

    private fun performed(
        id: Long,
        exercise: Exercise,
        supersetId: Long? = null,
        sets: Int = 1,
    ) = SessionExercise(
        id = id,
        exercise = exercise,
        orderIndex = id.toInt(),
        sets = (1..sets).map {
            PerformedSet(
                setIndex = it,
                type = SetType.WORKING,
                efforts = listOf(
                    Effort(
                        id = it.toLong(),
                        side = null,
                        performance = Performance.WeightAndReps(Kilograms(60.0), 5),
                        note = null,
                    ),
                ),
            )
        },
        supersetId = supersetId,
    )

    private companion object {
        val bench = exercise("Barbell_Bench_Press", "Barbell Bench Press", "chest")
        val row = exercise("Seated_Cable_Row", "Seated Cable Row", "middle back")
        val curl = exercise("Barbell_Curl", "Barbell Curl", "biceps")
        val squat = exercise("Barbell_Squat", "Barbell Squat", "quadriceps")

        fun exercise(id: String, name: String, muscle: String) = Exercise(
            id = id,
            bankName = name,
            primaryMuscles = listOf(muscle),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )
    }
}
