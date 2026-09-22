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

    /**
     * What the lifter's barbell weighs, empty.
     *
     * A fact about the room rather than about them, and here because the room has exactly one of
     * everything this app knows about: one lifter, one set of plates, one bar. A gym worth
     * modelling separately is a gym somebody trains at *as well as* another one, and nothing in
     * the app can express that yet.
     *
     * Twenty kilograms or forty-five pounds are different bars rather than two spellings of one
     * — which is the whole reason this is stored rather than converted from a constant.
     */
    /**
     * How much training this lifter has behind them.
     *
     * Theirs rather than any routine's, which is why it sits here with their preferred unit. Only
     * one rule reads it — whether to steer somebody away from high-rep work they would have to
     * judge proximity to failure for — and "trained" is the assumption until they say otherwise,
     * because it is the one that overrules nothing.
     */
    @ColumnInfo(name = "experience", defaultValue = "TRAINED")
    val experience: String = "TRAINED",

    @ColumnInfo(name = "bar_kg")
    val barKg: Double? = null,
) {
    companion object {
        /** There is one lifter, so there is one row, and it has a known id to be written over. */
        const val SINGLETON_ID = 1L
    }
}
