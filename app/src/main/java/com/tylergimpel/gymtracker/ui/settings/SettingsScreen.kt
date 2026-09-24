package com.tylergimpel.gymtracker.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

            TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.settings_profile)) }
            TextButton(onClick = onOpenPlates) { Text(stringResource(R.string.settings_plates)) }
            // Everything excluded from swap and dislike, where each can be taken back (#93).
            TextButton(onClick = onOpenExcluded) { Text(stringResource(R.string.settings_excluded)) }
        }
    }
}

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
