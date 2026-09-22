package com.tylergimpel.gymtracker.ui.routine

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
    /** How many times variety has been asked for, which is what makes each answer a new one. */
    val seed: Int = 0,
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
)

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
     * Everything the setup asked for travels together, because the cadence is what volume
     * translation needs and the cardio placement is what decided whether a cardio position was
     * among the shapes at all.
     */
    fun createFromShape(
        name: String,
        shape: SplitShape,
        weeklyFrequency: Int,
        cardio: CardioPlacement,
        equipment: Set<String>,
        goal: RoutineGoal = RoutineGoal.HYPERTROPHY,
        repPreference: RepPreference = RepPreference.MODERATE,
    ) {
        viewModelScope.launch {
            repository.createFromShape(
                name = name,
                shape = shape,
                goal = goal,
                cadence = weeklyFrequency,
                equipment = equipment,
                repPreference = repPreference,
                cardio = cardio,
                // Their exclusions travel on the rows themselves, so the generator reads what
                // they decided without being told about it separately.
                bank = exercises.observeBank().first(),
            )
            held.update { it.copy(seed = 0) }
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
        viewModelScope.launch { repository.addDay(routine.id, name, kind) }
    }

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
     * different one rather than the same one again.
     */
    fun regenerate() {
        val shape = state.value.progress?.routine?.shape ?: return
        viewModelScope.launch {
            val next = state.value.seed + 1
            repository.regenerate(shape, exercises.observeBank().first(), seed = next)
            held.update { it.copy(seed = next) }
        }
    }

    /**
     * Brings back the routine a run of past sessions was following.
     *
     * Read out of the **Plan** snapshots the log already holds, which is why this needs no storage
     * of its own — and why one active routine is non-lossy rather than approximately so. What
     * comes back replaces the current one; it does not run beside it.
     */
    fun rebuildFromLog(weeklyFrequency: Int, cardio: CardioPlacement, equipment: Set<String>) {
        viewModelScope.launch {
            val recovered = rebuildFrom(sessions.observeRecent(RECOVERABLE_SESSIONS).first())
            if (recovered.isEmpty()) {
                // Said rather than silently doing nothing. A button that answers a tap with
                // stillness reads as broken, and "you have no history yet" is a real answer.
                held.update { it.copy(nothingToRebuild = true) }
                return@launch
            }

            repository.rebuildFromSessions(
                name = recovered.first().name,
                recovered = recovered,
                goal = RoutineGoal.HYPERTROPHY,
                cadence = weeklyFrequency,
                equipment = equipment,
                repPreference = RepPreference.MODERATE,
                cardio = cardio,
            )
            // No shape is recorded for it, because this one was not laid out from one.
            // Regenerating it would be inventing a structure the lifter never chose.
            held.update { it.copy(seed = 0) }
        }
    }

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
    fun addExerciseToDay(dayId: Long, exercise: Exercise) {
        viewModelScope.launch { repository.addExerciseToDay(dayId, exercise.id) }
    }

    /** Takes an exercise out of a day. Not a revision either, for the same reason. */
    fun removeExerciseFromDay(dayId: Long, exerciseId: String) {
        viewModelScope.launch { repository.removeExerciseFromDay(dayId, exerciseId) }
    }

    fun removeDay(dayId: Long) {
        viewModelScope.launch { repository.removeDay(dayId) }
    }

    fun moveDay(dayId: Long, by: Int) {
        viewModelScope.launch { repository.moveDay(dayId, by) }
    }

    fun deleteRoutine() {
        viewModelScope.launch { repository.deleteRoutine() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How far back to read for a routine worth rebuilding.
         *
         * Enough to hold several passes through any rotation the app offers, so the *last* time
         * each day was trained is in there — which is the version a lifter rebuilding from last
         * spring actually wants.
         */
        const val RECOVERABLE_SESSIONS = 40
    }
}
