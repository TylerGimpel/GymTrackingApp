package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the workout's three tips is showing (#145, #156): one at a time, each at the moment it
 * is useful, and none that has been seen or used.
 */
class WorkoutTipTest {

    private val opened = TipMoment(exerciseOpen = true, setLogged = false, unitShown = true, movedOn = false)
    private val logged = opened.copy(setLogged = true)
    private val next = logged.copy(movedOn = true)

    @Test
    fun aFreshWorkoutTeachesTypingFirst() {
        assertEquals(WorkoutTip.TAP_TO_TYPE, workoutTip(done = emptySet(), opened))
    }

    @Test
    fun nothingIsTaughtBeforeThereIsAnExerciseToTeachItOn() {
        assertNull(workoutTip(done = emptySet(), opened.copy(exerciseOpen = false)))
    }

    @Test
    fun theUnitWaitsForTheFirstSet() {
        val typed = setOf(WorkoutTip.TAP_TO_TYPE)

        assertNull(workoutTip(typed, opened))
        assertEquals(WorkoutTip.SWITCH_UNIT, workoutTip(typed, logged))
    }

    @Test
    fun oneAtATimeSoAnEarlierTipStillShowingHoldsTheNextBack() {
        // Logged without typing anything or tapping the bubble: the first tip has not gone yet.
        assertEquals(WorkoutTip.TAP_TO_TYPE, workoutTip(done = emptySet(), next))
    }

    @Test
    fun theSwipeWaitsForTheNextExercise() {
        val both = setOf(WorkoutTip.TAP_TO_TYPE, WorkoutTip.SWITCH_UNIT)

        assertNull(workoutTip(both, logged))
        assertEquals(WorkoutTip.SWIPE_FOR_WORKOUT, workoutTip(both, next))
    }

    @Test
    fun aGestureAlreadyUsedIsNeverTaught() {
        // Switched the unit before its tip came: straight past it to the swipe.
        val unitUsed = setOf(WorkoutTip.TAP_TO_TYPE, WorkoutTip.SWITCH_UNIT)
        assertEquals(WorkoutTip.SWIPE_FOR_WORKOUT, workoutTip(unitUsed, next))

        assertNull(workoutTip(WorkoutTip.entries.toSet(), next))
    }

    @Test
    fun theUnitTipWaitsForAnExerciseThatHasAUnit() {
        val typed = setOf(WorkoutTip.TAP_TO_TYPE)

        // A plank has no kg or lb to point at, so the tip has nothing to stand by; the swipe does.
        assertNull(workoutTip(typed, logged.copy(unitShown = false)))
        assertEquals(WorkoutTip.SWIPE_FOR_WORKOUT, workoutTip(typed, next.copy(unitShown = false)))
        assertEquals(WorkoutTip.SWITCH_UNIT, workoutTip(typed, next))
    }
}
