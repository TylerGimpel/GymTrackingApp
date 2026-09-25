package com.tylergimpel.gymtracker.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.excluded
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The exercises the lifter has excluded, each of which can be taken back.
 *
 * Taking one back only lets the app suggest it again from now on. No routine changes, because a
 * routine changes only when the lifter changes it, and no history changes, because excluding an
 * exercise never touched what was logged against it.
 */
class ExcludedViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val exercises = ExerciseRepository(
        database,
        database.exerciseDao(),
        database.sessionDao(),
        database.profileRepository(),
    )

    val excluded: StateFlow<List<Exercise>> = exercises.observeBank()
        .map { it.excluded() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun takeBack(exercise: Exercise) {
        viewModelScope.launch { exercises.setDisliked(exercise.id, disliked = false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun ExcludedScreen(
    onBack: () -> Unit,
    onOpenHistory: (Exercise) -> Unit = {},
    viewModel: ExcludedViewModel = viewModel(),
) {
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    ExcludedScreen(excluded = excluded, onBack = onBack, onTakeBack = viewModel::takeBack, onOpenHistory = onOpenHistory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludedScreen(
    excluded: List<Exercise>,
    onBack: () -> Unit = {},
    onTakeBack: (Exercise) -> Unit = {},
    onOpenHistory: (Exercise) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.excluded_title)) },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.settings_done)) }
                },
            )
        },
    ) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            item {
                Text(
                    text = stringResource(
                        if (excluded.isEmpty()) R.string.excluded_none else R.string.excluded_why,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(excluded, key = Exercise::id) { exercise ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Excluding it erased nothing: what was done on it is a tap away (#121).
                    TextButton(onClick = { onOpenHistory(exercise) }, modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(onClick = { onTakeBack(exercise) }) {
                        Text(stringResource(R.string.excluded_take_back))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
