package com.tylergimpel.gymtracker.ui.settings

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import com.tylergimpel.gymtracker.ui.theme.ControlShape
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.RowDivider
import com.tylergimpel.gymtracker.ui.theme.CardRow
import com.tylergimpel.gymtracker.ui.theme.AppCard
import androidx.compose.ui.platform.testTag
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.ui.RestFix
import com.tylergimpel.gymtracker.ui.WeightUnitChips
import com.tylergimpel.gymtracker.ui.settingsFor
import com.tylergimpel.gymtracker.data.ImportResult
import com.tylergimpel.gymtracker.data.RefusedBecause
import com.tylergimpel.gymtracker.data.ListedImport
import com.tylergimpel.gymtracker.domain.NameDecision
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate

/**
 * One place for everything that is the lifter's rather than a routine's (#91).
 *
 * What lives here is what somebody looks for once and then leaves alone: the unit they think in,
 * whether the app is light or dark, the plates in their gym, who they are, and whether the rest
 * timer can reach them. Nothing here touches a routine.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPlates: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenExcluded: () -> Unit,
    /** Settings proper, or the lifter's data (#148). */
    section: SettingsSection = SettingsSection.SETTINGS,
    /** Opened from the first screen's "Import from FitNotes": start the picker straight away. */
    importOnArrival: Boolean = false,
    onImportStarted: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system's own save dialog: the lifter picks where it goes, and no account or network is
    // involved anywhere, the same as the rest of the app.
    val exportForSpreadsheets = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { destination -> destination?.let(viewModel::exportForSpreadsheets) }
    val backUp = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination -> destination?.let(viewModel::backUp) }
    val importFitNotes = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source -> source?.let(viewModel::importFitNotes) }
    LaunchedEffect(importOnArrival) {
        if (importOnArrival) {
            onImportStarted()
            importFitNotes.launch(arrayOf("*/*"))
        }
    }
    val restore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source -> source?.let(viewModel::restore) }

    // Both permissions are granted in the system's own settings, so the answer is read again
    // every time the lifter comes back here — which is exactly when they have just fixed one.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckRestAlerts()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        state = state,
        section = section,
        onBack = onBack,
        onOpenPlates = onOpenPlates,
        onOpenProfile = onOpenProfile,
        onOpenExcluded = onOpenExcluded,
        onDisplayUnit = viewModel::setDisplayUnit,
        onTheme = viewModel::setTheme,
        onShowTipsAgain = viewModel::showTipsAgain,
        onFixRestAlerts = { fix -> context.startActivity(settingsFor(fix, context)) },
        onExportForSpreadsheets = {
            exportForSpreadsheets.launch("gym-tracker-export-${LocalDate.now()}.csv")
        },
        onBackUp = { backUp.launch("gym-tracker-backup-${LocalDate.now()}.json") },
        // Any file: FitNotes' backup has its own extension, which no system knows the type of.
        onImportFitNotes = { importFitNotes.launch(arrayOf("*/*")) },
        // Any file, because some file managers label JSON as plain text or nothing at all; the
        // backup itself says whether it is one.
        onRestore = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onDismissDataMessage = viewModel::dismissDataMessage,
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        onAskToUndo = viewModel::askToUndo,
        onUndoImport = viewModel::undoImport,
        onKeepImport = viewModel::keepImport,
        onDecide = viewModel::decide,
        onChooseSameAs = viewModel::chooseSameAs,
        onImportWithDecisions = viewModel::importWithDecisions,
        onCancelPreview = viewModel::cancelPreview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    section: SettingsSection = SettingsSection.SETTINGS,
    onBack: () -> Unit = {},
    onOpenPlates: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenExcluded: () -> Unit = {},
    onDisplayUnit: (WeightUnit) -> Unit = {},
    onTheme: (Theme) -> Unit = {},
    onShowTipsAgain: () -> Unit = {},
    onFixRestAlerts: (RestFix) -> Unit = {},
    onExportForSpreadsheets: () -> Unit = {},
    onBackUp: () -> Unit = {},
    onImportFitNotes: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismissDataMessage: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onAskToUndo: (ListedImport) -> Unit = {},
    onUndoImport: () -> Unit = {},
    onKeepImport: () -> Unit = {},
    onDecide: (String, NameDecision) -> Unit = { _, _ -> },
    onChooseSameAs: (String?) -> Unit = {},
    onImportWithDecisions: () -> Unit = {},
    onCancelPreview: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (section == SettingsSection.DATA) R.string.settings_data else R.string.settings_title),
            )
        },
    ) { insets ->
        // A previewed import takes the whole screen until the lifter goes ahead or backs out.
        state.previewing?.let { previewing ->
            ImportPreviewScreen(
                previewing = previewing,
                insets = insets,
                onDecide = onDecide,
                onChooseSameAs = onChooseSameAs,
                onImport = onImportWithDecisions,
                onCancel = onCancelPreview,
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (section == SettingsSection.SETTINGS) {
            Setting(label = stringResource(R.string.settings_unit)) {
                // The same setting a weigh-in's chip settles, and the one every exercise falls
                // back to until it says otherwise.
                WeightUnitChips(selected = state.preferredUnit, onReadIn = onDisplayUnit)
            }

            Setting(label = stringResource(R.string.settings_theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Theme.SYSTEM to R.string.settings_theme_system,
                        Theme.LIGHT to R.string.settings_theme_light,
                        Theme.DARK to R.string.settings_theme_dark,
                    ).forEach { (theme, text) ->
                        Pill(stringResource(text), selected = state.theme == theme) { onTheme(theme) }
                    }
                }
            }

            // Whether a rest can reach the lifter, said before they miss one rather than after.
            // Each failure carries the way out of it; going to the system's settings without
            // being taken there is half an answer.
            Setting(label = stringResource(R.string.settings_rest)) {
                RestAlertLine(
                    fine = state.restAlerts.alarmsAreReliable,
                    fineText = R.string.settings_rest_alarms_fine,
                    brokenText = R.string.rest_unreliable,
                    fixText = R.string.rest_allow_alarms,
                    onFix = { onFixRestAlerts(RestFix.EXACT_ALARMS) },
                )
                RestAlertLine(
                    fine = state.restAlerts.countdownVisible,
                    fineText = R.string.settings_rest_countdown_fine,
                    brokenText = R.string.rest_countdown_hidden,
                    fixText = R.string.rest_allow_notifications,
                    onFix = { onFixRestAlerts(RestFix.NOTIFICATIONS) },
                )
            }

            // The workout's three tips, back for the next workout (#156). Once they are all
            // showing again there is nothing to bring back, and the row says when they will be.
            AppCard(Modifier.fillMaxWidth()) {
                CardRow(
                    stringResource(R.string.settings_show_tips),
                    subtitle = if (state.tipsGone) null else stringResource(R.string.settings_tips_back),
                    onClick = if (state.tipsGone) onShowTipsAgain else null,
                    modifier = Modifier.testTag(SHOW_TIPS_AGAIN),
                )
            }

            }

            if (section == SettingsSection.DATA) {
            // The lifter's data, going out. Named for what each file is, because the difference
            // between a backup and an export is the thing nobody finds out until it matters.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The backup first: it is the one that restores, and the one worth keeping.
                AppCard(Modifier.fillMaxWidth()) {
                    CardRow(stringResource(R.string.settings_backup), onClick = onBackUp)
                    RowDivider()
                    CardRow(stringResource(R.string.settings_restore), onClick = onRestore)
                    RowDivider()
                    CardRow(stringResource(R.string.settings_export), onClick = onExportForSpreadsheets)
                    RowDivider()
                    // The FitNotes backup, which is richer than its CSV and the only one this reads.
                    CardRow(
                        stringResource(R.string.settings_import),
                        subtitle = stringResource(R.string.settings_import_short),
                        onClick = onImportFitNotes,
                    )
                }
                // Where imports are listed, each with the way to take it back (#102).
                state.imports.forEach { listed ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.import_listed,
                                listed.importedAt.asLocalDay(),
                                listed.sessions,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onAskToUndo(listed) }) {
                            Text(stringResource(R.string.import_undo))
                        }
                    }
                }
                state.dataMessage?.let { message ->
                    Text(
                        text = when (message) {
                            is DataMessage.Exported ->
                                stringResource(R.string.settings_exported, message.sessions)
                            is DataMessage.BackedUp ->
                                stringResource(R.string.settings_backed_up, message.sessions)
                            is DataMessage.Restored ->
                                stringResource(R.string.settings_restored, message.sessions)
                            is DataMessage.NotRestorable -> stringResource(
                                when (message.because) {
                                    RefusedBecause.NOT_A_BACKUP -> R.string.restore_refused_not_backup
                                    RefusedBecause.NEWER_APP -> R.string.restore_refused_newer
                                    RefusedBecause.DOES_NOT_FIT -> R.string.restore_refused_does_not_fit
                                },
                            )
                            DataMessage.NotImportable -> stringResource(R.string.settings_not_importable)
                            is DataMessage.Imported -> importedText(message.result)
                            is DataMessage.AlreadyImported -> stringResource(
                                R.string.import_already,
                                message.importedAt.asLocalDay(),
                            )
                            is DataMessage.Undone -> stringResource(R.string.import_undone, message.sessions)
                            DataMessage.BackupFailed -> stringResource(R.string.settings_backup_failed)
                            DataMessage.Failed -> stringResource(R.string.settings_export_failed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    // A restore is the moment a phone is new to the app, and Android starts it with
                    // exact alarms denied — and denies them again after a system restore (#95). Said
                    // here, with the way to fix it, rather than found by a rest that ends late.
                    if (message is DataMessage.Restored && !state.restAlerts.alarmsAreReliable) {
                        Text(
                            text = stringResource(R.string.settings_restored_alarms),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { onFixRestAlerts(RestFix.EXACT_ALARMS) }) {
                            Text(stringResource(R.string.rest_allow_alarms))
                        }
                    }
                    TextButton(onClick = onDismissDataMessage) {
                        Text(stringResource(R.string.settings_ok))
                    }
                }
            }

            }

            state.confirmingUndo?.let { listed ->
                AlertDialog(
                    onDismissRequest = onKeepImport,
                    title = { Text(stringResource(R.string.import_undo_title)) },
                    text = { Text(stringResource(R.string.import_undo_body, listed.sessions)) },
                    confirmButton = {
                        TextButton(onClick = onUndoImport) { Text(stringResource(R.string.import_undo_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = onKeepImport) { Text(stringResource(R.string.import_undo_keep)) }
                    },
                )
            }

            state.confirmingRestore?.let {
                ConfirmRestoreDialog(confirm = it, onConfirm = onConfirmRestore, onCancel = onCancelRestore)
            }

        }
    }
}

