package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.CalibrationRamp
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetOutcome
import com.tylergimpel.gymtracker.domain.SetType
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.volumeByMuscle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What the recommendation engine does against a real database, over real sessions.
 *
 * The arithmetic is the domain's and is tested there. What has to hold here is everything the
 * domain cannot promise on its own: that a recommendation is built from one exercise's own sets and
 * nothing else, that what was asked is written down beside what was done, that what the app worked
 * out survives the release that improves the exercise bank, and that a first session ramps and
 * hands over to the engine at the second.
 */
@RunWith(AndroidJUnit4::class)
class RecommendationPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/London")

    private lateinit var database: GymDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var recommendations: RecommendationRepository

    /** Pinned rather than the device's: what a test reads in is part of what it asserts. */
    private lateinit var profile: ProfileRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        database = Room.databaseBuilder(context, GymDatabase::class.java, TEST_DATABASE)
            .createFromAsset(GymDatabase.ASSET_PATH)
            .addMigrations(*MIGRATIONS)
            .build()
        profile = ProfileRepository(database.profileDao(), WeightUnit.KILOGRAMS)
        exercises = ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            profile,
        )
        recommendations =
            RecommendationRepository(database, database.exerciseDao(), database.sessionDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun anExerciseWithHistoryIsPrescribedAWeightAndARepCount() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val next = recommendations.nextSet(exercise(BENCH))!!.prescription

        // Eight at 60 makes a 76 kg single, which is 60 again at the bottom of the default range —
        // the weight holds while the reps climb, which is the whole of double progression.
        assertEquals(60.0, next.load!!.value, TOLERANCE)
        assertEquals(8, next.reps)
    }

    @Test
    fun noSetOfAnotherExerciseCanMoveARecommendation() = runBlocking {
        train("2026-09-14T18:00") {
            logSet(BENCH, SetDraft("60", "8"))
            logSet(SQUAT, SetDraft("100", "8"))
        }

        // Two lifts, one session, and neither number has anything to say about the other: cross
        // exercise strength ratios are folklore, so the app never extrapolates (ADR 0004).
        assertEquals(60.0, recommendations.nextSet(exercise(BENCH))!!.prescription.load!!.value, TOLERANCE)
        assertEquals(100.0, recommendations.nextSet(exercise(SQUAT))!!.prescription.load!!.value, TOLERANCE)
    }

    @Test
    fun aFirstSessionIsCalibratedRatherThanPrescribed() = runBlocking {
        val next = recommendations.nextSet(exercise(SQUAT))!!

        // Nothing logged, so nothing to prescribe from — and a real number anyway, because an app
        // that says "I don't know" to somebody standing at a rack has not helped them. The empty
        // bar, which is where every novice programme starts.
        assertTrue(next is Recommendation.Calibrating)
        assertEquals(20.0, next.prescription.load!!.value, TOLERANCE)
        assertEquals(CalibrationRamp.RAMP_REPS, next.prescription.reps)
    }

    @Test
    fun theRampClimbsAsTheSessionGoesOn() = runBlocking {
        train("2026-09-14T18:00", finish = false) {
            logSet(SQUAT, SetDraft("20", "5", type = SetType.CALIBRATION))
        }

        // Today's sets do not prescribe — they are not history yet — but they do say how far up
        // the ramp the lifter has got, which is the one thing the current session is asked.
        val second = recommendations.nextSet(exercise(SQUAT))!!
        assertTrue(second is Recommendation.Calibrating)
        assertEquals(30.0, second.prescription.load!!.value, TOLERANCE)
    }

    @Test
    fun aRampSetIsRecordedAsACalibrationSetAndEarnsNoVolume() = runBlocking {
        val ramp = recommendations.nextSet(exercise(SQUAT))!!

        val sessions = train("2026-09-14T18:00", finish = false) {
            // Logged exactly as offered, which is the one-tap path: the type comes with the
            // recommendation rather than being left to the lifter to remember.
            logSet(SQUAT, SetDraft().showing(ramp), prescription = ramp.prescription)
        }

        val logged = sessions.observeActiveSession().first()!!
        val performed = sessions.observeSession(logged.id).first()!!.exercises
        assertEquals(SetType.CALIBRATION, performed.single().sets.single().type)
        // A session spent finding out what somebody can lift must not corrupt the history it is
        // establishing, and volume is the first thing it would corrupt.
        assertEquals(emptyMap<String, Double>(), volumeByMuscle(performed))
    }

    @Test
    fun theAppStopsCalibratingOnceTheFirstSessionIsHistory() = runBlocking {
        train("2026-09-14T18:00") {
            logSet(SQUAT, SetDraft("40", "5", type = SetType.CALIBRATION))
        }

        // The ramp's last set is the exercise's first data point, and one data point is enough to
        // stop guessing. A calibration set earns no volume and still informs: answering this
        // question is why it was performed.
        val next = recommendations.nextSet(exercise(SQUAT))!!
        assertTrue(next is Recommendation.Prescribing)
        assertEquals(35.0, next.prescription.load!!.value, TOLERANCE)
    }

    @Test
    fun aLiftTheLifterDoesNotWantCoachedIsNotCalibratedEither() = runBlocking {
        exercises.setProgressionModel(SQUAT, ProgressionModel.NONE)

        // Not a recommendation and not a ramp: the app was told to stop coaching this lift, and
        // offering to find a weight for it would be the same overruling by another route.
        assertNull(recommendations.nextSet(exercise(SQUAT)))
    }

    @Test
    fun aLiftTheLifterDoesNotWantCoachedIsNeverPrescribedFor() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        exercises.setProgressionModel(BENCH, ProgressionModel.NONE)

        // The history is there and the app has been told not to use it. Saying nothing is the
        // point of the setting, and the alternative would be the app overruling the lifter.
        assertNull(recommendations.nextSet(exercise(BENCH)))
    }

    @Test
    fun whatWasPrescribedIsRecordedBesideWhatWasDone() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val asked = recommendations.nextSet(exercise(BENCH))!!.prescription
        val sessions = train("2026-09-16T18:00", finish = false) {
            // Offered 60 and loaded 65: a decision, and one that only reads as a decision because
            // the offer was written down beside it.
            logSet(BENCH, SetDraft("65", "8"), prescription = asked)
        }

        val logged = sessions.observeActiveSession().first()!!
        val set = sessions.observeSession(logged.id).first()!!.exercises.single().sets.single()
        assertEquals(60.0, set.prescription.load!!.value, TOLERANCE)
        assertEquals(8, set.prescription.reps)
        assertEquals(SetOutcome.OVERRIDDEN, set.outcome)
    }

    @Test
    fun takingTheRecommendationAsOfferedReadsAsHavingFollowedIt() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val asked = recommendations.nextSet(exercise(BENCH))!!.prescription
        val sessions = train("2026-09-16T18:00", finish = false) {
            logSet(BENCH, SetDraft("60", "8"), prescription = asked)
        }

        val logged = sessions.observeActiveSession().first()!!
        val set = sessions.observeSession(logged.id).first()!!.exercises.single().sets.single()
        assertEquals(SetOutcome.FOLLOWED, set.outcome)
    }

    @Test
    fun todaysSetsDoNotMoveTodaysRecommendation() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val sessions = train("2026-09-16T18:00", finish = false) {
            logSet(BENCH, SetDraft("60", "12"))
        }
        val next = recommendations.nextSet(exercise(BENCH))!!.prescription

        // Twelve at 60 has beaten the range, and none of that reaches the set the lifter is about
        // to do: today is not history yet, so the second set of a session is still the set they
        // were prescribed rather than a heavier one earned ten minutes ago.
        assertEquals(60.0, next.load!!.value, TOLERANCE)
        assertEquals(8, next.reps)

        sessions.finishSession(sessions.observeActiveSession().first()!!.id)

        // Once it is history it counts: the reps asked for are what was managed at this weight.
        // The weight itself holds — stepping it up after a beaten range is #50's, and it takes two
        // consecutive sessions.
        val afterwards = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(60.0, afterwards.load!!.value, TOLERANCE)
        assertEquals(12, afterwards.reps)
    }


    @Test
    fun beatingTheRangeTwiceRunningAdvancesTheWeightOnceItIsHistory() = runBlocking {
        // A first session to give the engine something to prescribe from, then two sessions that
        // clear the top of the default 8 - 12 range at what it asked for.
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        val first = recommendations.nextSet(exercise(BENCH))!!.prescription
        train("2026-09-16T18:00") {
            logSet(BENCH, SetDraft("60", "14"), prescription = first)
        }
        val second = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(60.0, second.load!!.value, TOLERANCE)

        train("2026-09-18T18:00") {
            logSet(BENCH, SetDraft("60", "14"), prescription = second)
        }

        // One increment on the bar, and the reps start again at the bottom of the range.
        val next = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(62.5, next.load!!.value, TOLERANCE)
        assertEquals(8, next.reps)
    }

    @Test
    fun twoSessionsShortOfTheWeightBackItOff() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        val asked = recommendations.nextSet(exercise(BENCH))!!.prescription

        train("2026-09-16T18:00") { logSet(BENCH, SetDraft("60", "4"), prescription = asked) }
        // One short session costs nothing: the same weight comes back.
        val afterOne = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(60.0, afterOne.load!!.value, TOLERANCE)

        train("2026-09-18T18:00") { logSet(BENCH, SetDraft("60", "4"), prescription = afterOne) }

        // Two running, and about a tenth comes off — rounded down to a weight that exists.
        assertEquals(52.5, recommendations.nextSet(exercise(BENCH))!!.prescription.load!!.value, TOLERANCE)
    }

    @Test
    fun aWeightTheLifterChoseThemselvesNeverBacksTheirTrainingOff() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        val asked = recommendations.nextSet(exercise(BENCH))!!.prescription

        // Two sessions at a lighter weight of their own. Falling short and deciding to go lighter
        // look identical in a log that holds only what was done, which is why the prescription is
        // written down beside it.
        train("2026-09-16T18:00") { logSet(BENCH, SetDraft("50", "8"), prescription = asked) }
        train("2026-09-18T18:00") { logSet(BENCH, SetDraft("50", "8"), prescription = asked) }

        assertEquals(60.0, recommendations.nextSet(exercise(BENCH))!!.prescription.load!!.value, TOLERANCE)
    }

    @Test
    fun aLiftThatKeepsComingUpShortStopsBeingPrescribedFor() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        var asked = recommendations.nextSet(exercise(BENCH))!!.prescription
        repeat(5) { session ->
            train("2026-09-2${session}T18:00") {
                logSet(BENCH, SetDraft(asked.load!!.value.toString(), "3"), prescription = asked)
            }
            recommendations.nextSet(exercise(BENCH))
                ?.takeIf { it !is Recommendation.Stopped }
                ?.let { asked = it.prescription }
        }

        // Five sessions short of the same lift, and the app says so rather than guessing a sixth
        // time. Nothing is prescribed, so a set logged now records that nothing was asked.
        assertEquals(Recommendation.Stopped, recommendations.nextSet(exercise(BENCH)))
    }

    @Test
    fun everyPrescribedSetCarriesARestDerivedFromWhatThatSetIs() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val next = recommendations.nextSet(exercise(BENCH))!!.prescription

        // A multi-joint lift worked in the default 8 - 12 range: two and a half minutes, which is
        // the middle of what the research gives for it.
        assertEquals(Duration.ofSeconds(150), next.rest)
    }

    @Test
    fun aLifterWhoNeedsLongerOnAMachineSaysSoOnce() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        exercises.setRest(BENCH, Duration.ofMinutes(4))

        // Their gym, their machine, their answer — and it outlives the session.
        assertEquals(Duration.ofMinutes(4), recommendations.nextSet(exercise(BENCH))!!.prescription.rest)
    }

    @Test
    fun aSupersetRestsAfterTheGroupRatherThanBetweenItsMembers() = runBlocking {
        // History first, so that what the app asks for today is work rather than a ramp rung.
        train("2026-09-14T18:00") {
            logSet(BENCH, SetDraft("60", "8"))
            logSet(SQUAT, SetDraft("100", "8"))
        }
        val sessions = train("2026-09-16T18:00", finish = false) {
            logSet(BENCH, SetDraft("60", "8"))
            logSet(SQUAT, SetDraft("100", "8"))
            val logged = observeActiveSession().first()!!
            val performed = observeSession(logged.id).first()!!.exercises
            groupWithNext(performed.first().id)
        }

        // No member carries a rest: the point of alternating is not stopping.
        assertNull(recommendations.nextSet(exercise(BENCH))!!.prescription.rest)

        // The group carries it instead, for as long as the hardest thing in it asked.
        val logged = sessions.observeActiveSession().first()!!
        val group = sessions.observeSession(logged.id).first()!!.supersets.single()
        assertEquals(150, group.restSeconds)
    }

    @Test
    fun whatTheAppWorkedOutSurvivesABankUpdate() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        val before = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(1, countOf("exercise_progress"))

        // A release that rewrites every shipped exercise row, which is what an update is.
        exercises.applyBankUpdate(bank().map { it.copy(chosenProgressionModel = null) })

        assertEquals(1, countOf("exercise_progress"))
        val after = recommendations.nextSet(exercise(BENCH))!!.prescription
        assertEquals(before.load!!.value, after.load!!.value, TOLERANCE)
        assertEquals(before.reps, after.reps)
    }

    @Test
    fun anEstimateIsForgottenWhenTheTrainingItCameFromIsDeleted() = runBlocking {
        val sessions = train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        recommendations.nextSet(exercise(BENCH))
        assertEquals(1, countOf("exercise_progress"))

        val performed = sessions.observeLastFinished().first()!!.exercises.single()
        sessions.deleteSet(performed.id, performed.sets.single().setIndex)

        // Nothing left to have worked anything out from, so the row goes rather than prescribing
        // from training that is no longer there — and the exercise is back to being one the app
        // knows nothing about, which is a ramp rather than a recommendation.
        assertTrue(recommendations.nextSet(exercise(BENCH)) is Recommendation.Calibrating)
        assertEquals(0, countOf("exercise_progress"))
    }

    /**
     * Logs a session on [localDateTime] and finishes it, so that what it holds is history.
     *
     * A repository per day rather than a clock that moves: the session a set belongs to is decided
     * by the clock the repository was built with, and pinning one per session says what each test
     * means more plainly than advancing a shared one.
     */
    private suspend fun train(
        localDateTime: String,
        finish: Boolean = true,
        log: suspend SessionRepository.() -> Unit,
    ): SessionRepository {
        val sessions = SessionRepository(
            database = database,
            sessions = database.sessionDao(),
            clock = Clock.fixed(at(localDateTime), zone),
            zone = zone,
            profile = profile,
        )
        sessions.log()
        if (finish) {
            sessions.observeActiveSession().first()?.let { sessions.finishSession(it.id) }
        }
        return sessions
    }

    private suspend fun bank(): List<Exercise> = exercises.observeBank().first()

    private suspend fun exercise(id: String): Exercise = bank().single { it.id == id }

    private fun at(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    private fun countOf(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "recommendation-persistence-test.db"
        const val BENCH = "Barbell_Bench_Press_-_Medium_Grip"
        const val SQUAT = "Barbell_Squat"

        /** Five grams: far below the smallest plate, far above binary floating point. */
        const val TOLERANCE = 0.005
    }
}
