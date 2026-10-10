package com.tylergimpel.traintracks.data

import androidx.room.withTransaction
import com.tylergimpel.traintracks.domain.BodyweightEntry
import com.tylergimpel.traintracks.domain.BodyweightHistory
import com.tylergimpel.traintracks.domain.Kilograms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
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
     * Records [weight] as read at [recordedAt] and filed on [date].
     *
     * Always a new reading, never an overwrite. A lifter who steps on the scale twice on a Monday
     * has measured the swing between morning and evening rather than corrected themselves, and
     * keeping only the last of the two would throw away the more useful reading.
     */
    suspend fun record(recordedAt: Instant, date: LocalDate, weight: Kilograms): Long =
        entries.insert(
            BodyweightEntryEntity(recordedAt = recordedAt, date = date, weightKg = weight.value),
        )

    /**
     * Corrects a weigh-in — the weight, the day it is filed on, or both.
     *
     * When it was taken is untouched, exactly as correcting a session's date leaves when it was
     * trained alone: the lifter is fixing what they wrote down, not claiming to have stood on the
     * scale at a different moment.
     */
    suspend fun correct(id: Long, date: LocalDate, weight: Kilograms) = database.withTransaction {
        val existing = entries.entry(id) ?: return@withTransaction
        entries.update(existing.copy(date = date, weightKg = weight.value))
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
