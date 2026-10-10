package com.tylergimpel.gymtracker.ui.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Binding
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.bindingOn
import com.tylergimpel.gymtracker.domain.offeredFor
import com.tylergimpel.gymtracker.domain.rankedFor
import com.tylergimpel.gymtracker.domain.commonFirst
import com.tylergimpel.gymtracker.domain.weeklyVolumeTargetFor
import com.tylergimpel.gymtracker.domain.whatItTrains
import com.tylergimpel.gymtracker.domain.MAIN_MUSCLES
import com.tylergimpel.gymtracker.ui.MuscleDiagram
import com.tylergimpel.gymtracker.ui.icons.AppIcons
import com.tylergimpel.gymtracker.ui.icons.EquipmentIcons
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import com.tylergimpel.gymtracker.ui.theme.ControlShape
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.MainButton
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.UppercaseText

/**
 * Replace and Add share this one picker (#136): search, My gym or Everything, and rows of the
 * equipment, the plain name and the main muscle. Anything not at the gym is dimmed. Replacing
 * puts the same muscle first. What the day already holds is not offered.
 */
@Composable
internal fun LiftPicker(
    picking: PickingFor,
    equipment: Set<String>,
    inDay: Set<String>,
    addingDisliked: Exercise?,
    onPick: (Exercise) -> Unit,
    onAddNew: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalGymColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var myGym by rememberSaveable { mutableStateOf(true) }
    val replacing = picking.replacing
    val offered = remember(picking, query, myGym, inDay) {
        picking.bank
            .offeredFor(equipment = equipment, query = query, muscle = null, ownEquipmentOnly = myGym)
            .filter { it.id !in inDay }
            // The lifts programs use first, by the generator's own ranking (ADR 0013), and the
            // rest after in the bank's order, so nothing becomes harder to find by name.
            .commonFirst(picking.experience)
    }
    val atGym: (Exercise) -> Boolean = { equipment.isEmpty() || it.equipment == null || it.equipment in equipment }
    // Ranked the way a slot would fill it, with nothing that could never be generated suggested;
    // those are still in the list below and found by typing.
    val sameMuscle = remember(offered, replacing) {
        val muscle = replacing?.primaryMuscles?.firstOrNull()
        if (muscle == null) emptyList() else offered.rankedFor(muscle, picking.experience)
    }
    val others = offered - sameMuscle.toSet()

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Back, stringResource(R.string.setup_back), tint = colors.text)
                }
                UppercaseText(
                    if (replacing != null) {
                        stringResource(R.string.routine_replace_title, replacing.name)
                    } else {
                        stringResource(R.string.routine_add_to, picking.dayName)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, color = colors.text),
                    maxLines = 1,
                )
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    leadingIcon = { Icon(AppIcons.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(PICKER_SEARCH),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterPill(stringResource(R.string.picker_my_gym), myGym) { myGym = true }
                    FilterPill(stringResource(R.string.picker_everything), !myGym) { myGym = false }
                }
                addingDisliked?.let {
                    Text(stringResource(R.string.picker_disliked, it.name), color = colors.warn, fontSize = 15.sp)
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sameMuscle.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.picker_same_muscle), Modifier.padding(top = 10.dp)) }
                    items(sameMuscle, key = { "same-${it.id}" }) { PickerRow(it, atGym(it)) { onPick(it) } }
                    item { SectionLabel(stringResource(R.string.picker_others), Modifier.padding(top = 10.dp)) }
                }
                items(others, key = { it.id }) { PickerRow(it, atGym(it)) { onPick(it) } }
                if (offered.isEmpty()) item { Text(stringResource(R.string.routine_picker_nothing), color = colors.dim) }
                item {
                    TextButton(onClick = onAddNew, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        UppercaseText(stringResource(R.string.routine_picker_add_new), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent else colors.raised,
        contentColor = if (selected) colors.onAccent else colors.text,
        modifier = Modifier.height(40.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PickerRow(exercise: Exercise, atGym: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    AppCard(Modifier.fillMaxWidth().alpha(if (atGym) 1f else 0.5f), onClick = onClick) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(colors.raised, ControlShape), contentAlignment = Alignment.Center) {
                Icon(EquipmentIcons.iconFor(exercise.equipment), null, tint = colors.dim, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.text)
                Text(
                    exercise.primaryMuscles.joinToString(", ") + if (atGym) "" else " · " + stringResource(R.string.picker_not_at_gym),
                    fontSize = 14.sp,
                    color = colors.dim,
                )
            }
        }
    }
}

/**
 * The Muscles page (#136): the diagram, then what needs attention under plain headings — *Runs
 * long*, *No lift of their own* — then every main muscle's sets a week against the goal's band.
 */
@Composable
fun RoutineMusclesScreen(onBack: () -> Unit, viewModel: RoutineViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.progress?.routine
    val colors = LocalGymColors.current
    Scaffold(topBar = { AppTopBar(stringResource(R.string.routine_muscles)) }) { insets ->
        if (routine == null) return@Scaffold
        val findings = remember(routine, state.bank) { bindingOn(routine, bank = state.bank) }
        val target = weeklyVolumeTargetFor(routine.goal)
        val loads = remember(routine) { routine.whatItTrains().associateBy { it.muscle } }
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MuscleDiagram(shade = routineShade(routine))
            val long = findings.filterIsInstance<Binding.SessionTooLong>()
            if (long.isNotEmpty()) {
                SectionLabel(stringResource(R.string.routine_runs_long_heading), Modifier.padding(top = 10.dp))
                long.forEach {
                    Finding(
                        stringResource(R.string.routine_runs_long_line, it.day, it.estimated.toMinutes(), it.ceiling.toMinutes()),
                        stringResource(R.string.routine_runs_long_fix),
                    )
                }
            }
            val none = findings.filterIsInstance<Binding.MuscleNotTrained>()
            if (none.isNotEmpty()) {
                SectionLabel(stringResource(R.string.routine_no_lift_heading), Modifier.padding(top = 10.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        none.forEach {
                            Text(
                                it.muscle.replaceFirstChar(Char::uppercase) + " · " + if (it.weeklySets > 0) {
                                    stringResource(R.string.routine_from_other_lifts, it.weeklySets.asSetsText())
                                } else {
                                    stringResource(R.string.routine_nothing_yet)
                                },
                                color = colors.text,
                            )
                        }
                        Text(stringResource(R.string.routine_no_lift_fix), fontSize = 15.sp, color = colors.dim)
                    }
                }
            }
            SectionLabel(
                stringResource(R.string.routine_sets_a_week, target.start.asSetsText(), target.endInclusive.asSetsText()),
                Modifier.padding(top = 10.dp),
            )
            val max = target.endInclusive * 1.5
            MAIN_MUSCLES.forEach { muscle ->
                val load = loads[muscle]
                val weekly = load?.weeklySets ?: 0.0
                HorizontalDivider(color = colors.line)
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UppercaseText(muscle, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, color = colors.text), modifier = Modifier.weight(1f))
                        Text(
                            weekly.asSetsText() + if ((load?.sessions ?: 0) > 0) " · " + stringResource(R.string.routine_times_a_cycle, load!!.sessions) else "",
                            fontSize = 15.sp,
                            color = colors.dim,
                        )
                    }
                    BandBar(weekly, target, max)
                }
            }
        }
    }
}

