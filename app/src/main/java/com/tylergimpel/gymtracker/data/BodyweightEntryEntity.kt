package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.Kilograms
import java.time.Instant
import java.time.LocalDate

/**
 * One weigh-in: a day, and what the lifter weighed on it.
 *
 * **Deliberately no foreign key, and deliberately no session.** A weigh-in is profile data that
 * happens to fall on the calendar; it is not part of any session, and there is nothing here for a
 * set or a volume query to reach — those read `set_entry` through `session_exercise`, and this
 * table is not on that path at all. Keeping it off that path is structural rather than a matter of
 * writing careful queries later.
 *
 * A day may hold several readings, each with the moment it was taken. Bodyweight swings a kilogram
 * or two between morning and evening, so a lifter who weighed themselves twice measured that swing;
 * `recorded_at` is what tells the two apart and orders them.
 *
 * `weight_kg` is canonical kilograms, like every other weight stored in this app.
 */
@Entity(tableName = "bodyweight_entry")
data class BodyweightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** When the reading was taken. Stored as a moment, like every other instant in this schema. */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Instant,

    /** The day it is filed on, which a lifter can correct without moving when it was taken. */
    @ColumnInfo(name = "date")
    val date: LocalDate,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
)

fun BodyweightEntryEntity.toDomain() = BodyweightEntry(
    id = id,
    recordedAt = recordedAt,
    date = date,
    weight = Kilograms(weightKg),
)
