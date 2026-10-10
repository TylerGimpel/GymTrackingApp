package com.tylergimpel.traintracks.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.SessionExercise
import com.tylergimpel.traintracks.domain.TipMoment
import com.tylergimpel.traintracks.domain.WorkoutTip
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import kotlin.math.roundToInt

/**
 * Where on screen each tip's thing is, measured as it is laid out (#156).
 *
 * The things are far apart — a stepper, a rung of the wheel, the strip — so each one says where it
 * is here rather than the bubble reaching into all three. A thing that leaves the screen takes its
 * place with it, and its tip waits until it is back.
 */
@Stable
class TipAnchors {
    internal val bounds = mutableStateMapOf<WorkoutTip, Rect>()
}

/** The workout's anchors, for anything inside it to mark itself with; none anywhere else. */
val LocalTipAnchors = staticCompositionLocalOf<TipAnchors?> { null }

/** A modifier marking what [tip] points at, wherever the workout's anchors are provided. */
@Composable
internal fun rememberTipAnchor(tip: WorkoutTip): Modifier {
    val anchors = LocalTipAnchors.current ?: return Modifier
    DisposableEffect(anchors, tip) { onDispose { anchors.bounds.remove(tip) } }
    return Modifier.onGloballyPositioned { anchors.bounds[tip] = it.boundsInRoot() }
}

/**
 * The tips' moment, read off the workout: what is open, what is down, and what shows a unit.
 *
 * Moving on is any logged exercise that is not the one in front of the lifter, however they got
 * there: logging the last set, the drawer, or Resume the next day.
 */
internal fun SessionUiState.tipMoment(): TipMoment {
    val type = draft.loggingType
    return TipMoment(
        exerciseOpen = selected != null,
        setLogged = exercises.any(SessionExercise::hasSets),
        unitShown = type.recordsWeight ||
            ((type.recordsAddedWeight || type.recordsAssistance) && draft.addedWeightText.isNotBlank()),
        movedOn = exercises.any { it.hasSets() && it.exercise.id != selected?.id },
    )
}

private fun SessionExercise.hasSets() = sets.isNotEmpty()

/**
 * One tip: a small yellow bubble with a point at its thing, and nothing else on screen changed.
 *
 * It covers only itself — the rest of the screen, Log above all, keeps taking taps — and tapping
 * it is one of the two ways it goes; doing what it says is the other. Above its thing or below it,
 * whichever leaves the thumb's controls alone.
 */
@Composable
internal fun TipBubble(tip: WorkoutTip, anchors: TipAnchors, onDismiss: () -> Unit) {
    val anchor = anchors.bounds[tip] ?: return
    val colors = LocalGymColors.current
    val above = tip == WorkoutTip.TAP_TO_TYPE
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() },
        content = {
            Text(
                text = stringResource(tip.text),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onAccent,
                modifier = Modifier
                    .testTag(TIP)
                    .background(colors.accent, ControlShape)
                    .clickable(onClickLabel = stringResource(R.string.tip_dismiss), onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Canvas(Modifier.size(POINT_WIDTH, POINT_HEIGHT)) {
                val path = Path().apply {
                    if (above) {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                    } else {
                        moveTo(0f, size.height)
                        lineTo(size.width, size.height)
                        lineTo(size.width / 2, 0f)
                    }
                    close()
                }
                drawPath(path, colors.accent)
            }
        },
    ) { measurables, constraints ->
        val gutter = GUTTER.roundToPx()
        val body = measurables[0].measure(
            Constraints(maxWidth = (constraints.maxWidth - 2 * gutter).coerceAtLeast(0)),
        )
        val point = measurables[1].measure(Constraints())
        val centre = anchor.center.x - origin.x
        val bodyX = (centre - body.width / 2f).roundToInt()
            .coerceIn(gutter, (constraints.maxWidth - gutter - body.width).coerceAtLeast(gutter))
        val pointX = (centre - point.width / 2f).roundToInt()
            .coerceIn(bodyX, (bodyX + body.width - point.width).coerceAtLeast(bodyX))
        val pointY = if (above) {
            (anchor.top - origin.y).roundToInt() - point.height
        } else {
            (anchor.bottom - origin.y).roundToInt()
        }
        val bodyY = if (above) pointY - body.height + 1 else pointY + point.height - 1
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.place(bodyX, bodyY)
            point.place(pointX, pointY)
        }
    }
}

private val WorkoutTip.text: Int
    get() = when (this) {
        WorkoutTip.TAP_TO_TYPE -> R.string.tip_tap_to_type
        WorkoutTip.SWITCH_UNIT -> R.string.tip_switch_unit
        WorkoutTip.SWIPE_FOR_WORKOUT -> R.string.tip_swipe_for_workout
    }

private val GUTTER = 16.dp
private val POINT_WIDTH = 16.dp
private val POINT_HEIGHT = 8.dp

/** Handle for the tests: whichever tip is showing. */
const val TIP = "workout-tip"
