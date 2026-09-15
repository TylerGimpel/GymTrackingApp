package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * A duration as the clock a lifter was watching showed it.
 *
 * `1:30` rather than `90`, because a hold is read off a stopwatch and counted in minutes and
 * seconds. The hours place appears only when there is an hour to show, so a plank is never `0:01:30`
 * — and a long piece of cardio is never `65:00`.
 */
fun Duration.asTimeText(): String {
    val hours = toHours()
    val minutes = toMinutes() % MINUTES_PER_HOUR
    val seconds = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
