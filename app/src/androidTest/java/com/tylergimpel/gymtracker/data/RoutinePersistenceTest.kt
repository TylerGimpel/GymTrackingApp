package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.SetDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The seam this ticket exists to protect: a routine can be thrown away without touching a single
 * logged set, because nothing in history points back at it.
 */
@RunWith(AndroidJUnit4::class)
class RoutinePersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val clock = Clock.fixed(at("2026-09-14T18:00"), zone)

    private lateinit var database: GymDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        routines = RoutineRepository(database, database.routineDao(), clock)
        sessions = SessionRepository(database, database.sessionDao(), clock, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun deletingARoutineLeavesEveryLoggedSessionAndSetIntact() = runBlocking {
        val routine = pushPullLegs()
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        val push = routine.rotation.first()
        sessions.finishSession(session.id, routine, push)
        routines.recordCompletion(routine.id, push, session.id)

        routines.deleteRoutine()

        assertNull(routines.observeProgress().first())
        assertEquals(0, countOf("routine_day"))
        assertEquals(0, countOf("cycle"))
        assertEquals(0, countOf("cycle_completion"))
        // The history the routine pointed into is untouched.
        assertEquals(1, countOf("session"))
        assertEquals(1, countOf("set_entry"))
        assertEquals(1, countOf("session_exercise"))
    }

    @Test
    fun aFinishedSessionKeepsTheRoutineNamesAfterTheRoutineIsGone() = runBlocking {
        val routine = pushPullLegs()
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        sessions.finishSession(session.id, routine, routine.rotation.first())

        routines.deleteRoutine()

        val stored = database.sessionDao().session(session.id)!!
        assertEquals("Push / Pull / Legs", stored.routineName)
        assertEquals("Push", stored.routineDayName)
    }

    @Test
    fun aSessionTrainedWithNoRoutineCarriesNoSnapshotAndThatIsNotAnError() = runBlocking {
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!

        sessions.finishSession(session.id)

        val stored = database.sessionDao().session(session.id)!!
        assertNull(stored.routineName)
        assertNull(stored.routineDayName)
    }

    @Test
    fun theCursorDerivesFromStoredCycleState() = runBlocking {
        val routine = pushPullLegs()
        assertEquals("Push", routines.observeProgress().first()!!.cursor?.name)

        complete(routine.rotation[0])

        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun trainingOutOfOrderHoldsTheCursorAcrossARoundTrip() = runBlocking {
        val routine = pushPullLegs()

        complete(routine.rotation[1])

        assertEquals("Push", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun aSkippedDayIsPassedOverAndRecordedApartFromAnySession() = runBlocking {
        val routine = pushPullLegs()

        routines.recordSkip(routine.id, routine.rotation.first())

        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)
        assertEquals(1, countOf("skip"))
        // A skip is not training, so nothing in the log claims it was.
        assertEquals(0, countOf("session"))
    }

    @Test
    fun dealingWithEveryDayOpensTheNextCycleWithEverythingUndone() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation[0])
        complete(routine.rotation[1])
        routines.recordSkip(routine.id, routine.rotation[2])

        // The cycle ends the moment its last day is dealt with, so what shows is the new one.
        val afterRollover = routines.observeProgress().first()!!
        assertEquals(2, afterRollover.cycle!!.ordinal)
        assertEquals(emptyMap<Long, Any>(), afterRollover.cycle!!.outcomes)
        assertEquals("Push", afterRollover.cursor?.name)

        complete(routine.rotation[0])

        val second = routines.observeProgress().first()!!
        assertEquals(2, second.cycle!!.ordinal)
        assertEquals(1, second.cycle!!.outcomes.size)
        assertEquals("Pull", second.cursor?.name)
    }

    @Test
    fun deletingTheSessionThatCompletedADayMakesItUndoneAgain() = runBlocking {
        val routine = pushPullLegs()
        val sessionId = complete(routine.rotation.first())
        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)

        database.openHelper.writableDatabase.execSQL("DELETE FROM session WHERE id = $sessionId")

        // The cycle never claims training that no longer exists.
        assertEquals("Push", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun editingRoutinePropertiesLeavesTheCycleAlone() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation.first())
        val before = routines.observeProgress().first()!!.cycle!!

        routines.editProperties(
            name = "Renamed",
            goal = RoutineGoal.STRENGTH,
            cadence = 5,
            equipment = setOf("barbell"),
            repPreference = RepPreference.LOW,
        )

        val after = routines.observeProgress().first()!!
        assertEquals("Renamed", after.routine.name)
        assertEquals(5, after.routine.cadence)
        assertEquals(before.id, after.cycle!!.id)
        assertEquals("Pull", after.cursor?.name)
    }

    @Test
    fun aRoutineHoldsAnyNumberOfDaysInOrder() = runBlocking {
        val routineId = routines.createRoutine(name = "Full body")
        repeat(7) { routines.addDay(routineId, "Day ${it + 1}") }

        val routine = routines.observeProgress().first()!!.routine
        assertEquals(7, routine.days.size)
        assertEquals((1..7).map { "Day $it" }, routine.rotation.map(RoutineDay::name))

        routines.moveDay(routine.rotation.last().id, -1)

        val moved = routines.observeProgress().first()!!.routine
        assertEquals("Day 7", moved.rotation[5].name)
        assertEquals("Day 6", moved.rotation[6].name)
    }

    @Test
    fun addingADayEndsTheCycleRatherThanReopeningAFinishedOne() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation[0])
        complete(routine.rotation[1])
        complete(routine.rotation[2])
        assertEquals(2, routines.observeProgress().first()!!.cycle!!.ordinal)

        routines.addDay(routine.id, "Arms")

        // Without ending the cycle, the finished one would reopen with only Arms undone and the
        // lifter would be told to train the day they had just invented.
        val revised = routines.observeProgress().first()!!
        assertEquals(3, revised.cycle!!.ordinal)
        assertEquals(emptyMap<Long, Any>(), revised.cycle!!.outcomes)
        assertEquals("Push", revised.cursor?.name)
    }

    @Test
    fun reorderingTheRotationEndsTheCycle() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation[0])
        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)

        routines.moveDay(routine.rotation[2].id, -1)

        val revised = routines.observeProgress().first()!!
        assertEquals(2, revised.cycle!!.ordinal)
        assertEquals("Push", revised.cursor?.name)
    }

    @Test
    fun removingADayEndsTheCycle() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation[0])

        routines.removeDay(routine.rotation[2].id)

        val revised = routines.observeProgress().first()!!
        assertEquals(2, revised.cycle!!.ordinal)
        assertEquals("Push", revised.cursor?.name)
        assertEquals(listOf("Push", "Pull"), revised.routine.rotation.map(RoutineDay::name))
    }

    @Test
    fun renamingADayIsNotARevisionAndLeavesTheCycleAlone() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation[0])
        val before = routines.observeProgress().first()!!.cycle!!

        routines.renameDay(routine.rotation[0].id, "Push A")

        val after = routines.observeProgress().first()!!
        assertEquals(before.id, after.cycle!!.id)
        assertEquals("Pull", after.cursor?.name)
        assertEquals("Push A", after.routine.rotation.first().name)
    }

    // --- fixtures ----------------------------------------------------------------------------

    private suspend fun pushPullLegs() = run {
        val id = routines.createRoutine(name = "Push / Pull / Legs")
        listOf("Push", "Pull", "Legs").forEach { routines.addDay(id, it) }
        routines.observeProgress().first()!!.routine
    }

    /** Trains [day] with one real session, returning that session's id. */
    private suspend fun complete(day: RoutineDay): Long {
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        val routine = routines.observeProgress().first()!!.routine
        sessions.finishSession(session.id, routine, day)
        routines.recordCompletion(routine.id, day, session.id)
        return session.id
    }

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    private companion object {
        const val TEST_DATABASE = "routine-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
    }
}
