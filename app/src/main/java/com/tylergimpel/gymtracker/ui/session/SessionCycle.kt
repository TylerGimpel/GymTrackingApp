package com.tylergimpel.gymtracker.ui.session

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The session being refiled onto another day, and the day it currently sits on. */
data class Refiling(val sessionId: Long, val date: LocalDate)

/** The three questions this screen asks about a session as a whole rather than about a set. */
data class CycleState(
    /** The routine day the lifter is being asked about skipping, if they are being asked. */
    val skipping: RoutineDay? = null,
    /** The session being moved to another date, if one is. */
    val refiling: Refiling? = null,
    /** Whether the lifter is being asked which day this session realised. */
    val finishing: Boolean = false,
)

/**
 * Starting a day, skipping one, refiling a session and finishing one.
 *
 * Its own thing because it is a different question from what weight goes on the bar: all of it
 * is about where a session sits in a **Cycle**, and none of it is about the set in front of the
 * lifter (#63).
 *
 * **The routine is passed in rather than watched.** The progress flow belongs to the screen that
 * renders it, and a second subscription here would be a second answer to the same question — so
 * every function that needs to know what day it is gets told.
 */
class SessionCycle(
    private val scope: CoroutineScope,
    private val database: GymDatabase,
    private val repository: SessionRepository,
    private val routines: RoutineRepository,
) {
    private val held = MutableStateFlow(CycleState())

    val state: StateFlow<CycleState> = held.asStateFlow()

    /** Starts the day the cursor points at, with its exercises as the session's **Plan**. */
    fun startDay(progress: RoutineProgress?) {
        val day = progress?.cursor ?: return
        scope.launch { repository.startSession(SessionPlan(day.exercises)) }
    }

    /** Opens the question of skipping the day the cursor points at. */
    fun startSkipping(progress: RoutineProgress?) {
        val day = progress?.cursor ?: return
        held.update { it.copy(skipping = day) }
    }

    fun cancelSkipping() = held.update { it.copy(skipping = null) }

    /**
     * Marks the day as dealt with, without a session.
     *
     * The escape hatch a derived cursor structurally requires: without it the only way past a day
     * is to train it, and a lifter who is never going to do day 2 as written would have nowhere to
     * go short of revising the routine (ADR 0001). Recorded apart from any session, so history
     * never claims training that did not happen.
     *
     * Asked before it is written, because there is no unskipping it: the record is permanent for
     * that cycle, and repeated skips of one day are meant to become a signal about the routine
     * rather than a tally of mis-taps.
     */
    fun skipDay(progress: RoutineProgress?) {
        val routine = progress?.routine ?: return
        val day = held.value.skipping ?: return
        scope.launch {
            routines.recordSkip(routine.id, day)
            held.update { it.copy(skipping = null) }
        }
    }

    fun startRefiling(session: Session) =
        held.update { it.copy(refiling = Refiling(session.id, session.date)) }

    fun cancelRefiling() = held.update { it.copy(refiling = null) }

    /**
     * Refiles the session onto [date] — the fix for training on Saturday and remembering on Monday.
     *
     * When it was trained is untouched, and so is the cursor: a session filed on a day other than
     * the one it was trained on records no completion when it finishes.
     */
    fun refileOn(date: LocalDate) {
        val open = held.value.refiling ?: return
        scope.launch {
            repository.refile(open.sessionId, date)
            held.update { it.copy(refiling = null) }
        }
    }

    /**
     * Opens the question of which routine day this session realised, or just finishes it.
     *
     * A routine with no days has nothing to ask about, so asking would be a dialog with one
     * answer. [finishNow] is what the caller does in that case, which is finish.
     */
    fun startFinishing(progress: RoutineProgress?, finishNow: () -> Unit) {
        if (progress?.routine?.days.orEmpty().isEmpty()) {
            finishNow()
        } else {
            held.update { it.copy(finishing = true) }
        }
    }

    fun cancelFinishing() = held.update { it.copy(finishing = false) }

    /**
     * Closes [sessionId], and records [day] as trained if one was named.
     *
     * Both writes go in one transaction: a finished session that failed to advance the cycle would
     * leave the cursor pointing at work already done, with nothing to show why.
     *
     * [onFinished] is what the rest of the screen does about a session ending — putting the set
     * being entered away — which is not this class's business and is why it is handed back.
     */
    fun finish(
        sessionId: Long,
        progress: RoutineProgress?,
        day: RoutineDay?,
        onFinished: () -> Unit,
    ) {
        val routine = progress?.routine
        scope.launch {
            database.withTransaction {
                repository.finishSession(sessionId, routine.takeIf { day != null }, day)
                if (routine != null && day != null) {
                    routines.recordCompletion(routine.id, day, sessionId)
                }
            }
            held.update { it.copy(finishing = false) }
            onFinished()
        }
    }
}
