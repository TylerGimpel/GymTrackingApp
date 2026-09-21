package com.tylergimpel.gymtracker.domain

import kotlin.math.abs

/**
 * A weight in kilograms, the canonical unit everything is stored in.
 *
 * Storage is canonical so that one gym's kg lat pulldown and another's lb machine can both be
 * logged honestly without putting a false step change in the progress graph. What a lifter reads is
 * a separate question, answered by [WeightUnit] — and a reading never reaches this type without
 * being converted first.
 */
@JvmInline
value class Kilograms(val value: Double)

/**
 * Whether two loads are the same weight as far as a lifter is concerned.
 *
 * Never an exact comparison. A reading is rounded to a hundredth of the unit it was entered in, so
 * a weight typed in pounds comes back a few thousandths of a kilogram from where it started, and a
 * lifter who loaded exactly what they were asked must not read as having done something else. The
 * tolerance belongs to [WeightUnit], which is what does the rounding that makes it necessary.
 */
fun Kilograms.isSameLoadAs(other: Kilograms): Boolean =
    abs(value - other.value) < WeightUnit.SAME_WEIGHT_TOLERANCE_KG

/**
 * This load moved by a [fraction] of its own magnitude: positive is harder, negative is easier.
 *
 * **By magnitude rather than by value**, which is the whole point. An assisted machine's load
 * counts upward from below zero, because help is weight taken off the lifter — so on that one
 * kind of exercise "ten percent more" and "ten percent of the number" point opposite ways.
 * Shifting by `abs(value)` means a positive fraction is harder work on every exercise there is,
 * and no caller has to remember which side of zero it is standing on (ADR 0004).
 *
 * The engine does this in four places — a substantial beat, a layoff's decay, a calibration rung
 * and the ladder's back-off — and the sign rule is the same one each time.
 */
fun Kilograms.shiftedBy(fraction: Double): Kilograms = Kilograms(value + abs(value) * fraction)
