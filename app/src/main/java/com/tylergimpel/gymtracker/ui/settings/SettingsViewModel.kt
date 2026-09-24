package com.tylergimpel.gymtracker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.rest.RestAlarmScheduler
import com.tylergimpel.gymtracker.rest.RestNotifications
import com.tylergimpel.gymtracker.ui.session.RestAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.data.BackupRepository
import com.tylergimpel.gymtracker.data.NotABackup
import com.tylergimpel.gymtracker.data.Backup
import com.tylergimpel.gymtracker.data.WouldReplace
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.FitNotesParser
import com.tylergimpel.gymtracker.data.ImportRepository
import com.tylergimpel.gymtracker.data.ImportResult
import java.io.File
import com.tylergimpel.gymtracker.domain.spreadsheetExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.ZoneId

data class SettingsUiState(
    /** The **Preferred unit**: what the app starts in, and what every exercise falls back to. */
    val preferredUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val theme: Theme = Theme.SYSTEM,
    /** What the rest timer can currently do, read from the platform as the session reads it. */
    val restAlerts: RestAlerts = RestAlerts(),
    /** What the last export or backup did, said once and then cleared. */
    val dataMessage: DataMessage? = null,
    /**
     * A restore waiting on the lifter, because it would replace something they have (#104).
     *
     * The one operation in the app that can destroy data logged since, so the one place a
     * confirmation earns its keep.
     */
    val confirmingRestore: ConfirmRestore? = null,
)

/** A backup ready to restore, and what restoring it would replace. */
data class ConfirmRestore(val backup: Backup, val replacing: WouldReplace)

/** The outcome of writing the lifter's data out, in their terms. */
sealed interface DataMessage {
    data class Exported(val sessions: Int) : DataMessage

    data class BackedUp(val sessions: Int) : DataMessage

    data class Restored(val sessions: Int) : DataMessage

    /** The file picked to restore from was not a backup this app can read, and why. */
    data class NotRestorable(val reason: String) : DataMessage

    /** The file picked to import was not a FitNotes backup this app can read, and why. */
    data class NotImportable(val reason: String) : DataMessage

    /** What a FitNotes import brought in, and what it left behind and why. */
    data class Imported(val result: ImportResult) : DataMessage

    data object Failed : DataMessage
}

