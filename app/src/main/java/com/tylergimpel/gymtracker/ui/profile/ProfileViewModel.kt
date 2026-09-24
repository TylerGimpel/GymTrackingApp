package com.tylergimpel.gymtracker.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.BodyweightRepository
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.BodyweightEntry
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.Profile
import com.tylergimpel.gymtracker.domain.Sex
import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile = Profile(),
    /** The latest weigh-in, which is what anything wanting a bodyweight reads. */
    val bodyweight: BodyweightEntry? = null,
    /** The **Preferred unit**, which the latest weigh-in is read in. */
    val preferredUnit: WeightUnit = WeightUnit.KILOGRAMS,
)

/**
 * Who the lifter is, as far as they choose to say.
 *
 * Every write goes straight through: there is no save button, because there is nothing to
 * commit to. Each answer stands on its own and can be withdrawn the same way it was given.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GymDatabase.getInstance(application)
    private val profile = database.profileRepository()
    private val bodyweight = BodyweightRepository(database, database.bodyweightDao())

    val state: StateFlow<ProfileUiState> = combine(
        profile.observeProfile(),
        bodyweight.observeHistory(),
        profile.observeDisplayUnit(),
    ) { chosen, history, unit ->
        ProfileUiState(profile = chosen, bodyweight = history.latest, preferredUnit = unit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileUiState())

    fun setSex(sex: Sex?) {
        viewModelScope.launch { profile.setSex(sex) }
    }

    fun setExperience(experience: Experience) {
        viewModelScope.launch { profile.setExperience(experience) }
    }

    fun setBirthYear(year: Int?) {
        viewModelScope.launch { profile.setBirthYear(year) }
    }

    fun setHeightCm(heightCm: Double?) {
        viewModelScope.launch { profile.setHeightCm(heightCm) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
