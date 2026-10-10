package com.tylergimpel.traintracks.ui.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Avoided
import com.tylergimpel.traintracks.domain.Effort
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.PerformedSet
import com.tylergimpel.traintracks.domain.Recommendation
import com.tylergimpel.traintracks.domain.RoutineDay
import com.tylergimpel.traintracks.domain.Session
import com.tylergimpel.traintracks.domain.SessionExercise
import com.tylergimpel.traintracks.domain.SessionPosition
import com.tylergimpel.traintracks.domain.SetDraft
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.WorkoutTip
import com.tylergimpel.traintracks.domain.workoutTip
import com.tylergimpel.traintracks.domain.asAmountText
import com.tylergimpel.traintracks.domain.asTextIn
import com.tylergimpel.traintracks.domain.asTimeText
import com.tylergimpel.traintracks.ui.RestFix
import com.tylergimpel.traintracks.ui.exercise.ExercisePicker
import com.tylergimpel.traintracks.ui.icons.AppIcons
import com.tylergimpel.traintracks.ui.label
import com.tylergimpel.traintracks.ui.settingsFor
import com.tylergimpel.traintracks.ui.theme.AppOutlinedButton
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.MainButton
import com.tylergimpel.traintracks.ui.theme.SectionLabel
import com.tylergimpel.traintracks.ui.theme.UppercaseText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The workout: full-screen, no top bar and no nav (#142).
 *
 * The design approved in [Prototype: starting and running a workout] (variant D), on the wheel
 * settled in `docs/design/logging-screen.md`. The only chrome is one 48 tall strip: the drawer,
 * the time, and Finish. Everything about the exercise that is not the numbers comes from its name.
 */
@Composable
fun SessionScreen(
    onPickExercise: () -> Unit,
    onOpenHistory: (String) -> Unit = {},
    onLeave: () -> Unit = {},
    onFinished: () -> Unit = {},
    viewModel: SessionViewModel = viewModel(),
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val tipsDone by viewModel.tipsDone.collectAsStateWithLifecycle()
    // One tip at a time, each at its moment and only until it is seen or used (#156).
    val state = loaded.copy(tip = workoutTip(tipsDone, loaded.tipMoment()))
    val context = LocalContext.current

    val askAboutNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.askedToShowRestCountdown() }

    // Asked when the first rest starts, and not before (#88): that is the moment the reason is
    // obvious. The standing line on screen catches a lifter who said no.
    LaunchedEffect(state.askToShowRestCountdown) {
        if (!state.askToShowRestCountdown) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askAboutNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.askedToShowRestCountdown()
        }
    }

    // Both permissions are revocable and granted somewhere else, so they are read again every
    // time the lifter comes back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckRestAlerts()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A finished workout goes back to Today, once.
    LaunchedEffect(state.finished) {
        if (state.finished) {
            viewModel.finishedSeen()
            onFinished()
        }
    }

    // Opened on a plan with nothing chosen yet (Resume, or a day started elsewhere): straight into
    // the first exercise with work left, never an overview.
    LaunchedEffect(state.selected == null, state.lineup.isNotEmpty()) {
        if (state.selected == null) {
            state.lineup.firstOrNull { it.hasWorkLeft }?.let { viewModel.select(it.exercise) }
        }
    }

    SessionScreen(
        state = state,
        entry = SetEntryActions(
            onDraftChange = viewModel::changeDraft,
            onRightDraftChange = viewModel::changeRightDraft,
            onTogglePerSide = viewModel::togglePerSide,
            onReadIn = viewModel::readIn,
            onToggleDisplayUnitOverride = viewModel::toggleDisplayUnitOverride,
            onLogSet = viewModel::logSet,
            onRecordSeen = viewModel::recordSeen,
            onEditEffort = viewModel::startEditing,
            onEditingDraftChange = viewModel::changeEditingDraft,
            onSaveEditing = viewModel::saveEditing,
            onDeleteEditing = viewModel::deleteEditing,
            onCancelEditing = viewModel::cancelEditing,
            onAddDrop = viewModel::startAddingDrop,
            onDropDraftChange = viewModel::changeDropDraft,
            onSaveDrop = viewModel::saveDrop,
            onCancelDrop = viewModel::cancelAddingDrop,
        ),
        rest = RestActions(
            onEndRest = viewModel::endRest,
            onAddRest = viewModel::addTimeToRest,
            onFixRestAlerts = { fix: RestFix -> context.startActivity(settingsFor(fix, context)) },
            onEditRest = viewModel::startEditingRest,
            onRestThisSet = viewModel::restThisSetOnly,
            onRestAlways = viewModel::restFromNowOn,
            onRestChange = viewModel::changeEditingRest,
            onCancelRest = viewModel::cancelEditingRest,
        ),
        cycle = CycleActions(
            onStartRefiling = viewModel::startRefiling,
            onRefileOn = viewModel::refileOn,
            onCancelRefiling = viewModel::cancelRefiling,
            onFinish = viewModel::openFinish,
            onCloseFinish = viewModel::closeFinish,
            onFinishWorkout = viewModel::finishWorkout,
            onFinishAs = viewModel::finishSession,
            onCancelFinishing = viewModel::cancelFinishing,
        ),
        exercises = ExerciseActions(
            onPickExercise = onPickExercise,
            onAdd = viewModel::startAdding,
            onSkip = viewModel::skipExercise,
            onEditExercise = viewModel::startEditingExercise,
            onExerciseDetailsChange = viewModel::changeExerciseDetails,
            onSaveExerciseDetails = viewModel::saveExerciseDetails,
            onCancelExerciseDetails = viewModel::cancelEditingExercise,
            onJumpTo = viewModel::select,
            onSwap = viewModel::startSwapping,
            onSwapAt = viewModel::startSwappingAt,
            onSwapTo = viewModel::swapTo,
            onCancelSwap = viewModel::cancelSwapping,
            onKeepAvoided = viewModel::keepAvoided,
            onSwapAvoided = viewModel::swapAvoided,
            onRemoveAvoided = viewModel::removeAvoided,
            onResetEstimate = viewModel::resetEstimate,
            onOpenHistory = { onOpenHistory(it.id) },
            onKeepEstimate = viewModel::keepEstimate,
        ),
        wholeSession = SessionActions(
            onGroupWithNext = viewModel::groupWithNext,
            onUngroup = viewModel::ungroup,
            onSetAutoJump = viewModel::setAutoJump,
            onSessionNoteChange = viewModel::noteSession,
            onTipDone = viewModel::tipDone,
            onLeave = onLeave,
        ),
    )
}

