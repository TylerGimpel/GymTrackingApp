package com.tylergimpel.gymtracker.ui.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Binding
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.PlannedExercise
import com.tylergimpel.gymtracker.domain.RecoveredRoutine
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.bindingOn
import com.tylergimpel.gymtracker.domain.estimatedLengthOf
import com.tylergimpel.gymtracker.domain.weeklyVolumeByMuscle
import com.tylergimpel.gymtracker.domain.weeklyVolumeTargetFor
import com.tylergimpel.gymtracker.ui.MuscleDiagram
import com.tylergimpel.gymtracker.ui.MuscleShade
import com.tylergimpel.gymtracker.ui.icons.AppIcons
import com.tylergimpel.gymtracker.ui.icons.EquipmentIcons
import com.tylergimpel.gymtracker.ui.session.Sheet
import com.tylergimpel.gymtracker.ui.session.SheetTitle
import com.tylergimpel.gymtracker.ui.shadeForExercise
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.AppOutlinedButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import com.tylergimpel.gymtracker.ui.theme.ControlShape
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.MainButton
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.UppercaseText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Routine tab (#146): one read-only page — the name, the muscle diagram with a plain line
 * about what needs attention, every day and its lifts, setup's rows, and Build it again — and one
 * Edit button that shows every control at once.
 */
@Composable
fun RoutineScreen(
    onBack: () -> Unit,
    onAddNewExercise: (Long) -> Unit,
    onStartSetup: () -> Unit = {},
    onOpenMuscles: () -> Unit = {},
    onOpenSetting: (RoutineSetting) -> Unit = {},
    viewModel: RoutineViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.progress?.routine
    val defaultName = stringResource(R.string.routine_default_name)
    val firstDay = stringResource(R.string.routine_first_day)

    // An empty start opens straight into Edit, since there is nothing yet to read.
    var editing by rememberSaveable(routine?.id) {
        mutableStateOf(routine != null && routine.days.all { it.exercises.isEmpty() })
    }
    var sheet by remember { mutableStateOf<RoutineSheet?>(null) }
    val snackbars = remember { SnackbarHostState() }

    val builtAgain = stringResource(R.string.routine_built_again)
    val undo = stringResource(R.string.routine_undo)
    LaunchedEffect(state.builtAgain) {
        if (!state.builtAgain) return@LaunchedEffect
        viewModel.builtAgainSeen()
        val result = snackbars.showSnackbar(builtAgain, actionLabel = undo, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRegeneration()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routine_title),
                actions = {
                    if (routine != null) EditButton(editing) { editing = !editing }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { insets ->
        if (routine == null) {
            EmptyRoutine(
                insets = insets,
                onStartSetup = onStartSetup,
                onCreate = { viewModel.createRoutine(defaultName, firstDay) },
                // Built against the usual answers; the routine's settings change any of them after.
                onRebuildFromLog = { viewModel.rebuildFromLog(SetupAnswers().toRoutineSetup()) },
            )
        } else {
            RoutinePage(
                routine = routine,
                state = state,
                editing = editing,
                insets = insets,
                onOpenMuscles = onOpenMuscles,
                onOpenSetting = onOpenSetting,
                onRegenerate = viewModel::regenerate,
                onSheet = { sheet = it },
                onMove = viewModel::moveDay,
                onPin = viewModel::setPinned,
                onReplace = viewModel::startReplacing,
                onRemoveLift = viewModel::removeExerciseFromDay,
                onAdd = viewModel::startPicking,
            )
        }
        state.choosingRoutine?.let { choice ->
            ChooseRoutine(choice.routines, choice.limit, viewModel::choose, viewModel::cancelRebuildChoice)
        }
    }

    when (val open = sheet) {
        null -> Unit
        is RoutineSheet.Lift -> LiftSheet(
            day = open.day,
            planned = open.planned,
            onDismiss = { sheet = null },
            onReplace = { viewModel.startReplacing(open.day.id, open.planned.exercise) },
            onKeep = { viewModel.setPinned(open.day.id, open.planned.exercise.id, !open.planned.pinned) },
            onRemove = { viewModel.removeExerciseFromDay(open.day.id, open.planned.exercise.id) },
        )
        is RoutineSheet.Rename -> RenameSheet(
            current = open.current,
            onDismiss = { sheet = null },
            onSave = { name ->
                if (open.dayId == null) viewModel.renameRoutine(name) else viewModel.renameDay(open.dayId, name)
                sheet = null
            },
        )
        RoutineSheet.AddDay -> AddDaySheet(
            onDismiss = { sheet = null },
            onAdd = { name, kind ->
                viewModel.addDay(name, kind)
                sheet = null
            },
        )
        is RoutineSheet.RemoveDay -> Sheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.routine_remove_day_title, open.day.name))
            Text(stringResource(R.string.routine_remove_day_body), color = LocalGymColors.current.dim)
            MainButton(
                stringResource(R.string.routine_remove),
                onClick = {
                    viewModel.removeDay(open.day.id)
                    sheet = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppOutlinedButton(stringResource(R.string.routine_keep_it), onClick = { sheet = null }, modifier = Modifier.fillMaxWidth())
        }
        RoutineSheet.Delete -> Sheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.routine_delete_title))
            Text(stringResource(R.string.routine_delete_body), color = LocalGymColors.current.dim)
            MainButton(
                stringResource(R.string.routine_delete),
                onClick = {
                    viewModel.deleteRoutine()
                    editing = false
                    sheet = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppOutlinedButton(stringResource(R.string.routine_keep_it), onClick = { sheet = null }, modifier = Modifier.fillMaxWidth())
        }
    }

    // An offer, with declining as easy as accepting. The routine is untouched until they answer.
    state.offeringRebuild?.let { offer ->
        Sheet(onDismiss = viewModel::declineRebuild) {
            SheetTitle(stringResource(R.string.routine_offer_title))
            Text(
                stringResource(
                    when (offer) {
                        RebuildOffer.EQUIPMENT -> R.string.routine_offer_equipment
                        RebuildOffer.WHAT_IT_IS_FOR -> R.string.routine_offer_rebuild
                        RebuildOffer.SPLIT -> R.string.routine_offer_split
                    },
                ),
                color = LocalGymColors.current.dim,
            )
            MainButton(stringResource(R.string.routine_offer_accept), onClick = viewModel::acceptRebuild, modifier = Modifier.fillMaxWidth())
            AppOutlinedButton(stringResource(R.string.routine_offer_decline), onClick = viewModel::declineRebuild, modifier = Modifier.fillMaxWidth())
        }
    }

    if (state.nothingToRebuild) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNothingToRebuild,
            text = { Text(stringResource(R.string.routine_rebuild_none)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissNothingToRebuild) { Text(stringResource(R.string.routine_offer_decline)) }
            },
        )
    }

    state.picking?.let { picking ->
        LiftPicker(
            picking = picking,
            equipment = routine?.equipment.orEmpty(),
            inDay = routine?.rotation?.firstOrNull { it.id == picking.dayId }?.exercises
                ?.map { it.exercise.id }?.toSet().orEmpty(),
            addingDisliked = state.addingDisliked,
            onPick = viewModel::pick,
            // Leaving for the bank's own form closes this, so coming back does not reopen a list
            // taken before the exercise they went to write down existed.
            onAddNew = {
                viewModel.cancelPicking()
                onAddNewExercise(picking.dayId)
            },
            onCancel = viewModel::cancelPicking,
        )
    }
}

