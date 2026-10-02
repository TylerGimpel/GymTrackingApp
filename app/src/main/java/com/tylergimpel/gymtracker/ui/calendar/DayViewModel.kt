package com.tylergimpel.gymtracker.ui.calendar

import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.ui.session.EditingEffort
import com.tylergimpel.gymtracker.ui.session.editingOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.tylergimpel.gymtracker.domain.ImportSource
import com.tylergimpel.gymtracker.domain.importSourceWithId
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.sessionRepository
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate? = null,
    /** Every session on the day, in the order they were started. */
    val sessions: List<LoggedSession> = emptyList(),
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
    /** Where each import behind these sessions came from, by batch (#119). */
    val importedFrom: Map<Long, ImportSource?> = emptyMap(),
    /** A logged set open for correction (#120). */
    val editing: EditingEffort? = null,
    /** A missed set being added (#120). */
    val adding: AddingSet? = null,
    /** What the lifter weighed that day, the latest reading, if they weighed in. */
    val weight: Kilograms? = null,
)

/** A set the lifter missed, being added to [sessionExerciseId] after the fact (#120). */
data class AddingSet(val sessionExerciseId: Long, val exerciseUnit: WeightUnit, val draft: SetDraft)

/** One day from the calendar, read back (#118). */
class DayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val sessions = database.sessionRepository()

    private val date = MutableStateFlow<LocalDate?>(null)
    private val editing = MutableStateFlow<EditingEffort?>(null)
    private val adding = MutableStateFlow<AddingSet?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val day: Flow<DayUiState> = date.filterNotNull()
        .flatMapLatest { day ->
            combine(
                sessions.observeOnDate(day),
                profile.observeDisplayUnit(),
                database.bodyweightDao().observeBetween(day, day),
            ) { onThatDay, unit, readings ->
                val batches = onThatDay.mapNotNull { it.session.importBatchId }.toSet()
                val sources = if (batches.isEmpty()) {
                    emptyMap()
                } else {
                    database.importBatchDao().all().filter { it.id in batches }
                        .associate { it.id to importSourceWithId(it.source) }
                }
                DayUiState(day, onThatDay, unit, sources, weight = readings.maxByOrNull { it.recordedAt }?.let { Kilograms(it.weightKg) })
            }
        }

    val state: StateFlow<DayUiState> = combine(day, editing, adding) { read, correcting, missed ->
        read.copy(editing = correcting, adding = missed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DayUiState())

    fun open(day: LocalDate) {
        date.value = day
        editing.value = null
        adding.value = null
    }

    /**
     * Opens a logged set for correction. Records and the **Anchor** are read from the log, so
     * whatever is saved here is what they follow, with nothing else to update (ADR 0010).
     */
    fun startEditing(performed: SessionExercise, set: PerformedSet, effort: Effort) {
        editing.value = editingOf(performed, set, effort, state.value.lifterUnit)
    }

    fun changeEditingDraft(draft: SetDraft) = editing.update { it?.copy(draft = draft) }

    fun cancelEditing() {
        editing.value = null
    }

    fun saveEditing() {
        val open = editing.value ?: return
        viewModelScope.launch {
            sessions.editSet(open.effortId, open.draft, open.exerciseUnit)
            editing.value = null
        }
    }

    /** Removes the whole set, both rows if it has two: half a per-side set is not a set. */
    fun deleteEditing() {
        val open = editing.value ?: return
        viewModelScope.launch {
            sessions.deleteSet(open.sessionExerciseId, open.setIndex)
            editing.value = null
        }
    }

    /** Starts a set the lifter missed on [performed], in its exercise's own terms. */
    fun startAdding(performed: SessionExercise) {
        val exercise = performed.exercise
        // Filled in from the last working set, which is what a missed set most often repeats.
        val last = performed.sets.lastOrNull { it.type.countsTowardVolume } ?: performed.sets.lastOrNull()
        adding.value = AddingSet(
            sessionExerciseId = performed.id,
            exerciseUnit = exercise.displayUnit,
            draft = last?.let { editingOf(performed, it, it.leadingEffort, state.value.lifterUnit).draft.copy(note = null) }
                ?: SetDraft(
                    loggingType = exercise.loggingType,
                    displayUnit = exercise.displayUnit,
                    lifterUnit = state.value.lifterUnit,
                ),
        )
    }

    fun changeAddingDraft(draft: SetDraft) = adding.update { it?.copy(draft = draft) }

    fun cancelAdding() {
        adding.value = null
    }

    fun saveAdding() {
        val open = adding.value ?: return
        viewModelScope.launch {
            if (sessions.addSetTo(open.sessionExerciseId, open.draft, open.exerciseUnit)) adding.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
