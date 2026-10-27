package com.tylergimpel.traintracks.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** When Today has to read the clock again, so that it never shows yesterday as today. */
class TodayClockTest {

    private val zone = ZoneId.of("Europe/London")

    @Test
    fun `in the small hours the next turn is the end of the training day`() {
        assertEquals(at("2026-10-27T04:00"), turnAfter("2026-10-27T01:30"))
    }

    @Test
    fun `once the training day has ended the next turn is midnight`() {
        assertEquals(at("2026-10-28T00:00"), turnAfter("2026-10-27T04:00"))
        assertEquals(at("2026-10-28T00:00"), turnAfter("2026-10-27T23:59"))
    }

    @Test
    fun `the end of the training day is four on the wall clock, even the night the clocks go back`() {
        assertEquals(at("2026-10-25T04:00"), turnAfter("2026-10-25T00:30"))
    }

    @Test
    fun `a week runs Monday to Sunday around the day`() {
        val week = weekOf(LocalDate.parse("2026-11-01"))

        assertEquals(LocalDate.parse("2026-10-26"), week.first())
        assertEquals(LocalDate.parse("2026-11-01"), week.last())
        assertEquals(7, week.size)
    }

    private fun turnAfter(localDateTime: String) = nextTurnAfter(at(localDateTime), zone)

    private fun at(localDateTime: String) = LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
}
