package com.tylergimpel.gymtracker.domain

/**
 * A set part-way through being entered: what the screen currently holds, and what it means.
 *
 * Keeping the raw text here rather than parsed values lets the screen stay a dumb pair of fields
 * while the rules about what counts as a weight live somewhere they can be tested. What reaches
 * storage is canonical kilograms whatever [displayUnit] says — it decides what the typed
 * means, never what is written down.
 */
data class SetDraft(
    val weightText: String = "",
    val repsText: String = "",
    val note: String? = null,
    /**
     * Defaults to working, because most sets are. It is a choice the lifter makes rather than
     * something the app infers from the numbers.
     */
    val type: SetType = SetType.WORKING,
    /**
     * The unit [weightText] is being read in, which is the exercise's unless this one set departs
     * from it — the machine in the corner labelled in pounds among a gym of kilogram ones.
     */
    val displayUnit: WeightUnit = WeightUnit.DEFAULT,
) {
    /** The weight entered, or null while it is blank or unreadable. Zero is a real answer. */
    val weight: Kilograms? get() = amount?.let(displayUnit::toKilograms)

    /** The reps entered, or null until they read as a whole positive count. */
    val reps: Int?
        get() = repsText.trim().toIntOrNull()?.takeIf { it > 0 }

    val isLoggable: Boolean get() = weight != null && reps != null

    /**
     * The same set read in [to]: the number is rewritten, what it weighs is not.
     *
     * Text that does not read as a weight is left exactly as it is, blank included. There is
     * nothing to convert, and filling the field in on the lifter's behalf would be the app entering
     * a weight nobody lifted.
     */
    fun readIn(to: WeightUnit): SetDraft =
        if (to == displayUnit) {
            this
        } else {
            copy(displayUnit = to, weightText = weight?.asTextIn(to) ?: weightText)
        }

    /**
     * The weight moved by [steps] of the display unit's own step — 2.5 kg or 5 lb.
     *
     * An unreadable field steps from nothing, so the stepper is usable before anything is typed
     * rather than inert until it is.
     */
    fun stepWeight(steps: Int): SetDraft =
        copy(weightText = displayUnit.stepped(amount ?: 0.0, steps).asWeightText())

    /**
     * The number typed, read in [displayUnit] and before it is turned into a weight.
     *
     * Zero is kept, unlike a bodyweight: an unloaded bar and a bodyweight dip both genuinely weigh
     * nothing added, and that is the only thing that differs between the two readings.
     */
    private val amount: Double? get() = weightText.asEnteredAmount()?.takeIf { it >= 0.0 }
}

/**
 * A typed number read as an amount of whatever unit the field is labelled in, or null where it does
 * not read as one at all.
 *
 * Shared by every field a weight is entered into, because what counts as a number is not a property
 * of what is being weighed: a comma is a decimal point to half the world, a stray space is a slip
 * rather than an answer, and infinity is not a reading. What each field then does with the number —
 * a set allows zero, a bodyweight does not — is the field's own rule and stays with it.
 */
internal fun String.asEnteredAmount(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
