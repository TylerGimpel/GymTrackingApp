package com.tylergimpel.gymtracker.data

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
    @Embedded val exercise: ExerciseEntity?,
)
