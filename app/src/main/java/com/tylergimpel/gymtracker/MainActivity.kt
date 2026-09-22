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
import com.tylergimpel.gymtracker.ui.routine.RoutineScreen
import com.tylergimpel.gymtracker.ui.routine.RoutineViewModel
import com.tylergimpel.gymtracker.ui.session.SessionScreen
import com.tylergimpel.gymtracker.ui.session.SessionViewModel
import com.tylergimpel.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                GymTracker()
            }
        }
    }
}

private enum class Destination { SESSION, EXERCISE_PICKER, ROUTINE, BODYWEIGHT, PLATES }

/**
 * Four destinations, switched by hand.
 *
 * A navigation library would earn its place once there is a back stack worth modelling; with a
 * session, a picker, an editor and a weigh-in list it would be more machinery than the app has
 * behaviour.
 */
@Composable
private fun GymTracker(
    viewModel: SessionViewModel = viewModel(),
    routineViewModel: RoutineViewModel = viewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.SESSION) }

    // Which routine day is being filled, if the bank was opened to write down a machine for one.
    // The bank screen is the same either way; only where the new exercise lands differs.
    var pickingForDay by rememberSaveable { mutableStateOf<Long?>(null) }

    fun leavePicker() {
        destination = if (pickingForDay == null) Destination.SESSION else Destination.ROUTINE
        pickingForDay = null
    }

    if (destination != Destination.SESSION) {
        BackHandler { leavePicker() }
    }

    when (destination) {
        Destination.SESSION -> SessionScreen(
            onPickExercise = { destination = Destination.EXERCISE_PICKER },
            onOpenRoutine = { destination = Destination.ROUTINE },
            onOpenBodyweight = { destination = Destination.BODYWEIGHT },
            onOpenPlates = { destination = Destination.PLATES },
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

        Destination.BODYWEIGHT -> BodyweightScreen(
            onBack = { destination = Destination.SESSION },
        )

        Destination.PLATES -> PlatesScreen(
            onBack = { destination = Destination.SESSION },
        )

        Destination.ROUTINE -> RoutineScreen(
            onBack = { destination = Destination.SESSION },
            // The only reason left to leave the routine: writing down a machine the app has
            // never heard of, which is the one form in the app that does that. The day comes
            // with them, so what they add lands where they were putting it.
            onAddNewExercise = { dayId ->
                pickingForDay = dayId
                destination = Destination.EXERCISE_PICKER
            },
            viewModel = routineViewModel,
        )
    }
}
