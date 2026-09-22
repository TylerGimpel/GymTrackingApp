package com.tylergimpel.gymtracker.ui.routine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.seedRepRange
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.splitShapesFor
import com.tylergimpel.gymtracker.domain.SplitShape
import com.tylergimpel.gymtracker.domain.whatItTrains
import com.tylergimpel.gymtracker.ui.exercise.ExercisePicker
import com.tylergimpel.gymtracker.domain.Exercise

/**
 * A minimal routine editor: a name, the properties, and an ordered list of day names.
 *
 * Deliberately provisional. Generation, pinning, validation and revision belong to the routines
 * spec; this exists so that the cursor has a routine to point into and so the day structure can be
 * exercised by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    onBack: () -> Unit,
    onAddNewExercise: (Long) -> Unit,
    viewModel: RoutineViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.progress?.routine
    val defaultName = stringResource(R.string.routine_default_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_title)) },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.routine_done)) }
                },
            )
        },
    ) { insets ->
        if (routine == null) {
            EmptyRoutine(
                insets = insets,
                availableEquipment = state.availableEquipment,
                onCreate = { viewModel.createRoutine(defaultName) },
                onCreateFromShape = { shape, frequency, cardio, equipment ->
                    viewModel.createFromShape(
                        name = shape.description,
                        shape = shape,
                        weeklyFrequency = frequency,
                        cardio = cardio,
                        equipment = equipment,
                    )
                },
            )
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            item { RoutineProperties(routine, state.availableEquipment, viewModel) }
            item { HorizontalDivider() }
            item {
                Text(
                    text = stringResource(R.string.routine_days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(routine.rotation, key = RoutineDay::id) { day ->
                RoutineDayRow(
                    day = day,
                    position = day.orderIndex,
                    isNext = state.progress?.cursor?.id == day.id,
                    onRename = { viewModel.renameDay(day.id, it) },
                    onMove = { viewModel.moveDay(day.id, it) },
                    onRemove = { viewModel.removeDay(day.id) },
                    onAddExercise = { viewModel.startPicking(day.id) },
                    onRemoveExercise = { viewModel.removeExerciseFromDay(day.id, it) },
                )
                HorizontalDivider()
            }
            item { AddDay(onAdd = viewModel::addDay) }
            item { WhatItTrains(state.progress?.routine) }
            item {
                TextButton(
                    onClick = viewModel::deleteRoutine,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(stringResource(R.string.routine_delete))
                }
            }
        }
    }

    // Over the routine rather than away from it: filling a day is part of building one, and
    // being thrown at another screen to do it loses the lifter their place.
    state.picking?.let { picking ->
        RoutinePicker(
            picking = picking,
            equipment = state.progress?.routine?.equipment.orEmpty(),
            addingDisliked = state.addingDisliked,
            onPick = viewModel::pick,
            // Leaving for the bank's own form closes this, so coming back does not reopen a
            // list taken before the exercise they went to write down existed.
            onAddNew = {
                viewModel.cancelPicking()
                onAddNewExercise(picking.dayId)
            },
            onCancel = viewModel::cancelPicking,
        )
    }
}

/**
 * What the routine does, muscle by muscle.
 *
 * **Facts and never grades.** "This cycle trains chest 3x a cycle" is something a lifter can act
 * on; "your routine is unbalanced" is marking their homework. So this prints counts and says
 * nothing about whether they are the right ones — training chest three times and back once is a
 * decision somebody is allowed to make, and the app's only job is to make sure they made it
 * knowing what it was.
 *
 * Nothing here blocks a save, because there is nothing here to fail.
 */
