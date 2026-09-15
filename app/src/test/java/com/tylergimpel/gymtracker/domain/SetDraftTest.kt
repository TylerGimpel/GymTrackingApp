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
 * and a rep count. This is where one becomes the other, so it is also where junk is stopped — and
 * where the unit a lifter is reading turns a typed number into a weight.
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

    @Test
    fun `a weight typed in pounds is a weight in kilograms`() {
        val draft = SetDraft("225", "5", displayUnit = WeightUnit.POUNDS)

        assertEquals(102.05828325, draft.weight?.value!!, 1e-9)
    }

    @Test
    fun `reading the same draft in another unit rewrites the number, not the weight`() {
        val kilograms = SetDraft("100", "5")

        val pounds = kilograms.readIn(WeightUnit.POUNDS)

        assertEquals("220.46", pounds.weightText)
        // What the lifter has in their hands has not moved, so neither has what would be stored.
        assertEquals(100.0, pounds.weight?.value!!, 0.01)
    }

    @Test
    fun `a blank weight stays blank when the unit changes`() {
        // Nothing has been typed, so there is nothing to convert — putting a 0 in the field here
        // would be the app entering a weight on the lifter's behalf.
        assertEquals("", SetDraft("", "5").readIn(WeightUnit.POUNDS).weightText)
    }

    @Test
    fun `reading in the unit already chosen leaves the draft exactly as typed`() {
        // Untouched text matters: 62,50 is what someone half way through typing has, and rewriting
        // it to 62.5 under them moves the cursor.
        val draft = SetDraft("62,50", "5")

        assertEquals(draft, draft.readIn(WeightUnit.KILOGRAMS))
    }

    @Test
    fun `stepping moves by the display unit's own step`() {
        assertEquals("62.5", SetDraft("60", "5").stepWeight(1).weightText)
        assertEquals("230", SetDraft("225", "5", displayUnit = WeightUnit.POUNDS).stepWeight(1).weightText)
    }

    @Test
    fun `a step in pounds is five pounds, not a converted two and a half kilograms`() {
        val stepped = SetDraft("225", "5", displayUnit = WeightUnit.POUNDS).stepWeight(1)

        // 5 lb is 2.27 kg. A stepper that moved by 2.5 kg would show 230.51 lb and be useless at a
        // machine whose pins are five pounds apart.
        assertEquals(230.0, WeightUnit.POUNDS.fromKilograms(stepped.weight!!), 0.0)
    }

    @Test
    fun `stepping down stops at nothing rather than going negative`() {
        assertEquals("0", SetDraft("2", "5").stepWeight(-1).weightText)
    }

    @Test
    fun `stepping an empty field starts from nothing`() {
        assertEquals("2.5", SetDraft("", "5").stepWeight(1).weightText)
        assertEquals("0", SetDraft("", "5").stepWeight(-1).weightText)
    }

    @Test
    fun `a draft reads in kilograms until it is told otherwise`() {
        assertEquals(WeightUnit.DEFAULT, SetDraft().displayUnit)
    }
}
