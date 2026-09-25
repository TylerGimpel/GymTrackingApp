package com.tylergimpel.gymtracker.ui.calendar

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
)

/** One day from the calendar, read back (#118). */
class DayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val sessions = database.sessionRepository()

    private val date = MutableStateFlow<LocalDate?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayUiState> = date.filterNotNull()
        .flatMapLatest { day ->
            combine(sessions.observeOnDate(day), profile.observeDisplayUnit()) { onThatDay, unit ->
                val batches = onThatDay.mapNotNull { it.session.importBatchId }.toSet()
                val sources = if (batches.isEmpty()) {
                    emptyMap()
                } else {
                    database.importBatchDao().all().filter { it.id in batches }
                        .associate { it.id to importSourceWithId(it.source) }
                }
                DayUiState(day, onThatDay, unit, sources)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DayUiState())

    fun open(day: LocalDate) {
        date.value = day
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
