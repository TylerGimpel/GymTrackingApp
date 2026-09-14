package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Cycle
import com.tylergimpel.gymtracker.domain.DayOutcome
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock

/** A routine together with where it has got to, and therefore what comes next. */
data class RoutineProgress(
    val routine: Routine,
    val cycle: Cycle?,
) {
    /** The first routine day in the current cycle not yet completed or skipped. */
    val cursor: RoutineDay? get() = routine.cursor(cycle)
}

/**
 * Holds the routine and the cycle state the cursor is derived from.
 *
 * Nothing here consults the date to decide what comes next. `started_at` on a cycle is recorded for
 * history, and no query orders or filters by it — which is what makes a fortnight off free.
 */
class RoutineRepository(
    private val database: GymDatabase,
    private val routines: RoutineDao,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProgress(): Flow<RoutineProgress?> =
        routines.observeRoutine().flatMapLatest { routine ->
            if (routine == null) {
                flowOf(null)
            } else {
                combine(
                    routines.observeDays(routine.id),
                    routines.observeLatestCycle(routine.id),
                ) { days, cycle -> routine to (days to cycle) }
                    .flatMapLatest { (routineEntity, state) ->
                        val (days, cycle) = state
                        if (cycle == null) {
                            flowOf(RoutineProgress(routineEntity.toDomain(days), cycle = null))
                        } else {
                            combine(
                                routines.observeCompletions(cycle.id),
                                routines.observeSkips(cycle.id),
                            ) { completions, skips ->
                                RoutineProgress(
                                    routine = routineEntity.toDomain(days),
                                    cycle = cycle.toDomain(completions, skips),
                                )
                            }
                        }
                    }
            }
        }

    suspend fun createRoutine(
        name: String,
        goal: RoutineGoal = RoutineGoal.HYPERTROPHY,
        cadence: Int = 3,
        equipment: Set<String> = emptySet(),
        repPreference: RepPreference = RepPreference.MODERATE,
    ): Long = routines.insert(
        RoutineEntity(
            name = name,
            goal = goal.name,
            cadence = cadence,
            equipment = equipment.toList(),
            repPreference = repPreference.name,
        ),
    )

    /** Edits the routine's properties. Its day structure is untouched, so no cycle ends. */
    suspend fun editProperties(
        routineId: Long,
        name: String,
        goal: RoutineGoal,
        cadence: Int,
        equipment: Set<String>,
        repPreference: RepPreference,
    ) {
        val existing = routines.routine()?.takeIf { it.id == routineId } ?: return
        routines.update(
            existing.copy(
                name = name,
                goal = goal.name,
                cadence = cadence,
                equipment = equipment.toList(),
                repPreference = repPreference.name,
            ),
        )
    }

    suspend fun addDay(routineId: Long, name: String, isCardio: Boolean = false): Long =
        routines.insert(
            RoutineDayEntity(
                routineId = routineId,
                orderIndex = routines.highestDayOrder(routineId) + 1,
                name = name,
                isCardio = isCardio,
            ),
        )

    suspend fun renameDay(dayId: Long, name: String) = database.withTransaction {
        val routineId = routines.routine()?.id ?: return@withTransaction
        routines.days(routineId).firstOrNull { it.id == dayId }?.let {
            routines.update(it.copy(name = name))
        }
    }

    suspend fun removeDay(dayId: Long) = database.withTransaction {
        routines.deleteDay(dayId)
        renumber()
    }

    /** Moves a day one position earlier or later in the rotation. */
    suspend fun moveDay(dayId: Long, by: Int) = database.withTransaction {
        val routineId = routines.routine()?.id ?: return@withTransaction
        val days = routines.days(routineId).toMutableList()
        val from = days.indexOfFirst { it.id == dayId }
        val to = from + by
        if (from < 0 || to !in days.indices) return@withTransaction
        days.add(to, days.removeAt(from))
        days.forEachIndexed { index, day -> routines.update(day.copy(orderIndex = index + 1)) }
    }

    suspend fun deleteRoutine(routineId: Long) {
        routines.routine()?.takeIf { it.id == routineId }?.let { routines.delete(it) }
    }

    /** Records that [day] was trained, by the session that realised it. */
    suspend fun recordCompletion(routineId: Long, day: RoutineDay, sessionId: Long) =
        database.withTransaction {
            val cycle = currentCycle(routineId)
            routines.insert(
                CycleCompletionEntity(
                    cycleId = cycle.id,
                    routineDayId = day.id,
                    sessionId = sessionId,
                ),
            )
        }

    /** Records that [day] was dealt with without training it — the escape hatch past a day. */
    suspend fun recordSkip(routineId: Long, day: RoutineDay) = database.withTransaction {
        val cycle = currentCycle(routineId)
        routines.insert(
            SkipEntity(
                cycleId = cycle.id,
                routineDayId = day.id,
                recordedAt = clock.instant(),
            ),
        )
    }

    /**
     * The cycle that new outcomes belong to.
     *
     * A cycle with every day dealt with is over, so the next outcome opens the following one. This
     * is where the roll-over actually happens; the cursor only ever reports it.
     */
    private suspend fun currentCycle(routineId: Long): CycleEntity {
        val days = routines.days(routineId)
        val latest = routines.latestCycle(routineId)
        if (latest != null) {
            val outcomes = latest.toDomain(
                routines.completions(latest.id),
                routines.skips(latest.id),
            )
            val routine = routines.routine()?.toDomain(days) ?: return latest
            if (!outcomes.isComplete(routine)) return latest
        }
        val next = CycleEntity(
            routineId = routineId,
            ordinal = (latest?.ordinal ?: 0) + 1,
            startedAt = clock.instant(),
        )
        return next.copy(id = routines.insert(next))
    }

    private suspend fun renumber() {
        val routineId = routines.routine()?.id ?: return
        routines.days(routineId).forEachIndexed { index, day ->
            if (day.orderIndex != index + 1) routines.update(day.copy(orderIndex = index + 1))
        }
    }
}

fun CycleEntity.toDomain(
    completions: List<CycleCompletionEntity>,
    skips: List<SkipEntity>,
) = Cycle(
    id = id,
    ordinal = ordinal,
    outcomes = buildMap {
        // A skip and a completion cannot both be true of a day, but if a race ever produced both,
        // the completion is the one that reflects training actually happening.
        skips.forEach { put(it.routineDayId, DayOutcome.SKIPPED) }
        completions.forEach { put(it.routineDayId, DayOutcome.COMPLETED) }
    },
)
