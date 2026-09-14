package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Side
import com.tylergimpel.gymtracker.domain.volumeByMuscle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What a real SQLite instance proves and plain objects cannot: that a gap survives a round trip,
 * that deleting a session takes its sets with it, and that an exercise with logged history cannot
 * be deleted out from under it.
 */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val clock = MovableClock(at("2026-09-14T18:00"), zone)

    private lateinit var database: GymDatabase
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        // A real file opened from the shipped asset: the exercise rows have to exist for the
        // foreign key on a logged set to mean anything.
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        repository = SessionRepository(database, database.sessionDao(), clock, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun loggingASetWithNoSessionOpensOne() = runBlocking {
        assertNull(repository.observeActiveSession().first())

        repository.logSet(BENCH, SetDraft("60", "5"))

        val session = repository.observeActiveSession().first()
        assertNotNull(session)
        assertEquals(LocalDate.of(2026, 9, 14), session!!.date)
        assertTrue(session.isActive)
    }

    @Test
    fun aSessionIsCompletedWithoutEverNamingARoutine() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        repository.finishSession(session.id)

        assertNull(repository.observeActiveSession().first())
        assertEquals(1, countOf("session"))
    }

    @Test
    fun setsAreListedBackGroupedByExercise() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))
        repository.logSet(BENCH, SetDraft("62.5", "5"))

        val logged = currentSession()

        assertEquals(listOf("Barbell Bench Press - Medium Grip", "Barbell Squat"), logged.exercises.map { it.exercise.name })
        assertEquals(listOf(1, 2), logged.exercises[0].sets.map(PerformedSet::setIndex))
        assertEquals(listOf(1), logged.exercises[1].sets.map(PerformedSet::setIndex))
    }

    @Test
    fun weightIsStoredAsTheKilogramsItWasEnteredIn() = runBlocking {
        repository.logSet(BENCH, SetDraft("62.5", "5"))

        assertEquals(62.5, currentSession().exercises.single().sets.single().leadingEffort.weight.value, 0.0)
    }

    @Test
    fun deletingASetLeavesAGapAndEveryOtherSetIntact() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("${60 + it}", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }

        repository.deleteSet(sessionExerciseId(), middle.setIndex)

        val remaining = currentSession().exercises.single().sets
        assertEquals(listOf(1, 3), remaining.map(PerformedSet::setIndex))
        assertEquals(listOf(60.0, 62.0), remaining.map { it.leadingEffort.weight.value })
    }

    @Test
    fun theGapSurvivesAndTheNextSetLandsAboveIt() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("60", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }
        repository.deleteSet(sessionExerciseId(), middle.setIndex)

        repository.logSet(BENCH, SetDraft("65", "5"))

        assertEquals(listOf(1, 3, 4), currentSession().exercises.single().sets.map(PerformedSet::setIndex))
    }

    @Test
    fun editingASetLeavesEveryOtherSetAlone() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("${60 + it}", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }

        repository.editSet(middle.leadingEffort.id, SetDraft("70", "3", note = "felt heavy"))

        val sets = currentSession().exercises.single().sets
        assertEquals(listOf(60.0, 70.0, 62.0), sets.map { it.leadingEffort.weight.value })
        assertEquals(listOf(5, 3, 5), sets.map { it.leadingEffort.reps })
        assertEquals("felt heavy", sets[1].leadingEffort.note)
        // The edited set keeps its position: editing is not a delete and re-add.
        assertEquals(listOf(1, 2, 3), sets.map(PerformedSet::setIndex))
    }

    @Test
    fun deletingASessionTakesItsSetsWithIt() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        database.openHelper.writableDatabase.execSQL("DELETE FROM session WHERE id = ${session.id}")

        assertEquals(0, countOf("set_entry"))
        assertEquals(0, countOf("session_exercise"))
    }

    @Test
    fun anExerciseWithLoggedHistoryCannotBeDeleted() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        val refused = runCatching {
            database.openHelper.writableDatabase.execSQL("DELETE FROM exercise WHERE id = '$BENCH'")
        }.exceptionOrNull()

        assertTrue("expected a constraint failure, got $refused", refused is SQLiteConstraintException)
        assertEquals(1, currentSession().exercises.single().sets.size)
    }

    @Test
    fun aSessionRunningPastMidnightKeepsTheDateItStartedOn() = runBlocking {
        clock.moveTo(at("2026-09-14T23:40"))
        repository.logSet(BENCH, SetDraft("60", "5"))

        clock.moveTo(at("2026-09-15T00:20"))
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!
        repository.finishSession(session.id)

        val finished = database.sessionDao().session(session.id)!!
        assertEquals(LocalDate.of(2026, 9, 14), finished.date)
        // Both sets belong to that one session rather than to a second one opened after midnight.
        assertEquals(1, countOf("session"))
        assertEquals(2, countOf("set_entry"))
    }

    @Test
    fun aSessionDateCanBeCorrectedWithoutMovingWhenItWasTrained() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!

        repository.dateSession(session.id, LocalDate.of(2026, 9, 12))

        val corrected = database.sessionDao().session(session.id)!!
        assertEquals(LocalDate.of(2026, 9, 12), corrected.date)
        assertEquals(session.startedAt, corrected.startedAt)
    }

    @Test
    fun notesAttachToBothASetAndASession() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5", note = "shoulder felt off"))
        val session = repository.observeActiveSession().first()!!

        repository.noteSession(session.id, "short on time")

        assertEquals("shoulder felt off", currentSession().exercises.single().sets.single().leadingEffort.note)
        assertEquals("short on time", database.sessionDao().session(session.id)!!.note)
    }

    @Test
    fun aSessionLeftUnfinishedDoesNotSwallowALaterDaysTraining() = runBlocking {
        clock.moveTo(at("2026-09-14T18:00"))
        repository.logSet(BENCH, SetDraft("60", "5"))

        // Nobody pressed Finish on Monday. Thursday is not a continuation of it.
        clock.moveTo(at("2026-09-17T07:00"))
        repository.logSet(BENCH, SetDraft("65", "5"))

        assertEquals(2, countOf("session"))
        val current = repository.observeActiveSession().first()!!
        assertEquals(LocalDate.of(2026, 9, 17), current.date)
        assertEquals(1, currentSession().exercises.single().sets.size)
    }

    @Test
    fun aSetKeepsTheTypeItWasLoggedWith() = runBlocking {
        repository.logSet(BENCH, SetDraft("40", "10", type = SetType.WARMUP))
        repository.logSet(BENCH, SetDraft("60", "5", type = SetType.WORKING))
        repository.logSet(BENCH, SetDraft("50", "8", type = SetType.BACKOFF))

        val sets = currentSession().exercises.single().sets
        assertEquals(
            listOf(SetType.WARMUP, SetType.WORKING, SetType.BACKOFF),
            sets.map(PerformedSet::type),
        )
    }

    @Test
    fun aSetLoggedWithNoStatedTypeIsWorking() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        assertEquals(SetType.WORKING, currentSession().exercises.single().sets.single().type)
    }

    @Test
    fun correctingASetCanChangeWhatItWasFor() = runBlocking {
        repository.logSet(BENCH, SetDraft("40", "10", type = SetType.WORKING))
        val logged = currentSession().exercises.single().sets.single()

        repository.editSet(logged.leadingEffort.id, SetDraft("40", "10", type = SetType.WARMUP))

        val corrected = currentSession().exercises.single().sets.single()
        assertEquals(SetType.WARMUP, corrected.type)
        assertEquals(logged.setIndex, corrected.setIndex)
    }

    @Test
    fun volumeReadsBackFromStoredSetsCountingOnlyTheWork() = runBlocking {
        repository.logSet(BENCH, SetDraft("40", "10", type = SetType.WARMUP))
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(BENCH, SetDraft("60", "5"))

        // Bench is chest primary, shoulders and triceps secondary; the warm-up earns nothing.
        val volume = volumeByMuscle(currentSession().exercises)
        assertEquals(2.0, volume["chest"]!!, 0.0)
        assertEquals(1.0, volume["shoulders"]!!, 0.0)
        assertEquals(1.0, volume["triceps"]!!, 0.0)
    }

    @Test
    fun aPerSideSetIsTwoRowsSharingOneSetIndex() = runBlocking {
        repository.logPerSideSet(
            BENCH,
            SetDraft("22.5", "10"),
            SetDraft("20", "10"),
        )

        // Two rows in storage...
        assertEquals(2, countOf("set_entry"))
        // ...and one set coming back, because a set is one set however many rows record it.
        val set = currentSession().exercises.single().sets.single()
        assertEquals(1, set.setIndex)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), set.efforts.map { it.side })
        // Weights may differ, which is the only reason to expand a set to per-side at all.
        assertEquals(listOf(22.5, 20.0), set.efforts.map { it.weight.value })
    }

    @Test
    fun aPerSideSetCountsOnceTowardVolume() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))

        assertEquals(1.0, volumeByMuscle(currentSession().exercises).getValue("chest"), 0.0)
    }

    @Test
    fun theNextSetAfterAPerSideSetTakesTheFollowingIndex() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))

        repository.logSet(BENCH, SetDraft("45", "8"))

        // Two rows then one, but the indices read 1 and 2 — not 1, 1, 3.
        assertEquals(listOf(1, 2), currentSession().exercises.single().sets.map(PerformedSet::setIndex))
    }

    @Test
    fun dropsComeBackInTheOrderTheyWerePerformed() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "8"))
        val effort = currentSession().exercises.single().sets.single().leadingEffort

        repository.addDrop(effort.id, Kilograms(50.0), 4)
        repository.addDrop(effort.id, Kilograms(40.0), 3)

        val drops = currentSession().exercises.single().sets.single().leadingEffort.drops
        assertEquals(listOf(1, 2), drops.map { it.orderIndex })
        assertEquals(listOf(50.0, 40.0), drops.map { it.weight.value })
        assertEquals(listOf(4, 3), drops.map { it.reps })
    }

    @Test
    fun aDropSetCountsOnceHoweverManyDropsItHas() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "8"))
        val effort = currentSession().exercises.single().sets.single().leadingEffort
        repository.addDrop(effort.id, Kilograms(50.0), 4)
        repository.addDrop(effort.id, Kilograms(40.0), 3)

        assertEquals(1.0, volumeByMuscle(currentSession().exercises).getValue("chest"), 0.0)
        // The drops are stored, and still contribute nothing of their own.
        assertEquals(2, countOf("drop_segment"))
    }

    @Test
    fun progressionReadsTheFirstEffortRatherThanTheDrops() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "8"))
        val effort = currentSession().exercises.single().sets.single().leadingEffort
        repository.addDrop(effort.id, Kilograms(40.0), 3)

        // The fatigued drop must never be what an estimated one-rep max is built from.
        val leading = currentSession().exercises.single().sets.single().leadingEffort
        assertEquals(60.0, leading.weight.value, 0.0)
        assertEquals(8, leading.reps)
    }

    @Test
    fun deletingASetTakesItsDropsWithIt() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "8"))
        val set = currentSession().exercises.single().sets.single()
        repository.addDrop(set.leadingEffort.id, Kilograms(50.0), 4)

        repository.deleteSet(sessionExerciseId(), set.setIndex)

        assertEquals(0, countOf("set_entry"))
        assertEquals(0, countOf("drop_segment"))
    }

    @Test
    fun deletingAPerSideSetRemovesBothOfItsRows() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))
        val set = currentSession().exercises.single().sets.single()

        repository.deleteSet(sessionExerciseId(), set.setIndex)

        // Half a per-side set is not a set.
        assertEquals(0, countOf("set_entry"))
    }

    private suspend fun sessionExerciseId(): Long =
        currentSession().exercises.single().id

    private suspend fun currentSession(): LoggedSession {
        val active = repository.observeActiveSession().first()!!
        return repository.observeSession(active.id).first()!!
    }

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    /** A clock the test moves by hand, so midnight can be crossed without waiting for it. */
    private class MovableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
        fun moveTo(instant: Instant) { now = instant }
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
        override fun instant(): Instant = now
    }

    private companion object {
        const val TEST_DATABASE = "session-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT = "Barbell_Squat"
    }
}
