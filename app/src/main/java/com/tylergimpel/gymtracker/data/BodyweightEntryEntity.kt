package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.Kilograms
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
 * `date` is unique. A day holds one weight, so weighing again on a day corrects that day rather
 * than adding a second reading for it — see [BodyweightEntry].
 *
 * `weight_kg` is canonical kilograms, like every other weight stored in this app.
 */
@Entity(
    tableName = "bodyweight_entry",
    indices = [Index(value = ["date"], unique = true)],
)
data class BodyweightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: LocalDate,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
)

fun BodyweightEntryEntity.toDomain() = BodyweightEntry(
    id = id,
    date = date,
    weight = Kilograms(weightKg),
)
