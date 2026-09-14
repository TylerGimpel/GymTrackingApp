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
import com.tylergimpel.gymtracker.ui.routine.RoutineScreen
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

private enum class Destination { SESSION, EXERCISE_PICKER, ROUTINE }

/**
 * Three destinations, switched by hand.
 *
 * A navigation library would earn its place once there is a back stack worth modelling; with a
 * session, a picker and an editor it would be more machinery than the app has behaviour.
 */
@Composable
private fun GymTracker(viewModel: SessionViewModel = viewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.SESSION) }

    if (destination != Destination.SESSION) {
        BackHandler { destination = Destination.SESSION }
    }

    when (destination) {
        Destination.SESSION -> SessionScreen(
            onPickExercise = { destination = Destination.EXERCISE_PICKER },
            onOpenRoutine = { destination = Destination.ROUTINE },
            viewModel = viewModel,
        )

        Destination.EXERCISE_PICKER -> ExerciseBankScreen(
            onSelect = { exercise ->
                viewModel.select(exercise)
                destination = Destination.SESSION
            },
        )

        Destination.ROUTINE -> RoutineScreen(
            onBack = { destination = Destination.SESSION },
        )
    }
}
