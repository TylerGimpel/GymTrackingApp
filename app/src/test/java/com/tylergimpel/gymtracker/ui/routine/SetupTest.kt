package com.tylergimpel.gymtracker.ui.routine

import com.tylergimpel.gymtracker.domain.CARDIO_EQUIPMENT
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.splitShapesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Letting the app build a routine, as a short conversation in the order people finish (#86).
 *
 * The flow is a value, so what it asks, in what order, and what it builds from the answers can be
 * stated without a screen — which is the only way "setup answers are the routine's properties"
 * can be asserted rather than hoped.
 */
class SetupTest {

    @Test
    fun `the questions come in the order people finish them`() {
        // Goal first because it is the one people want to answer; cardio before equipment because
        // the cardio machines depend on it; equipment last because it is much the longest.
        assertEquals(
            listOf(
                SetupStep.GOAL,
                SetupStep.REP_PREFERENCE,
                SetupStep.CADENCE,
                SetupStep.SHAPE,
                SetupStep.CARDIO,
                SetupStep.EQUIPMENT,
            ),
            SetupStep.entries,
        )
        assertEquals(SetupStep.GOAL, SetupAnswers().step)
    }

    @Test
    fun `progress says how far through a finite thing the lifter is`() {
        val third = SetupAnswers().next().next()

        assertEquals(3, third.stepNumber)
        assertEquals(6, SetupAnswers.STEPS)
    }

    @Test
    fun `going back changes an answer without losing the others`() {
        val answered = SetupAnswers()
            .copy(goal = RoutineGoal.STRENGTH).next()
            .copy(repPreference = RepPreference.LOW).next()
            .copy(cadence = 4)

        val back = answered.back().back()

        assertEquals(SetupStep.GOAL, back.step)
        assertEquals(RepPreference.LOW, back.repPreference)
        assertEquals(4, back.cadence)
        // And back at the very start there is nowhere further back to go.
        assertEquals(SetupStep.GOAL, back.back().step)
    }

    @Test
    fun `skipping a question leaves it unanswered and moves on`() {
        val skipped = SetupAnswers().copy(goal = RoutineGoal.STRENGTH).skip()

        assertEquals(SetupStep.REP_PREFERENCE, skipped.step)
        assertNull(skipped.goal)
    }

    @Test
    fun `setup answers are the routine's properties`() {
        val answers = SetupAnswers(
            goal = RoutineGoal.STRENGTH,
            repPreference = RepPreference.LOW,
            cadence = 4,
            cardio = CardioPlacement.AFTER_LIFTING,
            equipment = setOf("barbell", "dumbbell", "rower"),
        )

        // The same type every path that makes a routine takes, so answering differently here and
        // editing a property later are one and the same thing.
        assertEquals(
            RoutineSetup(
                cadence = 4,
                cardio = CardioPlacement.AFTER_LIFTING,
                equipment = setOf("barbell", "dumbbell", "rower"),
                goal = RoutineGoal.STRENGTH,
                repPreference = RepPreference.LOW,
            ),
            answers.toRoutineSetup(),
        )
    }

    @Test
    fun `everything skipped is built from what each skipped question says it assumes`() {
        val setup = SetupAnswers().toRoutineSetup()

        assertEquals(RoutineSetup.ASSUMED_GOAL, setup.goal)
        assertEquals(RoutineSetup.ASSUMED_REP_PREFERENCE, setup.repPreference)
        assertEquals(SetupAnswers.ASSUMED_CADENCE, setup.cadence)
        assertEquals(CardioPlacement.NONE, setup.cardio)
        // Nothing ticked is "nobody has described the gym yet", which offers everything rather
        // than nothing.
        assertEquals(emptySet<String>(), setup.equipment)
    }

    @Test
    fun `a skipped shape is the likeliest one for the week they train`() {
        val answers = SetupAnswers(cadence = 4)

        assertEquals(splitShapesFor(4).first().id, answers.shapeToBuild().id)
    }

    @Test
    fun `cardio on its own day is added to the shape chosen before it was asked`() {
        val answers = SetupAnswers(shapeId = "upper-lower-2", cardio = CardioPlacement.OWN_DAY)

        val shape = answers.shapeToBuild()

        assertEquals(RoutineDayKind.CARDIO, shape.days.last().kind)
        assertEquals(3, shape.days.size)
    }

    @Test
    fun `cardio machines are only offered to somebody who does cardio`() {
        val everything = listOf("barbell", "dumbbell", "treadmill", "rower")

        assertFalse(
            SetupAnswers(cardio = CardioPlacement.NONE).equipmentOffered(everything)
                .any { it in CARDIO_EQUIPMENT },
        )
        assertTrue(SetupAnswers(cardio = null).equipmentOffered(everything).none { it in CARDIO_EQUIPMENT })
        assertEquals(everything, SetupAnswers(cardio = CardioPlacement.OWN_DAY).equipmentOffered(everything))
    }

    @Test
    fun `a cardio machine ticked before cardio was turned off is not built with`() {
        // Going back and turning cardio off after ticking a rower hides the rower; building with
        // it anyway would be the app keeping an answer to a question it stopped asking.
        val answers = SetupAnswers(cardio = CardioPlacement.NONE, equipment = setOf("barbell", "rower"))

        assertEquals(setOf("barbell"), answers.toRoutineSetup().equipment)
    }
}
