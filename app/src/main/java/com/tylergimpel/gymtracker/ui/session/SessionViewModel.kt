package com.tylergimpel.gymtracker.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.LoggedSession
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
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

/** The effort being corrected, if one is open for editing. */
data class EditingEffort(
    val effortId: Long,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val draft: SetDraft,
    /**
     * What the exercise reads in, which is what decides whether this set records a unit at all:
     * correcting a set must not quietly turn the exercise's own setting into an override on it.
     */
    val exerciseUnit: WeightUnit,
)

/** The effort a drop is being added to, if one is open. */
data class AddingDrop(val effortId: Long, val draft: SetDraft)

/**
 * Everything on this screen that is not read from the database.
 *
 * Held as one object rather than as a flow each, because `combine` runs out of typed overloads at
 * five and the alternative is packing values into pairs to smuggle them past it.
 */
private data class ScreenState(
    val selected: Exercise? = null,
    val draft: SetDraft = SetDraft(),
    val perSide: Boolean = false,
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
    val finishing: Boolean = false,
)

data class SessionUiState(
    val session: Session? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val selected: Exercise? = null,
    val draft: SetDraft = SetDraft(),
    /** Whether the entry controls are expanded to record each side separately. */
    val perSide: Boolean = false,
    /** The right-side draft, used only while [perSide] is set. */
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
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

    private val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao())

    private val routines = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        clock = Clock.systemDefaultZone(),
    )

    private val screen = MutableStateFlow(ScreenState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logged: StateFlow<LoggedSession?> = repository.observeActiveSession()
        .flatMapLatest { session ->
            if (session == null) flowOf(null) else repository.observeSession(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val state: StateFlow<SessionUiState> =
        combine(logged, routines.observeProgress(), screen) { logged, routine, screen ->
            SessionUiState(
                session = logged?.session,
                exercises = logged?.exercises.orEmpty(),
                selected = screen.selected,
                draft = screen.draft,
                perSide = screen.perSide,
                rightDraft = screen.rightDraft,
                editing = screen.editing,
                addingDrop = screen.addingDrop,
                routine = routine,
                finishing = screen.finishing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

    /**
     * Points the controls at an exercise.
     *
     * Per-side entry collapses on the way: it is a property of the exercise being trained, and
     * carrying it across to the next one would silently write two rows for a barbell bench press.
     */
    fun select(exercise: Exercise) = screen.update {
        it.copy(
            selected = exercise,
            // The drafts start in the unit this exercise is read in, which is the point of
            // remembering it: walking up to the pound machine needs no setting up first.
            draft = SetDraft(displayUnit = exercise.displayUnit),
            perSide = false,
            rightDraft = SetDraft(displayUnit = exercise.displayUnit),
        )
    }

    /**
     * Takes what the left-hand controls now hold, keeping the right-hand side reading in the same
     * unit — a set is read off one machine, so its two sides cannot be in different ones.
     */
    fun changeDraft(draft: SetDraft) = screen.update {
        it.copy(draft = draft, rightDraft = it.rightDraft.readIn(draft.displayUnit))
    }

    fun changeRightDraft(draft: SetDraft) = screen.update { it.copy(rightDraft = draft) }

    /**
     * Remembers the unit the controls are in as how this exercise is read from now on.
     *
     * Offered separately from changing the unit rather than happening on every toggle: the one
     * machine in the corner labelled in pounds is a set-sized decision, and rewriting the
     * exercise's setting on its behalf would make every later set follow a one-off.
     */
    fun rememberDisplayUnit() {
        val exercise = screen.value.selected ?: return
        val displayUnit = screen.value.draft.displayUnit
        viewModelScope.launch {
            exercises.setDisplayUnit(exercise.id, displayUnit)
            // Carried across rather than re-read: this view model is the only thing that writes a
            // preference, and the controls should not sit in the old unit while the write lands.
            screen.update { it.copy(selected = it.selected?.copy(displayUnit = displayUnit)) }
        }
    }

    /** Expands the controls to record each side separately, or collapses them back to one entry. */
    fun togglePerSide() = screen.update {
        it.copy(perSide = !it.perSide, rightDraft = it.draft.copy(note = null))
    }

    fun logSet() {
        val current = screen.value
        val exercise = current.selected ?: return
        viewModelScope.launch {
            val recorded = if (current.perSide) {
                repository.logPerSideSet(
                    exercise.id,
                    current.draft,
                    current.rightDraft,
                    exercise.displayUnit,
                )
            } else {
                repository.logSet(exercise.id, current.draft, exercise.displayUnit)
            }
            if (!recorded) return@launch
            // Keep the numbers: the next set is usually the same load, and retyping them every time
            // is the single most repeated action in a session.
            screen.update {
                it.copy(
                    draft = it.draft.copy(note = null),
                    rightDraft = it.rightDraft.copy(note = null),
                )
            }
        }
    }

    fun startEditing(performed: SessionExercise, set: PerformedSet, effort: Effort) = screen.update {
        val displayUnit = performed.displayUnitFor(set)
        it.copy(
            editing = EditingEffort(
                effortId = effort.id,
                sessionExerciseId = performed.id,
                setIndex = set.setIndex,
                draft = SetDraft(
                    // Seeded in the unit the set is shown in, so opening one for correction does
                    // not change the number the lifter was reading a moment ago.
                    weightText = effort.weight.asTextIn(displayUnit),
                    repsText = effort.reps.toString(),
                    note = effort.note,
                    type = set.type,
                    displayUnit = displayUnit,
                ),
                exerciseUnit = performed.exercise.displayUnit,
            ),
        )
    }

    fun changeEditingDraft(draft: SetDraft) =
        screen.update { it.copy(editing = it.editing?.copy(draft = draft)) }

    fun cancelEditing() = screen.update { it.copy(editing = null) }

    fun saveEditing() {
        val open = screen.value.editing ?: return
        viewModelScope.launch {
            repository.editSet(open.effortId, open.draft, open.exerciseUnit)
            screen.update { it.copy(editing = null) }
        }
    }

    /** Deletes the whole set, both rows if it has two: half a per-side set is not a set. */
    fun deleteEditing() {
        val open = screen.value.editing ?: return
        viewModelScope.launch {
            repository.deleteSet(open.sessionExerciseId, open.setIndex)
            screen.update { it.copy(editing = null) }
        }
    }

    /** [displayUnit] is what the set being continued reads in: a drop is part of it, not apart. */
    fun startAddingDrop(displayUnit: WeightUnit, effort: Effort) = screen.update {
        it.copy(addingDrop = AddingDrop(effort.id, SetDraft(displayUnit = displayUnit)))
    }

    fun changeDropDraft(draft: SetDraft) =
        screen.update { it.copy(addingDrop = it.addingDrop?.copy(draft = draft)) }

    fun cancelAddingDrop() = screen.update { it.copy(addingDrop = null) }

    fun saveDrop() {
        val open = screen.value.addingDrop ?: return
        val weight = open.draft.weight ?: return
        val reps = open.draft.reps ?: return
        viewModelScope.launch {
            repository.addDrop(open.effortId, weight, reps)
            screen.update { it.copy(addingDrop = null) }
        }
    }

    fun noteSession(text: String) {
        val session = logged.value?.session ?: return
        viewModelScope.launch { repository.noteSession(session.id, text) }
    }

    /** Opens the question of which routine day this session realised, or just finishes it. */
    fun startFinishing() {
        if (state.value.routine?.routine?.days.orEmpty().isEmpty()) {
            finishSession(null)
        } else {
            screen.update { it.copy(finishing = true) }
        }
    }

    fun cancelFinishing() = screen.update { it.copy(finishing = false) }

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
            screen.update {
                it.copy(finishing = false, selected = null, draft = SetDraft(), perSide = false)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
