package com.tylergimpel.gymtracker.ui.session

import com.tylergimpel.gymtracker.domain.MuscleLastTrained
import com.tylergimpel.gymtracker.domain.PersonalRecord
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
import com.tylergimpel.gymtracker.domain.PlateLine
import com.tylergimpel.gymtracker.domain.PlannedSets
import com.tylergimpel.gymtracker.domain.Recommendation
import com.tylergimpel.gymtracker.domain.RepRange
import com.tylergimpel.gymtracker.domain.RestAlarms
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


/** What a typed **Rep range** means for the one that is stored. */
sealed interface RepRangeEdit {
    /** Both fields empty: hand the question back to the routine's goal. */
    data object Clear : RepRangeEdit

    /** A usable band, which becomes the lifter's own. */
    data class Set(val range: RepRange) : RepRangeEdit

    /** Half a pair or a top under its bottom. Leave what is stored exactly as it is. */
    data object Unusable : RepRangeEdit
}

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
    /**
     * The step this exercise moves by, as typed, in the unit the lifter reads.
     *
     * Text rather than a number because it is a field: empty means "the equipment decides",
     * which is the ordinary answer and the one that puts the plate inventory or the logged stack
     * positions back in charge.
     */
    val increment: String = "",
    /**
     * The band the lifter works this exercise in, as typed: the bottom and the top.
     *
     * Two fields rather than one, because the two ends do different jobs — the bottom is what the
     * load formula reads and the top is what has to be beaten. Both blank means the routine's goal
     * answers, which is the ordinary state.
     */
    val repRangeBottom: String = "",
    val repRangeTop: String = "",
    /**
     * The unit this exercise is read in: the same setting the logging screen's unit chip settles.
     *
     * Here as well as there because the chip is deliberately undecorated and taught once, by a tip
     * the lifter can dismiss unread. **A tip that is the only route to a feature is a broken
     * feature** (#29), so this is the route that does not depend on it.
     */
    val displayUnit: WeightUnit = WeightUnit.KILOGRAMS,
) {

    /**
     * What the typed band should do to what is stored.
     *
     * Three answers, not two, because **an unusable pair is not the same as an empty one**. Both
     * fields blank is a lifter handing the question back to their goal, and it clears. A top
     * below its bottom, or half a pair, is somebody mid-edit or mistyping — and writing that
     * through as "clear it" would silently delete a band they had already chosen, which is the
     * opposite of what a save is for.
     */
    fun repRangeEdit(): RepRangeEdit = when {
        repRangeBottom.isBlank() && repRangeTop.isBlank() -> RepRangeEdit.Clear
        else -> {
            val bottom = repRangeBottom.trim().toIntOrNull()
            val top = repRangeTop.trim().toIntOrNull()
            val usable = bottom != null && top != null &&
                bottom >= 1 && top >= bottom && top <= RepRange.MAX_PRESCRIBED_REPS
            if (usable) RepRangeEdit.Set(RepRange(bottom, top)) else RepRangeEdit.Unusable
        }
    }
}

/**
 * What no database supplies: what the screen holds, the rest timer, and where the cycle is.
 *
 * Held as one object rather than as a flow each, because `combine` runs out of typed overloads at
 * five and the alternative is packing values into pairs to smuggle them past it.
 */
private data class OnScreen(
    val screen: ScreenState,
    val rest: RestState,
    val cycle: CycleState,
)

