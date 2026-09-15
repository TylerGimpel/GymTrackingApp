package com.tylergimpel.gymtracker.domain

/**
 * A weight as a lifter would write it in [unit]: 60 rather than 60.0, but 62.5 kept whole.
 *
 * Used both to show a logged set and to seed the field that edits one, so a set does not change
 * appearance the moment it is opened for correction — and so toggling a unit rewrites the field to
 * exactly the number the screen was already showing beside it.
 */
fun Kilograms.asTextIn(unit: WeightUnit): String = unit.fromKilograms(this).asWeightText()

/** The same rule for an amount already read in a unit, which is what a stepper produces. */
fun Double.asWeightText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
