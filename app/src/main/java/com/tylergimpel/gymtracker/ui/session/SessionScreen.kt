package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.domain.asWeightText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
        onReadIn = viewModel::readIn,
        onToggleDisplayUnitOverride = viewModel::toggleDisplayUnitOverride,
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
        onStartDay = viewModel::startCursorDay,
        onSkipDay = viewModel::startSkipping,
        onConfirmSkip = viewModel::skipDay,
        onCancelSkip = viewModel::cancelSkipping,
        onStartRefiling = viewModel::startRefiling,
        onRefileOn = viewModel::refileOn,
        onCancelRefiling = viewModel::cancelRefiling,
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
    onReadIn: (WeightUnit) -> Unit = {},
    onToggleDisplayUnitOverride: () -> Unit = {},
    onLogSet: () -> Unit = {},
    onEditEffort: (SessionExercise, PerformedSet, Effort) -> Unit = { _, _, _ -> },
    onEditingDraftChange: (SetDraft) -> Unit = {},
    onSaveEditing: () -> Unit = {},
    onDeleteEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
    onAddDrop: (WeightUnit, Effort) -> Unit = { _, _ -> },
    onDropDraftChange: (SetDraft) -> Unit = {},
    onSaveDrop: () -> Unit = {},
    onCancelDrop: () -> Unit = {},
    onSessionNoteChange: (String) -> Unit = {},
    onStartDay: () -> Unit = {},
    onSkipDay: () -> Unit = {},
    onConfirmSkip: () -> Unit = {},
    onCancelSkip: () -> Unit = {},
    onStartRefiling: (Session) -> Unit = {},
    onRefileOn: (LocalDate) -> Unit = {},
    onCancelRefiling: () -> Unit = {},
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
                            // Tappable, because a session logged the day after it happened is
                            // ordinary and the date is the only thing about it that is wrong.
                            Text(
                                text = it.date.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(
                                        // A label, because the date alone says nothing about being
                                        // tappable to anyone who cannot see where it sits.
                                        onClickLabel = stringResource(R.string.session_refile),
                                        onClick = { onStartRefiling(it) },
                                    )
                                    // Padding rather than a button: it keeps the top bar quiet
                                    // while giving the target something to be hit with.
                                    .padding(vertical = 4.dp),
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
            item {
                NextUp(
                    progress = state.routine,
                    canStart = state.session == null,
                    onStartDay = onStartDay,
                    onSkipDay = onSkipDay,
                )
            }

            state.selected?.let { selected ->
                item {
                    SetEntryControls(
                        exercise = selected,
                        draft = state.draft,
                        rightDraft = state.rightDraft,
                        perSide = state.perSide,
                        overridingDisplayUnit = state.overridingDisplayUnit,
                        onDraftChange = onDraftChange,
                        onRightDraftChange = onRightDraftChange,
                        onTogglePerSide = onTogglePerSide,
                        onReadIn = onReadIn,
                        onToggleDisplayUnitOverride = onToggleDisplayUnitOverride,
                        onLogSet = onLogSet,
                    )
                    HorizontalDivider()
                }
            }

            if (state.session == null) {
                item {
                    EmptySession(
                        hasSelection = state.selected != null,
                        lastFinished = state.lastFinished,
                        onStartRefiling = onStartRefiling,
                    )
                }
            }

            items(state.exercises, key = SessionExercise::id) { performed ->
                LoggedExercise(performed, onEditEffort, onAddDrop)
                HorizontalDivider()
            }

            if (state.unperformed.isNotEmpty()) {
                item {
                    Unperformed(state.unperformed)
                    HorizontalDivider()
                }
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

    state.refiling?.let { RefileSessionDialog(it.date, onRefileOn, onCancelRefiling) }

    state.skipping?.let { SkipDayDialog(it, onConfirmSkip, onCancelSkip) }
}

/**
 * What comes next in the routine, and the way past it.
 *
 * Derived from which days of the current cycle are still undone, never from the date — so this line
 * reads the same after a fortnight off as it did the evening before. The skip beside it is the
 * escape hatch a derived cursor requires, offered here because this is where a lifter reads which
 * day they are declining.
 */
@Composable
private fun NextUp(
    progress: RoutineProgress?,
    canStart: Boolean,
    onStartDay: () -> Unit,
    onSkipDay: () -> Unit,
) {
    val cursor = progress?.cursor
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
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

        Row {
            // Starting here is what takes the copy of the day's exercises. Logging a set without
            // it is still a session — just one that was not for anything in particular.
            if (cursor != null && canStart) {
                TextButton(onClick = onStartDay) {
                    Text(stringResource(R.string.session_start_day))
                }
            }
            // The way past a day nobody is going to train as written. Offered beside the day
            // itself, because that is the moment a lifter decides they are not doing it.
            if (cursor != null) {
                TextButton(onClick = onSkipDay) { Text(stringResource(R.string.session_skip_day)) }
            }
        }
    }
}