/**
 * What the workout can be asked to do, grouped by what it is about (#63).
 *
 * Every lambda defaults to doing nothing, so a preview or a test wires up the one it is about.
 *
 * Entering a set, and correcting one that is already down.
 */
data class SetEntryActions(
    val onDraftChange: (SetDraft) -> Unit = {},
    val onRightDraftChange: (SetDraft) -> Unit = {},
    val onTogglePerSide: () -> Unit = {},
    val onReadIn: (WeightUnit) -> Unit = {},
    val onToggleDisplayUnitOverride: () -> Unit = {},
    val onLogSet: () -> Unit = {},
    /** A record announcement has been shown. */
    val onRecordSeen: () -> Unit = {},
    val onEditEffort: (SessionExercise, PerformedSet, Effort) -> Unit = { _, _, _ -> },
    val onEditingDraftChange: (SetDraft) -> Unit = {},
    val onSaveEditing: () -> Unit = {},
    val onDeleteEditing: () -> Unit = {},
    val onCancelEditing: () -> Unit = {},
    val onAddDrop: (WeightUnit, Effort) -> Unit = { _, _ -> },
    val onDropDraftChange: (SetDraft) -> Unit = {},
    val onSaveDrop: () -> Unit = {},
    val onCancelDrop: () -> Unit = {},
)

/** The rest timer: the one running, and the length the lifter is arguing with. */
data class RestActions(
    val onEndRest: () -> Unit = {},
    val onAddRest: () -> Unit = {},
    val onFixRestAlerts: (RestFix) -> Unit = {},
    val onEditRest: () -> Unit = {},
    val onRestThisSet: () -> Unit = {},
    val onRestAlways: () -> Unit = {},
    val onRestChange: (Duration) -> Unit = {},
    val onCancelRest: () -> Unit = {},
)

/** Where this session sits in the **Cycle**: refiling it, and ending it. */
data class CycleActions(
    val onStartRefiling: (Session) -> Unit = {},
    val onRefileOn: (LocalDate) -> Unit = {},
    val onCancelRefiling: () -> Unit = {},
    /** Opens the finish summary. */
    val onFinish: () -> Unit = {},
    val onCloseFinish: () -> Unit = {},
    val onFinishWorkout: () -> Unit = {},
    val onFinishAs: (RoutineDay?) -> Unit = {},
    val onCancelFinishing: () -> Unit = {},
)

/** Which exercise is in front of the lifter, and everything they can say about it. */
data class ExerciseActions(
    /** The whole bank, to write down a machine the picker does not know. */
    val onPickExercise: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onEditExercise: () -> Unit = {},
    val onExerciseDetailsChange: (ExerciseDetails) -> Unit = {},
    val onSaveExerciseDetails: () -> Unit = {},
    val onCancelExerciseDetails: () -> Unit = {},
    val onJumpTo: (Exercise) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onSwapAt: (Exercise) -> Unit = {},
    val onSwapTo: (Exercise) -> Unit = {},
    val onCancelSwap: () -> Unit = {},
    val onKeepAvoided: (Avoided) -> Unit = {},
    val onSwapAvoided: (Avoided) -> Unit = {},
    val onRemoveAvoided: (Avoided) -> Unit = {},
    val onResetEstimate: () -> Unit = {},
    val onKeepEstimate: () -> Unit = {},
    val onOpenHistory: (Exercise) -> Unit = {},
)

