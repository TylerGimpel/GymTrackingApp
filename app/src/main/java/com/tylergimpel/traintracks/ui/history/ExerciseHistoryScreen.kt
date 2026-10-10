package com.tylergimpel.traintracks.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.ExerciseHistoryEntry
import com.tylergimpel.traintracks.domain.PerformedSet
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.ui.icons.EquipmentIcons
import com.tylergimpel.traintracks.ui.session.ExerciseDetailsFields
import com.tylergimpel.traintracks.ui.session.summary
import com.tylergimpel.traintracks.ui.settings.Pill
import com.tylergimpel.traintracks.ui.theme.AppCard
import com.tylergimpel.traintracks.ui.theme.AppTopBar
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.MainButton
import com.tylergimpel.traintracks.ui.theme.SectionLabel
import com.tylergimpel.traintracks.ui.theme.UppercaseText
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
    ExerciseHistoryScreen(
        state = state,
        onBack = onBack,
        onLoadMore = viewModel::loadMore,
        onDetailsChange = viewModel::changeDetails,
        onSaveDetails = viewModel::saveDetails,
    )
}

/** The three tabs of one exercise (#148). */
enum class ExerciseTab { PROGRESS, SETS, SETTINGS }

/**
 * One exercise (#121, #148): a header with its equipment, name and muscle, then Progress, Sets and
 * Settings. Sets reads every session newest first, a page at a time as the lifter scrolls back.
 */
@Composable
fun ExerciseHistoryScreen(
    state: ExerciseHistoryUiState,
    onBack: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onDetailsChange: (com.tylergimpel.traintracks.ui.session.ExerciseDetails) -> Unit = {},
    onSaveDetails: () -> Unit = {},
) {
    val colors = LocalGymColors.current
    var tab by rememberSaveable { mutableStateOf(ExerciseTab.PROGRESS) }
    val list = rememberLazyListState()
    val nearTheEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearTheEnd, state.entries.size, tab) {
        if (tab == ExerciseTab.SETS && nearTheEnd && !state.reachedEnd) onLoadMore()
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.history_title)) }) { insets ->
        val exercise = state.exercise
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (exercise != null) item { Header(exercise) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    ExerciseTab.entries.forEach { each ->
                        Box(Modifier.testTag("exercise-tab-${each.name.lowercase()}")) {
                            Pill(stringResource(each.label), selected = tab == each) { tab = each }
                        }
                    }
                }
            }
            when (tab) {
                ExerciseTab.PROGRESS -> item { ProgressTab(state) }
                ExerciseTab.SETS -> {
                    if (state.reachedEnd && state.entries.isEmpty()) {
                        item { Text(stringResource(R.string.history_none), color = colors.dim) }
                    }
                    val displayUnit = exercise?.displayUnit ?: state.lifterUnit
                    items(state.entries, key = ExerciseHistoryEntry::sessionExerciseId) { entry ->
                        SessionCard(entry, displayUnit, state.lifterUnit)
                    }
                }
                ExerciseTab.SETTINGS -> item {
                    state.details?.let { details ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExerciseDetailsFields(details, onDetailsChange)
                            MainButton(
                                stringResource(if (state.saved) R.string.history_saved else R.string.session_save),
                                onClick = onSaveDetails,
                                modifier = Modifier.fillMaxWidth().testTag(EXERCISE_SAVE),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ExerciseTab.label: Int
    get() = when (this) {
        ExerciseTab.PROGRESS -> R.string.history_tab_progress
        ExerciseTab.SETS -> R.string.history_tab_sets
        ExerciseTab.SETTINGS -> R.string.history_tab_settings
    }

@Composable
private fun Header(exercise: Exercise) {
    val colors = LocalGymColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).background(colors.raised, ControlShape), contentAlignment = Alignment.Center) {
            Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.accentText, modifier = Modifier.size(34.dp))
        }
        Column {
            UppercaseText(exercise.name, style = MaterialTheme.typography.headlineMedium.copy(color = colors.text))
            Text(
                listOfNotNull(exercise.primaryMuscle, stringResource(EquipmentIcons.pluralFor(exercise.equipment))).joinToString(" · "),
                fontSize = 15.sp,
                color = colors.dim,
            )
        }
    }
}

/**
 * One session of this exercise: the date, how many sets or where it came from, then the sets in
 * one line, warm-ups dimmed and the best set in the accent.
 */
@Composable
private fun SessionCard(entry: ExerciseHistoryEntry, displayUnit: WeightUnit, lifterUnit: WeightUnit) {
    val colors = LocalGymColors.current
    val best = entry.sets.filter { it.type.countsTowardVolume }.maxByOrNull { it.weightTimesReps() }
    val texts = entry.sets.map { set -> set to set.efforts.map { it.summary(set.displayUnit ?: displayUnit, lifterUnit) }.joinToString(" / ") }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                SectionLabel(
                    if (entry.imported) {
                        stringResource(R.string.history_fitnotes)
                    } else {
                        pluralStringResource(R.plurals.routine_sets, entry.sets.size, entry.sets.size)
                    },
                )
            }
            Text(
                buildAnnotatedString {
                    texts.forEachIndexed { index, (set, text) ->
                        if (index > 0) append("  ·  ")
                        withStyle(
                            SpanStyle(
                                color = when {
                                    set == best -> colors.accentText
                                    !set.type.countsTowardVolume -> colors.dim
                                    else -> colors.text
                                },
                                fontWeight = if (set == best) FontWeight.Bold else FontWeight.Normal,
                            ),
                        ) { append(text) }
                    }
                },
                fontSize = 16.sp,
            )
        }
    }
}

/** A rough size of a set, enough to pick the best one in a session to mark. */
private fun PerformedSet.weightTimesReps(): Double {
    val performance = leadingEffort.performance
    return when (performance) {
        is com.tylergimpel.traintracks.domain.Performance.WeightAndReps -> performance.weight.value * (1 + performance.reps / 30.0)
        else -> 0.0
    }
}

/** Invented: how close to the bottom of what is loaded the next page is asked for, in rows. */
private const val LOAD_AHEAD = 3

/** A handle for the tests. */
const val EXERCISE_SAVE = "exercise-save"

