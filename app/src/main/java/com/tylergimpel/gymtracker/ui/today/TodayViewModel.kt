package com.tylergimpel.gymtracker.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.sessionRepository
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.estimatedLengthOf
import com.tylergimpel.gymtracker.ui.MuscleGroup
import com.tylergimpel.gymtracker.ui.muscleGroupsOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The routine day Today puts in front of the lifter, read the way the hero card shows it. */
data class NextDay(
    val day: RoutineDay,
    /** Where it sits in the rotation, from 1, out of [of]. */
    val position: Int,
    val of: Int,
    val muscles: List<MuscleGroup>,
    val length: Duration,
)

data class TodayUiState(
    /** False until the routine has been read, so the screen does not flash "no routine" first. */
    val loaded: Boolean = false,
    val progress: RoutineProgress? = null,
    val next: NextDay? = null,
    /** A session still open today, which Today offers to go back to rather than start another. */
    val running: LoggedSession? = null,
    /** Monday to Sunday of this week. */
    val week: List<LocalDate> = emptyList(),
    /** The days of [week] something was trained on. */
    val trained: Set<LocalDate> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val last: LoggedSession? = null,
    /** The day being asked about skipping, while it is. */
    val skipping: RoutineDay? = null,
)

/**
 * Today: what comes next, this week, and the last time (#141).
 *
 * Reads what the session screen reads for the cursor, so Start here and the day the session screen
 * takes a copy of cannot disagree.
 */
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val clock = Clock.systemDefaultZone()
    private val zone = ZoneId.systemDefault()
    private val database = GymDatabase.getInstance(application)
    private val sessions = database.sessionRepository()
    private val routines = RoutineRepository(database, database.routineDao(), database.sessionDao(), clock)

    private val skipping = MutableStateFlow<RoutineDay?>(null)

    private val today = LocalDate.now(clock)
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val week = (0L..6L).map(monday::plusDays)

    private val trained = database.sessionDao().observeCalendar(week.first(), week.last())
        .map { rows -> rows.filter { it.exercise != null }.map { it.date }.toSet() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = combine(
        routines.observeProgress(),
        sessions.observeActiveSession().flatMapLatest { active ->
            if (active == null || !active.accepts(clock.instant(), zone)) flowOf(null) else sessions.observeSession(active.id)
        },
        sessions.observeLastFinished(),
        trained,
        skipping,
    ) { progress, active, last, trained, skipping ->
        TodayUiState(
            loaded = true,
            progress = progress,
            next = progress?.let(::nextOf),
            running = active,
            week = week,
            trained = trained,
            today = today,
            last = last,
            skipping = skipping,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState(week = week))

    private fun nextOf(progress: RoutineProgress): NextDay? {
        val day = progress.cursor ?: return null
        val rotation = progress.routine.rotation
        return NextDay(
            day = day,
            position = rotation.indexOfFirst { it.id == day.id } + 1,
            of = rotation.size,
            muscles = muscleGroupsOf(day.exercises.map { it.exercise }),
            length = progress.routine.estimatedLengthOf(day),
        )
    }

    fun startSkipping() = skipping.update { state.value.next?.day }

    fun cancelSkipping() = skipping.update { null }

    /** Records the day as dealt with without training it; asked first, because nothing undoes it. */
    fun skip() {
        val routine = state.value.progress?.routine ?: return
        val day = skipping.value ?: return
        viewModelScope.launch {
            routines.recordSkip(routine.id, day)
            skipping.update { null }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
