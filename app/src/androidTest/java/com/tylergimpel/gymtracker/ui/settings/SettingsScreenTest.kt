package com.tylergimpel.gymtracker.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.gymtracker.ui.RestFix
import com.tylergimpel.gymtracker.ui.session.RestAlerts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings says whether a rest can reach the lifter before they find out by missing one (#91).
 *
 * The permission is the one part of the rest timer's failure the app can detect, and the only
 * useful thing to do with detecting it is to say so with the way out attached.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aRestThatCannotEndOnTimeSaysSoWithTheWayToFixIt() {
        var fixed: RestFix? = null
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(restAlerts = RestAlerts(alarmsAreReliable = false)),
                onFixRestAlerts = { fixed = it },
            )
        }

        compose.onNodeWithText("Allow alarms").performClick()

        assertEquals(RestFix.EXACT_ALARMS, fixed)
    }

    @Test
    fun aRestTimerThatWorksHasNothingToFix() {
        compose.setContent { SettingsScreen(state = SettingsUiState()) }

        compose.onNodeWithText("Rests end on time.").assertExists()
        compose.onNodeWithText("Allow alarms").assertDoesNotExist()
    }
}
