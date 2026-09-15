package com.tylergimpel.gymtracker.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SessionPlan
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
import java.time.LocalDate
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
 * The session whose date is being changed, if one is.
 *
 * Carries the id rather than the session, because the one being refiled is not always the one on
 * screen: a date is most often found to be wrong just after Finish, when the session has already
 * stopped being the active one.
 */
data class Refiling(val sessionId: Long, val date: LocalDate)

/**
 * What the lifter calls an exercise and what they wrote about it, while they are editing them.
 *
 * Both at once, because they are the same conversation: this is the machine in the corner, and
 * here is what you need to remember about it.
 */
data class ExerciseDetails(
    val exerciseId: String,
    val name: String,
    val note: String,
)

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
    val overridingDisplayUnit: Boolean = false,
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
    val finishing: Boolean = false,
    val refiling: Refiling? = null,
    val skipping: RoutineDay? = null,
    val editingExercise: ExerciseDetails? = null,
)

data class SessionUiState(
    val session: Session? = null,
    val exercises: List<SessionExercise> = emptyList(),
    /**
     * The session laid out as it was performed, with superset members gathered.
     *
     * The list the screen renders. [exercises] stays as the flat record, because volume and
     * progression read the work and have no interest in how it was grouped.
     */
    val positions: List<SessionPosition> = emptyList(),
    val selected: Exercise? = null,
    val draft: SetDraft = SetDraft(),
    /** Whether the entry controls are expanded to record each side separately. */
    val perSide: Boolean = false,
    /**
     * Whether the unit chips are departing for this one set rather than settling the exercise.
     *
     * Off is the common case and the one the controls are built around: a machine is labelled in
     * one unit whoever walks up to it, so the choice belongs to the exercise.
     */
    val overridingDisplayUnit: Boolean = false,
    /** The right-side draft, used only while [perSide] is set. */
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
    /**
     * What the session was supposed to contain and did not — what makes "I did four of six"
     * answerable, and a prescribed exercise that went untrained visible rather than absent.
     */
    val unperformed: List<Exercise> = emptyList(),
    /** The routine and where it has got to, so the screen can say what comes next. */
    val routine: RoutineProgress? = null,
    /** Set while the screen is asking which routine day the finishing session realised. */
    val finishing: Boolean = false,
    /**
     * The most recently finished session, offered only when none is being logged.
     *
     * There so that a date noticed to be wrong after pressing Finish is still fixable. Deliberately
     * one session rather than a list: this is a way back to what was just done, not history.
     */
    val lastFinished: LoggedSession? = null,
    /** The session whose date the screen is asking about, if it is asking. */
    val refiling: Refiling? = null,
    /** The routine day the screen is asking about skipping, if it is asking. */
    val skipping: RoutineDay? = null,
    /** The name and note being edited on the selected exercise, if they are. */
    val editingExercise: ExerciseDetails? = null,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val repository = SessionRepository(
        database = database,
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
        zone = ZoneId.systemDefault(),
        profile = profile,
    )

    private val exercises =
        ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

    private val routines = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        sessions = database.sessionDao(),
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
        combine(
            logged,
            repository.observeLastFinished(),
            routines.observeProgress(),
            screen,
        ) { logged, lastFinished, routine, screen ->
            SessionUiState(
                session = logged?.session,
                unperformed = logged?.unperformed.orEmpty(),
                lastFinished = lastFinished,
                exercises = logged?.exercises.orEmpty(),
                positions = logged?.positions.orEmpty(),
                selected = screen.selected,
                draft = screen.draft,
                perSide = screen.perSide,
                overridingDisplayUnit = screen.overridingDisplayUnit,
                rightDraft = screen.rightDraft,
                editing = screen.editing,
                addingDrop = screen.addingDrop,
                routine = routine,
                finishing = screen.finishing,
                refiling = screen.refiling,
                skipping = screen.skipping,
                editingExercise = screen.editingExercise,
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
            // Departing is a property of the machine in front of the lifter, so it does not
            // follow them to the next exercise any more than per-side entry does.
            overridingDisplayUnit = false,
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
     * Reads the controls in [displayUnit], and remembers it as how this exercise is read.
     *
     * Remembering is what a unit chip does, because a machine is labelled in one unit whoever walks
     * up to it and the gym's pound lat pulldown is still in pounds next week — see
     * `docs/design/logging-screen.md`. The exception is explicit: while the controls are
     * overriding, this settles nothing beyond the set being entered.
     */
    fun readIn(displayUnit: WeightUnit) {
        val exercise = screen.value.selected ?: return
        screen.update {
            it.copy(
                draft = it.draft.readIn(displayUnit),
                rightDraft = it.rightDraft.readIn(displayUnit),
            )
        }
        if (screen.value.overridingDisplayUnit) return
        viewModelScope.launch {
            exercises.setDisplayUnit(exercise.id, displayUnit)
            // Carried across rather than re-read: this view model is the only thing that writes a
            // preference, and the controls should not sit in the old unit while the write lands.
            screen.update { it.copy(selected = it.selected?.copy(displayUnit = displayUnit)) }
        }
    }

    /**
     * Lets this one set depart from the exercise's unit, or puts it back on it.
     *
     * Going back re-reads the drafts in the exercise's own unit, so "not departing" and "reads as
     * the exercise does" cannot come apart and leave a set quietly recording an override of a unit
     * it agrees with.
     */
    fun toggleDisplayUnitOverride() = screen.update {
        val exerciseUnit = it.selected?.displayUnit
        if (it.overridingDisplayUnit && exerciseUnit != null) {
            it.copy(
                overridingDisplayUnit = false,
                draft = it.draft.readIn(exerciseUnit),
                rightDraft = it.rightDraft.readIn(exerciseUnit),
            )
        } else {
            it.copy(overridingDisplayUnit = !it.overridingDisplayUnit)
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

    /** Puts the exercise after [sessionExerciseId] into a superset with it. */
    fun groupWithNext(sessionExerciseId: Long) {
        viewModelScope.launch { repository.groupWithNext(sessionExerciseId) }
    }

    /** Breaks a group up. The sets logged against its members are untouched. */
    fun ungroup(supersetId: Long) {
        viewModelScope.launch { repository.ungroup(supersetId) }
    }

    /** Whether this group moves to its next member on its own after a set is logged. */
    fun setAutoJump(supersetId: Long, autoJump: Boolean) {
        viewModelScope.launch { repository.setAutoJump(supersetId, autoJump) }
    }

    fun noteSession(text: String) {
        val session = logged.value?.session ?: return
        viewModelScope.launch { repository.noteSession(session.id, text) }
    }

    /**
     * Opens a session for the day the cursor points at, copying what that day holds onto it.
     *
     * The only moment a plan is taken. A session started by simply logging a set has none, which is
     * what training with no routine looks like and is not a lesser session for it.
     */
    fun startCursorDay() {
        val day = state.value.routine?.cursor ?: return
        viewModelScope.launch { repository.startSession(SessionPlan(day.exercises)) }
    }

    /** Opens what the lifter calls the selected exercise, and the note they keep on it. */
    fun startEditingExercise() {
        val exercise = screen.value.selected ?: return
        screen.update {
            it.copy(
                editingExercise = ExerciseDetails(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    note = exercise.note.orEmpty(),
                ),
            )
        }
    }

    fun changeExerciseDetails(details: ExerciseDetails) =
        screen.update { it.copy(editingExercise = details) }

    fun cancelEditingExercise() = screen.update { it.copy(editingExercise = null) }

    /**
     * Records the lifter's name for the exercise and their note on it.
     *
     * A name matching what the bank already calls it is stored as no name at all, so that the row
     * says "renamed" only when it was — and a later update correcting the shipped name is then free
     * to take effect rather than being shadowed by a copy of the old one.
     */
    fun saveExerciseDetails() {
        val open = screen.value.editingExercise ?: return
        // The exercise the dialog was opened on, which is not necessarily the one selected now.
        val exercise = screen.value.selected?.takeIf { it.id == open.exerciseId } ?: return
        val renamed = open.name.trim().takeIf { it.isNotBlank() && it != exercise.bankName }
        viewModelScope.launch {
            exercises.rename(open.exerciseId, renamed)
            exercises.note(open.exerciseId, open.note)
            screen.update {
                it.copy(
                    editingExercise = null,
                    // Carried across rather than re-read, for the reason the unit is: this view
                    // model is the only thing that writes these, and the controls should not sit
                    // showing the old name while the write lands.
                    selected = it.selected?.copy(
                        userName = renamed,
                        note = open.note.takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    /** Opens the question of skipping the day the cursor points at. */
    fun startSkipping() {
        val day = state.value.routine?.cursor ?: return
        screen.update { it.copy(skipping = day) }
    }

    fun cancelSkipping() = screen.update { it.copy(skipping = null) }

    /**
     * Marks the day as dealt with, without a session.
     *
     * The escape hatch a derived cursor structurally requires: without it the only way past a day
     * is to train it, and a lifter who is never going to do day 2 as written would have nowhere to
     * go short of revising the routine (ADR 0001). Recorded apart from any session, so history
     * never claims training that did not happen.
     *
     * Asked before it is written, because there is no unskipping it: the record is permanent for
     * that cycle, and repeated skips of one day are meant to become a signal about the routine
     * rather than a tally of mis-taps.
     */
    fun skipDay() {
        val progress = state.value.routine ?: return
        val day = screen.value.skipping ?: return
        viewModelScope.launch {
            routines.recordSkip(progress.routine.id, day)
            screen.update { it.copy(skipping = null) }
        }
    }

    fun startRefiling(session: Session) =
        screen.update { it.copy(refiling = Refiling(session.id, session.date)) }

    fun cancelRefiling() = screen.update { it.copy(refiling = null) }

    /**
     * Refiles the session onto [date] — the fix for training on Saturday and remembering on Monday.
     *
     * When it was trained is untouched, and so is the cursor: a session filed on a day other than
     * the one it was trained on records no completion when it finishes.
     */
    fun refileOn(date: LocalDate) {
        val open = screen.value.refiling ?: return
        viewModelScope.launch {
            repository.refile(open.sessionId, date)
            screen.update { it.copy(refiling = null) }
        }
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
                it.copy(
                    finishing = false,
                    selected = null,
                    draft = SetDraft(),
                    perSide = false,
                    overridingDisplayUnit = false,
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
