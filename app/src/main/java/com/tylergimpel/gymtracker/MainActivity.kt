package com.tylergimpel.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tylergimpel.gymtracker.ui.bank.ExerciseBankScreen
import com.tylergimpel.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                ExerciseBankScreen()
            }
        }
    }
}
