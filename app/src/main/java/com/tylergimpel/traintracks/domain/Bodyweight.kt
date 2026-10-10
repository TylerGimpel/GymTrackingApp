package com.tylergimpel.traintracks.domain

import java.time.Instant
import java.time.LocalDate

/**
 * What the lifter weighed, and when they weighed it.
 *
 * Profile data rather than training. It sits on the same calendar as the sessions and belongs to
 * none of them: there is no session here, no exercise and no set index, which is what keeps a scale
 * reading out of volume and out of progression rather than merely filtered from them. Conflating
 * the two is the mistake this type exists to make impossible.
 *
 * [weight] is canonical kilograms, like every other weight in the app — what a lifter reads it in
 * is a display question answered by [WeightUnit], so a lifter who changes scales keeps one
 * continuous line rather than a step change the week they moved.
 */
data class BodyweightEntry(
    val id: Long,
    /**
     * The moment this reading was recorded.
     *
     * What makes a second weigh-in on a day a second reading rather than a correction of the first.
     * Bodyweight swings a kilogram or two between morning and evening, and a lifter who steps on
     * the scale twice has measured that swing — recording only the last of them would throw away
     * the more useful of the two readings and call it tidiness.
     */
    val recordedAt: Instant,
    /**
     * The day this weigh-in is filed on, stamped from [recordedAt] and editable afterwards.
     *
     * Held as its own value rather than derived on read, for the reason a session's is: a lifter
     * may correct it, having weighed themselves on Saturday and typed it in on Monday, and a day
     * derived from an instant moves under a lifter who travels.
     */
    val date: LocalDate,
    val weight: Kilograms,
)

/**
 * Every bodyweight recorded, in whatever order it came out of storage.
 *
 * Ordering and "the latest" live here rather than in a query so that the rule has one
 * implementation: the most recent weight is the most recent *reading on the most recent day*, which
 * is not the same as the row written last — a lifter filling in a missed weigh-in today has not
 * lost weight since this morning.
 */
data class BodyweightHistory(val entries: List<BodyweightEntry> = emptyList()) {

    /**
     * The history as it is read: newest day first, and within a day the latest reading first.
     *
     * The day leads rather than the moment, because a weigh-in backfilled onto last Saturday is
     * Saturday's however recently it was typed.
     */
    val mostRecentFirst: List<BodyweightEntry> get() = entries.sortedWith(NEWEST_FIRST)

    /**
     * The most recent weigh-in, or null for a lifter who has never recorded one.
     *
     * What cold-start estimation will ask for: an estimate that has to assume a bodyweight is worse
     * than one that knows it, and null is the honest answer rather than an invented figure.
     */
    val latest: BodyweightEntry? get() = mostRecentFirst.firstOrNull()

    /**
     * The days holding more than one reading.
     *
     * What lets the history show a time only where a time distinguishes anything. A date alone is
     * how a lifter thinks of a weigh-in, and stamping every row with 07:14 to serve the rare day
     * that was weighed twice would make every other row noisier to read.
     */
    val daysWeighedSeveralTimes: Set<LocalDate>
        get() = entries.groupingBy(BodyweightEntry::date).eachCount()
            .filterValues { it > 1 }
            .keys

    private companion object {
        val NEWEST_FIRST: Comparator<BodyweightEntry> =
            compareByDescending(BodyweightEntry::date)
                .thenByDescending(BodyweightEntry::recordedAt)
    }
}

/**
 * A weigh-in part-way through being entered: what the screen holds, and what it means.
 *
 * The same split as [SetDraft] — raw text here, so the screen stays a field and a day while the
 * rules about what counts as a bodyweight stay testable. What reaches storage is canonical
 * kilograms whatever [displayUnit] says; the unit decides what the number typed meant, never what
 * is written down.
 *
 * There is deliberately no moment here. When a reading was recorded is the clock's answer, taken as
 * it is written, and not something a half-finished field carries around.
 */
data class BodyweightDraft(
    val weightText: String = "",
    /** The day being recorded. Today for a new entry, and whatever a lifter corrects it to. */
    val date: LocalDate,
    /** The unit the scale in front of the lifter is labelled in. */
    val displayUnit: WeightUnit = WeightUnit.DEFAULT,
) {
    /**
     * The weight entered, or null while it is blank, unreadable or not a weight a body can be.
     *
     * Zero is rejected here, deliberately unlike a set where it is a real answer — an unloaded bar
     * and a bodyweight dip both genuinely weigh nothing added. Nobody weighs nothing, so a stray
     * zero is a mistyping, and recorded as a reading it would drag the graph and every later
     * estimate built on it to the floor.
     */
    val weight: Kilograms? get() = amount?.let(displayUnit::toKilograms)

    val isRecordable: Boolean get() = weight != null

    /**
     * The same weigh-in read in [to]: the number is rewritten, what it weighs is not.
     *
     * Text that does not read as a weight is left exactly as typed, blank included. There is
     * nothing to convert, and completing it would be the app recording a weight nobody stood on a
     * scale for.
     */
    fun readIn(to: WeightUnit): BodyweightDraft =
        if (to == displayUnit) {
            this
        } else {
            copy(displayUnit = to, weightText = weight?.asTextIn(to) ?: weightText)
        }

    /** The number typed, read in [displayUnit] and before it is turned into a weight. */
    private val amount: Double? get() = weightText.asEnteredAmount()?.takeIf { it > 0.0 }
}
