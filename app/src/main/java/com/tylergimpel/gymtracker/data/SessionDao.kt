package com.tylergimpel.gymtracker.data

import java.time.LocalDate
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** The session still being logged, if there is one. Unfinished is what makes it current. */
    @Query("SELECT * FROM session WHERE finished_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE finished_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    /**
     * The session finished most recently, if there is one.
     *
     * Ordered by when it was finished rather than by the date it carries, because the point of it
     * is to be the session a lifter has just closed and is about to realise they misdated — and
     * refiling is exactly the operation that would move it out of its own way.
     */
    @Transaction
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY finished_at DESC LIMIT 1")
    fun observeLastFinished(): Flow<SessionWithContent?>

    /**
     * The most recent finished sessions, newest first.
     *
     * What the revealed-preference counts are derived from: an exercise avoided once is nothing,
     * and the same exercise unperformed across most of the sessions that asked for it is a signal.
     */
    @Transaction
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY finished_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionWithContent>>

    /**
     * The most recent finished sessions and the plans they were trained against, newest first.
     *
     * What rebuilding a routine out of the log reads. Deliberately not [observeRecent]: this
     * window reaches back years rather than weeks, and every set it did not fetch is a set the
     * rebuild had no use for.
     */
    @Transaction
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY finished_at DESC LIMIT :limit")
    suspend fun recentPlans(limit: Int): List<SessionWithPlan>

    @Transaction
    @Query("SELECT * FROM session WHERE id = :sessionId")
    fun observeContent(sessionId: Long): Flow<SessionWithContent?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :sessionId")
    suspend fun session(sessionId: Long): SessionEntity?

    /**
     * The most recent entry for this exercise in this session, if any.
     *
     * An exercise may legitimately appear twice in one session, so new sets join the latest entry
     * rather than the first.
     */
    @Query(
        """
        SELECT * FROM session_exercise
        WHERE session_id = :sessionId AND exercise_id = :exerciseId
        ORDER BY order_index DESC LIMIT 1
        """,
    )
    suspend fun latestSessionExerciseFor(sessionId: Long, exerciseId: String): SessionExerciseEntity?

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM session_exercise WHERE session_id = :sessionId")
    suspend fun highestExerciseOrder(sessionId: Long): Int

    @Insert
    suspend fun insert(sessionExercise: SessionExerciseEntity): Long

    @Update
    suspend fun update(sessionExercise: SessionExerciseEntity)

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun sessionExercise(id: Long): SessionExerciseEntity?

    /** The exercise performed after [after] in this session, which is the one a group reaches for. */
    @Query(
        """
        SELECT * FROM session_exercise
        WHERE session_id = :sessionId AND order_index > :after
        ORDER BY order_index LIMIT 1
        """,
    )
    suspend fun exerciseAfter(sessionId: Long, after: Int): SessionExerciseEntity?

    @Insert
    suspend fun insert(superset: SupersetGroupEntity): Long

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM superset_group WHERE session_id = :sessionId")
    suspend fun highestSupersetOrder(sessionId: Long): Int

    /**
     * Breaks a group up.
     *
     * Its members are released by the foreign key rather than by anything here, and nothing it
     * touches is a logged set: grouping was only ever a statement about rest.
     */
    @Query("DELETE FROM superset_group WHERE id = :supersetId")
    suspend fun deleteSuperset(supersetId: Long)

    /** Moves every member of one group into another, which is how two groups become one. */
    @Query("UPDATE session_exercise SET superset_group_id = :to WHERE superset_group_id = :from")
    suspend fun moveSupersetMembers(from: Long, to: Long)

    /** Whether a group is on by default when formed, and what it is set to afterwards. */
    @Query("UPDATE superset_group SET auto_jump = :autoJump WHERE id = :supersetId")
    suspend fun setAutoJump(supersetId: Long, autoJump: Boolean)

    /**
     * How many sets of one type have been logged against one exercise in one session.
     *
     * What tells the ramp how far up it the lifter has got. Sets rather than rows, so a per-side
     * warm-up counts once (ADR 0003).
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT set_entry.session_exercise_id, set_entry.set_index
            FROM set_entry
            JOIN session_exercise ON session_exercise.id = set_entry.session_exercise_id
            WHERE session_exercise.exercise_id = :exerciseId
              AND session_exercise.session_id = :sessionId
              AND set_entry.type = :type
        )
        """,
    )
    suspend fun setsOfTypeInSession(exerciseId: String, sessionId: Long, type: String): Int

    /**
     * Which exercises have been trained in one session, in the order they were performed.
     *
     * What answers "is this muscle already warm": a ramp belongs to the muscle rather than to the
     * exercise, so what came earlier in the evening decides how much of one is left to do.
     */
    @Query(
        """
        SELECT exercise_id FROM session_exercise
        WHERE session_id = :sessionId ORDER BY order_index
        """,
    )
    suspend fun exercisesTrainedIn(sessionId: Long): List<String>

    /**
     * Every exercise anything has ever been logged against.
     *
     * What puts a swap candidate the lifter has done before at the top of the list: those need
     * no calibrating, so switching to one costs a walk across the gym and nothing else.
     */
    @Query("SELECT DISTINCT exercise_id FROM session_exercise")
    suspend fun exercisesEverTrained(): List<String>

    /** The superset an exercise is part of in one session, if it is in one. */
    @Query(
        """
        SELECT superset_group_id FROM session_exercise
        WHERE session_id = :sessionId AND exercise_id = :exerciseId
          AND superset_group_id IS NOT NULL
        ORDER BY order_index DESC LIMIT 1
        """,
    )
    suspend fun supersetOf(sessionId: Long, exerciseId: String): Long?

    /**
     * Records what a group rests for, where the app has worked out that its members want longer.
     *
     * Never shortens one: a group rests for as long as the hardest thing in it asked, and the
     * member being prescribed right now may not be that thing.
     */
    @Query(
        """
        UPDATE superset_group SET rest_seconds = :seconds
        WHERE id = :supersetId AND (rest_seconds IS NULL OR rest_seconds < :seconds)
        """,
    )
    suspend fun restAtLeast(supersetId: Long, seconds: Int)

    @Insert
    suspend fun insert(planned: SessionPlanEntity): Long

    /**
     * Forgets a regeneration the lifter could have stepped back from.
     *
     * Here rather than on the routine DAO because finishing a session is what spends it, and the
     * two happen in one transaction: a session that landed and an undo that survived it would let
     * somebody restore a routine they have since trained past.
     */
    @Query("DELETE FROM routine_day_exercise_undo")
    suspend fun clearRoutineUndo()

    /** Whether this session has already been planned. A session is planned once, when it begins. */
    @Query("SELECT COUNT(*) FROM session_plan WHERE session_id = :sessionId")
    suspend fun plannedCount(sessionId: Long): Int

    /**
     * How many working sets this session was asked for of an exercise, or null where none were.
     *
     * Null for an exercise the plan never named — a swap, or something the lifter simply did —
     * and for every session logged before the app prescribed set counts at all.
     */
    @Query(
        """
        SELECT sets FROM session_plan
        WHERE session_id = :sessionId AND exercise_id = :exerciseId
        """,
    )
    suspend fun plannedSets(sessionId: Long, exerciseId: String): Int?

    @Transaction
    @Query("SELECT * FROM set_entry WHERE session_exercise_id = :sessionExerciseId ORDER BY set_index")
    suspend fun setsFor(sessionExerciseId: Long): List<SetEntryWithDrops>

    /**
     * Every set ever logged against one exercise, oldest first, apart from [excludingSession].
     *
     * What the recommendation engine reads, and the shape of the constraint that matters most in
     * ADR 0004: the exercise id is in the `WHERE`, so no set of any other exercise can reach a
     * recommendation however similar the two movements are.
     *
     * [excludingSession] is the session being logged into. Today's sets are not history yet — an
     * anchor that moved mid-session would raise the weight between set one and set two on the
     * strength of a single good set, which is exactly what "advance only after two consecutive
     * sessions" exists to prevent. Pass [NO_SESSION] where there is none.
     *
     * Ordered by the day trained rather than by when the row was written, so a backdated session
     * lands where it was trained rather than at the end.
     */
    @Query(
        """
        SELECT set_entry.*, session.id AS session_id, session.date AS session_date,
               COALESCE(import_batch.volume_reliable, 1) AS session_volume_reliable
        FROM set_entry
        JOIN session_exercise ON session_exercise.id = set_entry.session_exercise_id
        JOIN session ON session.id = session_exercise.session_id
        LEFT JOIN import_batch ON import_batch.id = session.import_batch_id
        WHERE session_exercise.exercise_id = :exerciseId AND session.id <> :excludingSession
        ORDER BY session.date, session.id, set_entry.set_index, set_entry.id
        """,
    )
    suspend fun trainedSetsFor(exerciseId: String, excludingSession: Long): List<TrainedSetRow>

    /**
     * Every session between [from] and [to], each with the exercises it logged a set against (#118).
     *
     * Exercises only where a set was logged, so an exercise opened and abandoned — or a session
     * started for a routine day and then skipped — trains nothing on the calendar.
     */
    @Query(
        """
        SELECT session.id AS calendar_session_id, session.date AS calendar_date,
               session.routine_day_name AS calendar_day_name,
               (SELECT COUNT(DISTINCT set_entry.set_index) FROM set_entry
                 WHERE set_entry.session_exercise_id = session_exercise.id
                   AND set_entry.type IN ('working', 'backoff', 'amrap')) AS calendar_sets,
               exercise.*
        FROM session
        LEFT JOIN session_exercise ON session_exercise.session_id = session.id
          AND EXISTS (SELECT 1 FROM set_entry WHERE set_entry.session_exercise_id = session_exercise.id)
        LEFT JOIN exercise ON exercise.id = session_exercise.exercise_id
        WHERE session.date BETWEEN :from AND :to
        """,
    )
    fun observeCalendar(from: LocalDate, to: LocalDate): Flow<List<CalendarRow>>

    /** The day of the first session there is, or null with none. */
    @Query("SELECT MIN(date) FROM session")
    suspend fun firstDay(): LocalDate?

    /** Every session on [date], in the order they were started, with everything logged in them (#118). */
    @Transaction
    @Query("SELECT * FROM session WHERE date = :date ORDER BY started_at")
    fun observeOnDate(date: LocalDate): Flow<List<SessionWithContent>>

    /**
     * A page of [exerciseId]'s history, newest first, strictly before a cursor (#121).
     *
     * **Bounded however long the history grows.** Keyed on the day and the row rather than an
     * offset, so the hundredth page costs what the first does: an offset reads and throws away
     * every row before it, and years of training is a lot of rows to throw away per scroll.
     *
     * Only sessions where a set was logged: an exercise opened and left empty was not trained.
     */
    @Transaction
    @Query(
        """
        SELECT session_exercise.*, session.date AS session_date,
               session.import_batch_id AS session_import_batch_id
        FROM session_exercise
        JOIN session ON session.id = session_exercise.session_id
        WHERE session_exercise.exercise_id = :exerciseId
          AND (session.date < :beforeDate OR (session.date = :beforeDate AND session_exercise.id < :beforeId))
          AND EXISTS (SELECT 1 FROM set_entry WHERE set_entry.session_exercise_id = session_exercise.id)
        ORDER BY session.date DESC, session_exercise.id DESC
        LIMIT :limit
        """,
    )
    suspend fun historyPage(exerciseId: String, beforeDate: LocalDate, beforeId: Long, limit: Int): List<ExerciseHistoryRow>

    /**
     * Every exercise ever trained, with the last day it was (#127).
     *
     * **Trained means a set was logged.** An exercise opened and left empty, or a routine day
     * skipped, has no set, so neither moves anything. One row per exercise rather than per set, so
     * it stays one small answer however many years the log holds.
     */
    @Query(
        """
        SELECT exercise.*, MAX(session.date) AS last_date
        FROM session_exercise
        JOIN session ON session.id = session_exercise.session_id
        JOIN exercise ON exercise.id = session_exercise.exercise_id
        WHERE EXISTS (SELECT 1 FROM set_entry WHERE set_entry.session_exercise_id = session_exercise.id)
        GROUP BY exercise.id
        """,
    )
    fun observeLastTrained(): Flow<List<LastTrainedRow>>

    /**
     * How many sets of one exercise have been logged in one session.
     *
     * What tells the calibration ramp which rung comes next. Sets rather than rows, so a per-side
     * set counts once (ADR 0003) and a lifter tracking an imbalance does not skip up the ramp
     * twice as fast.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT set_entry.session_exercise_id, set_entry.set_index
            FROM set_entry
            JOIN session_exercise ON session_exercise.id = set_entry.session_exercise_id
            WHERE session_exercise.exercise_id = :exerciseId
              AND session_exercise.session_id = :sessionId
        )
        """,
    )
    suspend fun setsLoggedInSession(exerciseId: String, sessionId: Long): Int

    @Query("SELECT * FROM set_entry WHERE id = :setId")
    suspend fun setEntry(setId: Long): SetEntryEntity?

    @Insert
    suspend fun insert(setEntry: SetEntryEntity): Long

    @Update
    suspend fun update(setEntry: SetEntryEntity)

    /**
     * Retypes every row of one set.
     *
     * A type belongs to the set, not to the row: leaving one side of a per-side set as working and
     * the other as a warm-up would make whether it counts depend on which row is read first.
     */
    @Query(
        """
        UPDATE set_entry SET type = :type
        WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex
        """,
    )
    suspend fun retypeSet(sessionExerciseId: Long, setIndex: Int, type: String)

    /**
     * Changes which unit one set reads in, every row of it together.
     *
     * The same reasoning as the type: a per-side set whose two rows disagreed about the unit would
     * show one arm in kilograms and the other in pounds.
     */
    @Query(
        """
        UPDATE set_entry SET display_unit = :displayUnit
        WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex
        """,
    )
    suspend fun redisplaySet(sessionExerciseId: Long, setIndex: Int, displayUnit: String?)

    /**
     * Drops the unit override from every set of [exerciseId] that had overridden to [displayUnit].
     *
     * Run when an exercise adopts a unit some of its sets had already departed to: those sets are
     * no longer departures from anything, and one that kept its override would be frozen in that
     * unit the next time the exercise moved (ADR 0009).
     */
    @Query(
        """
        UPDATE set_entry SET display_unit = NULL
        WHERE display_unit = :displayUnit AND session_exercise_id IN (
            SELECT id FROM session_exercise WHERE exercise_id = :exerciseId
        )
        """,
    )
    suspend fun clearDisplayUnitOverrides(exerciseId: String, displayUnit: String)

    /**
     * Removes every row of one set. No renumbering: the index it held stays empty, because shifting
     * the sets around it can corrupt one the lifter is editing at that moment.
     */
    @Query("DELETE FROM set_entry WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex")
    suspend fun deleteSetAt(sessionExerciseId: Long, setIndex: Int)

    @Query("SELECT COALESCE(MAX(order_index), 0) FROM drop_segment WHERE set_entry_id = :setEntryId")
    suspend fun highestDropOrder(setEntryId: Long): Int

    @Insert
    suspend fun insert(drop: DropSegmentEntity): Long

    @Query("DELETE FROM drop_segment WHERE id = :dropId")
    suspend fun deleteDrop(dropId: Long)

    companion object {
        /** No session to leave out: an id no row can have, so the comparison simply passes. */
        const val NO_SESSION = 0L
    }
}
