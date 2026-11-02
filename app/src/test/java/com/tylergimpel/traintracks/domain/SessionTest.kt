package com.tylergimpel.traintracks.domain

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
    fun `a session needs no routine to start, to be trained or to finish`() {
        // "Let me just try it" is the most common first move in any app (#29). A session usually
        // realises a routine day and does not have to: nothing here asks for one, and finishing
        // without one is an ordinary ending rather than an error (#85).
        val tried = Session.start(at("2026-09-14T18:30"), zone)

        assertEquals(null, tried.routineName)
        assertEquals(null, tried.routineDayName)

        val finished = tried.finish(at("2026-09-14T19:15"))
        assertFalse(finished.isActive)
        assertFalse(finished.isBackdated(zone))
        assertEquals(null, finished.routineDayName)
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

    @Test
    fun `a session finished earlier today can be reopened, and then takes sets again`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)
            .finish(at("2026-09-14T19:45"))

        assertTrue(session.canReopen(at("2026-09-14T21:00"), zone))
        assertTrue(session.reopen().accepts(at("2026-09-14T21:00"), zone))
    }

    @Test
    fun `a session cannot be reopened once its training day is over`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)
            .finish(at("2026-09-14T19:45"))

        assertFalse(session.canReopen(at("2026-09-15T05:00"), zone))
    }

    @Test
    fun `an imported session cannot be reopened`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)
            .finish(at("2026-09-14T19:45"))
            .copy(importBatchId = 1)

        assertFalse(session.canReopen(at("2026-09-14T21:00"), zone))
    }

    @Test
    fun `a session filed on the day it was trained is not backdated`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertFalse(session.isBackdated(zone))
    }

    @Test
    fun `a session refiled onto an earlier day is backdated`() {
        // Trained on Saturday, remembered on Monday. What happened is Saturday's; what comes next
        // is unaffected, which is the whole of the rule (ADR 0001).
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertTrue(session.on(LocalDate.of(2026, 9, 12)).isBackdated(zone))
    }

    @Test
    fun `a session that ran past midnight is not backdated`() {
        // Its date is the day before it finished, which is the midnight rule doing its job rather
        // than anyone refiling anything.
        val session = Session.start(at("2026-09-14T23:40"), zone)
            .finish(at("2026-09-15T00:20"))

        assertFalse(session.isBackdated(zone))
    }

    @Test
    fun `refiling a session back onto the day it started stops it being backdated`() {
        val session = Session.start(at("2026-09-14T18:30"), zone)
            .on(LocalDate.of(2026, 9, 12))

        assertFalse(session.on(LocalDate.of(2026, 9, 14)).isBackdated(zone))
    }

    @Test
    fun `a session filed forward is backdated too, because it is still not today's training`() {
        // Not a case anyone should reach, but the cursor asks one question — was this session
        // trained on the day it claims — and a date in the future answers it the same way.
        val session = Session.start(at("2026-09-14T18:30"), zone)

        assertTrue(session.on(LocalDate.of(2026, 9, 16)).isBackdated(zone))
    }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
}
