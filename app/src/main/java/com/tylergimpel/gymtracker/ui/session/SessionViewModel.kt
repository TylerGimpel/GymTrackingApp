package com.tylergimpel.gymtracker.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RecommendationRepository
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.rest.RestAlarmScheduler
import com.tylergimpel.gymtracker.rest.RestNotifications
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.DistanceUnit
import com.tylergimpel.gymtracker.domain.Effort
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.LoggedSession
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.PerformedSet
import com.tylergimpel.gymtracker.domain.Prescription
import com.tylergimpel.gymtracker.domain.ProgressionModel
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RestAlarms
import com.tylergimpel.gymtracker.domain.restEnding
import com.tylergimpel.gymtracker.domain.resting
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.Session
import com.tylergimpel.gymtracker.domain.SessionPosition
import com.tylergimpel.gymtracker.domain.SessionExercise
import com.tylergimpel.gymtracker.domain.AfterLogging
import com.tylergimpel.gymtracker.domain.Avoided
import com.tylergimpel.gymtracker.domain.avoidedExercises
import com.tylergimpel.gymtracker.domain.SessionPace
import com.tylergimpel.gymtracker.domain.afterLogging
import com.tylergimpel.gymtracker.domain.SessionPlan
import com.tylergimpel.gymtracker.domain.SetDraft
import com.tylergimpel.gymtracker.domain.swapCandidatesFor
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asAmountText
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.domain.distanceOrNull
import com.tylergimpel.gymtracker.domain.durationOrNull
import com.tylergimpel.gymtracker.domain.repsOrNull
import com.tylergimpel.gymtracker.domain.timeLeftIn
import com.tylergimpel.gymtracker.domain.weightOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The effort being corrected, if one is open for editing. */
data class EditingEffort(
    val effortId: Long,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val draft: SetDraft,
    /**
     * What the exercise reads in, which is what decides whether this set records a unit at all:
     * correcting a set must not quietly turn the exercise's own setting into an override on it.
     */
    val exerciseUnit: WeightUnit,
)

/**
 * A rest the app has started, and how long it runs for.
 *
 * Held here so that the ordering the prototype pinned is observable: a rest begins only after
 * focus has advanced, and never between the members of a **Superset**. What actually reaches a
 * lifter with the phone in their pocket is the alarm the rest timer schedules (#57); this is the
 * session's own record that one is running.
 */
data class Resting(val startedAt: Instant, val length: Duration) {
    /** When it is over, which is what the alarm and the countdown are both drawn from. */
    val endsAt: Instant get() = startedAt.plus(length)
}

/** The effort a drop is being added to, if one is open. */
data class AddingDrop(val effortId: Long, val draft: SetDraft)

/**
 * The session whose date is being changed, if one is.
 *
 * Carries the id rather than the session, because the one being refiled is not always the one on
 * screen: a date is most often found to be wrong just after Finish, when the session has already
 * stopped being the active one.
 */
data class Refiling(val sessionId: Long, val date: LocalDate)

/**
 * What the lifter calls an exercise and what they wrote about it, while they are editing them.
 *
 * Both at once, because they are the same conversation: this is the machine in the corner, and
 * here is what you need to remember about it.
 */
data class ExerciseDetails(
    val exerciseId: String,
    val name: String,
    val note: String,
    /** How the lifter wants this exercise coached, including not at all. */
    val progressionModel: ProgressionModel,
    /** Whether they never want it suggested again. */
    val disliked: Boolean = false,
)

/**
 * Everything on this screen that is not read from the database.
 *
 * Held as one object rather than as a flow each, because `combine` runs out of typed overloads at
 * five and the alternative is packing values into pairs to smuggle them past it.
 */
private data class ScreenState(
    val selected: Exercise? = null,
    val draft: SetDraft = SetDraft(),
    /**
     * A rest the lifter set for the set being entered, overruling what the app asked for.
     *
     * Cleared when the exercise changes, like everything else about the set in front of them: it
     * is a statement about this set rather than about the lifter.
     */
    val restForThisSet: Duration? = null,
    /** The rest being edited, while the lifter is changing one. */
    val editingRest: Duration? = null,
    /**
     * Which rung of the wheel the controls are acting on.
     *
     * An index into the sets the wheel shows — the logged ones and then the pending one — or null
     * for the pending set, which is where focus sits whenever a lifter is doing the ordinary
     * thing. Scrolling moves it; logging leaves it where it was, on the set about to be done.
     */
    val focusedSet: Int? = null,
    /** The rest running, if one is. */
    val resting: Resting? = null,
    /**
     * Whether the drafts still hold what the app put in them rather than something typed.
     *
     * What lets the recommendation seed the fields without ever overwriting a lifter mid-entry:
     * true from the moment an exercise is selected until the first keystroke, and true again the
     * next time one is.
     */
    val untouched: Boolean = true,
    val perSide: Boolean = false,
    val overridingDisplayUnit: Boolean = false,
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
    val finishing: Boolean = false,
    val refiling: Refiling? = null,
    val skipping: RoutineDay? = null,
    val editingExercise: ExerciseDetails? = null,
    /**
     * What to train instead, while the lifter is looking for something else to do.
     *
     * Null when the sheet is closed. Empty when it is open and the app has nothing to suggest,
     * which is a real answer and one the sheet says out loud rather than looking broken.
     */
    val swapCandidates: List<Exercise>? = null,
    /** Whether the only things left to suggest are ones the lifter has banned. */
    val swappingLeavesOnlyDisliked: Boolean = false,
    /** Exercises the lifter has already been asked about, so the question is asked once. */
    val askedAbout: Set<String> = emptySet(),
)

