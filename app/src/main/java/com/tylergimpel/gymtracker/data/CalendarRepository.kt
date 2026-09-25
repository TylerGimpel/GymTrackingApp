package com.tylergimpel.gymtracker.data

import com.tylergimpel.gymtracker.domain.CalendarDay
import com.tylergimpel.gymtracker.domain.CalendarSession
import com.tylergimpel.gymtracker.domain.calendarDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/** The calendar of what the lifter did, a month at a time (#118). */
class CalendarRepository(private val database: GymDatabase) {

    /** The marked days of [month]. Unmarked days are absent. */
    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, CalendarDay>> = combine(
        database.sessionDao().observeCalendar(month.atDay(1), month.atEndOfMonth()),
        database.bodyweightDao().observeDaysBetween(month.atDay(1), month.atEndOfMonth()),
    ) { rows, weighIns ->
        val sessions = rows.groupBy(CalendarRow::sessionId).values.map { session ->
            CalendarSession(
                date = session.first().date,
                trained = session.mapNotNull { it.exercise?.toDomain(preference = null) },
            )
        }
        calendarDays(sessions, weighIns)
    }

    /** The first month with anything to mark, which is as far back as the calendar pages. */
    suspend fun firstMonth(): YearMonth? =
        listOfNotNull(database.sessionDao().firstDay(), database.bodyweightDao().firstDay())
            .minOrNull()
            ?.let(YearMonth::from)
}
