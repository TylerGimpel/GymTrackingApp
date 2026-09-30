package com.tylergimpel.gymtracker.ui.routine

import com.tylergimpel.gymtracker.domain.WeightUnit
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.RoutineProgress
import com.tylergimpel.gymtracker.data.RoutineRepository
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.SplitShape
import com.tylergimpel.gymtracker.domain.CardioPlacement
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import kotlinx.coroutines.flow.SharingStarted
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.domain.rebuildFrom
import com.tylergimpel.gymtracker.domain.RecoveredRoutine
import com.tylergimpel.gymtracker.data.SessionRepository
import com.tylergimpel.gymtracker.data.profileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/** The day being filled, and everything the picker needs to offer something for it. */
data class PickingFor(
    val dayId: Long,
    val dayName: String,
    val bank: List<Exercise>,
)

/**
 * What setup asked the lifter, travelling together because a routine is created from all of it.
 *
 * One type rather than five parameters because every path that makes a routine needs the whole
 * answer: the cadence is what volume translation needs, the cardio placement is what decided
 * which shapes were offered, and the goal and the rep preference are what the days are filled
 * against. A path that took some of them would be building against a default for the rest —
 * which is exactly how the first routine the app ever built for somebody came to be built for
 * hypertrophy whatever they were training for.
 */
data class RoutineSetup(
    val cadence: Int,
    val cardio: CardioPlacement,
    val equipment: Set<String>,
    /**
     * What the lifter said they are training for, or what the app assumes if they said nothing.
     *
     * Resolved before it gets here: skipping the question is a real answer, and the screen is
     * where what it will assume is said out loud.
     */
    val goal: RoutineGoal,
    val repPreference: RepPreference,
) {
    companion object {
        /** What a lifter who skips the goal question is assumed to be training for. */
        val ASSUMED_GOAL = RoutineGoal.HYPERTROPHY

        /** What a lifter who skips the rep question is assumed to prefer. */
        val ASSUMED_REP_PREFERENCE = RepPreference.MODERATE
    }
}

/**
 * The past routines the log holds, and what setup said, while the lifter picks one to bring back.
 *
 * The setup waits here with them because it was answered before the question was asked, and the
 * routine that comes back is built against it whichever one they pick.
 */
data class RebuildChoice(
    /** Most recently trained first, which is the one most likely to be meant. */
    val routines: List<RecoveredRoutine>,
    val setup: RoutineSetup,
    /**
     * How many sessions were read, where the read stopped at its limit rather than at the start
     * of the log; null where it reached the start.
     *
     * Said to the lifter where it is true, because the oldest routine offered may then be missing
     * days that were trained before the window begins — and a list that is silently short is
     * worse than one that says where it stops.
     */
    val limit: Int?,
)

/**
 * A change to the rotation that ended the cycle, so the screen can say so where it happened.
 *
 * Only the three that are a **Revision** (ADR 0006). Renaming a day is not one, so it has no
 * entry here and nothing is said about it — which is what makes the difference learnable.
 */
enum class Revised { ADDED, REMOVED, MOVED }

/** A [Revised], and the cycle it started — which is how long saying so stays true. */
data class Revision(val kind: Revised, val cycle: Int?)

/** Why the app is offering to build the routine again. */
enum class RebuildOffer {
    /** The goal, the cadence or the rep preference — what the routine is *for*. */
    WHAT_IT_IS_FOR,

    /** What the gym has, which changes when somebody joins a new one and nothing else. */
    EQUIPMENT,
}