data class SessionUiState(
    /**
     * What this lifter reads in — their **Preferred unit**, not any machine's label.
     *
     * On the state rather than fetched where it is needed, because a distance and a weight shown
     * side by side must be read in one system: kilometres with kilograms, miles with pounds.
     */
    val lifterUnit: WeightUnit = WeightUnit.DEFAULT,
    val session: Session? = null,
    val exercises: List<SessionExercise> = emptyList(),
    /**
     * The session laid out as it was performed, with superset members gathered.
     *
     * The list the screen renders. [exercises] stays as the flat record, because volume and
     * progression read the work and have no interest in how it was grouped.
     */
    val positions: List<SessionPosition> = emptyList(),
    val selected: Exercise? = null,
    /**
     * What the app is telling the lifter to do for the next set, where it has anything to say —
     * and which of its two modes it is in.
     *
     * Always about [selected] and never about the exercise before it: it is matched to the current
     * selection on the way out, so a recommendation still in flight when the lifter changes
     * exercise cannot be shown against — or recorded beside — the wrong movement.
     */
    val recommendation: Recommendation? = null,
    val draft: SetDraft = SetDraft(),
    /** Whether the entry controls are expanded to record each side separately. */
    val perSide: Boolean = false,
    /**
     * Whether the unit chips are departing for this one set rather than settling the exercise.
     *
     * Off is the common case and the one the controls are built around: a machine is labelled in
     * one unit whoever walks up to it, so the choice belongs to the exercise.
     */
    val overridingDisplayUnit: Boolean = false,
    /** The right-side draft, used only while [perSide] is set. */
    val rightDraft: SetDraft = SetDraft(),
    val editing: EditingEffort? = null,
    val addingDrop: AddingDrop? = null,
    /**
     * What the session was supposed to contain and did not — what makes "I did four of six"
     * answerable, and a prescribed exercise that went untrained visible rather than absent.
     */
    val unperformed: List<Exercise> = emptyList(),
    /** The routine and where it has got to, so the screen can say what comes next. */
    val routine: RoutineProgress? = null,
    /** Set while the screen is asking which routine day the finishing session realised. */
    val finishing: Boolean = false,
    /**
     * The most recently finished session, offered only when none is being logged.
     *
     * There so that a date noticed to be wrong after pressing Finish is still fixable. Deliberately
     * one session rather than a list: this is a way back to what was just done, not history.
     */
    val lastFinished: LoggedSession? = null,
    /** The session whose date the screen is asking about, if it is asking. */
    val refiling: Refiling? = null,
    /** The routine day the screen is asking about skipping, if it is asking. */
    val skipping: RoutineDay? = null,
    /** The name and note being edited on the selected exercise, if they are. */
    val editingExercise: ExerciseDetails? = null,
    /** What to train instead, while the lifter is looking. Null while the sheet is closed. */
    val swapCandidates: List<Exercise>? = null,
    /**
     * Whether the only candidates left are ones the lifter has banned.
     *
     * The sheet says so and asks which is least bad, rather than quietly offering a banned
     * exercise or quietly skipping the muscle. Both of those decide something only the lifter
     * can decide.
     */
    val swappingLeavesOnlyDisliked: Boolean = false,
    /**
     * An exercise the recent sessions kept asking for and the training kept not containing.
     *
     * A question rather than an action: what the app has noticed, put to the lifter with keeping
     * it exactly as available as swapping or removing it. Null once they have answered, and never
     * more than one at a time — a screen full of questions is a screen nobody reads.
     */
    val avoided: Avoided? = null,
    /** The rest being edited, while the lifter is changing one. */
    val editingRest: Duration? = null,
    /**
     * Whether this exercise is starting without a ramp because the muscle is already warm.
     *
     * Said out loud for one reason: a lifter who got three warm-ups on their first chest movement
     * and none on their third needs the second answer to read as deliberate rather than as the
     * app having forgotten. Only while they are starting the exercise — once the sets are going,
     * saying it again is noise.
     */
    val muscleAlreadyWarm: Boolean = false,
    /** Which rung of the wheel the controls are acting on; null is the set about to be done. */
    val focusedSet: Int? = null,
    /** The rest running, if one is. */
    val resting: Resting? = null,
    /**
     * Whether the app can be trusted to wake the lifter on time.
     *
     * False where this phone will not let it schedule an exact alarm, which is the default on a
     * fresh install and again after a backup restore. Surfaced in the product rather than
     * swallowed: the failure it warns about is a timer that never fires, which a lifter discovers
     * by missing a set rather than by seeing an error.
     */
    val restAlarmsAreReliable: Boolean = true,
    /** How far through the exercise, and how far through the work that counts. */
    val pace: SessionPace? = null,
    /** Roughly how much longer the session has to run, cardio included. */
    val timeLeft: Duration? = null,
    /**
     * The rungs of the wheel: the sets logged against the selected exercise, then the one about
     * to be done.
     *
     * Assembled here rather than in the screen so that what the wheel shows and what the controls
     * act on cannot disagree about how many sets there are.
     */
    val wheel: List<WheelSet> = emptyList(),
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val clock: Clock = Clock.systemDefaultZone()

    private val database = GymDatabase.getInstance(application)

    private val profile = database.profileRepository()

    private val repository = SessionRepository(
        database = database,
        sessions = database.sessionDao(),
        clock = clock,
        zone = ZoneId.systemDefault(),
        profile = profile,
    )

    private val exercises =
        ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

    private val recommendations =
        RecommendationRepository(database, database.exerciseDao(), database.sessionDao(), clock)

    /**
     * What wakes the lifter when a rest is over.
     *
     * The alarm is the source of truth and this view model is not: the process can be killed
     * between two sets, so what is handed to the platform is a moment rather than a countdown.
     */
    private val alarms: RestAlarms = RestAlarmScheduler(application)

    private val notifications = RestNotifications(application)

    private val routines = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
    )

    private val screen = MutableStateFlow(ScreenState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logged: StateFlow<LoggedSession?> = repository.observeActiveSession()
        .flatMapLatest { session ->
            if (session == null) flowOf(null) else repository.observeSession(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * What the app has to say about the selected exercise, and which exercise it is about.
     *
     * Carries the id because the answer arrives after a database read: the lifter can have moved on
     * to the next machine by then, and a recommendation shown — or recorded — against the wrong
     * exercise is worse than none at all.
     */
    private data class Recommended(val exerciseId: String, val recommendation: Recommendation)

    /**
     * Re-asked whenever the selection changes or the log does.
     *
     * The log, because finishing a session turns today's work into history and moves what comes
     * next. Logging a set within one changes nothing, by design — advancing takes two consecutive
     * sessions rather than one good set — so this re-reads and arrives at the same answer.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val recommended: Flow<Recommended?> =
        combine(screen.map { it.selected }.distinctUntilChanged(), logged) { selected, _ ->
            selected
        }.mapLatest { selected ->
            selected?.let { exercise ->
                recommendations.nextSet(exercise)?.let { Recommended(exercise.id, it) }
            }
        }

    /**
     * The session being logged and the recommendation, paired.
     *
     * Together because `combine` runs out of typed overloads at five, and the two belong together
     * anyway: they are the two halves of what this screen is for.
     */
    private data class Training(
        val logged: LoggedSession?,
        val recommended: Recommended?,
        /** The last few finished sessions, which is what "keeps not doing it" is counted over. */
        val recent: List<LoggedSession>,
    )

    val state: StateFlow<SessionUiState> =
        combine(
            combine(logged, recommended, repository.observeRecent(), ::Training),
            repository.observeLastFinished(),
            routines.observeProgress(),
            screen,
            profile.observeDisplayUnit(),
        ) { training, lastFinished, routine, screen, lifterUnit ->
            val logged = training.logged
            val recent = training.recent
            // Only where it is about the exercise in front of the lifter right now.
            val recommendation = training.recommended
                ?.takeIf { it.exerciseId == screen.selected?.id }
                ?.recommendation
                // What the lifter said about this one set has the last word over the table.
                ?.let { if (screen.restForThisSet != null) it.resting(screen.restForThisSet) else it }
            SessionUiState(
                lifterUnit = lifterUnit,
                session = logged?.session,
                unperformed = logged?.unperformed.orEmpty(),
                lastFinished = lastFinished,
                exercises = logged?.exercises.orEmpty(),
                positions = logged?.positions.orEmpty(),
                selected = screen.selected,
                recommendation = recommendation,
                // The lifter's own unit is applied on the way out rather than copied into the draft
                // when it is made, so a distance field cannot be left reading in the unit they had
                // before they changed it.
                draft = screen.draft.copy(lifterUnit = lifterUnit).seededWith(recommendation, screen),
                perSide = screen.perSide,
                overridingDisplayUnit = screen.overridingDisplayUnit,
                rightDraft = screen.rightDraft.copy(lifterUnit = lifterUnit)
                    .seededWith(recommendation, screen),
                editing = screen.editing?.let { it.copy(draft = it.draft.copy(lifterUnit = lifterUnit)) },
                addingDrop = screen.addingDrop,
                routine = routine,
                finishing = screen.finishing,
                refiling = screen.refiling,
                skipping = screen.skipping,
                editingExercise = screen.editingExercise,
                swapCandidates = screen.swapCandidates,
                swappingLeavesOnlyDisliked = screen.swappingLeavesOnlyDisliked,
                // The first one they have not answered about yet, and only one at a time.
                avoided = avoidedExercises(recent)
                    .firstOrNull { it.exercise.id !in screen.askedAbout },
                editingRest = screen.editingRest,
                muscleAlreadyWarm = screen.selected?.let { chosen ->
                    recommendation !is Recommendation.WarmingUp &&
                        logged?.setsOf(chosen).isNullOrEmpty() &&
                        logged?.exercises.orEmpty().any { performed ->
                            performed.exercise.id != chosen.id &&
                                performed.exercise.primaryMuscles.any { it in chosen.primaryMuscles }
                        }
                } ?: false,
                focusedSet = screen.focusedSet,
                resting = screen.resting,
                restAlarmsAreReliable = alarms.canWakeReliably(),
                pace = screen.selected?.let { chosen ->
                    SessionPace.of(logged?.setsOf(chosen).orEmpty(), recommendation?.setType)
                },
                wheel = screen.selected?.let { chosen ->
                    logged?.setsOf(chosen).orEmpty()
                        .mapIndexed { index, set -> WheelSet.Logged(index, set) } +
                        WheelSet.Pending(recommendation)
                }.orEmpty(),
                // What is left of what the session was for. An estimate and shown as one: nothing
                // knows how many sets an exercise gets until the generator does (#26).
                timeLeft = logged?.unperformed?.takeIf { it.isNotEmpty() }?.let(::timeLeftIn),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

    /**
     * The draft with the recommendation in it, while the lifter has typed nothing of their own.
     *
     * Seeded on the way out rather than written into the draft when it arrives, so that accepting a
     * recommendation and typing over one are the same path: what the log button records is whatever
     * the fields hold, and nothing has to remember to clear a suggestion that has been answered.
     */
    private fun SetDraft.seededWith(
        recommendation: Recommendation?,
        screen: ScreenState,
    ): SetDraft =
        if (recommendation == null || !screen.untouched) this else showing(recommendation)

    /**
     * Points the controls at an exercise.
     *
     * Per-side entry collapses on the way: it is a property of the exercise being trained, and
     * carrying it across to the next one would silently write two rows for a barbell bench press.
     */
    fun select(exercise: Exercise) = screen.update {
        it.copy(
            selected = exercise,
            // The drafts start in the unit this exercise is read in, which is the point of
            // remembering it: walking up to the pound machine needs no setting up first — and
            // recording what this exercise records, so a plank asks for seconds rather than a
            // weight nobody put on it.
            draft = SetDraft(
                loggingType = exercise.loggingType,
                displayUnit = exercise.displayUnit,
            ),
            perSide = false,
            // Nothing typed yet, so the recommendation for this exercise is free to fill the
            // fields the moment it arrives.
            untouched = true,
            // A rest set for one set belongs to that set, not to the lifter's evening.
            restForThisSet = null,
            // Departing is a property of the machine in front of the lifter, so it does not
            // follow them to the next exercise any more than per-side entry does.
            overridingDisplayUnit = false,
            rightDraft = SetDraft(
                loggingType = exercise.loggingType,
                displayUnit = exercise.displayUnit,
            ),
        )
    }

    /**
     * Takes what the left-hand controls now hold, keeping the right-hand side reading in the same
     * unit — a set is read off one machine, so its two sides cannot be in different ones.
     */
    fun changeDraft(draft: SetDraft) = screen.update {
        it.copy(
            draft = draft,
            rightDraft = it.rightDraft.readIn(draft.displayUnit),
            // The lifter has said something of their own, so the recommendation stops filling the
            // fields in: it was a suggestion, and one typed over has been answered.
            untouched = false,
        )
    }

    fun changeRightDraft(draft: SetDraft) =
        screen.update { it.copy(rightDraft = draft, untouched = false) }

    /**
     * Reads the controls in [displayUnit], and remembers it as how this exercise is read.
     *
     * Remembering is what a unit chip does, because a machine is labelled in one unit whoever walks
     * up to it and the gym's pound lat pulldown is still in pounds next week — see
     * `docs/design/logging-screen.md`. The exception is explicit: while the controls are
     * overriding, this settles nothing beyond the set being entered.
     */
    fun readIn(displayUnit: WeightUnit) {
        val exercise = screen.value.selected ?: return
        screen.update {
            it.copy(
                draft = it.draft.readIn(displayUnit),
                rightDraft = it.rightDraft.readIn(displayUnit),
            )
        }
        if (screen.value.overridingDisplayUnit) return
        viewModelScope.launch {
            exercises.setDisplayUnit(exercise.id, displayUnit)
            // Carried across rather than re-read: this view model is the only thing that writes a
            // preference, and the controls should not sit in the old unit while the write lands.
            screen.update { it.copy(selected = it.selected?.copy(displayUnit = displayUnit)) }
        }
    }

    /**
     * Lets this one set depart from the exercise's unit, or puts it back on it.
     *
     * Going back re-reads the drafts in the exercise's own unit, so "not departing" and "reads as
     * the exercise does" cannot come apart and leave a set quietly recording an override of a unit
     * it agrees with.
     */
    fun toggleDisplayUnitOverride() = screen.update {
        val exerciseUnit = it.selected?.displayUnit
        if (it.overridingDisplayUnit && exerciseUnit != null) {
            it.copy(
                overridingDisplayUnit = false,
                draft = it.draft.readIn(exerciseUnit),
                rightDraft = it.rightDraft.readIn(exerciseUnit),
            )
        } else {
            it.copy(overridingDisplayUnit = !it.overridingDisplayUnit)
        }
    }

    /** Expands the controls to record each side separately, or collapses them back to one entry. */
    fun togglePerSide() = screen.update {
        it.copy(perSide = !it.perSide, rightDraft = it.draft.copy(note = null))
    }

    fun logSet() {
        val current = state.value
        val exercise = current.selected ?: return
        viewModelScope.launch {
            // What was asked, recorded whether or not it was done: a lifter offered 60 who loads
            // 65 has overridden a recommendation, and only the offer beside the result tells that
            // apart from falling short of one.
            val asked = current.recommendation?.prescription ?: Prescription()
            val recorded = if (current.perSide) {
                repository.logPerSideSet(
                    exercise.id,
                    current.draft,
                    current.rightDraft,
                    exercise.displayUnit,
                    asked,
                )
            } else {
                repository.logSet(exercise.id, current.draft, exercise.displayUnit, asked)
            }
            if (!recorded) return@launch
            // Keep the numbers: the next set is usually the same load, and retyping them every time
            // is the single most repeated action in a session.
            screen.update {
                it.copy(
                    draft = it.draft.copy(note = null),
                    rightDraft = it.rightDraft.copy(note = null),
                    // Focus stays on the set about to be done, which is now the next one. This is
                    // what makes the common path a single tap with no scrolling.
                    focusedSet = null,
                    // The set it was for is down, so it has said what it had to say. Leaving it
                    // standing would quietly make "just this set" mean "this exercise, tonight".
                    restForThisSet = null,
                )
            }
            advanceAfterLogging(exercise, asked.rest)
        }
    }

    /**
     * Where the session goes once a set is down, in the order the prototype pinned.
     *
     * The ordering is easy to get wrong and expensive to get wrong: a **Superset** jump takes
     * precedence over resting, because the point of alternating is not stopping, and a rest that
     * started between two members would have the lifter standing still in the middle of the one
     * thing that is supposed to keep moving. Rest begins only after focus has gone somewhere.
     */
    private fun advanceAfterLogging(exercise: Exercise, rest: Duration?) {
        when (val next = afterLogging(exercise, state.value.positions, rest)) {
            AfterLogging.PromptToFinish -> startFinishing()
            is AfterLogging.JumpTo -> {
                // Focus first and rest second, which is the ordering this whole type exists for.
                // The rest is only ever present on the jump that closes the group's round.
                select(next.exercise)
                next.rest?.let(::startResting)
            }
            is AfterLogging.StartResting -> startResting(next.length)

            AfterLogging.Nothing -> Unit
        }
    }

    /**
     * Starts a rest: an alarm to wake the lifter, and a countdown they can read without opening
     * anything.
     */
    private fun startResting(length: Duration) {
        val resting = Resting(clock.instant(), length)
        screen.update { it.copy(resting = resting) }
        alarms.schedule(restEnding(resting.startedAt, length))
        notifications.countDownTo(resting.endsAt)
    }

    /**
     * Adds a minute to the rest that is running.
     *
     * The end moves rather than the rest restarting: a lifter tapping this is saying the set is
     * not ready yet, not that the last two minutes did not happen.
     */
    fun addTimeToRest() {
        val resting = screen.value.resting ?: return
        val longer = resting.copy(length = resting.length.plus(MORE_REST))
        screen.update { it.copy(resting = longer) }
        alarms.schedule(restEnding(longer.startedAt, longer.length))
        notifications.countDownTo(longer.endsAt)
    }

    /** Points the wheel at one of its rungs. Null is the set about to be done. */
    fun focusSet(index: Int?) = screen.update { it.copy(focusedSet = index) }

    /**
     * Ends a rest, whether it ran out or the lifter had somewhere to be.
     *
     * Takes the alarm with it. Otherwise the lifter is three sets further on when the phone goes
     * off in their pocket.
     */
    fun endRest() {
        alarms.cancel()
        screen.update { it.copy(resting = null) }
    }

    fun startEditing(performed: SessionExercise, set: PerformedSet, effort: Effort) = screen.update {
        val displayUnit = performed.displayUnitFor(set)
        val lifterUnit = state.value.lifterUnit
        it.copy(
            editing = EditingEffort(
                effortId = effort.id,
                sessionExerciseId = performed.id,
                setIndex = set.setIndex,
                draft = SetDraft(
                    // What the row recorded rather than what its exercise records today: an
                    // exercise retagged since it was logged would otherwise open its old sets with
                    // every field blank and no way to correct them in place.
                    loggingType = effort.performance.correctedAs(performed.exercise.loggingType),
                    // Seeded in the unit the set is shown in, so opening one for correction does
                    // not change the number the lifter was reading a moment ago.
                    weightText = effort.performance.weightOrNull?.asTextIn(displayUnit).orEmpty(),
                    repsText = effort.performance.repsOrNull?.toString().orEmpty(),
                    durationText = effort.performance.durationOrNull?.seconds?.toString().orEmpty(),
                    addedWeightText = effort.performance.beltOrAssistanceText(displayUnit),
                    distanceText = effort.performance.distanceOrNull
                        ?.asTextIn(DistanceUnit.alongside(lifterUnit)).orEmpty(),
                    lifterUnit = lifterUnit,
                    note = effort.note,
                    rpeText = set.measurements.rpe?.asAmountText().orEmpty(),
                    heartRateText = set.measurements.heartRate?.toString().orEmpty(),
                    restTakenText = set.measurements.restTaken?.seconds?.toString().orEmpty(),
                    type = set.type,
                    displayUnit = displayUnit,
                ),
                exerciseUnit = performed.exercise.displayUnit,
            ),
        )
    }

    fun changeEditingDraft(draft: SetDraft) =
        screen.update { it.copy(editing = it.editing?.copy(draft = draft)) }

    fun cancelEditing() = screen.update { it.copy(editing = null) }

    fun saveEditing() {
        val open = state.value.editing ?: return
        viewModelScope.launch {
            repository.editSet(open.effortId, open.draft, open.exerciseUnit)
            screen.update { it.copy(editing = null) }
        }
    }

    /** Deletes the whole set, both rows if it has two: half a per-side set is not a set. */
    fun deleteEditing() {
        val open = screen.value.editing ?: return
        viewModelScope.launch {
            repository.deleteSet(open.sessionExerciseId, open.setIndex)
            screen.update { it.copy(editing = null) }
        }
    }

    /** [displayUnit] is what the set being continued reads in: a drop is part of it, not apart. */
    fun startAddingDrop(displayUnit: WeightUnit, effort: Effort) = screen.update {
        it.copy(addingDrop = AddingDrop(effort.id, SetDraft(displayUnit = displayUnit)))
    }

    fun changeDropDraft(draft: SetDraft) =
        screen.update { it.copy(addingDrop = it.addingDrop?.copy(draft = draft)) }

    fun cancelAddingDrop() = screen.update { it.copy(addingDrop = null) }

    fun saveDrop() {
        val open = state.value.addingDrop ?: return
        val weight = open.draft.weight ?: return
        val reps = open.draft.reps ?: return
        viewModelScope.launch {
            repository.addDrop(open.effortId, weight, reps)
            screen.update { it.copy(addingDrop = null) }
        }
    }

    /** Puts the exercise after [sessionExerciseId] into a superset with it. */
    fun groupWithNext(sessionExerciseId: Long) {
        viewModelScope.launch { repository.groupWithNext(sessionExerciseId) }
    }

    /** Breaks a group up. The sets logged against its members are untouched. */
    fun ungroup(supersetId: Long) {
        viewModelScope.launch { repository.ungroup(supersetId) }
    }

    /** Whether this group moves to its next member on its own after a set is logged. */
    fun setAutoJump(supersetId: Long, autoJump: Boolean) {
        viewModelScope.launch { repository.setAutoJump(supersetId, autoJump) }
    }

    fun noteSession(text: String) {
        val session = logged.value?.session ?: return
        viewModelScope.launch { repository.noteSession(session.id, text) }
    }

    /**
     * Opens a session for the day the cursor points at, copying what that day holds onto it.
     *
     * The only moment a plan is taken. A session started by simply logging a set has none, which is
     * what training with no routine looks like and is not a lesser session for it.
     */
    fun startCursorDay() {
        val day = state.value.routine?.cursor ?: return
        viewModelScope.launch { repository.startSession(SessionPlan(day.exercises)) }
    }

    /** Opens what the lifter calls the selected exercise, and the note they keep on it. */
    fun startEditingExercise() {
        val exercise = screen.value.selected ?: return
        screen.update {
            it.copy(
                editingExercise = ExerciseDetails(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    note = exercise.note.orEmpty(),
                    progressionModel = exercise.progressionModel,
                    disliked = exercise.isDisliked,
                ),
            )
        }
    }

    fun changeExerciseDetails(details: ExerciseDetails) =
        screen.update { it.copy(editingExercise = details) }

    fun cancelEditingExercise() = screen.update { it.copy(editingExercise = null) }

    /**
     * Records the lifter's name for the exercise and their note on it.
     *
     * A name matching what the bank already calls it is stored as no name at all, so that the row
     * says "renamed" only when it was — and a later update correcting the shipped name is then free
     * to take effect rather than being shadowed by a copy of the old one.
     */
    fun saveExerciseDetails() {
        val open = screen.value.editingExercise ?: return
        // The exercise the dialog was opened on, which is not necessarily the one selected now.
        val exercise = screen.value.selected?.takeIf { it.id == open.exerciseId } ?: return
        val renamed = open.name.trim().takeIf { it.isNotBlank() && it != exercise.bankName }
        viewModelScope.launch {
            exercises.rename(open.exerciseId, renamed)
            exercises.note(open.exerciseId, open.note)
            // Recorded as a choice only where it departs from what the bank derived, the same move
            // a display unit makes: agreeing with the derivation is not a decision, and a row that
            // stored the agreement would be stranded there the day the derivation improved.
            exercises.setProgressionModel(
                open.exerciseId,
                open.progressionModel.takeIf { it != exercise.bankProgressionModel },
            )
            exercises.setDisliked(open.exerciseId, open.disliked)
            screen.update {
                it.copy(
                    editingExercise = null,
                    // Carried across rather than re-read, for the reason the unit is: this view
                    // model is the only thing that writes these, and the controls should not sit
                    // showing the old name while the write lands.
                    selected = it.selected?.copy(
                        userName = renamed,
                        isDisliked = open.disliked,
                        note = open.note.takeIf(String::isNotBlank),
                        chosenProgressionModel = open.progressionModel
                            .takeIf { model -> model != exercise.bankProgressionModel },
                    ),
                )
            }
        }
    }

    /**
     * Opens the question of training something else, because the machine is busy.
     *
     * A **Swap** is for this session only and nothing about it is written down: the session's
     * plan still records what the evening was for, so the exercise that got skipped reads as
     * **Unperformed** rather than absent. Making a substitution permanent is a **Revision**,
     * which belongs to the routine rather than here.
     */
    fun startSwapping() {
        val replacing = screen.value.selected ?: return
        viewModelScope.launch {
            val bank = exercises.observeBank().first()
            val equipment = state.value.routine?.routine?.equipment.orEmpty()
            val trained = database.sessionDao().exercisesEverTrained().toSet()

            val candidates = swapCandidatesFor(replacing, bank, equipment, trained)
            // When the bans leave no way to train the muscle, the app **asks which is least bad**
            // rather than silently including one — which would break the promise a dislike makes
            // — or silently skipping the muscle, which would quietly leave a hole in the
            // training. The decision is the lifter's; only they can make it.
            val leastBad = if (candidates.isEmpty()) {
                swapCandidatesFor(replacing, bank, equipment, trained, includeDisliked = true)
            } else {
                emptyList()
            }

            screen.update {
                it.copy(
                    swapCandidates = candidates.ifEmpty { leastBad },
                    swappingLeavesOnlyDisliked = leastBad.isNotEmpty(),
                )
            }
        }
    }

    /** Never wants to see [exercise] suggested again, or wants it back. */
    fun setDisliked(exercise: Exercise, disliked: Boolean) {
        viewModelScope.launch {
            exercises.setDisliked(exercise.id, disliked)
            screen.update {
                it.copy(selected = it.selected?.takeIf { chosen -> chosen.id == exercise.id }
                    ?.copy(isDisliked = disliked) ?: it.selected)
            }
        }
    }

    /**
     * Keeps an exercise the lifter has been avoiding, and stops asking about it.
     *
     * As available as swapping or removing it, and deliberately so: people skip squats for being
     * hard rather than for being disliked, and an app that made avoidance mean dislike would
     * quietly strip the most valuable work out of a programme.
     */
    fun keepAvoided(avoided: Avoided) =
        screen.update { it.copy(askedAbout = it.askedAbout + avoided.exercise.id) }

    /** Takes an exercise out of the routine day it keeps going untrained in. */
    fun removeAvoided(avoided: Avoided) {
        val day = state.value.routine?.routine?.rotation
            ?.firstOrNull { day -> day.exercises.any { it.id == avoided.exercise.id } }
            ?: return keepAvoided(avoided)

        viewModelScope.launch {
            routines.removeExerciseFromDay(day.id, avoided.exercise.id)
            keepAvoided(avoided)
        }
    }

    /** Offers something else instead of the one they keep not doing. */
    fun swapAvoided(avoided: Avoided) {
        keepAvoided(avoided)
        select(avoided.exercise)
        startSwapping()
    }

    fun cancelSwapping() =
        screen.update { it.copy(swapCandidates = null, swappingLeavesOnlyDisliked = false) }

    /**
     * Trains [instead] for the rest of this session.
     *
     * Nothing is stored and nothing is inherited: an exercise swapped in with no history
     * calibrates like any other first session, because the app never extrapolates across
     * exercises and a busy rack is not evidence about anything (ADR 0004).
     */
    fun swapTo(instead: Exercise) {
        select(instead)
        screen.update { it.copy(swapCandidates = null, swappingLeavesOnlyDisliked = false) }
    }

    /** Opens what the app is asking the lifter to rest for, so they can disagree with it. */
    fun startEditingRest() = screen.update {
        it.copy(editingRest = state.value.recommendation?.prescription?.rest ?: Duration.ZERO)
    }

    fun changeEditingRest(rest: Duration) = screen.update { it.copy(editingRest = rest) }

    fun cancelEditingRest() = screen.update { it.copy(editingRest = null) }

    /** Rests for this long after the set being entered, and says nothing about the next one. */
    fun restThisSetOnly() =
        screen.update { it.copy(restForThisSet = it.editingRest, editingRest = null) }

    /**
     * Rests for this long on this exercise from now on.
     *
     * Recorded against the exercise rather than the set, because "I need longer on this machine"
     * is a standing fact about the machine and the lifter rather than about tonight.
     */
    fun restFromNowOn() {
        val exercise = screen.value.selected ?: return
        val rest = screen.value.editingRest ?: return
        viewModelScope.launch {
            exercises.setRest(exercise.id, rest)
            screen.update {
                it.copy(
                    editingRest = null,
                    restForThisSet = null,
                    // Carried across rather than re-read, as the unit and the name are.
                    selected = it.selected?.copy(chosenRest = rest),
                )
            }
        }
    }

    /** Opens the question of skipping the day the cursor points at. */
    fun startSkipping() {
        val day = state.value.routine?.cursor ?: return
        screen.update { it.copy(skipping = day) }
    }

    fun cancelSkipping() = screen.update { it.copy(skipping = null) }

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
    fun skipDay() {
        val progress = state.value.routine ?: return
        val day = screen.value.skipping ?: return
        viewModelScope.launch {
            routines.recordSkip(progress.routine.id, day)
            screen.update { it.copy(skipping = null) }
        }
    }

    fun startRefiling(session: Session) =
        screen.update { it.copy(refiling = Refiling(session.id, session.date)) }

    fun cancelRefiling() = screen.update { it.copy(refiling = null) }

    /**
     * Refiles the session onto [date] — the fix for training on Saturday and remembering on Monday.
     *
     * When it was trained is untouched, and so is the cursor: a session filed on a day other than
     * the one it was trained on records no completion when it finishes.
     */
    fun refileOn(date: LocalDate) {
        val open = screen.value.refiling ?: return
        viewModelScope.launch {
            repository.refile(open.sessionId, date)
            screen.update { it.copy(refiling = null) }
        }
    }

    /** Opens the question of which routine day this session realised, or just finishes it. */
    fun startFinishing() {
        if (state.value.routine?.routine?.days.orEmpty().isEmpty()) {
            finishSession(null)
        } else {
            screen.update { it.copy(finishing = true) }
        }
    }

    fun cancelFinishing() = screen.update { it.copy(finishing = false) }

    /**
     * Closes the session, and records [day] as trained if one was named.
     *
     * Both writes go in one transaction: a finished session that failed to advance the cycle would
     * leave the cursor pointing at work already done, with nothing to show why.
     */
    fun finishSession(day: RoutineDay?) {
        val session = logged.value?.session ?: return
        val routine = state.value.routine?.routine
        viewModelScope.launch {
            database.withTransaction {
                repository.finishSession(session.id, routine.takeIf { day != null }, day)
                if (routine != null && day != null) {
                    routines.recordCompletion(routine.id, day, session.id)
                }
            }
            screen.update {
                it.copy(
                    finishing = false,
                    selected = null,
                    draft = SetDraft(),
                    perSide = false,
                    overridingDisplayUnit = false,
                )
            }
        }
    }

    private fun Performance.beltOrAssistanceText(displayUnit: WeightUnit): String = when (this) {
        // Both are typed as a plain magnitude: the field says whether it is help or load, and the
        // sign belongs to storage rather than to anything a lifter types.
        is Performance.BodyweightReps -> addedWeight?.asTextIn(displayUnit).orEmpty()
        is Performance.Assisted -> assistance.asTextIn(displayUnit)
        is Performance.WeightAndReps, is Performance.Timed, is Performance.DistanceAndTime -> ""
    }

    /**
     * The logging type whose fields can restate this performance.
     *
     * [recorded] is preferred where it already matches, so that correcting a pull-up stays a
     * pull-up rather than becoming a plain weight-and-reps set.
     */
    private fun Performance.correctedAs(recorded: LoggingType): LoggingType = when (this) {
        is Performance.WeightAndReps ->
            recorded.takeIf { it.recordsWeight && it.recordsReps } ?: LoggingType.WEIGHT_REPS

        is Performance.BodyweightReps -> LoggingType.BODYWEIGHT_REPS
        is Performance.Assisted -> LoggingType.ASSISTED
        is Performance.Timed -> LoggingType.TIME
        is Performance.DistanceAndTime -> LoggingType.DISTANCE_TIME
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** What "+1:00" adds. A minute is the unit a lifter thinks in between sets. */
        val MORE_REST: Duration = Duration.ofMinutes(1)
    }
}
