package com.tylergimpel.gymtracker.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggingType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every exclusion can be looked at and taken back from one list (#93). */
@RunWith(AndroidJUnit4::class)
class ExcludedScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anExclusionIsTakenBackFromTheList() {
        var takenBack: Exercise? = null
        compose.setContent { ExcludedScreen(excluded = listOf(arnold), onTakeBack = { takenBack = it }) }

        compose.onNodeWithText("Arnold Press").assertExists()
        compose.onNodeWithText("Allow again").performClick()

        assertEquals(arnold, takenBack)
    }

    @Test
    fun anEmptyListSaysThereIsNothingExcluded() {
        compose.setContent { ExcludedScreen(excluded = emptyList()) }

        // Said rather than shown as nothing, which reads as a screen that failed to load.
        compose.onNodeWithText("Nothing is excluded", substring = true).assertExists()
    }

    private val arnold = Exercise(
        id = "Arnold_Press",
        bankName = "Arnold Press",
        primaryMuscles = listOf("shoulders"),
        secondaryMuscles = emptyList(),
        equipment = "dumbbell",
        mechanic = "compound",
        force = "push",
        category = "strength",
        level = "beginner",
        loggingType = LoggingType.WEIGHT_REPS,
        unilateral = false,
        isDisliked = true,
    )
}
