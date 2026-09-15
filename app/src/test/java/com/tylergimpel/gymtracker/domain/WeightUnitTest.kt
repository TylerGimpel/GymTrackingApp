package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

/**
 * The arithmetic under "the gym has a kg lat pulldown and an lb one".
 *
 * Storage is kilograms and a unit is only how a machine is labelled, so everything here is a
 * reading rather than a value: a weight goes in as what the lifter read off the stack and has to
 * come back out as the same number, or the graph grows a step change nobody lifted.
 */
class WeightUnitTest {

    @Test
    fun `a weight in kilograms reads as itself`() {
        assertEquals(62.5, WeightUnit.KILOGRAMS.fromKilograms(Kilograms(62.5)), 0.0)
    }

    @Test
    fun `a hundred kilograms is two hundred and twenty pounds`() {
        assertEquals(220.46, WeightUnit.POUNDS.fromKilograms(Kilograms(100.0)), 0.0)
    }

    @Test
    fun `a weight read in pounds is stored as the kilograms it actually weighs`() {
        // The international pound, exactly: 225 lb is 102.05828325 kg and nothing else.
        assertEquals(102.05828325, WeightUnit.POUNDS.toKilograms(225.0).value, 1e-9)
    }

    @Test
    fun `a stack read in pounds reads back as the same number of pounds`() {
        val stored = WeightUnit.POUNDS.toKilograms(225.0)

        assertEquals(225.0, WeightUnit.POUNDS.fromKilograms(stored), 0.0)
    }

    @Test
    fun `a weight survives a round trip through the other unit`() {
        val stored = Kilograms(62.5)

        val throughPounds = WeightUnit.POUNDS.toKilograms(WeightUnit.POUNDS.fromKilograms(stored))

        // Not the identical double — a hundredth of a pound is finer than the display shows — but
        // the same weight as far as anything a lifter reads is concerned.
        assertEquals(62.5, WeightUnit.KILOGRAMS.fromKilograms(throughPounds), 0.0)
    }

    @Test
    fun `a reading is rounded to the hundredth, which is finer than any plate`() {
        // 62.5 kg is 137.78855... lb. Showing all of that would be precision the stack does not
        // have; rounding harder than this would make an edited set drift off its stored weight.
        assertEquals(137.79, WeightUnit.POUNDS.fromKilograms(Kilograms(62.5)), 0.0)
    }

    @Test
    fun `each unit steps by what a lifter moves by in it`() {
        // Not a converted step: 5 lb is 2.27 kg, and a stepper that moved by that would put
        // unloadable weights on a barbell every time the unit was toggled.
        assertEquals(2.5, WeightUnit.KILOGRAMS.step, 0.0)
        assertEquals(5.0, WeightUnit.POUNDS.step, 0.0)
    }

    @Test
    fun `the only units are the two a machine is ever labelled in`() {
        // A machine's stack number is its own gym-local scale: pulley ratios vary between
        // manufacturers and are not labelled, so 60 on one machine is not 60 on another and no
        // factor can relate them. There is deliberately nothing here to convert a stack against,
        // and adding one would have to answer that first.
        assertEquals(
            listOf(WeightUnit.KILOGRAMS, WeightUnit.POUNDS),
            WeightUnit.entries.toList(),
        )
    }

    @Test
    fun `a weight reads in kilograms when nothing at all is known`() {
        // The floor rather than the answer: storage is kilograms, so it is the one reading that
        // cannot be wrong about what a number means.
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.DEFAULT)
    }

    @Test
    fun `a lifter is assumed to read in what their country reads in`() {
        assertEquals(WeightUnit.POUNDS, WeightUnit.forLocale(Locale.US))
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.forLocale(Locale.FRANCE))
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.forLocale(Locale.JAPAN))
    }

    @Test
    fun `a British lifter reads in kilograms, whatever they weigh themselves in`() {
        // Bodyweight is spoken in stones there, which this app does not offer; every barbell and
        // stack in a British gym is labelled in kilograms.
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.forLocale(Locale.UK))
    }

    @Test
    fun `a locale naming no country is answered by the unit that cannot be wrong`() {
        // A guess is worth making from a country and worth nothing from a language: Spanish is
        // spoken either side of a unit boundary.
        assertEquals(WeightUnit.DEFAULT, WeightUnit.forLocale(Locale.forLanguageTag("es")))
    }

    @Test
    fun `a stored unit survives a round trip, and an unknown one is not guessed at`() {
        WeightUnit.entries.forEach { assertEquals(it, WeightUnit.fromStored(it.stored)) }
        assertEquals(null, WeightUnit.fromStored(null))
        assertThrows(IllegalArgumentException::class.java) { WeightUnit.fromStored("stone") }
    }

    @Test
    fun `a weight is written as a lifter would write it`() {
        assertEquals("60", Kilograms(60.0).asTextIn(WeightUnit.KILOGRAMS))
        assertEquals("62.5", Kilograms(62.5).asTextIn(WeightUnit.KILOGRAMS))
        assertEquals("225", WeightUnit.POUNDS.toKilograms(225.0).asTextIn(WeightUnit.POUNDS))
    }
}
