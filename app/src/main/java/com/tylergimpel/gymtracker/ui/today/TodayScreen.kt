package com.tylergimpel.gymtracker.ui.today

import kotlinx.coroutines.delay
import java.time.Instant
import com.tylergimpel.gymtracker.domain.timeLeftIn
import com.tylergimpel.gymtracker.domain.asTimeText
import androidx.compose.runtime.produceState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.ui.MuscleChips
import com.tylergimpel.gymtracker.ui.theme.AppButton
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.AppOutlinedButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import com.tylergimpel.gymtracker.ui.theme.CardRow
import com.tylergimpel.gymtracker.ui.theme.ControlShape
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.MainButton
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.UppercaseText
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The tag on Start and Resume, whichever the hero card is showing. */
const val TODAY_START = "today-start"

/**
 * Today, the home: one hero card with the next day and a big Start, this week, and the last
 * workout (#133, #141).
 */
@Composable
fun TodayScreen(
    onStart: () -> Unit,
    onResume: () -> Unit,
    onJustLog: () -> Unit,
    onBuildRoutine: () -> Unit,
    onLetTheAppBuild: () -> Unit,
    onImportFitNotes: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: TodayViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_today)) }) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !state.loaded -> Unit
                state.running != null -> ResumeCard(state, onResume)
                state.last?.session?.date == state.today -> DoneCard(state.last!!, state.next, onStart)
                state.next != null -> NextCard(state.next!!, onStart, viewModel::startSkipping)
                state.progress != null -> EmptyRoutineCard(onJustLog)
                else -> WaysToStartCard(onLetTheAppBuild, onBuildRoutine, onImportFitNotes, onJustLog)
            }
            ThisWeek(state)
            // Unless the card above is already about it.
            state.last?.takeIf { it.session.date != state.today || state.running != null }
                ?.let { LastWorkout(it, onOpen = { onOpenDay(it.session.date) }) }
        }
    }

    state.skipping?.let { day ->
        AlertDialog(
            onDismissRequest = viewModel::cancelSkipping,
            title = { Text(stringResource(R.string.session_skip_title, day.name)) },
            text = { Text(stringResource(R.string.today_skip_explain)) },
            confirmButton = { TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.session_skip_day)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelSkipping) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NextCard(next: NextDay, onStart: () -> Unit, onSkip: () -> Unit) {
    val colors = LocalGymColors.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.today_next_up, next.position, next.of), Modifier.weight(1f))
                // The way past a day nobody is going to train as written, where they decide it.
                TextButton(onClick = onSkip) { Text(stringResource(R.string.session_skip_day), color = colors.dim) }
            }
            UppercaseText(next.day.name, style = MaterialTheme.typography.displayMedium)
            if (next.muscles.isNotEmpty()) MuscleChips(next.muscles)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Meta(
                    value = next.day.exercises.size.toString(),
                    label = pluralStringResource(R.plurals.today_exercises, next.day.exercises.size),
                )
                if (next.day.exercises.isNotEmpty()) {
                    Meta(value = stringResource(R.string.today_minutes, minutesOf(next.length)), label = stringResource(R.string.today_estimated))
                }
            }
            MainButton(
                text = stringResource(R.string.today_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().testTag(TODAY_START),
            )
        }
    }
}

@Composable
private fun ResumeCard(state: TodayUiState, onResume: () -> Unit) {
    val running = state.running ?: return
    val colors = LocalGymColors.current
    // A session started from a routine day carries that day's plan; its name is recorded at the end.
    val name = running.session.routineDayName
        ?: state.next?.day?.name?.takeIf { !running.plan.isEmpty }
        ?: stringResource(R.string.today_workout)
    val done = running.exercises.sumOf { it.sets.size }
    val planned = running.plan.exercises.sumOf { it.sets ?: 0 }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.today_in_progress, clock(running.session.startedAt)).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentText,
            )
            UppercaseText(name, style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Meta(value = if (planned > 0) "$done / $planned" else done.toString(), label = stringResource(R.string.workout_sets))
                if (running.unperformed.isNotEmpty()) {
                    Meta(value = stringResource(R.string.today_minutes, minutesOf(timeLeftIn(running.unperformed))), label = stringResource(R.string.today_left))
                }
            }
            MainButton(
                text = stringResource(R.string.today_resume),
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().testTag(TODAY_START),
            )
        }
    }
}