/** The session as a whole, and the way out of it. */
data class SessionActions(
    val onGroupWithNext: (Long) -> Unit = {},
    val onUngroup: (Long) -> Unit = {},
    val onSetAutoJump: (Long, Boolean) -> Unit = { _, _ -> },
    val onSessionNoteChange: (String) -> Unit = {},
    /** A workout tip was tapped away or its gesture used: it does not show again (#156). */
    val onTipDone: (WorkoutTip) -> Unit = {},
    /** Back to Today with the workout still running. */
    val onLeave: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState = SessionUiState(),
    entry: SetEntryActions = SetEntryActions(),
    rest: RestActions = RestActions(),
    cycle: CycleActions = CycleActions(),
    exercises: ExerciseActions = ExerciseActions(),
    wholeSession: SessionActions = SessionActions(),
) {
    val colors = LocalGymColors.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val anchors = remember { TipAnchors() }
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    // A record registers when it is set (#124): said once, briefly, and out of the way.
    val snackbars = remember { SnackbarHostState() }
    val recordText = state.newRecord?.let { record ->
        val unit = state.selected?.displayUnit ?: state.lifterUnit
        if (record.load.value < 0) {
            stringResource(R.string.session_record_assisted, record.reps, Kilograms(-record.load.value).asTextIn(unit), stringResource(unit.label))
        } else {
            stringResource(R.string.session_record, record.reps, record.load.asTextIn(unit), stringResource(unit.label))
        }
    }
    LaunchedEffect(state.newRecord) {
        recordText ?: return@LaunchedEffect
        snackbars.showSnackbar(recordText, duration = SnackbarDuration.Short)
        entry.onRecordSeen()
    }

    // Back closes the top layer first; with nothing open it asks rather than leaving (#134).
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen) { leaving = true }

    // How far the current sideways drag has travelled, so a slow pull opens the drawer as a
    // thrown one does. Reset when a drag starts, because one that ends off the screen never says.
    var dragged by remember { mutableFloatStateOf(0f) }
    val dragToOpen = with(LocalDensity.current) { DRAG_TO_OPEN.toPx() }

    CompositionLocalProvider(LocalTipAnchors provides anchors) {
        ModalNavigationDrawer(
            drawerState = drawer,
            // **Never from the edge**, where gesture navigation's Back lives: the drawer opens from
            // the strip, or a sideways swipe anywhere. Once open it follows a finger dragging it shut.
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                WorkoutDrawer(
                    state = state,
                    onJumpTo = { exercise ->
                        exercises.onJumpTo(exercise)
                        scope.launch { drawer.close() }
                    },
                    onSwap = { exercise ->
                        scope.launch { drawer.close() }
                        exercises.onSwapAt(exercise)
                    },
                    onAdd = {
                        scope.launch { drawer.close() }
                        exercises.onAdd()
                    },
                    onRefile = cycle.onStartRefiling,
                    onNoteChange = wholeSession.onSessionNoteChange,
                )
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> dragged += delta },
                        onDragStarted = { dragged = 0f },
                        onDragStopped = { velocity ->
                            // Either direction, thrown or pulled: a phone is held in whichever hand
                            // is free, and a thumb resting on the glass has no velocity to give.
                            if (velocity > SWIPE_TO_OPEN || velocity < -SWIPE_TO_OPEN ||
                                dragged > dragToOpen || dragged < -dragToOpen
                            ) {
                                wholeSession.onTipDone(WorkoutTip.SWIPE_FOR_WORKOUT)
                                drawer.open()
                            }
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Strip(state, onOpenDrawer = { scope.launch { drawer.open() } }, onFinish = cycle.onFinish)
                    val selected = state.selected
                    if (selected == null) {
                        NothingChosen(onAdd = exercises.onAdd, onPickExercise = exercises.onPickExercise)
                    } else {
                        Head(state, selected, onOpenMenu = { menuOpen = true })
                        Notices(state, rest.onFixRestAlerts, exercises)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Wheel(state, selected, entry, onSwitchedUnit = { wholeSession.onTipDone(WorkoutTip.SWITCH_UNIT) })
                        }
                        Hints(state, selected)
                        Controls(state, entry, onTyped = { wholeSession.onTipDone(WorkoutTip.TAP_TO_TYPE) })
                    }
                }

                state.resting?.let { resting ->
                    RestBar(
                        resting = resting,
                        after = state.restAfter,
                        onAdd = rest.onAddRest,
                        onSkip = rest.onEndRest,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
                // Last, so it sits over the rest bar too; it covers nothing but itself.
                state.tip?.let { tip -> TipBubble(tip, anchors, onDismiss = { wholeSession.onTipDone(tip) }) }
            }
        }
    }

    if (menuOpen && state.selected != null) {
        ExerciseMenu(
            state = state,
            exercise = state.selected,
            onDismiss = { menuOpen = false },
            entry = entry,
            rest = rest,
            exercises = exercises,
            wholeSession = wholeSession,
        )
    }

    if (leaving) {
        Sheet(onDismiss = { leaving = false }) {
            SheetTitle(stringResource(R.string.workout_leave_title))
            Text(stringResource(R.string.workout_leave_body), color = colors.dim)
            MainButton(stringResource(R.string.workout_keep_going), onClick = { leaving = false }, Modifier.fillMaxWidth())
            AppOutlinedButton(
                stringResource(R.string.workout_go_to_today),
                onClick = {
                    leaving = false
                    wholeSession.onLeave()
                },
                Modifier.fillMaxWidth(),
            )
        }
    }

    state.askingToFinish?.let { asking ->
        FinishSheet(
            state = state,
            allDone = asking.allDone,
            onFinish = cycle.onFinishWorkout,
            onKeepGoing = cycle.onCloseFinish,
            onAdd = {
                cycle.onCloseFinish()
                exercises.onAdd()
            },
        )
    }

    state.editing?.let { editing ->
        val performed = state.exercises.firstOrNull { it.id == editing.sessionExerciseId }
        val effort = performed?.sets?.firstOrNull { it.setIndex == editing.setIndex }?.efforts
            ?.firstOrNull { it.id == editing.effortId }
        EditSetDialog(
            editing,
            entry.onEditingDraftChange,
            entry.onSaveEditing,
            entry.onDeleteEditing,
            entry.onCancelEditing,
            onAddDrop = effort?.takeIf { it.performance is com.tylergimpel.traintracks.domain.Performance.WeightAndReps }?.let {
                {
                    entry.onCancelEditing()
                    entry.onAddDrop(editing.exerciseUnit, it)
                }
            },
        )
    }

    state.addingDrop?.let { drop ->
        AddDropDialog(drop, entry.onDropDraftChange, entry.onSaveDrop, entry.onCancelDrop)
    }

    if (state.finishing) {
        WhichDayDialog(state.routine, cycle.onFinishAs, cycle.onCancelFinishing)
    }

    state.refiling?.let { RefileSessionDialog(it.date, cycle.onRefileOn, cycle.onCancelRefiling) }

    state.avoided?.let {
        AvoidedPrompt(it, exercises.onKeepAvoided, exercises.onSwapAvoided, exercises.onRemoveAvoided)
    }

    state.swapCandidates?.let { candidates ->
        ExercisePicker(
            title = if (state.picking == Picking.ADD) {
                stringResource(R.string.workout_add_title)
            } else {
                stringResource(R.string.workout_swap_title, state.selected?.name.orEmpty())
            },
            bank = state.pickerBank,
            equipment = state.routine?.routine?.equipment.orEmpty(),
            onPick = exercises.onSwapTo,
            onAddNew = exercises.onPickExercise,
            onCancel = exercises.onCancelSwap,
            // For a swap: same muscle, same mechanic, the lifter's own equipment, with history
            // first. For an add: what they have done before. Either stands until they type.
            suggested = candidates,
            note = when {
                state.swappingLeavesOnlyDisliked -> stringResource(R.string.swap_only_disliked)
                candidates.isEmpty() && state.picking == Picking.SWAP -> stringResource(R.string.swap_nothing)
                else -> null
            },
        )
    }

    state.editingRest?.let {
        RestDialog(it, rest.onRestChange, rest.onRestThisSet, rest.onRestAlways, rest.onCancelRest)
    }

    state.editingExercise?.let { details ->
        ExerciseDetailsDialog(
            details = details,
            onChange = exercises.onExerciseDetailsChange,
            onSave = exercises.onSaveExerciseDetails,
            onCancel = exercises.onCancelExerciseDetails,
        )
    }
}

