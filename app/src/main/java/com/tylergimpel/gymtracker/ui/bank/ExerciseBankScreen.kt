package com.tylergimpel.gymtracker.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseBankScreen(
    state: ExerciseBankUiState,
    onSelect: (Exercise) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFilterBy: (String) -> Unit = {},
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
                        name = "Butterfly",
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
