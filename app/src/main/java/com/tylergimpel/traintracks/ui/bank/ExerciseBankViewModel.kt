package com.tylergimpel.traintracks.ui.bank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.traintracks.data.ExerciseAliasStore
import com.tylergimpel.traintracks.data.ExerciseRepository
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.profileRepository
import com.tylergimpel.traintracks.domain.Exercise
import com.tylergimpel.traintracks.domain.NewExerciseDraft
import com.tylergimpel.traintracks.domain.hasNameLike
import kotlinx.coroutines.flow.map
import com.tylergimpel.traintracks.domain.ExerciseAliases
import com.tylergimpel.traintracks.domain.search
import com.tylergimpel.traintracks.domain.workingMuscle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseBankUiState(
    /** What to show: the whole bank, or what is left after typing and filtering. */
    val exercises: List<Exercise> = emptyList(),
    val query: String = "",
    val muscle: String? = null,
    /** The equipment the list is narrowed to, or null for all of it. */
    val equipment: String? = null,
    /** What each exercise the lifter has done was last done at: weight in kilograms and reps. */
    val last: Map<String, Pair<Double?, Int?>> = emptyMap(),
    /** The muscles the bank actually uses, offered as filters. */
    val muscles: List<String> = emptyList(),
    val total: Int = 0,
    /** The draft of a new exercise, while one is being written. */
    val adding: NewExerciseDraft? = null,
    /** Whether what is being written names something the bank already has. */
    val nameTaken: Boolean = false,
    /**
     * The vocabularies a new exercise chooses from, read out of the bank.
     *
     * Not restated in the app: `data/README.md` is where the closed sets are written down and the
     * build step is what enforces them, so offering anything else here would be inventing a value
     * the bank would reject.
     */
    val vocabulary: BankVocabulary = BankVocabulary(),
)

/** The closed sets a shipped exercise draws on, as the shipped rows actually use them. */
data class BankVocabulary(
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val mechanics: List<String> = emptyList(),
    val forces: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
)

/** What the screen has typed and chosen, as opposed to what the bank holds. */
private data class ScreenState(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
    val adding: NewExerciseDraft? = null,
)

class ExerciseBankViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)

    private val repository =
        ExerciseRepository(
            database,
            database.exerciseDao(),
            database.sessionDao(),
            database.profileRepository(),
        )
    private val aliasStore = ExerciseAliasStore(application)

    private val screen = MutableStateFlow(ScreenState())
    private val aliases = MutableStateFlow<ExerciseAliases>(emptyMap())

    init {
        viewModelScope.launch { aliases.value = aliasStore.load() }
    }

    /**
     * The vocabularies, derived from the bank alone.
     *
     * Its own flow rather than part of the combine below, which re-runs on every keystroke: six
     * passes over 896 rows per typed character, to produce a list that only changes when the bank
     * does.
     */
    private val vocabulary = repository.observeBank()
        .map { it.vocabulary() }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<ExerciseBankUiState> =
        combine(
            repository.observeBank(),
            screen,
            combine(aliases, database.sessionDao().observeWorkingSets()) { a, sets -> a to sets },
            vocabulary,
        ) { bank, screen, aliasesAndSets, vocabulary ->
            val (aliases, sets) = aliasesAndSets
            // Muscle first, then what was typed: the muscle answers "what is there for this body
            // part" and the query narrows that, rather than the other way round.
            val forMuscle = screen.muscle?.let(bank::workingMuscle) ?: bank
            // Bodyweight covers the rows that need nothing at all, as the gym checklist does.
            val forEquipment = when (val wanted = screen.equipment) {
                null -> forMuscle
                "body only" -> forMuscle.filter { it.equipment == null || it.equipment == "body only" }
                else -> forMuscle.filter { it.equipment == wanted }
            }
            val matching = forEquipment.search(screen.query, aliases)

            ExerciseBankUiState(
                exercises = matching,
                query = screen.query,
                muscle = screen.muscle,
                equipment = screen.equipment,
                last = sets.associate { it.exerciseId to (it.weightKg to it.reps) },
                // Every muscle the filter could match on, so nothing is filterable but unoffered.
                muscles = bank
                    .flatMap { it.primaryMuscles + it.secondaryMuscles }
                    .distinct()
                    .sorted(),
                total = bank.size,
                vocabulary = vocabulary,
                adding = screen.adding,
                nameTaken = screen.adding
                    ?.takeIf { it.name.isNotBlank() }
                    ?.let(NewExerciseDraft::name)
                    ?.let(bank::hasNameLike)
                    ?: false,
            )
        }
            // Matching runs over the whole bank on every keystroke, so it does not run on the
            // thread drawing the results.
            .flowOn(Dispatchers.Default)
            .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ExerciseBankUiState(),
        )

    fun search(query: String) = screen.update { it.copy(query = query) }

    fun startAdding(name: String = "") = screen.update { it.copy(adding = NewExerciseDraft(name = name)) }

    /** Choosing the equipment already chosen goes back to all of it. */
    fun filterByEquipment(equipment: String?) =
        screen.update { it.copy(equipment = equipment.takeIf { e -> e != it.equipment }) }

    fun changeAdding(draft: NewExerciseDraft) = screen.update { it.copy(adding = draft) }

    fun cancelAdding() = screen.update { it.copy(adding = null) }

    /** Adds the exercise being written, giving it a UUID in the lifter's own id space. */
    fun saveAdding() {
        val draft = screen.value.adding?.takeIf(NewExerciseDraft::isComplete) ?: return
        viewModelScope.launch {
            repository.createExercise(draft)
            screen.update { it.copy(adding = null) }
        }
    }

    /** Selecting the muscle already chosen clears the filter. */
    fun filterBy(muscle: String) =
        screen.update { it.copy(muscle = if (it.muscle == muscle) null else muscle) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The closed vocabularies as the shipped rows actually use them.
 *
 * Read from the bank rather than restated, so a new exercise can only ever choose a value the build
 * step would accept — the same reason the equipment checklist is read from it.
 */
private fun List<Exercise>.vocabulary() = BankVocabulary(
    muscles = flatMap { it.primaryMuscles + it.secondaryMuscles }.distinct().sorted(),
    equipment = mapNotNull(Exercise::equipment).distinct().sorted(),
    mechanics = mapNotNull(Exercise::mechanic).distinct().sorted(),
    forces = mapNotNull(Exercise::force).distinct().sorted(),
    categories = map(Exercise::category).distinct().sorted(),
    levels = map(Exercise::level).distinct().sorted(),
)