package com.tylergimpel.traintracks.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.RoutineProgress
import com.tylergimpel.traintracks.data.RoutineRepository
import com.tylergimpel.traintracks.data.sessionRepository
import com.tylergimpel.traintracks.domain.LoggedSession
import com.tylergimpel.traintracks.domain.RoutineDay
import com.tylergimpel.traintracks.domain.Session
import com.tylergimpel.traintracks.domain.estimatedLengthOf
import com.tylergimpel.traintracks.ui.MuscleGroup
import com.tylergimpel.traintracks.ui.muscleGroupsOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
import java.time.Instant
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
    /** Required rather than defaulted, so that no state can carry a day read when it was made. */
    val today: LocalDate,
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

    /**
     * The moment, read again whenever what Today shows can change without the database saying so.
     *
     * A ViewModel outlives the night it was made on, so a day read once would leave Today showing
     * yesterday, and last week, to a lifter who left the app open. Read on every collection too,
     * so coming back to the app reads the clock afresh rather than trusting a tick that slept.
     */
    private val now: Flow<Instant> = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(Duration.between(now, nextTurnAfter(now, zone)).toMillis().coerceAtLeast(1))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thisWeek: Flow<ThisWeek> = now.map { it.atZone(zone).toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val week = weekOf(today)
            database.sessionDao().observeCalendar(week.first(), week.last()).map { rows ->
                ThisWeek(today, week, trained = rows.filter { it.exercise != null }.map { it.date }.toSet())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = combine(
        routines.observeProgress(),
        // Asked again at each turn of the clock as well as at each change to the session: last
        // night's unfinished session stops being one to go back to when the training day ends.
        combine(sessions.observeActiveSession(), now) { active, now -> active?.takeIf { it.accepts(now, zone) }?.id }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else sessions.observeSession(id) },
        sessions.observeLastFinished(),
        thisWeek,
        skipping,
    ) { progress, active, last, thisWeek, skipping ->
        TodayUiState(
            loaded = true,
            progress = progress,
            next = progress?.let(::nextOf),
            running = active,
            week = thisWeek.week,
            trained = thisWeek.trained,
            today = thisWeek.today,
            last = last,
            skipping = skipping,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), placeholder())

    /** Until the first reading lands: the week drawn empty rather than not at all. */
    private fun placeholder(): TodayUiState {
        val today = LocalDate.now(clock)
        return TodayUiState(week = weekOf(today), today = today)
    }

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

/** The day Today is about, Monday to Sunday around it, and which of those were trained on. */
private data class ThisWeek(val today: LocalDate, val week: List<LocalDate>, val trained: Set<LocalDate>)

/** Monday to Sunday of the week [day] falls in. */
internal fun weekOf(day: LocalDate): List<LocalDate> {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map(monday::plusDays)
}

/**
 * The next moment after [now] that Today has to look at the clock again: midnight, when the day
 * and maybe the week turn over, or the hour a training day ends, whichever comes first.
 */
internal fun nextTurnAfter(now: Instant, zone: ZoneId): Instant {
    val local = now.atZone(zone)
    val midnight = local.toLocalDate().plusDays(1).atStartOfDay(zone)
    val dayEnds = local.toLocalDate().atTime(Session.TRAINING_DAY_ENDS_AT_HOUR, 0).atZone(zone)
    return (if (dayEnds.isAfter(local)) dayEnds else midnight).toInstant()
}
