package com.tylergimpel.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.ui.bank.ExerciseBankScreen
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

/**
 * Two destinations, switched by hand.
 *
 * A navigation library would earn its place once there are more screens and a back stack worth
 * modelling; with the session and the bank picker it would be more machinery than the app has
 * behaviour.
 */
@Composable
private fun GymTracker(viewModel: SessionViewModel = viewModel()) {
    var pickingExercise by rememberSaveable { mutableStateOf(false) }

    if (pickingExercise) {
        BackHandler { pickingExercise = false }
        ExerciseBankScreen(
            onSelect = { exercise ->
                viewModel.select(exercise.id, exercise.name)
                pickingExercise = false
            },
        )
    } else {
        SessionScreen(
            onPickExercise = { pickingExercise = true },
            viewModel = viewModel,
        )
    }
}
