package com.tylergimpel.traintracks.ui.bodyweight

import com.tylergimpel.traintracks.ui.session.SheetTitle
import com.tylergimpel.traintracks.ui.session.Sheet
import com.tylergimpel.traintracks.ui.theme.UppercaseText
import com.tylergimpel.traintracks.ui.theme.MainButton
import com.tylergimpel.traintracks.ui.theme.RowDivider
import com.tylergimpel.traintracks.ui.theme.AppCard
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.chart.ChartSeries
import com.tylergimpel.traintracks.ui.chart.ChartValue
import com.tylergimpel.traintracks.ui.chart.Mark
import com.tylergimpel.traintracks.ui.chart.TimeChart
import com.tylergimpel.traintracks.domain.asAmountText
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
import com.tylergimpel.traintracks.ui.theme.AppButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.tylergimpel.traintracks.ui.theme.AppOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.traintracks.ui.theme.TextButton
import com.tylergimpel.traintracks.ui.theme.AppTopBar
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
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.BodyweightDraft
import com.tylergimpel.traintracks.domain.BodyweightEntry
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.asTextIn
import com.tylergimpel.traintracks.ui.DayPickerDialog
import com.tylergimpel.traintracks.ui.WeightUnitChips
import com.tylergimpel.traintracks.ui.label
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

/**
 * Weigh-ins (#148): the latest weight big with its day, the trend, every reading (tap to correct
 * or delete), and Weigh in, which opens a stepper that moves a tenth at a time.
 */
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
    val colors = LocalGymColors.current
    var weighing by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.bodyweight_title)) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.history.firstOrNull()?.let { latest ->
                item {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            UppercaseText(latest.weight.asTextIn(state.displayUnit), style = MaterialTheme.typography.displayLarge.copy(color = colors.text))
                            Text(" " + stringResource(state.displayUnit.label), fontSize = 22.sp, color = colors.dim, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Text(latest.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), color = colors.dim)
                    }
                }
                item { WeighInChart(state) }
            }
            item {
                MainButton(stringResource(R.string.bodyweight_weigh_in), onClick = { weighing = true }, modifier = Modifier.fillMaxWidth().testTag(WEIGH_IN))
            }
            if (state.history.isEmpty()) {
                item { Text(stringResource(R.string.bodyweight_none), color = colors.dim) }
            } else {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        state.history.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            WeighInRow(
                                entry = entry,
                                displayUnit = state.displayUnit,
                                // Only where the day holds more than one reading.
                                showTime = entry.date in state.daysWeighedSeveralTimes,
                                onEdit = onEdit,
                            )
                        }
                    }
                }
            }
        }
    }

    if (weighing) {
        Sheet(onDismiss = { weighing = false }) {
            SheetTitle(stringResource(R.string.bodyweight_weigh_in))
            WeighInStepper(state.draft, onDraftChange)
            WeightUnitChips(selected = state.draft.displayUnit, onReadIn = onReadIn)
            DayButton(state.draft.date) { onDraftChange(state.draft.copy(date = it)) }
            MainButton(
                stringResource(R.string.bodyweight_record),
                onClick = {
                    onRecord()
                    weighing = false
                },
                enabled = state.draft.isRecordable,
                modifier = Modifier.fillMaxWidth(),
            )
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

/** The weight, typed or moved a tenth at a time either way. */
@Composable
private fun WeighInStepper(draft: BodyweightDraft, onChange: (BodyweightDraft) -> Unit) {
    fun step(by: Double) {
        val now = draft.weightText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        onChange(draft.copy(weightText = "%.1f".format(java.util.Locale.ROOT, (now + by).coerceAtLeast(0.0))))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AppOutlinedButton("−", onClick = { step(-0.1) }, modifier = Modifier.width(64.dp))
        WeighInField(draft, onChange, modifier = Modifier.weight(1f))
        AppOutlinedButton("+", onClick = { step(0.1) }, modifier = Modifier.width(64.dp))
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
    val day = entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
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
    )
}

/** A handle for the tests. */
const val WEIGH_IN = "weigh-in"
