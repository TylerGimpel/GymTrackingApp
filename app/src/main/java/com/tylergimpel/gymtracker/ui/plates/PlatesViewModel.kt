package com.tylergimpel.gymtracker.ui.plates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.plateRepository
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.PlatePair
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the screen holds that the database does not: two fields being typed into. */
private data class ScreenState(val bar: String? = null, val adding: String = "")

data class PlatesUiState(
    /** The pairs in the room, heaviest first. */
    val pairs: List<PlatePair> = emptyList(),
    val bar: Kilograms = Kilograms(0.0),
    val unit: WeightUnit = WeightUnit.DEFAULT,
    /** The bar as it is being typed, or null while nobody is typing into it. */
    val barDraft: String? = null,
    /** A weight being added to the rack, as typed. */
    val adding: String = "",
) {
    /** What the bar field shows: what is being typed, or what is recorded. */
    val barText: String get() = barDraft ?: bar.asTextIn(unit)
}

/**
 * The plates in the room, as the lifter keeps them.
 *
 * One inventory, global, and the same one the engine rounds against and the plate line reads —
 * which is why editing it here changes what the next set asks for, with nothing to synchronise.
 */
class PlatesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val profile = database.profileRepository()
    private val plates = database.plateRepository()

    private val screen = MutableStateFlow(ScreenState())

    val state: StateFlow<PlatesUiState> =
        combine(plates.observeBarbell(), profile.observeDisplayUnit(), screen) { barbell, unit, s ->
            PlatesUiState(
                pairs = barbell.plates.sortedByDescending { it.weight.value },
                bar = barbell.bar,
                unit = unit,
                barDraft = s.bar,
                adding = s.adding,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            PlatesUiState(),
        )

    fun typeBar(text: String) = screen.update { it.copy(bar = text) }

    /** Records the typed bar, and gives the field back to whatever is stored. */
    fun saveBar() {
        val typed = screen.value.bar?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }
        screen.update { it.copy(bar = null) }
        val unit = state.value.unit
        typed?.let { viewModelScope.launch { plates.setBar(unit.toKilograms(it)) } }
    }

    fun typeAdding(text: String) = screen.update { it.copy(adding = text) }

    /** Adds one pair of whatever weight is typed, which is how a rack grows. */
    fun addPair() {
        val typed = screen.value.adding.trim().toDoubleOrNull()?.takeIf { it > 0 } ?: return
        val weight = state.value.unit.toKilograms(typed)
        // Compared as a weight rather than as text: "20" and "20.0" are the same plate, and
        // matching the strings would quietly reset a count to one instead of adding to it.
        val already = state.value.pairs.firstOrNull { abs(it.weight.value - weight.value) < DUST }

        screen.update { it.copy(adding = "") }
        viewModelScope.launch { plates.setPairs(weight, (already?.pairs ?: 0) + 1) }
    }

    /**
     * Says there are [pairs] of [weight], and nothing at all at none.
     *
     * Removing rather than storing a zero, so that an inventory lists what somebody has. The one
     * consequence worth knowing: emptying it entirely hands the rack back to the standard one for
     * their unit, because an app that can build no weight at all is of no use to anybody.
     */
    fun setPairs(weight: Kilograms, pairs: Int) {
        viewModelScope.launch { plates.setPairs(weight, pairs) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Far below the lightest plate ever cast, far above what a conversion leaves behind. */
        const val DUST = 1e-6
    }
}
