package com.tylergimpel.gymtracker.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

/** One value on a day. */
data class ChartValue(val date: LocalDate, val value: Double)

/** How a series is drawn: joined up, as dots, or both. */
enum class Mark { LINE, POINTS }

data class ChartSeries(val values: List<ChartValue>, val marks: Set<Mark>, val colour: Color)

/**
 * Values over time, drawn on a Canvas (#122, #125, #126): the app's one chart.
 *
 * **Every axis label is a value the chart reaches** — the lowest and highest values plotted, and
 * the first and last days — never a rounded tick the data does not get to, which would read as a
 * number the lifter once lifted. Colours come from the caller, out of the theme, so the chart reads
 * in a dim gym in dark mode as everything else does.
 *
 * Draws nothing for no values; a caller with nothing to plot says so in words instead. A single
 * value, or values all the same, sit in the middle rather than on an axis with no extent.
 */
@Composable
fun TimeChart(
    series: List<ChartSeries>,
    valueText: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val all = series.flatMap(ChartSeries::values)
    if (all.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axisColour = MaterialTheme.colorScheme.outlineVariant
    val low = all.minOf(ChartValue::value)
    val high = all.maxOf(ChartValue::value)
    val first = all.minOf(ChartValue::date)
    val last = all.maxOf(ChartValue::date)
    val days = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val highText = valueText(high)
    val lowText = valueText(low)
    val firstText = first.format(days)
    val lastText = last.format(days)

    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = description }) {
        val gap = LABEL_GAP.toPx()
        val highLabel = measurer.measure(highText, labelStyle)
        val lowLabel = measurer.measure(lowText, labelStyle)
        val firstLabel = measurer.measure(firstText, labelStyle)
        val lastLabel = measurer.measure(lastText, labelStyle)

        val left = max(highLabel.size.width, lowLabel.size.width) + gap
        val right = size.width - gap
        val top = highLabel.size.height / 2f
        val bottom = size.height - firstLabel.size.height - gap
        val span = (last.toEpochDay() - first.toEpochDay()).toFloat()

        fun x(date: LocalDate) =
            if (span == 0f) (left + right) / 2 else left + (right - left) * (date.toEpochDay() - first.toEpochDay()) / span

        fun y(value: Double) =
            if (high == low) (top + bottom) / 2 else bottom - (bottom - top) * ((value - low) / (high - low)).toFloat()

        drawLine(axisColour, Offset(left, top), Offset(left, bottom))
        drawLine(axisColour, Offset(left, bottom), Offset(right, bottom))

        drawText(highLabel, topLeft = Offset(left - gap - highLabel.size.width, y(high) - highLabel.size.height / 2))
        if (high != low) {
            drawText(lowLabel, topLeft = Offset(left - gap - lowLabel.size.width, y(low) - lowLabel.size.height / 2))
        }
        drawText(firstLabel, topLeft = Offset(if (span == 0f) x(first) - firstLabel.size.width / 2 else left, bottom + gap / 2))
        if (span != 0f) drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, bottom + gap / 2))

        series.forEach { one ->
            val placed = one.values.sortedBy(ChartValue::date).map { Offset(x(it.date), y(it.value)) }
            if (Mark.LINE in one.marks && placed.size > 1) {
                val path = Path().apply {
                    moveTo(placed.first().x, placed.first().y)
                    placed.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, one.colour, style = Stroke(width = LINE_WIDTH.toPx()))
            }
            if (Mark.POINTS in one.marks) placed.forEach { drawCircle(one.colour, radius = POINT_RADIUS.toPx(), center = it) }
        }
    }
}

private val CHART_HEIGHT = 180.dp
private val LABEL_GAP = 6.dp
private val LINE_WIDTH = 2.dp
private val POINT_RADIUS = 3.5.dp
