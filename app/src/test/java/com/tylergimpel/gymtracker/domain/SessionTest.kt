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

    @Test
    fun `a set logged later the same day joins the open session`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertTrue(session.accepts(at("2026-09-14T19:45"), zone))
    }

    @Test
    fun `a set logged just after midnight still joins the session that was running`() {
        val session = Session.start(at("2026-09-14T23:40"), zone)

        assertTrue(session.accepts(at("2026-09-15T00:20"), zone))
    }

    @Test
    fun `a session left unfinished does not swallow a later day's first set`() {
        val tuesday = Session.start(at("2026-09-14T18:30"), zone)

        // Nobody pressed Finish on Tuesday. Thursday's first set is not Tuesday's training, and
        // filing it under Tuesday's date would put work in the log on a day it never happened.
        assertFalse(tuesday.accepts(at("2026-09-17T07:00"), zone))
    }

    @Test
    fun `an unfinished session stops accepting sets once the training day is over`() {
        val session = Session.start(at("2026-09-14T23:40"), zone)

        assertFalse(session.accepts(at("2026-09-15T05:00"), zone))
    }

    @Test
    fun `a finished session accepts nothing further`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)
            .finish(at("2026-09-14T19:45"))

        assertFalse(session.accepts(at("2026-09-14T20:00"), zone))
    }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
}
