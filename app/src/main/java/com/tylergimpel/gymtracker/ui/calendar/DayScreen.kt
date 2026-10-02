package com.tylergimpel.gymtracker.ui.calendar

import com.tylergimpel.gymtracker.ui.theme.UppercaseText
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.CardRow
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.session.summary
import com.tylergimpel.gymtracker.ui.session.label
import com.tylergimpel.gymtracker.ui.routine.DashedButton
import com.tylergimpel.gymtracker.ui.label
import com.tylergimpel.gymtracker.ui.icons.EquipmentIcons
import com.tylergimpel.gymtracker.ui.MuscleShade
import com.tylergimpel.gymtracker.ui.MuscleGroup
import com.tylergimpel.gymtracker.ui.MuscleDiagram
import com.tylergimpel.gymtracker.ui.MuscleChips
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.domain.SetType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.ui.session.EditSetDialog
import com.tylergimpel.gymtracker.ui.session.SetDraftFields
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.Exercise
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DayScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onOpenHistory: (Exercise) -> Unit = {},
    viewModel: DayViewModel = viewModel(),
) {
    LaunchedEffect(date) { viewModel.open(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DayScreen(
        state = state,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        correcting = Corrections(
            onEdit = viewModel::startEditing,
            onEditingChange = viewModel::changeEditingDraft,
            onSaveEditing = viewModel::saveEditing,
            onDeleteEditing = viewModel::deleteEditing,
            onCancelEditing = viewModel::cancelEditing,
            onAdd = viewModel::startAdding,
            onAddingChange = viewModel::changeAddingDraft,
            onSaveAdding = viewModel::saveAdding,
            onCancelAdding = viewModel::cancelAdding,
        ),
    )
}

/** What correcting a past session can do (#120). */
data class Corrections(
    val onEdit: (SessionExercise, PerformedSet, Effort) -> Unit = { _, _, _ -> },
    val onEditingChange: (SetDraft) -> Unit = {},
    val onSaveEditing: () -> Unit = {},
    val onDeleteEditing: () -> Unit = {},
    val onCancelEditing: () -> Unit = {},
    val onAdd: (SessionExercise) -> Unit = {},
    val onAddingChange: (SetDraft) -> Unit = {},
    val onSaveAdding: () -> Unit = {},
    val onCancelAdding: () -> Unit = {},
)

/**
 * What was done on one day (#118, #147): the weigh-in first, then each session as a card — where it
 * came from, its name, its muscles as chips and a small diagram — then each lift's numbered sets.
 * Any set can be corrected, removed or added to after the fact (#120).
 *
 * **In the shape it was logged in** (#119): a **Superset** as a group, drops beneath their set,
 * both sides of a per-side set together.
 */
@Composable
fun DayScreen(
    state: DayUiState,
    onBack: () -> Unit = {},
    onOpenHistory: (Exercise) -> Unit = {},
    correcting: Corrections = Corrections(),
) {
    Scaffold(
        topBar = {
            AppTopBar(title = state.date?.format(DateTimeFormatter.ofPattern("EEE d MMM")).orEmpty())
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.weight?.let { weight ->
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        CardRow(
                            title = stringResource(R.string.day_weigh_in, weight.asTextIn(state.lifterUnit), stringResource(state.lifterUnit.label)),
                        )
                    }
                }
            }
            items(state.sessions, key = { it.session.id }) { logged ->
                PastSession(logged, state, onOpenHistory, correcting)
            }
        }
    }

    // A mistyped number stays fixable after the fact (#120): the same correction as mid-session.
    state.editing?.let {
        EditSetDialog(
            editing = it,
            onChange = correcting.onEditingChange,
            onSave = correcting.onSaveEditing,
            onDelete = correcting.onDeleteEditing,
            onCancel = correcting.onCancelEditing,
        )
    }
    state.adding?.let { adding ->
        AlertDialog(
            onDismissRequest = correcting.onCancelAdding,
            title = { Text(stringResource(R.string.day_add_set_title)) },
            text = { SetDraftFields(adding.draft, correcting.onAddingChange, showNote = false) },
            confirmButton = {
                TextButton(onClick = correcting.onSaveAdding, enabled = adding.draft.isLoggable) {
                    Text(stringResource(R.string.session_save))
                }
            },
            dismissButton = {
                TextButton(onClick = correcting.onCancelAdding) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PastSession(
    logged: LoggedSession,
    state: DayUiState,
    onOpenHistory: (Exercise) -> Unit,
    correcting: Corrections,
) {
    val colors = LocalGymColors.current
    val session = logged.session
    // Lit by what this session did, warm-ups left out: its main muscles fully, the rest half.
    val muscleSets = logged.exercises.flatMap { performed ->
        val counted = performed.sets.count { it.type.countsTowardVolume }.toDouble()
        performed.exercise.primaryMuscles.map { it to counted } +
            performed.exercise.secondaryMuscles.map { it to counted / 2 }
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    val groups = muscleSets.entries.mapNotNull { (muscle, sets) -> MuscleGroup.of(muscle)?.let { it to sets } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        .filterValues { it >= 3 }.entries.sortedByDescending { it.value }.map { it.key }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Where the data came from, by name, so an imported session is never mistaken for one
            // logged here (#119).
            val source = when {
                session.isImported -> state.importedFrom[session.importBatchId]?.let { stringResource(R.string.day_imported_from, it.name) }
                    ?: stringResource(R.string.day_imported)
                session.routineDayName == null -> stringResource(R.string.day_no_routine)
                else -> null
            }
            source?.let { SectionLabel(it) }
            val named = groups.take(2).map { stringResource(it.label) }
            UppercaseText(
                session.routineDayName ?: named.takeIf { it.isNotEmpty() }?.joinToString(" + ") ?: stringResource(R.string.day_session),
                style = MaterialTheme.typography.headlineMedium.copy(color = colors.text),
            )
            if (groups.isNotEmpty()) MuscleChips(groups)
            MuscleDiagram(
                shade = { muscle ->
                    val sets = muscleSets[muscle] ?: 0.0
                    when {
                        sets >= 3 -> MuscleShade.FULL
                        sets > 0 -> MuscleShade.PARTIAL
                        else -> MuscleShade.NONE
                    }
                },
                maxHeight = 120.dp,
            )
            session.note?.let { Text(it, fontSize = 15.sp, color = colors.dim) }
            logged.positions.forEach { position ->
                val performed = position.exercises.filter { it.sets.isNotEmpty() }
                if (performed.isEmpty()) return@forEach
                when (position) {
                    is SessionPosition.Alone -> performed.forEach { PerformedExercise(it, state.lifterUnit, onOpenHistory, correcting) }
                    // Together, as they were trained: a superset is neighbouring exercises done in turn.
                    is SessionPosition.Grouped -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
                        Column(Modifier.padding(start = 10.dp)) {
                            SectionLabel(stringResource(R.string.day_superset))
                            performed.forEach { PerformedExercise(it, state.lifterUnit, onOpenHistory, correcting) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformedExercise(
    performed: SessionExercise,
    lifterUnit: WeightUnit,
    onOpenHistory: (Exercise) -> Unit,
    correcting: Corrections,
) {
    val colors = LocalGymColors.current
    val exercise = performed.exercise
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The whole history of the exercise is a tap away from any one day of it (#121).
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onOpenHistory(exercise) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.dim, modifier = Modifier.size(22.dp))
            Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
        }
        performed.sets.forEachIndexed { index, set ->
            SetLine(index + 1, set, performed.displayUnitFor(set), lifterUnit) { effort -> correcting.onEdit(performed, set, effort) }
        }
        DashedButton(stringResource(R.string.day_add_set)) { correcting.onAdd(performed) }
    }
}

/**
 * One set, numbered: `112.5 kg × 10`. Warm-ups dimmed and marked, both sides of a per-side set
 * together, drops beneath with an arrow. Tapping corrects it.
 */
@Composable
private fun SetLine(number: Int, set: PerformedSet, displayUnit: WeightUnit, lifterUnit: WeightUnit, onEdit: (Effort) -> Unit) {
    val colors = LocalGymColors.current
    val warm = !set.type.countsTowardVolume
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onEdit(set.leadingEffort) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(number.toString(), color = colors.dim, fontSize = 15.sp, modifier = Modifier.width(20.dp))
        val efforts = set.efforts.map { it.summary(displayUnit, lifterUnit) }
        Text(efforts.joinToString(" / "), color = if (warm) colors.dim else colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (set.efforts.size > 1) {
            val sides = set.efforts.mapNotNull { it.side }.map { stringResource(it.label).take(1).uppercase() }
            Text(sides.joinToString(" / "), color = colors.dim, fontSize = 14.sp)
        }
        if (set.type != SetType.WORKING) {
            Text(stringResource(set.type.label), color = colors.dim, fontSize = 14.sp)
        }
    }
    set.efforts.flatMap { it.drops }.forEach { drop ->
        Text(
            "↳ " + stringResource(R.string.day_drop, drop.weight.asTextIn(displayUnit), stringResource(displayUnit.label), drop.reps),
            color = colors.dim,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 32.dp),
        )
    }
}
