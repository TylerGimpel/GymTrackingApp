package com.tylergimpel.gymtracker.data

import androidx.room.TypeConverter

/**
 * Stores a list of muscles in one column.
 *
 * The separator is a vertical bar rather than a comma because the muscle vocabulary is closed and
 * contains neither — but a comma is the character a future value is most likely to contain. The
 * build step asserts the same invariant before it writes the asset, so the two halves cannot drift.
 */
class StringListConverter {

    @TypeConverter
    fun fromStoredValue(stored: String): List<String> =
        if (stored.isEmpty()) emptyList() else stored.split(DELIMITER)

    @TypeConverter
    fun toStoredValue(values: List<String>): String = values.joinToString(DELIMITER)

    private companion object {
        const val DELIMITER = "|"
    }
}
