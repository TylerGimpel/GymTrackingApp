package com.tylergimpel.gymtracker.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.ui.theme.GymTrackerTheme

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
        onFilterBy = viewModel::filterBy,
        onStartAdding = viewModel::startAdding,
        onAddingChange = viewModel::changeAdding,
        onSaveAdding = viewModel::saveAdding,
        onCancelAdding = viewModel::cancelAdding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseBankScreen(
    state: ExerciseBankUiState,
    onSelect: (Exercise) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFilterBy: (String) -> Unit = {},
    onStartAdding: () -> Unit = {},
    onAddingChange: (NewExerciseDraft) -> Unit = {},
    onSaveAdding: () -> Unit = {},
    onCancelAdding: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.exercise_bank_title))
                        if (state.total > 0) {
                            Text(
                                text = stringResource(
                                    R.string.exercise_bank_count,
                                    state.exercises.size,
                                    state.total,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onStartAdding) {
                        Text(stringResource(R.string.exercise_new))
                    }
                },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            SearchControls(state, onSearch, onFilterBy)
            HorizontalDivider()

            if (state.exercises.isEmpty() && state.total > 0) {
                Text(
                    text = stringResource(R.string.exercise_bank_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.exercises, key = Exercise::id) { exercise ->
                    ExerciseRow(exercise, Modifier.clickable { onSelect(exercise) })
                    HorizontalDivider()
                }
            }
        }
    }

    state.adding?.let { draft ->
        NewExerciseDialog(
            draft = draft,
            vocabulary = state.vocabulary,
            nameTaken = state.nameTaken,
            onChange = onAddingChange,
            onSave = onSaveAdding,
            onCancel = onCancelAdding,
        )
    }
}

@Composable
private fun SearchControls(
    state: ExerciseBankUiState,
    onSearch: (String) -> Unit,
    onFilterBy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.exercise_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.muscles.forEach { muscle ->
                FilterChip(
                    selected = muscle == state.muscle,
                    onClick = { onFilterBy(muscle) },
                    label = { Text(muscle) },
                )
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
    ) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = exercise.subtitle(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The muscle it targets and the equipment it needs — the two things that decide whether an exercise
 * is worth reading further about. Equipment can be absent upstream, so the muscle stands alone.
 */
private fun Exercise.subtitle(): String =
    listOfNotNull(primaryMuscle, equipment).joinToString(" · ")

@Preview(showBackground = true)
@Composable
private fun ExerciseBankPreview() {
    GymTrackerTheme {
        ExerciseBankScreen(
            ExerciseBankUiState(
                exercises = listOf(
                    Exercise(
                        id = "Butterfly",
                        bankName = "Butterfly",
                        primaryMuscles = listOf("chest"),
                        secondaryMuscles = listOf("shoulders"),
                        equipment = "machine",
                        mechanic = "isolation",
                        force = "push",
                        category = "strength",
                        level = "beginner",
                        loggingType = LoggingType.WEIGHT_REPS,
                        unilateral = false,
                    ),
                ),
                query = "pec deck",
                muscles = listOf("chest", "lats", "quadriceps"),
                total = 896,
            ),
        )
    }
}

/**
 * Writing down the machine a gym has that the app has never heard of.
 *
 * Every vocabulary here comes from the bank rather than from a list in the app, so an exercise a
 * lifter adds can only take values the shipped rows already use — which is what "first-class" has
 * to mean if it is to be searched, filtered and counted alongside them.
 */
@Composable
private fun NewExerciseDialog(
    draft: NewExerciseDraft,
    vocabulary: BankVocabulary,
    nameTaken: Boolean,
    onChange: (NewExerciseDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.exercise_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                    modifier = Modifier.fillMaxWidth(),
                )

                ChipRow(
                    label = stringResource(R.string.exercise_primary_muscles),
                    options = vocabulary.muscles,
                    isSelected = { it in draft.primaryMuscles },
                    onClick = { onChange(draft.togglePrimary(it)) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_secondary_muscles),
                    options = vocabulary.muscles,
                    isSelected = { it in draft.secondaryMuscles },
                    onClick = { onChange(draft.toggleSecondary(it)) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_equipment),
                    options = vocabulary.equipment,
                    isSelected = { it == draft.equipment },
                    onClick = { onChange(draft.copy(equipment = it.takeIf { v -> v != draft.equipment })) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_mechanic),
                    options = vocabulary.mechanics,
                    isSelected = { it == draft.mechanic },
                    onClick = { onChange(draft.copy(mechanic = it.takeIf { v -> v != draft.mechanic })) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_force),
                    options = vocabulary.forces,
                    isSelected = { it == draft.force },
                    onClick = { onChange(draft.copy(force = it.takeIf { v -> v != draft.force })) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_category),
                    options = vocabulary.categories,
                    isSelected = { it == draft.category },
                    onClick = { onChange(draft.copy(category = it)) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_level),
                    options = vocabulary.levels,
                    isSelected = { it == draft.level },
                    onClick = { onChange(draft.copy(level = it)) },
                )
                ChipRow(
                    label = stringResource(R.string.exercise_logging_type),
                    options = LoggingType.entries.map(LoggingType::stored),
                    isSelected = { it == draft.loggingType.stored },
                    onClick = { onChange(draft.copy(loggingType = LoggingType.fromStored(it))) },
                )

                // Offered as a plain question because it changes how the exercise is logged rather
                // than what it is: a unilateral one expands to per-side on demand.
                FilterChip(
                    selected = draft.unilateral,
                    onClick = { onChange(draft.copy(unilateral = !draft.unilateral)) },
                    label = { Text(stringResource(R.string.exercise_unilateral)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.isComplete && !nameTaken) {
                Text(stringResource(R.string.exercise_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One closed vocabulary, as a scrolling row of chips. */
@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    isSelected: (String) -> Boolean,
    onClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