/**
 * Whether to deal with a day without training it.
 *
 * Asked rather than done, because nothing unskips a day: the record stands for that cycle, and the
 * point of recording skips at all is that repeated ones say something about the routine.
 */
@Composable
private fun SkipDayDialog(day: RoutineDay, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_skip_title, day.name)) },
        text = { Text(stringResource(R.string.session_skip_explain)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.session_skip_day)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Which day this session should be filed on.
 *
 * Material's picker speaks in UTC milliseconds, so both directions convert against UTC rather than
 * the device's zone: mixing the two files a late evening session on the following day, which is the
 * exact error the session's own midnight rule exists to avoid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefileSessionDialog(
    date: LocalDate,
    onRefileOn: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let {
                        onRefileOn(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = picker.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.session_save))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = picker)
    }
}

/**
 * What the screen says with nothing being logged — and the way back to what was just logged.
 *
 * [lastFinished] is offered for one reason: a wrong date is usually noticed after pressing Finish,
 * and without this the only way to correct one would be a history screen that does not exist yet.
 * One session, not a list. This is a way back to the last thing done, not a place to browse.
 */
@Composable
private fun EmptySession(
    hasSelection: Boolean,
    lastFinished: LoggedSession?,
    onStartRefiling: (Session) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (hasSelection) R.string.session_empty_selected else R.string.session_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastFinished?.let { logged ->
            Text(
                text = stringResource(R.string.session_last, logged.session.date.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.session_refile),
                        onClick = { onStartRefiling(logged.session) },
                    )
                    .padding(vertical = 4.dp),
            )
            // How much of what it was for got done — the question the plan exists to answer, and
            // the one a lifter asks the moment a session is over.
            if (!logged.plan.isEmpty) {
                val (done, planned) = logged.plan.progress(logged.exercises)
                Text(
                    text = stringResource(R.string.session_plan_progress, done, planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetEntryControls(
    exercise: Exercise,
    draft: SetDraft,
    rightDraft: SetDraft,
    perSide: Boolean,
    overridingDisplayUnit: Boolean,
    onDraftChange: (SetDraft) -> Unit,
    onRightDraftChange: (SetDraft) -> Unit,
    onTogglePerSide: () -> Unit,
    onReadIn: (WeightUnit) -> Unit,
    onToggleDisplayUnitOverride: () -> Unit,
    onLogSet: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.titleMedium)

        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_left),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetDraftFields(
            draft,
            onDraftChange,
            // Here a unit is a statement about the machine, so it settles the exercise — unless
            // the lifter says this one set is the exception.
            unitChoice = UnitChoice(
                onReadIn = onReadIn,
                overriding = overridingDisplayUnit,
                onToggleOverride = onToggleDisplayUnitOverride,
            ),
        )

        // A single entry by default; expanding is for tracking an imbalance, and the two sides carry
        // their own weights because that is the only reason to bother.
        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_right),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The unit belongs to the set rather than to one of its rows, so it is chosen once
            // above and this side follows it.
            SetDraftFields(
                rightDraft,
                onRightDraftChange,
                showType = false,
                unitChoice = null,
            )
        }

        // Offered only where it means something. An exercise trained one side at a time is the one
        // a lifter expands to track an imbalance; a barbell bench press has no sides to separate.
        if (exercise.unilateral) {
            FilterChip(
                selected = perSide,
                onClick = onTogglePerSide,
                label = { Text(stringResource(R.string.set_per_side)) },
            )
        }

        Button(
            onClick = onLogSet,
            enabled = draft.isLoggable && (!perSide || rightDraft.isLoggable),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.session_log_set))
        }
    }
}

/**
 * What choosing a unit beside a weight means.
 *
 * Correcting or continuing a set changes that set alone, which is [forThisSetAlone] and the
 * default. The entry controls pass their own, because a unit chosen while entering is a statement
 * about the machine and is remembered for the exercise.
 */
private data class UnitChoice(
    val onReadIn: (WeightUnit) -> Unit,
    /** Whether the entry controls are departing for one set; null where there is nothing to settle. */
    val overriding: Boolean? = null,
    val onToggleOverride: () -> Unit = {},
) {
    companion object {
        fun forThisSetAlone(draft: SetDraft, onChange: (SetDraft) -> Unit) =
            UnitChoice(onReadIn = { onChange(draft.readIn(it)) })
    }
}

