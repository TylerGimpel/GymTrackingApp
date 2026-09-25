package com.tylergimpel.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.ui.chart.ChartSeries
import com.tylergimpel.gymtracker.ui.chart.ChartValue
import com.tylergimpel.gymtracker.ui.chart.Mark
import com.tylergimpel.gymtracker.ui.chart.TimeChart
import com.tylergimpel.gymtracker.ui.label
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * What an exercise's history adds up to, above the raw table (#122, #123, #125): a headline
 * estimate, strength as points and a line, the best set at each rep count, and volume from where
 * it can be trusted. Nothing here counts what was not done (ADR 0008).
 */
@Composable
internal fun ExerciseSummary(state: ExerciseHistoryUiState) {
    val exercise = state.exercise ?: return
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val loadText = rememberLoadText(exercise)
        state.strength.lastOrNull()?.let { latest ->
            Text(
                text = stringResource(R.string.history_headline, loadText(latest.anchor)),
                style = MaterialTheme.typography.titleLarge,
            )
            StrengthChart(state, loadText)
        }
        if (state.records.isNotEmpty()) Records(state, loadText)
        when {
            state.volume.points.isNotEmpty() -> VolumeChart(state)
            // All of it from a source that never said which sets were warm-ups: no chart to draw
            // yet, and the reason said rather than a section that silently is not there.
            state.volume.startsAfterHistory -> {
                Text(stringResource(R.string.history_volume), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.history_volume_not_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.history_every_set),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StrengthChart(state: ExerciseHistoryUiState, loadText: (Kilograms) -> String) {
    val colours = MaterialTheme.colorScheme
    Text(stringResource(R.string.history_strength), style = MaterialTheme.typography.titleSmall)
    TimeChart(
        series = listOf(
            ChartSeries(state.strength.map { ChartValue(it.date, it.best.value) }, setOf(Mark.POINTS), colours.tertiary),
            ChartSeries(state.strength.map { ChartValue(it.date, it.anchor.value) }, setOf(Mark.LINE), colours.primary),
        ),
        valueText = { loadText(Kilograms(it)) },
        description = stringResource(R.string.history_strength),
    )
    // Why a great day did not move the next prescription: the chart shows it, this says it.
    Text(
        text = stringResource(R.string.history_strength_why),
        style = MaterialTheme.typography.bodySmall,
        color = colours.onSurfaceVariant,
    )
}

@Composable
private fun Records(state: ExerciseHistoryUiState, loadText: (Kilograms) -> String) {
    Text(stringResource(R.string.history_records), style = MaterialTheme.typography.titleSmall)
    state.records.forEach { record ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.history_record_reps, record.reps), style = MaterialTheme.typography.bodyLarge)
            Text(loadText(record.load), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun VolumeChart(state: ExerciseHistoryUiState) {
    val colours = MaterialTheme.colorScheme
    Text(stringResource(R.string.history_volume), style = MaterialTheme.typography.titleSmall)
    TimeChart(
        series = listOf(
            ChartSeries(
                state.volume.points.map { ChartValue(it.date, it.sets.toDouble()) },
                setOf(Mark.LINE, Mark.POINTS),
                colours.primary,
            ),
        ),
        valueText = { it.roundToInt().toString() },
        description = stringResource(R.string.history_volume),
    )
    // Only where it begins after the history does, which is the only time it needs saying.
    if (state.volume.startsAfterHistory) {
        val from = state.volume.points.first().date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        Text(
            text = stringResource(R.string.history_volume_starts, from),
            style = MaterialTheme.typography.bodySmall,
            color = colours.onSurfaceVariant,
        )
    }
}

/**
 * How a signed load reads on this exercise: weight on a bar, help on an assisted machine, and
 * bodyweight with whatever was added to it. Resolved once, as a plain function, so the chart's
 * axis labels read exactly as the records and the headline do.
 */
@Composable
private fun rememberLoadText(exercise: Exercise): (Kilograms) -> String {
    val unit = exercise.displayUnit
    val unitLabel = stringResource(unit.label)
    val help = stringResource(R.string.history_load_help)
    val bodyweight = stringResource(R.string.history_load_bodyweight)
    val added = stringResource(R.string.history_load_added)
    val weight = stringResource(R.string.history_load)
    return { load ->
        when {
            load.value < 0 -> help.format(Kilograms(-load.value).asTextIn(unit), unitLabel)
            exercise.loggingType == LoggingType.BODYWEIGHT_REPS && load.value == 0.0 -> bodyweight
            exercise.loggingType == LoggingType.BODYWEIGHT_REPS -> added.format(load.asTextIn(unit), unitLabel)
            else -> weight.format(load.asTextIn(unit), unitLabel)
        }
    }
}
