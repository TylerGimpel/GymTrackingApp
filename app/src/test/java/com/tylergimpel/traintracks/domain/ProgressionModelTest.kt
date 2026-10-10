package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * How an exercise's recommendation advances, and the lifter's right to say "not this one".
 *
 * `none` is the half that earns its place immediately, and it is not a placeholder: a stretch, a
 * hold and a treadmill are recorded and never prescribed. Without a way to say so, an engine has to
 * infer "do not coach this" from the logging type every time it asks, and a lifter has no way to say
 * it at all about a lift they simply do not want coached.
 */
class ProgressionModelTest {

    @Test
    fun `a loaded exercise advances by double progression`() {
        // ADR 0004: double progression inside a rep range, Epley-anchored. The fuller vocabulary
        // belongs to the engine that reads it, not to the bank that carries it.
        listOf(LoggingType.WEIGHT_REPS, LoggingType.BODYWEIGHT_REPS, LoggingType.ASSISTED)
            .forEach { type ->
                assertEquals("$type", ProgressionModel.DOUBLE_PROGRESSION, ProgressionModel.of(type))
            }
    }

    @Test
    fun `an exercise measured in time or distance is recorded and never prescribed`() {
        // A plank and a treadmill are training a lifter keeps track of, not training the app tells
        // them how to do: there is no weight for a recommendation to advance.
        assertEquals(ProgressionModel.NONE, ProgressionModel.of(LoggingType.TIME))
        assertEquals(ProgressionModel.NONE, ProgressionModel.of(LoggingType.DISTANCE_TIME))
    }

    @Test
    fun `every logging type derives a model, so no exercise is left without one`() {
        LoggingType.entries.forEach { type -> ProgressionModel.of(type) }
    }

    @Test
    fun `nothing is prescribed for an exercise set to none`() {
        assertEquals(false, ProgressionModel.NONE.isPrescribed)
        assertEquals(true, ProgressionModel.DOUBLE_PROGRESSION.isPrescribed)
    }

    @Test
    fun `a stored model survives a round trip, and an unknown one is not guessed at`() {
        ProgressionModel.entries.forEach { assertEquals(it, ProgressionModel.fromStored(it.stored)) }
        assertEquals(null, ProgressionModel.fromStored(null))
        assertThrows(IllegalArgumentException::class.java) { ProgressionModel.fromStored("linear") }
    }

    @Test
    fun `what the lifter chose is what the exercise advances by`() {
        val treadmill = exercise(LoggingType.WEIGHT_REPS)

        // The bank derived double progression from the logging type; the lifter said not this one.
        assertEquals(ProgressionModel.DOUBLE_PROGRESSION, treadmill.progressionModel)
        assertEquals(
            ProgressionModel.NONE,
            treadmill.copy(chosenProgressionModel = ProgressionModel.NONE).progressionModel,
        )
    }

    @Test
    fun `a bank update leaves the lifter's choice where it was`() {
        // The same protection the rename, the note and the display unit have: an update writes bank
        // rows and what the lifter decided is not one (ADR 0009).
        val chosen = exercise(LoggingType.WEIGHT_REPS)
            .copy(chosenProgressionModel = ProgressionModel.NONE)
        val incoming = exercise(LoggingType.WEIGHT_REPS).copy(bankName = "Barbell Bench Press")

        assertEquals(ProgressionModel.NONE, incoming.keepingChoicesOf(chosen).progressionModel)
    }

    private fun exercise(loggingType: LoggingType) = Exercise(
        id = "Barbell_Bench_Press",
        bankName = "Bench Press",
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "barbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = loggingType,
        unilateral = false,
        bankProgressionModel = ProgressionModel.of(loggingType),
    )
}
