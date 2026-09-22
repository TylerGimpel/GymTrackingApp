package com.tylergimpel.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tylergimpel.gymtracker.domain.PlateLine
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.domain.DistanceUnit
import com.tylergimpel.gymtracker.domain.Avoided
import com.tylergimpel.gymtracker.domain.Drop
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.PrescribedLoad
import com.tylergimpel.gymtracker.domain.reading
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.domain.asTimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import com.tylergimpel.gymtracker.domain.asAmountText
import com.tylergimpel.gymtracker.ui.DayPickerDialog
import com.tylergimpel.gymtracker.ui.WeightUnitChips
import com.tylergimpel.gymtracker.ui.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The live logging screen: the wheel of sets, what the app asks for, and the rest that follows.
 *
 * The settled design in `docs/design/logging-screen.md`, built in #53 — [SetWheel] is the screen
 * proper and [WheelFooting] the fixed controls under it. The fields arrive holding the
 * recommendation, so logging a set as prescribed is one tap and the wheel moves on by itself.
 */
@Composable
fun SessionScreen(
    onPickExercise: () -> Unit,
    onOpenRoutine: () -> Unit,
    onOpenBodyweight: () -> Unit,
    onOpenPlates: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val askAboutNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.recheckRestAlerts() }

    // Asked once, on the way in. On Android 13 and later an app that never asks simply never
    // appears, and the countdown is the whole of the rest timer to a lifter with a phone in a
    // pocket. The system shows this at most once; the standing line below is what catches the
    // lifter who said no, or who turned notifications off later.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askAboutNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Both permissions are revocable and both are granted somewhere else, so the answer is read
    // again every time the lifter comes back rather than once at launch.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckRestAlerts()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
            onStartDay = viewModel::startCursorDay,
            onSkipDay = viewModel::startSkipping,
            onConfirmSkip = viewModel::skipDay,
            onCancelSkip = viewModel::cancelSkipping,
            onStartRefiling = viewModel::startRefiling,
            onRefileOn = viewModel::refileOn,
            onCancelRefiling = viewModel::cancelRefiling,
            onFinish = viewModel::startFinishing,
            onFinishAs = viewModel::finishSession,
            onCancelFinishing = viewModel::cancelFinishing,
        ),
        exercises = ExerciseActions(
            onPickExercise = onPickExercise,
            onEditExercise = viewModel::startEditingExercise,
            onExerciseDetailsChange = viewModel::changeExerciseDetails,
            onSaveExerciseDetails = viewModel::saveExerciseDetails,
            onCancelExerciseDetails = viewModel::cancelEditingExercise,
            onJumpTo = viewModel::select,
            onSwap = viewModel::startSwapping,
            onSwapTo = viewModel::swapTo,
            onCancelSwap = viewModel::cancelSwapping,
            onKeepAvoided = viewModel::keepAvoided,
            onSwapAvoided = viewModel::swapAvoided,
            onRemoveAvoided = viewModel::removeAvoided,
        ),
        wholeSession = SessionActions(
            onGroupWithNext = viewModel::groupWithNext,
            onUngroup = viewModel::ungroup,
            onSetAutoJump = viewModel::setAutoJump,
            onSessionNoteChange = viewModel::noteSession,
            onOpenRoutine = onOpenRoutine,
            onOpenBodyweight = onOpenBodyweight,
            onOpenPlates = onOpenPlates,
        ),
    )
}

/**
 * What the logging screen can be asked to do, grouped by what it is about.
 *
 * Five bundles rather than fifty parameters. The screen had grown one argument per interaction
 * until the list was longer than most of the composables reading it, and a flat list of lambdas
 * tells a reader nothing about which of them belong together (#63). These do: each group is the
 * surface of one thing the session view model holds.
 *
 * Every lambda defaults to doing nothing, so a preview or a test wires up the one it is about
 * and says nothing about the rest.
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

/** Where this session sits in the **Cycle** — starting a day, skipping, refiling, ending. */
data class CycleActions(
    val onStartDay: () -> Unit = {},
    val onSkipDay: () -> Unit = {},
    val onConfirmSkip: () -> Unit = {},
    val onCancelSkip: () -> Unit = {},
    val onStartRefiling: (Session) -> Unit = {},
    val onRefileOn: (LocalDate) -> Unit = {},
    val onCancelRefiling: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onFinishAs: (RoutineDay?) -> Unit = {},
    val onCancelFinishing: () -> Unit = {},
)

/** Which exercise is in front of the lifter, and everything they can say about it. */
data class ExerciseActions(
    val onPickExercise: () -> Unit = {},
    val onEditExercise: () -> Unit = {},
    val onExerciseDetailsChange: (ExerciseDetails) -> Unit = {},
    val onSaveExerciseDetails: () -> Unit = {},
    val onCancelExerciseDetails: () -> Unit = {},
    val onJumpTo: (Exercise) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onSwapTo: (Exercise) -> Unit = {},
    val onCancelSwap: () -> Unit = {},
    val onKeepAvoided: (Avoided) -> Unit = {},
    val onSwapAvoided: (Avoided) -> Unit = {},
    val onRemoveAvoided: (Avoided) -> Unit = {},
)

