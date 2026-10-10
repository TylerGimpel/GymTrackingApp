package com.tylergimpel.traintracks.ui.session

import com.tylergimpel.traintracks.domain.RestAlarms
import com.tylergimpel.traintracks.domain.RestCountdown
import com.tylergimpel.traintracks.domain.restEnding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration

/**
 * What the platform will currently let a rest do: wake the lifter, and be seen while it runs.
 *
 * Two answers together because they fail the same way — silently, discovered by missing a set —
 * and are surfaced together in the same corner of the screen.
 */
data class RestAlerts(
    val alarmsAreReliable: Boolean = true,
    val countdownVisible: Boolean = true,
)

/** Everything the screen shows about resting, and nothing it shows about anything else. */
data class RestState(
    /** The rest running, if one is. */
    val running: Resting? = null,
    /** The rest being edited, while the lifter is changing one. */
    val editing: Duration? = null,
    /**
     * A rest the lifter set for the set being entered, overruling what the app asked for.
     *
     * Cleared when the exercise changes and when the set it was for goes down: it is a statement
     * about this set rather than about the lifter.
     */
    val forThisSet: Duration? = null,
    /** What the rest timer is currently able to do, as last read from the platform. */
    val alerts: RestAlerts = RestAlerts(),
    /**
     * Whether the screen should ask to show the countdown now.
     *
     * Raised by the first rest that has nowhere to show its countdown, and by nothing else: the
     * reason for the permission is obvious at that moment and at no earlier one (#88). The screen
     * asks, because only it has an activity to ask with, and says so with [SessionRest.asked].
     */
    val askToShowCountdown: Boolean = false,
)

/**
 * The rest timer, as the screen holds it.
 *
 * Its own thing because it shares nothing with entering a set but the exercise it is about: a
 * countdown, two revocable permissions, and a length the lifter can argue with. The session view
 * model delegates rather than reimplementing, which is what keeps both of them readable.
 *
 * **The alarm is the source of truth and this is not.** The process can be killed between two
 * sets, so what is handed to the platform is a moment rather than a countdown; everything here
 * is what the screen draws while the app happens to be alive.
 */
class SessionRest(
    private val alarms: RestAlarms,
    private val notifications: RestCountdown,
    private val clock: Clock,
) {
    private val held = MutableStateFlow(RestState())

    /** Whether the app has asked since it opened; the lifter's answer is theirs to change, not ours. */
    private var askedToShowCountdown = false

    val state: StateFlow<RestState> = held.asStateFlow()

    init {
        recheckAlerts()
    }

    /**
     * Re-reads what the rest timer is allowed to do.
     *
     * Called when the screen is shown and every time it is returned to, because both permissions
     * are revocable and the way either gets granted is the lifter leaving the app and coming
     * back. Nothing here asks for anything: asking belongs to the screen, which has an activity.
     */
    fun recheckAlerts() = held.update {
        it.copy(
            alerts = RestAlerts(
                alarmsAreReliable = alarms.canWakeReliably(),
                countdownVisible = notifications.canBeSeen(),
            ),
        )
    }

    /**
     * Starts a rest: an alarm to wake the lifter, and a countdown they can read without opening
     * anything.
     */
    fun start(length: Duration) {
        val resting = Resting(clock.instant(), length)
        val ask = !askedToShowCountdown && !notifications.canBeSeen()
        held.update { it.copy(running = resting, askToShowCountdown = ask) }
        alarms.schedule(restEnding(resting.startedAt, length))
        notifications.countDownTo(resting.endsAt)
    }

    /** The screen has asked to show the countdown; whatever the answer, it is not asked again. */
    fun asked() {
        askedToShowCountdown = true
        held.update { it.copy(askToShowCountdown = false) }
        recheckAlerts()
    }

    /**
     * Adds a minute to the rest that is running.
     *
     * The end moves rather than the rest restarting: a lifter tapping this is saying the set is
     * not ready yet, not that the last two minutes did not happen.
     */
    fun addTime() {
        val resting = held.value.running ?: return
        val longer = resting.copy(length = resting.length.plus(MORE_REST))
        held.update { it.copy(running = longer) }
        alarms.schedule(restEnding(longer.startedAt, longer.length))
        notifications.countDownTo(longer.endsAt)
    }

    /**
     * Ends a rest, whether it ran out or the lifter had somewhere to be.
     *
     * Takes the alarm with it. Otherwise the lifter is three sets further on when the phone goes
     * off in their pocket.
     */
    fun end() {
        alarms.cancel()
        held.update { it.copy(running = null) }
    }

    /** Opens what the app is asking the lifter to rest for, so they can disagree with it. */
    fun startEditing(asked: Duration?) = held.update { it.copy(editing = asked ?: Duration.ZERO) }

    fun changeEditing(rest: Duration) = held.update { it.copy(editing = rest) }

    fun cancelEditing() = held.update { it.copy(editing = null) }

    /** Rests for this long after the set being entered, and says nothing about the next one. */
    fun thisSetOnly() = held.update { it.copy(forThisSet = it.editing, editing = null) }

    /**
     * The length the lifter typed, for a caller that is about to make it the exercise's own.
     *
     * Handed back rather than written from here: what an exercise rests for is a preference, and
     * preferences belong to the repository that keeps them rather than to a countdown.
     */
    fun editedLength(): Duration? = held.value.editing

    /** Both per-set answers dropped, because the exercise is now settled for this one. */
    fun settledOnTheExercise() = held.update { it.copy(editing = null, forThisSet = null) }

    /**
     * Forgets a rest that was only ever about one set.
     *
     * Called when that set goes down and when the lifter moves to another exercise. Leaving it
     * standing would quietly turn "just this set" into "this exercise, tonight".
     */
    fun forgetThisSet() = held.update { it.copy(forThisSet = null) }

    private companion object {
        /**
         * What "+1:00" adds.
         *
         * A minute rather than the prototype's thirty seconds, and the design it came from now
         * says so (`docs/design/logging-screen.md`). Prescribed rests run from 45 seconds to
         * three minutes, so a lifter reaching for more time between heavy sets is not reaching
         * for half a minute of it; anything finer is what editing the length is for.
         */
        val MORE_REST: Duration = Duration.ofMinutes(1)
    }
}
