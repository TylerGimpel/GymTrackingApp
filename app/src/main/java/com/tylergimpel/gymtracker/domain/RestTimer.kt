package com.tylergimpel.gymtracker.domain

import java.time.Duration
import java.time.Instant

/**
 * A rest to be woken from: the moment it ends, and how hard the app has to try to be on time.
 *
 * The deadline is a moment rather than a countdown, because the countdown is the phone's job.
 * Nothing in this app can be trusted to still be running in three minutes — the process can be
 * killed, the device can doze — so what gets handed to the platform is "wake me at this instant",
 * and the display is drawn from the same instant by something that survives the app.
 */
data class ScheduledRest(
    val endsAt: Instant,
    /**
     * Whether this rest is long enough to need the alarm-clock path.
     *
     * The strongest thing an app can ask for, and reserved for the case that needs it: a long
     * rest is long enough for the device to doze off, and an exact alarm that is merely
     * *allowed* while idle is still subject to quotas that an alarm clock is exempt from. A short
     * rest does not need it and would show a clock icon in the status bar for ninety seconds.
     */
    val byAlarmClock: Boolean,
)

/** When a rest of [length] started at [startedAt] ends, and how it should be asked for. */
fun restEnding(startedAt: Instant, length: Duration): ScheduledRest = ScheduledRest(
    endsAt = startedAt.plus(length),
    byAlarmClock = length >= LONG_ENOUGH_TO_DOZE,
)

/**
 * A rest with more time on it.
 *
 * Added to the end rather than restarted, because a lifter tapping "+1:00" is saying the set is
 * not ready yet, not that the last two minutes did not happen.
 */
fun ScheduledRest.plus(extra: Duration): ScheduledRest = copy(endsAt = endsAt.plus(extra))

/**
 * How long a rest has to be before the device might doze through an ordinary exact alarm.
 *
 * Judgement rather than measurement: idle mode can begin within a few minutes of a screen going
 * off in a pocket, and the cost of escalating early is a clock icon while the cost of escalating
 * late is a missed set.
 */
private val LONG_ENOUGH_TO_DOZE: Duration = Duration.ofMinutes(3)

/**
 * What can wake a lifter at the end of a rest.
 *
 * An interface because the platform is the untestable part: a fake can prove the app asked for
 * the right moment by the right route, which is all a test can honestly claim. **It cannot prove
 * Android complies** — permission denied, idle quotas, an OEM process killer, the restricted
 * bucket after days unused. Those need a real device, ideally an aggressive one.
 */
interface RestAlarms {

    /**
     * Whether the app can currently schedule an alarm that will actually be on time.
     *
     * False on a fresh install on recent Android, and again after a backup restore, because
     * scheduling exact alarms is denied by default. The fallback is unbounded lateness, which is
     * the worst kind of failure this app has: a timer that silently never fires is discovered by
     * missing a set rather than by an error. So the answer is surfaced in the product.
     */
    fun canWakeReliably(): Boolean

    /** Asks to be woken when [rest] ends, replacing any rest already scheduled. */
    fun schedule(rest: ScheduledRest)

    /** Drops whatever was scheduled: the lifter skipped the rest, or the set is already done. */
    fun cancel()
}
