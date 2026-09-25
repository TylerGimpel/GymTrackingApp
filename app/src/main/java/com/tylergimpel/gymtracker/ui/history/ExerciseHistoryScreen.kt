package com.tylergimpel.gymtracker.ui.history

import com.tylergimpel.gymtracker.domain.PerformedSet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.ExerciseHistoryEntry
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.ui.session.label
import com.tylergimpel.gymtracker.ui.session.summary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ExerciseHistoryScreen(
    exerciseId: String,
    onBack: () -> Unit,
    viewModel: ExerciseHistoryViewModel = viewModel(),
) {
    LaunchedEffect(exerciseId) { viewModel.open(exerciseId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseHistoryScreen(state = state, onBack = onBack, onLoadMore = viewModel::loadMore)
}

/**
 * Every set ever done on one exercise, newest first, back as far as the lifter scrolls (#121).
 *
 * **The raw numbers**, per the original request for graphs *and* numbers: grouped by session,
 * each set as it was logged, imported sessions marked as imported. Read a page at a time, so a
 * lift with years behind it opens as fast as one with a week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    state: ExerciseHistoryUiState,
    onBack: () -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val nearTheEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearTheEnd, state.entries.size) {
        if (nearTheEnd && !state.reachedEnd) onLoadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name.orEmpty()) },
                actions = { TextButton(onClick = onBack) { Text(stringResource(R.string.settings_done)) } },
            )
        },
    ) { insets ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            if (state.reachedEnd && state.entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            val displayUnit = state.exercise?.displayUnit ?: state.lifterUnit
            items(state.entries, key = ExerciseHistoryEntry::sessionExerciseId) { entry ->
                HistoryEntry(entry, displayUnit, state.lifterUnit)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryEntry(entry: ExerciseHistoryEntry, displayUnit: WeightUnit, lifterUnit: WeightUnit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val day = entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        Text(
            text = if (entry.imported) stringResource(R.string.history_imported, day) else day,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoggedSetLines(entry.sets, displayUnit, lifterUnit)
    }
}

/**
 * Sets as they were logged, one line each, numbered: the plain reading of a past set wherever
 * one is read back (#121, #118). Anything but a working set says what kind it was.
 */
@Composable
internal fun LoggedSetLines(sets: List<PerformedSet>, displayUnit: WeightUnit, lifterUnit: WeightUnit) {
    sets.forEachIndexed { index, set ->
        // Both sides of a per-side set on one line: it is one set, however many rows record it.
        val done = set.efforts.map { it.summary(displayUnit, lifterUnit) }.joinToString(" / ")
        val line = stringResource(R.string.history_set, index + 1, done)
        Text(
            text = if (set.type == SetType.WORKING) line else "$line · ${stringResource(set.type.label)}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** How close to the bottom of what is loaded the next page is asked for. */
private const val LOAD_AHEAD = 3
