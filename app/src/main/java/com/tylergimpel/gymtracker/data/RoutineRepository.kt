package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Cycle
import com.tylergimpel.gymtracker.domain.DayOutcome
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * A routine together with where it has got to, and therefore what comes next.
 *
 * "Progress" is not a glossary term — this is a read model pairing the two things the cursor is
 * derived from, so that no caller can ask for one without the other and get a stale answer.
 */
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
 * **The cursor is derived without reference to any date.** `started_at` on a cycle is recorded for
 * history, and no query orders or filters by it — which is what makes a fortnight off free.
 *
 * One date is read, in one place: [recordCompletion] asks whether the session in front of it was
 * trained on the day it claims, because a session logged after the fact must not advance a cycle.
 * That decides whether an outcome is *recorded*; it never touches how the cursor is *derived* from
 * the outcomes that are.
 */
class RoutineRepository(
    private val database: GymDatabase,
    private val routines: RoutineDao,
    private val sessions: SessionDao,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProgress(): Flow<RoutineProgress?> =
        routines.observeRoutine().flatMapLatest { routine ->
            if (routine == null) flowOf(null) else observeProgressOf(routine)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProgressOf(routine: RoutineEntity): Flow<RoutineProgress> =
        routines.observeLatestCycle(routine.id).flatMapLatest { cycle ->
            if (cycle == null) {
                routines.observeDays(routine.id).map { days ->
                    RoutineProgress(routine.toDomain(days), cycle = null)
                }
            } else {
                combine(
                    routines.observeDays(routine.id),
                    routines.observeCompletions(cycle.id),
                    routines.observeSkips(cycle.id),
                ) { days, completions, skips ->
                    RoutineProgress(
                        routine = routine.toDomain(days),
                        cycle = cycle.toDomain(completions, skips),
                    )
                }
            }
        }

    suspend fun createRoutine(
        name: String,
        goal: RoutineGoal = RoutineGoal.HYPERTROPHY,
        cadence: Int = 3,
        equipment: Set<String> = emptySet(),
        repPreference: RepPreference = RepPreference.MODERATE,
        cardio: CardioPlacement = CardioPlacement.NONE,
    ): Long = routines.insert(
        RoutineEntity(
            name = name,
            goal = goal.name,
            cadence = cadence,
            equipment = equipment.toList(),
            repPreference = repPreference.name,
            cardio = cardio.name,
        ),
    )

    /**
     * Edits the routine's properties.
     *
     * Not a revision: the rotation is untouched, so no cycle ends. Changing a goal or the equipment
     * at a gym should not cost a lifter their place in the routine.
     */
    suspend fun editProperties(
        name: String,
        goal: RoutineGoal,
        cadence: Int,
        equipment: Set<String>,
        repPreference: RepPreference,
        cardio: CardioPlacement = CardioPlacement.NONE,
    ) {
        val existing = routines.routine() ?: return
        routines.update(
            existing.copy(
                name = name,
                goal = goal.name,
                cadence = cadence,
                equipment = equipment.toList(),
                repPreference = repPreference.name,
                cardio = cardio.name,
            ),
        )
    }

    /** Adds a day to the end of the rotation. A revision, so the current cycle ends. */
    suspend fun addDay(
        routineId: Long,
        name: String,
        kind: RoutineDayKind = RoutineDayKind.LIFTING,
    ): Long = database.withTransaction {
        val id = routines.insert(
            RoutineDayEntity(
                routineId = routineId,
                orderIndex = routines.highestDayOrder(routineId) + 1,
                name = name,
                kind = kind.name,
            ),
        )
        revise(routineId)
        id
    }

    /**
     * Renames a day.
     *
     * Not a revision: the rotation still holds the same days in the same order, and completions are
     * keyed by day rather than by name, so nothing about the cycle becomes ambiguous.
     */
    suspend fun renameDay(dayId: Long, name: String) = database.withTransaction {
        val routineId = routines.routine()?.id ?: return@withTransaction
        routines.days(routineId).firstOrNull { it.id == dayId }?.let {
            routines.update(it.copy(name = name))
        }
    }

    /** Removes a day from the rotation. A revision, so the current cycle ends. */
    suspend fun removeDay(dayId: Long) = database.withTransaction {
        val routineId = routines.routine()?.id ?: return@withTransaction
        routines.deleteDay(dayId)
        renumber(routineId)
        revise(routineId)
    }

    /** Moves a day one position earlier or later. A revision, so the current cycle ends. */
    suspend fun moveDay(dayId: Long, by: Int) = database.withTransaction {
        val routineId = routines.routine()?.id ?: return@withTransaction
        val days = routines.days(routineId).toMutableList()
        val from = days.indexOfFirst { it.id == dayId }
        val to = from + by
        if (from < 0 || to !in days.indices) return@withTransaction
        days.add(to, days.removeAt(from))
        days.forEachIndexed { index, day -> routines.update(day.copy(orderIndex = index + 1)) }
        revise(routineId)
    }

    /**
     * Adds [exerciseId] to the end of [dayId]'s list, or leaves it where it is if already there.
     *
     * Not a revision: the rotation still holds the same days in the same order, so the cycle and
     * the cursor are untouched (ADR 0006). Filling in a day mid-cycle is ordinary, and costing a
     * lifter their place for it would be absurd.
     */
    suspend fun addExerciseToDay(dayId: Long, exerciseId: String) = database.withTransaction {
        routines.insert(
            RoutineDayExerciseEntity(
                routineDayId = dayId,
                exerciseId = exerciseId,
                orderIndex = routines.highestDayExerciseOrder(dayId) + 1,
            ),
        )
    }

    /** Takes [exerciseId] out of [dayId]'s list. Not a revision, for the same reason. */
    suspend fun removeExerciseFromDay(dayId: Long, exerciseId: String) =
        routines.deleteDayExercise(dayId, exerciseId)

    suspend fun deleteRoutine() {
        routines.routine()?.let { routines.delete(it) }
    }

    /**
     * Records that [day] was trained, by the session that realised it.
     *
     * A backdated session records nothing at all — see [Session.isBackdated] for why the cursor
     * refuses to move for one.
     *
     * The session is read here rather than passed in, so the date the rule judges is the one
     * actually stored at the moment the outcome is written. A caller holding a copy taken before
     * it refiled the session would otherwise get the opposite answer, silently.
     *
     * Asked once, as the session is finished. Correcting a date afterwards does not un-train the
     * day: that session did realise it, and the cursor moved because the training happened.
     */
    suspend fun recordCompletion(routineId: Long, day: RoutineDay, sessionId: Long) =
        database.withTransaction {
            val session = sessions.session(sessionId)?.toDomain()
            if (session == null || session.isBackdated(clock.zone)) return@withTransaction
            val cycle = currentCycle(routineId)
            routines.insert(
                CycleCompletionEntity(
                    cycleId = cycle.id,
                    routineDayId = day.id,
                    sessionId = sessionId,
                ),
            )
            openNextCycleIfComplete(routineId, cycle)
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
        openNextCycleIfComplete(routineId, cycle)
    }

    /** The cycle in progress, opening the first one if the routine has never started. */
    private suspend fun currentCycle(routineId: Long): CycleEntity =
        routines.latestCycle(routineId) ?: openCycle(routineId, ordinal = 1)

    /**
     * Ends a cycle the moment its last day is dealt with rather than when the next outcome arrives,
     * so stored state says what the cursor says: the cycle showing is the one being trained.
     */
    private suspend fun openNextCycleIfComplete(routineId: Long, cycle: CycleEntity) {
        val routine = routines.routine()?.toDomain(routines.daysWithExercises(routineId)) ?: return
        val outcomes = cycle.toDomain(routines.completions(cycle.id), routines.skips(cycle.id))
        if (outcomes.isComplete(routine)) openCycle(routineId, cycle.ordinal + 1)
    }

    /**
     * Ends the current cycle and starts the next one, with everything undone.
     *
     * A change to which days are in the rotation, or to their order, is a revision, and ADR 0006
     * ends the cycle rather than trying to carry completions across it. Without this, adding a day
     * to a cycle that had already finished reopens it and points the lifter at the day they have
     * just invented, with every other day still stamped done from a cycle that is over.
     */
    private suspend fun revise(routineId: Long) {
        val latest = routines.latestCycle(routineId) ?: return
        openCycle(routineId, latest.ordinal + 1)
    }

    private suspend fun openCycle(routineId: Long, ordinal: Int): CycleEntity {
        val cycle = CycleEntity(
            routineId = routineId,
            ordinal = ordinal,
            startedAt = clock.instant(),
        )
        return cycle.copy(id = routines.insert(cycle))
    }

    private suspend fun renumber(routineId: Long) {
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
