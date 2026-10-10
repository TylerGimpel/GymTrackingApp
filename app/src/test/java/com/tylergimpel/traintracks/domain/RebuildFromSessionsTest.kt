package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
                session("Upper lower", "Push", "2026-03-02", bench to 4),
                session("Upper lower", "Pull", "2026-03-04", row to 4),
                session("Upper lower", "Legs", "2026-03-06", squat to 5),
            ),
        ).single()

        assertEquals("Upper lower", recovered.name)
        assertEquals(listOf("Push", "Pull", "Legs"), recovered.days.map { it.name })
        assertEquals(
            listOf("Bench", "Row", "Squat"),
            recovered.days.map { it.exercises.single().exercise.id },
        )
    }

    @Test
    fun `what each session was asked for comes back with it`() {
        val recovered = rebuildFrom(listOf(session("Mine", "Legs", "2026-03-06", squat to 5)))

        // The point of rebuilding from a session rather than generating something new: it is
        // what they were actually doing, set counts and all.
        assertEquals(5, recovered.single().days.single().exercises.single().sets)
    }

    @Test
    fun `the last time a day was trained is the version that comes back`() {
        val recovered = rebuildFrom(
            listOf(
                session("Mine", "Push", "2026-03-02", bench to 3),
                session("Mine", "Push", "2026-03-16", bench to 5, row to 4),
            ),
        ).single()

        // A lifter rebuilding from last spring wants what they were doing by the end of it, not
        // the first thing they tried. Where the same day was trained repeatedly, the last one is
        // the one that stuck.
        assertEquals(1, recovered.days.size)
        assertEquals(2, recovered.days.single().exercises.size)
        assertEquals(5, recovered.days.single().exercises.first().sets)
    }

    @Test
    fun `a rotation is never assembled from days belonging to two different routines`() {
        val recovered = rebuildFrom(
            listOf(
                session("Upper lower", "Upper", "2026-03-02", bench to 4),
                session("Upper lower", "Lower", "2026-03-05", squat to 4),
                session("Push pull legs", "Push", "2026-06-01", bench to 4),
                session("Push pull legs", "Pull", "2026-06-03", row to 4),
                session("Push pull legs", "Legs", "2026-06-05", squat to 5),
            ),
        )

        // Anybody who has trained for a few months has changed routines inside the window, and
        // blending the two into one rotation is the ordinary case rather than the odd one.
        assertEquals(
            listOf("Push pull legs", "Upper lower"),
            recovered.map { it.name },
        )
        assertEquals(listOf("Push", "Pull", "Legs"), recovered.first().days.map { it.name })
        assertEquals(listOf("Upper", "Lower"), recovered.last().days.map { it.name })
    }

    @Test
    fun `two routines that happen to share a name are still two routines`() {
        val recovered = rebuildFrom(
            listOf(
                session("My routine", "Upper", "2026-01-05", bench to 4),
                session("My routine", "Lower", "2026-01-07", squat to 4),
                session("Push pull legs", "Push", "2026-03-02", bench to 4),
                session("My routine", "Full body", "2026-06-01", row to 3),
            ),
        )

        // The name is all a session copies, and "My routine" is what every routine built by hand
        // starts out called. Somebody else's routine in between is what says the two were not
        // the same one — so the spring one does not come back with a day from the summer.
        assertEquals(
            listOf("My routine", "Push pull legs", "My routine"),
            recovered.map { it.name },
        )
        assertEquals(listOf("Full body"), recovered.first().days.map { it.name })
        assertEquals(listOf("Upper", "Lower"), recovered.last().days.map { it.name })
    }

    @Test
    fun `the routine trained most recently is offered first`() {
        val recovered = rebuildFrom(
            listOf(
                session("Last spring", "Push", "2026-03-02", bench to 4),
                session("What I do now", "Upper", "2026-09-05", bench to 4),
            ),
        )

        // Which is the one they are most likely to mean, and the one the old blend would have
        // half-buried under a routine they left months ago.
        assertEquals(listOf("What I do now", "Last spring"), recovered.map { it.name })
        assertEquals(LocalDate.parse("2026-03-02"), recovered.last().lastTrained)
    }

    @Test
    fun `a session that followed no routine day contributes nothing`() {
        val recovered = rebuildFrom(
            listOf(
                session("Mine", day = null, on = "2026-03-02", bench to 4),
                session("Mine", "Push", "2026-03-04", row to 4),
            ),
        )

        // Training with no routine is an ordinary session, and there is no rotation in it to
        // recover — not a gap, just not an answer to this question.
        assertEquals(listOf("Push"), recovered.single().days.map { it.name })
    }

    @Test
    fun `a session with no plan contributes nothing`() {
        val recovered = rebuildFrom(listOf(session("Mine", "Push", "2026-03-02")))

        // It named a day but nothing recorded what that day held, so there is nothing to rebuild
        // from. Creating an empty "Push" would be inventing a day rather than recovering one.
        assertTrue(recovered.isEmpty())
    }

    @Test
    fun `no sessions at all is nothing to rebuild rather than a failure`() {
        assertEquals(emptyList<RecoveredRoutine>(), rebuildFrom(emptyList()))
    }

    private companion object {
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
            routine: String,
            day: String?,
            on: String,
            vararg planned: Pair<Exercise, Int>,
        ) = PlannedSession(
            session = Session(
                id = "$routine$day$on".hashCode().toLong(),
                startedAt = Instant.parse("${on}T18:00:00Z"),
                finishedAt = Instant.parse("${on}T19:00:00Z"),
                date = LocalDate.parse(on),
                routineName = day?.let { routine },
                routineDayName = day,
                note = null,
            ),
            plan = SessionPlan(planned.map { (exercise, sets) -> PlannedExercise(exercise, sets) }),
        )
    }
}
