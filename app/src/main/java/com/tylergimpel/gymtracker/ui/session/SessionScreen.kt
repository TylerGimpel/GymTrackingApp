package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.domain.LoggedSet
import com.tylergimpel.gymtracker.domain.SessionExercise

/**
 * The plain logging screen: pick an exercise, enter a weight and reps, see what was logged.
 *
 * Deliberately not the settled live logging design in `docs/design/logging-screen.md` — that wheel
 * belongs to the training-loop spec and needs a recommendation to render. This is the functional
 * path underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onPickExercise: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Session")
                        state.session?.let {
                            Text(
                                text = it.date.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.session != null) {
                        TextButton(onClick = viewModel::finishSession) { Text("Finish") }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickExercise,
                text = { Text(if (state.selected == null) "Pick an exercise" else "Change exercise") },
                icon = {},
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets,
        ) {
            state.selected?.let { selected ->
                item {
                    SetEntry(
                        exerciseName = selected.name,
                        state = state,
                        viewModel = viewModel,
                    )
                    HorizontalDivider()
                }
            }

            if (state.session == null) {
                item { EmptySession(hasSelection = state.selected != null) }
            }

            items(state.exercises, key = SessionExercise::id) { exercise ->
                LoggedExercise(exercise, viewModel::startEditing)
                HorizontalDivider()
            }

            state.session?.let { session ->
                item {
                    OutlinedTextField(
                        value = session.note.orEmpty(),
                        onValueChange = viewModel::noteSession,
                        label = { Text("Session note") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
    }

    state.editing?.let { editing ->
        EditSetDialog(editing, viewModel)
    }
}

@Composable
private fun EmptySession(hasSelection: Boolean) {
    Text(
        text = if (hasSelection) {
            "Log your first set and the session starts."
        } else {
            "No session yet. Pick an exercise to start logging."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun SetEntry(
    exerciseName: String,
    state: SessionUiState,
    viewModel: SessionViewModel,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.draft.weightText,
                onValueChange = viewModel::editWeight,
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.draft.repsText,
                onValueChange = viewModel::editReps,
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = state.draft.note.orEmpty(),
            onValueChange = viewModel::editNote,
            label = { Text("Set note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::logSet,
            enabled = state.draft.isLoggable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log set")
        }
    }
}

@Composable
private fun LoggedExercise(exercise: SessionExercise, onEditSet: (LoggedSet) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
        exercise.sets.forEach { set ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditSet(set) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The set index is shown rather than the row's position, so a gap left by a
                // deletion is visible instead of being silently closed up.
                Text(
                    text = set.setIndex.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(set.summary(), style = MaterialTheme.typography.bodyLarge)
                set.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSetDialog(editing: EditingSet, viewModel: SessionViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEditing,
        title = { Text("Edit set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editing.draft.weightText,
                    onValueChange = viewModel::editEditingWeight,
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = editing.draft.repsText,
                    onValueChange = viewModel::editEditingReps,
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = editing.draft.note.orEmpty(),
                    onValueChange = viewModel::editEditingNote,
                    label = { Text("Set note") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveEditing, enabled = editing.draft.isLoggable) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::deleteEditing) { Text("Delete") }
        },
    )
}

private fun LoggedSet.summary(): String = "${weight.asText()} kg × $reps"
