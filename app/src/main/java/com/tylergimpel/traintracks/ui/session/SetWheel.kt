package com.tylergimpel.traintracks.ui.session

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.PerformedSet
import com.tylergimpel.traintracks.domain.Recommendation
import com.tylergimpel.traintracks.domain.SetDraft
import com.tylergimpel.traintracks.domain.SetType
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.WorkoutTip
import com.tylergimpel.traintracks.domain.asTimeText
import com.tylergimpel.traintracks.ui.label
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import kotlin.math.abs

/**
 * One rung of the wheel: a set that happened, or the one about to.
 *
 * The pending set is not a [PerformedSet] and never will be — it has not happened — so the wheel
 * carries the two shapes side by side rather than inventing a half-logged set to hold a place.
 */
sealed interface WheelSet {
    data class Logged(val index: Int, val set: PerformedSet) : WheelSet

    /** The set the controls are about to log, holding whatever the app is asking for. */
    data class Pending(val recommendation: Recommendation?) : WheelSet
}

/**
 * The logging screen's centrepiece: a vertical wheel of sets, read at arm's length
 * (`docs/design/logging-screen.md`, restyled in #142).
 *
 * Each rung reads top to bottom as what kind of set it is, the band, **one big number** and what
 * that set was last time. The focused rung is large and its neighbours tilt, shrink and fade. It
 * sits on the set about to be done, and logging moves it on, so the common path never scrolls.
 * A set already down is corrected by tapping it.
 *
 * The falloff is drawn rather than recomposed: each rung reads the scroll position inside its
 * [graphicsLayer] block, so scrolling never recomposes the list.
 */
