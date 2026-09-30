package com.tylergimpel.gymtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.ui.BottomNav
import com.tylergimpel.gymtracker.ui.Tab
import com.tylergimpel.gymtracker.ui.bank.ExerciseBankScreen
import com.tylergimpel.gymtracker.ui.bodyweight.BodyweightScreen
import com.tylergimpel.gymtracker.ui.calendar.CalendarScreen
import com.tylergimpel.gymtracker.ui.calendar.DayScreen
import com.tylergimpel.gymtracker.ui.history.ExerciseHistoryScreen
import com.tylergimpel.gymtracker.ui.more.MoreScreen
import com.tylergimpel.gymtracker.ui.plates.PlatesScreen
import com.tylergimpel.gymtracker.ui.profile.ProfileScreen
import com.tylergimpel.gymtracker.ui.routine.RoutineScreen
import com.tylergimpel.gymtracker.ui.routine.RoutineViewModel
import com.tylergimpel.gymtracker.ui.session.SessionScreen
import com.tylergimpel.gymtracker.ui.session.SessionViewModel
import com.tylergimpel.gymtracker.ui.settings.ExcludedScreen
import com.tylergimpel.gymtracker.ui.settings.SettingsScreen
import com.tylergimpel.gymtracker.ui.theme.GymTrackerTheme
import com.tylergimpel.gymtracker.ui.today.TodayScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val themes = GymDatabase.getInstance(this).profileRepository().observeTheme()
        setContent {
            // Following the phone until the lifter says otherwise, which is also what shows for
            // the moment before the stored choice has been read.
            val theme by themes.collectAsStateWithLifecycle(initialValue = Theme.SYSTEM)
            val dark = when (theme) {
                Theme.SYSTEM -> isSystemInDarkTheme()
                Theme.LIGHT -> false
                Theme.DARK -> true
            }
            // The status bar's icons follow the app's theme rather than the phone's, or a lifter who
            // picked light on a dark phone gets white icons on the page right under them.
            DisposableEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            GymTrackerTheme(darkTheme = dark) {
                GymTracker()
            }
        }
    }
}

private enum class Destination {
    TODAY, CALENDAR, ROUTINE, MORE,
    WORKOUT, EXERCISE_PICKER, EXERCISES, BODYWEIGHT, PLATES, PROFILE, SETTINGS, EXCLUDED, EXERCISE_HISTORY, DAY,
}

/** The destination each tab opens on. */
private val Tab.root: Destination
    get() = when (this) {
        Tab.TODAY -> Destination.TODAY
        Tab.CALENDAR -> Destination.CALENDAR
        Tab.ROUTINE -> Destination.ROUTINE
        Tab.MORE -> Destination.MORE
    }

/**
 * The tabs, the destinations under them, and a back stack of them, kept by hand.
 *
 * **Today is always at the bottom** (#141): a tab is laid on top of it, so Back from any tab comes
 * home, and only Back on Today leaves the app. Opening a tab starts it afresh rather than returning
 * to wherever it was left, because a tab tapped is a place asked for. The nav shows on a tab's own
 * screen and nowhere deeper, and never over the workout, which is the whole screen.
 *
 * A navigation library would still be more machinery than that needs.
 */