/** The only chrome: the drawer with where you are, the time, and Finish. 48 tall. */
@Composable
private fun Strip(state: SessionUiState, onOpenDrawer: () -> Unit, onFinish: () -> Unit) {
    val colors = LocalGymColors.current
    val position = state.lineup.indexOfFirst { it.exercise.id == state.selected?.id }
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StripButton(
            onClick = onOpenDrawer,
            modifier = Modifier.testTag(DRAWER_BUTTON).then(rememberTipAnchor(WorkoutTip.SWIPE_FOR_WORKOUT)),
        ) {
            Icon(AppIcons.List, contentDescription = stringResource(R.string.drawer_open), modifier = Modifier.size(22.dp))
            if (state.lineup.isNotEmpty() && position >= 0) {
                Text("${position + 1}/${state.lineup.size}", fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            state.session?.let {
                Text(clockText(it.startedAt), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.text)
            }
            state.timeLeft?.let {
                Text(stringResource(R.string.wheel_time_left, minutesText(it)), fontSize = 15.sp, color = colors.dim, lineHeight = 17.sp)
            }
        }
        StripButton(onClick = onFinish, modifier = Modifier.testTag(FINISH)) {
            Text(stringResource(R.string.session_finish), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StripButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = LocalGymColors.current.raised,
        contentColor = LocalGymColors.current.text,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** The exercise's name (its menu), where the lifter is in it, and whether the app is guessing. */
@Composable
private fun Head(state: SessionUiState, exercise: Exercise, onOpenMenu: () -> Unit) {
    val colors = LocalGymColors.current
    Column {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = stringResource(R.string.workout_exercise_menu), onClick = onOpenMenu)
                .testTag(EXERCISE_NAME),
            verticalAlignment = Alignment.Bottom,
        ) {
            UppercaseText(exercise.name, style = MaterialTheme.typography.headlineLarge.copy(color = colors.text), modifier = Modifier.weight(1f, fill = false))
            Text(" ▾", fontSize = 22.sp, color = colors.dim)
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            counterText(state)?.let { Text(it, fontSize = 15.sp, color = colors.dim) }
            if (state.recommendation is Recommendation.Calibrating) {
                Text(
                    stringResource(R.string.session_calibrating).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = colors.calib,
                    modifier = Modifier.border(1.dp, colors.calib, ControlShape).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        // In front of the lifter every time they train it, which is the whole point of writing it
        // down: "seat notch 3" is no use on a screen nobody opens in a gym.
        exercise.note?.let { Text(it, fontSize = 15.sp, color = colors.dim, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** `Set 4 of 6 · working 1 of 3`, or `· warm-up`; totals only where something asked for them. */
@Composable
private fun counterText(state: SessionUiState): String? {
    val pace = state.pace ?: return null
    val type = state.recommendation?.setType
    val planned = pace.planned
    val set = if (planned != null) {
        stringResource(R.string.workout_set_of, pace.setsDone, planned.total)
    } else {
        stringResource(R.string.workout_set, pace.setsDone)
    }
    val tail = when {
        type == null || type.countsTowardVolume -> if (planned != null) {
            stringResource(R.string.workout_working_of, pace.workingDone, planned.working)
        } else {
            stringResource(R.string.workout_working, pace.workingDone)
        }
        else -> stringResource(type.label)
    }
    return "$set · $tail"
}

/** The few things said above the wheel: failures that are otherwise silent, and an offer. */
@Composable
private fun Notices(
    state: SessionUiState,
    onFixRestAlerts: (RestFix) -> Unit,
    exercises: ExerciseActions,
) {
    if (!state.restAlarmsAreReliable) {
        RestAlertWarning(R.string.workout_alarms_late, R.string.workout_allow) { onFixRestAlerts(RestFix.EXACT_ALARMS) }
    }
    if (!state.restCountdownVisible) {
        RestAlertWarning(R.string.workout_countdown_off, R.string.workout_turn_on) {
            onFixRestAlerts(RestFix.NOTIFICATIONS)
        }
    }
    if (state.offerResetEstimate) ResetEstimateOffer(exercises.onResetEstimate, exercises.onKeepEstimate)
}

/** The wheel: the sets done, the one to do, and the way to one more once the plan is met. */
@Composable
private fun Wheel(state: SessionUiState, exercise: Exercise, entry: SetEntryActions, onSwitchedUnit: () -> Unit) {
    val complete = state.lineup.firstOrNull { it.exercise.id == exercise.id }
        ?.let { it.planned != null && it.done >= it.planned } == true
    var extra by remember(exercise.id, state.wheel.size) { mutableStateOf(false) }
    SetWheel(
        exercise = exercise,
        sets = if (complete && !extra) state.wheel.filterIsInstance<WheelSet.Logged>() else state.wheel,
        lifterUnit = state.lifterUnit,
        draft = state.draft,
        previous = state.previousSets,
        onEdit = { logged ->
            val performed = state.exercises.firstOrNull { row ->
                row.exercise.id == exercise.id && row.sets.any { it.setIndex == logged.set.setIndex && it == logged.set }
            }
            if (performed != null) entry.onEditEffort(performed, logged.set, logged.set.efforts.first())
        },
        // Tapping the unit on the wheel is the gesture its tip teaches; the menu's way is not.
        onReadIn = {
            onSwitchedUnit()
            entry.onReadIn(it)
        },
        onAddSet = if (complete && !extra) { { extra = true } } else null,
        modifier = Modifier.fillMaxSize(),
    )
}

/** What the number cannot say by itself: the plate line, the app guessing, and effort. */
@Composable
private fun Hints(state: SessionUiState, exercise: Exercise) {
    val colors = LocalGymColors.current
    val recommendation = state.recommendation
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        state.plateLine?.let { PlateLineRow(it, exercise.displayUnit) }
        val line = when (recommendation) {
            is Recommendation.Calibrating -> R.string.session_calibrating_hint
            is Recommendation.CuttingVolume -> R.string.session_cutting_volume
            Recommendation.Stopped -> R.string.session_stopped
            is Recommendation.Prescribing -> when {
                recommendation.reAnchored -> R.string.session_re_anchored
                recommendation.atTheTopOfTheStack -> R.string.session_top_of_stack
                else -> null
            }
            else -> null
        }
        line?.let {
            Text(
                stringResource(it),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = if (recommendation is Recommendation.Calibrating) colors.calib else colors.dim,
            )
        }
        // Hypertrophy is load-independent above a third of a max *only if the effort is high*, so
        // a set of fifteen prescribed without saying so is not what the evidence supports.
        val range = recommendation?.repRange()
        if (range != null && range.top >= REPS_THAT_NEED_EFFORT) {
            Text(stringResource(R.string.wheel_effort), fontSize = 15.sp, textAlign = TextAlign.Center, color = colors.accentText)
        }
    }
}

/**
 * The controls a thumb acts on, fixed below the wheel: the steppers, then Log.
 *
 * The same place every set, because a control that moves between sets is one you have to look
 * at. Each number is typed by tapping it, which is what [onTyped] hears about.
 */
@Composable
private fun Controls(state: SessionUiState, entry: SetEntryActions, onTyped: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.perSide) SectionLabel(stringResource(R.string.set_side_left))
        Steppers(state.draft, entry.onDraftChange, tagged = true, onTyped)
        if (state.perSide) {
            SectionLabel(stringResource(R.string.set_side_right))
            Steppers(state.rightDraft, entry.onRightDraftChange, tagged = false, onTyped)
        }
        MainButton(
            text = stringResource(R.string.session_log_set),
            onClick = entry.onLogSet,
            enabled = state.draft.isLoggable && (!state.perSide || state.rightDraft.isLoggable),
            modifier = Modifier.fillMaxWidth().testTag(LOG_SET),
        )
    }
}

/**
 * A stepper for each thing this exercise records, two to a row.
 *
 * The [tagged] row's first number is the one the tap-to-type tip points at.
 */
@Composable
private fun Steppers(draft: SetDraft, onChange: (SetDraft) -> Unit, tagged: Boolean, onTyped: () -> Unit) {
    val type = draft.loggingType
    val unit = stringResource(draft.displayUnit.label)
    val steppers = buildList<@Composable (Modifier) -> Unit> {
        if (type.recordsWeight) add { m ->
            Stepper(onTyped, draft.weightText, unit, { onChange(draft.copy(weightText = it)) }, { onChange(draft.stepWeight(it)) }, KeyboardType.Decimal, m, if (tagged) WEIGHT_FIELD else null)
        }
        if (type.recordsAddedWeight || type.recordsAssistance) add { m ->
            val label = stringResource(if (type.recordsAssistance) R.string.workout_help else R.string.workout_added, unit)
            Stepper(onTyped, draft.addedWeightText, label, { onChange(draft.copy(addedWeightText = it)) }, { onChange(draft.copy(addedWeightText = stepped(draft.addedWeightText, draft.displayUnit.step * it))) }, KeyboardType.Decimal, m)
        }
        if (type.recordsReps) add { m ->
            Stepper(onTyped, draft.repsText, stringResource(R.string.set_reps), { onChange(draft.copy(repsText = it)) }, { onChange(draft.copy(repsText = stepped(draft.repsText, it.toDouble(), whole = true))) }, KeyboardType.Number, m)
        }
        if (type.recordsDistance) add { m ->
            Stepper(onTyped, draft.distanceText, stringResource(draft.distanceUnit.label), { onChange(draft.copy(distanceText = it)) }, { onChange(draft.copy(distanceText = stepped(draft.distanceText, 0.5 * it))) }, KeyboardType.Decimal, m)
        }
        if (type.recordsDuration) add { m ->
            Stepper(
                onTyped,
                draft.durationText,
                draft.duration?.asTimeText() ?: stringResource(R.string.set_seconds),
                { onChange(draft.copy(durationText = it)) },
                { onChange(draft.copy(durationText = stepped(draft.durationText, 15.0 * it, whole = true))) },
                KeyboardType.Number,
                m,
            )
        }
    }
    val anchor = if (tagged) rememberTipAnchor(WorkoutTip.TAP_TO_TYPE) else Modifier
    steppers.chunked(2).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEachIndexed { i, stepper ->
                stepper(Modifier.weight(1f).let { if (r == 0 && i == 0) it.then(anchor) else it })
            }
        }
    }
}

/** [text] moved by [by], never below nothing; whole numbers where [whole]. */
private fun stepped(text: String, by: Double, whole: Boolean = false): String {
    val now = text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val next = (now + by).coerceAtLeast(0.0)
    return if (whole) next.toLong().toString() else next.asAmountText()
}

/** − value + , where tapping the value types it, which [onTyped] hears about. */
@Composable
private fun Stepper(
    onTyped: () -> Unit,
    value: String,
    label: String,
    onType: (String) -> Unit,
    onStep: (Int) -> Unit,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val colors = LocalGymColors.current
    var typing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .background(colors.surface, ControlShape)
            .border(1.dp, colors.line, ControlShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", stringResource(R.string.workout_less, label)) { onStep(-1) }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = stringResource(R.string.workout_type_it)) {
                    typing = true
                    onTyped()
                }
                .let { if (tag != null) it.testTag(tag) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (typing) {
                val focus = remember { FocusRequester() }
                var field by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
                var focused by remember { mutableStateOf(false) }
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onType(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.text, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { typing = false }),
                    modifier = Modifier
                        .width(80.dp)
                        .border(2.dp, colors.accent, ControlShape)
                        .padding(vertical = 4.dp)
                        .focusRequester(focus)
                        .onFocusChanged {
                            if (focused && !it.isFocused) typing = false
                            focused = it.isFocused
                        },
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value.ifBlank { "–" }, style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text(label, fontSize = 13.sp, color = colors.dim, lineHeight = 16.sp)
                }
            }
        }
        StepButton("+", stringResource(R.string.workout_more, label)) { onStep(1) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 30.sp, color = LocalGymColors.current.text)
    }
}

/**
 * The rest, a full-width accent bar over Log (#134): what it follows, a big countdown, +1:00 and
 * Skip. The steppers above stay usable. It starts by itself when a set goes down.
 */
@Composable
private fun RestBar(
    resting: Resting,
    after: com.tylergimpel.traintracks.domain.SetType?,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGymColors.current
    val left by produceState(Duration.between(Instant.now(), resting.endsAt), resting) {
        while (true) {
            value = Duration.between(Instant.now(), resting.endsAt)
            delay(250)
        }
    }
    if (left.isNegative || left.isZero) return
    Surface(color = colors.accent, contentColor = colors.onAccent, modifier = modifier.fillMaxWidth().testTag(REST_BAR)) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(min = 104.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (after?.let { stringResource(R.string.workout_rest_after, stringResource(it.label)) }
                        ?: stringResource(R.string.workout_rest)).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = colors.onAccent.copy(alpha = 0.75f),
                )
                Text(countdown(left), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp))
            }
            RestButton(stringResource(R.string.rest_add_time), onAdd)
            RestButton(stringResource(R.string.wheel_skip_rest), onSkip)
        }
    }
}

