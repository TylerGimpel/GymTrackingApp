package com.tylergimpel.gymtracker.ui.bank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.ExerciseAliasStore
import com.tylergimpel.gymtracker.data.ExerciseRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ExerciseAliases
import com.tylergimpel.gymtracker.domain.search
import com.tylergimpel.gymtracker.domain.workingMuscle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

/** What the screen has typed and chosen, as opposed to what the bank holds. */
private data class ScreenState(val query: String = "", val muscle: String? = null)

class ExerciseBankViewModel(application: Application) : AndroidViewModel(application) {

    private val repository =
        ExerciseRepository(GymDatabase.getInstance(application).exerciseDao())
    private val aliasStore = ExerciseAliasStore(application)

    private val screen = MutableStateFlow(ScreenState())
    private val aliases = MutableStateFlow<ExerciseAliases>(emptyMap())

    init {
        viewModelScope.launch { aliases.value = aliasStore.load() }
    }

    val state: StateFlow<ExerciseBankUiState> =
        combine(repository.observeBank(), screen, aliases) { bank, screen, aliases ->
            // Muscle first, then what was typed: the muscle answers "what is there for this body
            // part" and the query narrows that, rather than the other way round.
            val forMuscle = screen.muscle?.let(bank::workingMuscle) ?: bank
            val matching = forMuscle.search(screen.query, aliases)

            ExerciseBankUiState(
                exercises = matching,
                query = screen.query,
                muscle = screen.muscle,
                muscles = bank.flatMap(Exercise::primaryMuscles).distinct().sorted(),
                total = bank.size,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ExerciseBankUiState(),
        )

    fun search(query: String) = screen.update { it.copy(query = query) }

    /** Selecting the muscle already chosen clears the filter. */
    fun filterBy(muscle: String) =
        screen.update { it.copy(muscle = if (it.muscle == muscle) null else muscle) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
