package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.Side
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Records what was actually lifted.
 *
 * The session is implicit: logging a set when nothing is open starts one. That is what lets a
 * lifter open the app in the gym and train without setting anything up first — there is no routine
 * to pick and nothing to start.
 */
class SessionRepository(
    private val database: GymDatabase,
    private val sessions: SessionDao,
    private val clock: Clock,
    private val zone: ZoneId,
    /**
     * Where the unit comes from for an exercise the lifter has said nothing about, so that a logged
     * set and the same exercise in the bank are never read in different units.
     */
    private val profile: ProfileRepository,
) {
    fun observeActiveSession(): Flow<Session?> =
        sessions.observeActiveSession().map { it?.toDomain() }

    /**
     * The session finished most recently, so its date can still be corrected.
     *
     * Kept reachable because realising the date was wrong usually comes after pressing Finish, and
     * a lifter who has to wait for a history screen to fix it will simply live with a session
     * filed on the wrong day.
     */
    fun observeLastFinished(): Flow<LoggedSession?> =
        sessions.observeLastFinished().withLifterUnit { last, unit -> last?.toLoggedSession(unit) }

    /**
     * The most recent finished sessions, newest first — what [unperformedExerciseCounts] reads.
     *
     * A window rather than everything: what a lifter has been avoiding lately is the question, and
     * a routine they abandoned a year ago should not answer it.
     */
    fun observeRecent(limit: Int = RECENT_SESSIONS): Flow<List<LoggedSession>> =
        sessions.observeRecent(limit).withLifterUnit { recent, unit ->
            recent.map { it.toLoggedSession(unit) }
        }

    fun observeSession(sessionId: Long): Flow<LoggedSession?> =
        sessions.observeContent(sessionId).withLifterUnit { content, unit -> content?.toLoggedSession(unit) }

    /**
     * Opens a session for a routine day, recording [plan] as what it is for.
     *
     * The plan is a copy taken here and never a reference: what the routine day holds today is what
     * this session was for, whatever the routine becomes afterwards.
     *
     * Reuses the session already being logged if there is one, and leaves its plan alone — a
     * session is planned once, and starting again is not re-planning it.
     */
    suspend fun startSession(plan: SessionPlan): Long = database.withTransaction {
        val session = currentSession()
        plan(session.id, plan)
        session.id
    }

    /**
     * Records a set against [exerciseId], opening a session and an entry for the exercise if this
     * is the first set of either.
     *
     * [exerciseUnit] is what the exercise is read in, which is all this needs to know to decide
     * whether the set departed from it. What is written is canonical kilograms either way.
     *
     * Returns false if the draft does not yet read as a weight and a rep count.
     */
    suspend fun logSet(
        exerciseId: String,
        draft: SetDraft,
        exerciseUnit: WeightUnit = WeightUnit.DEFAULT,
    ): Boolean {
        val performance = draft.performance ?: return false
        val displayUnit = draft.displayUnit.overriding(exerciseUnit)

        database.withTransaction {
            val session = currentSession()
            val performed = sessions.latestSessionExerciseFor(session.id, exerciseId)
                ?: openSessionExercise(session.id, exerciseId)
            sessions.insert(
                SetEntryEntity(
                    sessionExerciseId = performed.id,
                    setIndex = nextSetIndexFor(performed.id),
                    type = draft.type.stored,
                    note = draft.note,
                    side = null,
                    displayUnit = displayUnit?.stored,
                ).recording(performance),
            )
        }
        return true
    }

    /**
     * Records one set as two rows, one per side.
     *
     * Both rows take the same set index, because this is one set: a lifter expanding a unilateral
     * exercise to per-side is tracking an imbalance, not doubling their training.
     */
    suspend fun logPerSideSet(
        exerciseId: String,
        left: SetDraft,
        right: SetDraft,
        exerciseUnit: WeightUnit = WeightUnit.DEFAULT,
    ): Boolean {
        if (!left.isLoggable || !right.isLoggable) return false
        val displayUnit = left.displayUnit.overriding(exerciseUnit)

        database.withTransaction {
            val session = currentSession()
            val performed = sessions.latestSessionExerciseFor(session.id, exerciseId)
                ?: openSessionExercise(session.id, exerciseId)
            val setIndex = nextSetIndexFor(performed.id)

            // The type and the unit come from the left draft for both rows: both belong to the
            // set, and the controls only offer one chooser of each for that reason.
            listOf(Side.LEFT to left, Side.RIGHT to right).forEach { (side, entered) ->
                sessions.insert(
                    SetEntryEntity(
                        sessionExerciseId = performed.id,
                        setIndex = setIndex,
                        type = left.type.stored,
                        note = entered.note,
                        side = side.stored,
                        displayUnit = displayUnit?.stored,
                    ).recording(requireNotNull(entered.performance)),
                )
            }
        }
        return true
    }

    /**
     * Puts the exercise after [sessionExerciseId] into a superset with it, forming one if needed.
     *
     * Grows a group a member at a time, which is how a triple or a quintuple gets built: group the
     * first with the second, then the second with the third.
     *
     * Where either exercise is already grouped, that group is the one they end up in, and two
     * different groups **merge**. Overwriting the next exercise's group instead would take a member
     * out of it and leave whatever remained behind alone in a superset of one, which is not a
     * superset.
     *
     * Returns false where there is no exercise after this one to alternate with.
     */
    suspend fun groupWithNext(sessionExerciseId: Long): Boolean = database.withTransaction {
        val performed = sessions.sessionExercise(sessionExerciseId)
            ?: return@withTransaction false
        val next = sessions.exerciseAfter(performed.sessionId, performed.orderIndex)
            ?: return@withTransaction false

        val target = performed.supersetGroupId
            ?: next.supersetGroupId
            ?: sessions.insert(
                SupersetGroupEntity(
                    sessionId = performed.sessionId,
                    orderIndex = sessions.highestSupersetOrder(performed.sessionId) + 1,
                    // On by default: the point of a superset is not stopping, and being asked to
                    // pick the next exercise between members is the stopping.
                    autoJump = true,
                    restSeconds = null,
                ),
            )

        next.supersetGroupId?.takeIf { it != target }?.let { merged ->
            sessions.moveSupersetMembers(from = merged, to = target)
            sessions.deleteSuperset(merged)
        }

        sessions.update(performed.copy(supersetGroupId = target))
        sessions.update(next.copy(supersetGroupId = target))
        true
    }

    /** Breaks a group up, leaving every set logged against its members exactly as it was. */
    suspend fun ungroup(supersetId: Long) = sessions.deleteSuperset(supersetId)

    /**
     * Says whether this group moves to its next member on its own after a set is logged.
     *
     * A real choice rather than an assumption: alternating without being asked is the point for
     * most lifters, and walking between two machines across a gym is the case where it is not.
     */
    suspend fun setAutoJump(supersetId: Long, autoJump: Boolean) =
        sessions.setAutoJump(supersetId, autoJump)

    /** Continues an effort at a reduced weight, with no rest. Never a set of its own. */
    suspend fun addDrop(effortId: Long, weight: Kilograms, reps: Int): Long =
        database.withTransaction {
            sessions.insert(
                DropSegmentEntity(
                    setEntryId = effortId,
                    orderIndex = sessions.highestDropOrder(effortId) + 1,
                    weightKg = weight.value,
                    reps = reps,
                ),
            )
        }

    suspend fun deleteDrop(dropId: Long) = sessions.deleteDrop(dropId)

    /** Removes a whole set, both its rows if it has two, and every drop hanging off them. */
    suspend fun deleteSet(sessionExerciseId: Long, setIndex: Int) =
        sessions.deleteSetAt(sessionExerciseId, setIndex)

    /**
     * Corrects one effort, leaving its index and every other set alone.
     *
     * The weight, reps and note belong to the effort, so only that row changes. The type and the
     * unit belong to the set, so every row of it is restated together — otherwise the two sides of
     * a per-side set could disagree about whether the set counts at all, or about what its numbers
     * are read in.
     */
    suspend fun editSet(
        effortId: Long,
        draft: SetDraft,
        exerciseUnit: WeightUnit = WeightUnit.DEFAULT,
    ): Boolean {
        val performance = draft.performance ?: return false
        val existing = sessions.setEntry(effortId) ?: return false
        val displayUnit = draft.displayUnit.overriding(exerciseUnit)

        database.withTransaction {
            sessions.update(
                existing.copy(note = draft.note).recording(performance),
            )
            sessions.retypeSet(existing.sessionExerciseId, existing.setIndex, draft.type.stored)
            sessions.redisplaySet(
                existing.sessionExerciseId,
                existing.setIndex,
                displayUnit?.stored,
            )
        }
        return true
    }

    /**
     * Closes the session, recording the routine day it realised as copied names if it realised one.
     *
     * Both are optional and stay null for a session trained with no routine at all, which is an
     * ordinary session rather than an incomplete one.
     */
    suspend fun finishSession(sessionId: Long, routine: Routine? = null, day: RoutineDay? = null) {
        database.withTransaction {
            val session = sessions.session(sessionId) ?: return@withTransaction
            val finished = session.toDomain().finish(clock.instant())
            sessions.update(
                (if (routine != null && day != null) finished.realising(routine, day) else finished)
                    .toEntity(),
            )
            // A session that names its day only at the end is planned here instead. ADR 0006 leans
            // on every session that realised a day carrying that day's contents — it is what makes
            // a past routine recoverable from the log — so the snapshot cannot depend on the lifter
            // having pressed Start. Later than "when it begins", and the same list unless the
            // routine changed mid-session, which is the best that can honestly be had for a session
            // that did not say what it was for until it was over.
            if (day != null) plan(sessionId, SessionPlan(day.exercises))
        }
    }

    /**
     * Records [plan] as what a session was for, unless it already carries one.
     *
     * A session is planned once. Whichever moment gets there first — starting from a day, or naming
     * one at the end — is the one that took the snapshot, and the other leaves it alone.
     */
    private suspend fun plan(sessionId: Long, plan: SessionPlan) {
        if (sessions.plannedCount(sessionId) > 0) return
        plan.exercises.forEachIndexed { index, exercise ->
            sessions.insert(
                SessionPlanEntity(
                    sessionId = sessionId,
                    exerciseId = exercise.id,
                    orderIndex = index + 1,
                ),
            )
        }
    }

    suspend fun noteSession(sessionId: Long, note: String?) {
        val session = sessions.session(sessionId) ?: return
        sessions.update(session.copy(note = note?.takeIf(String::isNotBlank)))
    }

    /** Refiles the session onto [date]. When it was trained is untouched. */
    suspend fun refile(sessionId: Long, date: LocalDate) {
        val session = sessions.session(sessionId) ?: return
        sessions.update(session.toDomain().on(date).toEntity())
    }

    /**
     * The session this set belongs to.
     *
     * An open session is reused only while it still accepts sets. One left unfinished is not
     * reopened days later: it stays unfinished, which is what actually happened, and the new
     * session becomes the current one.
     */
    private suspend fun currentSession(): SessionEntity {
        val now = clock.instant()
        val open = sessions.activeSession()
        return open?.takeIf { it.toDomain().accepts(now, zone) } ?: openSession()
    }

    private suspend fun openSession(): SessionEntity {
        val started: Instant = clock.instant()
        val session = Session.start(started, zone).toEntity()
        return session.copy(id = sessions.insert(session))
    }

    private companion object {
        /** How far back the revealed-preference counts look. Recent enough to be about now. */
        const val RECENT_SESSIONS = 8
    }

    private suspend fun nextSetIndexFor(sessionExerciseId: Long): Int =
        SessionExercise.nextSetIndex(sessions.setsFor(sessionExerciseId).toPerformedSets())

    /**
     * Reads what comes out of storage alongside the unit this lifter reads in.
     *
     * The unit is a property of who is looking rather than of what happened, so it joins here on the
     * way out rather than being stored on anything: changing it re-reads the whole log and moves not
     * one logged kilogram.
     */
    private fun <T, R> Flow<T>.withLifterUnit(read: (T, WeightUnit) -> R): Flow<R> =
        combine(profile.observeDisplayUnit(), read)

    private fun SessionWithContent.toLoggedSession(lifterUnit: WeightUnit) = LoggedSession(
        session = session.toDomain(),
        exercises = exercises.map { it.toDomain(lifterUnit) }
            .sortedBy(SessionExercise::orderIndex),
        plan = plan.toSessionPlan(lifterUnit),
        supersets = supersets.map(SupersetGroupEntity::toDomain),
    )

    private suspend fun openSessionExercise(sessionId: Long, exerciseId: String): SessionExerciseEntity {
        val performed = SessionExerciseEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = sessions.highestExerciseOrder(sessionId) + 1,
        )
        return performed.copy(id = sessions.insert(performed))
    }
}
