package com.tylergimpel.gymtracker.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    ExerciseBankScreen(exercises, onSelect)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseBankScreen(exercises: List<Exercise>, onSelect: (Exercise) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.exercise_bank_title))
                        if (exercises.isNotEmpty()) {
                            Text(
                                text = "${exercises.size} exercises",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets,
        ) {
            items(exercises, key = Exercise::id) { exercise ->
                ExerciseRow(exercise, Modifier.clickable { onSelect(exercise) })
                HorizontalDivider()
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
            listOf(
                Exercise(
                    id = "Barbell_Bench_Press_-_Medium_Grip",
                    name = "Barbell Bench Press - Medium Grip",
                    primaryMuscles = listOf("chest"),
                    secondaryMuscles = listOf("shoulders", "triceps"),
                    equipment = "barbell",
                    mechanic = "compound",
                    force = "push",
                    category = "strength",
                    level = "beginner",
                    loggingType = LoggingType.WEIGHT_REPS,
                    unilateral = false,
                ),
                Exercise(
                    id = "Rowing_Stationary",
                    name = "Rowing, Stationary",
                    primaryMuscles = listOf("lats"),
                    secondaryMuscles = emptyList(),
                    equipment = "rower",
                    mechanic = "compound",
                    force = "pull",
                    category = "cardio",
                    level = "beginner",
                    loggingType = LoggingType.DISTANCE_TIME,
                    unilateral = false,
                ),
            ),
        )
    }
}
