package com.tylergimpel.gymtracker.ui.routine

import com.tylergimpel.gymtracker.domain.CARDIO_EQUIPMENT
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.SPLIT_SHAPES
import com.tylergimpel.gymtracker.domain.SplitShape
import com.tylergimpel.gymtracker.domain.splitShapesFor

/**
 * The questions setup asks, in the order it asks them (#86).
 *
 * **Goal → rep preference → cadence → shape → cardio → equipment**, chosen for completion rather
 * than for the app's convenience: the goal first because it is the one question people want to
 * answer, cardio before equipment because the cardio machines are only offered to somebody who
 * does cardio, and equipment last because it is much the longest and people abandon long
 * checklists before they have invested anything.
 */
enum class SetupStep { GOAL, REP_PREFERENCE, CADENCE, SHAPE, CARDIO, EQUIPMENT }

/**
 * Where a lifter is in setup, and what they have said so far.
 *
 * A value rather than a screen, so the flow can be tested without one. **Null is a skipped
 * question**, never a default in disguise: what a skipped question assumes is decided once, in
 * [toRoutineSetup] and [shapeToBuild], and said on screen from the same constants.
 */
data class SetupAnswers(
    val goal: RoutineGoal? = null,
    val repPreference: RepPreference? = null,
    val cadence: Int? = null,
    /** A base shape, without any cardio day; cardio is asked after it and added when building. */
    val shapeId: String? = null,
    val cardio: CardioPlacement? = null,
    val equipment: Set<String>? = null,
    val step: SetupStep = SetupStep.GOAL,
) {
    /** Which question this is, from one, for "3 of 6". */
    val stepNumber: Int get() = step.ordinal + 1

    val isLast: Boolean get() = step == SetupStep.entries.last()

    fun next(): SetupAnswers = copy(step = SetupStep.entries.getOrElse(step.ordinal + 1) { step })

    /** One question back, with every answer kept: a mistake is never a restart. */
    fun back(): SetupAnswers = copy(step = SetupStep.entries.getOrElse(step.ordinal - 1) { step })

    /** Leaves this question unanswered, which is an answer, and moves on. */
    fun skip(): SetupAnswers = when (step) {
        SetupStep.GOAL -> copy(goal = null)
        SetupStep.REP_PREFERENCE -> copy(repPreference = null)
        SetupStep.CADENCE -> copy(cadence = null)
        SetupStep.SHAPE -> copy(shapeId = null)
        SetupStep.CARDIO -> copy(cardio = null)
        SetupStep.EQUIPMENT -> copy(equipment = null)
    }.next()

    /** How often they train, or what the app assumes if they did not say. */
    val resolvedCadence: Int get() = cadence ?: ASSUMED_CADENCE

    private val resolvedCardio: CardioPlacement get() = cardio ?: CardioPlacement.NONE

    /** The shapes worth offering at the SHAPE step, likeliest first, without a cardio day yet. */
    fun shapesOffered(): List<SplitShape> = splitShapesFor(resolvedCadence)

    /**
     * The equipment to offer from [available], which is the bank's own vocabulary.
     *
     * Cardio machines only where cardio is on. A lifter who does none, or has not said, is not
     * asked to scroll past six machines they will never use.
     */
    fun equipmentOffered(available: List<String>): List<String> =
        if (resolvedCardio == CardioPlacement.NONE) available.filterNot { it in CARDIO_EQUIPMENT }
        else available

    /**
     * The routine's properties, from what was said and what each skipped question assumes.
     *
     * The same [RoutineSetup] every path that makes a routine takes, so answering differently here
     * and editing a property later are one and the same thing. A cardio machine ticked before
     * cardio was turned off is dropped: the app does not keep an answer to a question it stopped
     * asking.
     */
    fun toRoutineSetup(): RoutineSetup = RoutineSetup(
        cadence = resolvedCadence,
        cardio = resolvedCardio,
        equipment = equipment.orEmpty()
            .filterNot { resolvedCardio == CardioPlacement.NONE && it in CARDIO_EQUIPMENT }
            .toSet(),
        goal = goal ?: RoutineSetup.ASSUMED_GOAL,
        repPreference = repPreference ?: RoutineSetup.ASSUMED_REP_PREFERENCE,
    )

    /**
     * The shape to lay the routine out from: the one chosen, or the likeliest for their week.
     *
     * With a cardio day of its own on the end where that is where their cardio goes — added here
     * because the shape was chosen before cardio was asked, and a cardio position carries no
     * resistance work, so no shape offered is ever made invalid by it.
     */
    fun shapeToBuild(): SplitShape {
        val base = shapeId?.let { id -> SPLIT_SHAPES.firstOrNull { it.id == id } }
            ?: shapesOffered().first()
        return if (resolvedCardio == CardioPlacement.OWN_DAY) base.withCardioDay() else base
    }

    companion object {
        /** How many questions there are, for "3 of 6". */
        val STEPS: Int = SetupStep.entries.size

        /**
         * How often a lifter who skips the question is assumed to train.
         *
         * Three, which is what setup always started at: the commonest answer, and one every shape
         * can be laid out at.
         */
        const val ASSUMED_CADENCE = 3
    }
}
