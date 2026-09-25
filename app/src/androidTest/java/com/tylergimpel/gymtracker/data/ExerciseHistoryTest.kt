package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.ExerciseHistoryEntry
import com.tylergimpel.gymtracker.domain.HISTORY_PAGE
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * An exercise's history stays fast as it grows (#121): read a page at a time, and every page costs
 * the same, against a generated history bigger than the author's real 2,234 sets.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseHistoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private lateinit var database: GymDatabase
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        sessions = SessionRepository(
            database,
            database.sessionDao(),
            Clock.fixed(Instant.parse("2026-09-24T18:00:00Z"), zone),
            zone,
            ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun yearsOfHistoryAreReadAPageAtATimeNewestFirstAndNothingIsMissedOrRepeated() = runBlocking {
        generateHistory()

        val pages = mutableListOf<List<ExerciseHistoryEntry>>()
        var after: ExerciseHistoryEntry? = null
        do {
            val page = sessions.historyPage(SQUAT, after)
            pages += page
            after = page.lastOrNull()
        } while (page.size == HISTORY_PAGE)

        // Never more than a page at a time, however far back.
        assertTrue(pages.all { it.size <= HISTORY_PAGE })
        val read = pages.flatten()
        // Every session exactly once, newest first, and every set in it.
        assertEquals(SESSIONS, read.size)
        assertEquals(read.map { it.sessionExerciseId }.toSet().size, read.size)
        assertEquals(read.sortedByDescending(ExerciseHistoryEntry::date), read)
        assertEquals(SESSIONS * SETS_EACH, read.sumOf { it.sets.size })
        assertTrue(SESSIONS * SETS_EACH >= AUTHORS_SETS)
        // Only the squat's: the bench logged beside it every time is not its history.
        assertTrue(read.all { entry -> entry.sets.all { it.efforts.single().performance is Performance.WeightAndReps } })
    }

    @Test
    fun anExerciseNeverTrainedHasNoHistory() = runBlocking {
        assertTrue(sessions.historyPage(SQUAT).isEmpty())
    }

    /** [SESSIONS] sessions two days apart, each with [SETS_EACH] squat sets and a bench set beside them. */
    private suspend fun generateHistory() = database.withTransaction {
        val dao = database.sessionDao()
        val first = LocalDate.parse("2024-01-01")
        repeat(SESSIONS) { n ->
            val date = first.plusDays(2L * n)
            val at = date.atTime(18, 0).atZone(zone).toInstant()
            val session = dao.insert(SessionEntity(startedAt = at, finishedAt = at, date = date, note = null, routineName = null, routineDayName = null))
            val squat = dao.insert(SessionExerciseEntity(sessionId = session, exerciseId = SQUAT, orderIndex = 1))
            repeat(SETS_EACH) { index ->
                dao.insert(
                    SetEntryEntity(
                        sessionExerciseId = squat,
                        setIndex = index + 1,
                        type = SetType.WORKING.stored,
                        note = null,
                        side = null,
                        displayUnit = null,
                    ).recording(Performance.WeightAndReps(Kilograms(100.0 + n % 10), 5)),
                )
            }
            val bench = dao.insert(SessionExerciseEntity(sessionId = session, exerciseId = BENCH, orderIndex = 2))
            dao.insert(
                SetEntryEntity(
                    sessionExerciseId = bench,
                    setIndex = 1,
                    type = SetType.WORKING.stored,
                    note = null,
                    side = null,
                    displayUnit = null,
                ).recording(Performance.WeightAndReps(Kilograms(60.0), 8)),
            )
        }
    }

    private companion object {
        const val TEST_DATABASE = "exercise-history-test.db"
        const val SQUAT = "Barbell_Squat"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SESSIONS = 260
        const val SETS_EACH = 9
        const val AUTHORS_SETS = 2234
    }
}
