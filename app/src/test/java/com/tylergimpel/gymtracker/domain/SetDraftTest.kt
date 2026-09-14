package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the logging screen has typed so far, and what it means.
 *
 * The screen holds text; the set that reaches the database holds a weight in canonical kilograms
 * and a rep count. This is where one becomes the other, so it is also where junk is stopped.
 */
class SetDraftTest {

    @Test
    fun `a whole number of kilograms is read as typed`() {
        assertEquals(60.0, SetDraft("60", "5").weight?.value)
    }

    @Test
    fun `a fractional weight survives, because plates come in halves`() {
        assertEquals(62.5, SetDraft("62.5", "5").weight?.value)
    }

    @Test
    fun `a comma is accepted as a decimal point`() {
        // Several locales put a comma on the number pad, and a lifter typing 62,5 means 62.5 rather
        // than sixty-two.
        assertEquals(62.5, SetDraft("62,5", "5").weight?.value)
    }

    @Test
    fun `surrounding whitespace does not change the weight`() {
        assertEquals(62.5, SetDraft("  62.5 ", "5").weight?.value)
    }

    @Test
    fun `a bodyweight set may weigh nothing`() {
        assertEquals(0.0, SetDraft("0", "8").weight?.value)
        assertTrue(SetDraft("0", "8").isLoggable)
    }

    @Test
    fun `a weight that is not a number is not a weight`() {
        assertNull(SetDraft("", "5").weight)
        assertNull(SetDraft("heavy", "5").weight)
        assertNull(SetDraft("-5", "5").weight)
    }

    @Test
    fun `reps are whole and positive`() {
        assertEquals(5, SetDraft("60", "5").reps)
        assertNull(SetDraft("60", "0").reps)
        assertNull(SetDraft("60", "-3").reps)
        assertNull(SetDraft("60", "3.5").reps)
        assertNull(SetDraft("60", "").reps)
    }

    @Test
    fun `a set is loggable only once both halves read`() {
        assertTrue(SetDraft("60", "5").isLoggable)
        assertFalse(SetDraft("60", "").isLoggable)
        assertFalse(SetDraft("", "5").isLoggable)
        assertFalse(SetDraft("", "").isLoggable)
    }
}
