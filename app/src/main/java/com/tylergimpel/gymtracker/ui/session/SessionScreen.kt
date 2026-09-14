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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.LoggedSet
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft

/**
 * The plain logging screen: pick an exercise, enter a weight and reps, see what was logged.
 *
 * Deliberately not the settled live logging design in `docs/design/logging-screen.md` — that wheel
 * belongs to the training-loop spec and needs a recommendation to render. This is the functional
 * path underneath it.
 */
@Composable
fun SessionScreen(
    onPickExercise: () -> Unit,
    onOpenRoutine: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SessionScreen(
        state = state,
        onPickExercise = onPickExercise,
        onDraftChange = viewModel::changeDraft,
        onLogSet = viewModel::logSet,
        onEditSet = viewModel::startEditing,
        onEditingDraftChange = viewModel::changeEditingDraft,
        onSaveEditing = viewModel::saveEditing,
        onDeleteEditing = viewModel::deleteEditing,
        onCancelEditing = viewModel::cancelEditing,
        onSessionNoteChange = viewModel::noteSession,
        onFinish = viewModel::startFinishing,
        onFinishAs = viewModel::finishSession,
        onCancelFinishing = viewModel::cancelFinishing,
        onOpenRoutine = onOpenRoutine,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onPickExercise: () -> Unit = {},
    onDraftChange: (SetDraft) -> Unit = {},
    onLogSet: () -> Unit = {},
    onEditSet: (LoggedSet) -> Unit = {},
    onEditingDraftChange: (SetDraft) -> Unit = {},
    onSaveEditing: () -> Unit = {},
    onDeleteEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
    onSessionNoteChange: (String) -> Unit = {},
    onFinish: () -> Unit = {},
    onFinishAs: (RoutineDay?) -> Unit = {},
    onCancelFinishing: () -> Unit = {},
    onOpenRoutine: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.session_title))
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
                    TextButton(onClick = onOpenRoutine) {
                        Text(stringResource(R.string.session_open_routine))
                    }
                    if (state.session != null) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickExercise,
                text = {
                    Text(
                        stringResource(
                            if (state.selected == null) {
                                R.string.session_pick_exercise
                            } else {
                                R.string.session_change_exercise
                            },
                        ),
                    )
                },
                icon = {},
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets,
        ) {
            item { NextUp(state.routine) }

            state.selected?.let { selected ->
                item {
                    SetEntryControls(selected.name, state.draft, onDraftChange, onLogSet)
                    HorizontalDivider()
                }
            }

            if (state.session == null) {
                item { EmptySession(hasSelection = state.selected != null) }
            }

            items(state.exercises, key = SessionExercise::id) { exercise ->
                LoggedExercise(exercise, onEditSet)
                HorizontalDivider()
            }

            state.session?.let { session ->
                item { SessionNote(session.id, session.note, onSessionNoteChange) }
            }
        }
    }

    state.editing?.let { editing ->
        EditSetDialog(editing, onEditingDraftChange, onSaveEditing, onDeleteEditing, onCancelEditing)
    }

    if (state.finishing) {
        WhichDayDialog(state.routine, onFinishAs, onCancelFinishing)
    }
}

/**
 * What comes next in the routine.
 *
 * Derived from which days of the current cycle are still undone, never from the date — so this line
 * reads the same after a fortnight off as it did the evening before.
 */
@Composable
private fun NextUp(progress: RoutineProgress?) {
    val cursor = progress?.cursor
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = if (cursor == null) {
                stringResource(R.string.session_no_routine)
            } else {
                stringResource(R.string.session_next_up, cursor.name)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        progress?.cycle?.let {
            Text(
                text = stringResource(R.string.session_cycle, it.ordinal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Asks which routine day the finishing session realised, defaulting to nothing in particular.
 *
 * The cursor is offered first but any day can be chosen, because training out of order is ordinary
 * and the cursor holding its place afterwards is the whole point of deriving it.
 */
@Composable
private fun WhichDayDialog(
    progress: RoutineProgress?,
    onFinishAs: (RoutineDay?) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_which_day)) },
        text = {
            Column {
                progress?.routine?.rotation.orEmpty().forEach { day ->
                    TextButton(onClick = { onFinishAs(day) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (progress?.cursor?.id == day.id) {
                                "${day.name}  ·  ${stringResource(R.string.routine_next_up)}"
                            } else {
                                day.name
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFinishAs(null) }) {
                Text(stringResource(R.string.session_no_day))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EmptySession(hasSelection: Boolean) {
    Text(
        text = stringResource(
            if (hasSelection) R.string.session_empty_selected else R.string.session_empty,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun SetEntryControls(
    exerciseName: String,
    draft: SetDraft,
    onDraftChange: (SetDraft) -> Unit,
    onLogSet: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.titleMedium)
        SetDraftFields(draft, onDraftChange)
        Button(
            onClick = onLogSet,
            enabled = draft.isLoggable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.session_log_set))
        }
    }
}

/** The weight, reps and note fields, shared by the entry controls and the edit dialog. */
@Composable
private fun SetDraftFields(draft: SetDraft, onChange: (SetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.weightText,
                onValueChange = { onChange(draft.copy(weightText = it)) },
                label = { Text(stringResource(R.string.set_weight_kg)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.repsText,
                onValueChange = { onChange(draft.copy(repsText = it)) },
                label = { Text(stringResource(R.string.set_reps)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = draft.note.orEmpty(),
            onValueChange = { onChange(draft.copy(note = it.takeIf(String::isNotBlank))) },
            label = { Text(stringResource(R.string.set_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LoggedExercise(exercise: SessionExercise, onEditSet: (LoggedSet) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
        exercise.sets.forEachIndexed { position, set ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditSet(set) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Numbered at render rather than by the stored set index, per ADR 0003: the index
                // is identity and tolerates gaps, while the number a lifter reads should just count
                // the sets in front of them.
                Text(
                    text = (position + 1).toString(),
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

/**
 * The session note.
 *
 * The field owns its own text and writes through, rather than rendering what has come back from the
 * database: a value that arrives an asynchronous write later drops and reorders characters as fast
 * as they are typed.
 */
@Composable
private fun SessionNote(sessionId: Long, stored: String?, onChange: (String) -> Unit) {
    var text by rememberSaveable(sessionId) { mutableStateOf(stored.orEmpty()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(stringResource(R.string.session_note)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

@Composable
private fun EditSetDialog(
    editing: EditingSet,
    onChange: (SetDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_edit_set)) },
        text = { SetDraftFields(editing.draft, onChange) },
        confirmButton = {
            TextButton(onClick = onSave, enabled = editing.draft.isLoggable) {
                Text(stringResource(R.string.session_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.session_delete)) }
        },
    )
}

@Composable
private fun LoggedSet.summary(): String =
    stringResource(R.string.set_summary, weight.asText(), reps)
