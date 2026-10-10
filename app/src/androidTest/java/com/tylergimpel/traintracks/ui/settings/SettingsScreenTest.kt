package com.tylergimpel.traintracks.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.traintracks.data.Backup
import com.tylergimpel.traintracks.data.WouldReplace
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.data.ImportResult
import com.tylergimpel.traintracks.domain.FITNOTES
import com.tylergimpel.traintracks.domain.ImportPreview
import com.tylergimpel.traintracks.domain.LoggingType
import com.tylergimpel.traintracks.domain.NameDecision
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
    fun unmatchedNamesAreCountedUpFrontAndEachIsTheLiftersOwnUntilSaidOtherwise() {
        var imported = 0
        var choosing: String? = null
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(previewing = previewing()),
                onChooseSameAs = { choosing = it },
                onImportWithDecisions = { imported++ },
            )
        }

        // The size of the chore before any of it (#99), and the default for every name.
        compose.onNodeWithText("2 exercise names from FitNotes need a decision").assertExists()
        compose.onNodeWithText("Hammer Strength Row · 14 sets").assertExists()
        compose.onAllNodesWithText("My own").assertCountEquals(2)
        compose.onAllNodesWithText("My own")[0].assertIsSelected()

        compose.onAllNodesWithText("Same as…")[0].performClick()
        assertEquals("Hammer Strength Row", choosing)

        compose.onNodeWithTag(IMPORT_GO).performClick()
        assertEquals(1, imported)
    }

    @Test
    fun aNameIsSaidToBeTheSameAsAnExerciseChosenFromTheBank() {
        var decided: Pair<String, NameDecision>? = null
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(previewing = previewing().copy(choosingFor = "Hammer Strength Row")),
                onDecide = { name, decision -> decided = name to decision },
            )
        }

        // Only what is here to choose from: a mapped name never makes a new exercise.
        compose.onNodeWithText("Hammer Strength Row is the same as").assertExists()
        compose.onNodeWithText("Add new", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Bent Over Barbell Row").performClick()

        assertEquals("Hammer Strength Row" to NameDecision.SameAs("Bent_Over_Barbell_Row"), decided)
    }

    @Test
    fun aDecidedNameSaysWhatItIsTheSameAs() {
        val decided = previewing().let {
            it.copy(decisions = it.decisions + ("Pec Deck Machine" to NameDecision.SameAs("Bent_Over_Barbell_Row")))
        }
        compose.setContent { SettingsScreen(state = SettingsUiState(previewing = decided)) }

        compose.onNodeWithText("Same as Bent Over Barbell Row").assertIsSelected()
    }

    @Test
    fun aPickedFileSaysWhatItWillBringInAndBackingOutWritesNothing() {
        var cancelled = 0
        var imported = 0
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(previewing = previewing()),
                onCancelPreview = { cancelled++ },
                onImportWithDecisions = { imported++ },
            )
        }

        // Before anything is written (#103): how much, over when, what is left out, and the
        // honest word on volume.
        compose.onNodeWithText("212").assertExists()
        compose.onNodeWithText("2234").assertExists()
        compose.onNodeWithText("Jan 8, 2024 – Mar 2, 2026").assertExists()
        compose.onNodeWithText("5 sets cannot be read", substring = true).assertExists()
        compose.onNodeWithText("volume from before the import is approximate", substring = true).assertExists()

        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, imported)
    }

    @Test
    fun aFileWithNothingReadableCannotBeImported() {
        val empty = previewing().copy(
            preview = ImportPreview(from = null, to = null, sessions = 0, sets = 0, unreadable = 4, unmatched = emptyList()),
            decisions = emptyMap(),
        )
        compose.setContent { SettingsScreen(state = SettingsUiState(previewing = empty)) }

        compose.onNodeWithText("Nothing in this file can be brought in.").assertExists()
        compose.onNodeWithTag(IMPORT_GO).assertIsNotEnabled()
    }

    @Test
    fun theSummarySaysWhatWasLeftBehindThatVolumeIsApproximateAndHowToUndo() {
        val result = ImportResult(
            batchId = 1,
            sessions = 212,
            sets = 2229,
            unmatched = emptyMap(),
            notYetSupported = mapOf("unreadable" to 5),
            volumeReliable = false,
        )
        compose.setContent { SettingsScreen(state = SettingsUiState(dataMessage = DataMessage.Imported(result)), section = SettingsSection.DATA) }

        compose.onNodeWithText("Brought in 212 sessions, 2229 sets.", substring = true).assertExists()
        compose.onNodeWithText("5 sets could not be read", substring = true).assertExists()
        compose.onNodeWithText("strength history is reliable all the way back", substring = true).assertExists()
        compose.onNodeWithText("use Undo next to it", substring = true).assertExists()
    }

    private fun previewing(): Previewing {
        val unmatched = listOf(Unmatched("Hammer Strength Row", sets = 14), Unmatched("Pec Deck Machine", sets = 3))
        return Previewing(
            records = NeutralImport(source = FITNOTES, fingerprint = "f", sets = emptyList()),
            preview = ImportPreview(
                from = LocalDate.parse("2024-01-08"),
                to = LocalDate.parse("2026-03-02"),
                sessions = 212,
                sets = 2234,
                unreadable = 5,
                unmatched = unmatched,
            ),
            decisions = unmatched.associate { it.name to NameDecision.CreateOwn },
            bank = listOf(
                Exercise(
                    id = "Bent_Over_Barbell_Row",
                    bankName = "Bent Over Barbell Row",
                    primaryMuscles = listOf("middle back"),
                    secondaryMuscles = emptyList(),
                    equipment = "barbell",
                    mechanic = "compound",
                    force = "pull",
                    category = "strength",
                    level = "beginner",
                    loggingType = LoggingType.WEIGHT_REPS,
                    unilateral = false,
                ),
            ),
        )
    }

    @Test
    fun aRestTimerThatWorksHasNothingToFix() {
        compose.setContent { SettingsScreen(state = SettingsUiState()) }

        compose.onNodeWithText("Rests end on time.").assertExists()
        compose.onNodeWithText("Allow alarms").assertDoesNotExist()
    }
}
