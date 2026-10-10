package com.tylergimpel.traintracks.ui.session

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.traintracks.data.RoutineProgress
import com.tylergimpel.traintracks.domain.Effort
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.LineupItem
import com.tylergimpel.traintracks.domain.LoggingType
import com.tylergimpel.traintracks.domain.Performance
import com.tylergimpel.traintracks.domain.PerformedSet
import com.tylergimpel.traintracks.domain.PlannedExercise
import com.tylergimpel.traintracks.domain.Prescription
import com.tylergimpel.traintracks.domain.Recommendation
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RepRange
import com.tylergimpel.traintracks.domain.Routine
import com.tylergimpel.traintracks.domain.RoutineDay
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.Session
import com.tylergimpel.traintracks.domain.SetDraft
import com.tylergimpel.traintracks.domain.SetType
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.WorkoutTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * What the workout does when a thumb touches it (#142).
 *
 * Deliberately nothing about the wheel's falloff: how far a neighbouring set fades is a judgement
 * the prototype settled by looking at it. What these cover is the behaviour the screen is *for* —
 * that logging costs one tap, that a value can be typed instead of stepped, that the unit toggle
 * changes the reading rather than the weight, and that the exercise's actions are one tap away.
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
        // The band, small above the one big number.
        compose.onNodeWithText("8 - 12 reps").assertIsDisplayed()
    }

    @Test
    fun theWheelSitsOnTheSetAboutToBeDoneWithSetsBehindIt() {
        compose.setContent { SessionScreen(state = state(logged = 4)) }

        // Four sets down and the fifth is the one on screen: no scrolling to find what to do next.
        compose.onNodeWithTag(PENDING).assertIsDisplayed()
    }

    @Test
    fun loggingASetIsOneTapOnAControlThatDoesNotMove() {
        var logged = 0
        compose.setContent { SessionScreen(state = state(logged = 12), entry = SetEntryActions(onLogSet = { logged++ })) }

        // Fixed below the wheel however many sets are behind it.
        compose.onNodeWithTag(WEIGHT_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(LOG_SET).performClick()

        assertEquals(1, logged)
    }

    @Test
    fun aValueCanBeTypedRatherThanStepped() {
        var typed: SetDraft? = null
        compose.setContent {
            SessionScreen(state = state(), entry = SetEntryActions(onDraftChange = { typed = it }))
        }

        // Tapping the number types it: stepping from 20 to 60 is absurd, and overriding is the
        // expected path while the app is still guessing.
        compose.onNodeWithTag(WEIGHT_FIELD).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("62.5")

        assertEquals(62.5, typed?.weight?.value)
    }

    @Test
    fun theUnitToggleChangesWhatIsShownAndNotWhatIsStored() {
        var read: WeightUnit? = null
        compose.setContent { SessionScreen(state = state(), entry = SetEntryActions(onReadIn = { read = it })) }

        compose.onNodeWithTag(UNIT_TOGGLE).performSemanticsAction(SemanticsActions.OnClick)

        // Display only: the machine is labelled in pounds and the log stays in kilograms.
        assertEquals(WeightUnit.POUNDS, read)
    }

    @Test
    fun theExerciseNameOpensSwapAddAndSkip() {
        var swapped = 0
        var added = 0
        var skipped = 0
        compose.setContent {
            SessionScreen(
                state = state(),
                exercises = ExerciseActions(onSwap = { swapped++ }, onAdd = { added++ }, onSkip = { skipped++ }),
            )
        }

        compose.onNodeWithTag(EXERCISE_NAME).performClick()
        compose.onNodeWithText("Swap for another").performClick()
        compose.onNodeWithTag(EXERCISE_NAME).performClick()
        compose.onNodeWithText("Add an exercise after this").performClick()
        compose.onNodeWithTag(EXERCISE_NAME).performClick()
        compose.onNodeWithText("Skip this exercise").performClick()

        assertEquals(listOf(1, 1, 1), listOf(swapped, added, skipped))
    }

    @Test
    fun aTipPointsAtItsThingWithoutGettingInTheWay() {
        val done = mutableListOf<WorkoutTip>()
        var logged = 0
        compose.setContent {
            SessionScreen(
                state = state().copy(tip = WorkoutTip.TAP_TO_TYPE),
                entry = SetEntryActions(onLogSet = { logged++ }),
                wholeSession = SessionActions(onTipDone = { done += it }),
            )
        }

        compose.onNodeWithTag(TIP).assertIsDisplayed()
        compose.onNodeWithText("Tap to type").assertIsDisplayed()

        // Never in the way: a set logs with the tip still up.
        compose.onNodeWithTag(LOG_SET).performClick()
        assertEquals(1, logged)

        // Tapping the bubble is one way it goes.
        compose.onNodeWithTag(TIP).performClick()
        assertEquals(listOf(WorkoutTip.TAP_TO_TYPE), done)
    }

    @Test
    fun usingAGestureCountsAsItsTip() {
        val done = mutableListOf<WorkoutTip>()
        compose.setContent { SessionScreen(state = state(), wholeSession = SessionActions(onTipDone = { done += it })) }

        // Whether or not its tip is showing: a gesture used before its tip comes means it never does.
        compose.onNodeWithTag(WEIGHT_FIELD).performClick()
        compose.onNodeWithTag(UNIT_TOGGLE).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(WorkoutTip.TAP_TO_TYPE, WorkoutTip.SWITCH_UNIT), done)
    }

    @Test
    fun noTipNoBubble() {
        compose.setContent { SessionScreen(state = state().copy(tip = null)) }

        compose.onNodeWithTag(TIP).assertDoesNotExist()
    }

    @Test
    fun anExercisesUnitCanBeSetFromItsOwnSettings() {
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

        // The route that does not depend on the tip (#89).
        compose.onNodeWithTag(EXERCISE_UNIT_POUNDS).performClick()

        assertEquals(WeightUnit.POUNDS, changed?.displayUnit)
    }

    @Test
    fun theScreenSaysNothingAboutItselfWhileItIsPrescribing() {
        compose.setContent { SessionScreen(state = state()) }

        compose.onNodeWithText("calibrating").assertDoesNotExist()
    }

    @Test
    fun theScreenSaysWhenItIsGuessing() {
        val calibrating = Recommendation.Calibrating(Prescription(load = Kilograms(20.0), reps = 5))

        compose.setContent { SessionScreen(state = state(recommendation = calibrating)) }

        compose.onNodeWithText("Stop when it gets hard", substring = true).assertIsDisplayed()
    }

    @Test
    fun aHighRepPrescriptionSaysThatTheEffortIsThePoint() {
        val endurance = Recommendation.Prescribing(
            prescription = Prescription(load = Kilograms(30.0), reps = 15),
            repRange = RepRange(12, 15),
        )

        compose.setContent { SessionScreen(state = state(recommendation = endurance)) }

        compose.onNodeWithText("Take this close to failure — high reps only work hard.").assertIsDisplayed()
    }

    @Test
    fun aRestThatIsRunningCanBeSkipped() {
        var skipped = false
        compose.setContent {
            SessionScreen(
                state = state().copy(resting = Resting(Instant.now(), REST), restAfter = SetType.WORKING),
                rest = RestActions(onEndRest = { skipped = true }),
            )
        }

        // A bar over Log, saying what it follows.
        compose.onNodeWithTag(REST_BAR).assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()

        assertTrue(skipped)
    }

    @Test
    fun theDrawerOpensFromTheStripAndNeverNeedsAnEdgeSwipe() {
        compose.setContent { SessionScreen(state = state(routine = pushDay)) }

        // What must never open it is a swipe from the left edge, which on gesture navigation is
        // the system Back gesture and never reaches the app.
        compose.onNodeWithTag(DRAWER_BUTTON).performClick()

        compose.onNodeWithTag(DRAWER).assertIsDisplayed()
    }

    @Test
    fun tappingAnExerciseInTheDrawerMovesTheWorkoutThere() {
        var jumped: Exercise? = null
        compose.setContent {
            SessionScreen(state = state(routine = pushDay), exercises = ExerciseActions(onJumpTo = { jumped = it }))
        }

        compose.onNodeWithTag(DRAWER_BUTTON).performClick()
        compose.onNodeWithText("Barbell Row").performClick()

        // Training out of order is ordinary, and the cursor holds its place meanwhile.
        assertEquals("Row", jumped?.id)
    }

    @Test
    fun finishingShowsWhatWasDoneFirst() {
        var finished = 0
        compose.setContent {
            SessionScreen(
                state = state(logged = 2).copy(askingToFinish = FinishAsk(allDone = false)),
                cycle = CycleActions(onFinishWorkout = { finished++ }),
            )
        }

        compose.onNodeWithTag(FINISH_WORKOUT).performClick()

        assertEquals(1, finished)
    }

    @Test
    fun theStripSaysHowLongTheLifterHasBeenHere() {
        val startedAt = Instant.now().minus(Duration.ofMinutes(47))
        compose.setContent {
            SessionScreen(state = state(session = Session.start(startedAt, ZoneId.systemDefault())))
        }

        // Counted from the session rather than the first working set, so a treadmill before the
        // weights is inside it.
        compose.onNodeWithText("47:0", substring = true).assertIsDisplayed()
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
        lineup = listOf(LineupItem(bench, done = logged, planned = 20), LineupItem(row, done = 0, planned = 4)),
    )

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
        val REST: Duration = Duration.ofSeconds(150)

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

        /** A routine with one day in it. */
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
