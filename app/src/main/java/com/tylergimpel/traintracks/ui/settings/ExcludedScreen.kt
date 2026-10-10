package com.tylergimpel.traintracks.ui.settings

import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.RowDivider
import com.tylergimpel.traintracks.ui.theme.CardRow
import com.tylergimpel.traintracks.ui.theme.AppCard
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.PaddingValues
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
import com.tylergimpel.traintracks.ui.theme.TextButton
import com.tylergimpel.traintracks.ui.theme.AppTopBar
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
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.data.ExerciseRepository
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.profileRepository
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.excluded
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

    /** Excludes [exercise] again: the Undo after allowing it. */
    fun exclude(exercise: Exercise) {
        viewModelScope.launch { exercises.setDisliked(exercise.id, disliked = true) }
    }

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
    ExcludedScreen(
        excluded = excluded,
        onBack = onBack,
        onTakeBack = viewModel::takeBack,
        onUndo = viewModel::exclude,
        onOpenHistory = onOpenHistory,
    )
}

/**
 * Excluded exercises (#148): never suggested, each one a tap from being allowed again, with an
 * Undo for the tap. Its history is a tap on its name, because excluding it erased nothing (#121).
 */
@Composable
fun ExcludedScreen(
    excluded: List<Exercise>,
    onBack: () -> Unit = {},
    onTakeBack: (Exercise) -> Unit = {},
    onUndo: (Exercise) -> Unit = {},
    onOpenHistory: (Exercise) -> Unit = {},
) {
    val colors = LocalGymColors.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val allowed = stringResource(R.string.excluded_allowed)
    val undo = stringResource(R.string.routine_undo)
    Scaffold(topBar = { AppTopBar(stringResource(R.string.excluded_title)) }, snackbarHost = { SnackbarHost(snackbars) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(if (excluded.isEmpty()) R.string.excluded_none else R.string.excluded_never), color = colors.dim)
            }
            if (excluded.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        excluded.forEachIndexed { index, exercise ->
                            if (index > 0) RowDivider()
                            CardRow(
                                title = exercise.name,
                                subtitle = exercise.primaryMuscle,
                                onClick = { onOpenHistory(exercise) },
                                trailing = {
                                    Pill(stringResource(R.string.excluded_allow), selected = false) {
                                        onTakeBack(exercise)
                                        scope.launch {
                                            val result = snackbars.showSnackbar(allowed.format(exercise.name), actionLabel = undo, duration = SnackbarDuration.Short)
                                            if (result == SnackbarResult.ActionPerformed) onUndo(exercise)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