/** What a sheet over the routine is showing. */
private sealed interface RoutineSheet {
    data class Lift(val day: RoutineDay, val planned: PlannedExercise) : RoutineSheet
    data class Rename(val dayId: Long?, val current: String) : RoutineSheet
    data object AddDay : RoutineSheet
    data class RemoveDay(val day: RoutineDay) : RoutineSheet
    data object Delete : RoutineSheet
}

@Composable
private fun EditButton(editing: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (editing) colors.accent else colors.bg,
        contentColor = if (editing) colors.onAccent else colors.text,
        modifier = Modifier.height(44.dp).padding(end = 4.dp).testTag(ROUTINE_EDIT),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            UppercaseText(
                stringResource(if (editing) R.string.setup_done else R.string.routine_edit),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            )
        }
    }
}

@Composable
private fun RoutinePage(
    routine: Routine,
    state: RoutineUiState,
    editing: Boolean,
    insets: PaddingValues,
    onOpenMuscles: () -> Unit,
    onOpenSetting: (RoutineSetting) -> Unit,
    onRegenerate: () -> Unit,
    onSheet: (RoutineSheet) -> Unit,
    onMove: (Long, Int) -> Unit,
    onPin: (Long, String, Boolean) -> Unit,
    onReplace: (Long, Exercise) -> Unit,
    onRemoveLift: (Long, String) -> Unit,
    onAdd: (Long) -> Unit,
) {
    val colors = LocalGymColors.current
    val findings = remember(routine, state.bank) { bindingOn(routine, bank = state.bank) }
    val rotation = routine.rotation
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ROUTINE_PAGE),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.let { if (editing) it.clickable { onSheet(RoutineSheet.Rename(null, routine.name)) } else it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UppercaseText(routine.name, style = MaterialTheme.typography.headlineLarge.copy(color = colors.text))
                    if (editing) {
                        Icon(AppIcons.Edit, stringResource(R.string.routine_rename), tint = colors.dim, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                    }
                }
                Text(
                    stringResource(R.string.routine_meta, pluralStringResource(R.plurals.routine_days, rotation.size, rotation.size), routine.cadence),
                    fontSize = 15.sp,
                    color = colors.dim,
                )
            }
        }
        state.revised?.let { revised ->
            item {
                Text(
                    stringResource(
                        when (revised) {
                            Revised.ADDED -> R.string.routine_revised_added
                            Revised.REMOVED -> R.string.routine_revised_removed
                            Revised.MOVED -> R.string.routine_revised_moved
                        },
                    ),
                    fontSize = 15.sp,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(colors.raised, ControlShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        if (!editing) {
            item {
                Column(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenMuscles).padding(bottom = 8.dp).testTag(ROUTINE_MUSCLES),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MuscleDiagram(shade = routineShade(routine), maxHeight = 150.dp)
                    val line = findingLine(findings)
                    Text(
                        (line ?: stringResource(R.string.routine_muscles)) + " ›",
                        fontSize = 15.sp,
                        color = if (line != null) colors.warn else colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        rotation.forEachIndexed { index, day ->
            item(key = "day-${day.id}") {
                DayBlock(
                    routine = routine,
                    day = day,
                    isNext = state.progress?.cursor?.id == day.id,
                    first = index == 0,
                    last = index == rotation.lastIndex,
                    editing = editing,
                    onSheet = onSheet,
                    onMove = onMove,
                    onPin = onPin,
                    onReplace = onReplace,
                    onRemoveLift = onRemoveLift,
                    onAdd = onAdd,
                )
            }
        }
        if (editing) {
            item {
                DashedButton(stringResource(R.string.routine_add_day), Modifier.padding(top = 12.dp)) { onSheet(RoutineSheet.AddDay) }
            }
        }
        item {
            SectionLabel(stringResource(R.string.routine_setup), Modifier.padding(top = 24.dp, bottom = 10.dp))
            SettingRows(routine, onOpenSetting)
        }
        if (routine.shape != null && !editing) {
            item {
                AppOutlinedButton(
                    stringResource(R.string.routine_offer_accept),
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(ROUTINE_BUILD_AGAIN),
                )
            }
        }
        if (editing) {
            item {
                TextButton(onClick = { onSheet(RoutineSheet.Delete) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    UppercaseText(stringResource(R.string.routine_delete), style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, color = colors.warn))
                }
            }
        }
    }
}

/** A muscle lit fully once its weekly sets reach the goal's band, partly while any reach it. */
internal fun routineShade(routine: Routine): (String) -> MuscleShade {
    val weekly = routine.weeklyVolumeByMuscle()
    val floor = weeklyVolumeTargetFor(routine.goal).start
    return { muscle ->
        val sets = weekly[muscle] ?: 0.0
        when {
            sets >= floor -> MuscleShade.FULL
            sets > 0 -> MuscleShade.PARTIAL
            else -> MuscleShade.NONE
        }
    }
}

/** What needs attention, in plain words: `Legs runs long · No lift for traps and glutes`. */
@Composable
private fun findingLine(findings: List<Binding>): String? {
    val long = findings.filterIsInstance<Binding.SessionTooLong>().map { it.day }
    val none = findings.filterIsInstance<Binding.MuscleNotTrained>().map { it.muscle }
    val parts = buildList {
        if (long.isNotEmpty()) add(pluralStringResource(R.plurals.routine_runs_long, long.size, andList(long)))
        if (none.isNotEmpty()) add(stringResource(R.string.routine_no_lift_for, andList(none)))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
internal fun andList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> stringResource(R.string.and_list, items.dropLast(1).joinToString(", "), items.last())
}

@Composable
private fun DayBlock(
    routine: Routine,
    day: RoutineDay,
    isNext: Boolean,
    first: Boolean,
    last: Boolean,
    editing: Boolean,
    onSheet: (RoutineSheet) -> Unit,
    onMove: (Long, Int) -> Unit,
    onPin: (Long, String, Boolean) -> Unit,
    onReplace: (Long, Exercise) -> Unit,
    onRemoveLift: (Long, String) -> Unit,
    onAdd: (Long) -> Unit,
) {
    val colors = LocalGymColors.current
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.line)
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editing) {
                    Row(
                        Modifier.weight(1f).clickable { onSheet(RoutineSheet.Rename(day.id, day.name)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UppercaseText(day.name, style = MaterialTheme.typography.headlineMedium.copy(color = colors.text))
                        Icon(AppIcons.Edit, stringResource(R.string.routine_rename), tint = colors.dim, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                    }
                    SmallIconButton(AppIcons.Up, stringResource(R.string.routine_move_earlier), enabled = !first) { onMove(day.id, -1) }
                    SmallIconButton(AppIcons.Down, stringResource(R.string.routine_move_later), enabled = !last) { onMove(day.id, 1) }
                    SmallIconButton(AppIcons.Trash, stringResource(R.string.routine_remove_day)) { onSheet(RoutineSheet.RemoveDay(day)) }
                } else {
                    UppercaseText(day.name, style = MaterialTheme.typography.headlineMedium.copy(color = colors.text), modifier = Modifier.weight(1f, fill = false))
                    if (isNext) Tag(stringResource(R.string.routine_next_up))
                }
            }
            DayMeta(routine, day)
            if (day.kind == RoutineDayKind.CARDIO) {
                Text(stringResource(R.string.routine_cardio_day), color = colors.dim)
            } else {
                day.exercises.forEach { planned ->
                    LiftRow(
                        planned = planned,
                        editing = editing,
                        onOpen = { onSheet(RoutineSheet.Lift(day, planned)) },
                        onKeep = { onPin(day.id, planned.exercise.id, !planned.pinned) },
                        onReplace = { onReplace(day.id, planned.exercise) },
                        onRemove = { onRemoveLift(day.id, planned.exercise.id) },
                    )
                }
                if (editing) {
                    DashedButton(stringResource(R.string.routine_add_exercise)) { onAdd(day.id) }
                } else if (day.exercises.isEmpty()) {
                    Text(stringResource(R.string.routine_no_lifts), color = colors.dim)
                }
            }
        }
    }
}

/** `5 lifts · 18 sets · ~52 min`, the minutes in the warning colour past the ceiling's margin. */
@Composable
private fun DayMeta(routine: Routine, day: RoutineDay) {
    val colors = LocalGymColors.current
    if (day.kind == RoutineDayKind.CARDIO) return
    val minutes = routine.estimatedLengthOf(day).toMinutes()
    val long = minutes > routine.sessionMinutes * 1.1
    val sets = day.exercises.sumOf { it.sets ?: 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(pluralStringResource(R.plurals.routine_lifts, day.exercises.size, day.exercises.size), fontSize = 15.sp, color = colors.dim)
        Text(pluralStringResource(R.plurals.routine_sets, sets, sets), fontSize = 15.sp, color = colors.dim)
        if (day.exercises.isNotEmpty()) {
            Text(stringResource(R.string.today_minutes, minutes), fontSize = 15.sp, color = if (long) colors.warn else colors.dim)
        }
    }
}

/**
 * One lift: its equipment, its plain name, a pin if it is kept, and how many sets. In Edit, Keep,
 * Replace and Remove beside it instead.
 */
@Composable
private fun LiftRow(
    planned: PlannedExercise,
    editing: Boolean,
    onOpen: () -> Unit,
    onKeep: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalGymColors.current
    val exercise = planned.exercise
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (editing) 60.dp else 48.dp)
            .let { if (!editing) it.clickable(onClick = onOpen) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.dim, modifier = Modifier.size(24.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.text, modifier = Modifier.weight(1f, fill = false))
            if (planned.pinned) {
                Icon(AppIcons.Pin, stringResource(R.string.routine_kept), tint = colors.accentText, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
        }
        if (editing) {
            SmallIconButton(AppIcons.Pin, stringResource(R.string.routine_keep), on = planned.pinned, onClick = onKeep)
            SmallIconButton(AppIcons.Swap, stringResource(R.string.routine_replace), onClick = onReplace)
            SmallIconButton(AppIcons.Close, stringResource(R.string.routine_remove), onClick = onRemove)
        } else {
            planned.sets?.let {
                Text("$it×", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.dim)
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, enabled: Boolean = true, on: Boolean = false, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = if (on) colors.accent else colors.raised,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                description,
                modifier = Modifier.size(22.dp),
                tint = when {
                    on -> colors.onAccent
                    enabled -> colors.dim
                    else -> colors.line
                },
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    val colors = LocalGymColors.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = colors.onAccent,
        modifier = Modifier.background(colors.accent, ControlShape).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
internal fun DashedButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = colors.bg,
        contentColor = colors.dim,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            UppercaseText(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        }
    }
}

/** The routine's settings, as setup's summary rows: each opens its question's page. */
enum class RoutineSetting { GOAL, REPS, DAYS, SPLIT, CARDIO, EQUIPMENT, SESSION_LENGTH }

@Composable
private fun SettingRows(routine: Routine, onOpen: (RoutineSetting) -> Unit) {
    val answers = routine.asSetupAnswers()
    AppCard(Modifier.fillMaxWidth()) {
        SettingRow(R.string.setup_short_goal, stringResource(routine.goal.setupLabel)) { onOpen(RoutineSetting.GOAL) }
        SettingRow(R.string.setup_short_reps, stringResource(routine.repPreference.setupLabel)) { onOpen(RoutineSetting.REPS) }
        SettingRow(R.string.setup_short_days, stringResource(R.string.setup_days_value, routine.cadence)) { onOpen(RoutineSetting.DAYS) }
        SettingRow(R.string.setup_short_split, routine.shape?.let { shapeName(it) } ?: stringResource(R.string.routine_by_hand)) {
            onOpen(RoutineSetting.SPLIT)
        }
        SettingRow(R.string.setup_short_cardio, stringResource(routine.cardio.setupLabel)) { onOpen(RoutineSetting.CARDIO) }
        SettingRow(R.string.setup_short_equipment, equipmentSummary(answers)) { onOpen(RoutineSetting.EQUIPMENT) }
        SettingRow(R.string.routine_session_length, stringResource(R.string.workout_minutes_short, routine.sessionMinutes), last = true) {
            onOpen(RoutineSetting.SESSION_LENGTH)
        }
    }
}

@Composable
private fun SettingRow(label: Int, value: String, last: Boolean = false, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel(stringResource(label), Modifier.padding(bottom = 6.dp))
            UppercaseText(value, style = MaterialTheme.typography.headlineSmall.copy(color = colors.text))
        }
        Icon(AppIcons.Chevron, null, tint = colors.dim)
    }
    if (!last) HorizontalDivider(color = colors.line)
}

/** A routine's settings read as setup's answers, so its pages can show them. */
internal fun Routine.asSetupAnswers(): SetupAnswers = SetupAnswers(
    goal = goal,
    repPreference = repPreference,
    cadence = cadence,
    shapeId = shape?.id?.removeSuffix("-cardio"),
    cardio = cardio,
    // Nothing narrowed is a full gym, as setup builds it.
    equipment = if (equipment.isEmpty()) {
        (SETUP_LIFTING_EQUIPMENT + SETUP_CARDIO_EQUIPMENT).toSet()
    } else {
        equipment - SetupAnswers.BODYWEIGHT
    },
)

/** The lift's own sheet: its equipment, sets and muscles, and Replace, Keep and Remove. */
@Composable
private fun LiftSheet(
    day: RoutineDay,
    planned: PlannedExercise,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalGymColors.current
    val exercise = planned.exercise
    fun then(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    Sheet(onDismiss = onDismiss) {
        SheetTitle(exercise.name)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.dim, modifier = Modifier.size(22.dp))
            Text(stringResource(EquipmentIcons.pluralFor(exercise.equipment)), color = colors.dim, fontSize = 15.sp)
            planned.sets?.let { Text(pluralStringResource(R.plurals.routine_sets, it, it), color = colors.dim, fontSize = 15.sp) }
        }
        MuscleDiagram(shade = shadeForExercise(exercise.primaryMuscles, exercise.secondaryMuscles), maxHeight = 150.dp)
        Text(
            exercise.primaryMuscles.joinToString(", ") +
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    " · " + stringResource(R.string.routine_also, exercise.secondaryMuscles.joinToString(", "))
                } else {
                    ""
                },
            fontSize = 15.sp,
            color = colors.dim,
        )
        SheetAction(AppIcons.Swap, stringResource(R.string.routine_replace), onClick = then(onReplace))
        SheetAction(
            AppIcons.Pin,
            stringResource(if (planned.pinned) R.string.routine_kept else R.string.routine_keep),
            sub = stringResource(R.string.routine_keep_sub),
            on = planned.pinned,
            onClick = then(onKeep),
        )
        SheetAction(AppIcons.Close, stringResource(R.string.routine_remove), danger = true, onClick = then(onRemove))
        Text(day.name, fontSize = 13.sp, color = colors.dim)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, text: String, sub: String? = null, on: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (on) colors.accent else colors.raised,
        contentColor = when {
            on -> colors.onAccent
            danger -> colors.warn
            else -> colors.text
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null)
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            sub?.let { Text(it, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    Sheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.routine_rename))
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MainButton(stringResource(R.string.session_save), onClick = { if (name.isNotBlank()) onSave(name.trim()) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddDaySheet(onDismiss: () -> Unit, onAdd: (String, RoutineDayKind) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(RoutineDayKind.LIFTING) }
    Sheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.routine_add_day_title))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.routine_add_day_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(selected = kind == RoutineDayKind.LIFTING, onClick = { kind = RoutineDayKind.LIFTING }, modifier = Modifier.weight(1f).heightIn(min = 90.dp)) {
                Icon(EquipmentIcons.iconFor("barbell"), null, modifier = Modifier.size(36.dp))
                UppercaseText(stringResource(R.string.routine_lifting), style = MaterialTheme.typography.titleLarge)
            }
            Tile(selected = kind == RoutineDayKind.CARDIO, onClick = { kind = RoutineDayKind.CARDIO }, modifier = Modifier.weight(1f).heightIn(min = 90.dp)) {
                Icon(EquipmentIcons.iconFor("treadmill"), null, modifier = Modifier.size(36.dp))
                UppercaseText(stringResource(R.string.setup_cardio_group), style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(stringResource(R.string.routine_cycle_restarts), fontSize = 15.sp, color = LocalGymColors.current.dim)
        MainButton(
            stringResource(R.string.routine_add_day_button),
            onClick = { if (name.isNotBlank()) onAdd(name.trim(), kind) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * No routine yet: setup, building one by hand, or bringing back what the log shows was trained
 * before (#144). Setup itself is its own full-screen flow.
 */
@Composable
private fun EmptyRoutine(
    insets: PaddingValues,
    onStartSetup: () -> Unit,
    onCreate: () -> Unit,
    onRebuildFromLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UppercaseText(stringResource(R.string.today_no_routine), style = MaterialTheme.typography.headlineMedium)
                MainButton(stringResource(R.string.welcome_get_started), onClick = onStartSetup, modifier = Modifier.fillMaxWidth())
                AppOutlinedButton(stringResource(R.string.welcome_build_myself), onClick = onCreate, modifier = Modifier.fillMaxWidth())
                AppOutlinedButton(stringResource(R.string.routine_rebuild_from_log), onClick = onRebuildFromLog, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

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

/** Handles for the tests. */
const val ROUTINE_EDIT = "routine-edit"
const val ROUTINE_PAGE = "routine-page"
const val ROUTINE_MUSCLES = "routine-muscles"
const val ROUTINE_BUILD_AGAIN = "routine-build-again"
