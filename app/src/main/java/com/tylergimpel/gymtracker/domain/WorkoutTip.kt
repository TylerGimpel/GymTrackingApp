package com.tylergimpel.gymtracker.domain

/**
 * The workout's hidden gestures, each taught once by a small bubble at the moment it is useful
 * (#145, #156).
 *
 * In the order they come up. Each one is remembered on its own — seen or used — and every gesture
 * keeps a visible way round it, so a tip missed costs nothing: the steppers, the drawer button and
 * the exercise's own Settings are all on screen anyway.
 */
enum class WorkoutTip {
    /** Tapping a number types it: at the first set's number, when the workout opens. */
    TAP_TO_TYPE,

    /** Tapping the unit switches kg and lb: at the unit, once the first set is down. */
    SWITCH_UNIT,

    /** A sideways swipe opens the whole workout: once the first exercise is done and the next begins. */
    SWIPE_FOR_WORKOUT,
}

/** Where the workout is, as far as the tips are concerned. */
data class TipMoment(
    /** An exercise is in front of the lifter, with its numbers to point at. */
    val exerciseOpen: Boolean,
    /** Any set is down in this session. */
    val setLogged: Boolean,
    /** The set about to be logged shows a kg or lb to tap; a plank has none. */
    val unitShown: Boolean,
    /** The lifter has moved on from an exercise they logged to another one. */
    val movedOn: Boolean,
)

/**
 * The tip to show now, if any: the first one not yet seen or used whose moment has come.
 *
 * One at a time, so a tip still showing holds the next back rather than two bubbles competing for
 * one glance. A gesture used before its tip came counts as done, so it is never taught at all.
 */
fun workoutTip(done: Set<WorkoutTip>, moment: TipMoment): WorkoutTip? {
    if (!moment.exerciseOpen) return null
    return WorkoutTip.entries.firstOrNull { tip ->
        tip !in done && when (tip) {
            WorkoutTip.TAP_TO_TYPE -> true
            WorkoutTip.SWITCH_UNIT -> moment.setLogged && moment.unitShown
            WorkoutTip.SWIPE_FOR_WORKOUT -> moment.movedOn
        }
    }
}
