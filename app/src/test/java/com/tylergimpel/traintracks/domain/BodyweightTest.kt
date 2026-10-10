package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bodyweight is what the lifter weighs, recorded against a day.
 *
 * Profile data rather than training: it sits on the same calendar as the sessions and belongs to
 * none of them. The whole reason it is modelled apart is that conflating it with training data is
 * what makes it pollute volume figures and progression — a scale reading is not a set of anything.
 */
class BodyweightTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)
    private val zone: ZoneId = ZoneId.of("Europe/London")

    @Test
    fun `an entry records a day and what the lifter weighed on it`() {
        val draft = BodyweightDraft(weightText = "82.4", date = monday)

        assertTrue(draft.isRecordable)
        assertEquals(monday, draft.date)
        assertEquals(82.4, draft.weight!!.value, 0.0)
    }

    @Test
    fun `a reading taken on a pound scale is recorded as the kilograms it actually weighs`() {
        // The same rule as a set: what is stored is canonical kilograms, and the unit only ever
        // decides what the number typed meant.
        val draft = BodyweightDraft(weightText = "180", date = monday, displayUnit = WeightUnit.POUNDS)

        assertEquals(81.6466266, draft.weight!!.value, 1e-7)
    }

    @Test
    fun `switching scales rewrites the reading and leaves the weight alone`() {
        val draft = BodyweightDraft(weightText = "82.4", date = monday)

        val inPounds = draft.readIn(WeightUnit.POUNDS)

        assertEquals("181.66", inPounds.weightText)
        // Not the identical double — a hundredth of a pound is finer than any scale reads — but the
        // same weight as far as anything a lifter ever sees is concerned.
        assertEquals("82.4", inPounds.weight!!.asTextIn(WeightUnit.KILOGRAMS))
    }

    @Test
    fun `an unreadable field is left exactly as it was typed`() {
        // Nothing to convert, and filling it in on the lifter's behalf would be the app recording a
        // weight nobody stood on a scale for.
        val draft = BodyweightDraft(weightText = "eigh", date = monday)

        assertEquals("eigh", draft.readIn(WeightUnit.POUNDS).weightText)
    }

    @Test
    fun `nothing is recorded until the field reads as a weight`() {
        assertFalse(BodyweightDraft(weightText = "", date = monday).isRecordable)
        assertNull(BodyweightDraft(weightText = "  ", date = monday).weight)
        assertNull(BodyweightDraft(weightText = "heavy", date = monday).weight)
    }

    @Test
    fun `nobody weighs nothing, so zero is not a bodyweight`() {
        // Deliberately unlike a set, where zero is a real answer — an unloaded bar and a bodyweight
        // dip are both genuinely zero added weight. A lifter does not weigh zero, so a blank-ish
        // reading recorded as one would drag a graph, and later a cold-start estimate, to the floor.
        assertNull(BodyweightDraft(weightText = "0", date = monday).weight)
        assertNull(BodyweightDraft(weightText = "-82", date = monday).weight)
    }

    @Test
    fun `a day can hold more than one reading, and keeps both`() {
        // Bodyweight swings a kilogram or two across a day, so a lifter who stepped on the scale
        // morning and evening measured that swing. Keeping only the last would throw away the more
        // useful of the two readings and call it tidiness.
        val history = BodyweightHistory(
            listOf(
                entry(id = 1, on = monday, weighing = 82.4, at = "07:10"),
                entry(id = 2, on = monday, weighing = 83.6, at = "21:30"),
            ),
        )

        assertEquals(listOf(2L, 1L), history.mostRecentFirst.map(BodyweightEntry::id))
        assertEquals(setOf(monday), history.daysWeighedSeveralTimes)
    }

    @Test
    fun `a day weighed once is not a day with several readings`() {
        // What decides whether a time is worth showing beside a date at all.
        val history = BodyweightHistory(
            listOf(
                entry(id = 1, on = monday, weighing = 82.4),
                entry(id = 2, on = monday.minusDays(1), weighing = 82.8),
            ),
        )

        assertEquals(emptySet<LocalDate>(), history.daysWeighedSeveralTimes)
    }

    @Test
    fun `the latest reading of the most recent day is the latest weight`() {
        val history = BodyweightHistory(
            listOf(
                entry(id = 1, on = monday, weighing = 82.4, at = "07:10"),
                entry(id = 2, on = monday, weighing = 83.6, at = "21:30"),
            ),
        )

        assertEquals(83.6, history.latest!!.weight.value, 0.0)
    }

    @Test
    fun `a weigh-in backfilled onto an earlier day is that day's, however recently it was typed`() {
        // Recorded last and dated first: the day leads the ordering, because Saturday's weight is
        // Saturday's whether it was typed on Saturday or remembered on Monday.
        val saturday = entry(id = 2, on = monday.minusDays(2), weighing = 83.1, at = "23:59")
        val history = BodyweightHistory(
            listOf(entry(id = 1, on = monday, weighing = 82.4, at = "07:10"), saturday),
        )

        assertEquals(listOf(1L, 2L), history.mostRecentFirst.map(BodyweightEntry::id))
        assertEquals(82.4, history.latest!!.weight.value, 0.0)
    }

    @Test
    fun `history reads most recent first, whatever order the entries arrive in`() {
        val history = BodyweightHistory(
            listOf(
                entry(id = 1, on = monday.minusDays(7), weighing = 83.1),
                entry(id = 2, on = monday, weighing = 82.4),
                entry(id = 3, on = monday.minusDays(14), weighing = 84.0),
            ),
        )

        assertEquals(
            listOf(monday, monday.minusDays(7), monday.minusDays(14)),
            history.mostRecentFirst.map(BodyweightEntry::date),
        )
    }

    @Test
    fun `the latest bodyweight is the most recent day's, not the most recently written`() {
        // What cold-start estimation will ask for. A lifter filling in last week's missed weigh-in
        // today has not lost weight since this morning, and the row written last is not the answer.
        val history = BodyweightHistory(
            listOf(
                entry(id = 1, on = monday, weighing = 82.4),
                entry(id = 2, on = monday.minusDays(7), weighing = 83.1),
            ),
        )

        assertEquals(82.4, history.latest!!.weight.value, 0.0)
    }

    @Test
    fun `a lifter who has never weighed themselves has no latest weight`() {
        assertNull(BodyweightHistory(emptyList()).latest)
        assertEquals(emptyList<BodyweightEntry>(), BodyweightHistory(emptyList()).mostRecentFirst)
    }

    @Test
    fun `a weight is stored in kilograms and read in whatever unit the lifter reads`() {
        val entry = entry(id = 1, on = monday, weighing = 82.4)

        assertEquals("82.4", entry.weight.asTextIn(WeightUnit.KILOGRAMS))
        assertEquals("181.66", entry.weight.asTextIn(WeightUnit.POUNDS))
    }

    @Test
    fun `bodyweight is recorded against a day rather than a session, so volume cannot reach it`() {
        // The structural claim, and the reason this is separate work at all. Volume is counted from
        // the sets performed against exercises, and a bodyweight entry carries no exercise to be
        // performed against and no set index to hold a place in one — so there is no list it could
        // be added to and nothing to filter it out of. What is asserted here is that the two never
        // meet: the heaviest number on the calendar that day is in neither the sets nor the volume
        // they earn.
        val performed = listOf(
            SessionExercise(
                id = 1,
                exercise = bench,
                orderIndex = 1,
                sets = listOf(
                    PerformedSet(
                        setIndex = 1,
                        type = SetType.WORKING,
                        efforts = listOf(
                            Effort(
                                id = 1,
                                side = null,
                                performance = Performance.WeightAndReps(Kilograms(60.0), 5),
                                note = null,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val history = BodyweightHistory(listOf(entry(id = 1, on = monday, weighing = 82.4)))

        assertEquals(82.4, history.latest!!.weight.value, 0.0)
        // One set of 60 kg, and nothing else logged against the exercise.
        assertEquals(
            listOf(60.0),
            performed.flatMap(SessionExercise::sets)
                .flatMap(PerformedSet::efforts)
                .map { it.performance.weightOrNull!!.value },
        )
        assertEquals(mapOf("chest" to 1.0, "triceps" to 0.5), volumeByMuscle(performed))
    }

    private fun entry(id: Long, on: LocalDate, weighing: Double, at: String = "07:10") =
        BodyweightEntry(
            id = id,
            recordedAt = LocalDateTime.parse("${on}T$at").atZone(zone).toInstant(),
            date = on,
            weight = Kilograms(weighing),
        )

    private val bench = Exercise(
        id = "Barbell_Bench_Press",
        bankName = "Barbell Bench Press",
        primaryMuscles = listOf("chest"),
        secondaryMuscles = listOf("triceps"),
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
    )
}
