package com.tylergimpel.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.UppercaseText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * The Progress tab (#122, #123, #125, #148): the estimated max big, with how far it moved in three
 * months; the strength chart with the FitNotes seam marked; the best set at each rep count as
 * tiles; and working sets per session from where they can be trusted (ADR 0008).
 */
@Composable
internal fun ProgressTab(state: ExerciseHistoryUiState) {
    val exercise = state.exercise ?: return
    val colors = LocalGymColors.current
    val loadText = rememberLoadText(exercise)
    // The volume series starts where set types became known, which is where imported history ends.
    val seam = state.volume.points.firstOrNull()?.date?.takeIf { state.volume.startsAfterHistory }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val latest = state.strength.lastOrNull()
        if (latest == null) {
            Text(stringResource(R.string.history_none), color = colors.dim)
            return@Column
        }
        SectionLabel(stringResource(R.string.history_estimated_max))
        UppercaseText(loadText(latest.anchor), style = MaterialTheme.typography.displayMedium.copy(color = colors.text))
        val before = state.strength.lastOrNull { !it.date.isAfter(latest.date.minusMonths(3)) }
        if (before != null) {
            val change = latest.anchor.value - before.anchor.value
            val sign = if (change >= 0) "+" else "−"
            Text(
                stringResource(R.string.history_change, sign + Kilograms(kotlin.math.abs(change)).asTextIn(exercise.displayUnit) + " " + stringResource(exercise.displayUnit.label)),
                fontSize = 15.sp,
                color = colors.dim,
            )
        }
        TimeChart(
            series = listOf(
                ChartSeries(state.strength.map { ChartValue(it.date, it.best.value) }, setOf(Mark.POINTS), colors.dim),
                ChartSeries(state.strength.map { ChartValue(it.date, it.anchor.value) }, setOf(Mark.LINE), colors.accent),
            ),
            valueText = { loadText(Kilograms(it)) },
            description = stringResource(R.string.history_strength),
            seam = seam,
            seamLabel = stringResource(R.string.history_fitnotes),
        )
        if (state.records.isNotEmpty()) {
            SectionLabel(stringResource(R.string.history_records), Modifier.padding(top = 8.dp))
            state.records.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { record ->
                        AppCard(Modifier.weight(1f).heightIn(min = 72.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.history_record_reps, record.reps), fontSize = 13.sp, color = colors.dim)
                                Text(loadText(record.load), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.text)
                            }
                        }
                    }
                    repeat(3 - row.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
        SectionLabel(stringResource(R.string.history_working_sets), Modifier.padding(top = 8.dp))
        if (state.volume.points.isNotEmpty()) {
            TimeChart(
                series = listOf(ChartSeries(state.volume.points.map { ChartValue(it.date, it.sets.toDouble()) }, setOf(Mark.POINTS), colors.accent)),
                valueText = { it.roundToInt().toString() },
                description = stringResource(R.string.history_working_sets),
            )
        }
        if (state.volume.startsAfterHistory) {
            Text(
                seam?.let { stringResource(R.string.history_from, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                    ?: stringResource(R.string.history_from_now),
                fontSize = 15.sp,
                color = colors.dim,
            )
        }
    }
}

/**
 * How a signed load reads on this exercise: weight on a bar, help on an assisted machine, and
 * bodyweight with whatever was added to it. Resolved once, as a plain function, so the chart's
 * labels read exactly as the records and the headline do.
 */
@Composable
internal fun rememberLoadText(exercise: Exercise): (Kilograms) -> String {
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
