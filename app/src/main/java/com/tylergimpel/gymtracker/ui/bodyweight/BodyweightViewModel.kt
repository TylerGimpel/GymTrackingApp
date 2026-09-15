package com.tylergimpel.gymtracker.ui.bodyweight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.BodyweightRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.domain.BodyweightDraft
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** The weigh-in being corrected, if one is open. */
data class EditingWeighIn(
    val id: Long,
    val draft: BodyweightDraft,
    /**
     * Whether the day this has been moved onto already holds a different weigh-in, which is the one
     * correction that is refused: a day holds one weight, and taking this one there would drop a
     * reading the lifter never asked to lose.
     */
    val dayAlreadyWeighed: Boolean = false,
)

/** Everything on this screen that is not read from the database. */
private data class ScreenState(
    val draft: BodyweightDraft,
    val editing: EditingWeighIn? = null,
)

data class BodyweightUiState(
    /** Every weigh-in, newest day first. */
    val history: List<BodyweightEntry> = emptyList(),
    val draft: BodyweightDraft,
    val editing: EditingWeighIn? = null,
) {
    /**
     * The unit this screen reads in, taken from the entry controls so that one weight and the whole
     * history beneath it can never be read in different units at the same time.
     */
    val displayUnit: WeightUnit get() = draft.displayUnit
}

/**
 * Recording what the lifter weighs, and reading back what they have weighed.
 *
 * The unit here belongs to the lifter rather than to any one weigh-in — unlike an exercise's, which
 * belongs to the machine — so nothing is stored per entry and every row on screen is read in the
 * same one. It starts at [WeightUnit.DEFAULT] and is not remembered across a visit: the profile
 * default it will eventually come from belongs to onboarding, which is out of scope for the
 * foundation spec, and inventing a settings table to hold one weight unit would be the wrong place
 * to put it.
 */
class BodyweightViewModel(application: Application) : AndroidViewModel(application) {

    private val clock: Clock = Clock.systemDefaultZone()

    private val database = GymDatabase.getInstance(application)

    private val repository = BodyweightRepository(database, database.bodyweightDao())

    private val screen = MutableStateFlow(ScreenState(draft = BodyweightDraft(date = today())))

    val state: StateFlow<BodyweightUiState> =
        combine(repository.observeHistory(), screen) { history, screen ->
            BodyweightUiState(
                history = history.mostRecentFirst,
                draft = screen.draft,
                editing = screen.editing,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            BodyweightUiState(draft = BodyweightDraft(date = today())),
        )

    fun changeDraft(draft: BodyweightDraft) = screen.update { it.copy(draft = draft) }

    /**
     * Reads the whole screen in [displayUnit] — the field, and the history under it.
     *
     * A weigh-in open for correction moves with them, because a lifter who has just switched the
     * screen to pounds is correcting a number they are now reading in pounds.
     */
    fun readIn(displayUnit: WeightUnit) = screen.update {
        it.copy(
            draft = it.draft.readIn(displayUnit),
            editing = it.editing?.let { open -> open.copy(draft = open.draft.readIn(displayUnit)) },
        )
    }

    /**
     * Records what the field holds against the day it is dated.
     *
     * The field empties afterwards and the date returns to today. Unlike a set, where the numbers
     * are kept because the next one is usually the same, there is no second weigh-in coming: the
     * next one is tomorrow, and leaving today's reading in the field invites recording it twice.
     */
    fun record() {
        val draft = screen.value.draft
        val weight = draft.weight ?: return
        viewModelScope.launch {
            repository.record(draft.date, weight)
            screen.update { it.copy(draft = BodyweightDraft(date = today(), displayUnit = draft.displayUnit)) }
        }
    }

    fun startEditing(entry: BodyweightEntry) = screen.update {
        val displayUnit = it.draft.displayUnit
        it.copy(
            editing = EditingWeighIn(
                id = entry.id,
                draft = BodyweightDraft(
                    // Seeded in the unit the row is shown in, so opening one for correction does
                    // not change the number the lifter was reading a moment ago.
                    weightText = entry.weight.asTextIn(displayUnit),
                    date = entry.date,
                    displayUnit = displayUnit,
                ),
            ),
        )
    }

    /** Editing anything clears the refusal: the lifter is answering it. */
    fun changeEditingDraft(draft: BodyweightDraft) = screen.update {
        it.copy(editing = it.editing?.copy(draft = draft, dayAlreadyWeighed = false))
    }

    fun cancelEditing() = screen.update { it.copy(editing = null) }

    /**
     * Saves the correction, or says why it could not be saved.
     *
     * The dialog stays open on a refusal rather than closing over a change that did not happen —
     * the day it was moved onto already holds a weight, and which of the two survives is the
     * lifter's to decide.
     */
    fun saveEditing() {
        val open = screen.value.editing ?: return
        val weight = open.draft.weight ?: return
        viewModelScope.launch {
            val corrected = repository.correct(open.id, open.draft.date, weight)
            screen.update {
                if (corrected) it.copy(editing = null)
                else it.copy(editing = it.editing?.copy(dayAlreadyWeighed = true))
            }
        }
    }

    fun deleteEditing() {
        val open = screen.value.editing ?: return
        viewModelScope.launch {
            repository.delete(open.id)
            screen.update { it.copy(editing = null) }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
