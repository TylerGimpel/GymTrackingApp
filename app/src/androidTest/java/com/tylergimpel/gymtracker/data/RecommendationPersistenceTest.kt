package com.tylergimpel.gymtracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.SetOutcome
import com.tylergimpel.gymtracker.domain.WeightUnit
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
 * What the recommendation engine does against a real database, over real sessions.
 *
 * The arithmetic is the domain's and is tested there. What has to hold here is everything the
 * domain cannot promise on its own: that a recommendation is built from one exercise's own sets and
 * nothing else, that what was asked is written down beside what was done, and that what the app
 * worked out survives the release that improves the exercise bank.
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

        val next = recommendations.prescribeNextSet(exercise(BENCH))!!

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
        assertEquals(60.0, recommendations.prescribeNextSet(exercise(BENCH))!!.load!!.value, TOLERANCE)
        assertEquals(100.0, recommendations.prescribeNextSet(exercise(SQUAT))!!.load!!.value, TOLERANCE)
    }

    @Test
    fun anExerciseNobodyHasTrainedHasNothingToPrescribe() = runBlocking {
        assertNull(recommendations.prescribeNextSet(exercise(SQUAT)))
    }

    @Test
    fun aLiftTheLifterDoesNotWantCoachedIsNeverPrescribedFor() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        exercises.setProgressionModel(BENCH, ProgressionModel.NONE)

        // The history is there and the app has been told not to use it. Saying nothing is the
        // point of the setting, and the alternative would be the app overruling the lifter.
        assertNull(recommendations.prescribeNextSet(exercise(BENCH)))
    }

    @Test
    fun whatWasPrescribedIsRecordedBesideWhatWasDone() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }

        val asked = recommendations.prescribeNextSet(exercise(BENCH))!!
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

        val asked = recommendations.prescribeNextSet(exercise(BENCH))!!
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
        val next = recommendations.prescribeNextSet(exercise(BENCH))!!

        // Twelve at 60 has beaten the range, and none of that reaches the set the lifter is about
        // to do: today is not history yet, so the second set of a session is still the set they
        // were prescribed rather than a heavier one earned ten minutes ago.
        assertEquals(60.0, next.load!!.value, TOLERANCE)
        assertEquals(8, next.reps)

        sessions.finishSession(sessions.observeActiveSession().first()!!.id)

        // Once it is history it counts: the reps asked for are what was managed at this weight.
        // The weight itself holds — stepping it up after a beaten range is #50's, and it takes two
        // consecutive sessions.
        val afterwards = recommendations.prescribeNextSet(exercise(BENCH))!!
        assertEquals(60.0, afterwards.load!!.value, TOLERANCE)
        assertEquals(12, afterwards.reps)
    }

    @Test
    fun whatTheAppWorkedOutSurvivesABankUpdate() = runBlocking {
        train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        val before = recommendations.prescribeNextSet(exercise(BENCH))!!
        assertEquals(1, countOf("exercise_progress"))

        // A release that rewrites every shipped exercise row, which is what an update is.
        exercises.applyBankUpdate(bank().map { it.copy(chosenProgressionModel = null) })

        assertEquals(1, countOf("exercise_progress"))
        val after = recommendations.prescribeNextSet(exercise(BENCH))!!
        assertEquals(before.load!!.value, after.load!!.value, TOLERANCE)
        assertEquals(before.reps, after.reps)
    }

    @Test
    fun anEstimateIsForgottenWhenTheTrainingItCameFromIsDeleted() = runBlocking {
        val sessions = train("2026-09-14T18:00") { logSet(BENCH, SetDraft("60", "8")) }
        recommendations.prescribeNextSet(exercise(BENCH))
        assertEquals(1, countOf("exercise_progress"))

        val performed = sessions.observeLastFinished().first()!!.exercises.single()
        sessions.deleteSet(performed.id, performed.sets.single().setIndex)

        // Nothing left to have worked anything out from, so the row goes rather than prescribing
        // from training that is no longer there.
        assertNull(recommendations.prescribeNextSet(exercise(BENCH)))
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
