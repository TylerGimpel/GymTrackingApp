package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an app update that improves the exercise bank is allowed to do.
 *
 * The bank will be updated, and the author's own history contains machines the bank does not know.
 * An update that clobbers what a lifter added, or orphans what they logged, destroys the thing the
 * app exists to protect — so the rules are here, where they can be read and tested without a
 * database anywhere near them.
 */
class BankUpdateTest {

    @Test
    fun `a row the bank has gained is added`() {
        val plan = planBankUpdate(current = listOf(bench), incoming = listOf(bench, squat))

        assertEquals(listOf(BankChange.Add(squat)), plan)
    }

    @Test
    fun `a row the bank has corrected is replaced`() {
        val corrected = bench.copy(bankName = "Barbell Bench Press", equipment = "barbell")

        val plan = planBankUpdate(current = listOf(bench), incoming = listOf(corrected))

        assertEquals(listOf(BankChange.Replace(corrected)), plan)
    }

    @Test
    fun `a row the bank has dropped, and nobody trained, goes`() {
        val plan = planBankUpdate(current = listOf(bench, squat), incoming = listOf(bench))

        assertEquals(listOf(BankChange.Remove(squat.id)), plan)
    }

    @Test
    fun `a row the bank has dropped, but somebody trained, is tombstoned rather than deleted`() {
        // Deleting it would orphan every set logged against it. The training happened; the app's
        // opinion of the exercise changing does not unhappen it.
        val plan = planBankUpdate(
            current = listOf(bench, squat),
            incoming = listOf(bench),
            isReferenced = { it == squat.id },
        )

        assertEquals(listOf(BankChange.Tombstone(squat.id)), plan)
    }

    @Test
    fun `a tombstoned row the bank brings back is revived`() {
        val plan = planBankUpdate(
            current = listOf(bench.copy(isTombstoned = true)),
            incoming = listOf(bench),
        )

        val replaced = plan.single() as BankChange.Replace
        assertEquals(false, replaced.exercise.isTombstoned)
    }

    @Test
    fun `an exercise the lifter added is never touched`() {
        val mine = userAuthored("My Gym's Chest Press")

        val plan = planBankUpdate(current = listOf(bench, mine), incoming = listOf(bench))

        // Not removed, not tombstoned, not mentioned. It was never the bank's to have an opinion
        // about — that is what the separate id space is for.
        assertTrue(plan.none { it.id == mine.id })
    }

    @Test
    fun `an incoming row colliding with an id the lifter owns is refused`() {
        // Should be impossible — one id space is upstream strings and the other is UUIDs — but an
        // update that overwrote a lifter's own exercise would be the worst outcome in this file,
        // so it does not depend on the id spaces staying apart by luck.
        val mine = userAuthored("My Gym's Chest Press")
        val impostor = bench.copy(id = mine.id)

        val plan = planBankUpdate(current = listOf(mine), incoming = listOf(impostor))

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a rename survives an update to the row it was made on`() {
        val renamed = bench.copy(userName = "Flat Press", note = "seat notch 3, use the long bar")
        val corrected = bench.copy(bankName = "Barbell Bench Press")

        val plan = planBankUpdate(current = listOf(renamed), incoming = listOf(corrected))

        // The bank's own fields are the bank's to correct. What the lifter called it, and what they
        // wrote about it, are not (ADR 0009).
        val replaced = plan.single() as BankChange.Replace
        assertEquals("Barbell Bench Press", replaced.exercise.bankName)
        assertEquals("Flat Press", replaced.exercise.userName)
        assertEquals("seat notch 3, use the long bar", replaced.exercise.note)
        assertEquals("Flat Press", replaced.exercise.name)
    }

    @Test
    fun `an update that adds, corrects and drops rows leaves everything of the lifter's standing`() {
        val renamed = bench.copy(userName = "Flat Press", note = "long bar")
        val mine = userAuthored("Pendulum Squat")
        val trained = squat
        val dropped = curl

        val plan = planBankUpdate(
            current = listOf(renamed, trained, dropped, mine),
            incoming = listOf(bench.copy(bankName = "Barbell Bench Press"), row),
            isReferenced = { it == trained.id },
        )

        assertEquals(
            listOf(
                BankChange.Replace(
                    bench.copy(bankName = "Barbell Bench Press", userName = "Flat Press", note = "long bar"),
                ),
                BankChange.Add(row),
                // Dropped and trained: kept, so the sets logged against it still read.
                BankChange.Tombstone(squat.id),
                // Dropped and untouched by anybody: simply gone.
                BankChange.Remove(curl.id),
            ),
            plan,
        )
        // The exercise the lifter added is not in the plan at all, and the rename on the corrected
        // row came through it untouched.
        assertTrue(plan.none { it.id == mine.id })
    }

    @Test
    fun `a row already tombstoned and still gone is left where it is`() {
        // There is nothing for this update to do about it, and saying so every time would make
        // every plan for ever carry an instruction that changes nothing.
        val plan = planBankUpdate(
            current = listOf(bench, squat.copy(isTombstoned = true)),
            incoming = listOf(bench),
            isReferenced = { true },
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `anything pointing at a dropped exercise is enough to tombstone it`() {
        // Not only logged sets: a session's plan and a routine day hold an exercise with a real
        // constraint too, and an exercise planned but never performed has no logged set at all.
        val plan = planBankUpdate(
            current = listOf(bench, squat),
            incoming = listOf(bench),
            isReferenced = { it == squat.id },
        )

        assertEquals(listOf(BankChange.Tombstone(squat.id)), plan)
    }

    @Test
    fun `an exercise with history that the bank keeps is left entirely alone`() {
        val plan = planBankUpdate(
            current = listOf(bench),
            incoming = listOf(bench),
            isReferenced = { true },
        )

        // Unchanged and still shipped: nothing to add, correct, tombstone or remove.
        assertTrue(plan.isEmpty())
    }

    private fun userAuthored(name: String) = Exercise(
        id = "8f14e45f-ceea-467a-9c2b-27e8a1d4f9b0",
        bankName = name,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "machine",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
        source = ExerciseSource.USER,
    )

    private companion object {
        val bench = shipped("Barbell_Bench_Press", "Barbell Bench Press - Medium Grip", "chest")
        val squat = shipped("Barbell_Squat", "Barbell Squat", "quadriceps")
        val curl = shipped("Barbell_Curl", "Barbell Curl", "biceps")
        val row = shipped("Seated_Cable_Rows", "Seated Cable Rows", "middle back")

        fun shipped(id: String, name: String, muscle: String) = Exercise(
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
            source = ExerciseSource.UPSTREAM,
        )
    }
}
