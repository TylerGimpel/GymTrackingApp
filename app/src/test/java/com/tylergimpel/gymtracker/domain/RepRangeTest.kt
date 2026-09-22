package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The band an exercise is worked in, and where it comes from.
 *
 * These state a goal and a preference and assert the band that comes back — never how it is
 * stored, and never which constant produced it. What matters to a lifter is that training for
 * strength puts them in low reps and that being overruled is something they are told about.
 */
class RepRangeTest {

    @Test
    fun `a goal and a preference that agree give the band the preference asked for`() {
        assertEquals(RepRange(6, 8), seedRepRange(RoutineGoal.HYPERTROPHY, RepPreference.LOW).range)
        assertEquals(
            RepRange(8, 12),
            seedRepRange(RoutineGoal.HYPERTROPHY, RepPreference.MODERATE).range,
        )
        assertEquals(
            RepRange(12, 15),
            seedRepRange(RoutineGoal.HYPERTROPHY, RepPreference.HIGH).range,
        )
    }

    @Test
    fun `nothing is overruled where the goal leaves room for the preference`() {
        RoutineGoal.entries.forEach { goal ->
            listOf(RepPreference.LOW, RepPreference.MODERATE).forEach { preference ->
                assertNull(
                    "$goal with a $preference preference overruled something",
                    seedRepRange(goal, preference).overruled,
                )
            }
        }
    }

    @Test
    fun `training for strength puts a lifter in low reps`() {
        // 1-6RM is where the load evidence puts strength work. The exact split between a low and
        // a moderate preference inside that is a judgement call; being under six is not.
        RepPreference.entries.forEach { preference ->
            val seeded = seedRepRange(RoutineGoal.STRENGTH, preference)
            assert(seeded.range.top <= 6) {
                "a $preference preference under a strength goal topped out at ${seeded.range.top}"
            }
        }
    }

    @Test
    fun `a strength goal overrules a high rep preference and says so`() {
        val seeded = seedRepRange(RoutineGoal.STRENGTH, RepPreference.HIGH)

        // The conflict this spec's table settles: opposed by the load evidence, so the goal wins.
        // Silently satisfying the preference produces a routine that will not deliver the goal,
        // and the lifter does not find out for three months.
        assertEquals(RepPreference.HIGH, seeded.overruled)
        assertEquals(seedRepRange(RoutineGoal.STRENGTH, RepPreference.MODERATE).range, seeded.range)
    }

    @Test
    fun `training for endurance puts a lifter where high reps need the effort said out loud`() {
        // The logging screen's effort line keys off the top of the range at 15, and an endurance
        // goal is exactly the case it exists for: these bands only work if the effort is there.
        val seeded = seedRepRange(RoutineGoal.ENDURANCE, RepPreference.MODERATE)

        assert(seeded.range.top >= 15) { "an endurance goal topped out at ${seeded.range.top}" }
    }

    @Test
    fun `every band a goal and preference can produce is one the app will prescribe`() {
        // The constructor refuses a range topping out past what anybody trains, and a seed that
        // tripped it would crash the screen rather than prescribe badly. Cheaper to assert here.
        RoutineGoal.entries.forEach { goal ->
            RepPreference.entries.forEach { preference ->
                val range = seedRepRange(goal, preference).range
                assert(range.top <= RepRange.MAX_PRESCRIBED_REPS)
                assert(range.bottom >= 1)
            }
        }
    }

    @Test
    fun `a band a lifter chose is theirs whatever the goal says`() {
        val chosen = RepRange(5, 5)

        // The routine's goal seeds; it does not overrule. A lifter who has said what they want
        // this exercise worked in has answered the question, and a goal changing later must not
        // quietly take it back.
        assertEquals(chosen, repRangeOf(chosen = chosen, seeded = seedRepRange(
            RoutineGoal.ENDURANCE,
            RepPreference.HIGH,
        ).range))
    }

    @Test
    fun `an exercise nobody has spoken about takes the seeded band`() {
        val seeded = seedRepRange(RoutineGoal.HYPERTROPHY, RepPreference.LOW).range

        assertEquals(seeded, repRangeOf(chosen = null, seeded = seeded))
    }
}
