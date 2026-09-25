package com.tylergimpel.gymtracker.ui.bodyweight

import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.chart.ChartSeries
import com.tylergimpel.gymtracker.ui.chart.ChartValue
import com.tylergimpel.gymtracker.ui.chart.Mark
import com.tylergimpel.gymtracker.ui.chart.TimeChart
import com.tylergimpel.gymtracker.domain.asAmountText
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
import com.tylergimpel.gymtracker.ui.theme.AppButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.tylergimpel.gymtracker.ui.theme.AppOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
            AppTopBar(
                title = stringResource(R.string.bodyweight_title),
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

            // Over time, above the list (#126): the trend behind what they weigh now. The same chart
            // as strength, in the unit this screen reads in; nothing drawn until there is a reading.
            if (state.history.isNotEmpty()) {
                item {
                    WeighInChart(state)
                    HorizontalDivider()
                }
            }

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
                WeighInRow(
                    entry = entry,
                    displayUnit = state.displayUnit,
                    // Only where the day holds more than one reading: otherwise the day is the
                    // whole of what a lifter came to read.
                    showTime = entry.date in state.daysWeighedSeveralTimes,
                    onEdit = onEdit,
                )
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
            AppButton(
                text = stringResource(R.string.bodyweight_record),
                onClick = onRecord,
                enabled = draft.isRecordable,
            )
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

    AppOutlinedButton(
        text = stringResource(R.string.bodyweight_on, date.toString()),
        onClick = { picking = true },
    )

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
    showTime: Boolean,
    onEdit: (BodyweightEntry) -> Unit,
) {
    val day = entry.date.toString()
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
            text = if (showTime) {
                stringResource(R.string.bodyweight_day_at, day, entry.recordedAt.asLocalTimeText())
            } else {
                day
            },
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

/**
 * A moment as the time of day it was, in the lifter's own zone and their own locale's shape.
 *
 * Shown only to tell two readings on one day apart, so the date is left to the row beside it and
 * the seconds to nobody: a scale is not read to the second.
 */
@Composable
private fun Instant.asLocalTimeText(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(atZone(ZoneId.systemDefault()).toLocalTime())

@Composable
private fun WeighInChart(state: BodyweightUiState) {
    val unit = state.displayUnit
    val unitLabel = stringResource(unit.label)
    TimeChart(
        series = listOf(
            ChartSeries(
                state.history.map { ChartValue(it.date, unit.fromKilograms(it.weight)) },
                setOf(Mark.LINE, Mark.POINTS),
                LocalGymColors.current.accentText,
            ),
        ),
        valueText = { "${it.asAmountText()} $unitLabel" },
        description = stringResource(R.string.bodyweight_chart),
        modifier = Modifier.padding(16.dp),
    )
}
