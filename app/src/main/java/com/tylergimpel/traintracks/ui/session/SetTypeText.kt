package com.tylergimpel.traintracks.ui.session

import androidx.annotation.StringRes
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.ProgressionModel
import com.tylergimpel.traintracks.domain.SetType
import com.tylergimpel.traintracks.domain.Side

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

/** How a side is written for a lifter, kept apart from the stored value for the same reason. */
@get:StringRes
internal val Side.label: Int
    get() = when (this) {
        Side.LEFT -> R.string.set_side_left
        Side.RIGHT -> R.string.set_side_right
    }

/**
 * How a progression model is written for a lifter.
 *
 * "Progress by reps, then weight" rather than the stored `double_progression`: a lifter is choosing
 * what the app does for them, not naming a scheme.
 */
@get:StringRes
internal val ProgressionModel.label: Int
    get() = when (this) {
        ProgressionModel.DOUBLE_PROGRESSION -> R.string.progression_double
        ProgressionModel.NONE -> R.string.progression_none
    }
