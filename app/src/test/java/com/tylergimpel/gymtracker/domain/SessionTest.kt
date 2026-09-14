package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A session is one logged trip to the gym. It takes the date it started on, which is the whole of
 * the midnight rule: a late session belongs to the day the lifter walked in, not the day they
 * walked out.
 */
class SessionTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")

    @Test
    fun `a session takes the local date it started on`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertEquals(LocalDate.of(2026, 9, 14), session.date)
    }

    @Test
    fun `a session that runs past midnight keeps the date it started on`() {
        val session = Session.start(at("2026-09-14T23:40"), zone)
            .finish(at("2026-09-15T00:20"))

        assertEquals(LocalDate.of(2026, 9, 14), session.date)
    }

    @Test
    fun `a session is active until it is finished`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertTrue(session.isActive)
        assertFalse(session.finish(at("2026-09-14T19:45")).isActive)
    }

    @Test
    fun `a session date can be corrected without moving when it was trained`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)

        val corrected = session.on(LocalDate.of(2026, 9, 13))

        assertEquals(LocalDate.of(2026, 9, 13), corrected.date)
        assertEquals(session.startedAt, corrected.startedAt)
    }

    @Test
    fun `correcting the date of a finished session leaves it finished`() {
        val session = Session.start(at("2026-09-14T23:40"), zone)
            .finish(at("2026-09-15T00:20"))

        val corrected = session.on(LocalDate.of(2026, 9, 13))

        assertFalse(corrected.isActive)
        assertEquals(session.finishedAt, corrected.finishedAt)
    }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
}
