package com.tylergimpel.gymtracker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.sessionRepository
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.data.toDomain
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseHistoryEntry
import com.tylergimpel.gymtracker.domain.HISTORY_PAGE
import com.tylergimpel.gymtracker.domain.WeightUnit
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

    private val screen = MutableStateFlow(ExerciseHistoryUiState())
    val state: StateFlow<ExerciseHistoryUiState> = screen.asStateFlow()

    private var loading = false

    fun open(exerciseId: String) {
        viewModelScope.launch {
            val unit = profile.displayUnit()
            screen.value = ExerciseHistoryUiState(
                exercise = database.exerciseDao().byId(exerciseId)?.toDomain(unit),
                lifterUnit = unit,
            )
            loadMore()
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
