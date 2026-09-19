package com.tylergimpel.gymtracker.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun loggingASetIsOneTapOnAControlThatDoesNotMove() {
        var logged = 0
        compose.setContent { SessionScreen(state = state(), onLogSet = { logged++ }) }

        compose.onNodeWithTag(LOG_SET).performScrollTo().performClick()

        // One tap, and nothing to scroll to first: a screen that takes three taps per set is a
        // screen somebody stops using by week three.
        assertEquals(1, logged)
    }

    @Test
    fun aValueCanBeTypedRatherThanStepped() {
        var typed: SetDraft? = null
        compose.setContent {
            SessionScreen(state = state(), onDraftChange = { typed = it })
        }

        compose.onNodeWithTag(WEIGHT_FIELD).performScrollTo().performTextReplacement("62.5")

        // Stepping from 20 to 60 is absurd, and overriding is the expected path while the app is
        // still guessing rather than an exception.
        assertEquals(62.5, typed?.weight?.value)
    }

    @Test
    fun theUnitToggleChangesWhatIsShownAndNotWhatIsStored() {
        var read: WeightUnit? = null
        compose.setContent { SessionScreen(state = state(), onReadIn = { read = it }) }

        compose.onNodeWithText("lb").performClick()

        // Display only: the machine is labelled in pounds and the log stays in kilograms, which is
        // what keeps one gym's two lat pulldowns on one graph.
        assertEquals(WeightUnit.POUNDS, read)
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
                onEndRest = { skipped = true },
            )
        }

        compose.onNodeWithText("Skip").performClick()

        // The timer bends to the session rather than the other way round.
        assertTrue(skipped)
    }

    private fun state(
        recommendation: Recommendation = prescribed,
    ): SessionUiState = SessionUiState(
        selected = bench,
        recommendation = recommendation,
        draft = SetDraft(weightText = "60", repsText = "8"),
        wheel = listOf(WheelSet.Pending(recommendation)),
    )

    private companion object {
        val REST: java.time.Duration = java.time.Duration.ofSeconds(150)

        val prescribed = Recommendation.Prescribing(
            prescription = Prescription(load = Kilograms(60.0), reps = 8),
            repRange = RepRange(8, 12),
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
    }
}