@Composable
private fun GymTracker(
    viewModel: SessionViewModel = viewModel(),
    routineViewModel: RoutineViewModel = viewModel(),
) {
    // Saved as names, which a Bundle holds without question, and never empty.
    var stack by rememberSaveable { mutableStateOf(listOf(Destination.TODAY.name)) }
    val destination = Destination.valueOf(stack.last())

    // Which routine day is being filled, if the bank was opened to write down a machine for one.
    // The bank screen is the same either way; only where the new exercise lands differs.
    var pickingForDay by rememberSaveable { mutableStateOf<Long?>(null) }

    // Whose history is open (#121). Beside the stack rather than in it, like the day being picked for.
    var historyOf by rememberSaveable { mutableStateOf<String?>(null) }

    // Which day the calendar opened (#118), as ISO text a Bundle holds without question.
    var dayOpen by rememberSaveable { mutableStateOf<String?>(null) }

    // Whether Settings was opened to import, from the first screen, rather than to look around.
    var importOnArrival by rememberSaveable { mutableStateOf(false) }

    val navigator = object : Navigator {
        override fun open(next: Destination) {
            stack = stack + next.name
        }

        override fun back() {
            if (stack.size > 1) stack = stack.dropLast(1)
        }

        override fun openTab(tab: Tab) {
            stack = if (tab == Tab.TODAY) {
                listOf(Destination.TODAY.name)
            } else {
                listOf(Destination.TODAY.name, tab.root.name)
            }
        }

        override fun openHistory(exerciseId: String) {
            historyOf = exerciseId
            open(Destination.EXERCISE_HISTORY)
        }

        override fun openDay(day: LocalDate) {
            dayOpen = day.toString()
            open(Destination.DAY)
        }

        override fun pickFor(dayId: Long?) {
            pickingForDay = dayId
            open(Destination.EXERCISE_PICKER)
        }

        override fun leavePicker() {
            pickingForDay = null
            back()
        }

        override fun openImport() {
            importOnArrival = true
            open(Destination.SETTINGS)
        }
    }

    // The tab a destination is under: the one laid on Today, or Today itself.
    val tab = stack.getOrNull(1)?.let(Destination::valueOf)
        ?.let { under -> Tab.entries.firstOrNull { it.root == under } }
        ?: Tab.TODAY
    val atTabRoot = destination == tab.root

    if (destination != Destination.TODAY) {
        BackHandler {
            if (destination == Destination.EXERCISE_PICKER) navigator.leavePicker() else navigator.back()
        }
    }

    val defaultRoutineName = stringResource(R.string.routine_default_name)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .weight(1f)
                // The nav pads itself clear of the gesture bar, so the screen above it must not too.
                .let { if (atTabRoot) it.consumeWindowInsets(WindowInsets.navigationBars) else it },
        ) {
            when (destination) {
                Destination.TODAY -> TodayScreen(
                    onStart = {
                        viewModel.startCursorDay()
                        navigator.open(Destination.WORKOUT)
                    },
                    onResume = { navigator.open(Destination.WORKOUT) },
                    // No routine, or nothing in it: the session starts with the first set logged.
                    onJustLog = { navigator.open(Destination.WORKOUT) },
                    // By hand is the same mechanism from an empty start: a named routine with no
                    // days, and the editor to grow it in.
                    onBuildRoutine = {
                        routineViewModel.createRoutine(defaultRoutineName)
                        navigator.openTab(Tab.ROUTINE)
                    },
                    onLetTheAppBuild = { navigator.openTab(Tab.ROUTINE) },
                    // Straight into the file picker, from the same flow Settings offers, so
                    // importing is one tap and its outcome is said where the rest of the lifter's
                    // data is.
                    onImportFitNotes = navigator::openImport,
                    onOpenDay = navigator::openDay,
                )

                Destination.WORKOUT -> SessionScreen(
                    onPickExercise = { navigator.pickFor(null) },
                    onOpenHistory = navigator::openHistory,
                    onLeave = navigator::back,
                    onFinished = navigator::back,
                    viewModel = viewModel,
                )

                Destination.EXERCISE_PICKER -> ExerciseBankScreen(
                    onSelect = { exercise ->
                        val day = pickingForDay
                        if (day == null) viewModel.select(exercise)
                        else routineViewModel.pickInto(day, exercise)
                        navigator.leavePicker()
                    },
                )

                Destination.EXERCISES -> ExerciseBankScreen(onSelect = { navigator.openHistory(it.id) })

                Destination.MORE -> MoreScreen(
                    onOpenExercises = { navigator.open(Destination.EXERCISES) },
                    onOpenBodyweight = { navigator.open(Destination.BODYWEIGHT) },
                    onOpenPlates = { navigator.open(Destination.PLATES) },
                    onOpenProfile = { navigator.open(Destination.PROFILE) },
                    onOpenSettings = { navigator.open(Destination.SETTINGS) },
                )

                Destination.BODYWEIGHT -> BodyweightScreen(onBack = navigator::back)

                Destination.PLATES -> PlatesScreen(onBack = navigator::back)

                Destination.PROFILE -> ProfileScreen(
                    onBack = navigator::back,
                    onOpenBodyweight = { navigator.open(Destination.BODYWEIGHT) },
                )

                Destination.SETTINGS -> SettingsScreen(
                    onBack = navigator::back,
                    importOnArrival = importOnArrival,
                    onImportStarted = { importOnArrival = false },
                    onOpenPlates = { navigator.open(Destination.PLATES) },
                    onOpenProfile = { navigator.open(Destination.PROFILE) },
                    onOpenExcluded = { navigator.open(Destination.EXCLUDED) },
                )

                Destination.EXCLUDED -> ExcludedScreen(
                    onBack = navigator::back,
                    onOpenHistory = { navigator.openHistory(it.id) },
                )

                Destination.EXERCISE_HISTORY -> historyOf?.let {
                    ExerciseHistoryScreen(exerciseId = it, onBack = navigator::back)
                }

                Destination.CALENDAR -> CalendarScreen(onBack = navigator::back, onOpenDay = navigator::openDay)

                Destination.DAY -> dayOpen?.let {
                    DayScreen(
                        date = LocalDate.parse(it),
                        onBack = navigator::back,
                        onOpenHistory = { exercise -> navigator.openHistory(exercise.id) },
                    )
                }

                Destination.ROUTINE -> RoutineScreen(
                    onBack = navigator::back,
                    // The only reason left to leave the routine: writing down a machine the app
                    // has never heard of, which is the one form in the app that does that. The day
                    // comes with them, so what they add lands where they were putting it.
                    onAddNewExercise = navigator::pickFor,
                    viewModel = routineViewModel,
                )
            }
        }
        if (atTabRoot) BottomNav(selected = tab, onSelect = navigator::openTab)
    }
}

/** Every way one screen leads to another. */
private interface Navigator {
    fun open(next: Destination)
    fun back()
    fun openTab(tab: Tab)
    fun openHistory(exerciseId: String)
    fun openDay(day: LocalDate)

    /** The bank, to add an exercise to [dayId] of the routine, or to the session where it is null. */
    fun pickFor(dayId: Long?)
    fun leavePicker()

    /** Settings, straight into the FitNotes file picker. */
    fun openImport()
}
