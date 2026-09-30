package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.tylergimpel.gymtracker.domain.asTextIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import androidx.compose.material3.Surface
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.Avoided
import com.tylergimpel.gymtracker.domain.PlateLine
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.reading
import com.tylergimpel.gymtracker.ui.DayPickerDialog
import com.tylergimpel.gymtracker.ui.WeightUnitChips
import com.tylergimpel.gymtracker.ui.label
import com.tylergimpel.gymtracker.ui.theme.TextButton
import java.time.Duration
import java.time.LocalDate

/** The unit chips on an exercise's own screen, apart from the ones beside a set. */
const val EXERCISE_UNIT_KILOGRAMS = "exercise-unit-kg"
const val EXERCISE_UNIT_POUNDS = "exercise-unit-lb"

/**
 * What the lifter calls this exercise, what they need to remember about it, and whether they want
 * it coached at all.
 *
 * All three live apart from the bank row, so an app update that corrects what the bank ships cannot
 * reach any of them (ADR 0009).
 */
@Composable
internal fun ExerciseDetailsDialog(
    details: ExerciseDetails,
    onChange: (ExerciseDetails) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.exercise_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = details.name,
                    onValueChange = { onChange(details.copy(name = it)) },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = details.note,
                    onValueChange = { onChange(details.copy(note = it)) },
                    label = { Text(stringResource(R.string.exercise_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // The lifter's word over the engine's: a rack that goes up in fours is a fact
                // about their gym, and blank hands it back to the plates or the logged stack.
                OutlinedTextField(
                    value = details.increment,
                    onValueChange = { onChange(details.copy(increment = it)) },
                    label = { Text(stringResource(R.string.exercise_increment)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The band this exercise is worked in, where the lifter wants a say in it.
                // Blank hands the question back to what they are training for, which is where it
                // normally lives: a goal seeds a band, and a goal edited later does not reach
                // back and take this one away.
                Text(
                    text = stringResource(R.string.exercise_rep_range),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = details.repRangeBottom,
                        onValueChange = { onChange(details.copy(repRangeBottom = it)) },
                        label = { Text(stringResource(R.string.exercise_rep_range_bottom)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = details.repRangeTop,
                        onValueChange = { onChange(details.copy(repRangeTop = it)) },
                        label = { Text(stringResource(R.string.exercise_rep_range_top)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // The settled unit for this exercise, and the same setting the logging screen's
                // chip changes. That chip is undecorated and taught once; this is where anybody who
                // missed the tip finds it, beside everything else about the exercise.
                Text(
                    text = stringResource(R.string.exercise_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeightUnitChips(
                    selected = details.displayUnit,
                    onReadIn = { onChange(details.copy(displayUnit = it)) },
                    chipTag = { unit ->
                        if (unit == WeightUnit.POUNDS) EXERCISE_UNIT_POUNDS else EXERCISE_UNIT_KILOGRAMS
                    },
                )
                ProgressionModelChooser(details.progressionModel) {
                    onChange(details.copy(progressionModel = it))
                }
                // Global, reversible, and a statement about what to suggest rather than about
                // what was trained: everything already logged against it stays put.
                FilterChip(
                    selected = details.disliked,
                    onClick = { onChange(details.copy(disliked = !details.disliked)) },
                    label = { Text(stringResource(R.string.exercise_disliked)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.session_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * How this exercise's recommendation advances, and the lifter's right to say "not this one".
 *
 * Offered here rather than buried, because the only value that means anything before the engine
 * exists is the one that turns it off — a lift somebody does not want coached is their business, and
 * the app has no way to work that out for itself.
 */
@Composable
internal fun ProgressionModelChooser(
    selected: ProgressionModel,
    onSelect: (ProgressionModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.exercise_progression_model),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressionModel.entries.forEach { model ->
                FilterChip(
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    label = { Text(stringResource(model.label)) },
                )
            }
        }
    }
}

/**
 * Which day this session should be filed on.
 *
 * The picker itself is shared, because reading a calendar day out of it correctly is one rule and
 * the session is not the only thing filed on a day — see [DayPickerDialog].
 */
@Composable
internal fun RefileSessionDialog(
    date: LocalDate,
    onRefileOn: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    DayPickerDialog(date = date, onPick = onRefileOn, onDismiss = onCancel)
}

/**
 * The engine admitting it has been wrong about a lift, and offering to find it again (#128).
 *
 * In line rather than a dialog: it is not urgent, and a lifter mid-set should not have to answer
 * anything to carry on. It says what the app will do and nothing about how often it was overruled.
 */
@Composable
internal fun ResetEstimateOffer(onReset: () -> Unit, onKeep: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reset_estimate_offer), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onKeep) { Text(stringResource(R.string.reset_estimate_keep)) }
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset_estimate_reset)) }
            }
        }
    }
}

/**
 * The question the app asks about an exercise a lifter keeps not doing.
 *
 * **Keeping it is as prominent as swapping or removing it**, and that is the whole design rather
 * than a courtesy: people skip squats because squats are hard, not because they dislike them, and
 * a prompt that made "remove" the easy answer would quietly strip the most valuable work out of a
 * programme. The app noticed; the lifter decides, and nothing happens until they do.
 */
@Composable
internal fun AvoidedPrompt(
    avoided: Avoided,
    onKeep: (Avoided) -> Unit,
    onSwap: (Avoided) -> Unit,
    onRemove: (Avoided) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onKeep(avoided) },
        title = { Text(avoided.exercise.name) },
        text = {
            Text(
                stringResource(
                    R.string.avoided_question,
                    avoided.unperformed,
                    avoided.asked,
                ),
            )
        },
        // Three answers, side by side and the same size as each other.
        confirmButton = {
            Row {
                TextButton(onClick = { onKeep(avoided) }) {
                    Text(stringResource(R.string.avoided_keep))
                }
                TextButton(onClick = { onSwap(avoided) }) {
                    Text(stringResource(R.string.avoided_swap))
                }
                TextButton(onClick = { onRemove(avoided) }) {
                    Text(stringResource(R.string.avoided_remove))
                }
            }
        },
    )
}

/**
 * How long to rest, and for how long that answer holds.
 *
 * Two confirmations rather than one, because "longer today" and "longer on this machine from now
 * on" are different things and the app cannot tell which one a lifter means. Neither is a setting
 * buried anywhere: both are one tap from the set being entered.
 */
@Composable
internal fun RestDialog(
    rest: Duration,
    onChange: (Duration) -> Unit,
    onThisSet: () -> Unit,
    onAlways: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_rest_title)) },
        text = {
            OutlinedTextField(
                value = rest.seconds.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { typed ->
                    onChange(Duration.ofSeconds(typed.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0))
                },
                label = { Text(stringResource(R.string.set_rest_taken)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onThisSet) { Text(stringResource(R.string.session_rest_this_set)) }
        },
        dismissButton = {
            TextButton(onClick = onAlways) { Text(stringResource(R.string.session_rest_always)) }
        },
    )
}

/** A silent failure said out loud, with the one tap that fixes it. */
@Composable
internal fun RestAlertWarning(
    @StringRes said: Int,
    @StringRes fix: Int,
    onFix: () -> Unit,
) {
    // One line and its fix, so a failure is said without pushing the wheel off the screen.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(said),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(fix), fontSize = 14.sp)
        }
    }
}

/**
 * How to load the bar, under the weight it makes.
 *
 * **Always visible and never interactive.** A lifter reads it and loads the bar; there is nothing
 * to tap, because it is not a choice — it is the rounding the engine already did, shown. The
 * numbers come from the same inventory the rounding read, so the line always adds up to the
 * weight above it.
 */
@Composable
internal fun PlateLineRow(line: PlateLine, unit: WeightUnit) {
    val label = stringResource(unit.label)
    Text(
        text = if (line.perSide.isEmpty()) {
            stringResource(R.string.wheel_bar_only, line.bar.asTextIn(unit), label)
        } else {
            stringResource(
                R.string.wheel_plate_line,
                line.bar.asTextIn(unit),
                label,
                line.perSide.joinToString { it.asTextIn(unit) },
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
    )
}

/**
 * Asks which routine day the finishing session realised, defaulting to nothing in particular.
 *
 * The cursor is offered first but any day can be chosen, because training out of order is ordinary
 * and the cursor holding its place afterwards is the whole point of deriving it.
 */
@Composable
internal fun WhichDayDialog(
    progress: RoutineProgress?,
    onFinishAs: (RoutineDay?) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_which_day)) },
        text = {
            Column {
                progress?.routine?.rotation.orEmpty().forEach { day ->
                    TextButton(onClick = { onFinishAs(day) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (progress?.cursor?.id == day.id) {
                                "${day.name}  ·  ${stringResource(R.string.routine_next_up)}"
                            } else {
                                day.name
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFinishAs(null) }) {
                Text(stringResource(R.string.session_no_day))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * One tip on the first session, covering the two things the screen leaves undecorated: the unit is
 * tapped to switch it, and the drawer opens with a sideways swipe. Neither depends on it: the unit
 * is also set from the exercise's settings, and the drawer opens from the strip too.
 */
@Composable
internal fun CoachMark(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(LocalGymColors.current.raised, MaterialTheme.shapes.small).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(stringResource(R.string.coach_unit), fontSize = 14.sp, lineHeight = 17.sp)
            Text(stringResource(R.string.coach_drawer), fontSize = 14.sp, lineHeight = 17.sp)
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.coach_dismiss), fontSize = 14.sp) }
    }
}
