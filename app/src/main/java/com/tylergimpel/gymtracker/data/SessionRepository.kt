package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.Side
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A session and everything logged in it, ready to show. */
data class LoggedSession(
    val session: Session,
    val exercises: List<SessionExercise>,
)

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
) {
    fun observeActiveSession(): Flow<Session?> =
        sessions.observeActiveSession().map { it?.toDomain() }

    fun observeSession(sessionId: Long): Flow<LoggedSession?> =
        sessions.observeContent(sessionId).map { content ->
            content?.let {
                LoggedSession(
                    session = it.session.toDomain(),
                    exercises = it.exercises.map(SessionExerciseWithSets::toDomain)
                        .sortedBy(SessionExercise::orderIndex),
                )
            }
        }

    /**
     * Records a set against [exerciseId], opening a session and an entry for the exercise if this
     * is the first set of either.
     *
     * Returns null if the draft does not yet read as a weight and a rep count.
     */
    suspend fun logSet(exerciseId: String, draft: SetDraft): Long? {
        val weight = draft.weight ?: return null
        val reps = draft.reps ?: return null

        return database.withTransaction {
            val session = currentSession()
            val performed = sessions.latestSessionExerciseFor(session.id, exerciseId)
                ?: openSessionExercise(session.id, exerciseId)
            sessions.insert(
                SetEntryEntity(
                    sessionExerciseId = performed.id,
                    setIndex = nextSetIndexFor(performed.id),
                    type = draft.type.stored,
                    weightKg = weight.value,
                    reps = reps,
                    note = draft.note,
                    side = null,
                ),
            )
        }
    }

    /**
     * Records one set as two rows, one per side.
     *
     * Both rows take the same set index, because this is one set: a lifter expanding a unilateral
     * exercise to per-side is tracking an imbalance, not doubling their training.
     */
    suspend fun logPerSideSet(exerciseId: String, left: SetDraft, right: SetDraft): Boolean {
        val leftWeight = left.weight ?: return false
        val leftReps = left.reps ?: return false
        val rightWeight = right.weight ?: return false
        val rightReps = right.reps ?: return false

        database.withTransaction {
            val session = currentSession()
            val performed = sessions.latestSessionExerciseFor(session.id, exerciseId)
                ?: openSessionExercise(session.id, exerciseId)
            val setIndex = nextSetIndexFor(performed.id)

            listOf(
                Side.LEFT to (leftWeight to leftReps),
                Side.RIGHT to (rightWeight to rightReps),
            ).forEach { (side, effort) ->
                sessions.insert(
                    SetEntryEntity(
                        sessionExerciseId = performed.id,
                        setIndex = setIndex,
                        type = left.type.stored,
                        weightKg = effort.first.value,
                        reps = effort.second,
                        note = if (side == Side.LEFT) left.note else right.note,
                        side = side.stored,
                    ),
                )
            }
        }
        return true
    }

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

    /** Corrects one set, leaving its index and every other set alone. */
    suspend fun editSet(setId: Long, draft: SetDraft): Boolean {
        val weight = draft.weight ?: return false
        val reps = draft.reps ?: return false
        val existing = sessions.setEntry(setId) ?: return false

        sessions.update(
            existing.copy(
                weightKg = weight.value,
                reps = reps,
                note = draft.note,
                type = draft.type.stored,
            ),
        )
        return true
    }

    /**
     * Closes the session, recording the routine day it realised as copied names if it realised one.
     *
     * Both are optional and stay null for a session trained with no routine at all, which is an
     * ordinary session rather than an incomplete one.
     */
    suspend fun finishSession(sessionId: Long, routine: Routine? = null, day: RoutineDay? = null) {
        val session = sessions.session(sessionId) ?: return
        val finished = session.toDomain().finish(clock.instant())
        sessions.update(
            (if (routine != null && day != null) finished.realising(routine, day) else finished)
                .toEntity(),
        )
    }

    suspend fun noteSession(sessionId: Long, note: String?) {
        val session = sessions.session(sessionId) ?: return
        sessions.update(session.copy(note = note?.takeIf(String::isNotBlank)))
    }

    suspend fun dateSession(sessionId: Long, date: LocalDate) {
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

    private suspend fun nextSetIndexFor(sessionExerciseId: Long): Int =
        SessionExercise.nextSetIndex(sessions.setsFor(sessionExerciseId).toPerformedSets())

    private suspend fun openSessionExercise(sessionId: Long, exerciseId: String): SessionExerciseEntity {
        val performed = SessionExerciseEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = sessions.highestExerciseOrder(sessionId) + 1,
        )
        return performed.copy(id = sessions.insert(performed))
    }
}
