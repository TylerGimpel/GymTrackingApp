package com.tylergimpel.gymtracker.ui.bank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.ui.icons.AppIcons
import com.tylergimpel.gymtracker.ui.icons.EquipmentIcons
import com.tylergimpel.gymtracker.ui.routine.Tile
import com.tylergimpel.gymtracker.ui.session.Sheet
import com.tylergimpel.gymtracker.ui.session.SheetTitle
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import com.tylergimpel.gymtracker.ui.theme.ControlShape
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.MainButton
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.UppercaseText

@Composable
fun ExerciseBankScreen(
    onSelect: (Exercise) -> Unit,
    viewModel: ExerciseBankViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseBankScreen(
        state = state,
        onSelect = onSelect,
        onSearch = viewModel::search,
        onFilterByEquipment = viewModel::filterByEquipment,
        onStartAdding = viewModel::startAdding,
        onAddingChange = viewModel::changeAdding,
        onSaveAdding = viewModel::saveAdding,
        onCancelAdding = viewModel::cancelAdding,
    )
}

/** The equipment the bank's one row of chips offers, in order; null is All. */
private val EQUIPMENT_CHIPS = listOf(null, "barbell", "dumbbell", "cable", "machine", "body only", "e-z curl bar", "kettlebells", "bands")

/**
 * The exercise bank (#148): search, one sideways row of equipment chips, then what the lifter has
 * done before — with what it was last done at — ahead of everything else.
 */
@Composable
fun ExerciseBankScreen(
    state: ExerciseBankUiState,
    onSelect: (Exercise) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFilterByEquipment: (String?) -> Unit = {},
    onStartAdding: (String) -> Unit = {},
    onAddingChange: (NewExerciseDraft) -> Unit = {},
    onSaveAdding: () -> Unit = {},
    onCancelAdding: () -> Unit = {},
) {
    val colors = LocalGymColors.current
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.more_exercises),
                actions = {
                    Surface(onClick = { onStartAdding("") }, shape = ControlShape, color = colors.bg, modifier = Modifier.size(48.dp).testTag(BANK_ADD)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Plus, stringResource(R.string.exercise_new), tint = colors.text)
                        }
                    }
                },
            )
        },
    ) { insets ->
        val done = state.exercises.filter { it.id in state.last }
        val others = state.exercises.filter { it.id !in state.last }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    placeholder = { Text(stringResource(R.string.exercise_search)) },
                    leadingIcon = { Icon(AppIcons.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(BANK_SEARCH),
                )
            }
            item { EquipmentChips(state.equipment, onFilterByEquipment) }
            if (done.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.bank_done_before), Modifier.padding(top = 8.dp)) }
                items(done, key = { "done-${it.id}" }) { exercise ->
                    BankRow(exercise, lastText(exercise, state.last[exercise.id])) { onSelect(exercise) }
                }
            }
            if (others.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.bank_others), Modifier.padding(top = 8.dp)) }
                items(others, key = Exercise::id) { exercise -> BankRow(exercise, null) { onSelect(exercise) } }
            }
            if (state.exercises.isEmpty() && state.total > 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.bank_nothing), color = colors.dim)
                        if (state.query.isNotBlank()) {
                            TextButton(onClick = { onStartAdding(state.query.trim()) }) {
                                UppercaseText(stringResource(R.string.bank_add_named, state.query.trim()), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                            }
                        }
                    }
                }
            }
        }
    }

    state.adding?.let { draft ->
        NewExerciseSheet(
            draft = draft,
            vocabulary = state.vocabulary,
            nameTaken = state.nameTaken,
            onChange = onAddingChange,
            onSave = onSaveAdding,
            onCancel = onCancelAdding,
        )
    }
}

