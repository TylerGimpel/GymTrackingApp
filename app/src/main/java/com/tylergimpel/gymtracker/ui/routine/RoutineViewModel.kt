package com.tylergimpel.gymtracker.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineGoal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

data class RoutineUiState(
    val progress: RoutineProgress? = null,
    /** The equipment vocabulary the shipped bank uses, offered as the gym checklist. */
    val availableEquipment: List<String> = emptyList(),
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val repository = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
    )

    val state: StateFlow<RoutineUiState> = combine(
        repository.observeProgress(),
        database.exerciseDao().observeEquipment(),
    ) { progress, equipment ->
        RoutineUiState(progress = progress, availableEquipment = equipment)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutineUiState())

    fun createRoutine(name: String) {
        viewModelScope.launch { repository.createRoutine(name = name) }
    }

    fun editProperties(
        name: String,
        goal: RoutineGoal,
        cadence: Int,
        equipment: Set<String>,
        repPreference: RepPreference,
    ) {
        viewModelScope.launch {
            repository.editProperties(name, goal, cadence, equipment, repPreference)
        }
    }

    fun addDay(name: String) {
        val routine = state.value.progress?.routine ?: return
        viewModelScope.launch { repository.addDay(routine.id, name) }
    }

    fun renameDay(dayId: Long, name: String) {
        viewModelScope.launch { repository.renameDay(dayId, name) }
    }

    fun removeDay(dayId: Long) {
        viewModelScope.launch { repository.removeDay(dayId) }
    }

    fun moveDay(dayId: Long, by: Int) {
        viewModelScope.launch { repository.moveDay(dayId, by) }
    }

    fun deleteRoutine() {
        viewModelScope.launch { repository.deleteRoutine() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