@Composable
private fun WhatItTrains(routine: Routine?) {
    val load = remember(routine) { routine?.whatItTrains().orEmpty() }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.routine_what_it_trains),
            style = MaterialTheme.typography.labelLarge,
        )
        if (load.isEmpty()) {
            Text(
                text = stringResource(R.string.routine_trains_nothing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        load.forEach { entry ->
            Text(
                text = stringResource(
                    R.string.routine_muscle_load,
                    entry.muscle,
                    entry.sessions,
                    entry.weeklySets.asSetsText(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The one thing worth saying beyond the counts, and it is still a count: work past the cap
        // is work that is there and not doing anything.
        load.filter { it.pastSessionCap }.forEach { entry ->
            Text(
                text = stringResource(
                    R.string.routine_past_session_cap,
                    entry.muscle,
                    entry.mostInOneSession.asSetsText(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A fractional set count as a lifter would write it.
 *
 * Halves exist because indirect work counts at a half (ADR 0005), and "7.5" is the honest way to
 * print one. A whole number keeps no decimal point, because "8.0 sets" reads like a measurement.
 */
private fun Double.asSetsText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

@Composable
private fun EmptyRoutine(
    insets: PaddingValues,
    availableEquipment: List<String>,
    onCreate: () -> Unit,
    onCreateFromShape: (SplitShape, Int, CardioPlacement, Set<String>) -> Unit,
) {
    // The one question everybody can answer, and the one the volume arithmetic actually needs.
    // What is never asked is a cycle length: "how many days is your routine" reads as both this
    // and the number of positions, and conflating them makes sound routines look invalid.
    var frequency by rememberSaveable { mutableStateOf(3) }
    var cardio by rememberSaveable { mutableStateOf(CardioPlacement.NONE) }
    var equipment by rememberSaveable { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(insets),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.routine_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                text = stringResource(R.string.routine_setup_frequency),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..7).forEach { times ->
                    FilterChip(
                        selected = times == frequency,
                        onClick = { frequency = times },
                        label = { Text(times.toString()) },
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.routine_setup_frequency_why),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ChoiceRow(
                label = stringResource(R.string.routine_cardio),
                options = listOf(
                    CardioPlacement.NONE to stringResource(R.string.cardio_none),
                    CardioPlacement.OWN_DAY to stringResource(R.string.cardio_own_day),
                    CardioPlacement.AFTER_LIFTING to stringResource(R.string.cardio_after_lifting),
                ),
                selected = cardio,
                onSelect = { cardio = it },
            )
        }
        item {
            Text(
                text = stringResource(R.string.routine_equipment),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item {
            // One row of chips off the bank's own equipment vocabulary, which is a dozen values
            // and finishable in a sitting. A checklist nobody completes is a checklist that leaves
            // the generator guessing, and it guesses by offering everything.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableEquipment.forEach { item ->
                    FilterChip(
                        selected = item in equipment,
                        onClick = {
                            equipment =
                                if (item in equipment) equipment - item else equipment + item
                        },
                        label = { Text(item) },
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.routine_setup_shape),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item {
            // Said out loud, because the honest thing to say about splits is that the evidence
            // found no difference between them.
            Text(
                text = stringResource(R.string.routine_setup_shape_why),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // By shape, never by position count: "alternate upper and lower" is a thing anyone can
        // choose, and "a 2-position cycle" is not. Nothing invalid is here to be rejected,
        // because nothing invalid was ever authored.
        items(splitShapesFor(frequency, cardio), key = SplitShape::id) { shape ->
            TextButton(onClick = { onCreateFromShape(shape, frequency, cardio, equipment) }) {
                Text(
                    stringResource(
                        R.string.routine_shape_days,
                        shape.description,
                        shape.days.size,
                    ),
                )
            }
        }
        item {
            // The same mechanism from an empty start, for somebody who knows exactly what they
            // want and does not need a shape suggested to them.
            TextButton(onClick = onCreate) {
                Text(stringResource(R.string.routine_build_by_hand))
            }
        }
    }
}

/**
 * The routine's properties.
 *
 * Editable without touching the day structure, which is why they live in their own block: changing
 * a goal or the equipment at a gym is not a revision of the rotation and ends no cycle.
 */
@Composable
private fun RoutineProperties(
    routine: Routine,
    availableEquipment: List<String>,
    viewModel: RoutineViewModel,
) {
    var name by rememberSaveable(routine.id) { mutableStateOf(routine.name) }
    var cadence by rememberSaveable(routine.id) { mutableStateOf(routine.cadence.toString()) }

    fun save(
        goal: RoutineGoal = routine.goal,
        repPreference: RepPreference = routine.repPreference,
        equipment: Set<String> = routine.equipment,
        cardio: CardioPlacement = routine.cardio,
    ) = viewModel.editProperties(
        name = name.ifBlank { routine.name },
        goal = goal,
        cadence = cadence.toIntOrNull()?.coerceAtLeast(1) ?: routine.cadence,
        equipment = equipment,
        repPreference = repPreference,
        cardio = cardio,
    )

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; save() },
            label = { Text(stringResource(R.string.routine_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cadence,
            onValueChange = { cadence = it; save() },
            label = { Text(stringResource(R.string.routine_cadence)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        ChoiceRow(
            label = stringResource(R.string.routine_goal),
            options = RoutineGoal.entries.map { it to it.name.lowercase() },
            selected = routine.goal,
            onSelect = { save(goal = it) },
        )
        ChoiceRow(
            label = stringResource(R.string.routine_rep_preference),
            options = RepPreference.entries.map { it to it.name.lowercase() },
            selected = routine.repPreference,
            onSelect = { save(repPreference = it) },
        )

        // Said here, at the moment the two are chosen, because this is where the lifter can do
        // something about it. An override nobody is told about produces training that will not
        // deliver the goal, and they do not find out for three months.
        val seeded = seedRepRange(routine.goal, routine.repPreference)
        if (seeded.overruled != null) {
            Text(
                text = stringResource(
                    R.string.routine_preference_overruled,
                    seeded.range.bottom,
                    seeded.range.top,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Whether they do any, and where it goes. A day of its own takes a position in the
        // rotation; after lifting takes none, because the lifting session still happened.
        ChoiceRow(
            label = stringResource(R.string.routine_cardio),
            options = listOf(
                CardioPlacement.NONE to stringResource(R.string.cardio_none),
                CardioPlacement.OWN_DAY to stringResource(R.string.cardio_own_day),
                CardioPlacement.AFTER_LIFTING to stringResource(R.string.cardio_after_lifting),
            ),
            selected = routine.cardio,
            onSelect = { save(cardio = it) },
        )

        Text(stringResource(R.string.routine_equipment), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableEquipment.forEach { item ->
                val owned = item in routine.equipment
                FilterChip(
                    selected = owned,
                    onClick = {
                        save(
                            equipment = if (owned) {
                                routine.equipment - item
                            } else {
                                routine.equipment + item
                            },
                        )
                    },
                    label = { Text(item) },
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
private fun RoutineDayRow(
    day: RoutineDay,
    position: Int,
    isNext: Boolean,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (String) -> Unit,
) {
    var name by rememberSaveable(day.id) { mutableStateOf(day.name) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onRename(it) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        // A cardio day is said out loud, because an empty one looks like an unfinished lifting
        // day otherwise — and because what it does to the volume arithmetic is worth knowing.
        if (day.kind == RoutineDayKind.CARDIO) {
            Text(
                text = stringResource(R.string.routine_cardio_day_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isNext) {
                Text(
                    text = stringResource(R.string.routine_next_up),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            TextButton(onClick = { onMove(-1) }) { Text(stringResource(R.string.routine_move_up)) }
            TextButton(onClick = { onMove(1) }) { Text(stringResource(R.string.routine_move_down)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.routine_remove)) }
        }

        // What the day holds, which is what a session started from it will copy. Editing this is
        // not a revision: the rotation is unchanged, so the cycle carries on.
        if (day.exercises.isEmpty()) {
            Text(
                text = stringResource(R.string.routine_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        day.exercises.forEach { planned ->
            val exercise = planned.exercise
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // What to do and how much of it, which is what a day says. The count is the
                // engine's and arrives whether the exercise was generated or added by hand.
                Text(
                    text = planned.sets
                        ?.let { stringResource(R.string.routine_exercise_sets, exercise.name, it) }
                        ?: exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onRemoveExercise(exercise.id) }) {
                    Text(stringResource(R.string.routine_remove_exercise))
                }
            }
        }
        TextButton(onClick = onAddExercise) {
            Text(stringResource(R.string.routine_add_exercise))
        }
    }
}

/**
 * The one picker, opened to fill a routine day.
 *
 * The same component a **Swap** opens, with nothing ranked in front of it: a day being built has
 * no exercise to be "instead of", so the lifter starts from their own gym and types.
 *
 * A **Dislike** stops the app suggesting something. It was never a promise to argue with somebody
 * who asks for one by name, so picking a banned exercise asks once and then adds it — the routine
 * is theirs.
 */
@Composable
private fun RoutinePicker(
    picking: PickingFor,
    equipment: Set<String>,
    addingDisliked: Exercise?,
    onPick: (Exercise) -> Unit,
    onAddNew: () -> Unit,
    onCancel: () -> Unit,
) {
    ExercisePicker(
        title = stringResource(R.string.routine_pick_exercise, picking.dayName),
        bank = picking.bank,
        equipment = equipment,
        onPick = onPick,
        onAddNew = onAddNew,
        onCancel = onCancel,
        note = addingDisliked?.let { stringResource(R.string.picker_disliked, it.name) },
    )
}

@Composable
private fun AddDay(onAdd: (String, RoutineDayKind) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var isCardio by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.routine_new_day)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAdd(
                        name.trim(),
                        if (isCardio) RoutineDayKind.CARDIO else RoutineDayKind.LIFTING,
                    )
                    name = ""
                    isCardio = false
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.routine_add))
            }
        }
        // Said when the day is made rather than afterwards: what a day is for decides how it is
        // counted, and a cardio day added as a lifting one is wrong from its first cycle.
        FilterChip(
            selected = isCardio,
            onClick = { isCardio = !isCardio },
            label = { Text(stringResource(R.string.routine_day_is_cardio)) },
        )
    }
}