private data class ScreenState(
    val selected: Exercise? = null,
    val draft: SetDraft = SetDraft(),
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
    /**
     * The whole bank, while the picker is open.
     *
     * Held only then, because it is 893 rows and nothing else on this screen wants it. The picker
     * searches and filters it in place rather than asking the database again on every keystroke.
     */
    val pickerBank: List<Exercise> = emptyList(),
    /** Exercises the lifter has already been asked about, so the question is asked once. */
    val askedAbout: Set<String> = emptySet(),
    /** A record the set just logged set, until it has been shown (#124). */
    val newRecord: PersonalRecord? = null,
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
     * The whole bank, while the picker is open.
     *
     * Held only then, because it is 893 rows and nothing else on this screen wants it. The picker
     * searches and filters it in place rather than asking the database again on every keystroke.
     */
    val pickerBank: List<Exercise> = emptyList(),
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
    /** Whether to ask, now, to show the rest countdown: true once, on the first rest that needs it. */
    val askToShowRestCountdown: Boolean = false,
    /**
     * Whether to point out the unit and the routine drawer, which the screen leaves undecorated.
     *
     * Only with an exercise on screen, since that is where both are, and only until dismissed.
     */
    val showCoachMark: Boolean = false,
    /**
     * Whether the countdown will actually appear.
     *
     * False when notifications are off or were never granted, which on Android 13 and later is
     * the default. The same silent failure as a denied alarm and surfaced the same way: with the
     * phone in a pocket, the notification *is* the rest timer.
     */
    val restCountdownVisible: Boolean = true,
    /**
     * How to load the bar for what is being asked, where the exercise is a barbell.
     *
     * Always visible and never interactive: a view onto rounding the engine has already done,
     * reading the inventory it rounded against, so the line and the number can never disagree.
     */
    val plateLine: PlateLine? = null,
    /** A record the set just logged set, to be announced once (#124). */
    val newRecord: PersonalRecord? = null,
    /** How long since each muscle was trained, longest ago first (#127). */
    val lastTrained: List<MuscleLastTrained> = emptyList(),
    /**
     * Whether to offer to find the selected lift's estimate again, because the lifter has set
     * their own weight on it in every session for a month (#128). A prompt, never a statistic.
     */
    val offerResetEstimate: Boolean = false,
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

    /**
     * The rest timer, which is its own thing and delegated to rather than reimplemented here.
     *
     * It shares nothing with entering a set but the exercise it is about, so it keeps its own
     * state and this view model passes the screen's taps along (#63).
     */
    private val restTimer = SessionRest(alarms, notifications, clock)

    /**
     * Where this session sits in the **Cycle**: starting a day, skipping one, refiling, ending.
     *
     * Delegated to for the same reason the rest timer is — none of it is about the set in front
     * of the lifter, and holding it here was most of what made this class unreadable (#63).
     */
    private val cycle = SessionCycle(viewModelScope, database, repository, routines)

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
    private data class Recommended(
        val exerciseId: String,
        val recommendation: Recommendation,
        /** How to load the bar for it, where it is a bar. Computed with the weight it explains. */
        val plateLine: PlateLine? = null,
        /** How many sets this session asked for of it, where it asked for any. */
        val planned: PlannedSets? = null,
        /** Whether to offer to find its estimate again (#128). */
        val offerReset: Boolean = false,
    )

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
                recommendations.nextSetWithPlan(exercise)?.let { next ->
                    Recommended(
                        exerciseId = exercise.id,
                        recommendation = next.recommendation,
                        plateLine = next.recommendation.prescription.load
                            ?.let { recommendations.plateLine(exercise, it) },
                        planned = next.planned,
                        offerReset = next.offerReset,
                    )
                }
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
        /** How long since each muscle was trained (#127). */
        val lastTrained: List<MuscleLastTrained>,
    )

    val state: StateFlow<SessionUiState> =
        combine(
            combine(logged, recommended, repository.observeRecent(), repository.observeLastTrained(), ::Training),
            repository.observeLastFinished(),
            routines.observeProgress(),
            // Paired rather than passed separately: `combine` runs out of typed overloads at
            // five, and what these two have in common is only that neither comes from the
            // database.
            combine(screen, restTimer.state, cycle.state, ::OnScreen),
            profile.observeDisplayUnit(),
        ) { training, lastFinished, routine, onScreen, lifterUnit ->
            val screen = onScreen.screen
            val rest = onScreen.rest
            val cycle = onScreen.cycle
            val logged = training.logged
            val recent = training.recent
            // Only where it is about the exercise in front of the lifter right now.
            val forThisExercise = training.recommended
                ?.takeIf { it.exerciseId == screen.selected?.id }
            val recommendation = forThisExercise
                ?.recommendation
                // What the lifter said about this one set has the last word over the table.
                ?.let { if (rest.forThisSet != null) it.resting(rest.forThisSet) else it }
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
                finishing = cycle.finishing,
                refiling = cycle.refiling,
                skipping = cycle.skipping,
                editingExercise = screen.editingExercise,
                swapCandidates = screen.swapCandidates,
                pickerBank = screen.pickerBank,
                swappingLeavesOnlyDisliked = screen.swappingLeavesOnlyDisliked,
                // The first one they have not answered about yet, and only one at a time.
                avoided = avoidedExercises(recent)
                    .firstOrNull { it.exercise.id !in screen.askedAbout },
                editingRest = rest.editing,
                resting = rest.running,
                restAlarmsAreReliable = rest.alerts.alarmsAreReliable,
                restCountdownVisible = rest.alerts.countdownVisible,
                askToShowRestCountdown = rest.askToShowCountdown,
                plateLine = forThisExercise?.plateLine,
                lastTrained = training.lastTrained,
                newRecord = screen.newRecord,
                offerResetEstimate = forThisExercise?.offerReset == true,
                pace = screen.selected?.let { chosen ->
                    SessionPace.of(
                        sets = logged?.setsOf(chosen).orEmpty(),
                        pending = recommendation?.setType,
                        planned = forThisExercise?.planned,
                    )
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
    fun select(exercise: Exercise) {
        // A rest the lifter set for one set belongs to that set, not to their evening, so it
        // does not follow them to the next exercise.
        restTimer.forgetThisSet()
        selectOnScreen(exercise)
    }

    private fun selectOnScreen(exercise: Exercise) = screen.update {
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
            // Asked before the set is down, so the standing record is the one it had to beat. A
            // per-side set is two efforts and not one number, so it announces nothing.
            val record = if (current.perSide) null else repository.recordSetBy(exercise.id, current.draft)
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
                    newRecord = record,
                )
            }
            // The set it was for is down, so it has said what it had to say. Leaving it
            // standing would quietly make "just this set" mean "this exercise, tonight".
            restTimer.forgetThisSet()
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
                next.rest?.let(restTimer::start)
            }
            is AfterLogging.StartResting -> restTimer.start(next.length)

            AfterLogging.Nothing -> Unit
        }
    }

    /** Finds the selected lift's estimate again from today (#128). */
    fun resetEstimate() {
        val exercise = state.value.selected ?: return
        val today = LocalDate.now(clock)
        viewModelScope.launch {
            exercises.resetEstimate(exercise.id, today)
            // A changed exercise is what asks the engine again, as every other choice here does.
            screen.update { it.copy(selected = it.selected?.copy(estimateResetOn = today)) }
        }
    }

    /** Not now: the offer waits until the whole pattern has happened again. */
    fun keepEstimate() {
        val exercise = state.value.selected ?: return
        val today = LocalDate.now(clock)
        viewModelScope.launch {
            exercises.keepEstimate(exercise.id, today)
            screen.update { it.copy(selected = it.selected?.copy(resetOfferDismissedOn = today)) }
        }
    }

    /** The record has been shown, and is not shown again. */
    fun recordSeen() = screen.update { it.copy(newRecord = null) }

    fun recheckRestAlerts() = restTimer.recheckAlerts()

    /**
     * Whether the first-session tip has been dismissed.
     *
     * Starts as dismissed, so the tip never flashes up for somebody who dismissed it long ago in the
     * moment before the stored answer has been read.
     */
    val coachMarkSeen: StateFlow<Boolean> = profile.observeCoachMarkSeen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    /** Dismisses the first-session tip, for good. */
    fun dismissCoachMark() {
        viewModelScope.launch { profile.dismissCoachMark() }
    }

    /** The screen has asked to show the rest countdown, whatever the lifter answered. */
    fun askedToShowRestCountdown() = restTimer.asked()

    fun addTimeToRest() = restTimer.addTime()

    fun endRest() = restTimer.end()

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
    fun startCursorDay() = cycle.startDay(state.value.routine)

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
                    increment = exercise.chosenIncrement
                        ?.asTextIn(state.value.lifterUnit)
                        .orEmpty(),
                    repRangeBottom = exercise.chosenRepRange?.bottom?.toString().orEmpty(),
                    repRangeTop = exercise.chosenRepRange?.top?.toString().orEmpty(),
                    displayUnit = exercise.displayUnit,
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
            // Blank puts the equipment back in charge, which is what clearing a field means.
            exercises.setIncrement(
                open.exerciseId,
                open.increment.trim().toDoubleOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { state.value.lifterUnit.toKilograms(it) },
            )
            // The same write the unit chip makes, so the two can never disagree about what this
            // exercise is read in. Made only when it changed: saving the dialog for a note is not
            // a statement about units.
            val unitChanged = open.displayUnit != exercise.displayUnit
            if (unitChanged) exercises.setDisplayUnit(open.exerciseId, open.displayUnit)
            // Both ends or neither, and a mistyped pair leaves what was there alone.
            when (val edit = open.repRangeEdit()) {
                RepRangeEdit.Clear -> exercises.setRepRange(open.exerciseId, null)
                is RepRangeEdit.Set -> exercises.setRepRange(open.exerciseId, edit.range)
                RepRangeEdit.Unusable -> Unit
            }
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
                        chosenRepRange = when (val edit = open.repRangeEdit()) {
                            RepRangeEdit.Clear -> null
                            is RepRangeEdit.Set -> edit.range
                            RepRangeEdit.Unusable -> it.selected?.chosenRepRange
                        },
                        displayUnit = open.displayUnit,
                    ),
                    // The controls re-read in the new unit, as they do when the chip is tapped —
                    // unless this one set is overriding it, which a setting cannot reach.
                    draft = if (unitChanged && !it.overridingDisplayUnit) {
                        it.draft.readIn(open.displayUnit)
                    } else {
                        it.draft
                    },
                    rightDraft = if (unitChanged && !it.overridingDisplayUnit) {
                        it.rightDraft.readIn(open.displayUnit)
                    } else {
                        it.rightDraft
                    },
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
                    pickerBank = bank,
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
            ?.firstOrNull { day -> day.exercises.any { it.exercise.id == avoided.exercise.id } }
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
    fun startEditingRest() = restTimer.startEditing(state.value.recommendation?.prescription?.rest)

    fun changeEditingRest(rest: Duration) = restTimer.changeEditing(rest)

    fun cancelEditingRest() = restTimer.cancelEditing()

    /** Rests for this long after the set being entered, and says nothing about the next one. */
    fun restThisSetOnly() = restTimer.thisSetOnly()

    /**
     * Rests for this long on this exercise from now on.
     *
     * Recorded against the exercise rather than the set, because "I need longer on this machine"
     * is a standing fact about the machine and the lifter rather than about tonight.
     */
    fun restFromNowOn() {
        val exercise = screen.value.selected ?: return
        val rest = restTimer.editedLength() ?: return
        viewModelScope.launch {
            exercises.setRest(exercise.id, rest)
            restTimer.settledOnTheExercise()
            // Carried across rather than re-read, as the unit and the name are.
            screen.update { it.copy(selected = it.selected?.copy(chosenRest = rest)) }
        }
    }

    fun startSkipping() = cycle.startSkipping(state.value.routine)

    fun cancelSkipping() = cycle.cancelSkipping()

    fun skipDay() = cycle.skipDay(state.value.routine)

    fun startRefiling(session: Session) = cycle.startRefiling(session)

    fun cancelRefiling() = cycle.cancelRefiling()

    fun refileOn(date: LocalDate) = cycle.refileOn(date)

    fun startFinishing() = cycle.startFinishing(state.value.routine) { finishSession(null) }

    fun cancelFinishing() = cycle.cancelFinishing()

    /**
     * Closes the session, and records [day] as trained if one was named.
     *
     * What is left here is what a session ending does to the set being entered: the exercise is
     * put away and the fields go back to empty, which is the screen's business rather than the
     * cycle's.
     */
    fun finishSession(day: RoutineDay?) {
        val session = logged.value?.session ?: return
        cycle.finish(session.id, state.value.routine, day) {
            screen.update {
                it.copy(
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

    }
}
