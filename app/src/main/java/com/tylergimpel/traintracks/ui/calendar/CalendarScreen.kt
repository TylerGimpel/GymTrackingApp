package com.tylergimpel.traintracks.ui.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.CalendarDay
import com.tylergimpel.traintracks.domain.DayTraining
import com.tylergimpel.traintracks.domain.MuscleLastTrained
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.asTextIn
import com.tylergimpel.traintracks.ui.MuscleGroup
import com.tylergimpel.traintracks.ui.label
import com.tylergimpel.traintracks.ui.icons.AppIcons
import com.tylergimpel.traintracks.ui.theme.AppTopBar
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.SectionLabel
import com.tylergimpel.traintracks.ui.theme.UppercaseText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarScreen(state, onBack, onOpenDay, viewModel::previousMonth, viewModel::nextMonth)
}

/**
 * The lifter's training, a month at a time (#118, #147).
 *
 * **What was done, never what was not** (ADR 0008). A lifting day is filled with bands of muscle
 * colour as tall as each group's share of its sets, and named by its routine day or its top
 * groups; a cardio-only day is marked as one; a weigh-in shows its weight. Rest, skipped and future
 * days are plain. Swipe sideways for another month; under the grid, how long since each group
 * was trained, which is also the key to the colours.
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onBack: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    val colors = LocalGymColors.current
    // The phone's own locale, read so that changing it redraws: which day a week starts on and
    // what the month is called are both the lifter's.
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.calendar_title)) }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(AppIcons.Back, stringResource(R.string.calendar_previous), state.canGoBack, onPreviousMonth)
                UppercaseText(
                    state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    style = MaterialTheme.typography.headlineSmall.copy(color = colors.text, textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(AppIcons.Chevron, stringResource(R.string.calendar_next), state.canGoForward, onNextMonth)
            }

            val firstDay = WeekFields.of(locale).firstDayOfWeek
            Row(Modifier.fillMaxWidth()) {
                repeat(DAYS_IN_A_WEEK) { day ->
                    Text(
                        firstDay.plus(day.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // The month follows the finger and turns past a quarter of the width. Where there is
            // nowhere to go it resists and springs back.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width = with(LocalDensity.current) { maxWidth.toPx() }
                Column(
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(offset.value.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val stuck = (offset.value + delta > 0 && !state.canGoBack) ||
                                    (offset.value + delta < 0 && !state.canGoForward)
                                scope.launch { offset.snapTo(offset.value + if (stuck) delta / 4 else delta) }
                            },
                            onDragStopped = {
                                val turn = abs(offset.value) > width / 4
                                when {
                                    turn && offset.value > 0 && state.canGoBack -> onPreviousMonth()
                                    turn && offset.value < 0 && state.canGoForward -> onNextMonth()
                                }
                                if (turn) offset.snapTo(0f) else offset.animateTo(0f)
                            },
                        )
                        .testTag(CALENDAR_GRID),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val leading = (state.month.atDay(1).dayOfWeek.value - firstDay.value + DAYS_IN_A_WEEK) % DAYS_IN_A_WEEK
                    val cells = List(leading) { null } + (1..state.month.lengthOfMonth()).map(state.month::atDay)
                    val today = LocalDate.now()
                    cells.chunked(DAYS_IN_A_WEEK).forEach { week ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { date ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (date != null) {
                                        DayCell(date, state.days[date], date == today, date.isAfter(today), state.lifterUnit, onOpenDay)
                                    }
                                }
                            }
                            repeat(DAYS_IN_A_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            LastTrained(state.lastTrained)
        }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(onClick = onClick, enabled = enabled, shape = ControlShape, color = colors.bg, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = if (enabled) colors.text else colors.line)
        }
    }
}

/**
 * One day: its date, and for a lifting day the bands of muscle colour and its name; for a
 * cardio-only day a green label; for a weigh-in the weight along the bottom.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    today: Boolean,
    future: Boolean,
    unit: WeightUnit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val colors = LocalGymColors.current
    val training = day?.training
    val bands = day?.let(::bandsOf).orEmpty()
    val topNames = bands.take(2).map { stringResource(it.first.label) }
    val name = day?.name ?: topNames.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    val described = listOfNotNull(
        when (training) {
            DayTraining.LIFTING -> name ?: stringResource(R.string.calendar_lifting)
            DayTraining.CARDIO_ONLY -> stringResource(R.string.calendar_cardio)
            null -> null
        },
        day?.weight?.let { stringResource(R.string.calendar_weighed, it.asTextIn(unit), stringResource(unit.label)) },
    ).joinToString(", ")
    Box(
        Modifier
            .fillMaxSize()
            .clip(ControlShape)
            .background(colors.surface)
            .border(if (today) 2.dp else 1.dp, if (today) colors.accent else colors.line, ControlShape)
            .alpha(if (future) 0.45f else 1f)
            .let { if (training != null) it.clickable { onOpenDay(date) } else it }
            .semantics { if (described.isNotEmpty()) contentDescription = "${date.dayOfMonth}: $described" },
    ) {
        if (training == DayTraining.LIFTING && bands.isNotEmpty()) {
            Column(Modifier.fillMaxSize()) {
                val total = bands.sumOf { it.second }
                bands.forEach { (group, sets) ->
                    Box(Modifier.fillMaxWidth().weight((sets / total).toFloat()).background(group.color.copy(alpha = 0.85f)))
                }
            }
        }
        if (training == DayTraining.CARDIO_ONLY) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(colors.good))
        }
        Column(Modifier.padding(4.dp)) {
            val onBand = training == DayTraining.LIFTING && bands.isNotEmpty()
            Text(
                date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                color = if (onBand) DARK_ON_BAND else if (today) colors.accentText else colors.text,
            )
            when (training) {
                DayTraining.LIFTING -> name?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.tylergimpel.traintracks.ui.theme.BarlowCondensed, fontWeight = FontWeight.Bold),
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        color = if (onBand) DARK_ON_BAND else colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DayTraining.CARDIO_ONLY -> Text(
                    stringResource(R.string.calendar_cardio).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.good,
                )
                null -> Unit
            }
        }
        day?.weight?.let { weight ->
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.surface.copy(alpha = 0.85f)).padding(horizontal = 3.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SCALE, null, tint = colors.dim, modifier = Modifier.size(10.dp))
                Text(weight.asTextIn(unit), fontSize = 11.sp, color = colors.text, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/**
 * The groups a day trained, the most first and at most four: a group needs three sets, counting
 * direct sets as one and indirect as a half, before it is a band (#137).
 */