@Composable
private fun RestButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ControlShape, color = Color.Black.copy(alpha = 0.14f), modifier = Modifier.height(56.dp)) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

/** No exercise chosen: a workout with nothing planned, or one whose plan is done. */
@Composable
private fun ColumnScope.NothingChosen(onAdd: () -> Unit, onPickExercise: () -> Unit) {
    Spacer(Modifier.weight(1f))
    MainButton(stringResource(R.string.workout_add_exercise), onClick = onAdd, Modifier.fillMaxWidth().testTag(ADD_EXERCISE))
    AppOutlinedButton(stringResource(R.string.workout_whole_bank), onClick = onPickExercise, Modifier.fillMaxWidth())
    Spacer(Modifier.weight(1f))
}

/** The exercise's own menu, from its name: swap, add after, skip, and everything else about it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseMenu(
    state: SessionUiState,
    exercise: Exercise,
    onDismiss: () -> Unit,
    entry: SetEntryActions,
    rest: RestActions,
    exercises: ExerciseActions,
    wholeSession: SessionActions,
) {
    fun then(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    val group = state.positions.filterIsInstance<SessionPosition.Grouped>()
        .firstOrNull { position -> position.exercises.any { it.exercise.id == exercise.id } }
    val performed = state.exercises.firstOrNull { it.exercise.id == exercise.id }
    val canGroup = performed != null && group == null &&
        state.positions.indexOfFirst { position -> position.exercises.any { it.id == performed.id } } < state.positions.lastIndex
    Sheet(onDismiss = onDismiss) {
        SheetTitle(exercise.name)
        MenuOption(stringResource(R.string.workout_swap), onClick = then(exercises.onSwap), icon = { Icon(AppIcons.Swap, null) })
        MenuOption(stringResource(R.string.workout_add_after), onClick = then(exercises.onAdd), icon = { Icon(AppIcons.Plus, null) })
        MenuOption(stringResource(R.string.workout_skip), onClick = then(exercises.onSkip), trailing = stringResource(R.string.workout_skip_back))
        state.recommendation?.prescription?.rest?.let {
            MenuOption(stringResource(R.string.workout_rest_length), onClick = then(rest.onEditRest), trailing = it.asTimeText())
        }
        if (exercise.unilateral) {
            MenuOption(stringResource(R.string.set_per_side), onClick = then(entry.onTogglePerSide), trailing = if (state.perSide) "✓" else null)
        }
        if (exercise.loggingType.recordsAnyWeight) {
            val other = if (state.draft.displayUnit == WeightUnit.KILOGRAMS) WeightUnit.POUNDS else WeightUnit.KILOGRAMS
            MenuOption(
                stringResource(R.string.workout_this_set_in, stringResource(other.label)),
                onClick = then {
                    if (!state.overridingDisplayUnit) entry.onToggleDisplayUnitOverride()
                    entry.onReadIn(other)
                },
            )
        }
        if (canGroup) {
            MenuOption(stringResource(R.string.session_group_with_next), onClick = then { wholeSession.onGroupWithNext(performed.id) })
        }
        group?.let {
            MenuOption(
                stringResource(R.string.session_auto_jump),
                onClick = then { wholeSession.onSetAutoJump(it.superset.id, !it.superset.autoJump) },
                trailing = if (it.superset.autoJump) "✓" else null,
            )
            MenuOption(stringResource(R.string.session_ungroup), onClick = then { wholeSession.onUngroup(it.superset.id) })
        }
        MenuOption(stringResource(R.string.history_open), onClick = then { exercises.onOpenHistory(exercise) })
        MenuOption(stringResource(R.string.workout_exercise_settings), onClick = then(exercises.onEditExercise))
    }
}

@Composable
private fun MenuOption(text: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null, trailing: String? = null) {
    val colors = LocalGymColors.current
    Surface(onClick = onClick, shape = ControlShape, color = colors.raised, contentColor = colors.text, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon?.invoke()
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, fontSize = 14.sp, color = colors.dim) }
        }
    }
}

/** Finishing: minutes, sets, exercises, what stays unlogged, and the way back in. */
@Composable
private fun FinishSheet(
    state: SessionUiState,
    allDone: Boolean,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = LocalGymColors.current
    val sets = state.exercises.sumOf { it.sets.size }
    val exercisesDone = state.exercises.count { it.sets.isNotEmpty() }
    val unlogged = state.lineup.filterNot { it.skipped }.sumOf { item -> item.planned?.let { (it - item.done).coerceAtLeast(0) } ?: 0 }
    val minutes = state.session?.let { Duration.between(it.startedAt, Instant.now()).toMinutes().coerceAtLeast(1) } ?: 0
    Sheet(onDismiss = onKeepGoing) {
        SheetTitle(
            if (allDone) {
                stringResource(R.string.workout_all_done)
            } else {
                stringResource(R.string.workout_finish_title, state.session?.routineDayName ?: state.routine?.cursor?.name ?: stringResource(R.string.today_workout))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tally(minutes.toString(), stringResource(R.string.workout_minutes), Modifier.weight(1f))
            Tally(sets.toString(), stringResource(R.string.workout_sets), Modifier.weight(1f))
            Tally(exercisesDone.toString(), stringResource(R.string.workout_exercises), Modifier.weight(1f))
        }
        if (unlogged > 0) Text(stringResource(R.string.workout_unlogged, unlogged), color = colors.dim)
        MainButton(stringResource(R.string.workout_finish), onClick = onFinish, Modifier.fillMaxWidth().testTag(FINISH_WORKOUT))
        if (allDone) {
            AppOutlinedButton(stringResource(R.string.workout_add_exercise), onClick = onAdd, Modifier.fillMaxWidth())
        } else {
            AppOutlinedButton(stringResource(R.string.workout_keep_going), onClick = onKeepGoing, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Tally(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalGymColors.current
    Column(modifier.background(colors.raised, ControlShape).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = colors.text)
        Text(label, fontSize = 14.sp, color = colors.dim)
    }
}

/** A bottom sheet of the new look: the surface, 10 corners on top, padded like the prototype's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalGymColors.current.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun SheetTitle(text: String) {
    UppercaseText(text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp), modifier = Modifier.padding(bottom = 4.dp))
}

/** The session's own clock, `12:04`, ticking. */
@Composable
private fun clockText(startedAt: Instant): String {
    val seconds by produceState(secondsSince(startedAt), startedAt) {
        while (true) {
            delay(1_000)
            value = secondsSince(startedAt)
        }
    }
    return Duration.ofSeconds(seconds).asTimeText()
}

private fun secondsSince(startedAt: Instant): Long = Duration.between(startedAt, Instant.now()).seconds.coerceAtLeast(0)

private fun countdown(left: Duration): String = Duration.ofSeconds(left.seconds + if (left.nano > 0) 1 else 0).asTimeText()

@Composable
private fun minutesText(length: Duration): String = stringResource(R.string.workout_minutes_short, length.toMinutes().coerceAtLeast(1))

/** The band a recommendation asks for, where it asks for one. */
private fun Recommendation.repRange() = when (this) {
    is Recommendation.Prescribing -> repRange
    is Recommendation.CuttingVolume -> repRange
    else -> null
}

/** The top of a rep range at which the prescription has to say that effort is the point. */
private const val REPS_THAT_NEED_EFFORT = 15

/** How hard a sideways swipe has to be thrown to open the drawer. */
private const val SWIPE_TO_OPEN = 300f

/** How far a sideways drag has to travel to open the drawer, for a thumb with no velocity to give. */
private val DRAG_TO_OPEN = 72.dp

/** Handles for the tests. */
const val DRAWER_BUTTON = "drawer-button"
const val FINISH = "finish"
const val FINISH_WORKOUT = "finish-workout"
const val EXERCISE_NAME = "exercise-name"
const val REST_BAR = "rest-bar"
const val ADD_EXERCISE = "add-exercise"
