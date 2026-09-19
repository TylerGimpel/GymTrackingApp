package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * The logging screen's centrepiece: a vertical wheel of sets, read at arm's length.
 *
 * Settled by prototype against three rejected layouts — a single big card with no context, a dense
 * list with no focus, and horizontal value pickers. The wheel is the only one that gives both the
 * zoomed prescription and the sets around it at once (`docs/design/logging-screen.md`).
 *
 * **The focused set is large and its neighbours recede**, so what is coming is visible without
 * navigating anywhere. Scrolling changes which set is focused; the controls below never move, and
 * act on whatever is focused — a lifter should not have to look at their thumb.
 *
 * The falloff is drawn rather than recomposed. Each item reads the scroll position inside its
 * [graphicsLayer] block, which defers the read to the draw phase: recomposing the list every
 * scroll frame fights the scroll position and feels broken, which is the one implementation note
 * the prototype left behind.
 */
@Composable
fun SetWheel(
    exercise: Exercise,
    sets: List<WheelSet>,
    lifterUnit: WeightUnit,
    onFocus: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(state, sets.size) {
        snapshotFlow { state.centredItem() }
            .distinctUntilChanged()
            .collect { centred -> centred?.let(onFocus) }
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxWidth().testTag(WHEEL),
        // Half the wheel's height of padding at each end, so the first and last sets can both
        // reach the middle: a wheel whose last rung cannot be focused is a wheel with a set on it
        // nobody can log.
        contentPadding = PaddingValues(vertical = WHEEL_HEIGHT / 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        itemsIndexed(sets) { index, set ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val fall = state.falloffAt(index)
                        scaleX = FAR_SCALE + (1f - FAR_SCALE) * fall
                        scaleY = scaleX
                        alpha = FAR_ALPHA + (1f - FAR_ALPHA) * fall
                    }
                    .padding(horizontal = 16.dp),
            ) {
                when (set) {
                    is WheelSet.Logged -> LoggedRung(set, exercise, lifterUnit)
                    is WheelSet.Pending -> PendingRung(set.recommendation, exercise)
                }
            }
        }
    }
}

/** A set that happened, written as it was performed. */
@Composable
private fun LoggedRung(rung: WheelSet.Logged, exercise: Exercise, lifterUnit: WeightUnit) {
    val displayUnit = rung.set.displayUnit ?: exercise.displayUnit
    Text(
        text = stringResource(R.string.wheel_set_number, rung.set.setIndex),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = rung.set.leadingEffort.summary(displayUnit, lifterUnit),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
}

/**
 * The set about to be logged, holding whatever the app is asking for.
 *
 * Reads the recommendation and nothing else, so that the plate line, the generated warm-ups and
 * the rests arriving with their own tickets show up here without this being rebuilt.
 */
@Composable
private fun PendingRung(recommendation: Recommendation?, exercise: Exercise) {
    Text(
        text = stringResource(R.string.wheel_next_set),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(PENDING),
    )
    if (recommendation == null) {
        // Nothing to say about this one: an exercise the app does not coach, or a ramp that has
        // run its course. The controls below still log it.
        Text(
            text = stringResource(R.string.wheel_nothing_asked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        NextSet(recommendation, exercise.loggingType, exercise.displayUnit)
    }
}

/**
 * Which item is nearest the middle of the viewport, or null before anything is laid out.
 *
 * The middle rather than the first visible, because the middle is what a wheel focuses and what
 * the lifter is reading.
 */
private fun LazyListState.centredItem(): Int? {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - centre) }?.index
}

/** How close item [index] is to the middle, from 0 at the edges to 1 in the centre. */
private fun LazyListState.falloffAt(index: Int): Float {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val distance = abs(item.offset + item.size / 2f - centre)
    return 1f - (distance / centre.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

/** How tall the wheel stands. Three rungs' worth: what is coming, what is now, what just went. */
val WHEEL_HEIGHT = 200.dp

/** What a set at the very edge of the wheel shrinks and fades to. */
private const val FAR_SCALE = 0.7f
private const val FAR_ALPHA = 0.35f

/** Handles for the tests, which care about what the screen does rather than how it looks. */
const val WHEEL = "set-wheel"
const val PENDING = "pending-set"
const val WEIGHT_FIELD = "weight-field"
const val LOG_SET = "log-set"
