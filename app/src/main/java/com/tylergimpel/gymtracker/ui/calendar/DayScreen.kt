package com.tylergimpel.gymtracker.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.ui.history.LoggedSetLines
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DayScreen(date: LocalDate, onBack: () -> Unit, viewModel: DayViewModel = viewModel()) {
    LaunchedEffect(date) { viewModel.open(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DayScreen(state = state, onBack = onBack)
}

/**
 * What was done on one day, read-only: every session on it, each exercise and each set (#118).
 *
 * A plain reading. How drops, supersets and per-side sets read as what they were is #119's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(state: DayUiState, onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).orEmpty()) },
                actions = { TextButton(onClick = onBack) { Text(stringResource(R.string.settings_done)) } },
            )
        },
    ) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            items(state.sessions, key = { it.session.id }) { logged ->
                PastSession(logged, state.lifterUnit)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PastSession(logged: LoggedSession, lifterUnit: WeightUnit) {
    Column(modifier = Modifier.padding(16.dp)) {
        val session = logged.session
        val heading = session.routineDayName ?: stringResource(R.string.day_session)
        Text(
            text = if (session.isImported) stringResource(R.string.history_imported, heading) else heading,
            style = MaterialTheme.typography.titleMedium,
        )
        session.note?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        logged.exercises.filter { it.sets.isNotEmpty() }.forEach { performed ->
            Text(
                text = performed.exercise.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            LoggedSetLines(performed.sets, performed.exercise.displayUnit, lifterUnit)
        }
    }
}