/** One row of equipment chips that scrolls sideways and never wraps, keeping the chosen one in view. */
@Composable
private fun EquipmentChips(selected: String?, onSelect: (String?) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = EQUIPMENT_CHIPS.indexOf(selected)
        if (index >= 0) list.animateScrollToItem(index)
    }
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(EQUIPMENT_CHIPS) { _, equipment ->
            val chosen = equipment == selected
            val colors = LocalGymColors.current
            Surface(
                onClick = { onSelect(equipment) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = if (chosen) colors.accent else colors.raised,
                contentColor = if (chosen) colors.onAccent else colors.text,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (equipment == null) stringResource(R.string.bank_all) else stringResource(EquipmentIcons.pluralFor(equipment)),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** `last 80 × 8`, in the unit the exercise is read in. */
@Composable
private fun lastText(exercise: Exercise, last: Pair<Double?, Int?>?): String? {
    val (weight, reps) = last ?: return null
    return when {
        weight != null && reps != null -> stringResource(R.string.bank_last_weight, Kilograms(weight).asTextIn(exercise.displayUnit), reps)
        reps != null -> stringResource(R.string.bank_last_reps, reps)
        else -> null
    }
}

@Composable
private fun BankRow(exercise: Exercise, last: String?, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.heightIn(min = 68.dp).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(colors.raised, ControlShape), contentAlignment = Alignment.Center) {
                Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.dim, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.text)
                exercise.primaryMuscle?.let { Text(it, fontSize = 14.sp, color = colors.dim) }
            }
            last?.let { Text(it, fontSize = 14.sp, color = colors.dim) }
        }
    }
}

/**
 * Writing down the machine a gym has that the app has never heard of: a name, its equipment, the
 * muscles it works, what it records, and whether it is done one side at a time.
 *
 * Every vocabulary comes from the bank rather than a list in the app, so an exercise a lifter adds
 * can only take values the shipped rows already use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewExerciseSheet(
    draft: NewExerciseDraft,
    vocabulary: BankVocabulary,
    nameTaken: Boolean,
    onChange: (NewExerciseDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalGymColors.current
    Sheet(onDismiss = onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(stringResource(R.string.exercise_new))
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.exercise_name)) },
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text(stringResource(R.string.exercise_name_taken)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag(NEW_EXERCISE_NAME),
            )
            SectionLabel(stringResource(R.string.exercise_equipment))
            vocabulary.equipment.filter { it != "other" }.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Tile(
                            selected = item == draft.equipment,
                            onClick = { onChange(draft.copy(equipment = item.takeIf { it != draft.equipment })) },
                            modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                        ) {
                            Icon(EquipmentIcons.iconFor(item), null, modifier = Modifier.size(30.dp))
                            Text(stringResource(EquipmentIcons.pluralFor(item)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            SectionLabel(stringResource(R.string.exercise_primary_muscles))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vocabulary.muscles.forEach { muscle ->
                    com.tylergimpel.gymtracker.ui.settings.Pill(muscle, selected = muscle in draft.primaryMuscles) {
                        onChange(draft.togglePrimary(muscle))
                    }
                }
            }
            SectionLabel(stringResource(R.string.exercise_records))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LoggingType.WEIGHT_REPS to R.string.records_weight_reps,
                    LoggingType.BODYWEIGHT_REPS to R.string.records_reps,
                    LoggingType.TIME to R.string.records_time,
                ).forEach { (type, text) ->
                    com.tylergimpel.gymtracker.ui.settings.Pill(stringResource(text), selected = draft.loggingType == type) {
                        onChange(draft.copy(loggingType = type))
                    }
                }
            }
            com.tylergimpel.gymtracker.ui.settings.Pill(stringResource(R.string.exercise_unilateral), selected = draft.unilateral) {
                onChange(draft.copy(unilateral = !draft.unilateral))
            }
            MainButton(
                stringResource(R.string.exercise_add),
                onClick = onSave,
                enabled = draft.isComplete && !nameTaken,
                modifier = Modifier.fillMaxWidth().testTag(NEW_EXERCISE_ADD),
            )
            Text(stringResource(R.string.bank_needs_muscle), fontSize = 14.sp, color = colors.dim)
        }
    }
}

/** Handles for the tests. */
const val BANK_ADD = "bank-add"
const val BANK_SEARCH = "bank-search"
const val NEW_EXERCISE_NAME = "new-exercise-name"
const val NEW_EXERCISE_ADD = "new-exercise-add"