/** The weight, reps, note, unit and type fields, shared by the entry controls and the dialogs. */
@Composable
private fun SetDraftFields(
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    showType: Boolean = true,
    showNote: Boolean = true,
    unitChoice: UnitChoice? = UnitChoice.forThisSetAlone(draft, onChange),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.weightText,
                onValueChange = { onChange(draft.copy(weightText = it)) },
                // Labelled with the unit the number is read in, because that is what decides what
                // typing 225 means.
                label = { Text(stringResource(draft.displayUnit.label)) },
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
        WeightControls(draft, onChange, unitChoice)
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
 * Stepping the weight, and saying what unit it is read in.
 *
 * The steps are the display unit's own — 2.5 kg or 5 lb — and are written on the buttons, because a
 * lifter who has just switched to pounds needs to see that the step came with it. Converting one
 * unit's step into the other would offer weights no barbell can be loaded to.
 *
 * A unit chip is remembered for the exercise (`docs/design/logging-screen.md`), which is right for
 * a machine that carries its label whoever walks up to it. The chip beside them is the exception
 * the spec also asks for: one set read off the odd machine, changing nothing else.
 */
@Composable
private fun WeightControls(
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    unitChoice: UnitChoice?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { onChange(draft.stepWeight(-1)) }) {
                Text(stringResource(R.string.set_weight_down, draft.displayUnit.step.asWeightText()))
            }
            OutlinedButton(onClick = { onChange(draft.stepWeight(1)) }) {
                Text(stringResource(R.string.set_weight_up, draft.displayUnit.step.asWeightText()))
            }
        }

        // A row of its own rather than trailing the steppers: "This set only" is the whole of the
        // override and is useless half off the edge of a phone.
        unitChoice?.let { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeightUnit.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == draft.displayUnit,
                        // A reading, not a value: the number in the field is rewritten and what it
                        // weighs is untouched, whichever of the two this settles.
                        onClick = { choice.onReadIn(candidate) },
                        label = { Text(stringResource(candidate.label)) },
                    )
                }
                choice.overriding?.let { overriding ->
                    FilterChip(
                        selected = overriding,
                        onClick = choice.onToggleOverride,
                        label = { Text(stringResource(R.string.set_unit_this_set_only)) },
                    )
                }
            }
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

/**
 * What this session was for and did not get to.
 *
 * The whole reason a plan is recorded: without this list an exercise nobody trained reads exactly
 * like one nobody prescribed, and a partial session cannot be told from a complete one.
 */
@Composable
private fun Unperformed(exercises: List<Exercise>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.session_unperformed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        exercises.forEach { exercise ->
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LoggedExercise(
    performed: SessionExercise,
    onEditEffort: (SessionExercise, PerformedSet, Effort) -> Unit,
    onAddDrop: (WeightUnit, Effort) -> Unit,
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
                // The exercise's unit, unless this set was read off some other machine.
                displayUnit = performed.displayUnitFor(set),
                onEditEffort = { effort -> onEditEffort(performed, set, effort) },
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
    displayUnit: WeightUnit,
    onEditEffort: (Effort) -> Unit,
    onAddDrop: (WeightUnit, Effort) -> Unit,
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
                    // A fixed width so the second side of a per-side set lines up under the first
                    // rather than sliding left into the space the number would have taken.
                    modifier = Modifier.width(20.dp),
                )
                effort.side?.let {
                    Text(
                        text = stringResource(it.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(effort.summary(displayUnit), style = MaterialTheme.typography.bodyLarge)
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

            // A drop is part of the set it continues, so it is read in the set's unit.
            effort.drops.forEach { drop -> DropRow(drop, displayUnit) }

            TextButton(onClick = { onAddDrop(displayUnit, effort) }) {
                Text(stringResource(R.string.set_add_drop))
            }
        }
    }
}

@Composable
private fun DropRow(drop: Drop, displayUnit: WeightUnit) {
    Text(
        text = stringResource(
            R.string.set_drop_summary,
            drop.orderIndex,
            drop.weight.asTextIn(displayUnit),
            stringResource(displayUnit.label),
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
        // Neither a type, a note nor a unit of its own: a drop is part of the set it continues, so
        // it is read in that set's unit and has nowhere to store the other two. Offering fields
        // that discard what is typed into them is worse than not offering them.
        text = {
            SetDraftFields(
                adding.draft,
                onChange,
                showType = false,
                showNote = false,
                unitChoice = null,
            )
        },
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
private fun Effort.summary(displayUnit: WeightUnit): String = stringResource(
    R.string.set_summary,
    weight.asTextIn(displayUnit),
    stringResource(displayUnit.label),
    reps,
)
