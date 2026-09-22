package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Getting back a routine that worked last spring, out of the log.
 *
 * **No new storage, which is the whole argument.** A session snapshots what its routine day held
 * when it was trained — ADR 0006 leans on exactly that to justify not versioning routines — so
 * every day a lifter ever followed is already recorded, and recovering one is a read.
 */
class RebuildFromSessionsTest {

    @Test
    fun `a cycle of sessions comes back as the rotation they were following`() {
        val recovered = rebuildFrom(
            listOf(
                session("Push", "2026-03-02", bench to 4),
                session("Pull", "2026-03-04", row to 4),
                session("Legs", "2026-03-06", squat to 5),
            ),
        )

        assertEquals(listOf("Push", "Pull", "Legs"), recovered.map { it.name })
        assertEquals(
            listOf("Bench", "Row", "Squat"),
            recovered.map { it.exercises.single().exercise.id },
        )
    }

    @Test
    fun `what each session was asked for comes back with it`() {
        val recovered = rebuildFrom(listOf(session("Legs", "2026-03-06", squat to 5)))

        // The point of rebuilding from a session rather than generating something new: it is
        // what they were actually doing, set counts and all.
        assertEquals(5, recovered.single().exercises.single().sets)
    }

    @Test
    fun `the last time a day was trained is the version that comes back`() {
        val recovered = rebuildFrom(
            listOf(
                session("Push", "2026-03-02", bench to 3),
                session("Push", "2026-03-16", bench to 5, row to 4),
            ),
        )

        // A lifter rebuilding from last spring wants what they were doing by the end of it, not
        // the first thing they tried. Where the same day was trained repeatedly, the last one is
        // the one that stuck.
        assertEquals(1, recovered.size)
        assertEquals(2, recovered.single().exercises.size)
        assertEquals(5, recovered.single().exercises.first().sets)
    }

    @Test
    fun `a session that followed no routine day contributes nothing`() {
        val recovered = rebuildFrom(
            listOf(
                session(day = null, on = "2026-03-02", bench to 4),
                session("Push", "2026-03-04", row to 4),
            ),
        )

        // Training with no routine is an ordinary session, and there is no rotation in it to
        // recover — not a gap, just not an answer to this question.
        assertEquals(listOf("Push"), recovered.map { it.name })
    }

    @Test
    fun `a session with no plan contributes nothing`() {
        val recovered = rebuildFrom(listOf(session("Push", "2026-03-02")))

        // It named a day but nothing recorded what that day held, so there is nothing to rebuild
        // from. Creating an empty "Push" would be inventing a day rather than recovering one.
        assertTrue(recovered.isEmpty())
    }

    @Test
    fun `no sessions at all is an empty rotation rather than a failure`() {
        assertEquals(emptyList<RecoveredDay>(), rebuildFrom(emptyList()))
    }

    private companion object {
        val zone: ZoneId = ZoneId.of("Europe/London")

        fun exercise(id: String) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bench = exercise("Bench")
        val row = exercise("Row")
        val squat = exercise("Squat")

        fun session(
            day: String?,
            on: String,
            vararg planned: Pair<Exercise, Int>,
        ) = LoggedSession(
            session = Session(
                id = on.hashCode().toLong(),
                startedAt = Instant.parse("${on}T18:00:00Z"),
                finishedAt = Instant.parse("${on}T19:00:00Z"),
                date = LocalDate.parse(on),
                routineName = day?.let { "Routine" },
                routineDayName = day,
                note = null,
            ),
            exercises = emptyList(),
            plan = SessionPlan(planned.map { (exercise, sets) -> PlannedExercise(exercise, sets) }),
        )
    }
}
