package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.CalendarDay
import com.tylergimpel.gymtracker.domain.DayTraining
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Metres
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The calendar as a real database answers it (#118): what a month's sessions and weigh-ins mark,
 * and that a session left empty marks nothing.
 */
@RunWith(AndroidJUnit4::class)
class CalendarPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private lateinit var database: GymDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun aMonthMarksWhatWasDoneAndNothingElse() = runBlocking {
        session(SEPTEMBER.atDay(1), SQUAT to Performance.WeightAndReps(Kilograms(100.0), 5))
        session(SEPTEMBER.atDay(3), TREADMILL to Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28)))
        // Started for the routine's day and then skipped: nothing logged.
        session(SEPTEMBER.atDay(5))
        session(SEPTEMBER.atDay(8), TREADMILL to Performance.DistanceAndTime(Metres(3000.0), Duration.ofMinutes(18)))
        session(SEPTEMBER.atDay(8), SQUAT to Performance.WeightAndReps(Kilograms(100.0), 5))
        weighIn(SEPTEMBER.atDay(3))
        // Next month's, which September does not show.
        session(SEPTEMBER.plusMonths(1).atDay(1), SQUAT to Performance.WeightAndReps(Kilograms(100.0), 5))

        val days = CalendarRepository(database).observeMonth(SEPTEMBER).first()

        assertEquals(
            mapOf(
                SEPTEMBER.atDay(1) to CalendarDay(SEPTEMBER.atDay(1), DayTraining.LIFTING, weighedIn = false),
                SEPTEMBER.atDay(3) to CalendarDay(SEPTEMBER.atDay(3), DayTraining.CARDIO_ONLY, weighedIn = true),
                SEPTEMBER.atDay(8) to CalendarDay(SEPTEMBER.atDay(8), DayTraining.LIFTING, weighedIn = false),
            ),
            days,
        )
        assertEquals(SEPTEMBER, CalendarRepository(database).firstMonth())
    }

    private suspend fun session(date: LocalDate, vararg sets: Pair<String, Performance>) {
        val dao = database.sessionDao()
        val at = date.atTime(18, 0).atZone(zone).toInstant()
        val session = dao.insert(
            SessionEntity(startedAt = at, finishedAt = at, date = date, note = null, routineName = null, routineDayName = null),
        )
        // The routine's exercise opened even where nothing was logged against it.
        if (sets.isEmpty()) dao.insert(SessionExerciseEntity(sessionId = session, exerciseId = SQUAT, orderIndex = 1))
        sets.forEachIndexed { index, (exerciseId, performance) ->
            val performed = dao.insert(SessionExerciseEntity(sessionId = session, exerciseId = exerciseId, orderIndex = index + 1))
            dao.insert(
                SetEntryEntity(
                    sessionExerciseId = performed,
                    setIndex = 1,
                    type = SetType.WORKING.stored,
                    note = null,
                    side = null,
                    displayUnit = null,
                ).recording(performance),
            )
        }
    }

    private suspend fun weighIn(date: LocalDate) {
        database.bodyweightDao().insert(
            BodyweightEntryEntity(recordedAt = date.atTime(7, 0).atZone(zone).toInstant(), date = date, weightKg = 80.0),
        )
    }

    private companion object {
        const val TEST_DATABASE = "calendar-persistence-test.db"
        const val SQUAT = "Barbell_Squat"
        const val TREADMILL = "Jogging_Treadmill"
        val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
    }
}
