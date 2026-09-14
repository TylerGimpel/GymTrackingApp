package com.tylergimpel.gymtracker.ui.session

import com.tylergimpel.gymtracker.domain.Kilograms

/**
 * A weight as a lifter would write it: 60 rather than 60.0, but 62.5 kept whole.
 *
 * Used both to show a logged set and to seed the field that edits one, so a set does not change
 * appearance the moment it is opened for correction.
 */
internal fun Kilograms.asText(): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
