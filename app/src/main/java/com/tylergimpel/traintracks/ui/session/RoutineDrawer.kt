package com.tylergimpel.traintracks.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.LineupItem
import com.tylergimpel.traintracks.domain.Session
import com.tylergimpel.traintracks.domain.SessionPosition
import com.tylergimpel.traintracks.ui.icons.AppIcons
import com.tylergimpel.traintracks.ui.theme.AppOutlinedButton
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.UppercaseText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Tonight's workout, one gesture away (#142): the day's name, how much is done, and a row per
 * exercise with its progress. Tapping a row jumps there; each row swaps; Add exercise is last.
 *
 * Training out of order is ordinary, and the **Cursor** holds its place when it happens.
 */
@Composable
fun WorkoutDrawer(
    state: SessionUiState,
    onJumpTo: (Exercise) -> Unit,
    onSwap: (Exercise) -> Unit,
    onAdd: () -> Unit,
    onRefile: (Session) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
) {
    val colors = LocalGymColors.current
    val done = state.exercises.sumOf { it.sets.size }
    val planned = state.lineup.sumOf { it.planned ?: 0 }
    val grouped = state.positions.filterIsInstance<SessionPosition.Grouped>()
    ModalDrawerSheet(
        modifier = Modifier.testTag(DRAWER).fillMaxHeight(),
        drawerContainerColor = colors.surface,
        drawerShape = RoundedCornerShape(0.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
            UppercaseText(
                state.session?.routineDayName ?: state.routine?.cursor?.takeIf { !state.lineup.isEmpty() }?.name
                    ?: stringResource(R.string.today_workout),
                style = MaterialTheme.typography.headlineMedium,
            )
            val tally = buildList {
                add(if (planned > 0) stringResource(R.string.drawer_sets_of, done, planned) else stringResource(R.string.drawer_sets, done))
                state.timeLeft?.let { add(stringResource(R.string.wheel_time_left, stringResource(R.string.workout_minutes_short, it.toMinutes().coerceAtLeast(1)))) }
            }
            Text(tally.joinToString(" · "), color = colors.dim, modifier = Modifier.padding(top = 6.dp))
            // A session filed on the wrong day is corrected here, where the session is.
            state.session?.let { session ->
                Text(
                    session.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    fontSize = 15.sp,
                    color = colors.dim,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.session_refile)) { onRefile(session) }
                        .padding(vertical = 4.dp),
                )
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.lineup, key = { it.exercise.id }) { item ->
                val group = grouped.firstOrNull { position -> position.exercises.any { it.exercise.id == item.exercise.id } }
                DrawerRow(
                    item = item,
                    current = item.exercise.id == state.selected?.id,
                    superset = group != null,
                    onJump = { onJumpTo(item.exercise) },
                    onSwap = { onSwap(item.exercise) },
                )
            }
            item {
                AppOutlinedButton(
                    stringResource(R.string.workout_add_exercise),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                )
            }
            state.session?.let { session ->
                item { DrawerNote(session.id, session.note, onNoteChange) }
            }
            // How long since each muscle was trained (#127): a fact, never an alarm.
            if (state.lastTrained.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.drawer_last_trained),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.lastTrained, key = { "muscle-${it.muscle}" }) { muscle ->
                    Text(
                        text = when (muscle.daysAgo) {
                            0L -> stringResource(R.string.drawer_trained_today, muscle.muscle)
                            1L -> stringResource(R.string.drawer_trained_yesterday, muscle.muscle)
                            else -> stringResource(R.string.drawer_trained_days_ago, muscle.muscle, muscle.daysAgo)
                        },
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(item: LineupItem, current: Boolean, superset: Boolean, onJump: () -> Unit, onSwap: () -> Unit) {
    val colors = LocalGymColors.current
    Column(Modifier.background(if (current) colors.raised else colors.surface)) {
        HorizontalDivider(color = colors.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .clickable(onClick = onJump)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.exercise.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (item.planned != null) "${item.done}/${item.planned}" else item.done.toString(),
                        fontSize = 15.sp,
                        color = colors.dim,
                    )
                }
                when {
                    item.skipped -> Text(stringResource(R.string.drawer_skipped), fontSize = 14.sp, color = colors.dim)
                    else -> ProgressBar(
                        fraction = item.planned?.let { (item.done.toFloat() / it).coerceIn(0f, 1f) } ?: if (item.done > 0) 1f else 0f,
                        full = item.planned != null && item.done >= item.planned,
                    )
                }
                if (superset) {
                    Text(stringResource(R.string.drawer_superset), fontSize = 13.sp, color = colors.accentText)
                }
            }
            Box(
                modifier = Modifier.width(56.dp).height(68.dp).clickable(onClickLabel = stringResource(R.string.workout_swap_named, item.exercise.name), onClick = onSwap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Swap, contentDescription = stringResource(R.string.workout_swap_named, item.exercise.name), tint = colors.dim)
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, full: Boolean) {
    val colors = LocalGymColors.current
    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.bg, RoundedCornerShape(3.dp))) {
        Box(
            Modifier.fillMaxWidth(fraction).height(6.dp)
                .background(if (full) colors.good else colors.accent, RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * The session note. The field owns its own text and writes through, rather than rendering what
 * comes back from the database, which drops characters typed faster than the write lands.
 */
@Composable
private fun DrawerNote(sessionId: Long, stored: String?, onChange: (String) -> Unit) {
    var text by rememberSaveable(sessionId) { mutableStateOf(stored.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(stringResource(R.string.session_note)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

/** A handle for the tests, which care that the drawer opens rather than how it looks. */
const val DRAWER = "routine-drawer"

