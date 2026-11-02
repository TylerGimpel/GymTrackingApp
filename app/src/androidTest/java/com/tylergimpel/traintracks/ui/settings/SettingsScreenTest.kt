package com.tylergimpel.traintracks.ui.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.traintracks.data.Backup
import com.tylergimpel.traintracks.data.WouldReplace
import com.tylergimpel.traintracks.data.ImportResult
import com.tylergimpel.traintracks.domain.FITNOTES
import com.tylergimpel.traintracks.domain.ImportPreview
import com.tylergimpel.traintracks.domain.NeutralImport
import com.tylergimpel.traintracks.domain.Unmatched
import com.tylergimpel.traintracks.ui.RestFix
import com.tylergimpel.traintracks.ui.session.RestAlerts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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
    fun aRestoreOntoAPhoneThatWillNotWakeTheLifterSaysSoThenAndThere() {
        var fixed: RestFix? = null
        compose.setContent {
            SettingsScreen(
                section = SettingsSection.DATA,
                state = SettingsUiState(
                    dataMessage = DataMessage.Restored(sessions = 112),
                    restAlerts = RestAlerts(alarmsAreReliable = false),
                ),
                onFixRestAlerts = { fixed = it },
            )
        }

        // A new phone, or a restore, starts with exact alarms denied (#95). Said with the restore,
        // where the lifter is looking, rather than discovered by a rest that ends late.
        compose.onNodeWithText("Restored 112 sessions", substring = true).assertExists()
        compose.onNodeWithText("this phone does not yet let rests", substring = true).assertExists()
        compose.onAllNodesWithText("Allow alarms")[0].performClick()

        assertEquals(RestFix.EXACT_ALARMS, fixed)
    }

    @Test
    fun aPickedFileAsksOnceWithWhatItWillBringIn() {
        var cancelled = 0
        var imported = 0
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmingImport = confirming()),
                section = SettingsSection.DATA,
                onConfirmImport = { imported++ },
                onCancelImport = { cancelled++ },
            )
        }

        // Before anything is written (#103): how much and over when, and nothing else to decide.
        compose.onNodeWithText("Import from FitNotes?").assertExists()
        compose.onNodeWithText("212 sessions and 2234 sets, Jan 8, 2024 – Mar 2, 2026.").assertExists()
        compose.onNodeWithText("My own").assertDoesNotExist()

        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        compose.onNodeWithTag(IMPORT_GO).performClick()
        assertEquals(1, imported)
    }

    @Test
    fun aFileWithNothingReadableCannotBeImported() {
        val empty = confirming().copy(
            preview = ImportPreview(from = null, to = null, sessions = 0, sets = 0, unreadable = 4, unmatched = emptyList()),
        )
        compose.setContent { SettingsScreen(state = SettingsUiState(confirmingImport = empty), section = SettingsSection.DATA) }

        compose.onNodeWithText("Nothing in this file can be brought in.").assertExists()
        compose.onNodeWithTag(IMPORT_GO).assertIsNotEnabled()
    }

    @Test
    fun anImportSaysHowManySessionsItBroughtInAndNoMore() {
        val result = ImportResult(
            batchId = 1,
            sessions = 212,
            sets = 2229,
            unmatched = emptyMap(),
            notYetSupported = mapOf("unreadable" to 5),
            volumeReliable = false,
        )
        compose.setContent { SettingsScreen(state = SettingsUiState(dataMessage = DataMessage.Imported(result)), section = SettingsSection.DATA) }

        compose.onNodeWithText("Imported 212 sessions.").assertExists()
        compose.onNodeWithText("approximate", substring = true).assertDoesNotExist()
    }

    private fun confirming() = ConfirmImport(
        records = NeutralImport(source = FITNOTES, fingerprint = "f", sets = emptyList()),
        preview = ImportPreview(
            from = LocalDate.parse("2024-01-08"),
            to = LocalDate.parse("2026-03-02"),
            sessions = 212,
            sets = 2234,
            unreadable = 5,
            unmatched = listOf(Unmatched("Hammer Strength Row", sets = 14)),
        ),
        bank = emptyList(),
    )

    @Test
    fun aRestTimerThatWorksHasNothingToFix() {
        compose.setContent { SettingsScreen(state = SettingsUiState()) }

        compose.onNodeWithText("Rests end on time.").assertExists()
        compose.onNodeWithText("Allow alarms").assertDoesNotExist()
    }
}
