package com.tylergimpel.gymtracker.ui.session

import androidx.annotation.StringRes
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * How a unit is written for a lifter.
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
