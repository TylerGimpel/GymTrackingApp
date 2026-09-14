package com.tylergimpel.gymtracker.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.LoggedSession
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggedSet
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.SetDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/** The exercise the entry controls are currently pointed at. */
data class SelectedExercise(val id: String, val name: String)

/** The set being corrected, if a set is open for editing. */
data class EditingSet(val setId: Long, val draft: SetDraft)

data class SessionUiState(
    val session: Session? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val selected: SelectedExercise? = null,
    val draft: SetDraft = SetDraft(),
    val editing: EditingSet? = null,
    /** The routine and where it has got to, so the screen can say what comes next. */
    val routine: RoutineProgress? = null,
    /** Set while the screen is asking which routine day the finishing session realised. */
    val finishing: Boolean = false,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val repository = SessionRepository(
        database = database,
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
        zone = ZoneId.systemDefault(),
    )

    private val routines = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        clock = Clock.systemDefaultZone(),
    )

    private val selected = MutableStateFlow<SelectedExercise?>(null)
    private val draft = MutableStateFlow(SetDraft())
    private val editing = MutableStateFlow<EditingSet?>(null)
    private val finishing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logged: StateFlow<LoggedSession?> = repository.observeActiveSession()
        .flatMapLatest { session ->
            if (session == null) flowOf(null) else repository.observeSession(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val state: StateFlow<SessionUiState> = combine(
        logged,
        selected,
        draft,
        editing,
        combine(routines.observeProgress(), finishing) { routine, finishing -> routine to finishing },
    ) { logged, selected, draft, editing, routineState ->
        val (routine, finishing) = routineState
        SessionUiState(
            session = logged?.session,
            exercises = logged?.exercises.orEmpty(),
            selected = selected,
            draft = draft,
            editing = editing,
            routine = routine,
            finishing = finishing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

    fun select(exercise: Exercise) {
        selected.value = SelectedExercise(exercise.id, exercise.name)
        draft.value = SetDraft()
    }

    /** The screen edits a whole draft rather than one field at a time. */
    fun changeDraft(draft: SetDraft) { this.draft.value = draft }

    fun logSet() {
        val exercise = selected.value ?: return
        val entered = draft.value
        viewModelScope.launch {
            if (repository.logSet(exercise.id, entered) != null) {
                // Keep the weight: the next set is usually the same load, and retyping it every
                // time is the single most repeated action in a session.
                draft.value = SetDraft(weightText = entered.weightText, repsText = entered.repsText)
            }
        }
    }

    fun startEditing(set: LoggedSet) {
        editing.value = EditingSet(
            setId = set.id,
            draft = SetDraft(
                weightText = set.weight.asText(),
                repsText = set.reps.toString(),
                note = set.note,
            ),
        )
    }

    fun changeEditingDraft(draft: SetDraft) = editing.update { it?.copy(draft = draft) }

    fun cancelEditing() { editing.value = null }

    fun saveEditing() {
        val open = editing.value ?: return
        viewModelScope.launch {
            repository.editSet(open.setId, open.draft)
            editing.value = null
        }
    }

    fun deleteEditing() {
        val open = editing.value ?: return
        viewModelScope.launch {
            repository.deleteSet(open.setId)
            editing.value = null
        }
    }

    fun noteSession(text: String) {
        val session = logged.value?.session ?: return
        viewModelScope.launch { repository.noteSession(session.id, text) }
    }

    /** Opens the question of which routine day this session realised, or just finishes it. */
    fun startFinishing() {
        if (state.value.routine?.routine?.days.orEmpty().isEmpty()) finishSession(null) else finishing.value = true
    }

    fun cancelFinishing() { finishing.value = false }

    /**
     * Closes the session, and records [day] as trained if one was named.
     *
     * Both writes go in one transaction: a finished session that failed to advance the cycle would
     * leave the cursor pointing at work already done, with nothing to show why.
     */
    fun finishSession(day: RoutineDay?) {
        val session = logged.value?.session ?: return
        val routine = state.value.routine?.routine
        viewModelScope.launch {
            database.withTransaction {
                repository.finishSession(session.id, routine.takeIf { day != null }, day)
                if (routine != null && day != null) {
                    routines.recordCompletion(routine.id, day, session.id)
                }
            }
            finishing.value = false
            selected.value = null
            draft.value = SetDraft()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
