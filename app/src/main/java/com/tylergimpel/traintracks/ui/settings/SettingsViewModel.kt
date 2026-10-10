package com.tylergimpel.traintracks.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.traintracks.data.ExerciseAliasStore
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.profileRepository
import com.tylergimpel.traintracks.domain.Theme
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.rest.RestAlarmScheduler
import com.tylergimpel.traintracks.rest.RestNotifications
import com.tylergimpel.traintracks.ui.session.RestAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import com.tylergimpel.traintracks.data.SessionRepository
import com.tylergimpel.traintracks.data.BackupRepository
import com.tylergimpel.traintracks.data.NotABackup
import com.tylergimpel.traintracks.data.Backup
import com.tylergimpel.traintracks.data.WouldReplace
import com.tylergimpel.traintracks.data.RefusedBecause
import kotlinx.coroutines.CancellationException
import com.tylergimpel.traintracks.data.ExerciseRepository
import com.tylergimpel.traintracks.data.FitNotesParser
import com.tylergimpel.traintracks.data.ImportRepository
import com.tylergimpel.traintracks.data.ImportResult
import com.tylergimpel.traintracks.data.ImportOutcome
import com.tylergimpel.traintracks.data.ListedImport
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.NameDecision
import java.time.Instant
import java.io.File
import com.tylergimpel.traintracks.domain.spreadsheetExport
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
    /** What the last export, backup, restore or import did, said once and then cleared. */
    val dataMessage: DataMessage? = null,
    /**
     * A restore waiting on the lifter, because it would replace something they have (#104).
     *
     * The one operation in the app that can destroy data logged since, so the one place a
     * confirmation earns its keep.
     */
    val confirmingRestore: ConfirmRestore? = null,
    /** Every import still standing, newest first, each of which can be undone (#102). */
    val imports: List<ListedImport> = emptyList(),
    /** An import the lifter asked to undo, waiting on them to confirm: it removes history. */
    val confirmingUndo: ListedImport? = null,
    /** A file read and previewed, waiting on the lifter to go ahead or back out (#99, #103). */
    val previewing: Previewing? = null,
    /** Whether any workout tip has gone, so Show tips again has something to bring back (#156). */
    val tipsGone: Boolean = false,
)

/** A backup ready to restore, and what restoring it would replace. */
data class ConfirmRestore(val backup: Backup, val replacing: WouldReplace)

/** What happened to the lifter's data, going out or coming in, in their terms. */
sealed interface DataMessage {
    /** Whether it is a failure, which the screen says in the colour for one. */
    val isError: Boolean get() = false

    data class Exported(val sessions: Int) : DataMessage

    data class BackedUp(val sessions: Int) : DataMessage

    data class Restored(val sessions: Int) : DataMessage

    /** The file picked to restore from could not be restored, and why; nothing changed. */
    data class NotRestorable(val because: RefusedBecause) : DataMessage {
        override val isError: Boolean get() = true
    }

    /** The file picked to import could not be read as a FitNotes backup; nothing changed. */
    data object NotImportable : DataMessage {
        override val isError: Boolean get() = true
    }

    /** The backup could not be written where the lifter chose. */
    data object BackupFailed : DataMessage {
        override val isError: Boolean get() = true
    }

    /** What a FitNotes import brought in, and what it left behind and why. */
    data class Imported(val result: ImportResult) : DataMessage

    /** That exact file was already imported, when; nothing was added the second time (#102). */
    data class AlreadyImported(val importedAt: Instant) : DataMessage

    /** An import was undone, and how many sessions it took with it. */
    data class Undone(val sessions: Int) : DataMessage

    /** The export could not be written where the lifter chose. */
    data object Failed : DataMessage {
        override val isError: Boolean get() = true
    }
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

