package com.tylergimpel.traintracks.ui.routine

import com.tylergimpel.traintracks.domain.CARDIO_EQUIPMENT
import com.tylergimpel.traintracks.domain.CardioPlacement
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.SPLIT_SHAPES
import com.tylergimpel.traintracks.domain.SplitShape
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.splitShapesFor

/**
 * The questions setup asks, one to a page, in the order it asks them (#86, #144).
 *
 * **Kilos or pounds → goal → rep style → days a week → split → cardio → equipment.** The unit first
 * because it is the quickest and says the app is theirs; cardio before equipment because the cardio
 * machines are only offered to somebody who does cardio; equipment last because it is the longest.
 */
enum class SetupStep { UNIT, GOAL, REP_PREFERENCE, CADENCE, SHAPE, CARDIO, EQUIPMENT }

/**
 * The lifting equipment setup offers, in the order its tiles are laid out.
 *
 * No bodyweight tile, because bodyweight is always included, and no "other", which nobody can
 * answer. The bank's own vocabulary, so what is ticked is what the generator reads.
 */
val SETUP_LIFTING_EQUIPMENT: List<String> = listOf(
    "barbell", "dumbbell", "cable", "machine", "e-z curl bar", "kettlebells",
    "bands", "medicine ball", "exercise ball", "foam roll",
)

/** The cardio equipment setup offers, only to somebody who does cardio. */
val SETUP_CARDIO_EQUIPMENT: List<String> = listOf(
    "treadmill", "stationary bike", "rower", "elliptical", "stair climber", "outdoor",
)

/**
 * Where a lifter is in setup, and what they have said so far.
 *
 * A value rather than a screen, so the flow can be tested without one. **Every answer starts as the
 * usual one** (#135): the page shows it picked, so Next always works and nothing needs skipping.
 * What those usual answers are is decided once, here, and they are the same values a question left
 * unanswered used to assume.
 */
data class SetupAnswers(
    val unit: WeightUnit = WeightUnit.DEFAULT,
    val goal: RoutineGoal = RoutineSetup.ASSUMED_GOAL,
    val repPreference: RepPreference = RoutineSetup.ASSUMED_REP_PREFERENCE,
    val cadence: Int = USUAL_CADENCE,
    /**
     * A base shape, without any cardio day, or null for the best fit for [cadence]: the pick that
     * follows the week until the lifter chooses one of their own.
     */
    val shapeId: String? = null,
    val cardio: CardioPlacement = CardioPlacement.NONE,
    /** What is ticked. A full gym to start with, lifting and cardio both. */
    val equipment: Set<String> = (SETUP_LIFTING_EQUIPMENT + SETUP_CARDIO_EQUIPMENT).toSet(),
    val step: SetupStep = SetupStep.UNIT,
) {
    /** Which question this is, from one, for "2 / 7". */
    val stepNumber: Int get() = step.ordinal + 1

    val isLast: Boolean get() = step == SetupStep.entries.last()

    fun next(): SetupAnswers = copy(step = SetupStep.entries.getOrElse(step.ordinal + 1) { step })

    /** One question back, with every answer kept: a mistake is never a restart. */
    fun back(): SetupAnswers = copy(step = SetupStep.entries.getOrElse(step.ordinal - 1) { step })

    /** A new week moves the split back to the best fit for it (#135). */
    fun withCadence(days: Int): SetupAnswers = copy(cadence = days, shapeId = null)

    /** Every shape, the best fit for their week first, without a cardio day yet. */
    fun shapesOffered(): List<SplitShape> = splitShapesFor(cadence)

    /** The base shape picked: the lifter's own, or the best fit for their week. */
    val shape: SplitShape
        get() = shapeId?.let { id -> SPLIT_SHAPES.firstOrNull { it.id == id } } ?: shapesOffered().first()

    /** The equipment tiles to show: cardio machines only where cardio is on. */
    val equipmentOffered: List<String>
        get() = if (cardio == CardioPlacement.NONE) SETUP_LIFTING_EQUIPMENT else SETUP_LIFTING_EQUIPMENT + SETUP_CARDIO_EQUIPMENT

    fun toggle(item: String): SetupAnswers =
        copy(equipment = if (item in equipment) equipment - item else equipment + item)

    fun withAllEquipment(): SetupAnswers = copy(equipment = equipment + equipmentOffered)

    fun withNoEquipment(): SetupAnswers = copy(equipment = equipment - equipmentOffered.toSet())

    /** Whether everything offered is ticked: a full gym. */
    val fullGym: Boolean get() = equipment.containsAll(equipmentOffered)

    /**
     * The routine's properties, from what was answered.
     *
     * The same [RoutineSetup] every path that makes a routine takes, so answering differently here
     * and editing a property later are one and the same thing. **A full gym is the empty set**,
     * which is what the rest of the app reads as "nobody has narrowed it" and offers the whole bank
     * for, the odd machines included. Otherwise bodyweight is always in, and a cardio machine
     * ticked before cardio was turned off is dropped: the app does not keep an answer to a
     * question it stopped asking.
     */
    fun toRoutineSetup(): RoutineSetup = RoutineSetup(
        cadence = cadence,
        cardio = cardio,
        equipment = if (fullGym) {
            emptySet()
        } else {
            equipment.filterNot { cardio == CardioPlacement.NONE && it in CARDIO_EQUIPMENT }.toSet() + BODYWEIGHT
        },
        goal = goal,
        repPreference = repPreference,
    )

    /**
     * The shape to lay the routine out from, with a cardio day of its own on the end where that is
     * where their cardio goes — added here because the shape was chosen before cardio was asked,
     * and a cardio position carries no resistance work, so no shape is ever made invalid by it.
     */
    fun shapeToBuild(): SplitShape = if (cardio == CardioPlacement.OWN_DAY) shape.withCardioDay() else shape

    companion object {
        /** How many questions there are, for "2 / 7". */
        val STEPS: Int = SetupStep.entries.size

        /** How often a lifter is assumed to train until they say: the commonest answer. */
        const val USUAL_CADENCE = 3

        /** The bank's word for needing nothing, which a narrowed gym always has. */
        const val BODYWEIGHT = "body only"
    }
}
