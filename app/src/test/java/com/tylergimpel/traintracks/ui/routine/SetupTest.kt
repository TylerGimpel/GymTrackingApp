package com.tylergimpel.traintracks.ui.routine

import com.tylergimpel.traintracks.domain.CARDIO_EQUIPMENT
import com.tylergimpel.traintracks.domain.CardioPlacement
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RoutineDayKind
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Letting the app build a routine, one question to a page with the usual answer already in (#144).
 *
 * The flow is a value, so what it asks, in what order, and what it builds from the answers can be
 * stated without a screen — which is the only way "setup answers are the routine's properties"
 * can be asserted rather than hoped.
 */
class SetupTest {

    @Test
    fun `the questions come in the agreed order`() {
        assertEquals(
            listOf(
                SetupStep.UNIT,
                SetupStep.GOAL,
                SetupStep.REP_PREFERENCE,
                SetupStep.CADENCE,
                SetupStep.SHAPE,
                SetupStep.CARDIO,
                SetupStep.EQUIPMENT,
            ),
            SetupStep.entries,
        )
        assertEquals(SetupStep.UNIT, SetupAnswers().step)
        assertEquals(7, SetupAnswers.STEPS)
    }

    @Test
    fun `every answer starts as the usual one, so Next always works`() {
        val setup = SetupAnswers().toRoutineSetup()

        assertEquals(RoutineGoal.HYPERTROPHY, setup.goal)
        assertEquals(RepPreference.MODERATE, setup.repPreference)
        assertEquals(3, setup.cadence)
        assertEquals(CardioPlacement.NONE, setup.cardio)
        // A full gym, which the rest of the app reads as the whole bank.
        assertEquals(emptySet<String>(), setup.equipment)
    }

    @Test
    fun `going back changes an answer without losing the others`() {
        val answered = SetupAnswers().next()
            .copy(goal = RoutineGoal.STRENGTH).next()
            .copy(repPreference = RepPreference.LOW).next()
            .withCadence(4)

        val back = answered.back().back()

        assertEquals(SetupStep.GOAL, back.step)
        assertEquals(RepPreference.LOW, back.repPreference)
        assertEquals(4, back.cadence)
        assertEquals(SetupStep.UNIT, back.back().back().step)
    }

    @Test
    fun `setup answers are the routine's properties`() {
        val answers = SetupAnswers(
            unit = WeightUnit.POUNDS,
            goal = RoutineGoal.STRENGTH,
            repPreference = RepPreference.LOW,
            cadence = 4,
            cardio = CardioPlacement.AFTER_LIFTING,
            equipment = setOf("barbell", "dumbbell", "rower"),
        )

        // Narrowed, so bodyweight goes in with what was ticked.
        assertEquals(
            RoutineSetup(
                cadence = 4,
                cardio = CardioPlacement.AFTER_LIFTING,
                equipment = setOf("barbell", "dumbbell", "rower", "body only"),
                goal = RoutineGoal.STRENGTH,
                repPreference = RepPreference.LOW,
            ),
            answers.toRoutineSetup(),
        )
    }

    @Test
    fun `the split starts as the best fit for the week and follows it until one is picked`() {
        assertEquals(4, SetupAnswers().withCadence(4).shapeToBuild().liftingPositions)
        assertEquals(2, SetupAnswers().withCadence(2).shapeToBuild().liftingPositions)

        val picked = SetupAnswers(shapeId = "push-pull-legs-3")
        assertEquals("push-pull-legs-3", picked.shapeToBuild().id)
        // A new week moves the pick back to the new best fit.
        assertEquals(4, picked.withCadence(4).shapeToBuild().liftingPositions)
    }

    @Test
    fun `cardio on its own day is added to the shape chosen before it was asked`() {
        val shape = SetupAnswers(shapeId = "upper-lower-2", cardio = CardioPlacement.OWN_DAY).shapeToBuild()

        assertEquals(RoutineDayKind.CARDIO, shape.days.last().kind)
        assertEquals(3, shape.days.size)
    }

    @Test
    fun `cardio machines are only offered to somebody who does cardio`() {
        assertTrue(SetupAnswers(cardio = CardioPlacement.NONE).equipmentOffered.none { it in CARDIO_EQUIPMENT })
        assertTrue(SetupAnswers(cardio = CardioPlacement.OWN_DAY).equipmentOffered.containsAll(CARDIO_EQUIPMENT))
    }

    @Test
    fun `a cardio machine ticked before cardio was turned off is not built with`() {
        val answers = SetupAnswers(cardio = CardioPlacement.NONE, equipment = setOf("barbell", "rower"))

        assertEquals(setOf("barbell", "body only"), answers.toRoutineSetup().equipment)
    }

    @Test
    fun `all and none work on what is offered`() {
        val none = SetupAnswers().withNoEquipment()
        assertFalse(none.fullGym)
        // Bodyweight only, which is still something to build from.
        assertEquals(setOf("body only"), none.toRoutineSetup().equipment)
        assertTrue(none.withAllEquipment().fullGym)
    }
}
