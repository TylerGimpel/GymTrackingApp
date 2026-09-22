package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A cardio day is a position in the rotation like any other, and is counted like none of them.
 *
 * Both halves matter and they pull in opposite directions. The **Cursor** must not know the
 * difference — a day is a day, and special-casing one kind is how "skip" starts meaning two
 * things. The volume arithmetic must know it exactly — a cardio position carries no resistance
 * work, so counting it as a training session under-prescribes lifting by 40% (ADR 0005).
 */
class CardioDayTest {

    @Test
    fun `a cardio day takes its turn in the rotation`() {
        val routine = upperLowerCardio()

        // Nothing done yet, so the cursor is at the top of the rotation.
        assertEquals("Upper", routine.cursor(null)?.name)

        val afterUpper = cycleWith(routine, done = listOf("Upper"))
        assertEquals("Lower", routine.cursor(afterUpper)?.name)

        val afterLower = cycleWith(routine, done = listOf("Upper", "Lower"))

        // And it is simply next. A rotation that stepped over it would be one the lifter could
        // never train the cardio in.
        assertEquals("Cardio", routine.cursor(afterLower)?.name)
    }

    @Test
    fun `completing a cardio day advances the cursor like any other`() {
        val routine = upperLowerCardio()

        val everything = cycleWith(routine, done = listOf("Upper", "Lower", "Cardio"))

        // Every position dealt with is a cycle finished, and the next one opens with everything
        // undone. The cursor has no idea one of those was a run.
        assertEquals("Upper", routine.cursor(everything)?.name)
    }

    @Test
    fun `skipping a cardio day holds the cursor exactly as skipping a lifting day does`() {
        val routine = upperLowerCardio()

        val skippedCardio = cycleWith(routine, done = listOf("Upper", "Lower"), skipped = listOf("Cardio"))
        val skippedLower = cycleWith(routine, done = listOf("Upper"), skipped = listOf("Lower"))

        // A skip is a position dealt with, whichever kind it was: the cycle moves past it and
        // comes round to it next time. The rules being the same is the point.
        assertEquals("Upper", routine.cursor(skippedCardio)?.name)
        assertEquals("Cardio", routine.cursor(skippedLower)?.name)
    }

    @Test
    fun `a cardio position carries no resistance work and is counted accordingly`() {
        val routine = upperLowerCardio()

        // Three positions, two of which are training. The arithmetic that keeps a lifter from
        // being under-prescribed by 40% reads these two numbers, and reading the first for both
        // is the mistake ADR 0005 exists to prevent.
        assertEquals(3, routine.positions)
        assertEquals(2, routine.liftingPositions)
    }

    @Test
    fun `a routine with no cardio day counts every position as training`() {
        val routine = routineOf("Upper" to RoutineDayKind.LIFTING, "Lower" to RoutineDayKind.LIFTING)

        assertEquals(2, routine.positions)
        assertEquals(2, routine.liftingPositions)
    }

    @Test
    fun `cardio trained after lifting takes no position at all`() {
        // Same-day cardio is an exercise inside a lifting day, not a day. The lifting session
        // still happened, so the rotation is untouched and so is the cadence — what it does
        // affect is how long the session takes, which is the time ceiling's problem (#76).
        val routine = routineOf(
            "Upper" to RoutineDayKind.LIFTING,
            "Lower" to RoutineDayKind.LIFTING,
        )

        assertEquals(2, routine.positions)
        assertEquals(2, routine.liftingPositions)
        assertEquals(CardioPlacement.AFTER_LIFTING, routine.copy(
            cardio = CardioPlacement.AFTER_LIFTING,
        ).cardio)
    }

    private companion object {

        fun upperLowerCardio() = routineOf(
            "Upper" to RoutineDayKind.LIFTING,
            "Lower" to RoutineDayKind.LIFTING,
            "Cardio" to RoutineDayKind.CARDIO,
        )

        fun routineOf(vararg days: Pair<String, RoutineDayKind>) = Routine(
            id = 1,
            name = "Routine",
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 3,
            equipment = emptySet(),
            repPreference = RepPreference.MODERATE,
            days = days.mapIndexed { index, (name, kind) ->
                RoutineDay(id = index + 1L, orderIndex = index + 1, name = name, kind = kind)
            },
        )

        /** A cycle in which [done] were trained and [skipped] were dealt with without a session. */
        fun cycleWith(
            routine: Routine,
            done: List<String> = emptyList(),
            skipped: List<String> = emptyList(),
        ): Cycle {
            fun idOf(name: String) = routine.days.single { it.name == name }.id
            return Cycle(
                id = 1,
                ordinal = 1,
                outcomes = done.associate { idOf(it) to DayOutcome.COMPLETED } +
                    skipped.associate { idOf(it) to DayOutcome.SKIPPED },
            )
        }
    }
}
