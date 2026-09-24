package com.tylergimpel.gymtracker.ui.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
        onBack = onBack,
        onOpenPlates = onOpenPlates,
        onOpenProfile = onOpenProfile,
        onOpenExcluded = onOpenExcluded,
        onDisplayUnit = viewModel::setDisplayUnit,
        onTheme = viewModel::setTheme,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit = {},
    onOpenPlates: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenExcluded: () -> Unit = {},
    onDisplayUnit: (WeightUnit) -> Unit = {},
    onTheme: (Theme) -> Unit = {},
    onFixRestAlerts: (RestFix) -> Unit = {},
    onExportForSpreadsheets: () -> Unit = {},
    onBackUp: () -> Unit = {},
    onImportFitNotes: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismissDataMessage: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.settings_done)) }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Setting(
                label = stringResource(R.string.settings_unit),
                why = stringResource(R.string.settings_unit_why),
            ) {
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
                        FilterChip(
                            selected = state.theme == theme,
                            onClick = { onTheme(theme) },
                            label = { Text(stringResource(text)) },
                        )
                    }
                }
            }

            // Whether a rest can reach the lifter, said before they miss one rather than after.
            // Each failure carries the way out of it; going to the system's settings without
            // being taken there is half an answer.
            Setting(
                label = stringResource(R.string.settings_rest),
                why = stringResource(R.string.settings_rest_why),
            ) {
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

            HorizontalDivider()

            // The lifter's data, going out. Named for what each file is, because the difference
            // between a backup and an export is the thing nobody finds out until it matters.
            Setting(
                label = stringResource(R.string.settings_data),
                why = stringResource(R.string.settings_backup_why),
            ) {
                // The backup first: it is the one that restores, and the one worth keeping.
                TextButton(onClick = onBackUp) { Text(stringResource(R.string.settings_backup)) }
                TextButton(onClick = onRestore) { Text(stringResource(R.string.settings_restore)) }
                // Bringing history in from FitNotes, said plainly so the lossy CSV is not picked by
                // mistake: the backup is richer, and it is the only one this reads.
                Text(
                    text = stringResource(R.string.settings_import_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onImportFitNotes) {
                    Text(stringResource(R.string.settings_import))
                }
                Text(
                    text = stringResource(R.string.settings_export_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onExportForSpreadsheets) {
                    Text(stringResource(R.string.settings_export))
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
                            is DataMessage.NotRestorable ->
                                stringResource(R.string.settings_not_restorable, message.reason)
                            is DataMessage.NotImportable ->
                                stringResource(R.string.settings_not_importable, message.reason)
                            is DataMessage.Imported -> importedText(message.result)
                            DataMessage.Failed -> stringResource(R.string.settings_export_failed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message == DataMessage.Failed || message is DataMessage.NotRestorable ||
                            message is DataMessage.NotImportable
                        ) {
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

            HorizontalDivider()

            state.confirmingRestore?.let {
                ConfirmRestoreDialog(confirm = it, onConfirm = onConfirmRestore, onCancel = onCancelRestore)
            }

            TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.settings_profile)) }
            TextButton(onClick = onOpenPlates) { Text(stringResource(R.string.settings_plates)) }
            // Everything excluded from swap and dislike, where each can be taken back (#93).
            TextButton(onClick = onOpenExcluded) { Text(stringResource(R.string.settings_excluded)) }
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

/**
 * What an import did, and what it did not: nothing left behind goes unsaid, because a gap found
 * later is a gap the lifter stops trusting the whole import over.
 */
@Composable
private fun importedText(result: ImportResult): String = buildList {
    add(stringResource(R.string.import_done, result.sessions, result.sets))
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
    if (later > 0) add(stringResource(R.string.import_not_yet, later))
}.joinToString(" ")

@Composable
private fun Setting(label: String, why: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
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
