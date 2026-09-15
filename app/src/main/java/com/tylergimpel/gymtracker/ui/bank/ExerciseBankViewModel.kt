package com.tylergimpel.gymtracker.ui.bank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.ExerciseAliasStore
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.hasNameLike
import kotlinx.coroutines.flow.map
import com.tylergimpel.gymtracker.domain.ExerciseAliases
import com.tylergimpel.gymtracker.domain.search
import com.tylergimpel.gymtracker.domain.workingMuscle
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
            aliases,
            vocabulary,
        ) { bank, screen, aliases, vocabulary ->
            // Muscle first, then what was typed: the muscle answers "what is there for this body
            // part" and the query narrows that, rather than the other way round.
            val forMuscle = screen.muscle?.let(bank::workingMuscle) ?: bank
            val matching = forMuscle.search(screen.query, aliases)

            ExerciseBankUiState(
                exercises = matching,
                query = screen.query,
                muscle = screen.muscle,
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

    fun startAdding() = screen.update { it.copy(adding = NewExerciseDraft()) }

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