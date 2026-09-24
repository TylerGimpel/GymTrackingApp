package com.tylergimpel.gymtracker.ui.exercise

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.offeredFor

/** The picker's list, so a test can find it wherever it was opened from. */
const val EXERCISE_PICKER = "exercise-picker"

/** The field a lifter types a name into. */
const val EXERCISE_PICKER_SEARCH = "exercise-picker-search"

/**
 * One exercise, chosen — to swap onto, or to put in a routine day.
 *
 * **The same component for both**, because both ask the same question and a lifter should not
 * have to learn two lists. What differs is only what the caller ranks in front: a swap arrives
 * with candidates sharing the muscle and the mechanic, history first; a routine day arrives with
 * nothing and a blank field.
 *
 * The bank is held to the lifter's own equipment to begin with, because a picker opening on
 * machines their gym has never had makes them scroll past the useless half of it every time. The
 * toggle is visible rather than buried: somebody building a routine for a gym they are about to
 * join needs to reach past what they have today.
 *
 * @param suggested what the caller ranked, shown until something is typed. Empty is ordinary.
 * @param note something to say before the list — that the bans have left nothing good, or that
 *   what they just picked is one they said they did not want. Never a reason to refuse.
 */
@Composable
fun ExercisePicker(
    title: String,
    bank: List<Exercise>,
    equipment: Set<String>,
    onPick: (Exercise) -> Unit,
    /** Where adding an exercise the bank lacks makes sense; null hides it, as mapping a name does. */
    onAddNew: (() -> Unit)?,
    onCancel: () -> Unit,
    suggested: List<Exercise> = emptyList(),
    note: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var ownEquipmentOnly by rememberSaveable { mutableStateOf(true) }

    // The muscles the bank actually uses, rather than a list restated here that could drift from
    // it. `data/README.md` is where the closed vocabulary lives.
    val muscles = remember(bank) {
        bank.flatMap(Exercise::primaryMuscles).distinct().sorted()
    }

    // What the caller ranked stands until the lifter says something of their own. Once they have
    // typed or filtered, they are asking a different question and the whole bank answers it.
    val asking = query.isNotBlank() || muscle != null
    val offered = when {
        !asking && suggested.isNotEmpty() -> suggested
        else -> bank.offeredFor(
            equipment = equipment,
            query = query,
            muscle = muscle,
            ownEquipmentOnly = ownEquipmentOnly,
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(EXERCISE_PICKER_SEARCH),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Off by default and visible, which is the whole point of it: the lifter can
                    // see that the list has been narrowed and can widen it in one tap.
                    FilterChip(
                        selected = !ownEquipmentOnly,
                        onClick = { ownEquipmentOnly = !ownEquipmentOnly },
                        label = { Text(stringResource(R.string.picker_everything)) },
                    )
                    muscles.forEach { name ->
                        FilterChip(
                            selected = name == muscle,
                            onClick = { muscle = if (name == muscle) null else name },
                            label = { Text(name) },
                        )
                    }
                }

                if (offered.isEmpty()) {
                    // A real answer rather than an empty box: the checklist may be narrow, or the
                    // bank may simply not have what they are typing.
                    Text(stringResource(R.string.picker_nothing))
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp).testTag(EXERCISE_PICKER),
                ) {
                    items(offered, key = Exercise::id) { exercise ->
                        TextButton(onClick = { onPick(exercise) }) { Text(exercise.name) }
                    }
                }
            }
        },
        // The way out of the bank entirely, for a machine the app has never heard of. Given the
        // same room as the list, because nobody is ever stuck with what is offered.
        confirmButton = {
            onAddNew?.let { TextButton(onClick = it) { Text(stringResource(R.string.picker_add_new)) } }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
