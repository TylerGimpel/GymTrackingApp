package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the lifter has decided about themselves, as opposed to about an exercise.
 *
 * One row, always id [SINGLETON_ID]. A table rather than a key-value store because the things that
 * land here are known in advance and each deserves a column a migration can see: the display unit
 * now, and the sex, age and training experience that cold-start estimation will ask for.
 *
 * Every column is nullable, like `exercise_preference`'s: the row holds only what has actually been
 * chosen, and a lifter who has chosen nothing is answered by a default rather than by a value
 * somebody had to invent for them.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = SINGLETON_ID,

    /**
     * `kg` or `lb` — the unit this lifter reads weights in.
     *
     * Theirs rather than a machine's, which is what makes it profile data: a bathroom scale is read
     * by one person, and the unit a lifter thinks in is the sensible starting point for every
     * exercise they have not said otherwise about. Never what anything is stored in.
     */
    @ColumnInfo(name = "display_unit")
    val displayUnit: String? = null,
) {
    companion object {
        /** There is one lifter, so there is one row, and it has a known id to be written over. */
        const val SINGLETON_ID = 1L
    }
}
