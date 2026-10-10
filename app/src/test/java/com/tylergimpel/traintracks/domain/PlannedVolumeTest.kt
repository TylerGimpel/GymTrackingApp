package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The numbers the generator reasons with, and the two that are wrong silently.
 *
 * Neither mistake guarded here produces an error. Mixing a fractional count with targets that
 * assume direct counting over-prescribes by about a third; feeding a lifter's stated cadence into
 * volume translation when some of their sessions are cardio under-prescribes lifting by 40%. Both
 * surface months later as somebody training wrongly, so both are asserted as arithmetic.
 */
class PlannedVolumeTest {

    @Test
    fun `a set counts in full toward what it trains and at half toward what it helps`() {
        val day = dayOf(bench to 4)

        val volume = plannedVolumeByMuscle(listOf(day))

        // ADR 0005: fractional counting, and it travels with Pelland's targets. A bench press does
        // work triceps, but not the way a triceps extension does.
        assertEquals(4.0, volume["chest"]!!, TOLERANCE)
        assertEquals(2.0, volume["triceps"]!!, TOLERANCE)
    }

    @Test
    fun `a muscle an exercise names twice is counted once, in full`() {
        val day = dayOf(stepUp to 3)

        // Nine shipped bank rows name the same muscle primary and secondary. Stacking the two
        // would quietly credit 1.5 sets for one set of work.
        assertEquals(3.0, plannedVolumeByMuscle(listOf(day))["quadriceps"]!!, TOLERANCE)
    }

    @Test
    fun `a cardio day carries no resistance volume`() {
        val lifting = dayOf(bench to 4)
        val cardio = RoutineDay(
            id = 2,
            orderIndex = 2,
            name = "Cardio",
            kind = RoutineDayKind.CARDIO,
            exercises = listOf(PlannedExercise(treadmill, 3)),
        )

        val volume = plannedVolumeByMuscle(listOf(lifting, cardio))

        // A cardio position carries no resistance work by definition (ADR 0005). Counting what a
        // treadmill row happens to name would put quadriceps volume into a week that had none.
        assertEquals(4.0, volume["chest"]!!, TOLERANCE)
        assertEquals(null, volume["quadriceps"])
    }

    @Test
    fun `an exercise nothing prescribed sets for contributes none`() {
        val day = RoutineDay(
            id = 1,
            orderIndex = 1,
            name = "Push",
            exercises = listOf(PlannedExercise(bench, sets = null)),
        )

        // Nothing asked, so nothing is counted. Guessing a number here would put volume into the
        // ledger that no routine ever prescribed.
        assertEquals(emptyMap<String, Double>(), plannedVolumeByMuscle(listOf(day)))
    }

    @Test
    fun `five sessions a week on upper lower cardio is three and a third lifting sessions`() {
        val routine = routineOf(
            cadence = 5,
            days = listOf(RoutineDayKind.LIFTING, RoutineDayKind.LIFTING, RoutineDayKind.CARDIO),
        )

        // The number this whole ticket exists for. Reading the lifter's stated five here
        // under-prescribes their lifting by 40%, silently, for as long as they use the app.
        assertEquals(3.333, routine.liftingCadence, 0.001)
    }

    @Test
    fun `a routine with no cardio lifts as often as the lifter trains`() {
        val routine = routineOf(cadence = 4, days = List(2) { RoutineDayKind.LIFTING })

        assertEquals(4.0, routine.liftingCadence, TOLERANCE)
    }

    @Test
    fun `weekly volume follows how often the cycle comes round`() {
        val routine = routineOf(
            cadence = 5,
            days = listOf(RoutineDayKind.LIFTING, RoutineDayKind.LIFTING, RoutineDayKind.CARDIO),
            content = listOf(dayOf(bench to 6), dayOf(bench to 6)),
        )

        // Twelve chest sets a cycle, and 3.33 lifting sessions a week across a two-lifting-day
        // cycle is five-thirds of a cycle a week: twenty weekly sets. Reading the raw cadence
        // would have said thirty, and prescribed a third less work to compensate.
        assertEquals(20.0, routine.weeklyVolumeByMuscle()["chest"]!!, 0.001)
    }

    @Test
    fun `the tiers are Pelland's and a set count lands in the one it belongs to`() {
        // The evidence half of the matched pair. If someone swaps in Baz-Valle's 12-20 while
        // leaving the counting fractional, these boundaries move and this fails.
        assertEquals(VolumeTier.BELOW_MINIMUM, VolumeTier.of(3.5))
        assertEquals(VolumeTier.MINIMUM_EFFECTIVE, VolumeTier.of(4.0))
        assertEquals(VolumeTier.HIGHER_EFFICIENCY, VolumeTier.of(10.0))
        assertEquals(VolumeTier.INTERMEDIATE_EFFICIENCY, VolumeTier.of(11.0))
        assertEquals(VolumeTier.INTERMEDIATE_EFFICIENCY, VolumeTier.of(18.0))
        assertEquals(VolumeTier.LOWER_EFFICIENCY, VolumeTier.of(19.0))
        assertEquals(VolumeTier.LOWEST_EFFICIENCY, VolumeTier.of(30.0))
        assertEquals(VolumeTier.UNCLEAR, VolumeTier.of(43.0))
    }

    @Test
    fun `the target band sits inside the tiers rather than beside them`() {
        // The judgement call is which tier a goal aims at; the tiers themselves are not one. What
        // must never happen is a target that is not a tier at all, which is what taking numbers
        // from one convention and counting in the other produces.
        RoutineGoal.entries.forEach { goal ->
            val target = weeklyVolumeTargetFor(goal)
            assertEquals(
                "the target band for $goal straddles a tier boundary",
                VolumeTier.of(target.start),
                VolumeTier.of(target.endInclusive),
            )
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001

        fun dayOf(vararg entries: Pair<Exercise, Int>, id: Long = 1) = RoutineDay(
            id = id,
            orderIndex = 1,
            name = "Day",
            exercises = entries.map { (exercise, sets) -> PlannedExercise(exercise, sets) },
        )

        fun routineOf(
            cadence: Int,
            days: List<RoutineDayKind>,
            content: List<RoutineDay> = emptyList(),
        ) = Routine(
            id = 1,
            name = "Routine",
            goal = RoutineGoal.HYPERTROPHY,
            cadence = cadence,
            equipment = emptySet(),
            repPreference = RepPreference.MODERATE,
            days = days.mapIndexed { index, kind ->
                RoutineDay(
                    id = index + 1L,
                    orderIndex = index + 1,
                    name = "Day ${index + 1}",
                    kind = kind,
                    exercises = content.getOrNull(index)?.exercises.orEmpty(),
                )
            },
        )

        fun exercise(
            id: String,
            primary: List<String>,
            secondary: List<String> = emptyList(),
        ) = Exercise(
            id = id,
            bankName = id,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "intermediate",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bench = exercise("Bench", listOf("chest"), listOf("triceps", "shoulders"))
        val stepUp = exercise("StepUp", listOf("quadriceps"), listOf("quadriceps", "glutes"))
        val treadmill = exercise("Treadmill", listOf("quadriceps"))
    }
}
