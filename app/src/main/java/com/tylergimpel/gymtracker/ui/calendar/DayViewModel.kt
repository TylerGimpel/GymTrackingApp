package com.tylergimpel.gymtracker.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.SessionRepository
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class DayUiState(
    val date: LocalDate? = null,
    /** Every session on the day, in the order they were started. */
    val sessions: List<LoggedSession> = emptyList(),
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
)

/** One day from the calendar, read back (#118). */
class DayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val sessions = SessionRepository(
        database,
        database.sessionDao(),
        Clock.systemDefaultZone(),
        ZoneId.systemDefault(),
        profile,
    )

    private val date = MutableStateFlow<LocalDate?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayUiState> = date.filterNotNull()
        .flatMapLatest { day ->
            combine(sessions.observeOnDate(day), profile.observeDisplayUnit()) { onThatDay, unit ->
                DayUiState(day, onThatDay, unit)
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
