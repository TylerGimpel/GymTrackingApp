package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.domain.asAmountText
import androidx.compose.foundation.layout.fillMaxWidth
import com.tylergimpel.gymtracker.domain.asTimeText
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.DistanceUnit
import com.tylergimpel.gymtracker.domain.Drop
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.reading
import com.tylergimpel.gymtracker.ui.WeightUnitChips
import com.tylergimpel.gymtracker.ui.label
import com.tylergimpel.gymtracker.ui.theme.AppOutlinedButton
import com.tylergimpel.gymtracker.ui.theme.TextButton

/**
 * What choosing a unit beside a weight means.
 *
 * Correcting or continuing a set changes that set alone, which is [forThisSetAlone] and the
 * default. The entry controls pass their own, because a unit chosen while entering is a statement
 * about the machine and is remembered for the exercise.
 */
internal data class UnitChoice(
    val onReadIn: (WeightUnit) -> Unit,
    /** Whether the entry controls are departing for one set; null where there is nothing to settle. */
    val overriding: Boolean? = null,
    val onToggleOverride: () -> Unit = {},
) {
    companion object {
        fun forThisSetAlone(draft: SetDraft, onChange: (SetDraft) -> Unit) =
            UnitChoice(onReadIn = { onChange(draft.readIn(it)) })
    }
}

/**
 * The numbers a set is logged as, and the steppers that move them.
 *
 * Separate from the rest of a set's fields because it is the half a lifter touches *during* one,
 * with a bar in the other hand. On the logging screen that earns it a fixed place below the
 * wheel (`docs/design/logging-screen.md`); in a dialog correcting a set it is simply the top of
 * the form. Neither placement is decided here.
 */
