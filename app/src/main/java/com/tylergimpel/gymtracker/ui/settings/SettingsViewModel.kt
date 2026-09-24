package com.tylergimpel.gymtracker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tylergimpel.gymtracker.data.GymDatabase
import com.tylergimpel.gymtracker.data.profileRepository
import com.tylergimpel.gymtracker.domain.Theme
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.rest.RestAlarmScheduler
import com.tylergimpel.gymtracker.rest.RestNotifications
import com.tylergimpel.gymtracker.ui.session.RestAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** The **Preferred unit**: what the app starts in, and what every exercise falls back to. */
    val preferredUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val theme: Theme = Theme.SYSTEM,
    /** What the rest timer can currently do, read from the platform as the session reads it. */
    val restAlerts: RestAlerts = RestAlerts(),
)

/**
 * Everything that is the lifter's rather than a routine's, in one place.
 *
 * Nothing here rebuilds a routine: the unit changes what is shown and never what is stored, the
 * theme is the app's, and the rest-timer state is read rather than changed.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val profile = GymDatabase.getInstance(application).profileRepository()
    private val alarms = RestAlarmScheduler(application)
    private val notifications = RestNotifications(application)

    private val restAlerts = MutableStateFlow(readRestAlerts())

    val state: StateFlow<SettingsUiState> = combine(
        profile.observeDisplayUnit(),
        profile.observeTheme(),
        restAlerts,
        ::SettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun setDisplayUnit(unit: WeightUnit) {
        viewModelScope.launch { profile.setDisplayUnit(unit) }
    }

    fun setTheme(theme: Theme) {
        viewModelScope.launch { profile.setTheme(theme) }
    }

    /**
     * Reads the rest-timer permissions again.
     *
     * Both are granted somewhere else and both are revocable, so the answer is read every time the
     * lifter comes back to this screen — which is exactly when they have just been to fix one.
     */
    fun recheckRestAlerts() {
        restAlerts.value = readRestAlerts()
    }

    private fun readRestAlerts() = RestAlerts(
        alarmsAreReliable = alarms.canWakeReliably(),
        countdownVisible = notifications.canBeSeen(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
