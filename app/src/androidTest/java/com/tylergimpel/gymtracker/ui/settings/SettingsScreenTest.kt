package com.tylergimpel.gymtracker.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.gymtracker.data.Backup
import com.tylergimpel.gymtracker.data.WouldReplace
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
    fun restoringOverTrainingSaysWhatIsHereAndDecliningIsOneTap() {
        var cancelled = 0
        var confirmed = 0
        val replacing = ConfirmRestore(
            backup = Backup(databaseVersion = 30, tables = mapOf("session" to List(40) { emptyMap() })),
            replacing = WouldReplace(sessions = 12, routine = true, weighIns = 3, customised = true),
        )
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmingRestore = replacing),
                onConfirmRestore = { confirmed++ },
                onCancelRestore = { cancelled++ },
            )
        }

        // What is here now and what comes back instead, before anything happens (#104).
        compose.onNodeWithText("12 sessions and 3 weigh-ins", substring = true).assertExists()
        compose.onNodeWithText("which has 40 sessions", substring = true).assertExists()
        compose.onNodeWithText("routine is replaced too", substring = true).assertExists()

        compose.onNodeWithText("Keep what is here").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    @Test
    fun aRestTimerThatWorksHasNothingToFix() {
        compose.setContent { SettingsScreen(state = SettingsUiState()) }

        compose.onNodeWithText("Rests end on time.").assertExists()
        compose.onNodeWithText("Allow alarms").assertDoesNotExist()
    }
}
