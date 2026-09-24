package com.tylergimpel.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.ui.bank.ExerciseBankScreen
import com.tylergimpel.gymtracker.ui.bodyweight.BodyweightScreen
import com.tylergimpel.gymtracker.ui.plates.PlatesScreen
import com.tylergimpel.gymtracker.ui.profile.ProfileScreen
import com.tylergimpel.gymtracker.ui.settings.ExcludedScreen
import com.tylergimpel.gymtracker.ui.settings.SettingsScreen
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.tylergimpel.gymtracker.ui.routine.RoutineScreen
import com.tylergimpel.gymtracker.ui.routine.RoutineViewModel
import com.tylergimpel.gymtracker.ui.session.SessionScreen
import com.tylergimpel.gymtracker.ui.session.SessionViewModel
import com.tylergimpel.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val themes = GymDatabase.getInstance(this).profileRepository().observeTheme()
        setContent {
            // Following the phone until the lifter says otherwise, which is also what shows for
            // the moment before the stored choice has been read.
            val theme by themes.collectAsStateWithLifecycle(initialValue = Theme.SYSTEM)
            GymTrackerTheme(
                darkTheme = when (theme) {
                    Theme.SYSTEM -> isSystemInDarkTheme()
                    Theme.LIGHT -> false
                    Theme.DARK -> true
                },
            ) {
                GymTracker()
            }
        }
    }
}

private enum class Destination {
    SESSION, EXERCISE_PICKER, ROUTINE, BODYWEIGHT, PLATES, PROFILE, SETTINGS, EXCLUDED,
}

/**
 * A handful of destinations and a back stack of them, kept by hand.
 *
 * A stack rather than one destination because Settings opened a second level: Profile and Plates
 * are reached from it and should return to it, and Weigh-ins is reached from both the session and
 * the profile and should return to whichever opened it. A navigation library would still be more
 * machinery than that needs.
 */
@Composable
private fun GymTracker(
    viewModel: SessionViewModel = viewModel(),
    routineViewModel: RoutineViewModel = viewModel(),
) {
    // Saved as names, which a Bundle holds without question, and never empty: the session is
    // always at the bottom, because it is where the app opens and where everything returns.
    var stack by rememberSaveable { mutableStateOf(listOf(Destination.SESSION.name)) }
    val destination = Destination.valueOf(stack.last())

    fun open(next: Destination) {
        stack = stack + next.name
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    // Which routine day is being filled, if the bank was opened to write down a machine for one.
    // The bank screen is the same either way; only where the new exercise lands differs.
    var pickingForDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val defaultRoutineName = stringResource(R.string.routine_default_name)

    // Whether Settings was opened to import, from the first screen, rather than to look around.
    var importOnArrival by rememberSaveable { mutableStateOf(false) }

    fun leavePicker() {
        pickingForDay = null
        back()
    }

    if (destination != Destination.SESSION) {
        BackHandler { if (destination == Destination.EXERCISE_PICKER) leavePicker() else back() }
    }

    when (destination) {
        Destination.SESSION -> SessionScreen(
            onPickExercise = { open(Destination.EXERCISE_PICKER) },
            onOpenRoutine = { open(Destination.ROUTINE) },
            onOpenBodyweight = { open(Destination.BODYWEIGHT) },
            onOpenSettings = { open(Destination.SETTINGS) },
            // By hand is the same mechanism from an empty start: a named routine with no days,
            // and the editor to grow it in.
            onBuildRoutine = {
                routineViewModel.createRoutine(defaultRoutineName)
                open(Destination.ROUTINE)
            },
            onLetTheAppBuild = { open(Destination.ROUTINE) },
            // Straight into the file picker, from the same flow Settings offers, so importing is
            // one tap and its outcome is said where the rest of the lifter's data is.
            onImportFitNotes = {
                importOnArrival = true
                open(Destination.SETTINGS)
            },
            viewModel = viewModel,
        )

        Destination.EXERCISE_PICKER -> ExerciseBankScreen(
            onSelect = { exercise ->
                val day = pickingForDay
                if (day == null) viewModel.select(exercise)
                else routineViewModel.pickInto(day, exercise)
                leavePicker()
            },
        )

        Destination.BODYWEIGHT -> BodyweightScreen(onBack = ::back)

        Destination.PLATES -> PlatesScreen(onBack = ::back)

        Destination.PROFILE -> ProfileScreen(
            onBack = ::back,
            onOpenBodyweight = { open(Destination.BODYWEIGHT) },
        )

        Destination.SETTINGS -> SettingsScreen(
            onBack = ::back,
            importOnArrival = importOnArrival,
            onImportStarted = { importOnArrival = false },
            onOpenPlates = { open(Destination.PLATES) },
            onOpenProfile = { open(Destination.PROFILE) },
            onOpenExcluded = { open(Destination.EXCLUDED) },
        )

        Destination.EXCLUDED -> ExcludedScreen(onBack = ::back)

        Destination.ROUTINE -> RoutineScreen(
            onBack = ::back,
            // The only reason left to leave the routine: writing down a machine the app has
            // never heard of, which is the one form in the app that does that. The day comes
            // with them, so what they add lands where they were putting it.
            onAddNewExercise = { dayId ->
                pickingForDay = dayId
                open(Destination.EXERCISE_PICKER)
            },
            viewModel = routineViewModel,
        )
    }
}
