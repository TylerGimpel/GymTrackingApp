package com.tylergimpel.gymtracker.domain

import java.time.Duration

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
    /**
     * What the lifter themselves reads in — their **Preferred unit**, not this machine's label.
     *
     * Only a distance is read in it. The two are different questions: the gym's lb lat pulldown is
     * labelled in pounds whoever walks up to it, while a distance belongs to the person reading it,
     * and a treadmill somebody once set to pounds must not start reporting miles to a lifter who
     * thinks in kilometres.
     */
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
    /** Seconds held, for an exercise measured in time rather than loaded with a weight. */
    val durationText: String = "",
    /** How far, for an exercise that covers ground. Read in [distanceUnit]. */
    val distanceText: String = "",
    /**
     * A belt, or a machine's help, as a plain magnitude read in [displayUnit].
     *
     * One field for both, because a lifter types the number the belt or the machine says and the
     * exercise already decides which of the two it is. Which way it counts is storage's business,
     * not something anyone types.
     */
    val addedWeightText: String = "",
    /** How hard it felt, 1 to 10, where the lifter says. Never inferred from the numbers. */
    val rpeText: String = "",
    /** Beats per minute, typed in by hand. */
    val heartRateText: String = "",
    /**
     * Seconds rested after this set, typed in.
     *
     * By hand until the rest timer arrives with its own spec, which is what will fill this without
     * anybody typing.
     */
    val restTakenText: String = "",
    /**
     * What the exercise being logged records, which decides what this draft must hold before it is
     * a set and which fields the screen shows.
     *
     * Last, and defaulted, because weight and reps is what all but a handful of exercises record —
     * a draft says which exercise it is for only when that is not the answer.
     */
    val loggingType: LoggingType = LoggingType.WEIGHT_REPS,
) {
    /** The weight entered, or null while it is blank or unreadable. Zero is a real answer. */
    val weight: Kilograms? get() = amount?.let(displayUnit::toKilograms)

    /** The reps entered, or null until they read as a whole positive count. */
    val reps: Int?
        get() = repsText.trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * What a distance typed here is read in.
     *
     * Derived from [lifterUnit] rather than chosen separately, so the two cannot come apart: a
     * lifter reading pounds runs in miles. Nothing stores it, because there is nothing to store
     * that the **Preferred unit** does not already answer.
     */
    val distanceUnit: DistanceUnit get() = DistanceUnit.alongside(lifterUnit)

    /**
     * The belt or the help entered, or null while it is blank or unreadable.
     *
     * Zero is rejected: a belt of nothing is an unweighted set, and help of nothing is not an
     * assisted one. Either way the honest record is the set without the number.
     */
    val addedWeight: Kilograms?
        get() = addedWeightText.asEnteredAmount()?.takeIf { it > 0.0 }?.let(displayUnit::toKilograms)

    /** How hard it felt, or null until it reads as a rating between 1 and 10. */
    val rpe: Double? get() = rpeText.asEnteredAmount()?.takeIf { it in RPE_RANGE }

    /** The heart rate typed, or null until it reads as a plausible one. */
    val heartRate: Int?
        get() = heartRateText.trim().toIntOrNull()?.takeIf { it in HEART_RATE_RANGE }

    /** The rest typed, or null until it reads as a length of time. */
    val restTaken: Duration?
        get() = restTakenText.trim().toIntOrNull()?.takeIf { it > 0 }
            ?.let { Duration.ofSeconds(it.toLong()) }

    /**
     * Whether a field holds something that will not be recorded.
     *
     * A lifter who types 11 into a 1-to-10 field has said something the log cannot keep, and
     * silently dropping it is worse than saying so: they would believe it was written down.
     */
    val rpeIsUnreadable: Boolean get() = rpeText.isNotBlank() && rpe == null

    val heartRateIsUnreadable: Boolean get() = heartRateText.isNotBlank() && heartRate == null

    /** How far entered, or null while it is blank, unreadable, or nowhere at all. */
    val distance: Metres?
        get() = distanceText.asEnteredAmount()?.takeIf { it > 0.0 }?.let(distanceUnit::toMetres)

    /**
     * The time held, or null until it reads as one. Nobody held a plank for no time at all.
     *
     * Read as an `Int` of seconds rather than a `Long`, because that is what the column holds: a
     * number too large to store should refuse here, where the field is, rather than overflow
     * quietly on the way into the database.
     */
    val duration: Duration?
        get() = durationText.trim().toIntOrNull()?.takeIf { it > 0 }
            ?.let { Duration.ofSeconds(it.toLong()) }

    /**
     * What this draft would record, or null while it does not yet amount to a set.
     *
     * Reads only the fields its [loggingType] records: a weight left over from the previous
     * exercise cannot follow the lifter onto a plank and be written down as something they lifted.
     */
    val performance: Performance?
        get() = when {
            loggingType.recordsDistance && loggingType.recordsDuration ->
                distance?.let { distance -> duration?.let { Performance.DistanceAndTime(distance, it) } }

            loggingType.recordsDuration -> duration?.let(Performance::Timed)

            loggingType.recordsWeight && loggingType.recordsReps ->
                weight?.let { weight -> reps?.let { Performance.WeightAndReps(weight, it) } }

            // The belt is optional — a pull-up is a pull-up without one — so only the reps gate it.
            loggingType.recordsAddedWeight ->
                reps?.let { Performance.BodyweightReps(addedWeight, it) }

            // The help is not optional: how much the machine took is the whole measurement, and an
            // assisted set without it says nothing about whether it was hard.
            loggingType.recordsAssistance ->
                addedWeight?.let { help -> reps?.let { Performance.Assisted(help, it) } }

            // A type whose shape has no home here yet records nothing rather than being filed as
            // the nearest thing that fits. Silently writing a weight for an exercise that does not
            // record one is how a meaningless number ends up in a progression graph.
            else -> null
        }

    val isLoggable: Boolean get() = performance != null

    /** What the lifter measured about this set, as far as the fields read. */
    val measurements: SetMeasurements
        get() = SetMeasurements(rpe = rpe, heartRate = heartRate, restTaken = restTaken)

    private companion object {
        /** The Borg CR10 scale this project uses, so 11 is a mistyping rather than a hard set. */
        val RPE_RANGE = 1.0..10.0

        /**
         * What a human heart can plausibly be doing in a gym.
         *
         * Wide on purpose: this is here to catch a mistyped rep count in the wrong field, not to
         * judge anybody's conditioning.
         */
        val HEART_RATE_RANGE = 30..250
    }

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
            copy(
                displayUnit = to,
                weightText = weight?.asTextIn(to) ?: weightText,
                // The belt and the machine's help are weights too, and a field left reading in the
                // old unit would silently reinterpret what the lifter typed.
                addedWeightText = addedWeight?.asTextIn(to) ?: addedWeightText,
            )
        }

    /**
     * The weight moved by [steps] of the display unit's own step — 2.5 kg or 5 lb.
     *
     * An unreadable field steps from nothing, so the stepper is usable before anything is typed
     * rather than inert until it is.
     */
    fun stepWeight(steps: Int): SetDraft =
        copy(weightText = displayUnit.stepped(amount ?: 0.0, steps).asAmountText())

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
