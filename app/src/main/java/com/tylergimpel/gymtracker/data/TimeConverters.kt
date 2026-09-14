package com.tylergimpel.gymtracker.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Moments are stored as epoch milliseconds and dates as ISO-8601 text.
 *
 * A moment is an absolute point in time, so it stores as a number; a session's date is a calendar
 * day a lifter can correct, so it stores as the day itself rather than as a moment that a timezone
 * could shift onto the day before.
 */
class TimeConverters {

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toLocalDate(isoDate: String?): LocalDate? = isoDate?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()
}
