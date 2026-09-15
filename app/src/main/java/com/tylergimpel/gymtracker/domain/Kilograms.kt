package com.tylergimpel.gymtracker.domain

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
