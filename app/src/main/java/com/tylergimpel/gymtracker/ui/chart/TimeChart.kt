package com.tylergimpel.gymtracker.ui.chart

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
    /** Where imported history ends, drawn as a dashed line with [seamLabel] beside it (#148). */
    seam: LocalDate? = null,
    seamLabel: String? = null,
) {
    val all = series.flatMap(ChartSeries::values)
    if (all.isEmpty()) return
    // The value under a tap or a drag, said above the plot while the finger is there.
    var touched by remember(series) { mutableStateOf<ChartValue?>(null) }
    val touchedText = touched?.let { valueText(it.value) + " · " + it.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val seamColour = MaterialTheme.colorScheme.onSurfaceVariant
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(series) {
                fun nearest(x: Float): ChartValue? {
                    val firstDay = all.minOf(ChartValue::date).toEpochDay()
                    val span = (all.maxOf(ChartValue::date).toEpochDay() - firstDay).coerceAtLeast(1)
                    val day = firstDay + (x / size.width * span).toLong()
                    return all.minByOrNull { kotlin.math.abs(it.date.toEpochDay() - day) }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touched = nearest(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        touched = nearest(change.position.x)
                    }
                }
            },
    ) {
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

        if (seam != null && seam > first && seam < last) {
            val at = x(seam)
            drawLine(
                seamColour,
                Offset(at, top),
                Offset(at, bottom),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            seamLabel?.let { drawText(measurer.measure(it, labelStyle), topLeft = Offset(at + gap / 2, top)) }
        }

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

        touched?.let { value ->
            val at = Offset(x(value.date), y(value.value))
            drawCircle(seamColour, radius = POINT_RADIUS.toPx() * 2, center = at, style = Stroke(width = 2f))
            touchedText?.let {
                val label = measurer.measure(it, labelStyle)
                val leftEdge = (at.x - label.size.width / 2).coerceIn(left, right - label.size.width)
                drawText(label, topLeft = Offset(leftEdge, 0f))
            }
        }
    }
}

private val CHART_HEIGHT = 180.dp
private val LABEL_GAP = 6.dp
private val LINE_WIDTH = 2.dp
private val POINT_RADIUS = 3.5.dp
