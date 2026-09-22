package com.tylergimpel.gymtracker.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import kotlinx.coroutines.flow.SharingStarted
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.profileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/** The day being filled, and everything the picker needs to offer something for it. */
data class PickingFor(
    val dayId: Long,
    val dayName: String,
    val bank: List<Exercise>,
)

data class RoutineUiState(
    val progress: RoutineProgress? = null,
    /** The equipment vocabulary the shipped bank uses, offered as the gym checklist. */
    val availableEquipment: List<String> = emptyList(),
    /** The day having an exercise added to it, while the picker is open. */
    val picking: PickingFor? = null,
    /**
     * An exercise the lifter picked that they had banned, waiting on them to say yes anyway.
     *
     * **A warning and never a refusal.** A dislike stops the app suggesting something; it was
     * never a promise to argue with somebody who asks for it by name, and a routine they built
     * themselves is theirs.
     */
    val addingDisliked: Exercise? = null,
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val repository = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
    )

    private val exercises = ExerciseRepository(
        database,
        database.exerciseDao(),
        database.sessionDao(),
        database.profileRepository(),
    )

    /** What the screen holds and no database supplies: the picker, and what it is asking about. */
    private val held = MutableStateFlow(RoutineUiState())

    val state: StateFlow<RoutineUiState> = combine(
        repository.observeProgress(),
        database.exerciseDao().observeEquipment(),
        held,
    ) { progress, equipment, onScreen ->
        onScreen.copy(progress = progress, availableEquipment = equipment)
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
        cardio: CardioPlacement,
    ) {
        viewModelScope.launch {
            repository.editProperties(name, goal, cadence, equipment, repPreference, cardio)
        }
    }

    fun addDay(name: String, kind: RoutineDayKind = RoutineDayKind.LIFTING) {
        val routine = state.value.progress?.routine ?: return
        viewModelScope.launch { repository.addDay(routine.id, name, kind) }
    }

    fun renameDay(dayId: Long, name: String) {
        viewModelScope.launch { repository.renameDay(dayId, name) }
    }

    /** Adds an exercise to a day. Not a revision, so the cycle and the cursor are untouched. */
    /** Opens the picker for [dayId], holding the bank while it is up. */
    fun startPicking(dayId: Long) {
        val day = state.value.progress?.routine?.rotation?.firstOrNull { it.id == dayId } ?: return
        viewModelScope.launch {
            val bank = exercises.observeBank().first()
            held.update { it.copy(picking = PickingFor(dayId, day.name, bank)) }
        }
    }

    fun cancelPicking() = held.update { it.copy(picking = null, addingDisliked = null) }

    /**
     * Adds what was picked, or asks first where they had banned it.
     *
     * The asking is the whole of the difference: nothing here can refuse.
     */
    fun pick(exercise: Exercise) {
        val picking = state.value.picking ?: return
        if (exercise.isDisliked && state.value.addingDisliked?.id != exercise.id) {
            held.update { it.copy(addingDisliked = exercise) }
            return
        }
        addExerciseToDay(picking.dayId, exercise)
        held.update { it.copy(picking = null, addingDisliked = null) }
    }

    fun addExerciseToDay(dayId: Long, exercise: Exercise) {
        viewModelScope.launch { repository.addExerciseToDay(dayId, exercise.id) }
    }

    /** Takes an exercise out of a day. Not a revision either, for the same reason. */
    fun removeExerciseFromDay(dayId: Long, exerciseId: String) {
        viewModelScope.launch { repository.removeExerciseFromDay(dayId, exerciseId) }
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