@Composable
private fun Finding(line: String, fix: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(line, color = LocalGymColors.current.text)
            Text(fix, fontSize = 15.sp, color = LocalGymColors.current.dim)
        }
    }
}

/** A bar against the band: the band drawn in, the week's sets filled over it. */
@Composable
private fun BandBar(weekly: Double, target: ClosedFloatingPointRange<Double>, max: Double) {
    val colors = LocalGymColors.current
    Box(Modifier.fillMaxWidth().height(10.dp).background(colors.raised, RoundedCornerShape(5.dp))) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight((target.start / max).toFloat().coerceAtLeast(0.001f)))
            Box(Modifier.weight(((target.endInclusive - target.start) / max).toFloat()).height(10.dp).background(colors.line))
            Spacer(Modifier.weight((1 - target.endInclusive / max).toFloat().coerceAtLeast(0.001f)))
        }
        Box(
            Modifier.padding(vertical = 3.dp).fillMaxWidth((weekly / max).toFloat().coerceIn(0f, 1f)).height(4.dp)
                .background(colors.accent, RoundedCornerShape(2.dp)),
        )
    }
}

/** A fractional set count as a lifter would write it: `7.5`, and `8` rather than `8.0`. */
internal fun Double.asSetsText(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

/**
 * One of the routine's settings, on setup's own tile page (#146), with Done returning. Changing
 * what the routine is for, the gym or the split asks whether to build it again.
 */
@Composable
fun RoutineSettingScreen(setting: RoutineSetting, onDone: () -> Unit, viewModel: RoutineViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.progress?.routine ?: return
    val colors = LocalGymColors.current
    var answers by rememberSaveable(stateSaver = SetupAnswersSaver) {
        mutableStateOf(routine.asSetupAnswers().copy(step = setting.step ?: SetupStep.GOAL))
    }
    var minutes by rememberSaveable { mutableStateOf(routine.sessionMinutes) }

    fun done() {
        apply(setting, routine, answers, minutes, viewModel)
        onDone()
    }
    BackHandler { done() }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable { done() }, contentAlignment = Alignment.Center) {
                Icon(AppIcons.Back, stringResource(R.string.setup_back), tint = colors.text)
            }
            UppercaseText(
                stringResource(setting.step?.shortName ?: R.string.routine_session_length),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, color = colors.text),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (setting == RoutineSetting.SESSION_LENGTH) {
                UppercaseText(stringResource(R.string.routine_q_session), style = MaterialTheme.typography.displaySmall.copy(color = colors.text))
                SESSION_LENGTHS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { length ->
                            Tile(
                                selected = length == minutes,
                                usual = length == Routine.USUAL_SESSION_MINUTES,
                                onClick = { minutes = length },
                                modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                            ) {
                                Text(length.toString(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp))
                                Text(stringResource(R.string.routine_min), fontSize = 14.sp, color = colors.dim)
                            }
                        }
                    }
                }
            } else {
                SetupQuestion(answers, onChange = { answers = it })
            }
        }
        HorizontalDivider(color = colors.line)
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            MainButton(stringResource(R.string.setup_done), onClick = { done() }, modifier = Modifier.fillMaxWidth().testTag(SETTING_DONE))
        }
    }
}