/** Trained today already: how it went, and the next day a tap away for anybody going again. */
@Composable
private fun DoneCard(last: LoggedSession, next: NextDay?, onStart: () -> Unit) {
    val session = last.session
    val minutes = session.finishedAt?.let { minutesOf(Duration.between(session.startedAt, it)) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel(stringResource(R.string.today_done))
            UppercaseText(session.routineDayName ?: stringResource(R.string.today_workout), style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                minutes?.let { Meta(value = stringResource(R.string.today_minutes, it).removePrefix("~"), label = stringResource(R.string.today_time)) }
                Meta(value = last.exercises.sumOf { it.sets.size }.toString(), label = stringResource(R.string.workout_sets))
                Meta(value = last.exercises.count { it.sets.isNotEmpty() }.toString(), label = stringResource(R.string.workout_exercises))
            }
            next?.let {
                AppOutlinedButton(
                    text = stringResource(R.string.today_start_next, it.day.name),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The session clock, `12:04`, ticking. */
@Composable
private fun clock(startedAt: Instant): String {
    val seconds by produceState(Duration.between(startedAt, Instant.now()).seconds, startedAt) {
        while (true) {
            delay(1_000)
            value = Duration.between(startedAt, Instant.now()).seconds
        }
    }
    return Duration.ofSeconds(seconds.coerceAtLeast(0)).asTimeText()
}

/** A routine with nothing in it yet: nothing to start, and logging still works. */
@Composable
private fun EmptyRoutineCard(onJustLog: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.today_routine_empty), color = LocalGymColors.current.dim)
            MainButton(
                text = stringResource(R.string.today_start),
                onClick = onJustLog,
                modifier = Modifier.fillMaxWidth().testTag(TODAY_START),
            )
        }
    }
}

/**
 * No routine yet: the ways to get one, and logging without one (#85). A set can be logged with
 * nothing set up, which is the first thing most people do with an app they have just installed.
 */
@Composable
private fun WaysToStartCard(
    onLetTheAppBuild: () -> Unit,
    onBuildRoutine: () -> Unit,
    onImportFitNotes: () -> Unit,
    onJustLog: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UppercaseText(stringResource(R.string.today_no_routine), style = MaterialTheme.typography.headlineMedium)
            AppButton(stringResource(R.string.start_generate), onLetTheAppBuild, Modifier.fillMaxWidth())
            AppOutlinedButton(stringResource(R.string.start_build), onBuildRoutine, Modifier.fillMaxWidth())
            AppOutlinedButton(stringResource(R.string.start_import), onImportFitNotes, Modifier.fillMaxWidth())
            TextButton(onClick = onJustLog, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.today_just_log))
            }
        }
    }
}

@Composable
private fun Meta(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = LocalGymColors.current.text)
        Text(label, fontSize = 16.sp, color = LocalGymColors.current.dim)
    }
}

/** Seven days, Monday first: trained days dotted, today outlined. */
@Composable
private fun ThisWeek(state: TodayUiState) {
    val colors = LocalGymColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.today_this_week, state.trained.size))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.week.forEach { day ->
                val done = day in state.trained
                val isToday = day == state.today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (done) colors.raised else colors.surface, ControlShape)
                        .border(
                            BorderStroke(if (isToday) 2.dp else 1.dp, if (isToday) colors.accent else colors.line),
                            ControlShape,
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        fontSize = 13.sp,
                        color = colors.dim,
                    )
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.text)
                    Box(Modifier.padding(top = 4.dp).size(8.dp).background(if (done) colors.accent else Color.Transparent, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun LastWorkout(last: LoggedSession, onOpen: () -> Unit) {
    val session = last.session
    val exercises = last.exercises.count { it.sets.isNotEmpty() }
    val parts = buildList {
        add(session.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + session.date.dayOfMonth)
        add(pluralStringResource(R.plurals.today_exercise_count, exercises, exercises))
        val length = session.finishedAt?.let { Duration.between(session.startedAt, it) }
        if (length != null && !session.isImported && length > Duration.ZERO) {
            add(stringResource(R.string.today_minutes, minutesOf(length)).removePrefix("~"))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.today_last))
        AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
            CardRow(
                title = session.routineDayName ?: stringResource(R.string.today_workout),
                subtitle = parts.joinToString(" · "),
                onClick = onOpen,
            )
        }
    }
}

private fun minutesOf(length: Duration): Long = ((length.seconds + 30) / 60).coerceAtLeast(1)