data class RoutineUiState(
    val progress: RoutineProgress? = null,
    /** The equipment vocabulary the shipped bank uses, offered as the gym checklist. */
    val availableEquipment: List<String> = emptyList(),
    /** The day having an exercise added to it, while the picker is open. */
    val picking: PickingFor? = null,
    /**
     * Whether the last regeneration can still be stepped back from.
     *
     * One step, and only until the routine has been trained (ADR 0006). Read from storage rather
     * than remembered, so finishing a session — which clears it — is reflected here, and so the
     * button does not vanish because the process was killed.
     */
    val canUndo: Boolean = false,
    /**
     * A property changed that the routine could be rebuilt around, waiting on an answer.
     *
     * **An offer and never an action.** Somebody ticking a machine because they joined a new gym
     * has said nothing about wanting a different routine, and quietly rebuilding their training
     * plan for them would be the worst kind of helpful.
     */
    val offeringRebuild: RebuildOffer? = null,
    /** Whether the log turned out to hold no routine worth rebuilding, after they asked. */
    val nothingToRebuild: Boolean = false,
    /**
     * The past routines the log holds, waiting on the lifter to say which one they meant.
     *
     * Null until they ask, and null again where the log held exactly one: a question with a
     * single answer is not a question. Asked rather than guessed at because anybody who has
     * trained for a few months has followed more than one routine, and picking for them is how a
     * rotation ends up assembled out of two.
     */
    val choosingRoutine: RebuildChoice? = null,
    /**
     * The last change to the rotation that started the cycle again, if one was made here.
     *
     * **Stated, never asked.** The cycle has already started again by the time this is set; the
     * screen says what happened and asks nobody to approve it.
     */
    val revision: Revision? = null,
    /**
     * How much training the lifter has behind them, which one conflict rule reads.
     *
     * Theirs rather than the routine's, so it comes from the profile.
     */
    val experience: Experience = Experience.TRAINED,
    /** The bank, so the least-bad question can be asked about muscles their bans have emptied. */
    val bank: List<Exercise> = emptyList(),
    /**
     * An exercise the lifter picked that they had banned, waiting on them to say yes anyway.
     *
     * **A warning and never a refusal.** A dislike stops the app suggesting something; it was
     * never a promise to argue with somebody who asks for it by name, and a routine they built
     * themselves is theirs.
     */
    val addingDisliked: Exercise? = null,
) {
    /**
     * What to say about the last revision, for as long as it is still true.
     *
     * "You are back at the first day" stops being true the moment a day of the new cycle is
     * trained, and was never true of a later cycle. Said past either point, it is a stale message
     * sitting beside a rotation that contradicts it.
     */
    val revised: Revised?
        get() = revision
            ?.takeIf { it.cycle == progress?.cycle?.ordinal }
            ?.takeIf { progress?.cycle?.outcomes.isNullOrEmpty() }
            ?.kind
}

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val repository = RoutineRepository(
        database = database,
        routines = database.routineDao(),
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
    )

    private val sessions = SessionRepository(
        database = database,
        sessions = database.sessionDao(),
        clock = Clock.systemDefaultZone(),
        zone = java.time.ZoneId.systemDefault(),
        profile = database.profileRepository(),
    )

    private val exercises = ExerciseRepository(
        database,
        database.exerciseDao(),
        database.sessionDao(),
        database.profileRepository(),
    )

    /** What the screen holds and no database supplies: the picker, and what it is asking about. */
    private val held = MutableStateFlow(RoutineUiState())

    val state: StateFlow<RoutineUiState> = combine(
        repository.observeProgress(),
        database.exerciseDao().observeEquipment(),
        held,
        database.profileRepository().observeExperience(),
        exercises.observeBank(),
    ) { progress, equipment, onScreen, experience, bank ->
        onScreen.copy(
            progress = progress,
            availableEquipment = equipment,
            experience = experience,
            bank = bank,
            // Asked of the database rather than remembered: a session logged elsewhere in the app
            // spends the step back, and this is what notices.
            canUndo = repository.canUndo(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutineUiState())

    fun createRoutine(name: String) {
        viewModelScope.launch { repository.createRoutine(name = name) }
    }

    /**
     * Lays out a routine from a shape the lifter recognised.
     *
     * Everything the setup asked for travels together as one [RoutineSetup], with no defaults to
     * fall back on: a goal left off here would be a goal the lifter never chose.
     */
    fun createFromShape(name: String, shape: SplitShape, setup: RoutineSetup) {
        viewModelScope.launch {
            repository.createFromShape(
                name = name,
                shape = shape,
                goal = setup.goal,
                cadence = setup.cadence,
                equipment = setup.equipment,
                repPreference = setup.repPreference,
                cardio = setup.cardio,
                // Their exclusions travel on the rows themselves, so the generator reads what
                // they decided without being told about it separately.
                bank = exercises.observeBank().first(),
            )
        }
    }

    /** What the lifter reads weights in now, which setup's first page starts on. */
    suspend fun preferredUnit(): WeightUnit = database.profileRepository().observeDisplayUnit().first()

    /**
     * Builds the routine setup asked for (#144): kilos or pounds is the lifter's own **Preferred
     * unit**, written where the setting lives, and the rest is the routine's.
     */
    fun buildFromSetup(answers: SetupAnswers) {
        viewModelScope.launch {
            database.profileRepository().setDisplayUnit(answers.unit)
            val shape = answers.shapeToBuild()
            val setup = answers.toRoutineSetup()
            repository.createFromShape(
                name = shape.description,
                shape = shape,
                goal = setup.goal,
                cadence = setup.cadence,
                equipment = setup.equipment,
                repPreference = setup.repPreference,
                cardio = setup.cardio,
                bank = exercises.observeBank().first(),
            )
        }
    }

    fun editProperties(
        name: String,
        goal: RoutineGoal,
        cadence: Int,
        equipment: Set<String>,
        repPreference: RepPreference,
        cardio: CardioPlacement,
    ) {
        val before = state.value.progress?.routine
        viewModelScope.launch {
            repository.editProperties(name, goal, cadence, equipment, repPreference, cardio)

            // The offer, made only where something actually changed that the routine could be
            // built around — and never for a rename, which changes nothing about the training.
            val offer = when {
                before == null || before.shape == null -> null
                before.equipment != equipment -> RebuildOffer.EQUIPMENT
                before.goal != goal ||
                    before.cadence != cadence ||
                    before.repPreference != repPreference -> RebuildOffer.WHAT_IT_IS_FOR

                else -> null
            }
            if (offer != null) held.update { it.copy(offeringRebuild = offer) }
        }
    }

    /** Takes the offer, which is the only way a property change reaches the days. */
    fun acceptRebuild() {
        held.update { it.copy(offeringRebuild = null) }
        regenerate()
    }

    /**
     * Declines it, leaving the routine exactly as it was.
     *
     * Nothing to undo, because nothing was done: the offer never touched the days.
     */
    fun declineRebuild() = held.update { it.copy(offeringRebuild = null) }

    fun dismissNothingToRebuild() = held.update { it.copy(nothingToRebuild = false) }

    fun addDay(name: String, kind: RoutineDayKind = RoutineDayKind.LIFTING) {
        val routine = state.value.progress?.routine ?: return
        viewModelScope.launch {
            repository.addDay(routine.id, name, kind)
            say(Revised.ADDED)
        }
    }

    /** Not a **Revision**, so nothing is said: the cycle carries on exactly where it was. */
    fun renameDay(dayId: Long, name: String) {
        viewModelScope.launch { repository.renameDay(dayId, name) }
    }

    /** Opens the picker for [dayId], holding the bank while it is up. */
    fun startPicking(dayId: Long) {
        val day = state.value.progress?.routine?.rotation?.firstOrNull { it.id == dayId } ?: return
        viewModelScope.launch {
            val bank = exercises.observeBank().first()
            held.update { it.copy(picking = PickingFor(dayId, day.name, bank)) }
        }
    }

    fun cancelPicking() = held.update { it.copy(picking = null, addingDisliked = null) }

    /** Keeps one of a day's exercises through the next regeneration, or stops keeping it. */
    fun setPinned(dayId: Long, exerciseId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(dayId, exerciseId, pinned) }
    }

    /**
     * Builds the routine's days again, keeping what was pinned.
     *
     * **Only from here.** Nothing else in the app calls it: a routine is fixed until the lifter
     * asks for something different, and the seed going up is what makes the next answer a
     * different one rather than the same one again. The seed lives on the routine, so closing the
     * app does not put it back to where it started.
     */
    fun regenerate() {
        val shape = state.value.progress?.routine?.shape ?: return
        viewModelScope.launch {
            repository.regenerate(shape, exercises.observeBank().first())
        }
    }

    /**
     * Asks the log which routines it holds, and brings one back where there is only one.
     *
     * Read out of the **Plan** snapshots the log already holds, which is why this needs no storage
     * of its own — and why one active routine is non-lossy rather than approximately so.
     *
     * **Where the log holds more than one, the lifter picks.** The seasonal case this exists for
     * is "the routine I ran last spring", which means naming it; and a window wide enough to reach
     * last spring is wide enough to hold whatever they were doing before and after it too.
     */
    fun rebuildFromLog(setup: RoutineSetup) {
        viewModelScope.launch {
            val read = sessions.recentPlans(RECOVERABLE_SESSIONS)
            val recovered = rebuildFrom(read)
            when (recovered.size) {
                // Said rather than silently doing nothing. A button that answers a tap with
                // stillness reads as broken, and "you have no history yet" is a real answer.
                0 -> held.update { it.copy(nothingToRebuild = true) }
                // A question with one answer is not a question.
                1 -> bringBack(recovered.single(), setup)
                else -> held.update {
                    it.copy(
                        choosingRoutine = RebuildChoice(
                            routines = recovered,
                            setup = setup,
                            limit = RECOVERABLE_SESSIONS.takeIf { read.size == it },
                        ),
                    )
                }
            }
        }
    }

    /** Brings back the past routine the lifter picked, built against what setup said. */
    fun choose(chosen: RecoveredRoutine) {
        val choice = state.value.choosingRoutine ?: return
        held.update { it.copy(choosingRoutine = null) }
        bringBack(chosen, choice.setup)
    }

    /**
     * Brings back [chosen], replacing the active routine rather than running beside it.
     *
     * What comes back is an ordinary routine, editable like any other. It carries neither a shape
     * nor a seed, because this one was generated from nothing: regenerating it would be inventing
     * a structure the lifter never chose.
     */
    private fun bringBack(chosen: RecoveredRoutine, setup: RoutineSetup) {
        viewModelScope.launch {
            repository.rebuildFromSessions(
                // The name the sessions themselves recorded, which is what the lifter called it.
                name = chosen.name,
                recovered = chosen.days,
                goal = setup.goal,
                cadence = setup.cadence,
                equipment = setup.equipment,
                repPreference = setup.repPreference,
                cardio = setup.cardio,
            )
        }
    }

    /** Leaves the choice, with no routine brought back and nothing else touched. */
    fun cancelRebuildChoice() = held.update { it.copy(choosingRoutine = null) }

    /** Puts back what the days held before the last regeneration. */
    fun undoRegeneration() {
        viewModelScope.launch { repository.undoRegeneration() }
    }

    /**
     * Adds what was picked, or asks first where they had banned it.
     *
     * The asking is the whole of the difference: nothing here can refuse.
     */
    fun pick(exercise: Exercise) {
        val picking = state.value.picking ?: return
        if (exercise.isDisliked && state.value.addingDisliked?.id != exercise.id) {
            held.update { it.copy(addingDisliked = exercise) }
            return
        }
        addExerciseToDay(picking.dayId, exercise)
        held.update { it.copy(picking = null, addingDisliked = null) }
    }

    /**
     * Puts [exercise] in [dayId], asking first where the lifter had banned it.
     *
     * The way back in from the bank's own screen, where they went to write down a machine. It
     * goes through the same asking as the picker, because the warning is about what is being
     * added and not about which screen it was chosen on.
     */
    fun pickInto(dayId: Long, exercise: Exercise) {
        if (exercise.isDisliked) {
            val day = state.value.progress?.routine?.rotation?.firstOrNull { it.id == dayId }
            if (day != null) {
                viewModelScope.launch {
                    val bank = exercises.observeBank().first()
                    held.update {
                        it.copy(
                            picking = PickingFor(dayId, day.name, bank),
                            addingDisliked = exercise,
                        )
                    }
                }
                return
            }
        }
        addExerciseToDay(dayId, exercise)
    }

    /** Adds an exercise to a day. Not a revision, so the cycle and the cursor are untouched. */
    private fun addExerciseToDay(dayId: Long, exercise: Exercise) {
        viewModelScope.launch { repository.addExerciseToDay(dayId, exercise.id) }
    }

    /** Takes an exercise out of a day. Not a revision either, for the same reason. */
    fun removeExerciseFromDay(dayId: Long, exerciseId: String) {
        viewModelScope.launch { repository.removeExerciseFromDay(dayId, exerciseId) }
    }

    fun removeDay(dayId: Long) {
        viewModelScope.launch {
            if (repository.removeDay(dayId)) say(Revised.REMOVED)
        }
    }

    /**
     * Moves a day, and says the cycle started again only where it did.
     *
     * Moving the first day up or the last day down changes nothing, so it revises nothing and
     * there is nothing to say.
     */
    fun moveDay(dayId: Long, by: Int) {
        viewModelScope.launch {
            if (repository.moveDay(dayId, by)) say(Revised.MOVED)
        }
    }

    /** Records that [kind] started the cycle again, against the cycle it started. */
    private suspend fun say(kind: Revised) {
        val cycle = repository.observeProgress().first()?.cycle?.ordinal
        held.update { it.copy(revision = Revision(kind, cycle)) }
    }

    fun deleteRoutine() {
        // What was said about the old routine's cycle is not true of whatever comes next.
        held.update { it.copy(revision = null) }
        viewModelScope.launch { repository.deleteRoutine() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How far back to read for a routine worth rebuilding.
         *
         * **Years rather than months.** The case this exists for is "the routine I ran last
         * spring", and forty sessions is about three months at any ordinary cadence — so the one
         * routine the feature is named after was the one routine outside the window. Five hundred
         * reaches back past two years of training four times a week, which is longer than most
         * people keep one plan.
         *
         * Affordable because of what it reads: the plans and none of the sets. Widening the old
         * read would have dragged every logged set of those two years along with them.
         */
        const val RECOVERABLE_SESSIONS = 500
    }
}