/** Which setup page a setting opens, or null for session length, which setup never asks. */
private val RoutineSetting.step: SetupStep?
    get() = when (this) {
        RoutineSetting.GOAL -> SetupStep.GOAL
        RoutineSetting.REPS -> SetupStep.REP_PREFERENCE
        RoutineSetting.DAYS -> SetupStep.CADENCE
        RoutineSetting.SPLIT -> SetupStep.SHAPE
        RoutineSetting.CARDIO -> SetupStep.CARDIO
        RoutineSetting.EQUIPMENT -> SetupStep.EQUIPMENT
        RoutineSetting.SESSION_LENGTH -> null
    }

private fun apply(setting: RoutineSetting, routine: Routine, answers: SetupAnswers, minutes: Int, viewModel: RoutineViewModel) {
    when (setting) {
        RoutineSetting.SESSION_LENGTH -> if (minutes != routine.sessionMinutes) viewModel.setSessionMinutes(minutes)
        RoutineSetting.SPLIT -> viewModel.offerSplit(answers.shape)
        else -> {
            val setup = answers.toRoutineSetup()
            val changed = setup.goal != routine.goal || setup.repPreference != routine.repPreference ||
                setup.cadence != routine.cadence || setup.cardio != routine.cardio || setup.equipment != routine.equipment
            if (changed) {
                viewModel.editProperties(routine.name, setup.goal, setup.cadence, setup.equipment, setup.repPreference, setup.cardio)
            }
        }
    }
}

/** The session lengths offered, in minutes. */
private val SESSION_LENGTHS = listOf(30, 45, 50, 60, 75, 90)

/** Handles for the tests. */
const val PICKER_SEARCH = "picker-search"
const val SETTING_DONE = "setting-done"

