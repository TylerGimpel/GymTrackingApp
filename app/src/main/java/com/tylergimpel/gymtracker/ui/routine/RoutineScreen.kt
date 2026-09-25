package com.tylergimpel.gymtracker.ui.routine

import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import com.tylergimpel.gymtracker.ui.theme.AppButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
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
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.conflictsFor
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.bindingOn
import com.tylergimpel.gymtracker.domain.Binding
import com.tylergimpel.gymtracker.domain.weeklyVolumeTargetFor
import com.tylergimpel.gymtracker.domain.SplitShape
import com.tylergimpel.gymtracker.domain.whatItTrains
import com.tylergimpel.gymtracker.ui.exercise.ExercisePicker
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.RecoveredRoutine
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
            AppTopBar(
                title = stringResource(R.string.routine_title),
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
                experience = state.experience,
                onCreate = { viewModel.createRoutine(defaultName) },
                onRebuildFromLog = viewModel::rebuildFromLog,
                onCreateFromShape = { shape, setup ->
                    viewModel.createFromShape(name = shape.description, shape = shape, setup = setup)
                },
            )
            state.choosingRoutine?.let { choice ->
                ChooseRoutine(
                    choices = choice.routines,
                    limit = choice.limit,
                    onChoose = viewModel::choose,
                    onCancel = viewModel::cancelRebuildChoice,
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            item {
                RoutineProperties(routine, state.availableEquipment, state.experience, viewModel)
            }
            item { HorizontalDivider() }
            item {
                Text(
                    text = stringResource(R.string.routine_days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Beside the days, where the change was made, rather than as a notification somewhere
            // else. A fact and nothing more: no button, because there is nothing to approve —
            // the cycle has already started again by the time this is read.
            state.revised?.let { revised ->
                item {
                    Text(
                        text = stringResource(
                            when (revised) {
                                Revised.ADDED -> R.string.routine_revised_added
                                Revised.REMOVED -> R.string.routine_revised_removed
                                Revised.MOVED -> R.string.routine_revised_moved
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalGymColors.current.accentText,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
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
                    onPin = { id, pinned -> viewModel.setPinned(day.id, id, pinned) },
                )
                HorizontalDivider()
            }
            item { AddDay(onAdd = viewModel::addDay) }
            item {
                Regenerate(
                    canRegenerate = state.progress?.routine?.shape != null,
                    canUndo = state.canUndo,
                    onRegenerate = viewModel::regenerate,
                    onUndo = viewModel::undoRegeneration,
                )
            }
            item { WhatItTrains(state.progress?.routine) }
            item { WhatIsBinding(state.progress?.routine, state.bank) }
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

    // An offer, in front of the lifter, with declining as easy as accepting. The routine is
    // untouched until they answer, and untouched afterwards if they say no.
    state.offeringRebuild?.let { offer ->
        AlertDialog(
            onDismissRequest = viewModel::declineRebuild,
            text = {
                Text(
                    stringResource(
                        when (offer) {
                            RebuildOffer.EQUIPMENT -> R.string.routine_offer_equipment
                            RebuildOffer.WHAT_IT_IS_FOR -> R.string.routine_offer_rebuild
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptRebuild) {
                    Text(stringResource(R.string.routine_offer_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineRebuild) {
                    Text(stringResource(R.string.routine_offer_decline))
                }
            },
        )
    }

    // Over the routine rather than away from it: filling a day is part of building one, and
    // being thrown at another screen to do it loses the lifter their place.
    state.nothingToRebuild.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissNothingToRebuild,
            text = { Text(stringResource(R.string.routine_rebuild_none)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissNothingToRebuild) {
                    Text(stringResource(R.string.routine_offer_decline))
                }
            },
        )
    }

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
    val target = routine?.let { weeklyVolumeTargetFor(it.goal) }

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
        // The band the goal aims at, said once rather than beside every muscle. A number to read
        // the rows against, and still a fact: it is what the app is aiming for, not a mark out of
        // ten for what they built.
        target?.takeIf { load.isNotEmpty() }?.let {
            Text(
                text = stringResource(
                    R.string.routine_muscle_target,
                    it.start.asSetsText(),
                    it.endInclusive.asSetsText(),
                ),
                style = MaterialTheme.typography.bodySmall,
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
 * What the routine cannot satisfy, and what would give.
 *
 * **Never a refusal.** Nothing on this screen stops a lifter saving or training what they built —
 * an impossible request is a conversation, so the app says what is binding, offers what could be
 * relaxed, and leaves the decision where it belongs.
 *
 * Silent when everything holds, like the rest of this screen.
 */
@Composable
private fun WhatIsBinding(routine: Routine?, bank: List<Exercise>) {
    val findings = remember(routine, bank) {
        routine?.let { bindingOn(it, bank = bank) }.orEmpty()
    }
    if (findings.isEmpty()) return

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.routine_binding),
            style = MaterialTheme.typography.labelLarge,
        )
        findings.forEach { finding ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = finding.said(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // What to relax, rather than what they did wrong. Every finding carries its own,
                // because a constraint named without a way out is just a complaint.
                finding.relax.forEach {
                    Text(
                        text = stringResource(R.string.routine_relax, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One finding, in the lifter's terms rather than the model's. */
@Composable
private fun Binding.said(): String = when (this) {
    is Binding.SessionTooLong -> stringResource(
        R.string.routine_too_long,
        day,
        estimated.toMinutes().toInt(),
        ceiling.toMinutes().toInt(),
    )

    is Binding.VolumeOutsideBand -> stringResource(
        R.string.routine_off_band,
        muscle,
        weeklySets.asSetsText(),
        target.start.asSetsText(),
        target.endInclusive.asSetsText(),
    )

    is Binding.OnlyDislikedLeft -> stringResource(R.string.routine_only_disliked, muscle)

    // Which of the two is true, because "never trained" is false about a muscle getting real
    // secondary work — and the lifter deciding whether to care needs the honest one.
    is Binding.MuscleNotTrained -> if (weeklySets == 0.0) {
        stringResource(R.string.routine_not_trained, muscle)
    } else {
        stringResource(R.string.routine_only_secondary, muscle, weeklySets.asSetsText())
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

/**
 * Letting the app build a routine: six questions, one at a time, in the order people finish (#86).
 *
 * Progress is shown so it reads as finite, every question can be skipped — and says what skipping
 * it assumes — and going back keeps every other answer. What is never asked is a cycle length:
 * "how many days is your routine" reads as both cadence and the number of positions, and
 * conflating them makes sound routines look invalid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyRoutine(
    insets: PaddingValues,
    availableEquipment: List<String>,
    /** How much training the lifter has behind them, which one conflict rule reads. */
    experience: Experience,
    onCreate: () -> Unit,
    onCreateFromShape: (SplitShape, RoutineSetup) -> Unit,
    onRebuildFromLog: (RoutineSetup) -> Unit,
) {
    var answers by rememberSaveable(stateSaver = SetupAnswersSaver) { mutableStateOf(SetupAnswers()) }
    val setup = answers.toRoutineSetup()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_progress, answers.stepNumber, SetupAnswers.STEPS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { answers.stepNumber / SetupAnswers.STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )

        when (answers.step) {
            SetupStep.GOAL -> SetupQuestion(
                question = stringResource(R.string.routine_setup_goal),
                options = RoutineGoal.entries.map { it to it.name.lowercase() },
                selected = answers.goal,
                onSelect = { answers = answers.copy(goal = it) },
                skipped = stringResource(
                    R.string.routine_setup_goal_skipped,
                    RoutineSetup.ASSUMED_GOAL.name.lowercase(),
                ),
            )

            SetupStep.REP_PREFERENCE -> {
                SetupQuestion(
                    question = stringResource(R.string.routine_setup_rep_preference),
                    options = RepPreference.entries.map { it to it.name.lowercase() },
                    selected = answers.repPreference,
                    onSelect = { answers = answers.copy(repPreference = it) },
                    skipped = stringResource(
                        R.string.routine_setup_rep_preference_skipped,
                        RoutineSetup.ASSUMED_REP_PREFERENCE.name.lowercase(),
                    ),
                )
                // Said at the moment the two are chosen, against what setup will actually build
                // with, so a skipped question that lands in a conflict says so too.
                Conflicts(setup, experience)
            }

            SetupStep.CADENCE -> {
                SetupQuestion(
                    question = stringResource(R.string.routine_setup_frequency),
                    options = (1..7).map { it to it.toString() },
                    selected = answers.cadence,
                    onSelect = { answers = answers.copy(cadence = it) },
                    skipped = stringResource(
                        R.string.setup_cadence_skipped,
                        SetupAnswers.ASSUMED_CADENCE,
                    ),
                )
                Explanation(stringResource(R.string.routine_setup_frequency_why))
            }

            SetupStep.SHAPE -> {
                Text(stringResource(R.string.routine_setup_shape), style = MaterialTheme.typography.titleMedium)
                // Said out loud, because the honest thing to say about splits is that the evidence
                // found no difference between them.
                Explanation(stringResource(R.string.routine_setup_shape_why))
                // By shape, never by position count: "alternate upper and lower" is a thing anyone
                // can choose, and "a 2-position cycle" is not. Nothing invalid is here to reject.
                answers.shapesOffered().forEach { shape ->
                    FilterChip(
                        selected = answers.shapeId == shape.id,
                        onClick = {
                            answers = answers.copy(
                                shapeId = if (answers.shapeId == shape.id) null else shape.id,
                            )
                        },
                        label = {
                            Text(stringResource(R.string.routine_shape_days, shape.description, shape.days.size))
                        },
                    )
                }
                if (answers.shapeId == null) {
                    Explanation(
                        stringResource(R.string.setup_shape_skipped, answers.shapesOffered().first().description),
                    )
                }
            }

            SetupStep.CARDIO -> SetupQuestion(
                question = stringResource(R.string.routine_cardio),
                options = listOf(
                    CardioPlacement.NONE to stringResource(R.string.cardio_none),
                    CardioPlacement.OWN_DAY to stringResource(R.string.cardio_own_day),
                    CardioPlacement.AFTER_LIFTING to stringResource(R.string.cardio_after_lifting),
                ),
                selected = answers.cardio,
                onSelect = { answers = answers.copy(cardio = it) },
                skipped = stringResource(R.string.setup_cardio_skipped),
            )

            SetupStep.EQUIPMENT -> {
                Text(stringResource(R.string.routine_equipment), style = MaterialTheme.typography.titleMedium)
                // Last because it is the longest, and people abandon a long checklist before they
                // have invested anything. Cardio machines appear only for somebody who does cardio.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    answers.equipmentOffered(availableEquipment).forEach { item ->
                        val ticked = item in answers.equipment.orEmpty()
                        FilterChip(
                            selected = ticked,
                            onClick = {
                                val now = answers.equipment.orEmpty()
                                answers = answers.copy(equipment = if (ticked) now - item else now + item)
                            },
                            label = { Text(item) },
                        )
                    }
                }
                if (answers.equipment.isNullOrEmpty()) {
                    Explanation(stringResource(R.string.setup_equipment_skipped))
                }
                // The one conflict that can only be known once the gym is: low reps with nothing
                // heavy to lift.
                Conflicts(setup, experience)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (answers.step != SetupStep.entries.first()) {
                TextButton(onClick = { answers = answers.back() }) {
                    Text(stringResource(R.string.setup_back))
                }
            }
            if (!answers.isLast) {
                TextButton(onClick = { answers = answers.skip() }) {
                    Text(stringResource(R.string.setup_skip))
                }
                AppButton(
                    text = stringResource(R.string.setup_next),
                    onClick = { answers = answers.next() },
                )
            } else {
                // The payoff for answering: a routine to train from, landed on straight away.
                AppButton(
                    text = stringResource(R.string.setup_build),
                    onClick = { onCreateFromShape(answers.shapeToBuild(), setup) },
                )
            }
        }

        HorizontalDivider()
        // The same mechanism from an empty start, for somebody who knows exactly what they want.
        TextButton(onClick = onCreate) { Text(stringResource(R.string.routine_build_by_hand)) }
        // And the seasonal case: something that worked last spring, read back out of the log, built
        // against whatever has been answered so far.
        TextButton(onClick = { onRebuildFromLog(setup) }) {
            Text(stringResource(R.string.routine_rebuild_from_log))
        }
    }
}

/** One setup question: its choices, and what skipping it assumes while nothing is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Any> SetupQuestion(
    question: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    skipped: String,
) {
    Text(question, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, text) ->
            // Tapping the chosen one again takes the answer back: skipping stays possible.
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(if (value == selected) null else value) },
                label = { Text(text) },
            )
        }
    }
    if (selected == null) Explanation(skipped)
}

@Composable
private fun Explanation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The conflict table, read against what setup will build with. */
@Composable
private fun Conflicts(setup: RoutineSetup, experience: Experience) {
    conflictsFor(
        goal = setup.goal,
        preference = setup.repPreference,
        experience = experience,
        equipment = setup.equipment,
    ).forEach {
        Text(
            text = it.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Keeps the answers through a rotation or the process being reclaimed, as plain values. */
private val SetupAnswersSaver = listSaver<SetupAnswers, Any?>(
    save = {
        listOf(
            it.goal?.name,
            it.repPreference?.name,
            it.cadence,
            it.shapeId,
            it.cardio?.name,
            it.equipment?.let(::ArrayList),
            it.step.name,
        )
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        SetupAnswers(
            goal = (it[0] as String?)?.let(RoutineGoal::valueOf),
            repPreference = (it[1] as String?)?.let(RepPreference::valueOf),
            cadence = it[2] as Int?,
            shapeId = it[3] as String?,
            cardio = (it[4] as String?)?.let(CardioPlacement::valueOf),
            equipment = (it[5] as List<String>?)?.toSet(),
            step = SetupStep.valueOf(it[6] as String),
        )
    },
)

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
    /** How much training the lifter has behind them, which one conflict rule reads. */
    experience: Experience,
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
        //
        // The whole table, so they meet every one of these in one place rather than finding one
        // here and another on the routine below.
        conflictsFor(
            goal = routine.goal,
            preference = routine.repPreference,
            experience = experience,
            equipment = routine.equipment,
        ).forEach {
            Text(
                text = it.explanation,
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

/**
 * Which past routine to bring back, where the log holds more than one.
 *
 * Each is named as the sessions named it, with when it was last trained — which is how somebody
 * tells "the routine I ran last spring" from another they also called "My routine". Cancelling
 * brings nothing back and touches nothing.
 */
@Composable
private fun ChooseRoutine(
    choices: List<RecoveredRoutine>,
    /** Whether the log goes back further than was read, which is said rather than hidden. */
    limit: Int?,
    onChoose: (RecoveredRoutine) -> Unit,
    onCancel: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.routine_rebuild_which)) },
        text = {
            LazyColumn {
                items(choices) { choice ->
                    TextButton(onClick = { onChoose(choice) }) {
                        Text(
                            stringResource(
                                R.string.routine_rebuild_choice,
                                choice.name,
                                choice.lastTrained.format(formatter),
                                choice.days.size,
                            ),
                        )
                    }
                }
                limit?.let {
                    item {
                        Text(
                            text = stringResource(R.string.routine_rebuild_limit, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.routine_rebuild_cancel)) }
        },
    )
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
    onPin: (String, Boolean) -> Unit,
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
                    color = LocalGymColors.current.accentText,
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
                Row {
                    // A pin is about the shape of the routine and nothing else. The copy stays
                    // away from anything that sounds like history being at stake, because it
                    // never is: history follows the exercise whatever a routine does.
                    FilterChip(
                        selected = planned.pinned,
                        onClick = { onPin(exercise.id, !planned.pinned) },
                        label = {
                            Text(
                                stringResource(
                                    if (planned.pinned) {
                                        R.string.routine_pinned
                                    } else {
                                        R.string.routine_pin
                                    },
                                ),
                            )
                        },
                    )
                    TextButton(onClick = { onRemoveExercise(exercise.id) }) {
                        Text(stringResource(R.string.routine_remove_exercise))
                    }
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

/**
 * Asking for a different routine, and taking it back.
 *
 * **Variety is asked for and never inflicted.** Routines do not self-rotate exercises across
 * cycles, so this button is the only thing in the app that changes what a generated routine
 * holds without the lifter choosing each exercise themselves.
 *
 * Undo is one step and lives until the next session is logged, which is exactly as long as the
 * case it exists for: "I regenerated and I hate it" surfaces in seconds.
 */
@Composable
private fun Regenerate(
    canRegenerate: Boolean,
    canUndo: Boolean,
    onRegenerate: () -> Unit,
    onUndo: () -> Unit,
) {
    if (!canRegenerate && !canUndo) return

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRegenerate) {
                TextButton(onClick = onRegenerate) {
                    Text(stringResource(R.string.routine_regenerate))
                }
            }
            if (canUndo) {
                TextButton(onClick = onUndo) { Text(stringResource(R.string.routine_undo)) }
            }
        }
        Text(
            text = stringResource(R.string.routine_pin_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            AppButton(
                text = stringResource(R.string.routine_add),
                onClick = {
                    onAdd(
                        name.trim(),
                        if (isCardio) RoutineDayKind.CARDIO else RoutineDayKind.LIFTING,
                    )
                    name = ""
                    isCardio = false
                },
                enabled = name.isNotBlank(),
            )
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