internal fun bandsOf(day: CalendarDay): List<Pair<MuscleGroup, Double>> =
    day.muscleSets.entries
        .mapNotNull { (muscle, sets) -> MuscleGroup.of(muscle)?.let { it to sets } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
        .filterValues { it >= TAG_SETS }
        .entries.sortedByDescending { it.value }
        .take(MAX_BANDS)
        .map { it.key to it.value }

/**
 * Last trained: all ten groups in body order, two rows of five, each its colour, its name and how
 * long ago. Also the key to the bands. A record of what was done, never of what was missed.
 */
@Composable
private fun LastTrained(lastTrained: List<MuscleLastTrained>) {
    val colors = LocalGymColors.current
    val byGroup = lastTrained.mapNotNull { trained -> MuscleGroup.of(trained.muscle)?.let { it to trained.daysAgo } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.min() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.drawer_last_trained))
        MuscleGroup.entries.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { group ->
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(group.color, ControlShape))
                        Text(stringResource(group.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (val days = byGroup[group]) {
                                null -> "—"
                                0L -> stringResource(R.string.calendar_today)
                                else -> androidx.compose.ui.res.pluralStringResource(R.plurals.calendar_days_ago, days.toInt(), days.toInt())
                            },
                            fontSize = 12.sp,
                            color = colors.dim,
                        )
                    }
                }
            }
        }
    }
}

/** A small scale, for a weigh-in. Drawn on the icons' grid. */
private val SCALE = com.tylergimpel.traintracks.ui.icons.lineIcon(
    "scale",
    com.tylergimpel.traintracks.ui.icons.rect(3f, 4f, 18f, 16f, 3f),
    "M8 9a4 4 0 0 1 8 0z",
    "M12 9l1.5-2",
)

/** Text on a muscle band: every band colour is light, so it reads dark in either theme. */
private val DARK_ON_BAND = androidx.compose.ui.graphics.Color(0xFF1A1813)

private const val DAYS_IN_A_WEEK = 7
private const val TAG_SETS = 3.0
private const val MAX_BANDS = 4

/** A handle for the tests. */
const val CALENDAR_GRID = "calendar-grid"
