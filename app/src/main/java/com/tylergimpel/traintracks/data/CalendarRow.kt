package com.tylergimpel.traintracks.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.LocalDate

/**
 * One session in a month, with one exercise it logged a set against — or none, for a session
 * opened and left empty, which is still a row so that the calendar can see it trained nothing.
 */
data class CalendarRow(
    @ColumnInfo(name = "calendar_session_id") val sessionId: Long,
    @ColumnInfo(name = "calendar_date") val date: LocalDate,
    /** The routine day the session realised, where it came from one (#147). */
    @ColumnInfo(name = "calendar_day_name") val routineDayName: String?,
    /** Sets of [exercise] that count as work, warm-ups left out (ADR 0008), or null with none. */
    @ColumnInfo(name = "calendar_sets") val sets: Int?,
    @Embedded val exercise: ExerciseEntity?,
)
