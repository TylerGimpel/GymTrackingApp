package com.tylergimpel.gymtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Metres
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetOutcome
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.Side
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.loadOrNull
import com.tylergimpel.gymtracker.domain.repsOrNull
import com.tylergimpel.gymtracker.domain.volumeByMuscle
import com.tylergimpel.gymtracker.domain.weightOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
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
    private lateinit var exercises: ExerciseRepository

    /** Pinned rather than the device's: what a test reads in is part of what it asserts. */
    private lateinit var profile: ProfileRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        // A real file opened from the shipped asset: the exercise rows have to exist for the
        // foreign key on a logged set to mean anything.
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)
        repository = SessionRepository(database, database.sessionDao(), clock, zone, profile)
        exercises =
            ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)
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

        assertEquals(62.5, currentSession().exercises.single().sets.single().leadingEffort.performance.weightOrNull!!.value, 0.0)
    }

    @Test
    fun deletingASetLeavesAGapAndEveryOtherSetIntact() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("${60 + it}", "5")) }
        val middle = currentSession().exercises.single().sets.single { it.setIndex == 2 }

        repository.deleteSet(sessionExerciseId(), middle.setIndex)

        val remaining = currentSession().exercises.single().sets
        assertEquals(listOf(1, 3), remaining.map(PerformedSet::setIndex))
        assertEquals(listOf(60.0, 62.0), remaining.map { it.leadingEffort.performance.weightOrNull!!.value })
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
        assertEquals(listOf(60.0, 70.0, 62.0), sets.map { it.leadingEffort.performance.weightOrNull!!.value })
        assertEquals(listOf(5, 3, 5), sets.map { it.leadingEffort.performance.repsOrNull!! })
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

        repository.refile(session.id, LocalDate.of(2026, 9, 12))

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
        assertEquals(listOf(22.5, 20.0), set.efforts.map { it.performance.weightOrNull!!.value })
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
        assertEquals(60.0, leading.performance.weightOrNull!!.value, 0.0)
        assertEquals(8, leading.performance.repsOrNull!!)
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

    @Test
    fun retypingOneSideOfAPerSideSetRetypesTheWholeSet() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))
        val set = currentSession().exercises.single().sets.single()
        val right = set.efforts.last()

        repository.editSet(right.id, SetDraft("20", "10", type = SetType.WARMUP))

        // A type belongs to the set. Leaving one row working and the other a warm-up would make
        // whether it counts depend on which row happened to be read first.
        val retyped = currentSession().exercises.single().sets.single()
        assertEquals(SetType.WARMUP, retyped.type)
        assertEquals(0.0, volumeByMuscle(currentSession().exercises)["chest"] ?: 0.0, 0.0)
    }

    @Test
    fun correctingOneSideLeavesTheOtherSideAlone() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))
        val set = currentSession().exercises.single().sets.single()

        repository.editSet(set.efforts.last().id, SetDraft("21", "9"))

        val corrected = currentSession().exercises.single().sets.single()
        assertEquals(listOf(22.5, 21.0), corrected.efforts.map { it.performance.weightOrNull!!.value })
        assertEquals(listOf(10, 9), corrected.efforts.map { it.performance.repsOrNull!! })
        assertEquals(listOf(Side.LEFT, Side.RIGHT), corrected.efforts.map { it.side })
    }

    @Test
    fun anExerciseRemembersTheUnitItIsReadIn() = runBlocking {
        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)

        repository.logSet(BENCH, SetDraft("225", "5", displayUnit = WeightUnit.POUNDS))

        // The choice comes back attached to the exercise, from a table the shipped bank does not
        // own — so a bank update replacing the row cannot take it away.
        assertEquals(WeightUnit.POUNDS, currentSession().exercises.single().exercise.displayUnit)
    }

    @Test
    fun aWeightReadInPoundsIsStoredAsTheKilogramsItWeighs() = runBlocking {
        repository.logSet(BENCH, SetDraft("225", "5", displayUnit = WeightUnit.POUNDS))

        val logged = currentSession().exercises.single().sets.single()
        assertEquals(102.05828325, logged.leadingEffort.performance.weightOrNull!!.value, 1e-9)
    }

    @Test
    fun switchingAnExercisesUnitChangesNothingThatWasStored() = runBlocking {
        repository.logSet(BENCH, SetDraft("100", "5"))
        repository.logSet(BENCH, SetDraft("102.5", "5"))
        val before = storedWeights()

        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)

        assertEquals(before, storedWeights())
        assertEquals(WeightUnit.POUNDS, currentSession().exercises.single().exercise.displayUnit)
    }

    @Test
    fun aSingleSetOverridesTheUnitWithoutChangingTheExercisesSetting() = runBlocking {
        repository.logSet(BENCH, SetDraft("100", "5"))
        repository.logSet(BENCH, SetDraft("225", "5", displayUnit = WeightUnit.POUNDS))

        val performed = currentSession().exercises.single()
        assertEquals(WeightUnit.KILOGRAMS, performed.exercise.displayUnit)
        assertEquals(WeightUnit.KILOGRAMS, performed.displayUnitFor(performed.sets[0]))
        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets[1]))
    }

    @Test
    fun aSetThatDidNotOverrideFollowsItsExerciseWhenTheUnitChanges() = runBlocking {
        repository.logSet(BENCH, SetDraft("100", "5"))

        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)

        // Nothing stored on the set itself, so its display moves with the exercise and the history
        // stays readable in one unit rather than splitting at the day the lifter switched.
        val performed = currentSession().exercises.single()
        assertNull(performed.sets.single().displayUnit)
        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets.single()))
    }

    @Test
    fun rereadingOneSideOfAPerSideSetRereadsTheWholeSet() = runBlocking {
        repository.logPerSideSet(BENCH, SetDraft("22.5", "10"), SetDraft("20", "10"))
        val right = currentSession().exercises.single().sets.single().efforts.last()

        repository.editSet(right.id, SetDraft("44", "10", displayUnit = WeightUnit.POUNDS))

        // A unit belongs to the set, like its type: one arm shown in pounds and the other in
        // kilograms would be two numbers a lifter cannot compare.
        assertEquals(WeightUnit.POUNDS, currentSession().exercises.single().sets.single().displayUnit)
    }

    @Test
    fun adoptingAUnitClearsTheOverridesThatHadDepartedToIt() = runBlocking {
        // Logged off the odd machine while the exercise still read in kilograms...
        repository.logSet(BENCH, SetDraft("225", "5", displayUnit = WeightUnit.POUNDS))

        // ...and then the lifter decides the whole exercise is read that way.
        exercises.setDisplayUnit(BENCH, WeightUnit.POUNDS)

        // The set is no longer departing from anything, so it stops claiming to. Were the override
        // left on it, moving the exercise back to kilograms would strand that one set in pounds.
        val performed = currentSession().exercises.single()
        assertNull(performed.sets.single().displayUnit)
        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets.single()))
        assertEquals(102.05828325, performed.sets.single().leadingEffort.performance.weightOrNull!!.value, 1e-9)
    }

    @Test
    fun adoptingAUnitLeavesAnOverrideInTheOtherUnitAlone() = runBlocking {
        repository.logSet(BENCH, SetDraft("225", "5", displayUnit = WeightUnit.POUNDS))

        // The lifter settles the exercise on kilograms. The set read off the pound machine is
        // still a departure from that, and only overrides the adopted unit made redundant go.
        exercises.setDisplayUnit(BENCH, WeightUnit.KILOGRAMS)

        val performed = currentSession().exercises.single()
        assertEquals(WeightUnit.POUNDS, performed.displayUnitFor(performed.sets.single()))
    }

    @Test
    fun nothingIsOfferedForCorrectionUntilASessionHasBeenFinished() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        // The session being logged is already on screen. There is nothing behind it yet.
        assertNull(repository.observeLastFinished().first())
    }

    @Test
    fun theSessionMostRecentlyFinishedIsTheOneOfferedForCorrection() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val monday = repository.observeActiveSession().first()!!
        repository.finishSession(monday.id)

        clock.moveTo(at("2026-09-17T18:00"))
        repository.logSet(BENCH, SetDraft("65", "5"))
        val thursday = repository.observeActiveSession().first()!!
        repository.finishSession(thursday.id)

        // The one just finished is the one a lifter is about to realise they misdated.
        assertEquals(thursday.id, repository.observeLastFinished().first()!!.session.id)
    }

    @Test
    fun aFinishedSessionsDateCanStillBeCorrected() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val session = repository.observeActiveSession().first()!!
        repository.finishSession(session.id)

        repository.refile(session.id, LocalDate.of(2026, 9, 12))

        val corrected = repository.observeLastFinished().first()!!.session
        assertEquals(LocalDate.of(2026, 9, 12), corrected.date)
        // Refiling moves where the session is filed and nothing else: it stays finished, and when
        // it was actually trained is untouched.
        assertEquals(session.startedAt, corrected.startedAt)
        assertFalse(corrected.isActive)
    }

    @Test
    fun twoExercisesAlternatedReadBackAsOneGroup() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))

        repository.groupWithNext(currentSession().exercises.first().id)

        // One position in the session holding both, rather than two positions that happen to be
        // next to each other.
        val positions = currentSession().positions
        assertEquals(1, positions.size)
        assertTrue(positions.single() is SessionPosition.Grouped)
        assertEquals(listOf(BENCH, SQUAT), positions.single().exercises.map { it.exercise.id })
    }

    @Test
    fun aGroupGrowsOneMemberAtATimeToAsManyAsWereAlternated() = runBlocking {
        val everything = listOf(BENCH, SQUAT, CURL, ROW, DEADLIFT)
        everything.forEach { repository.logSet(it, SetDraft("60", "5")) }

        // Group the first with the second, then the second with the third, and so on: seven
        // triples, two quads and a quintuple exist in the author's own history.
        repeat(everything.size - 1) {
            val performed = currentSession().exercises
            repository.groupWithNext(performed[it].id)
        }

        val positions = currentSession().positions
        assertEquals(1, positions.size)
        assertEquals(everything, positions.single().exercises.map { it.exercise.id })
    }

    @Test
    fun groupingIntoAGroupThatAlreadyExistsJoinsItRatherThanStealingFromIt() = runBlocking {
        listOf(BENCH, SQUAT, CURL).forEach { repository.logSet(it, SetDraft("60", "5")) }
        // The squat and the curl are already alternated.
        repository.groupWithNext(currentSession().exercises[1].id)

        // Now the bench joins them from the front.
        repository.groupWithNext(currentSession().exercises[0].id)

        // One group of three. Overwriting the squat's group instead would have left the curl on
        // its own in a superset of one, which is not a superset.
        val positions = currentSession().positions
        assertEquals(1, positions.size)
        assertEquals(listOf(BENCH, SQUAT, CURL), positions.single().exercises.map { it.exercise.id })
        assertEquals(1, countOf("superset_group"))
    }

    @Test
    fun reachingFromOneGroupIntoAnotherMergesThemRatherThanStrandingAMember() = runBlocking {
        listOf(BENCH, SQUAT, CURL, ROW).forEach { repository.logSet(it, SetDraft("60", "5")) }
        val performed = currentSession().exercises
        repository.groupWithNext(performed[0].id)
        repository.groupWithNext(performed[2].id)
        assertEquals(2, countOf("superset_group"))

        // Alternating the squat with the curl joins the two groups end to end.
        repository.groupWithNext(currentSession().exercises[1].id)

        val positions = currentSession().positions
        assertEquals(1, positions.size)
        assertEquals(
            listOf(BENCH, SQUAT, CURL, ROW),
            positions.single().exercises.map { it.exercise.id },
        )
        // The emptied group goes with it rather than lingering with nothing in it.
        assertEquals(1, countOf("superset_group"))
    }

    @Test
    fun aGroupCanBeToldNotToJumpToItsNextMember() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))
        repository.groupWithNext(currentSession().exercises.first().id)
        val superset = (currentSession().positions.single() as SessionPosition.Grouped).superset
        assertTrue(superset.autoJump)

        repository.setAutoJump(superset.id, false)

        // Two machines at opposite ends of a gym is the case where alternating unasked is wrong.
        val after = currentSession().positions.single() as SessionPosition.Grouped
        assertFalse(after.superset.autoJump)
    }

    @Test
    fun ungroupingLeavesEverySetExactlyAsItWas() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(BENCH, SetDraft("62.5", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))
        repository.groupWithNext(currentSession().exercises.first().id)
        val superset = (currentSession().positions.single() as SessionPosition.Grouped).superset

        repository.ungroup(superset.id)

        // The group is gone and the training is not: grouping was only ever a statement about rest.
        val after = currentSession()
        assertEquals(2, after.positions.size)
        assertTrue(after.positions.all { it is SessionPosition.Alone })
        assertEquals(listOf(60.0, 62.5), after.exercises[0].sets.map { it.leadingEffort.performance.weightOrNull!!.value })
        assertEquals(listOf(100.0), after.exercises[1].sets.map { it.leadingEffort.performance.weightOrNull!!.value })
        assertEquals(0, countOf("superset_group"))
    }

    @Test
    fun groupingChangesNoSetsContributionToVolume() = runBlocking {
        repeat(3) { repository.logSet(BENCH, SetDraft("60", "5")) }
        repeat(2) { repository.logSet(SQUAT, SetDraft("100", "5")) }
        val apart = volumeByMuscle(currentSession().exercises)

        repository.groupWithNext(currentSession().exercises.first().id)

        assertEquals(apart, volumeByMuscle(currentSession().exercises))
    }

    @Test
    fun aGroupCarriesItsOrderAutoJumpAndOwnRest() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))

        repository.groupWithNext(currentSession().exercises.first().id)

        val superset = (currentSession().positions.single() as SessionPosition.Grouped).superset
        assertEquals(1, superset.orderIndex)
        // On by default: the point of a superset is not stopping between its members.
        assertTrue(superset.autoJump)
        // No rest of its own yet, which means "whatever the app would rest for anyway".
        assertNull(superset.restSeconds)
    }

    @Test
    fun thereIsNothingToGroupTheLastExerciseWith() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        assertFalse(repository.groupWithNext(currentSession().exercises.single().id))
        assertEquals(0, countOf("superset_group"))
    }

    @Test
    fun deletingASessionTakesItsGroupsWithIt() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        repository.logSet(SQUAT, SetDraft("100", "5"))
        repository.groupWithNext(currentSession().exercises.first().id)
        val session = repository.observeActiveSession().first()!!

        database.openHelper.writableDatabase.execSQL("DELETE FROM session WHERE id = ${session.id}")

        assertEquals(0, countOf("superset_group"))
    }

    @Test
    fun aTimedSetRecordsHowLongAndNoWeightAtAll() = runBlocking {
        repository.logSet(HAMSTRING_STRETCH, SetDraft(durationText = "90", loggingType = LoggingType.TIME))

        val effort = currentSession().exercises.single().sets.single().leadingEffort
        assertEquals(Performance.Timed(Duration.ofSeconds(90)), effort.performance)
        // Not a weight of zero standing in for "there was no weight": there is no weight here.
        assertNull(effort.performance.weightOrNull)
        assertNull(effort.performance.repsOrNull)
    }

    @Test
    fun aWeightTypedAgainstATimedExerciseIsNotRecorded() = runBlocking {
        // The controls do not offer a weight field for a stretch, but a draft carried over from the
        // previous exercise must not smuggle one into storage behind them.
        repository.logSet(
            HAMSTRING_STRETCH,
            SetDraft(weightText = "60", repsText = "5", durationText = "90", loggingType = LoggingType.TIME),
        )

        assertEquals(listOf<Double?>(null), storedWeights())
    }

    @Test
    fun aTimedSetIsOneSetTowardVolumeLikeAnyOther() = runBlocking {
        repository.logSet(HAMSTRING_STRETCH, SetDraft(durationText = "60", loggingType = LoggingType.TIME))

        // Counted by its set type, exactly as a loaded set is: a set is one set whatever it recorded.
        assertEquals(
            mapOf("hamstrings" to 1.0, "calves" to 0.5),
            volumeByMuscle(currentSession().exercises),
        )
    }

    @Test
    fun aTimedSetIsCorrectedAndDeletedLikeAnyOther() = runBlocking {
        repository.logSet(HAMSTRING_STRETCH, SetDraft(durationText = "60", loggingType = LoggingType.TIME))
        val logged = currentSession().exercises.single()

        repository.editSet(
            logged.sets.single().leadingEffort.id,
            SetDraft(durationText = "75", loggingType = LoggingType.TIME),
        )

        assertEquals(
            Performance.Timed(Duration.ofSeconds(75)),
            currentSession().exercises.single().sets.single().leadingEffort.performance,
        )

        repository.deleteSet(logged.id, logged.sets.single().setIndex)
        assertEquals(emptyList<PerformedSet>(), currentSession().exercises.single().sets)
    }

    @Test
    fun correctingASetFromATimeToAWeightLeavesNoTimeBehind() = runBlocking {
        // Not a case a lifter reaches by accident, but a row that kept both would be a set that
        // recorded two different things, and the reader takes the first it finds.
        repository.logSet(HAMSTRING_STRETCH, SetDraft(durationText = "60", loggingType = LoggingType.TIME))
        val effortId = currentSession().exercises.single().sets.single().leadingEffort.id

        repository.editSet(effortId, SetDraft("60", "5"))

        val effort = currentSession().exercises.single().sets.single().leadingEffort
        assertEquals(Performance.WeightAndReps(Kilograms(60.0), 5), effort.performance)
        assertEquals(listOf<Int?>(null), storedDurations())
    }

    @Test
    fun aDistanceAndTimeSetRecordsHowFarAndHowLong() = runBlocking {
        repository.logSet(
            TREADMILL,
            SetDraft(durationText = "1680", distanceText = "5", loggingType = LoggingType.DISTANCE_TIME),
        )

        val effort = currentSession().exercises.single().sets.single().leadingEffort
        assertEquals(
            Performance.DistanceAndTime(Metres(5000.0), Duration.ofMinutes(28)),
            effort.performance,
        )
        // A run is not a lift: there is no weight here and no rep count.
        assertNull(effort.performance.weightOrNull)
        assertNull(effort.performance.repsOrNull)
    }

    @Test
    fun aDistanceIsStoredInMetresWhateverItWasReadIn() = runBlocking {
        // Entered by a lifter reading pounds, so the field was miles. What is stored is the ground
        // covered, which is the same ground whichever machine reported it.
        repository.logSet(
            TREADMILL,
            SetDraft(
                durationText = "1680",
                distanceText = "3.1",
                lifterUnit = WeightUnit.POUNDS,
                loggingType = LoggingType.DISTANCE_TIME,
            ),
        )

        assertEquals(4988.97, storedDistances().single()!!, 0.01)
    }

    @Test
    fun aDistanceAndTimeSetIsOneSetTowardVolumeLikeAnyOther() = runBlocking {
        repository.logSet(
            TREADMILL,
            SetDraft(durationText = "1680", distanceText = "5", loggingType = LoggingType.DISTANCE_TIME),
        )

        assertEquals(1, currentSession().exercises.single().sets.size)
    }

    @Test
    fun aDistanceAndTimeSetIsCorrectedAndDeletedLikeAnyOther() = runBlocking {
        repository.logSet(
            TREADMILL,
            SetDraft(durationText = "1680", distanceText = "5", loggingType = LoggingType.DISTANCE_TIME),
        )
        val logged = currentSession().exercises.single()

        repository.editSet(
            logged.sets.single().leadingEffort.id,
            SetDraft(durationText = "1800", distanceText = "5.5", loggingType = LoggingType.DISTANCE_TIME),
        )

        assertEquals(
            Performance.DistanceAndTime(Metres(5500.0), Duration.ofMinutes(30)),
            currentSession().exercises.single().sets.single().leadingEffort.performance,
        )

        repository.deleteSet(logged.id, logged.sets.single().setIndex)
        assertEquals(emptyList<PerformedSet>(), currentSession().exercises.single().sets)
    }

    @Test
    fun correctingARunToAHoldLeavesNoDistanceBehind() = runBlocking {
        repository.logSet(
            TREADMILL,
            SetDraft(durationText = "1680", distanceText = "5", loggingType = LoggingType.DISTANCE_TIME),
        )
        val effortId = currentSession().exercises.single().sets.single().leadingEffort.id

        repository.editSet(effortId, SetDraft(durationText = "60", loggingType = LoggingType.TIME))

        assertEquals(
            Performance.Timed(Duration.ofSeconds(60)),
            currentSession().exercises.single().sets.single().leadingEffort.performance,
        )
        assertEquals(listOf<Double?>(null), storedDistances())
    }

    @Test
    fun aPullUpRecordsItsRepsAndNoWeightAtAll() = runBlocking {
        repository.logSet(PULL_UP, SetDraft(repsText = "8", loggingType = LoggingType.BODYWEIGHT_REPS))

        val effort = currentSession().exercises.single().sets.single().leadingEffort
        assertEquals(Performance.BodyweightReps(addedWeight = null, reps = 8), effort.performance)
        assertEquals(listOf<Double?>(null), storedWeights())
    }

    @Test
    fun aBeltIsLoggedAgainstTheSameExerciseAsAnUnweightedSetOfIt() = runBlocking {
        repository.logSet(PULL_UP, SetDraft(repsText = "8", loggingType = LoggingType.BODYWEIGHT_REPS))
        repository.logSet(
            PULL_UP,
            SetDraft(repsText = "6", addedWeightText = "20", loggingType = LoggingType.BODYWEIGHT_REPS),
        )

        // One exercise and one history: putting a belt on does not start a second one.
        val performed = currentSession().exercises.single()
        assertEquals(PULL_UP, performed.exercise.id)
        assertEquals(
            listOf(
                Performance.BodyweightReps(addedWeight = null, reps = 8),
                Performance.BodyweightReps(Kilograms(20.0), reps = 6),
            ),
            performed.sets.map { it.leadingEffort.performance },
        )
    }

    @Test
    fun assistanceIsStoredAsWeightTakenOffTheLifter() = runBlocking {
        repository.logSet(
            ASSISTED_PULL_UP,
            SetDraft(repsText = "8", addedWeightText = "20", loggingType = LoggingType.ASSISTED),
        )

        // Negative in the row, so the row says which way the help went without asking the exercise.
        assertEquals(listOf(-20.0), storedAddedWeights())
        assertEquals(
            Performance.Assisted(Kilograms(20.0), reps = 8),
            currentSession().exercises.single().sets.single().leadingEffort.performance,
        )
    }

    @Test
    fun anAssistedSetReadsBackAsProgressWhenTheHelpComesDown() = runBlocking {
        repository.logSet(
            ASSISTED_PULL_UP,
            SetDraft(repsText = "8", addedWeightText = "30", loggingType = LoggingType.ASSISTED),
        )
        repository.logSet(
            ASSISTED_PULL_UP,
            SetDraft(repsText = "8", addedWeightText = "20", loggingType = LoggingType.ASSISTED),
        )

        // ADR 0004: less help is more progress. Read back, the second set outranks the first.
        val loads = currentSession().exercises.single().sets.map { it.leadingEffort.performance.loadOrNull!!.value }
        assertTrue("$loads should be increasing", loads[1] > loads[0])
    }

    @Test
    fun aBodyweightSetIsCorrectedAndDeletedLikeAnyOther() = runBlocking {
        repository.logSet(
            PULL_UP,
            SetDraft(repsText = "8", addedWeightText = "20", loggingType = LoggingType.BODYWEIGHT_REPS),
        )
        val logged = currentSession().exercises.single()

        repository.editSet(
            logged.sets.single().leadingEffort.id,
            SetDraft(repsText = "6", loggingType = LoggingType.BODYWEIGHT_REPS),
        )

        // The belt came off, and the row no longer claims one.
        assertEquals(
            Performance.BodyweightReps(addedWeight = null, reps = 6),
            currentSession().exercises.single().sets.single().leadingEffort.performance,
        )
        assertEquals(listOf<Double?>(null), storedAddedWeights())

        repository.deleteSet(logged.id, logged.sets.single().setIndex)
        assertEquals(emptyList<PerformedSet>(), currentSession().exercises.single().sets)
    }

    @Test
    fun aBodyweightSetIsOneSetTowardVolumeLikeAnyOther() = runBlocking {
        repository.logSet(PULL_UP, SetDraft(repsText = "8", loggingType = LoggingType.BODYWEIGHT_REPS))

        assertEquals(1, currentSession().exercises.single().sets.size)
        assertTrue(volumeByMuscle(currentSession().exercises).isNotEmpty())
    }

    @Test
    fun aSetRecordsWhatTheLifterMeasuredAboutIt() = runBlocking {
        repository.logSet(
            BENCH,
            SetDraft("60", "5", rpeText = "8.5", heartRateText = "142", restTakenText = "145"),
        )

        val logged = currentSession().exercises.single().sets.single()
        assertEquals(8.5, logged.measurements.rpe!!, 0.0)
        assertEquals(142, logged.measurements.heartRate)
        assertEquals(Duration.ofSeconds(145), logged.measurements.restTaken)
    }

    @Test
    fun anImplausibleReadingIsNotRecordedAsOne() = runBlocking {
        // A rep count typed into the heart rate field, and an effort rating off its own scale.
        repository.logSet(BENCH, SetDraft("60", "5", rpeText = "12", heartRateText = "8"))

        val logged = currentSession().exercises.single().sets.single()
        assertNull(logged.measurements.rpe)
        assertNull(logged.measurements.heartRate)
    }

    @Test
    fun aSetNothingPrescribedAnsweredNothing() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))

        // Every set logged before there is an engine to prescribe one.
        val logged = currentSession().exercises.single().sets.single()
        assertTrue(logged.prescription.isEmpty)
        assertNull(logged.outcome)
        assertNull(logged.measurements.restTaken)
    }

    @Test
    fun aPrescriptionIsReadBackBesideWhatWasActuallyDone() = runBlocking {
        repository.logSet(BENCH, SetDraft("60", "5"))
        val effortId = currentSession().exercises.single().sets.single().leadingEffort.id

        // Written straight to the row: nothing prescribes yet, and this is the schema the engine
        // will write into rather than a guess at its API.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE set_entry SET recommended_weight_kg = 60.0, recommended_reps = 8, " +
                "recommended_rest_seconds = 120 WHERE id = $effortId",
        )

        val logged = currentSession().exercises.single().sets.single()
        assertEquals(60.0, logged.prescription.load!!.value, 0.0)
        assertEquals(8, logged.prescription.reps)
        assertEquals(Duration.ofMinutes(2), logged.prescription.rest)
        // Asked for eight at sixty and given five at sixty: not an override, a miss.
        assertEquals(SetOutcome.FELL_SHORT, logged.outcome)
    }

    private fun storedAddedWeights(): List<Double?> =
        database.openHelper.readableDatabase
            .query("SELECT added_weight_kg FROM set_entry ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(0)) null else cursor.getDouble(0))
                    }
                }
            }

    private fun storedDistances(): List<Double?> =
        database.openHelper.readableDatabase
            .query("SELECT distance_m FROM set_entry ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(0)) null else cursor.getDouble(0))
                    }
                }
            }

    private fun storedDurations(): List<Int?> =
        database.openHelper.readableDatabase
            .query("SELECT duration_seconds FROM set_entry ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(0)) null else cursor.getInt(0))
                    }
                }
            }

    private fun storedWeights(): List<Double?> =
        database.openHelper.readableDatabase
            .query("SELECT weight_kg FROM set_entry ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(0)) null else cursor.getDouble(0))
                    }
                }
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
        const val HAMSTRING_STRETCH = "90_90_Hamstring"
        const val TREADMILL = "Jogging_Treadmill"
        const val PULL_UP = "Pullups"
        const val ASSISTED_PULL_UP = "Assisted_Pull_Up_Machine"
        const val SQUAT = "Barbell_Squat"
        const val CURL = "Barbell_Curl"
        const val ROW = "Seated_Cable_Rows"
        const val DEADLIFT = "Barbell_Deadlift"
    }
}
