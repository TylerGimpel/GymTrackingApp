package com.tylergimpel.gymtracker.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.CalendarRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.domain.CalendarDay
import com.tylergimpel.gymtracker.domain.MuscleLastTrained
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.data.sessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth,
    /** The marked days of [month]; a day not here has no mark. */
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
    /** Whether there is anything to mark further back. */
    val canGoBack: Boolean = false,
    /** Whether [month] is before this one: there is nothing to mark in the future. */
    val canGoForward: Boolean = false,
    /** How long since each muscle was trained, as of today (#127): the strip under the grid. */
    val lastTrained: List<MuscleLastTrained> = emptyList(),
    /** What a weight on the grid is read in. */
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val repository = CalendarRepository(database)

    private val month = MutableStateFlow(YearMonth.now())

    private val firstMonth = MutableStateFlow<YearMonth?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarUiState> = combine(month, firstMonth, ::Pair)
        .flatMapLatest { (shown, first) ->
            combine(
                repository.observeMonth(shown),
                database.sessionRepository().observeLastTrained(),
                database.profileRepository().observeDisplayUnit(),
            ) { days, lastTrained, unit ->
                CalendarUiState(
                    month = shown,
                    days = days,
                    canGoBack = first != null && shown > first,
                    canGoForward = shown < YearMonth.now(),
                    lastTrained = lastTrained,
                    lifterUnit = unit,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CalendarUiState(YearMonth.now()))

    /** Opens on this month, and finds out again how far back there is anything (an import moves it). */
    fun open() {
        month.value = YearMonth.now()
        viewModelScope.launch { firstMonth.value = repository.firstMonth() }
    }

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
