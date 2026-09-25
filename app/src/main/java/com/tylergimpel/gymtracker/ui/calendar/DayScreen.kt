package com.tylergimpel.gymtracker.ui.calendar

import androidx.compose.foundation.border
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.Exercise
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun DayScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onOpenHistory: (Exercise) -> Unit = {},
    viewModel: DayViewModel = viewModel(),
) {
    LaunchedEffect(date) { viewModel.open(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DayScreen(state = state, onBack = onBack, onOpenHistory = onOpenHistory)
}

/**
 * What was done on one day, read-only: every session on it, each exercise and each set (#118).
 *
 * **In the shape it was logged in** (#119): a superset as a group, drops beneath their set, both
 * sides of a per-side set together, and an imported session saying which source it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(state: DayUiState, onBack: () -> Unit = {}, onOpenHistory: (Exercise) -> Unit = {}) {
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
                PastSession(logged, state, onOpenHistory)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PastSession(logged: LoggedSession, state: DayUiState, onOpenHistory: (Exercise) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        val session = logged.session
        Text(
            text = session.routineDayName ?: stringResource(R.string.day_session),
            style = MaterialTheme.typography.titleMedium,
        )
        // Where the data came from, by name, so an imported session is never mistaken for one
        // logged here (#119).
        session.importBatchId?.let { batch ->
            Text(
                text = state.importedFrom[batch]?.let { stringResource(R.string.day_imported_from, it.name) }
                    ?: stringResource(R.string.day_imported),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        session.note?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        logged.positions.forEach { position ->
            val performed = position.exercises.filter { it.sets.isNotEmpty() }
            if (performed.isEmpty()) return@forEach
            when (position) {
                is SessionPosition.Alone -> performed.forEach { PerformedExercise(it, state.lifterUnit, onOpenHistory) }
                // Together, as they were trained: a superset is neighbouring exercises done in turn.
                is SessionPosition.Grouped -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .padding(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.day_superset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    performed.forEach { PerformedExercise(it, state.lifterUnit, onOpenHistory) }
                }
            }
        }
    }
}

@Composable
private fun PerformedExercise(performed: SessionExercise, lifterUnit: WeightUnit, onOpenHistory: (Exercise) -> Unit) {
    // The exercise's whole history is a tap away from any one day of it (#121).
    TextButton(onClick = { onOpenHistory(performed.exercise) }, modifier = Modifier.padding(top = 4.dp)) {
        Text(text = performed.exercise.name, style = MaterialTheme.typography.titleSmall)
    }
    LoggedSetLines(performed.sets, performed.exercise.displayUnit, lifterUnit)
}