/** The session as a whole, and the way out to the other screens. */
data class SessionActions(
    val onGroupWithNext: (Long) -> Unit = {},
    val onUngroup: (Long) -> Unit = {},
    val onSetAutoJump: (Long, Boolean) -> Unit = { _, _ -> },
    val onSessionNoteChange: (String) -> Unit = {},
    val onOpenRoutine: () -> Unit = {},
    val onOpenBodyweight: () -> Unit = {},
    val onOpenPlates: () -> Unit = {},
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // How far the current horizontal drag has travelled, so that a slow pull opens the routine
    // as a thrown one does. Reset when a drag starts rather than when it ends, because a drag
    // that ends off the edge of the screen never reports its finish.
    var dragged by remember { mutableFloatStateOf(0f) }
    val dragToOpen = with(LocalDensity.current) { DRAG_TO_OPEN.toPx() }

    ModalNavigationDrawer(
        drawerState = drawer,
        // **Never from the edge.** On gesture navigation a left-edge swipe is the system Back
        // gesture: an app can carve only narrow exclusion rectangles and the system takes the
        // gesture first, so a drawer that opens that way is a drawer that mostly does not open.
        // What opens this one is the handle in the top bar, or a horizontal swipe anywhere.
        gesturesEnabled = false,
        drawerContent = {
            RoutineDrawer(
                progress = state.routine,
                performed = state.exercises.associate { it.exercise.id to it.sets.size },
                onJumpTo = { exercise ->
                    exercises.onJumpTo(exercise)
                    scope.launch { drawer.close() }
                },
            )
        },
    ) {
    Scaffold(
        modifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta -> dragged += delta },
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity ->
                // Anywhere on the screen, in either direction: a phone is held in whichever hand
                // is free, and a gesture that only works from one side is one a lifter has to
                // think about. Thrown or dragged, too — a flick has velocity and a deliberate
                // pull across the screen has distance, and only counting the first leaves a slow
                // swipe doing nothing at all.
                val opening = velocity > SWIPE_TO_OPEN || dragged > dragToOpen
                val closing = velocity < -SWIPE_TO_OPEN || dragged < -dragToOpen
                if (opening) drawer.open() else if (closing) drawer.close()
            },
        ),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.session_title))
                        state.session?.let {
                            // Tappable, because a session logged the day after it happened is
                            // ordinary and the date is the only thing about it that is wrong.
                            Text(
                                text = it.date.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(
                                        // A label, because the date alone says nothing about being
                                        // tappable to anyone who cannot see where it sits.
                                        onClickLabel = stringResource(R.string.session_refile),
                                        onClick = { cycle.onStartRefiling(it) },
                                    )
                                    // Padding rather than a button: it keeps the top bar quiet
                                    // while giving the target something to be hit with.
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                },
                actions = {
                    // The handle. Tapping it is the other way in, for a lifter who would rather
                    // not swipe, or has not been told there is a swipe. In the actions rather
                    // than the navigation slot because a word does not fit in the navigation
                    // slot, and a drawer nobody can hit is a drawer that does not open.
                    TextButton(onClick = { scope.launch { drawer.open() } }) {
                        Text(stringResource(R.string.drawer_open))
                    }
                    TextButton(onClick = wholeSession.onOpenRoutine) {
                        Text(stringResource(R.string.session_open_routine))
                    }
                    // Bodyweight lives beside the routine rather than inside a session: it is
                    // recorded on mornings there is no session to put it in, and it is not
                    // training when there is.
                    TextButton(onClick = wholeSession.onOpenBodyweight) {
                        Text(stringResource(R.string.session_open_bodyweight))
                    }
                    // Where the plates live. Global equipment rather than anything about today,
                    // so it sits beside the routine and the weigh-ins rather than in a session.
                    TextButton(onClick = wholeSession.onOpenPlates) {
                        Text(stringResource(R.string.session_open_plates))
                    }
                    if (state.session != null) {
                        TextButton(onClick = cycle.onFinish) {
                            Text(stringResource(R.string.session_finish))
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Fixed, in the thumb zone, and only what belongs there: the numbers, the steppers
            // and the log button (`docs/design/logging-screen.md`). What scrolls above is the
            // session and everything about the exercise a lifter is not touching mid-set.
            if (state.selected != null) {
                // Opaque, because it is on top of a list that scrolls underneath it: a
                // transparent strip shows the session through the controls and reads as a
                // rendering fault rather than as a fixed bar.
                //
                // And it carries its own insets. The app draws edge to edge and a Scaffold insets
                // its content rather than this slot, so without them the strip sits under the
                // gesture bar — and the keyboard covers the very fields tap-to-type opens.
                // Both insets pad the contents *inside* the background, and it was worth trying
                // the other way round to find out why: padding the Surface moves the background
                // with it, so the bar stops reaching the bottom of the screen and the session
                // scrolls visibly through the gap it leaves — under the gesture bar with no
                // keyboard, and under half the screen with one. Padded inside, the bar always
                // reaches the edge and its contents sit above whatever is in the way.
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                        HorizontalDivider()
                        SetEntryThumbZone(
                            draft = state.draft,
                            rightDraft = state.rightDraft,
                            perSide = state.perSide,
                            onDraftChange = entry.onDraftChange,
                            onRightDraftChange = entry.onRightDraftChange,
                            onLogSet = entry.onLogSet,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = exercises.onPickExercise,
                text = {
                    Text(
                        stringResource(
                            if (state.selected == null) {
                                R.string.session_pick_exercise
                            } else {
                                R.string.session_change_exercise
                            },
                        ),
                    )
                },
                icon = {},
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(SESSION_LIST),
            contentPadding = insets,
        ) {
            item {
                NextUp(
                    progress = state.routine,
                    canStart = state.session == null,
                    onStartDay = cycle.onStartDay,
                    onSkipDay = cycle.onSkipDay,
                )
            }

            state.selected?.let { selected ->
                item {
                    // What is coming, what is now, and what just went — read at arm's length.
                    SetWheel(
                        exercise = selected,
                        sets = state.wheel,
                        lifterUnit = state.lifterUnit,
                        modifier = Modifier.height(WHEEL_HEIGHT),
                    )
                    // Under the prescription, where somebody standing at the bar reads it.
                    // The exercise's unit, like the weight above it: a machine labelled in
                    // pounds is labelled in pounds whoever is reading.
                    state.plateLine?.let { PlateLineRow(it, selected.displayUnit) }
                    WheelFooting(state, rest.onEndRest, rest.onAddRest, rest.onFixRestAlerts)
                    SetEntryDetails(
                        exercise = selected,
                        recommendation = state.recommendation,
                        onEditRest = rest.onEditRest,
                        onSwap = exercises.onSwap,
                        draft = state.draft,
                        rightDraft = state.rightDraft,
                        perSide = state.perSide,
                        overridingDisplayUnit = state.overridingDisplayUnit,
                        onDraftChange = entry.onDraftChange,
                        onRightDraftChange = entry.onRightDraftChange,
                        onTogglePerSide = entry.onTogglePerSide,
                        onEditExercise = exercises.onEditExercise,
                        onReadIn = entry.onReadIn,
                        onToggleDisplayUnitOverride = entry.onToggleDisplayUnitOverride,
                    )
                    HorizontalDivider()
                }
            }

            if (state.session == null) {
                item {
                    EmptySession(
                        hasSelection = state.selected != null,
                        lastFinished = state.lastFinished,
                        onStartRefiling = cycle.onStartRefiling,
                    )
                }
            }

            itemsIndexed(
                items = state.positions,
                key = { _, position -> position.exercises.first().id },
            ) { index, position ->
                SessionPositionRows(
                    position = position,
                    isLast = index == state.positions.lastIndex,
                    lifterUnit = state.lifterUnit,
                    onEditEffort = entry.onEditEffort,
                    onAddDrop = entry.onAddDrop,
                    onGroupWithNext = wholeSession.onGroupWithNext,
                    onUngroup = wholeSession.onUngroup,
                    onSetAutoJump = wholeSession.onSetAutoJump,
                )
                HorizontalDivider()
            }

            if (state.unperformed.isNotEmpty()) {
                item {
                    Unperformed(state.unperformed)
                    HorizontalDivider()
                }
            }

            state.session?.let { session ->
                item { SessionNote(session.id, session.note, wholeSession.onSessionNoteChange) }
            }
        }
    }
    }

    state.editing?.let { editing ->
        EditSetDialog(
            editing,
            entry.onEditingDraftChange,
            entry.onSaveEditing,
            entry.onDeleteEditing,
            entry.onCancelEditing,
        )
    }

    state.addingDrop?.let { drop ->
        AddDropDialog(drop, entry.onDropDraftChange, entry.onSaveDrop, entry.onCancelDrop)
    }

    if (state.finishing) {
        WhichDayDialog(state.routine, cycle.onFinishAs, cycle.onCancelFinishing)
    }

    state.refiling?.let { RefileSessionDialog(it.date, cycle.onRefileOn, cycle.onCancelRefiling) }

    state.skipping?.let { SkipDayDialog(it, cycle.onConfirmSkip, cycle.onCancelSkip) }

    state.avoided?.let {
        AvoidedPrompt(
            it,
            exercises.onKeepAvoided,
            exercises.onSwapAvoided,
            exercises.onRemoveAvoided,
        )
    }

    state.swapCandidates?.let {
        SwapSheet(
            it,
            state.swappingLeavesOnlyDisliked,
            exercises.onSwapTo,
            exercises.onPickExercise,
            exercises.onCancelSwap,
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

/**
 * What comes next in the routine, and the way past it.
 *
 * Derived from which days of the current cycle are still undone, never from the date — so this line
 * reads the same after a fortnight off as it did the evening before. The skip beside it is the
 * escape hatch a derived cursor requires, offered here because this is where a lifter reads which
 * day they are declining.
 */
@Composable
private fun NextUp(
    progress: RoutineProgress?,
    canStart: Boolean,
    onStartDay: () -> Unit,
    onSkipDay: () -> Unit,
) {
    val cursor = progress?.cursor
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = if (cursor == null) {
                    stringResource(R.string.session_no_routine)
                } else {
                    stringResource(R.string.session_next_up, cursor.name)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            progress?.cycle?.let {
                Text(
                    text = stringResource(R.string.session_cycle, it.ordinal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row {
            // Starting here is what takes the copy of the day's exercises. Logging a set without
            // it is still a session — just one that was not for anything in particular.
            if (cursor != null && canStart) {
                TextButton(onClick = onStartDay) {
                    Text(stringResource(R.string.session_start_day))
                }
            }
            // The way past a day nobody is going to train as written. Offered beside the day
            // itself, because that is the moment a lifter decides they are not doing it.
            if (cursor != null) {
                TextButton(onClick = onSkipDay) { Text(stringResource(R.string.session_skip_day)) }
            }
        }
    }
}

/**
 * What the lifter calls this exercise, what they need to remember about it, and whether they want
 * it coached at all.
 *
 * All three live apart from the bank row, so an app update that corrects what the bank ships cannot
 * reach any of them (ADR 0009).
 */
@Composable
private fun ExerciseDetailsDialog(
    details: ExerciseDetails,
    onChange: (ExerciseDetails) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.exercise_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = details.name,
                    onValueChange = { onChange(details.copy(name = it)) },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = details.note,
                    onValueChange = { onChange(details.copy(note = it)) },
                    label = { Text(stringResource(R.string.exercise_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // The lifter's word over the engine's: a rack that goes up in fours is a fact
                // about their gym, and blank hands it back to the plates or the logged stack.
                OutlinedTextField(
                    value = details.increment,
                    onValueChange = { onChange(details.copy(increment = it)) },
                    label = { Text(stringResource(R.string.exercise_increment)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The band this exercise is worked in, where the lifter wants a say in it.
                // Blank hands the question back to what they are training for, which is where it
                // normally lives: a goal seeds a band, and a goal edited later does not reach
                // back and take this one away.
                Text(
                    text = stringResource(R.string.exercise_rep_range),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = details.repRangeBottom,
                        onValueChange = { onChange(details.copy(repRangeBottom = it)) },
                        label = { Text(stringResource(R.string.exercise_rep_range_bottom)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = details.repRangeTop,
                        onValueChange = { onChange(details.copy(repRangeTop = it)) },
                        label = { Text(stringResource(R.string.exercise_rep_range_top)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                ProgressionModelChooser(details.progressionModel) {
                    onChange(details.copy(progressionModel = it))
                }
                // Global, reversible, and a statement about what to suggest rather than about
                // what was trained: everything already logged against it stays put.
                FilterChip(
                    selected = details.disliked,
                    onClick = { onChange(details.copy(disliked = !details.disliked)) },
                    label = { Text(stringResource(R.string.exercise_disliked)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.session_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * How this exercise's recommendation advances, and the lifter's right to say "not this one".
 *
 * Offered here rather than buried, because the only value that means anything before the engine
 * exists is the one that turns it off — a lift somebody does not want coached is their business, and
 * the app has no way to work that out for itself.
 */
@Composable
private fun ProgressionModelChooser(
    selected: ProgressionModel,
    onSelect: (ProgressionModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.exercise_progression_model),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressionModel.entries.forEach { model ->
                FilterChip(
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    label = { Text(stringResource(model.label)) },
                )
            }
        }
    }
}

/**
 * Whether to deal with a day without training it.
 *
 * Asked rather than done, because nothing unskips a day: the record stands for that cycle, and the
 * point of recording skips at all is that repeated ones say something about the routine.
 */
@Composable
private fun SkipDayDialog(day: RoutineDay, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_skip_title, day.name)) },
        text = { Text(stringResource(R.string.session_skip_explain)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.session_skip_day)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Which day this session should be filed on.
 *
 * The picker itself is shared, because reading a calendar day out of it correctly is one rule and
 * the session is not the only thing filed on a day — see [DayPickerDialog].
 */
@Composable
private fun RefileSessionDialog(
    date: LocalDate,
    onRefileOn: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    DayPickerDialog(date = date, onPick = onRefileOn, onDismiss = onCancel)
}

/**
 * What the screen says with nothing being logged — and the way back to what was just logged.
 *
 * [lastFinished] is offered for one reason: a wrong date is usually noticed after pressing Finish,
 * and without this the only way to correct one would be a history screen that does not exist yet.
 * One session, not a list. This is a way back to the last thing done, not a place to browse.
 */
@Composable
private fun EmptySession(
    hasSelection: Boolean,
    lastFinished: LoggedSession?,
    onStartRefiling: (Session) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (hasSelection) R.string.session_empty_selected else R.string.session_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastFinished?.let { logged ->
            Text(
                text = stringResource(R.string.session_last, logged.session.date.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.session_refile),
                        onClick = { onStartRefiling(logged.session) },
                    )
                    .padding(vertical = 4.dp),
            )
            // How much of what it was for got done — the question the plan exists to answer, and
            // the one a lifter asks the moment a session is over.
            if (!logged.plan.isEmpty) {
                val (done, planned) = logged.plan.progress(logged.exercises)
                Text(
                    text = stringResource(R.string.session_plan_progress, done, planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Everything about the exercise in front of the lifter that is not the numbers.
 *
 * Scrolls with the session. What is *not* here is the point: the fields, the steppers and the log
 * button sit in [SetEntryThumbZone], fixed below, because those are what a thumb finds without
 * looking and a control that moves between sets is one you have to look at
 * (`docs/design/logging-screen.md`).
 */
@Composable
private fun SetEntryDetails(
    exercise: Exercise,
    recommendation: Recommendation?,
    onEditRest: () -> Unit,
    onSwap: () -> Unit,
    draft: SetDraft,
    rightDraft: SetDraft,
    perSide: Boolean,
    overridingDisplayUnit: Boolean,
    onDraftChange: (SetDraft) -> Unit,
    onRightDraftChange: (SetDraft) -> Unit,
    onTogglePerSide: () -> Unit,
    onEditExercise: () -> Unit,
    onReadIn: (WeightUnit) -> Unit,
    onToggleDisplayUnitOverride: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Row {
                // Beside the exercise it would replace, because that is where a lifter is
                // standing when they find the machine occupied.
                TextButton(onClick = onSwap) { Text(stringResource(R.string.swap_open)) }
                TextButton(onClick = onEditExercise) {
                    Text(stringResource(R.string.exercise_edit))
                }
            }
        }

        // In front of the lifter every time they train it, which is the whole point of writing it
        // down: "seat notch 3, use the long bar" is no use on a screen nobody opens in a gym.
        exercise.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The one part of a prescription a lifter changes rather than answers — and changes once,
        // which is why it is here rather than under their thumb.
        recommendation?.prescription?.rest?.let { rest ->
            TextButton(onClick = onEditRest, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.session_rest, rest.asTimeText()))
            }
        }

        // Labelled only where there are two of them, and then both: an unlabelled block above
        // one headed "Right" reads as a heading somebody forgot rather than as the left side.
        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_left),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetDetailFields(
            draft,
            onDraftChange,
            // Here a unit is a statement about the machine, so it settles the exercise — unless
            // the lifter says this one set is the exception.
            unitChoice = UnitChoice(
                onReadIn = onReadIn,
                overriding = overridingDisplayUnit,
                onToggleOverride = onToggleDisplayUnitOverride,
            ),
        )

        // One set, felt one way: what the lifter measured is taken from the left-hand side, so
        // the right side carries nothing but a note of its own.
        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_right),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SetDetailFields(
                rightDraft,
                onRightDraftChange,
                showType = false,
                showMeasurements = false,
                unitChoice = null,
            )
        }

        // Offered only where it means something. An exercise trained one side at a time is the one
        // a lifter expands to track an imbalance; a barbell bench press has no sides to separate.
        if (exercise.unilateral) {
            FilterChip(
                selected = perSide,
                onClick = onTogglePerSide,
                label = { Text(stringResource(R.string.set_per_side)) },
            )
        }
    }
}

/**
 * The controls a thumb acts on, fixed below the wheel.
 *
 * **The whole of the thumb zone, and nothing else.** The design settled on the value fields,
 * their steppers and the log button — a lifter mid-set reaches for these with a bar in the other
 * hand, and they have to be in the same place every time. Everything else about the exercise
 * scrolls in [SetEntryDetails] above.
 *
 * The running rest and the pace line stay up with the wheel rather than appearing here, so that
 * a rest starting and ending does not move the log button under the lifter's thumb. It is not
 * fixed in height outright — a per-side exercise shows two of everything, and an exercise that
 * records a duration grows a line under the field — but neither of those changes between the
 * sets of one exercise, which is when a control moving is a control you have to look at.
 */
@Composable
private fun SetEntryThumbZone(
    draft: SetDraft,
    rightDraft: SetDraft,
    perSide: Boolean,
    onDraftChange: (SetDraft) -> Unit,
    onRightDraftChange: (SetDraft) -> Unit,
    onLogSet: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_left),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetValueFields(draft, onDraftChange)

        // A single entry by default; expanding is for tracking an imbalance, and the two sides
        // carry their own weights because that is the only reason to bother.
        if (perSide) {
            Text(
                text = stringResource(R.string.set_side_right),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SetValueFields(rightDraft, onRightDraftChange)
        }

        Button(
            onClick = onLogSet,
            enabled = draft.isLoggable && (!perSide || rightDraft.isLoggable),
            modifier = Modifier.fillMaxWidth().testTag(LOG_SET),
        ) {
            Text(stringResource(R.string.session_log_set))
        }
    }
}

/**
 * What to do next: the weight and the rep count the log button will record, and the one line of
 * explanation that some of what the engine does cannot go without.
 *
 * **In the normal state the screen says nothing about itself.** No badge, no hint, no label: the
 * previous set is already printed under every row below, and a line repeating that the number came
 * from it is noise (`docs/design/logging-screen.md`). Three things break that silence, and each
 * earns it — the app guessing, the app cutting the work rather than the weight, and the app having
 * stopped. All three are cases where the number alone would be read as something it is not.
 *
 * The value is written in the same shape as the logged sets below it, so accepting a recommendation
 * and reading back what was accepted look like the same thing rather than two different screens'
 * vocabulary. Read in the unit the fields are in — the machine's own, which is what the lifter is
 * looking at.
 */
@Composable
internal fun NextSet(
    recommendation: Recommendation,
    loggingType: LoggingType,
    displayUnit: WeightUnit,
) {
    val reps = recommendation.prescription.reps
    val asked = loggingType.reading(recommendation.prescription.load)
    val unit = stringResource(displayUnit.label)

    // Tight together rather than spaced as three things: the word, the number and the line under
    // it are one message, and the number is the part read from the rack.
    val repRange = when (recommendation) {
        is Recommendation.Prescribing -> recommendation.repRange
        is Recommendation.CuttingVolume -> recommendation.repRange
        // Neither a ramp rung nor a set the app is guessing at is progressed, so neither has a
        // top to reach — and a band on one would invite a lifter to chase it.
        is Recommendation.WarmingUp, is Recommendation.Calibrating, Recommendation.Stopped -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // What kind of set this is, where it is not the ordinary one. A lifter looking at a
        // light first set needs to know the app meant it.
        val kind = when (recommendation) {
            is Recommendation.Calibrating -> R.string.session_calibrating
            is Recommendation.WarmingUp -> R.string.set_type_warmup
            is Recommendation.Prescribing, is Recommendation.CuttingVolume,
            Recommendation.Stopped,
            -> null
        }
        kind?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The band, small and above the number: it reads top to bottom as what to stay inside,
        // what to go and do, and then what the set before this one did. A warm-up and a ramp rung
        // get no band at all, because neither is progressed and neither has a top to reach.
        repRange?.let {
            Text(
                text = stringResource(R.string.wheel_range, it.bottom, it.top),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reps != null) {
            Text(
                text = when (asked) {
                    is PrescribedLoad.Weight -> stringResource(
                        R.string.set_summary,
                        asked.weight.asTextIn(displayUnit),
                        unit,
                        reps,
                    )

                    is PrescribedLoad.Belt -> stringResource(
                        R.string.set_added_weight_summary,
                        asked.added.asTextIn(displayUnit),
                        unit,
                        reps,
                    )

                    // Written as the machine is set. The minus sign in front of it is the screen
                    // saying which way it counts, not a negative weight.
                    is PrescribedLoad.Help -> stringResource(
                        R.string.set_assisted_summary,
                        asked.assistance.asTextIn(displayUnit),
                        unit,
                        reps,
                    )

                    // A pull-up with no belt asks for reps and nothing else, which is the ordinary
                    // case on every exercise whose load is the lifter's own body.
                    PrescribedLoad.None -> stringResource(R.string.set_reps_summary, reps)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        // What the number cannot say by itself.
        val line = when (recommendation) {
            // That the app expects to be wrong, and what to do about it. Without it the word
            // above is the data model talking.
            is Recommendation.Calibrating -> R.string.session_calibrating_hint
            // That a weight which has not moved is a decision rather than nothing happening.
            is Recommendation.CuttingVolume -> R.string.session_cutting_volume
            // That there is no number because the app has stopped guessing, rather than because
            // it has nothing to say about this lift at all.
            Recommendation.Stopped -> R.string.session_stopped
            // A rung of a ramp says what it is above and needs no explaining under.
            is Recommendation.WarmingUp -> null
            // The two things a prescription says about itself, both of them cases where the
            // number alone would be read as something it is not. A weight that jumped reads as a
            // bug, and the safe thing to do about a bug is load what you lifted last time (#25).
            // A weight that has *not* moved after two good sessions reads as the engine having
            // given up, when what has actually happened is that the app has run out of pin
            // positions it has seen and needs the lifter to show it one more (#65).
            is Recommendation.Prescribing -> when {
                recommendation.reAnchored -> R.string.session_re_anchored
                recommendation.atTheTopOfTheStack -> R.string.session_top_of_stack
                else -> null
            }
        }
        line?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The second thing the screen breaks its silence for. Hypertrophy is load-independent
        // above about a third of a maximum *only if the effort is high*, so a set of fifteen
        // prescribed without saying so is not the intervention the evidence supports. It keys off
        // the top of the band rather than the number offered: a 12 - 15 set is asking for 15.
        if (repRange != null && repRange.top >= REPS_THAT_NEED_EFFORT) {
            Text(
                text = stringResource(R.string.wheel_effort),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The top of a rep range at which the prescription has to say that effort is the point.
 *
 * Not a tuning knob so much as where the evidence changes shape: below it, load does the work.
 */
private const val REPS_THAT_NEED_EFFORT = 15

/**
 * How hard a horizontal swipe has to be thrown to open the routine.
 *
 * A velocity rather than a distance, so that a deliberate flick opens it and a thumb drifting
 * sideways while reading a weight does not.
 */
private const val SWIPE_TO_OPEN = 300f

/**
 * How far a horizontal drag has to travel to open the routine.
 *
 * The other half of the gesture. A lifter mid-session drags as often as they flick — a thumb
 * already resting on the screen has no velocity to give — and a swipe that only answers to speed
 * is one that does nothing at all about half the time it is tried.
 */
private val DRAG_TO_OPEN = 72.dp

/**
 * The question the app asks about an exercise a lifter keeps not doing.
 *
 * **Keeping it is as prominent as swapping or removing it**, and that is the whole design rather
 * than a courtesy: people skip squats because squats are hard, not because they dislike them, and
 * a prompt that made "remove" the easy answer would quietly strip the most valuable work out of a
 * programme. The app noticed; the lifter decides, and nothing happens until they do.
 */
@Composable
private fun AvoidedPrompt(
    avoided: Avoided,
    onKeep: (Avoided) -> Unit,
    onSwap: (Avoided) -> Unit,
    onRemove: (Avoided) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onKeep(avoided) },
        title = { Text(avoided.exercise.name) },
        text = {
            Text(
                stringResource(
                    R.string.avoided_question,
                    avoided.unperformed,
                    avoided.asked,
                ),
            )
        },
        // Three answers, side by side and the same size as each other.
        confirmButton = {
            Row {
                TextButton(onClick = { onKeep(avoided) }) {
                    Text(stringResource(R.string.avoided_keep))
                }
                TextButton(onClick = { onSwap(avoided) }) {
                    Text(stringResource(R.string.avoided_swap))
                }
                TextButton(onClick = { onRemove(avoided) }) {
                    Text(stringResource(R.string.avoided_remove))
                }
            }
        },
    )
}

/**
 * What to train instead, because the machine is busy.
 *
 * The suggestions are the app's best guesses, and the two ways out of them get equal room:
 * searching the whole bank, and adding an exercise the app has never heard of. Nobody is ever
 * stuck with a list.
 */
@Composable
private fun SwapSheet(
    candidates: List<Exercise>,
    onlyDislikedLeft: Boolean,
    onSwapTo: (Exercise) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.swap_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // When the bans leave no way to train the muscle, the app asks which is
                // least bad. Silently including a banned exercise would break the promise the
                // ban makes, and silently skipping the muscle would leave a hole in the training
                // that nobody chose.
                if (onlyDislikedLeft) {
                    Text(stringResource(R.string.swap_only_disliked))
                }
                if (candidates.isEmpty()) {
                    // A real answer rather than an empty box: the gym checklist may be narrow, or
                    // this movement may have no close relatives in the bank.
                    Text(stringResource(R.string.swap_nothing))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = Exercise::id) { candidate ->
                        TextButton(onClick = { onSwapTo(candidate) }) { Text(candidate.name) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch) { Text(stringResource(R.string.swap_search)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * How long to rest, and for how long that answer holds.
 *
 * Two confirmations rather than one, because "longer today" and "longer on this machine from now
 * on" are different things and the app cannot tell which one a lifter means. Neither is a setting
 * buried anywhere: both are one tap from the set being entered.
 */
@Composable
private fun RestDialog(
    rest: Duration,
    onChange: (Duration) -> Unit,
    onThisSet: () -> Unit,
    onAlways: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_rest_title)) },
        text = {
            OutlinedTextField(
                value = rest.seconds.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { typed ->
                    onChange(Duration.ofSeconds(typed.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0))
                },
                label = { Text(stringResource(R.string.set_rest_taken)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onThisSet) { Text(stringResource(R.string.session_rest_this_set)) }
        },
        dismissButton = {
            TextButton(onClick = onAlways) { Text(stringResource(R.string.session_rest_always)) }
        },
    )
}

/**
 * The line under the wheel: where the lifter is, how long is left, and any rest that is running.
 *
 * Both halves of "where" together — "set 4 · working 2" — because a ramp of three warm-ups and one
 * working set is not four sets of training, and a screen that counts them as four makes it look
 * like one. Totals appear only where something has actually planned them; inventing one would be
 * stating a plan nobody made.
 */
/**
 * What a lifter can be sent to fix, when a rest cannot reach them.
 *
 * Both are permissions the app is allowed to ask for and neither is one it can grant itself, so
 * what the screen offers is the trip to the place that can.
 */
enum class RestFix { EXACT_ALARMS, NOTIFICATIONS }

/** A silent failure said out loud, with the one tap that fixes it. */
@Composable
private fun RestAlertWarning(
    @StringRes said: Int,
    @StringRes fix: Int,
    onFix: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(said),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onFix, contentPadding = PaddingValues(vertical = 4.dp)) {
            Text(stringResource(fix))
        }
    }
}

/**
 * How long the session has been running, re-read each time the minute turns over.
 *
 * **Minutes and never seconds.** A lifter reading this is deciding whether they have time to
 * finish before something else, which is not a question asked to the second — and a seconds place
 * ticking in the corner of a screen whose whole design is to say as little as possible about
 * itself would be the noisiest thing on it.
 *
 * The wake-up is timed to the next whole minute rather than polled on a loop, so the number
 * changes at the moment it becomes wrong rather than up to a minute afterwards.
 */
@Composable
private fun elapsedText(startedAt: Instant): String {
    val minutes by produceState(minutesSince(startedAt), startedAt) {
        while (true) {
            val running = Duration.between(startedAt, Instant.now()).toMillis()
            delay(MILLIS_PER_MINUTE - running.mod(MILLIS_PER_MINUTE))
            value = minutesSince(startedAt)
        }
    }
    return if (minutes >= MINUTES_PER_HOUR) {
        stringResource(
            R.string.wheel_elapsed_hours,
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR,
        )
    } else {
        stringResource(R.string.wheel_elapsed_minutes, minutes)
    }
}

/**
 * Whole minutes since [startedAt], and never fewer than none.
 *
 * The floor is for a session refiled onto a date in the future and for a phone whose clock has
 * moved under it. Neither is worth a branch on screen, and "-3 min so far" is worse than nothing.
 */
private fun minutesSince(startedAt: Instant): Long =
    Duration.between(startedAt, Instant.now()).toMinutes().coerceAtLeast(0)

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/**
 * How to load the bar, under the weight it makes.
 *
 * **Always visible and never interactive.** A lifter reads it and loads the bar; there is nothing
 * to tap, because it is not a choice — it is the rounding the engine already did, shown. The
 * numbers come from the same inventory the rounding read, so the line always adds up to the
 * weight above it.
 */
@Composable
private fun PlateLineRow(line: PlateLine, unit: WeightUnit) {
    val label = stringResource(unit.label)
    Text(
        text = if (line.perSide.isEmpty()) {
            stringResource(R.string.wheel_bar_only, line.bar.asTextIn(unit), label)
        } else {
            stringResource(
                R.string.wheel_plate_line,
                line.bar.asTextIn(unit),
                label,
                line.perSide.joinToString { it.asTextIn(unit) },
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WheelFooting(
    state: SessionUiState,
    onEndRest: () -> Unit,
    onAddRest: () -> Unit,
    onFixRestAlerts: (RestFix) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Totals only where something actually asked for them. A session trained with no routine
        // says where the lifter is and claims no plan, because there is not one.
        state.pace?.let { pace ->
            Text(
                text = if (pace.setsPlanned != null && pace.workingPlanned != null) {
                    stringResource(
                        R.string.wheel_pace_planned,
                        pace.setsDone,
                        pace.setsPlanned,
                        pace.workingDone,
                        pace.workingPlanned,
                    )
                } else {
                    stringResource(R.string.wheel_pace, pace.setsDone, pace.workingDone)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Half of "how much longer am I here", and the estimate beside it is the other half:
            // neither answers it alone. Counted from the session's own start rather than from the
            // first set, so a treadmill before the weights is inside it — the question being
            // asked is about time in the gym rather than time under a bar.
            state.session?.let {
                Text(
                    text = elapsedText(it.startedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The tilde is the point: it reads as an estimate rather than a deadline, which is what
            // made it acceptable to look at mid-session rather than stressful.
            state.timeLeft?.let {
                Text(
                    text = stringResource(R.string.wheel_time_left, it.asTimeText()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // A rest that is running, and the way out of it. What reaches a lifter with the phone in a
    // pocket is the alarm (#57); this is the session saying one is going.
    // Said where a lifter will read it, because both failures below are silent: an alarm that
    // never fires and a countdown nobody was allowed to draw are each discovered by missing a
    // set rather than by an error. Each carries the way out of it, because telling somebody to
    // go to Settings without taking them there is half a sentence.
    if (!state.restAlarmsAreReliable) {
        RestAlertWarning(R.string.rest_unreliable, R.string.rest_allow_alarms) {
            onFixRestAlerts(RestFix.EXACT_ALARMS)
        }
    }

    if (!state.restCountdownVisible) {
        RestAlertWarning(R.string.rest_countdown_hidden, R.string.rest_allow_notifications) {
            onFixRestAlerts(RestFix.NOTIFICATIONS)
        }
    }

    state.resting?.let {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wheel_resting, it.length.asTimeText()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row {
                // The timer bends to the session rather than the other way round.
                TextButton(onClick = onAddRest) { Text(stringResource(R.string.rest_add_time)) }
                TextButton(onClick = onEndRest) { Text(stringResource(R.string.wheel_skip_rest)) }
            }
        }
    }
}

/**
 * What choosing a unit beside a weight means.
 *
 * Correcting or continuing a set changes that set alone, which is [forThisSetAlone] and the
 * default. The entry controls pass their own, because a unit chosen while entering is a statement
 * about the machine and is remembered for the exercise.
 */
private data class UnitChoice(
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
private fun SetValueFields(draft: SetDraft, onChange: (SetDraft) -> Unit) {
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
private fun SetDetailFields(
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
private fun SetDraftFields(
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
private fun WeightSteppers(draft: SetDraft, onChange: (SetDraft) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { onChange(draft.stepWeight(-1)) }) {
            Text(stringResource(R.string.set_weight_down, draft.displayUnit.step.asAmountText()))
        }
        OutlinedButton(onClick = { onChange(draft.stepWeight(1)) }) {
            Text(stringResource(R.string.set_weight_up, draft.displayUnit.step.asAmountText()))
        }
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
private fun UnitChoiceRow(draft: SetDraft, choice: UnitChoice) {
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
private fun SetTypeChooser(selected: SetType, onSelect: (SetType) -> Unit) {
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

/**
 * What this session was for and did not get to.
 *
 * The whole reason a plan is recorded: without this list an exercise nobody trained reads exactly
 * like one nobody prescribed, and a partial session cannot be told from a complete one.
 */
@Composable
private fun Unperformed(exercises: List<Exercise>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.session_unperformed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        exercises.forEach { exercise ->
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * One position in the session: an exercise on its own, or a superset and its members.
 *
 * A group is drawn as one thing with its members inside it, because that is what it is — two or
 * more exercises alternated with no rest between them, resting only after the group.
 */
@Composable
private fun SessionPositionRows(
    position: SessionPosition,
    isLast: Boolean,
    /** What the lifter reads in, which is what a distance among these rows is written in. */
    lifterUnit: WeightUnit,
    onEditEffort: (SessionExercise, PerformedSet, Effort) -> Unit,
    onAddDrop: (WeightUnit, Effort) -> Unit,
    onGroupWithNext: (Long) -> Unit,
    onUngroup: (Long) -> Unit,
    onSetAutoJump: (Long, Boolean) -> Unit,
) {
    Column {
        if (position is SessionPosition.Grouped) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_superset, position.exercises.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { onUngroup(position.superset.id) }) {
                    Text(stringResource(R.string.session_ungroup))
                }
            }
            // A real choice rather than an assumption: alternating without being asked is the
            // point for most lifters, and two machines at opposite ends of a gym is where it is
            // not.
            FilterChip(
                selected = position.superset.autoJump,
                onClick = { onSetAutoJump(position.superset.id, !position.superset.autoJump) },
                label = { Text(stringResource(R.string.session_auto_jump)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        position.exercises.forEach { performed ->
            LoggedExercise(performed, lifterUnit, onEditEffort, onAddDrop)
        }

        // Offered on the last exercise of the position, because grouping reaches forward: this is
        // the one with something after it to alternate with.
        if (!isLast) {
            TextButton(
                onClick = { onGroupWithNext(position.exercises.last().id) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.session_group_with_next))
            }
        }
    }
}

@Composable
private fun LoggedExercise(
    performed: SessionExercise,
    lifterUnit: WeightUnit,
    onEditEffort: (SessionExercise, PerformedSet, Effort) -> Unit,
    onAddDrop: (WeightUnit, Effort) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(performed.exercise.name, style = MaterialTheme.typography.titleMedium)
        performed.exercise.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        performed.sets.forEachIndexed { position, set ->
            LoggedSetRows(
                // Numbered at render rather than by the stored set index, per ADR 0003: the index
                // is identity and tolerates gaps, while the number a lifter reads should just count
                // the sets in front of them.
                number = position + 1,
                set = set,
                // The exercise's unit, unless this set was read off some other machine.
                displayUnit = performed.displayUnitFor(set),
                lifterUnit = lifterUnit,
                onEditEffort = { effort -> onEditEffort(performed, set, effort) },
                onAddDrop = onAddDrop,
            )
        }
    }
}

/**
 * One set, however many rows recorded it.
 *
 * A per-side set shows both efforts under one number, and drops are indented under the effort they
 * continue — so what reads as one set on screen is one set in the log.
 */
@Composable
private fun LoggedSetRows(
    number: Int,
    set: PerformedSet,
    displayUnit: WeightUnit,
    lifterUnit: WeightUnit,
    onEditEffort: (Effort) -> Unit,
    onAddDrop: (WeightUnit, Effort) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        set.efforts.forEachIndexed { index, effort ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditEffort(effort) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (index == 0) number.toString() else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // A fixed width so the second side of a per-side set lines up under the first
                    // rather than sliding left into the space the number would have taken.
                    modifier = Modifier.width(20.dp),
                )
                effort.side?.let {
                    Text(
                        text = stringResource(it.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(effort.summary(displayUnit, lifterUnit), style = MaterialTheme.typography.bodyLarge)
                if (index == 0 && set.type != SetType.WORKING) {
                    Text(
                        text = stringResource(set.type.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                effort.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // A drop is part of the set it continues, so it is read in the set's unit.
            effort.drops.forEach { drop -> DropRow(drop, displayUnit) }

            // A Drop is a continuation at reduced weight, so it is offered against the effort in
            // hand rather than against what its exercise records today: an exercise retagged by a
            // bank update must not change what a set logged last year was.
            if (effort.performance is Performance.WeightAndReps) {
                TextButton(onClick = { onAddDrop(displayUnit, effort) }) {
                    Text(stringResource(R.string.set_add_drop))
                }
            }
        }
    }
}

@Composable
private fun DropRow(drop: Drop, displayUnit: WeightUnit) {
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

/**
 * The session note.
 *
 * The field owns its own text and writes through, rather than rendering what has come back from the
 * database: a value that arrives an asynchronous write later drops and reorders characters as fast
 * as they are typed.
 */
@Composable
private fun SessionNote(sessionId: Long, stored: String?, onChange: (String) -> Unit) {
    var text by rememberSaveable(sessionId) { mutableStateOf(stored.orEmpty()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(stringResource(R.string.session_note)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

@Composable
private fun EditSetDialog(
    editing: EditingEffort,
    onChange: (SetDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_edit_set)) },
        text = { SetDraftFields(editing.draft, onChange) },
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
private fun AddDropDialog(
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

/**
 * Asks which routine day the finishing session realised, defaulting to nothing in particular.
 *
 * The cursor is offered first but any day can be chosen, because training out of order is ordinary
 * and the cursor holding its place afterwards is the whole point of deriving it.
 */
@Composable
private fun WhichDayDialog(
    progress: RoutineProgress?,
    onFinishAs: (RoutineDay?) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.session_which_day)) },
        text = {
            Column {
                progress?.routine?.rotation.orEmpty().forEach { day ->
                    TextButton(onClick = { onFinishAs(day) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (progress?.cursor?.id == day.id) {
                                "${day.name}  ·  ${stringResource(R.string.routine_next_up)}"
                            } else {
                                day.name
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFinishAs(null) }) {
                Text(stringResource(R.string.session_no_day))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
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

/**
 * Where to send a lifter to let a rest reach them.
 *
 * Settings rather than a second permission dialog: the alarm one has no dialog at all, and the
 * notification one is shown at most once by the system, after which the only honest route is the
 * page that can still change the answer.
 */
private fun settingsFor(fix: RestFix, context: Context): Intent = when (fix) {
    RestFix.EXACT_ALARMS -> Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    )

    RestFix.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
