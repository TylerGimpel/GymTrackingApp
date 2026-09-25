package com.tylergimpel.gymtracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.CalendarDay
import com.tylergimpel.gymtracker.domain.DayTraining
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

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
 * The lifter's training, a month at a time (#118).
 *
 * **What was done, never what was not** (ADR 0008): a lifting day, a cardio-only day marked
 * differently, and a quiet dot for a weigh-in. A skipped day looks exactly like a rest day. Marks
 * take their colours from the theme, so they read in a dim gym as the rest of the app does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onBack: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    // The phone's own locale, read so that changing it redraws: which day a week starts on and
    // what the month is called are both the lifter's, not the app's.
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.calendar_title),
                actions = { TextButton(onClick = onBack) { Text(stringResource(R.string.settings_done)) } },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousMonth, enabled = state.canGoBack) {
                    Text(stringResource(R.string.calendar_previous))
                }
                Text(
                    text = state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onNextMonth, enabled = state.canGoForward) {
                    Text(stringResource(R.string.calendar_next))
                }
            }

            val firstDay = WeekFields.of(locale).firstDayOfWeek
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(DAYS_IN_A_WEEK) { offset ->
                    Text(
                        text = firstDay.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val leading = (state.month.atDay(1).dayOfWeek.value - firstDay.value + DAYS_IN_A_WEEK) % DAYS_IN_A_WEEK
            val cells = List(leading) { null } + (1..state.month.lengthOfMonth()).map(state.month::atDay)
            cells.chunked(DAYS_IN_A_WEEK).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(date, date?.let(state.days::get), onOpenDay, Modifier.weight(1f))
                    }
                    repeat(DAYS_IN_A_WEEK - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }

            Legend()
        }
    }
}

@Composable
private fun DayCell(date: LocalDate?, day: CalendarDay?, onOpenDay: (LocalDate) -> Unit, modifier: Modifier) {
    Box(modifier = modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box
        val colours = MaterialTheme.colorScheme
        val training = day?.training
        val described = listOfNotNull(
            when (training) {
                DayTraining.LIFTING -> stringResource(R.string.calendar_lifting)
                DayTraining.CARDIO_ONLY -> stringResource(R.string.calendar_cardio)
                null -> null
            },
            if (day?.weighedIn == true) stringResource(R.string.calendar_weigh_in) else null,
        ).joinToString(", ")
        Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .then(
                    when (training) {
                        DayTraining.LIFTING -> Modifier.background(colours.primary, CircleShape)
                        DayTraining.CARDIO_ONLY -> Modifier.border(2.dp, colours.tertiary, CircleShape)
                        null -> Modifier
                    },
                )
                // Only a day something was trained on opens: there is nothing to read on the others.
                .then(if (training != null) Modifier.clickable { onOpenDay(date) } else Modifier)
                .semantics { if (described.isNotEmpty()) stateDescription = described },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (training == DayTraining.LIFTING) colours.onPrimary else colours.onSurface,
            )
        }
        if (day?.weighedIn == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(WEIGH_IN_DOT)
                    .background(colours.onSurfaceVariant, CircleShape),
            )
        }
    }
}

/** What each mark means, once, under the month. */
@Composable
private fun Legend() {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(Modifier.background(colours.primary, CircleShape), stringResource(R.string.calendar_lifting))
        LegendItem(Modifier.border(2.dp, colours.tertiary, CircleShape), stringResource(R.string.calendar_cardio))
        LegendItem(Modifier.padding(4.dp).background(colours.onSurfaceVariant, CircleShape), stringResource(R.string.calendar_weigh_in))
    }
}

@Composable
private fun LegendItem(mark: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).then(mark))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val DAYS_IN_A_WEEK = 7
private val MARK_SIZE = 36.dp
private val WEIGH_IN_DOT = 5.dp
