package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.whatItTrains
import com.tylergimpel.gymtracker.domain.liftingCadence
import com.tylergimpel.gymtracker.domain.SPLIT_SHAPES
import com.tylergimpel.gymtracker.domain.rebuildFrom
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.SplitShape
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.unperformedExerciseCounts
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private lateinit var exercises: ExerciseRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        routines = RoutineRepository(
            database,
            database.routineDao(),
            database.sessionDao(),
            clock,
        )
        sessions = SessionRepository(
            database,
            database.sessionDao(),
            clock,
            zone,
            ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        )
        exercises = ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS),
        )
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
    fun aBackdatedSessionIsLoggedInFullAndLeavesTheCursorExactlyWhereItWas() = runBlocking {
        val routine = pushPullLegs()
        assertEquals("Push", routines.observeProgress().first()!!.cursor?.name)

        // Trained on Saturday, remembered on Monday.
        val sessionId = complete(routine.rotation.first(), on = LocalDate.of(2026, 9, 12))

        // The training is in history in full, with the day it realised named on it...
        assertEquals(1, countOf("session"))
        assertEquals(1, countOf("set_entry"))
        assertEquals("Push", database.sessionDao().session(sessionId)!!.routineDayName)
        // ...and what comes next has not moved, because this session was not about that.
        assertEquals(0, countOf("cycle_completion"))
        assertEquals("Push", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun twoSessionsInOneCalendarDayEachAdvanceTheCursorThatTheyMatched() = runBlocking {
        val routine = pushPullLegs()

        complete(routine.rotation[0])
        complete(routine.rotation[1])

        // A morning and an evening session, both on one date, each dealing with the day it
        // realised. Nothing here counts days of the week.
        assertEquals(2, countOf("session"))
        assertEquals(2, countOf("cycle_completion"))
        assertEquals("Legs", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun aSecondSessionInOneDayAdvancesTheCursorOnlyIfItMatchedTheDayItPointedAt() = runBlocking {
        val routine = pushPullLegs()

        complete(routine.rotation[0])
        // The rack was busy, so the evening session trained Legs rather than the Pull the cursor
        // had moved to. Two sessions, two days dealt with, but the cursor advanced once: it is
        // still holding Pull (ADR 0001).
        complete(routine.rotation[2])

        assertEquals(2, countOf("session"))
        assertEquals(2, countOf("cycle_completion"))
        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)
    }

    @Test
    fun aLongLayoffLeavesTheCursorWhereItWasWithNothingToCatchUpOn() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation.first())
        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)

        // Six weeks later, read by a repository whose clock has moved on. No missed sessions, no
        // catch-up, no new cycle: the cursor takes no date and so has nothing to notice.
        val sixWeeksLater = RoutineRepository(
            database,
            database.routineDao(),
            database.sessionDao(),
            Clock.fixed(at("2026-10-26T18:00"), zone),
        )

        assertEquals("Pull", sixWeeksLater.observeProgress().first()!!.cursor?.name)
        assertEquals(1, countOf("cycle"))
    }

    @Test
    fun startingASessionFromARoutineDayCopiesWhatThatDayHolds() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, SQUAT)

        sessions.startSession(SessionPlan(dayNow(push).exercises))

        // What the session is for, in the order the day holds it.
        assertEquals(listOf(BENCH, SQUAT), planOf(currentSessionId()))
    }

    @Test
    fun aSessionStartedWithNoRoutineDayHasNoPlanAndThatIsNotAnError() = runBlocking {
        pushPullLegs()

        // Logging a set is enough to start training; nothing about it is missing.
        sessions.logSet(BENCH, SetDraft("60", "5"))

        val logged = sessions.observeSession(currentSessionId()).first()!!
        assertTrue(logged.plan.isEmpty)
        assertTrue(logged.unperformed.isEmpty())
    }

    @Test
    fun aPartialSessionReportsWhatItWasForAgainstWhatItDid() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, SQUAT)
        sessions.startSession(SessionPlan(dayNow(push).exercises))

        sessions.logSet(BENCH, SetDraft("60", "5"))

        // Two planned, one trained — and the one that was not is named rather than absent.
        val logged = sessions.observeSession(currentSessionId()).first()!!
        assertEquals(2, logged.plan.exercises.size)
        assertEquals(listOf(SQUAT), logged.unperformed.map { it.id })
    }

    @Test
    fun changingADaysExercisesAfterwardsLeavesAnExistingPlanAlone() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        sessions.startSession(SessionPlan(dayNow(push).exercises))
        val session = currentSessionId()

        // The routine moves on: the day loses what it had and gains something else.
        routines.removeExerciseFromDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, SQUAT)

        // The session is still a record of what it was for at the time.
        assertEquals(listOf(BENCH), planOf(session))
    }

    @Test
    fun deletingTheRoutineLeavesEverySessionPlanIntact() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        sessions.startSession(SessionPlan(dayNow(push).exercises))
        val session = currentSessionId()

        routines.deleteRoutine()

        // The seam this table exists on: the plan is a copy, so nothing about revising or throwing
        // away a routine can reach it.
        assertNull(routines.observeProgress().first())
        assertEquals(0, countOf("routine_day_exercise"))
        assertEquals(listOf(BENCH), planOf(session))
    }

    @Test
    fun aSessionThatNamesItsDayOnlyAtTheEndIsPlannedFromThatDay() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, SQUAT)

        // Walked in and trained without announcing anything, which is the ordinary path.
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        assertTrue(sessions.observeSession(session.id).first()!!.plan.isEmpty)

        sessions.finishSession(session.id, routine, dayNow(push))

        // ADR 0006 leans on every session that realised a day carrying that day's contents, so the
        // snapshot cannot wait for a lifter who never pressed Start.
        assertEquals(listOf(BENCH, SQUAT), planOf(session.id))
        val logged = sessions.observeSession(session.id).first()!!
        assertEquals(listOf(SQUAT), logged.unperformed.map { it.id })
    }

    @Test
    fun aSessionTrainedOutOfOrderIsPlannedFromTheDayItSaysItRealised() = runBlocking {
        val routine = pushPullLegs()
        routines.addExerciseToDay(routine.rotation[0].id, BENCH)
        routines.addExerciseToDay(routine.rotation[2].id, SQUAT)

        // The rack was busy, so Legs got trained while the cursor still pointed at Push.
        sessions.logSet(SQUAT, SetDraft("100", "5"))
        val session = sessions.observeActiveSession().first()!!
        sessions.finishSession(session.id, routine, dayNow(routine.rotation[2]))

        assertEquals(listOf(SQUAT), planOf(session.id))
    }

    @Test
    fun aSessionAlreadyPlannedKeepsWhatItSetOutToDo() = runBlocking {
        val routine = pushPullLegs()
        routines.addExerciseToDay(routine.rotation[0].id, BENCH)
        routines.addExerciseToDay(routine.rotation[2].id, SQUAT)
        sessions.startSession(SessionPlan(dayNow(routine.rotation[0]).exercises))
        val session = sessions.observeActiveSession().first()!!

        // Started as Push and finished saying it realised Legs. A session is planned once: what it
        // was for is what the lifter was looking at when they began.
        sessions.finishSession(session.id, routine, dayNow(routine.rotation[2]))

        assertEquals(listOf(BENCH), planOf(session.id))
    }

    @Test
    fun whatHasBeenPlannedAndLeftUntrainedLatelyIsDerivable() = runBlocking {
        val routine = pushPullLegs()
        val push = routine.rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, SQUAT)

        repeat(2) {
            sessions.startSession(SessionPlan(dayNow(push).exercises))
            sessions.logSet(BENCH, SetDraft("60", "5"))
            val session = sessions.observeActiveSession().first()!!
            sessions.finishSession(session.id, routine, dayNow(push))
        }

        // Asked for twice, trained never — the shape swap and dislike are meant to read, derived
        // from stored sessions rather than only from objects built in a test.
        assertEquals(
            mapOf(SQUAT to 2),
            unperformedExerciseCounts(sessions.observeRecent().first()),
        )
    }

    @Test
    fun aDayHoldsEachExerciseOnceHoweverOftenItIsAdded() = runBlocking {
        val push = pushPullLegs().rotation.first()

        routines.addExerciseToDay(push.id, BENCH)
        routines.addExerciseToDay(push.id, BENCH)

        assertEquals(listOf(BENCH), dayNow(push).exercises.map { it.exercise.id })
    }

    @Test
    fun fillingInADayIsNotARevisionAndLeavesTheCycleAlone() = runBlocking {
        val routine = pushPullLegs()
        complete(routine.rotation.first())
        assertEquals("Pull", routines.observeProgress().first()!!.cursor?.name)

        routines.addExerciseToDay(routine.rotation[1].id, BENCH)

        // The rotation still holds the same days in the same order, so nothing about the cycle
        // changed — losing your place for filling in a day would be absurd.
        val after = routines.observeProgress().first()!!
        assertEquals("Pull", after.cursor?.name)
        assertEquals(1, after.cycle?.ordinal)
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
            cardio = CardioPlacement.NONE,
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

    /**
     * Trains [day] with one real session, returning that session's id.
     *
     * [on] refiles it before it is finished, which is how a session trained on Saturday and logged
     * on Monday reaches the cycle rules.
     */
    private suspend fun complete(day: RoutineDay, on: LocalDate? = null): Long {
        sessions.logSet(BENCH, SetDraft("60", "5"))
        val session = sessions.observeActiveSession().first()!!
        on?.let { sessions.refile(session.id, it) }
        val routine = routines.observeProgress().first()!!.routine
        sessions.finishSession(session.id, routine, day)
        routines.recordCompletion(routine.id, day, session.id)
        return session.id
    }

    @Test
    fun aDaySaysHowManySetsAndTheEngineAnswersForTheGoal() = runBlocking {
        val push = pushPullLegs().rotation.first()
        routines.addExerciseToDay(push.id, BENCH)

        // Nothing has written a number of its own, so the routine's goal answers. Hypertrophy by
        // default, which is four — a count arrives whether or not a generator ever ran.
        assertEquals(listOf(4), dayNow(push).exercises.map { it.sets })

        reGoal(RoutineGoal.STRENGTH)

        // And it follows the goal rather than being stamped into the day when it was added. A set
        // count is not something a lifter is mid-progression on, unlike a rep range.
        assertEquals(listOf(5), dayNow(push).exercises.map { it.sets })
    }

    @Test
    fun aPlanKeepsWhatItAskedForHoweverOftenTheRoutineChangesAfterwards() = runBlocking {
        val push = pushPullLegs().rotation.first()
        routines.addExerciseToDay(push.id, BENCH)
        sessions.startSession(SessionPlan(dayNow(push).exercises))
        val session = currentSessionId()

        reGoal(RoutineGoal.STRENGTH)
        routines.removeExerciseFromDay(push.id, BENCH)

        // The routine has been re-goaled and the exercise taken out of it entirely, and the
        // session still reads what it was actually asked for. A plan is a snapshot, which is what
        // makes revising a routine safe (ADR 0006).
        assertEquals(
            listOf(4),
            sessions.observeSession(session).first()!!.plan.exercises.map { it.sets },
        )
    }

    @Test
    fun aCardioDayTakesItsTurnAndSkippingItBehavesLikeAnyOtherSkip() = runBlocking {
        val id = routines.createRoutine(name = "Upper / Lower / Cardio")
        routines.addDay(id, "Upper")
        routines.addDay(id, "Lower")
        routines.addDay(id, "Cardio", RoutineDayKind.CARDIO)
        val rotation = routines.observeProgress().first()!!.routine.rotation

        complete(rotation[0])
        complete(rotation[1])

        // Its turn, like any other position. A rotation that stepped over it would be one the
        // lifter could never train the cardio in.
        assertEquals("Cardio", cursorNow()?.name)

        routines.recordSkip(id, rotation[2])

        // And dealt with by a skip exactly as a lifting day is: the cycle is over and the next
        // one opens at the top. Skipping meaning two things is what this is guarding against.
        assertEquals("Upper", cursorNow()?.name)
    }

    @Test
    fun aCardioPositionIsExcludedFromWhatCountsAsLiftingAndSameDayCardioTakesNoPosition() =
        runBlocking {
            val id = routines.createRoutine(name = "Upper / Lower / Cardio")
            routines.addDay(id, "Upper")
            routines.addDay(id, "Lower")
            routines.addDay(id, "Cardio", RoutineDayKind.CARDIO)

            val routine = routines.observeProgress().first()!!.routine

            // Three positions, two of them training — the two numbers that stop a lifter with a
            // cardio day being under-prescribed by 40% (ADR 0005).
            assertEquals(3, routine.days.size)
            assertEquals(2, routine.liftingPositions)

            // Cardio after lifting is a choice about how they train, and it moves neither number:
            // the treadmill goes inside a lifting day and the lifting day still happened.
            routines.editProperties(
                name = routine.name,
                goal = routine.goal,
                cadence = routine.cadence,
                equipment = routine.equipment,
                repPreference = routine.repPreference,
                cardio = CardioPlacement.AFTER_LIFTING,
            )
            val after = routines.observeProgress().first()!!.routine
            assertEquals(CardioPlacement.AFTER_LIFTING, after.cardio)
            assertEquals(3, after.days.size)
            assertEquals(2, after.liftingPositions)
        }

    @Test
    fun aShapeLaysOutANamedRotationOfEmptyDays() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "upper-lower-2" }.withCardioDay()

        routines.createFromShape(
            name = shape.description,
            shape = shape,
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 5,
            equipment = setOf("barbell"),
            repPreference = RepPreference.MODERATE,
            cardio = CardioPlacement.OWN_DAY,
        )

        val routine = routines.observeProgress().first()!!.routine

        // A rotation and nothing in it. Filling the days is the other half of generation, and a
        // separate decision: the structure is templatable and the exercises are not.
        assertEquals(listOf("Upper", "Lower", "Cardio"), routine.rotation.map { it.name })
        assertTrue(routine.rotation.all { it.exercises.isEmpty() })

        // Three positions at five sessions a week, two of them lifting: 3.33 lifting sessions.
        assertEquals(2, routine.liftingPositions)
        assertEquals(3.333, routine.liftingCadence, 0.001)
    }

    @Test
    fun theGeneratorFillsEveryDayAgainstTheRealBank() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = exercises.observeBank().first()

        routines.createFromShape(
            name = shape.description,
            shape = shape,
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 3,
            equipment = setOf("barbell", "dumbbell", "cable", "machine", "body only"),
            repPreference = RepPreference.MODERATE,
            cardio = CardioPlacement.NONE,
            bank = bank,
        )

        val routine = routines.observeProgress().first()!!.routine

        // Against the shipped 893 rows rather than a fixture, because what this proves is that
        // the bank the app actually carries can answer the days the app actually offers.
        assertEquals(3, routine.rotation.size)
        routine.rotation.forEach { day ->
            assertTrue("${day.name} came back empty", day.exercises.isNotEmpty())
        }

        // And the cycle trains something everywhere, which is the coverage check the report reads.
        assertTrue(routine.whatItTrains().isNotEmpty())
    }

    @Test
    fun regeneratingKeepsWhatWasPinnedAndReplacesWhatWasNot() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = generated(shape)
        val push = routines.observeProgress().first()!!.routine.rotation.first()
        val kept = dayNow(push).exercises.first().exercise

        routines.setPinned(push.id, kept.id, pinned = true)
        routines.regenerate(shape, bank, seed = 1)

        val after = dayNow(push).exercises.map { it.exercise.id }

        // The pin is a fixed constraint the day is rebuilt around, and it stays pinned — a pin
        // that did not survive its own regeneration was not a pin.
        assertTrue("the pinned exercise was replaced", kept.id in after)
        assertTrue(
            routines.observeProgress().first()!!.routine.rotation
                .first().exercises.isNotEmpty(),
        )
    }

    @Test
    fun askingForVarietyChangesTheRoutineAndNothingElseDoes() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = generated(shape)
        val before = everyExercise()

        // Reading the routine, editing a property, finishing a session: none of them reseeds.
        reGoal(RoutineGoal.HYPERTROPHY)
        assertEquals(before, everyExercise())

        routines.regenerate(shape, bank, seed = 2)
        assertNotEquals("asking for variety gave back the same routine", before, everyExercise())
    }

    @Test
    fun oneStepBackFromARegenerationPutsTheOldRoutineBack() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = generated(shape)
        val before = everyExercise()

        routines.regenerate(shape, bank, seed = 4)
        assertTrue(routines.canUndo())

        routines.undoRegeneration()

        // Byte for byte what they had, which is the whole of "I regenerated and I hate it".
        assertEquals(before, everyExercise())
        // And one step is one step: taking it spends it.
        assertFalse(routines.canUndo())
    }

    @Test
    fun aRegenerationCanNoLongerBeUndoneOnceTheRoutineHasBeenTrained() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        val bank = generated(shape)
        routines.regenerate(shape, bank, seed = 5)
        assertTrue(routines.canUndo())

        complete(routines.observeProgress().first()!!.routine.rotation.first())

        // Past a logged session, what undo would restore is no longer what the lifter is doing
        // (ADR 0006) — and a step back reaching across it would restore somebody else's evening.
        assertFalse(routines.canUndo())
    }

    @Test
    fun changingWhatARoutineIsForDoesNotTouchWhatItsDaysHold() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        generated(shape)
        val before = everyExercise()
        val cycleBefore = routines.observeProgress().first()!!.cycle?.ordinal

        reGoal(RoutineGoal.STRENGTH)

        // Editing a property is not a **Revision**: the rotation is untouched, so the cycle and
        // the cursor are where the lifter left them — and, crucially, so are the exercises.
        // Rebuilding around a new goal is something they are offered, never something done to
        // them, and declining has nothing to undo because nothing happened.
        assertEquals(before, everyExercise())
        assertEquals(cycleBefore, routines.observeProgress().first()!!.cycle?.ordinal)
    }

    @Test
    fun changingGymsNeverCostsTheLifterTheirRoutine() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        generated(shape)
        val before = everyExercise()

        val routine = routines.observeProgress().first()!!.routine
        routines.editProperties(
            name = routine.name,
            goal = routine.goal,
            cadence = routine.cadence,
            equipment = setOf("body only"),
            repPreference = routine.repPreference,
            cardio = routine.cardio,
        )

        // Somebody ticking a machine because they joined a new gym has said nothing about wanting
        // a different routine. Quietly rebuilding it for them would be the worst kind of helpful.
        assertEquals(before, everyExercise())
    }

    @Test
    fun aRoutineComesBackFromTheSessionsThatFollowedIt() = runBlocking {
        val shape = SPLIT_SHAPES.single { it.id == "push-pull-legs-3" }
        generated(shape)
        val was = everyExercise()
        val rotation = routines.observeProgress().first()!!.routine.rotation

        // A cycle's worth of training, each session snapshotting the day it realised.
        rotation.forEach { complete(it) }

        // Then the routine is gone entirely — deleted, as if they moved on from it months ago.
        routines.deleteRoutine()
        assertEquals(null, routines.observeProgress().first())

        val recovered = rebuildFrom(sessions.observeRecent(40).first())
        routines.rebuildFromSessions(
            name = "Last spring",
            recovered = recovered,
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 3,
            equipment = setOf("barbell"),
            repPreference = RepPreference.MODERATE,
            cardio = CardioPlacement.NONE,
        )

        // Back, out of the log alone. ADR 0006 leans on exactly this to justify not versioning
        // routines: the snapshots were taken for a different reason and happen to preserve every
        // routine day the lifter ever trained.
        assertEquals(was, everyExercise())
    }

    /** A routine generated from [shape] against the real bank, and that bank. */
    private suspend fun generated(shape: SplitShape): List<Exercise> {
        val bank = exercises.observeBank().first()
        routines.createFromShape(
            name = shape.description,
            shape = shape,
            goal = RoutineGoal.HYPERTROPHY,
            cadence = 3,
            equipment = setOf("barbell", "dumbbell", "cable", "machine", "body only"),
            repPreference = RepPreference.MODERATE,
            cardio = CardioPlacement.NONE,
            bank = bank,
        )
        return bank
    }

    /** Every exercise the routine holds, in order, which is what a regeneration changes. */
    private suspend fun everyExercise(): List<String> =
        routines.observeProgress().first()!!.routine.rotation
            .flatMap { day -> day.exercises.map { "${day.name}:${it.exercise.id}:${it.sets}" } }

    /** Where the cursor points now, which is the one question the rotation answers. */
    private suspend fun cursorNow(): RoutineDay? =
        routines.observeProgress().first()!!.cursor

    /** Changes what the routine is for, leaving everything else about it as it was. */
    private suspend fun reGoal(goal: RoutineGoal) {
        val routine = routines.observeProgress().first()!!.routine
        routines.editProperties(
            name = routine.name,
            goal = goal,
            cadence = routine.cadence,
            equipment = routine.equipment,
            repPreference = routine.repPreference,
            cardio = routine.cardio,
        )
    }

    /** The day as it stands now, since [pushPullLegs] returned it before it held anything. */
    private suspend fun dayNow(day: RoutineDay): RoutineDay =
        routines.observeProgress().first()!!.routine.rotation.single { it.id == day.id }

    private suspend fun currentSessionId(): Long =
        sessions.observeActiveSession().first()!!.id

    private suspend fun planOf(sessionId: Long): List<String> =
        sessions.observeSession(sessionId).first()!!.plan.exercises.map { it.exercise.id }

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
        const val SQUAT = "Barbell_Squat"
    }
}
