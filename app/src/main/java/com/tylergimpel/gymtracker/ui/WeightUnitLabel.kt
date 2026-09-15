package com.tylergimpel.gymtracker.ui

import androidx.annotation.StringRes
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.DistanceUnit
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * How a unit is written for a lifter.
 *
 * Shared rather than a session's: a weight is read in a unit wherever it is shown, and the scale in
 * the bathroom is labelled in one as much as the machine in the gym is.
 *
 * The stored values are the model's vocabulary; these are the screen's, kept apart so that renaming
 * one never silently renames the other. How the number beside it is written lives in the domain,
 * because the field a set is edited in and the row it is read from have to agree.
 */
@get:StringRes
internal val WeightUnit.label: Int
    get() = when (this) {
        WeightUnit.KILOGRAMS -> R.string.weight_unit_kilograms
        WeightUnit.POUNDS -> R.string.weight_unit_pounds
    }

/**
 * How a distance unit is written for a lifter.
 *
 * Beside the weight unit's, because they are read together and chosen together: the lifter who
 * reads `lb` reads `mi`.
 */
@get:StringRes
internal val DistanceUnit.label: Int
    get() = when (this) {
        DistanceUnit.KILOMETRES -> R.string.distance_unit_kilometres
        DistanceUnit.MILES -> R.string.distance_unit_miles
    }
