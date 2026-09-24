package com.tylergimpel.gymtracker.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.PlannedExercise
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * What the logging screen does when a thumb touches it.
 *
 * Deliberately nothing about the wheel's falloff: how far a neighbouring set fades is a judgement
 * the prototype settled by looking at it, and a test asserting a scale factor would fail the next
 * time somebody improves it while proving nothing a lifter cares about. What these cover is the
 * behaviour the screen is *for* — that logging costs one tap, that a value can be typed instead
 * of stepped, and that the unit toggle changes the reading rather than the weight.
 */
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theWheelShowsTheSetAboutToBeDone() {
        compose.setContent { SessionScreen(state = state()) }

        compose.onNodeWithTag(WHEEL).assertIsDisplayed()
        compose.onNodeWithTag(PENDING).assertIsDisplayed()
        // The number the lifter reads off the rack, and the band it sits inside.
        compose.onNodeWithText("60 kg × 8").assertIsDisplayed()
        compose.onNodeWithText("8 - 12 reps").assertIsDisplayed()
    }

    @Test
    fun theWheelSitsOnTheSetAboutToBeDoneWithSetsBehindIt() {
        compose.setContent { SessionScreen(state = state(logged = 4)) }

        // Four sets down and the fifth is the one on screen. A wheel that opened at the top
        // would have a lifter scrolling past their own history to find out what to do next,
        // which is the scrolling the whole layout exists to avoid.
        compose.onNodeWithTag(PENDING).assertIsDisplayed()
    }

    @Test
    fun loggingASetIsOneTapOnAControlThatDoesNotMove() {
        var logged = 0
        compose.setContent { SessionScreen(state = state(), entry = SetEntryActions(onLogSet = { logged++ })) }

        // No scrolling to reach it. The button is pinned below the wheel, so the common path is
        // one tap from wherever the session happens to be scrolled to — which is the whole
        // reason the thumb zone is a separate strip.
        compose.onNodeWithTag(LOG_SET).performClick()

        assertEquals(1, logged)
    }

    @Test
    fun theThumbZoneStaysPutWhileTheSessionScrolls() {
        compose.setContent { SessionScreen(state = state(logged = 12)) }

        // The session itself, not the wheel: the wheel is its own little scroller and moving
        // it proves nothing about the page. Scroll the page and the controls are still there,
        // because they were never in it.
        compose.onNodeWithTag(SESSION_LIST).performTouchInput { swipeUp() }
        compose.onNodeWithTag(LOG_SET).assertIsDisplayed()
        compose.onNodeWithTag(WEIGHT_FIELD).assertIsDisplayed()
    }

    @Test
    fun aValueCanBeTypedRatherThanStepped() {
        var typed: SetDraft? = null
        compose.setContent {
            SessionScreen(state = state(), entry = SetEntryActions(onDraftChange = { typed = it }))
        }

        compose.onNodeWithTag(WEIGHT_FIELD).performTextReplacement("62.5")

        // Stepping from 20 to 60 is absurd, and overriding is the expected path while the app is
        // still guessing rather than an exception.
        assertEquals(62.5, typed?.weight?.value)
    }

    @Test
    fun theUnitToggleChangesWhatIsShownAndNotWhatIsStored() {
        var read: WeightUnit? = null
        compose.setContent { SessionScreen(state = state(), entry = SetEntryActions(onReadIn = { read = it })) }

        compose.onNodeWithText("lb").performClick()

        // Display only: the machine is labelled in pounds and the log stays in kilograms, which is
        // what keeps one gym's two lat pulldowns on one graph.
        assertEquals(WeightUnit.POUNDS, read)
    }

    @Test
    fun withNoRoutineTheWaysToStartAreOfferedBesideAWorkingSession() {
        var built = 0
        var generated = 0
        compose.setContent {
            SessionScreen(
                state = state(routine = null),
                wholeSession = SessionActions(
                    onBuildRoutine = { built++ },
                    onLetTheAppBuild = { generated++ },
                ),
            )
        }

        // Offered, not imposed (#85): both ways to a routine side by side, and the session under
        // them still ready to log a set with nothing set up at all.
        compose.onNodeWithText("Build a routine").performClick()
        compose.onNodeWithText("Let the app build one").performClick()
        assertEquals(1, built)
        assertEquals(1, generated)
        compose.onNodeWithText("Change exercise", useUnmergedTree = true).assertExists()
    }

    @Test
    fun onceThereIsARoutineTheWaysToStartAreGone() {
        compose.setContent { SessionScreen(state = state(routine = pushDay)) }

        // Nothing asks twice.
        compose.onNodeWithText("Build a routine").assertDoesNotExist()
        compose.onNodeWithText("Let the app build one").assertDoesNotExist()
    }

    @Test
    fun theFirstSessionPointsOutTheUnitAndTheRoutineOnce() {
        var dismissed = 0
        var logged = 0
        compose.setContent {
            SessionScreen(
                state = state().copy(showCoachMark = true),
                entry = SetEntryActions(onLogSet = { logged++ }),
                wholeSession = SessionActions(onDismissCoachMark = { dismissed++ }),
            )
        }

        // The two things the screen leaves undecorated on purpose, taught in one place (#92).
        compose.onNodeWithText("tap the unit", substring = true).assertExists()
        compose.onNodeWithText("swipe right anywhere", substring = true).assertExists()

        // And never in the way: a set logs with the tip still up.
        compose.onNodeWithTag(LOG_SET).performClick()
        assertEquals(1, logged)

        compose.onNodeWithText("Got it").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun aDismissedTipIsNotShownAgain() {
        compose.setContent { SessionScreen(state = state().copy(showCoachMark = false)) }

        compose.onNodeWithText("Got it").assertDoesNotExist()
    }

    @Test
    fun anExercisesUnitCanBeSetFromItsOwnScreen() {
        var changed: ExerciseDetails? = null
        val details = ExerciseDetails(
            exerciseId = bench.id,
            name = bench.name,
            note = "",
            progressionModel = bench.progressionModel,
            displayUnit = WeightUnit.KILOGRAMS,
        )
        compose.setContent {
            SessionScreen(
                state = state().copy(editingExercise = details),
                exercises = ExerciseActions(onExerciseDetailsChange = { changed = it }),
            )
        }

        // The undecorated unit on the logging screen is taught once, by a tip a lifter can dismiss
        // unread. This is the route that does not depend on the tip: the exercise's own screen,
        // where anybody looks for anything else about it (#89).
        compose.onNodeWithTag(EXERCISE_UNIT_POUNDS).performClick()

        assertEquals(WeightUnit.POUNDS, changed?.displayUnit)
    }

    @Test
    fun theScreenSaysNothingAboutItselfWhileItIsPrescribing() {
        compose.setContent { SessionScreen(state = state()) }

        // The previous set is already printed under every row; a line repeating that the number
        // came from it is noise. Only `calibrating` and the high-rep effort line ever speak.
        compose.onNodeWithText("calibrating").assertDoesNotExist()
    }

    @Test
    fun theScreenSaysWhenItIsGuessing() {
        val calibrating = Recommendation.Calibrating(
            Prescription(load = Kilograms(20.0), reps = 5),
        )

        compose.setContent { SessionScreen(state = state(recommendation = calibrating)) }

        compose.onNodeWithText("calibrating").assertIsDisplayed()
    }

    @Test
    fun aHighRepPrescriptionSaysThatTheEffortIsThePoint() {
        val endurance = Recommendation.Prescribing(
            prescription = Prescription(load = Kilograms(30.0), reps = 15),
            repRange = RepRange(12, 15),
        )

        compose.setContent { SessionScreen(state = state(recommendation = endurance)) }

        // Hypertrophy is load-independent up here only if the effort is high, so a set of fifteen
        // prescribed without saying so is not the intervention the evidence supports.
        compose.onNodeWithText(
            "Take this close to failure — high reps only work hard.",
        ).assertIsDisplayed()
    }

    @Test
    fun aRestThatIsRunningCanBeSkipped() {
        var skipped = false
        compose.setContent {
            SessionScreen(
                state = state().copy(
                    resting = Resting(java.time.Instant.parse("2026-09-14T18:00:00Z"), REST),
                ),
                rest = RestActions(onEndRest = { skipped = true }),
            )
        }

        compose.onNodeWithText("Skip").performClick()

        // The timer bends to the session rather than the other way round.
        assertTrue(skipped)
    }


    @Test
    fun theRoutineIsOneTapAwayAndNeverNeedsAnEdgeSwipe() {
        compose.setContent { SessionScreen(state = state()) }

        // A handle, in the top bar, where a thumb can reach it. The swipe that also opens this
        // is horizontal and works anywhere — what it must never be is a swipe from the left
        // edge, which on gesture navigation is the system Back gesture and never reaches the app.
        compose.onNodeWithText("Routine").performClick()

        compose.onNodeWithTag(DRAWER).assertIsDisplayed()
    }

    @Test
    fun tappingAnExerciseInTheRoutineMovesTheSessionThere() {
        var jumped: Exercise? = null
        compose.setContent {
            SessionScreen(state = state(routine = pushDay), exercises = ExerciseActions(onJumpTo = { jumped = it }))
        }

        compose.onNodeWithText("Routine").performClick()
        compose.onNodeWithText("Barbell Row").performClick()

        // Training out of order is ordinary — a rack is busy, a machine is taken — and the
        // cursor holds its place while the lifter moves around the session.
        assertEquals("Row", jumped?.id)
    }

    private fun state(
        recommendation: Recommendation = prescribed,
        routine: RoutineProgress? = null,
        logged: Int = 0,
        session: Session? = null,
    ): SessionUiState = SessionUiState(
        session = session,
        selected = bench,
        recommendation = recommendation,
        draft = SetDraft(weightText = "60", repsText = "8"),
        wheel = (0 until logged).map { WheelSet.Logged(it, loggedSet(it + 1)) } +
            WheelSet.Pending(recommendation),
        routine = routine,
    )

    @Test
    fun theHeaderSaysHowLongTheLifterHasBeenHere() {
        val startedAt = Instant.now().minus(Duration.ofMinutes(47))
        compose.setContent {
            SessionScreen(state = state(session = Session.start(startedAt, ZoneId.systemDefault())))
        }

        // Counted from the session rather than from the first working set, so a treadmill before
        // the weights is inside it: what is being answered is how long this trip to the gym has
        // taken, which is the half of "how much longer am I here" the estimate cannot supply.
        compose.onNodeWithText("47 min so far").assertIsDisplayed()
    }

    @Test
    fun anHourInIsReadAsAnHourRatherThanAsMinutes() {
        val startedAt = Instant.now().minus(Duration.ofMinutes(72))
        compose.setContent {
            SessionScreen(state = state(session = Session.start(startedAt, ZoneId.systemDefault())))
        }

        // `72 min` is arithmetic the lifter should not be doing at the rack.
        compose.onNodeWithText("1:12 so far").assertIsDisplayed()
    }

    private fun loggedSet(index: Int) = PerformedSet(
        setIndex = index,
        type = SetType.WORKING,
        efforts = listOf(
            Effort(
                id = index.toLong(),
                side = null,
                performance = Performance.WeightAndReps(Kilograms(60.0), 8),
                note = null,
            ),
        ),
    )

    private companion object {
        val REST: java.time.Duration = java.time.Duration.ofSeconds(150)

        val prescribed = Recommendation.Prescribing(
            prescription = Prescription(load = Kilograms(60.0), reps = 8),
            repRange = RepRange(8, 12),
        )

        val row = Exercise(
            id = "Row",
            bankName = "Barbell Row",
            primaryMuscles = listOf("middle back"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "pull",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        val bench = Exercise(
            id = "Bench",
            bankName = "Bench Press",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(),
            equipment = "barbell",
            mechanic = "compound",
            force = "push",
            category = "strength",
            level = "beginner",
            loggingType = LoggingType.WEIGHT_REPS,
            unilateral = false,
        )

        /** A routine with one day in it, so the drawer has something to show. */
        val pushDay = RoutineProgress(
            routine = Routine(
                id = 1,
                name = "Push / Pull",
                goal = RoutineGoal.HYPERTROPHY,
                cadence = 3,
                equipment = setOf("barbell"),
                repPreference = RepPreference.MODERATE,
                days = listOf(
                    RoutineDay(
                        id = 1,
                        orderIndex = 1,
                        name = "Push",
                        exercises = listOf(PlannedExercise(bench, 4), PlannedExercise(row, 4)),
                    ),
                ),
            ),
            cycle = null,
        )
    }
}
