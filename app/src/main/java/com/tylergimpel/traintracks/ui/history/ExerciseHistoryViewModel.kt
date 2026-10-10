package com.tylergimpel.traintracks.ui.history

import com.tylergimpel.traintracks.ui.session.saveDetails
import com.tylergimpel.traintracks.ui.session.details
import com.tylergimpel.traintracks.ui.session.ExerciseDetails
import com.tylergimpel.traintracks.data.ExerciseRepository
import com.tylergimpel.traintracks.domain.PersonalRecord
import com.tylergimpel.traintracks.domain.StrengthPoint
import com.tylergimpel.traintracks.domain.VolumeSeries
import com.tylergimpel.traintracks.domain.personalRecords
import com.tylergimpel.traintracks.domain.strengthSeries
import com.tylergimpel.traintracks.domain.volumeSeries
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.sessionRepository
import com.tylergimpel.traintracks.data.profileRepository
import com.tylergimpel.traintracks.data.toDomain
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.ExerciseHistoryEntry
import com.tylergimpel.traintracks.domain.HISTORY_PAGE
import com.tylergimpel.traintracks.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseHistoryUiState(
    val exercise: Exercise? = null,
    /** What distances read in, the lifter's own unit; weights read in the exercise's. */
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
    /** Newest first, as far back as has been scrolled to. */
    val entries: List<ExerciseHistoryEntry> = emptyList(),
    /** Whether the first session ever has been read, so there is nothing further back. */
    val reachedEnd: Boolean = false,
    /** Each session's best and the **Anchor** after it, across the whole history (#122). */
    val strength: List<StrengthPoint> = emptyList(),
    /** The **Personal records**: the best set at each rep count, recomputed from the log (#123). */
    val records: List<PersonalRecord> = emptyList(),
    /** Counted sets per session, from where set types became known (#125). */
    val volume: VolumeSeries = VolumeSeries(emptyList(), startsAfterHistory = false),
    /** The Settings tab's fields, as the lifter is editing them (#148). */
    val details: ExerciseDetails? = null,
    /** Set briefly once the Settings tab has been saved. */
    val saved: Boolean = false,
)

/**
 * Every set on one exercise, read a page at a time as the lifter scrolls back (#121).
 *
 * Opened fresh each time, because a set logged since it was last looked at belongs at the top.
 */
class ExerciseHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val sessions = database.sessionRepository()

    private val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

    private val screen = MutableStateFlow(ExerciseHistoryUiState())
    val state: StateFlow<ExerciseHistoryUiState> = screen.asStateFlow()

    private var loading = false

    fun open(exerciseId: String) {
        viewModelScope.launch {
            val unit = profile.displayUnit()
            val exercise = database.exerciseDao().byId(exerciseId)?.toDomain(unit)
            val history = sessions.trainingHistoryOf(exerciseId)
            screen.value = ExerciseHistoryUiState(
                exercise = exercise,
                lifterUnit = unit,
                details = exercise?.details(unit),
                strength = history.strengthSeries(resetOn = exercise?.estimateResetOn),
                records = personalRecords(history),
                volume = history.volumeSeries(),
            )
            loadMore()
        }
    }

    fun changeDetails(details: ExerciseDetails) = screen.update { it.copy(details = details, saved = false) }

    /** Saves the Settings tab, the same writes the workout makes, and reads the exercise back. */
    fun saveDetails() {
        val current = screen.value
        val exercise = current.exercise ?: return
        val details = current.details ?: return
        viewModelScope.launch {
            exercises.saveDetails(exercise, details, current.lifterUnit)
            val reread = database.exerciseDao().byId(exercise.id)?.toDomain(current.lifterUnit) ?: exercise
            screen.update { it.copy(exercise = reread, details = reread.details(current.lifterUnit), saved = true) }
        }
    }

    /** Reads the next page back, unless one is already being read or there is nothing further. */
    fun loadMore() {
        val current = screen.value
        val exercise = current.exercise ?: return
        if (loading || current.reachedEnd) return
        loading = true
        viewModelScope.launch {
            try {
                val page = sessions.historyPage(exercise.id, after = current.entries.lastOrNull())
                screen.update {
                    it.copy(entries = it.entries + page, reachedEnd = page.size < HISTORY_PAGE)
                }
            } finally {
                loading = false
            }
        }
    }
}
