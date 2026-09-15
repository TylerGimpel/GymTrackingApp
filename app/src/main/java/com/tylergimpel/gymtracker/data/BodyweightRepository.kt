package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.BodyweightHistory
import com.tylergimpel.gymtracker.domain.Kilograms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Records what the lifter weighs.
 *
 * Nothing here opens a session, touches an exercise or writes a set: a weigh-in is profile data
 * that shares a calendar with training and nothing else. That is the whole point of it being its
 * own repository over its own table rather than another thing a session can contain.
 */
class BodyweightRepository(
    private val database: GymDatabase,
    private val entries: BodyweightDao,
) {
    fun observeHistory(): Flow<BodyweightHistory> =
        entries.observeAll().map { rows -> BodyweightHistory(rows.map(BodyweightEntryEntity::toDomain)) }

    /**
     * Records [weight] on [date], correcting that day if it has already been weighed.
     *
     * A day holds one weight. Stepping on the scale twice on a Monday is a lifter correcting
     * Monday, not two data points — bodyweight swings a kilogram across a day, and a history with
     * two answers for one day has no way to choose between them.
     */
    suspend fun record(date: LocalDate, weight: Kilograms): Long = database.withTransaction {
        val existing = entries.entryOn(date)
        if (existing == null) {
            entries.insert(BodyweightEntryEntity(date = date, weightKg = weight.value))
        } else {
            entries.update(existing.copy(weightKg = weight.value))
            existing.id
        }
    }

    /**
     * Corrects an entry — the weight, the day, or both.
     *
     * Returns false, changing nothing, where the day being moved onto already holds a different
     * weigh-in. A day holds one weight, so something would have to give, and quietly dropping the
     * weight already recorded there would destroy a reading the lifter never asked to lose — a
     * mistyped date is a common slip, and the record it lands on is the one thing that cannot be
     * recovered by typing again. Correcting that day directly, or deleting one of the two, are both
     * still open; neither of them is silent.
     */
    suspend fun correct(id: Long, date: LocalDate, weight: Kilograms): Boolean =
        database.withTransaction {
            val occupied = entries.entryOn(date)?.let { it.id != id } == true
            if (occupied) return@withTransaction false
            entries.update(BodyweightEntryEntity(id = id, date = date, weightKg = weight.value))
            true
        }

    suspend fun delete(id: Long) = entries.delete(id)

    /**
     * The most recent weigh-in, for cold-start estimation to read.
     *
     * Reaches for the whole history and asks it, rather than ordering in SQL, so that "the most
     * recent bodyweight" means the same thing here as it does on screen.
     */
    suspend fun latest(): BodyweightEntry? =
        BodyweightHistory(entries.all().map(BodyweightEntryEntity::toDomain)).latest
}
