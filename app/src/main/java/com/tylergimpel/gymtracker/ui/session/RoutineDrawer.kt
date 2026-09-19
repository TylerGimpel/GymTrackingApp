package com.tylergimpel.gymtracker.ui.session

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
) {
    ModalDrawerSheet(modifier = Modifier.testTag(DRAWER)) {
        Text(
            text = progress?.routine?.name ?: stringResource(R.string.session_no_routine),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            progress?.routine?.rotation.orEmpty().forEach { day ->
                item(key = "day-${day.id}") { DayHeading(day, isNext = day.id == progress?.cursor?.id) }
                items(day.exercises, key = { "${day.id}-${it.id}" }) { exercise ->
                    NavigationDrawerItem(
                        label = { Text(exercise.name) },
                        // What has been done, beside what there is to do: the question a lifter
                        // opens this to answer is usually "have I done that one yet".
                        badge = performed[exercise.id]?.let { done ->
                            { Text(stringResource(R.string.drawer_sets_done, done)) }
                        },
                        selected = false,
                        onClick = { onJumpTo(exercise) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeading(day: RoutineDay, isNext: Boolean) {
    Text(
        text = if (isNext) stringResource(R.string.drawer_day_next, day.name) else day.name,
        style = MaterialTheme.typography.labelLarge,
        color = if (isNext) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A handle for the tests, which care that the drawer opens rather than how it looks. */
const val DRAWER = "routine-drawer"
