package com.tylergimpel.gymtracker.ui.bodyweight

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tylergimpel.gymtracker.domain.BodyweightDraft
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.ui.DayPickerDialog
import com.tylergimpel.gymtracker.ui.WeightUnitChips
import com.tylergimpel.gymtracker.ui.label
import java.time.LocalDate

/**
 * Where the lifter records what they weigh, and reads back what they have weighed.
 *
 * A screen of its own rather than a corner of the session, because a weigh-in is not training: it
 * happens on a morning the lifter does not go to the gym as readily as on one they do, and putting
 * it inside a session would be the first step toward it being counted as one.
 */
@Composable
fun BodyweightScreen(
    onBack: () -> Unit,
    viewModel: BodyweightViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BodyweightScreen(
        state = state,
        onBack = onBack,
        onDraftChange = viewModel::changeDraft,
        onReadIn = viewModel::readIn,
        onRecord = viewModel::record,
        onEdit = viewModel::startEditing,
        onEditingDraftChange = viewModel::changeEditingDraft,
        onSaveEditing = viewModel::saveEditing,
        onDeleteEditing = viewModel::deleteEditing,
        onCancelEditing = viewModel::cancelEditing,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyweightScreen(
    state: BodyweightUiState,
    onBack: () -> Unit = {},
    onDraftChange: (BodyweightDraft) -> Unit = {},
    onReadIn: (WeightUnit) -> Unit = {},
    onRecord: () -> Unit = {},
    onEdit: (BodyweightEntry) -> Unit = {},
    onEditingDraftChange: (BodyweightDraft) -> Unit = {},
    onSaveEditing: () -> Unit = {},
    onDeleteEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bodyweight_title)) },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.bodyweight_done)) }
                },
            )
        },
    ) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            item {
                WeighInControls(
                    draft = state.draft,
                    onChange = onDraftChange,
                    onReadIn = onReadIn,
                    onRecord = onRecord,
                )
            }
            item { HorizontalDivider() }

            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bodyweight_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Newest first: what a lifter came here to read is what they weigh now, and the rest is
            // the trend behind it.
            items(state.history, key = BodyweightEntry::id) { entry ->
                WeighInRow(entry, state.displayUnit, onEdit)
            }
        }
    }

    state.editing?.let { editing ->
        EditWeighInDialog(
            editing = editing,
            onChange = onEditingDraftChange,
            onReadIn = onReadIn,
            onSave = onSaveEditing,
            onDelete = onDeleteEditing,
            onCancel = onCancelEditing,
        )
    }
}

/**
 * The field, the scale's unit, the day, and the button that records it.
 *
 * The day is on screen rather than assumed, because weighing yourself and remembering to type it in
 * are different mornings — and a weigh-in filed on the wrong day is a wrong answer to "what did I
 * weigh then", which is the only question this data is ever asked.
 */
@Composable
private fun WeighInControls(
    draft: BodyweightDraft,
    onChange: (BodyweightDraft) -> Unit,
    onReadIn: (WeightUnit) -> Unit,
    onRecord: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeighInField(draft, onChange, modifier = Modifier.weight(1f))
            Button(onClick = onRecord, enabled = draft.isRecordable) {
                Text(stringResource(R.string.bodyweight_record))
            }
        }
        WeightUnitChips(selected = draft.displayUnit, onReadIn = onReadIn)
        DayButton(draft.date) { onChange(draft.copy(date = it)) }
    }
}

/**
 * The number the scale showed.
 *
 * Labelled with the unit it is read in, because that is what decides what typing 180 means.
 */
@Composable
private fun WeighInField(
    draft: BodyweightDraft,
    onChange: (BodyweightDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = draft.weightText,
        onValueChange = { onChange(draft.copy(weightText = it)) },
        label = { Text(stringResource(draft.displayUnit.label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** The day being recorded, tappable to change. */
@Composable
private fun DayButton(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(onClick = { picking = true }) {
        Text(stringResource(R.string.bodyweight_on, date.toString()))
    }

    if (picking) {
        DayPickerDialog(
            date = date,
            onPick = {
                onPick(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun WeighInRow(
    entry: BodyweightEntry,
    displayUnit: WeightUnit,
    onEdit: (BodyweightEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.bodyweight_edit),
                onClick = { onEdit(entry) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.date.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            // Stored in kilograms and read in whatever the screen is set to, so a lifter who
            // changes scales sees one continuous history rather than a step where the unit moved.
            text = stringResource(
                R.string.bodyweight_summary,
                entry.weight.asTextIn(displayUnit),
                stringResource(displayUnit.label),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EditWeighInDialog(
    editing: EditingWeighIn,
    onChange: (BodyweightDraft) -> Unit,
    onReadIn: (WeightUnit) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.bodyweight_edit)) },
        text = {
            // The same two facts as recording one: what the scale said, and which morning it said
            // it.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WeighInField(editing.draft, onChange, modifier = Modifier.fillMaxWidth())
                WeightUnitChips(selected = editing.draft.displayUnit, onReadIn = onReadIn)
                DayButton(editing.draft.date) { onChange(editing.draft.copy(date = it)) }
                // Said rather than done: a day already weighed is not overwritten from here,
                // because the weight it holds is a reading the lifter never asked to lose.
                if (editing.dayAlreadyWeighed) {
                    Text(
                        text = stringResource(
                            R.string.bodyweight_day_taken,
                            editing.draft.date.toString(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = editing.draft.isRecordable) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.bodyweight_delete)) }
        },
    )
}
