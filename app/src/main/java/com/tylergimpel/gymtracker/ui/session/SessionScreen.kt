package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.Drop
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Side

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
        onRightDraftChange = viewModel::changeRightDraft,
        onTogglePerSide = viewModel::togglePerSide,
        onLogSet = viewModel::logSet,
        onEditEffort = viewModel::startEditing,
        onEditingDraftChange = viewModel::changeEditingDraft,
        onSaveEditing = viewModel::saveEditing,
        onDeleteEditing = viewModel::deleteEditing,
        onCancelEditing = viewModel::cancelEditing,
        onAddDrop = viewModel::startAddingDrop,
        onDropDraftChange = viewModel::changeDropDraft,
        onSaveDrop = viewModel::saveDrop,
        onCancelDrop = viewModel::cancelAddingDrop,
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
    onRightDraftChange: (SetDraft) -> Unit = {},
    onTogglePerSide: () -> Unit = {},
    onLogSet: () -> Unit = {},
    onEditEffort: (Long, PerformedSet, Effort) -> Unit = { _, _, _ -> },
    onEditingDraftChange: (SetDraft) -> Unit = {},
    onSaveEditing: () -> Unit = {},
    onDeleteEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
    onAddDrop: (Effort) -> Unit = {},
    onDropDraftChange: (SetDraft) -> Unit = {},
    onSaveDrop: () -> Unit = {},
    onCancelDrop: () -> Unit = {},
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
                    SetEntryControls(
                        exerciseName = selected.name,
                        state = state,
                        onDraftChange = onDraftChange,
                        onRightDraftChange = onRightDraftChange,
                        onTogglePerSide = onTogglePerSide,
                        onLogSet = onLogSet,
                    )
                    HorizontalDivider()
                }
            }

            if (state.session == null) {
                item { EmptySession(hasSelection = state.selected != null) }
            }

            items(state.exercises, key = SessionExercise::id) { performed ->
                LoggedExercise(performed, onEditEffort, onAddDrop)
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

    state.addingDrop?.let { drop ->
        AddDropDialog(drop, onDropDraftChange, onSaveDrop, onCancelDrop)
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
    state: SessionUiState,
    onDraftChange: (SetDraft) -> Unit,
    onRightDraftChange: (SetDraft) -> Unit,
    onTogglePerSide: () -> Unit,
    onLogSet: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.titleMedium)

        if (state.perSide) {
            Text(
                text = stringResource(R.string.set_side_left),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetDraftFields(state.draft, onDraftChange)

        // A single entry by default; expanding is for tracking an imbalance, and the two sides carry
        // their own weights because that is the only reason to bother.
        if (state.perSide) {
            Text(
                text = stringResource(R.string.set_side_right),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SetDraftFields(state.rightDraft, onRightDraftChange, showType = false)
        }

        FilterChip(
            selected = state.perSide,
            onClick = onTogglePerSide,
            label = { Text(stringResource(R.string.set_per_side)) },
        )

        Button(
            onClick = onLogSet,
            enabled = state.draft.isLoggable && (!state.perSide || state.rightDraft.isLoggable),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.session_log_set))
        }
    }
}

/** The weight, reps, note and type fields, shared by the entry controls and the edit dialog. */
@Composable
private fun SetDraftFields(
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    showType: Boolean = true,
    showNote: Boolean = true,
) {
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
        if (showNote) {
            OutlinedTextField(
                value = draft.note.orEmpty(),
                onValueChange = { onChange(draft.copy(note = it.takeIf(String::isNotBlank))) },
                label = { Text(stringResource(R.string.set_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showType) {
            SetTypeChooser(draft.type) { onChange(draft.copy(type = it)) }
        }
    }
}

/**
 * Which kind of set this is.
 *
 * Always on screen rather than hidden behind a menu, because the type is chosen by the lifter and
 * never inferred: a warm-up that goes in as working quietly inflates every volume figure built on
 * it, and nothing later can tell the difference.
 */
@Composable
private fun SetTypeChooser(selected: SetType, onSelect: (SetType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.set_type), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.label)) },
                )
            }
        }
    }
}

@Composable
private fun LoggedExercise(
    performed: SessionExercise,
    onEditEffort: (Long, PerformedSet, Effort) -> Unit,
    onAddDrop: (Effort) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(performed.exercise.name, style = MaterialTheme.typography.titleMedium)
        performed.sets.forEachIndexed { position, set ->
            LoggedSetRows(
                // Numbered at render rather than by the stored set index, per ADR 0003: the index
                // is identity and tolerates gaps, while the number a lifter reads should just count
                // the sets in front of them.
                number = position + 1,
                set = set,
                onEditEffort = { effort -> onEditEffort(performed.id, set, effort) },
                onAddDrop = onAddDrop,
            )
        }
    }
}

/**
 * One set, however many rows recorded it.
 *
 * A per-side set shows both efforts under one number, and drops are indented under the effort they
 * continue — so what reads as one set on screen is one set in the log.
 */
@Composable
private fun LoggedSetRows(
    number: Int,
    set: PerformedSet,
    onEditEffort: (Effort) -> Unit,
    onAddDrop: (Effort) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        set.efforts.forEachIndexed { index, effort ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditEffort(effort) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (index == 0) number.toString() else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                effort.side?.let {
                    Text(
                        text = stringResource(it.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(effort.summary(), style = MaterialTheme.typography.bodyLarge)
                if (index == 0 && set.type != SetType.WORKING) {
                    Text(
                        text = stringResource(set.type.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                effort.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            effort.drops.forEach { drop -> DropRow(drop) }

            TextButton(onClick = { onAddDrop(effort) }) {
                Text(stringResource(R.string.set_add_drop))
            }
        }
    }
}

@Composable
private fun DropRow(drop: Drop) {
    Text(
        text = stringResource(
            R.string.set_drop_summary,
            drop.orderIndex,
            drop.weight.asText(),
            drop.reps,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 2.dp),
    )
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
    editing: EditingEffort,
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
private fun AddDropDialog(
    adding: AddingDrop,
    onChange: (SetDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.set_drop)) },
        // Neither a type nor a note: a drop is part of the set it continues, carrying no type of
        // its own and nowhere to store a note. Offering fields that discard what is typed into them
        // is worse than not offering them.
        text = { SetDraftFields(adding.draft, onChange, showType = false, showNote = false) },
        confirmButton = {
            TextButton(onClick = onSave, enabled = adding.draft.isLoggable) {
                Text(stringResource(R.string.session_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
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
private fun Effort.summary(): String =
    stringResource(R.string.set_summary, weight.asText(), reps)

private val Side.label: Int
    get() = when (this) {
        Side.LEFT -> R.string.set_side_left
        Side.RIGHT -> R.string.set_side_right
    }