    private val importer = ImportRepository(
        database,
        profile = profile,
        formerNames = { ExerciseAliasStore(application).loadFormerNames() },
    )
    private val confirmingUndo = MutableStateFlow<ListedImport?>(null)
    private val previewing = MutableStateFlow<Previewing?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            profile.observeDisplayUnit(),
            profile.observeTheme(),
            restAlerts,
            dataMessage,
            confirming,
        ) { unit, theme, alerts, message, restoring ->
            SettingsUiState(unit, theme, alerts, message, restoring)
        },
        importer.observeListed(),
        confirmingUndo,
        previewing,
        profile.observeTipsDone(),
    ) { settings, imports, undoing, pending, tipsDone ->
        settings.copy(imports = imports, confirmingUndo = undoing, previewing = pending, tipsGone = tipsDone.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun setDisplayUnit(unit: WeightUnit) {
        viewModelScope.launch { profile.setDisplayUnit(unit) }
    }

    fun setTheme(theme: Theme) {
        viewModelScope.launch { profile.setTheme(theme) }
    }

    /** Brings all three workout tips back for the next workout (#156). */
    fun showTipsAgain() {
        viewModelScope.launch { profile.showTipsAgain() }
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
            dataMessage.value = try {
                val all = sessions.observeRecent(limit = Int.MAX_VALUE).first()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destination)
                        ?.bufferedWriter()
                        ?.use { it.write(spreadsheetExport(all)) }
                        ?: error("nowhere to write")
                }
                DataMessage.Exported(all.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                DataMessage.Failed
            }
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
            dataMessage.value = try {
                val written = getApplication<Application>().contentResolver.openOutputStream(destination)
                    ?.use { backups.write(it) }
                    ?: error("nowhere to write")
                DataMessage.BackedUp(written)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                DataMessage.BackupFailed
            }
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
                    ?: throw NotABackup(RefusedBecause.NOT_A_BACKUP, "the file could not be opened")
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
                DataMessage.NotRestorable(refused.because)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // Anything else went wrong applying it; the transaction rolled back, so it is a
                // backup that does not fit this app rather than a half-done restore.
                DataMessage.NotRestorable(RefusedBecause.DOES_NOT_FIT)
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
            val app = getApplication<Application>()
            val copy = File(app.cacheDir, "import.fitnotes")
            dataMessage.value = try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(source)?.use { input ->
                        copy.outputStream().use { input.copyTo(it) }
                    } ?: error("the file could not be opened")
                }
                val records = withContext(Dispatchers.IO) { FitNotesParser.parse(copy) }
                val bank = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)
                    .observeBank().first()
                // A file already in is said so at once, rather than previewed and decided for nothing.
                importer.importedAt(records.fingerprint)?.let { DataMessage.AlreadyImported(it) } ?: run {
                    // Nothing is written until the lifter has seen what will be and gone ahead (#103).
                    val preview = importer.preview(records, bank)
                    previewing.value = Previewing(
                        records = records,
                        preview = preview,
                        decisions = preview.unmatched.associate { it.name to NameDecision.CreateOwn },
                        bank = bank.filterNot(Exercise::isTombstoned),
                    )
                    null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                DataMessage.NotImportable
            } finally {
                // Whatever happened: a copy of somebody's training log is not left lying about.
                copy.delete()
            }
        }
    }

    /** Records what one unmatched name means, closing the picker if it was open for it. */
    fun decide(name: String, decision: NameDecision) {
        previewing.value = previewing.value?.let {
            it.copy(decisions = it.decisions + (name to decision), choosingFor = null)
        }
    }

    /** Opens the picker to say what [name] is the same as, or closes it with null. */
    fun chooseSameAs(name: String?) {
        previewing.value = previewing.value?.copy(choosingFor = name)
    }

    /** Goes ahead with the previewed import, every name decided. */
    fun importWithDecisions() {
        val decided = previewing.value ?: return
        previewing.value = null
        viewModelScope.launch {
            dataMessage.value = try {
                outcomeMessage(importer.import(decided.records, decided.bank, decided.decisions))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                DataMessage.NotImportable
            }
        }
    }

    /** Backs out of the preview with nothing written. */
    fun cancelPreview() {
        previewing.value = null
    }

    private fun outcomeMessage(outcome: ImportOutcome): DataMessage = when (outcome) {
        is ImportOutcome.Imported -> DataMessage.Imported(outcome.result)
        is ImportOutcome.AlreadyImported -> DataMessage.AlreadyImported(outcome.importedAt)
    }

    /** Asks before undoing an import, because undoing removes the history it brought. */
    fun askToUndo(listed: ListedImport) {
        confirmingUndo.value = listed
    }

    fun keepImport() {
        confirmingUndo.value = null
    }

    /** Removes exactly what the confirmed import brought in, and nothing the lifter logged. */
    fun undoImport() {
        val listed = confirmingUndo.value ?: return
        confirmingUndo.value = null
        viewModelScope.launch {
            importer.undo(listed.id)
            dataMessage.value = DataMessage.Undone(listed.sessions)
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
            } catch (refused: NotABackup) {
                DataMessage.NotRestorable(refused.because)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                DataMessage.NotRestorable(RefusedBecause.DOES_NOT_FIT)
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