@Composable
internal fun SetValueFields(draft: SetDraft, onChange: (SetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // What the exercise records decides what is on screen. A plank offered a weight field is
        // asking for a number that means nothing, and one that reaches storage is worse than one
        // that does not.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draft.loggingType.recordsWeight) {
                OutlinedTextField(
                    value = draft.weightText,
                    onValueChange = { onChange(draft.copy(weightText = it)) },
                    // Labelled with the unit the number is read in, because that is what decides
                    // what typing 225 means.
                    label = { Text(stringResource(draft.displayUnit.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag(WEIGHT_FIELD),
                )
            }
            if (draft.loggingType.recordsReps) {
                OutlinedTextField(
                    value = draft.repsText,
                    onValueChange = { onChange(draft.copy(repsText = it)) },
                    label = { Text(stringResource(R.string.set_reps)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (draft.loggingType.recordsAddedWeight) {
                OutlinedTextField(
                    value = draft.addedWeightText,
                    onValueChange = { onChange(draft.copy(addedWeightText = it)) },
                    label = { Text(stringResource(R.string.set_added_weight, stringResource(draft.displayUnit.label))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (draft.loggingType.recordsAssistance) {
                OutlinedTextField(
                    value = draft.addedWeightText,
                    onValueChange = { onChange(draft.copy(addedWeightText = it)) },
                    label = { Text(stringResource(R.string.set_assistance, stringResource(draft.displayUnit.label))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (draft.loggingType.recordsDistance) {
                OutlinedTextField(
                    value = draft.distanceText,
                    onValueChange = { onChange(draft.copy(distanceText = it)) },
                    // Labelled with the unit it is read in, as the weight field is.
                    label = { Text(stringResource(draft.distanceUnit.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (draft.loggingType.recordsDuration) {
                OutlinedTextField(
                    value = draft.durationText,
                    onValueChange = { onChange(draft.copy(durationText = it)) },
                    label = { Text(stringResource(R.string.set_seconds)) },
                    // What the lifter reads back is a clock, so the field says what it will become.
                    supportingText = draft.duration?.let { { Text(it.asTimeText()) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // A belt and a machine's help are weights too, stepped by the same plates.
        if (draft.loggingType.recordsAnyWeight) {
            WeightSteppers(draft, onChange)
        }
    }
}

/**
 * Everything about a set that is not the numbers: the unit, what was measured, a note, the type.
 *
 * The half nobody touches mid-set — a unit is settled once for a machine, an effort rating is
 * given after the fact, and a set type is chosen when it changes rather than every set. Which is
 * why the logging screen lets this scroll away while it pins the numbers.
 */
@Composable
internal fun SetDetailFields(
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    showType: Boolean = true,
    showNote: Boolean = true,
    /**
     * Whether to offer what the lifter measured about the set.
     *
     * Off for the second side of a per-side set, which is one set and felt one way: offering
     * fields there that the left-hand side's answers overwrite would discard what was typed
     * into them.
     */
    showMeasurements: Boolean = showNote,
    unitChoice: UnitChoice? = UnitChoice.forThisSetAlone(draft, onChange),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.loggingType.recordsAnyWeight) {
            unitChoice?.let { UnitChoiceRow(draft, it) }
        }
        if (showMeasurements) {
            // All three optional and all three the lifter's own: an effort rating is a judgement
            // nobody can make for them, a heart rate is typed because this app reads no wearable,
            // and the rest is typed until the rest timer arrives to fill it.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft.rpeText,
                    onValueChange = { onChange(draft.copy(rpeText = it)) },
                    label = { Text(stringResource(R.string.set_rpe)) },
                    // Said rather than swallowed: a number this field will not keep should not look
                    // to the lifter as though it was written down.
                    isError = draft.rpeIsUnreadable,
                    supportingText = if (draft.rpeIsUnreadable) {
                        { Text(stringResource(R.string.set_rpe_range)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.heartRateText,
                    onValueChange = { onChange(draft.copy(heartRateText = it)) },
                    label = { Text(stringResource(R.string.set_heart_rate)) },
                    isError = draft.heartRateIsUnreadable,
                    supportingText = if (draft.heartRateIsUnreadable) {
                        { Text(stringResource(R.string.set_heart_rate_range)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.restTakenText,
                    onValueChange = { onChange(draft.copy(restTakenText = it)) },
                    label = { Text(stringResource(R.string.set_rest_taken)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (showNote) {
            OutlinedTextField(
                value = draft.note.orEmpty(),
                onValueChange = { onChange(draft.copy(note = it.takeIf(String::isNotBlank))) },
                label = { Text(stringResource(R.string.set_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showType) {
            SetTypeChooser(draft.type) { onChange(draft.copy(type = it)) }
        }
    }
}

/** The numbers and the rest of it together, which is what a dialog editing one set wants. */
@Composable
internal fun SetDraftFields(
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    showType: Boolean = true,
    showNote: Boolean = true,
    showMeasurements: Boolean = showNote,
    unitChoice: UnitChoice? = UnitChoice.forThisSetAlone(draft, onChange),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetValueFields(draft, onChange)
        SetDetailFields(draft, onChange, showType, showNote, showMeasurements, unitChoice)
    }
}

/**
 * Up and down by the step of the unit the number is read in.
 *
 * The step rather than the **Increment**: this is the stepper's round number — 2.5 kg or 5 lb —
 * and belongs to how a weight is typed rather than to what the equipment can be loaded to. What
 * the equipment can do is the engine's business, and it has already done it by the time a
 * prescription reaches this field.
 */
@Composable
internal fun WeightSteppers(draft: SetDraft, onChange: (SetDraft) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppOutlinedButton(
            text = stringResource(R.string.set_weight_down, draft.displayUnit.step.asAmountText()),
            onClick = { onChange(draft.stepWeight(-1)) },
        )
        AppOutlinedButton(
            text = stringResource(R.string.set_weight_up, draft.displayUnit.step.asAmountText()),
            onClick = { onChange(draft.stepWeight(1)) },
        )
    }
}

/**
 * What the number in the field is read in.
 *
 * A row of its own rather than trailing the steppers: "This set only" is the whole of the
 * override and is useless half off the edge of a phone. Not a thumb-zone control — a unit is
 * settled once for a machine and then left alone, which is why it scrolls with the rest.
 */
@Composable
internal fun UnitChoiceRow(draft: SetDraft, choice: UnitChoice) {
    WeightUnitChips(
        selected = draft.displayUnit,
        // A reading, not a value: the number in the field is rewritten and what it weighs
        // is untouched, whichever of the two this settles.
        onReadIn = choice.onReadIn,
    ) {
        choice.overriding?.let { overriding ->
            FilterChip(
                selected = overriding,
                onClick = choice.onToggleOverride,
                label = { Text(stringResource(R.string.set_unit_this_set_only)) },
            )
        }
    }
}

/**
 * Which kind of set this is.
 *
 * Always on screen rather than hidden behind a menu, because the type is chosen by the lifter and
 * never inferred: a warm-up that goes in as working quietly inflates every volume figure built on
 * it, and nothing later can tell the difference.
 */
@Composable
internal fun SetTypeChooser(selected: SetType, onSelect: (SetType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.set_type), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.label)) },
                )
            }
        }
    }
}

@Composable
internal fun DropRow(drop: Drop, displayUnit: WeightUnit) {
    Text(
        text = stringResource(
            R.string.set_drop_summary,
            drop.orderIndex,
            drop.weight.asTextIn(displayUnit),
            stringResource(displayUnit.label),
            drop.reps,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
internal fun EditSetDialog(
    editing: EditingEffort,
    onChange: (SetDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    /** Continues the set at a lighter weight, where it was a weight and reps. */
    onAddDrop: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_edit_set)) },
        // RPE, heart rate and a note per set are no longer asked for (#142); what was stored stays.
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetDraftFields(editing.draft, onChange, showNote = false)
                onAddDrop?.let { TextButton(onClick = it) { Text(stringResource(R.string.set_add_drop)) } }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = editing.draft.isLoggable) {
                Text(stringResource(R.string.session_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.session_delete)) }
        },
    )
}

@Composable
internal fun AddDropDialog(
    adding: AddingDrop,
    onChange: (SetDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.set_drop)) },
        // Neither a type, a note nor a unit of its own: a drop is part of the set it continues, so
        // it is read in that set's unit and has nowhere to store the other two. Offering fields
        // that discard what is typed into them is worse than not offering them.
        text = {
            SetDraftFields(
                adding.draft,
                onChange,
                showType = false,
                showNote = false,
                unitChoice = null,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = adding.draft.isLoggable) {
                Text(stringResource(R.string.session_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** What one effort reads as: what it recorded, written the way the lifter entered it. */
@Composable
internal fun Effort.summary(displayUnit: WeightUnit, lifterUnit: WeightUnit): String =
    when (val done = performance) {
        // A weight is read in the machine's unit and a distance in the lifter's own: the gym's lb
        // lat pulldown carries its label whoever walks up to it, while five kilometres is five
        // kilometres to somebody who thinks in kilograms.
        is Performance.WeightAndReps -> stringResource(
            R.string.set_summary,
            done.weight.asTextIn(displayUnit),
            stringResource(displayUnit.label),
            done.reps,
        )

        is Performance.BodyweightReps -> done.addedWeight?.let { added ->
            stringResource(
                R.string.set_added_weight_summary,
                added.asTextIn(displayUnit),
                stringResource(displayUnit.label),
                done.reps,
            )
        } ?: stringResource(R.string.set_reps_summary, done.reps)

        is Performance.Assisted -> stringResource(
            R.string.set_assisted_summary,
            done.assistance.asTextIn(displayUnit),
            stringResource(displayUnit.label),
            done.reps,
        )

        is Performance.Timed -> done.duration.asTimeText()

        is Performance.DistanceAndTime -> {
            val distanceUnit = DistanceUnit.alongside(lifterUnit)
            stringResource(
                R.string.set_distance_summary,
                done.distance.asTextIn(distanceUnit),
                stringResource(distanceUnit.label),
                done.duration.asTimeText(),
            )
        }
    }