@Composable
fun SetWheel(
    exercise: Exercise,
    sets: List<WheelSet>,
    lifterUnit: WeightUnit,
    draft: SetDraft,
    modifier: Modifier = Modifier,
    previous: List<PerformedSet> = emptyList(),
    onEdit: (WheelSet.Logged) -> Unit = {},
    onReadIn: (WeightUnit) -> Unit = {},
    /** Offered after the last set once the plan is met; null hides it. */
    onAddSet: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
) {
    val last = if (onAddSet != null) sets.size else sets.lastIndex
    LaunchedEffect(sets.size, onAddSet != null) {
        if (last >= 0) state.animateScrollToItem(last)
    }

    BoxWithConstraints(modifier.testTag(WHEEL)) {
        val pad = ((maxHeight - RUNG) / 2).coerceAtLeast(0.dp)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            // Half a wheel at each end, so the first and last rungs can both reach the middle.
            contentPadding = PaddingValues(vertical = pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            itemsIndexed(sets) { index, set ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RUNG)
                        .falloff(state, index)
                        .let { if (set is WheelSet.Logged) it.clickable { onEdit(set) } else it },
                ) {
                    val before = previous.getOrNull(index)?.leadingEffort?.summary(exercise.displayUnit, lifterUnit)
                    when (set) {
                        is WheelSet.Logged -> LoggedRung(set, exercise, lifterUnit, before)
                        is WheelSet.Pending -> PendingRung(set.recommendation, draft, before, onReadIn)
                    }
                }
            }
            if (onAddSet != null) {
                item {
                    Column(
                        Modifier.height(RUNG).falloff(state, sets.size),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(R.string.workout_add_set),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = LocalGymColors.current.dim,
                            modifier = Modifier
                                .border(1.dp, LocalGymColors.current.line, ControlShape)
                                .clickable(onClick = onAddSet)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.falloff(state: LazyListState, index: Int) = graphicsLayer {
    val offset = state.offsetFromCentre(index)
    val distance = abs(offset)
    val scale = (1f - distance * 0.6f).coerceAtLeast(FAR_SCALE)
    scaleX = scale
    scaleY = scale
    alpha = (1f - distance * 0.3f).coerceAtLeast(FAR_ALPHA)
    rotationX = (-offset * 24f).coerceIn(-50f, 50f)
}

/** The type of a set, small and above everything else on its rung. */
@Composable
private fun TypeLine(type: SetType?) {
    val text = when (type) {
        null, SetType.WORKING -> stringResource(R.string.set_type_working)
        else -> stringResource(type.label)
    }
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = LocalGymColors.current.dim)
}

@Composable
private fun WasLine(before: String?) {
    Text(
        before?.let { stringResource(R.string.workout_was, it) } ?: "",
        fontSize = 16.sp,
        color = LocalGymColors.current.dim,
    )
}

/** A set that happened, written as it was performed, with a tick. */
@Composable
private fun LoggedRung(rung: WheelSet.Logged, exercise: Exercise, lifterUnit: WeightUnit, before: String?) {
    val colors = LocalGymColors.current
    val displayUnit = rung.set.displayUnit ?: exercise.displayUnit
    TypeLine(rung.set.type)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val efforts = rung.set.efforts.map { it.summary(displayUnit, lifterUnit) }
        Text(
            efforts.joinToString("  ·  "),
            style = BIG,
            color = colors.dim,
            maxLines = 1,
        )
        Text(" ✓", style = BIG.copy(fontSize = 36.sp), color = colors.good)
    }
    rung.set.efforts.flatMap { it.drops }.forEach { DropRow(it, displayUnit) }
    WasLine(before)
}

/**
 * The set about to be logged: what the controls hold, so the big number is always what the next
 * tap on Log will record. Tapping the unit reads the exercise in the other one.
 */
@Composable
private fun PendingRung(recommendation: Recommendation?, draft: SetDraft, before: String?, onReadIn: (WeightUnit) -> Unit) {
    val colors = LocalGymColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(PENDING)) {
        TypeLine(recommendation?.setType ?: draft.type)
        val range = when (recommendation) {
            is Recommendation.Prescribing -> recommendation.repRange
            is Recommendation.CuttingVolume -> recommendation.repRange
            else -> null
        }
        range?.let { Text(stringResource(R.string.wheel_range, it.bottom, it.top), fontSize = 16.sp, color = colors.dim) }
        val type = draft.loggingType
        val unit = stringResource(draft.displayUnit.label)
        Row(verticalAlignment = Alignment.Bottom) {
            when {
                type.recordsWeight -> {
                    Text(draft.weightText.ifBlank { "–" }, style = BIG)
                    UnitLabel(unit) { onReadIn(draft.displayUnit.other) }
                    Text(" × ${draft.repsText.ifBlank { "–" }}", style = BIG)
                }
                type.recordsAddedWeight || type.recordsAssistance -> {
                    val sign = if (type.recordsAssistance) "−" else "+"
                    if (draft.addedWeightText.isNotBlank()) {
                        Text(sign + draft.addedWeightText, style = BIG)
                        UnitLabel(unit) { onReadIn(draft.displayUnit.other) }
                        Text(" × ${draft.repsText.ifBlank { "–" }}", style = BIG)
                    } else {
                        Text(stringResource(R.string.workout_reps, draft.repsText.ifBlank { "–" }), style = BIG)
                    }
                }
                type.recordsDistance -> Text(
                    "${draft.distanceText.ifBlank { "–" }} ${stringResource(draft.distanceUnit.label)} · ${draft.duration?.asTimeText() ?: "–"}",
                    style = BIG.copy(fontSize = 44.sp),
                )
                type.recordsDuration -> Text(draft.duration?.asTimeText() ?: "–", style = BIG)
                type.recordsReps -> Text(stringResource(R.string.workout_reps, draft.repsText.ifBlank { "–" }), style = BIG)
            }
        }
        WasLine(before)
    }
}

@Composable
private fun UnitLabel(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 26.sp,
        color = LocalGymColors.current.dim,
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.workout_switch_unit), onClick = onClick)
            .testTag(UNIT_TOGGLE)
            .then(rememberTipAnchor(WorkoutTip.SWITCH_UNIT))
            .padding(start = 2.dp, bottom = 8.dp),
    )
}

private val WeightUnit.other: WeightUnit
    get() = if (this == WeightUnit.KILOGRAMS) WeightUnit.POUNDS else WeightUnit.KILOGRAMS

/** How far item [index] is from the middle, in rungs: 0 in the centre. */
private fun LazyListState.offsetFromCentre(index: Int): Float {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 3f
    return (item.offset + item.size / 2f - centre) / item.size.coerceAtLeast(1)
}

/** One rung's height, and the big number on it. */
private val RUNG = 120.dp
private val BIG = androidx.compose.ui.text.TextStyle(
    fontFamily = com.tylergimpel.traintracks.ui.theme.BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = 60.sp,
    lineHeight = 62.sp,
)

/** What a rung away from the middle shrinks and fades to. */
private const val FAR_SCALE = 0.4f
private const val FAR_ALPHA = 0.4f

/** Handles for the tests, which care about what the screen does rather than how it looks. */
const val WHEEL = "set-wheel"
const val PENDING = "pending-set"
const val WEIGHT_FIELD = "weight-field"
const val LOG_SET = "log-set"
const val UNIT_TOGGLE = "unit-toggle"