/**
 * Everything that is the lifter's rather than a routine's, in one place.
 *
 * Nothing here rebuilds a routine: the unit changes what is shown and never what is stored, the
 * theme is the app's, and the rest-timer state is read rather than changed.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val profile = database.profileRepository()
    private val sessions = SessionRepository(
        database = database,
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
        zone = ZoneId.systemDefault(),
        profile = profile,
    )
    private val alarms = RestAlarmScheduler(application)
    private val notifications = RestNotifications(application)

    private val backups = BackupRepository(database)

    private val restAlerts = MutableStateFlow(readRestAlerts())
    private val dataMessage = MutableStateFlow<DataMessage?>(null)
    private val confirming = MutableStateFlow<ConfirmRestore?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        profile.observeDisplayUnit(),
        profile.observeTheme(),
        restAlerts,
        dataMessage,
        confirming,
        ::SettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun setDisplayUnit(unit: WeightUnit) {
        viewModelScope.launch { profile.setDisplayUnit(unit) }
    }

    fun setTheme(theme: Theme) {
        viewModelScope.launch { profile.setTheme(theme) }
    }

    /**
     * Reads the rest-timer permissions again.
     *
     * Both are granted somewhere else and both are revocable, so the answer is read every time the
     * lifter comes back to this screen — which is exactly when they have just been to fix one.
     */
    fun recheckRestAlerts() {
        restAlerts.value = readRestAlerts()
    }

    /**
     * Writes every finished session to [destination] as CSV for a spreadsheet (#98).
     *
     * Off the main thread, because a couple of years of training is a few thousand rows; and a
     * failure is said rather than swallowed, since a lifter who thinks they exported and did not
     * finds out when they go looking for the file.
     */
    fun exportForSpreadsheets(destination: Uri) {
        viewModelScope.launch {
            dataMessage.value = runCatching {
                val all = sessions.observeRecent(limit = Int.MAX_VALUE).first()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destination)
                        ?.bufferedWriter()
                        ?.use { it.write(spreadsheetExport(all)) }
                        ?: error("nowhere to write")
                }
                DataMessage.Exported(all.size)
            }.getOrElse { DataMessage.Failed }
        }
    }

    /**
     * Writes the complete backup to [destination] (#97).
     *
     * Everything that makes the app the lifter's, losslessly. Said when done, with how many
     * sessions it holds, because that is how a lifter recognises the file they will need later.
     */
    fun backUp(destination: Uri) {
        viewModelScope.launch {
            dataMessage.value = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(destination)
                    ?.use { backups.write(it) }
                    ?: error("nowhere to write")
                DataMessage.BackedUp(sessions.observeRecent(limit = Int.MAX_VALUE).first().size)
            }.getOrElse { DataMessage.Failed }
        }
    }

    /**
     * Replaces everything with what the backup at [source] holds (#97).
     *
     * All or nothing: a file that is not a backup, or cannot be applied, changes nothing and says
     * why rather than leaving the lifter to wonder what happened.
     */
    fun restore(source: Uri) {
        viewModelScope.launch {
            dataMessage.value = try {
                val backup = getApplication<Application>().contentResolver.openInputStream(source)
                    ?.use { backups.read(it) }
                    ?: throw NotABackup("the file could not be opened")
                // Asked first where there is anything to lose; a new phone is simply restored.
                val replacing = backups.wouldReplace()
                if (!replacing.isNothing) {
                    confirming.value = ConfirmRestore(backup, replacing)
                    return@launch
                }
                backups.restore(backup)
                // Read again now: a restore is when a phone is new to the app, and the one moment
                // the lifter is looking at whether it is set up right.
                recheckRestAlerts()
                DataMessage.Restored(backup.sessions)
            } catch (refused: NotABackup) {
                DataMessage.NotRestorable(refused.message.orEmpty())
            } catch (failed: Exception) {
                DataMessage.NotRestorable(failed.message.orEmpty())
            }
        }
    }

    /**
     * Brings a FitNotes backup in, merging with whatever is already here (#96).
     *
     * Copied to a private file first, because the parser reads SQLite and a document from the
     * picker is a stream, not a file. Nothing logged here is touched.
     */
    fun importFitNotes(source: Uri) {
        viewModelScope.launch {
            dataMessage.value = try {
                val app = getApplication<Application>()
                val copy = File(app.cacheDir, "import.fitnotes")
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(source)?.use { input ->
                        copy.outputStream().use { input.copyTo(it) }
                    } ?: error("the file could not be opened")
                }
                val records = withContext(Dispatchers.IO) { FitNotesParser.parse(copy) }
                copy.delete()
                val bank = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)
                    .observeBank().first()
                DataMessage.Imported(ImportRepository(database).import(records, bank))
            } catch (failed: Exception) {
                DataMessage.NotImportable(failed.message.orEmpty())
            }
        }
    }

    /** Replaces what is here with the backup the lifter was warned about. */
    fun confirmRestore() {
        val confirmed = confirming.value ?: return
        confirming.value = null
        viewModelScope.launch {
            dataMessage.value = try {
                backups.restore(confirmed.backup)
                recheckRestAlerts()
                DataMessage.Restored(confirmed.backup.sessions)
            } catch (failed: Exception) {
                DataMessage.NotRestorable(failed.message.orEmpty())
            }
        }
    }

    /** Leaves everything exactly as it was. */
    fun cancelRestore() {
        confirming.value = null
    }

    fun dismissDataMessage() {
        dataMessage.value = null
    }

    private fun readRestAlerts() = RestAlerts(
        alarmsAreReliable = alarms.canWakeReliably(),
        countdownVisible = notifications.canBeSeen(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