/**
 * Said before a restore replaces what is here: what there is now, and what comes back instead.
 *
 * Never a silent merge and never a silent wipe. Declining is as easy as confirming, and leaves
 * everything exactly as it was.
 */
@Composable
private fun ConfirmRestoreDialog(confirm: ConfirmRestore, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val here = confirm.replacing
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.restore_confirm_body,
                    here.sessions,
                    here.weighIns,
                    confirm.backup.sessions,
                ) + if (here.routine) " " + stringResource(R.string.restore_confirm_routine) else "",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm_replace)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_confirm_keep)) }
        },
    )
}

/** The day an import happened, as the lifter's phone writes a date. */
private fun Instant.asLocalDay(): String =
    atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * What an import did, and what it did not: nothing left behind goes unsaid, because a gap found
 * later is a gap the lifter stops trusting the whole import over.
 */
@Composable
private fun importedText(result: ImportResult): String = buildList {
    add(stringResource(R.string.import_done, result.sessions, result.sets))
    if (result.alreadyHere > 0) add(stringResource(R.string.import_already_here, result.alreadyHere))
    if (result.created.isNotEmpty()) add(stringResource(R.string.import_created, result.created.size))
    if (result.createdWithoutMuscles.isNotEmpty()) {
        add(
            stringResource(
                R.string.import_created_without_muscles,
                result.createdWithoutMuscles.sorted().joinToString(", "),
            ),
        )
    }
    if (result.unmatched.isNotEmpty()) {
        add(
            stringResource(
                R.string.import_unmatched,
                result.unmatched.values.sum(),
                result.unmatched.keys.sorted().joinToString(", "),
            ),
        )
    }
    val later = result.notYetSupported.values.sum()
    if (later > 0) add(stringResource(R.string.import_unreadable, later))
    if (!result.volumeReliable) add(stringResource(R.string.import_volume_approximate))
    add(stringResource(R.string.import_how_to_undo))
}.joinToString(" ")

@Composable
private fun Setting(label: String, why: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        why?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun RestAlertLine(
    fine: Boolean,
    @StringRes fineText: Int,
    @StringRes brokenText: Int,
    @StringRes fixText: Int,
    onFix: () -> Unit,
) {
    Text(
        text = stringResource(if (fine) fineText else brokenText),
        style = MaterialTheme.typography.bodyMedium,
        color = if (fine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
    if (!fine) TextButton(onClick = onFix) { Text(stringResource(fixText)) }
}

/** Which half of what used to be one Settings screen this is (#148). */
enum class SettingsSection { SETTINGS, DATA }

/** A choice among a few, as the new look draws one: filled with the accent when chosen. */
@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (selected) colors.accent else colors.raised,
        contentColor = if (selected) colors.onAccent else colors.text,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

/** Handle for the tests. */
const val SHOW_TIPS_AGAIN = "show-tips-again"
