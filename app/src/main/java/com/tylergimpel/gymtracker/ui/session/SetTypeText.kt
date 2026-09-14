package com.tylergimpel.gymtracker.ui.session

import androidx.annotation.StringRes
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.SetType

/**
 * How a set type is written for a lifter.
 *
 * The stored values are the model's vocabulary; these are the screen's. The two are kept apart so
 * that renaming one never silently renames the other.
 */
@get:StringRes
internal val SetType.label: Int
    get() = when (this) {
        SetType.WARMUP -> R.string.set_type_warmup
        SetType.WORKING -> R.string.set_type_working
        SetType.CALIBRATION -> R.string.set_type_calibration
        SetType.BACKOFF -> R.string.set_type_backoff
        SetType.AMRAP -> R.string.set_type_amrap
    }
