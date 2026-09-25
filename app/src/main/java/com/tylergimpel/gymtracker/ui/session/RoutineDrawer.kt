package com.tylergimpel.gymtracker.ui.session

import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.domain.MuscleLastTrained
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.RoutineDay

/**
 * The whole routine, one gesture away and tappable to jump anywhere in it.
 *
 * Training out of order is ordinary — a squat rack is busy, a machine is taken — and the
 * **Cursor** already holds its place when it happens. What this adds is making the move easy
 * rather than a navigation exercise.
 *
 * **The whole routine, not only what is left.** A lifter looking in here is deciding what to do
 * next, and a list that hides what they have already done cannot answer "have I done the rows
 * yet". Sets logged against an exercise are shown beside it for the same reason.
 */
@Composable
fun RoutineDrawer(
    progress: RoutineProgress?,
    performed: Map<String, Int>,
    onJumpTo: (Exercise) -> Unit,
    lastTrained: List<MuscleLastTrained> = emptyList(),
    onOpenCalendar: () -> Unit = {},
) {
    ModalDrawerSheet(modifier = Modifier.testTag(DRAWER)) {
        Text(
            text = progress?.routine?.name ?: stringResource(R.string.session_no_routine),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        // Looking back, beside what is coming (#118): the top bar has no room left for it, and the
        // drawer is where a lifter already goes to see where they are.
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.calendar_open)) },
            selected = false,
            onClick = onOpenCalendar,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            progress?.routine?.rotation.orEmpty().forEach { day ->
                item(key = "day-${day.id}") {
                    DayHeading(day, isNext = day.id == progress?.cursor?.id)
                }
                items(day.exercises, key = { "${day.id}-${it.exercise.id}" }) { planned ->
                    val exercise = planned.exercise
                    NavigationDrawerItem(
                        label = { Text(exercise.name) },
                        // What has been done, beside what there is to do: the question a lifter
                        // opens this to answer is usually "have I done that one yet".
                        //
                        // Out of how many only where something asked for a number. `0 of 0` would
                        // be the drawer stating a plan nobody made, which is the same rule the
                        // session header keeps.
                        badge = badgeFor(performed[exercise.id] ?: 0, planned.sets),
                        selected = false,
                        onClick = { onJumpTo(exercise) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            // Where the next session is chosen, what was trained when (#127). Information in the
            // drawer's own quiet colours, never an alarm: how long ago is a fact, not overdue.
            if (lastTrained.isNotEmpty()) {
                item(key = "last-trained") {
                    Text(
                        text = stringResource(R.string.drawer_last_trained),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(lastTrained, key = { "muscle-${it.muscle}" }) { muscle ->
                    Text(
                        text = when (muscle.daysAgo) {
                            0L -> stringResource(R.string.drawer_trained_today, muscle.muscle)
                            1L -> stringResource(R.string.drawer_trained_yesterday, muscle.muscle)
                            else -> stringResource(R.string.drawer_trained_days_ago, muscle.muscle, muscle.daysAgo)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * How much of an exercise is done, out of how many where a number was asked for.
 *
 * Null rather than a badge reading `0 of 0`: an exercise nothing prescribed for has been trained
 * nought times out of no plan, and saying so in numbers is worse than saying nothing.
 */
@Composable
private fun badgeFor(done: Int, planned: Int?): (@Composable () -> Unit)? = when {
    planned != null -> {
        { Text(stringResource(R.string.drawer_sets_done, done, planned)) }
    }

    done > 0 -> {
        { Text(stringResource(R.string.drawer_sets_logged, done)) }
    }

    else -> null
}

@Composable
private fun DayHeading(day: RoutineDay, isNext: Boolean) {
    Text(
        text = if (isNext) stringResource(R.string.drawer_day_next, day.name) else day.name,
        style = MaterialTheme.typography.labelLarge,
        color = if (isNext) {
            LocalGymColors.current.accentText
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A handle for the tests, which care that the drawer opens rather than how it looks. */
const val DRAWER = "routine-drawer"
