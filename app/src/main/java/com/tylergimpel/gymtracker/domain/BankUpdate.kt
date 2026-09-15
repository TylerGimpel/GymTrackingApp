package com.tylergimpel.gymtracker.domain

/**
 * One thing an update to the exercise bank does.
 *
 * A plan rather than a set of writes, so the rules can be read and tested without a database — and
 * so that what an update is allowed to do is written down in one place rather than spread through
 * whatever applies it.
 */
sealed interface BankChange {

    /** The exercise this change is about. */
    val id: String

    /** A movement the bank has gained. */
    data class Add(val exercise: Exercise) : BankChange {
        override val id: String get() = exercise.id
    }

    /**
     * A movement the bank has corrected.
     *
     * [exercise] is what it should become: the incoming row's own fields, carrying forward
     * everything the lifter had decided about it.
     */
    data class Replace(val exercise: Exercise) : BankChange {
        override val id: String get() = exercise.id
    }

    /**
     * A movement the bank has dropped that somebody has already trained.
     *
     * It stops being suggested and stays exactly where it is, because the sets logged against it
     * are a record of training that happened.
     */
    data class Tombstone(override val id: String) : BankChange

    /** A movement the bank has dropped that nobody ever trained, so nothing points at it. */
    data class Remove(override val id: String) : BankChange
}

/**
 * What an update from [incoming] does to [current], given which exercises anything points at.
 *
 * Three rules, and the app's promise that improving it never costs a lifter their data rests on all
 * three:
 *
 * - **An exercise the lifter added is never touched.** Not corrected, not dropped, not mentioned.
 *   Shipped rows and the lifter's own live in separate id spaces, and this does not rely on them
 *   staying apart: a collision is refused rather than resolved in the bank's favour.
 * - **A dropped exercise anything points at is tombstoned, never deleted.** Deleting it would
 *   orphan every set recorded against it — and the database refuses outright, which would take the
 *   whole update down rather than just that row. Logged sets are not the only thing that points:
 *   so does a session's plan, and so does a routine day, and an exercise planned but never
 *   performed is precisely the case the plan exists to make visible.
 * - **What the lifter decided survives.** Their name for an exercise, their note and their unit
 *   live in a table an update never writes (ADR 0009), which is what actually protects them. The
 *   corrected row carries them anyway, so that "has this changed" compares like with like.
 *
 * Only what actually changes appears in the plan: a row the bank still ships unaltered produces
 * nothing at all.
 *
 * [isReferenced] answers whether anything at all points at an id. Defaults to nothing, which is
 * the right answer for a bank nobody has trained against or built a routine from yet.
 */
fun planBankUpdate(
    current: List<Exercise>,
    incoming: List<Exercise>,
    isReferenced: (String) -> Boolean = { false },
): List<BankChange> {
    val held = current.associateBy(Exercise::id)
    val arriving = incoming.associateBy(Exercise::id)

    val changed = incoming.mapNotNull { row ->
        val existing = held[row.id] ?: return@mapNotNull BankChange.Add(row)
        // The lifter owns this id. An update that overwrote their own exercise would be the worst
        // outcome here, so the collision is refused rather than settled in the bank's favour.
        if (existing.isUserAuthored) return@mapNotNull null

        // Only what actually moved. A plan listing every row the bank still ships says nothing
        // about what the update does, and is a great deal harder to read when it goes wrong.
        val corrected = row.keepingChoicesOf(existing)
        if (corrected == existing) null else BankChange.Replace(corrected)
    }

    val gone = current
        .filter { !it.isUserAuthored && it.id !in arriving }
        // Already tombstoned and still gone: there is nothing for this update to do about it.
        .filterNot { it.isTombstoned }
        .map { if (isReferenced(it.id)) BankChange.Tombstone(it.id) else BankChange.Remove(it.id) }

    return changed + gone
}
