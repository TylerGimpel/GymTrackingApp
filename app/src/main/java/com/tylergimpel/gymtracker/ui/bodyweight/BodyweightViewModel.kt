package com.tylergimpel.gymtracker.ui.bodyweight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.BodyweightRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
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
data class EditingWeighIn(val id: Long, val draft: BodyweightDraft)

/** Everything on this screen that is not read from the database. */
private data class ScreenState(
    val draft: BodyweightDraft,
    val editing: EditingWeighIn? = null,
)

data class BodyweightUiState(
    /** Every weigh-in, newest day first, and within a day the latest reading first. */
    val history: List<BodyweightEntry> = emptyList(),
    /**
     * The days holding more than one reading, which are the only rows that show a time.
     *
     * A date is how a lifter thinks of a weigh-in; the moment only earns its place on screen where
     * it tells two readings apart.
     */
    val daysWeighedSeveralTimes: Set<LocalDate> = emptySet(),
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
 * belongs to the machine — so nothing is stored per entry, every row on screen is read in the same
 * one, and tapping a chip settles it for good. It is the same setting every exercise falls back to,
 * so a lifter who switches to pounds here is not asked again on the logging screen.
 */
class BodyweightViewModel(application: Application) : AndroidViewModel(application) {

    private val clock: Clock = Clock.systemDefaultZone()

    private val database = GymDatabase.getInstance(application)

    private val repository = BodyweightRepository(database, database.bodyweightDao())

    private val profile = database.profileRepository()

    private val screen = MutableStateFlow(ScreenState(draft = BodyweightDraft(date = today())))

    /**
     * The screen, read in the unit the lifter reads in.
     *
     * The stored unit is applied to the draft here rather than copied into it when the screen opens,
     * so the field and the history can never be showing different units — and so a unit tapped on
     * the logging screen is already in force the next time this one is opened.
     */
    val state: StateFlow<BodyweightUiState> =
        combine(
            repository.observeHistory(),
            profile.observeDisplayUnit(),
            screen,
        ) { history, lifterUnit, screen ->
            BodyweightUiState(
                history = history.mostRecentFirst,
                daysWeighedSeveralTimes = history.daysWeighedSeveralTimes,
                draft = screen.draft.readIn(lifterUnit),
                editing = screen.editing?.let { it.copy(draft = it.draft.readIn(lifterUnit)) },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            BodyweightUiState(draft = BodyweightDraft(date = today())),
        )

    fun changeDraft(draft: BodyweightDraft) = screen.update { it.copy(draft = draft) }

    /**
     * Reads weights in [displayUnit] from now on — here, and everywhere else the lifter has not
     * settled a unit for a particular machine.
     *
     * Remembered rather than held for the visit, which is what a lifter means by tapping it: a
     * bathroom scale is labelled in one unit and does not change its mind on Tuesday. The screen
     * follows the stored value, so nothing here has to move the drafts by hand.
     */
    fun readIn(displayUnit: WeightUnit) {
        viewModelScope.launch { profile.setDisplayUnit(displayUnit) }
    }

    /**
     * Records what the field holds, stamped with the moment it was recorded.
     *
     * The field empties afterwards and the day returns to today, so that a lifter who weighs
     * themselves again this evening enters a reading rather than editing this morning's by
     * accident — the two are different measurements and the log now keeps both.
     */
    fun record() {
        val draft = state.value.draft
        val weight = draft.weight ?: return
        viewModelScope.launch {
            repository.record(clock.instant(), draft.date, weight)
            screen.update { it.copy(draft = BodyweightDraft(date = today(), displayUnit = draft.displayUnit)) }
        }
    }

    fun startEditing(entry: BodyweightEntry) {
        val displayUnit = state.value.displayUnit
        screen.update {
            it.copy(
                editing = EditingWeighIn(
                    id = entry.id,
                    draft = BodyweightDraft(
                        // Seeded in the unit the row is shown in, so opening one for correction
                        // does not change the number the lifter was reading a moment ago.
                        weightText = entry.weight.asTextIn(displayUnit),
                        date = entry.date,
                        displayUnit = displayUnit,
                    ),
                ),
            )
        }
    }

    fun changeEditingDraft(draft: BodyweightDraft) =
        screen.update { it.copy(editing = it.editing?.copy(draft = draft)) }

    fun cancelEditing() = screen.update { it.copy(editing = null) }

    /** Saves the correction. When the reading was taken is untouched. */
    fun saveEditing() {
        val open = state.value.editing ?: return
        val weight = open.draft.weight ?: return
        viewModelScope.launch {
            repository.correct(open.id, open.draft.date, weight)
            screen.update { it.copy(editing = null) }
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
