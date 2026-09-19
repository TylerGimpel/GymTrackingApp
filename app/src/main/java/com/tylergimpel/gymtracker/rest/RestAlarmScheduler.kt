package com.tylergimpel.gymtracker.rest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.tylergimpel.gymtracker.domain.RestAlarms
import com.tylergimpel.gymtracker.domain.ScheduledRest

/**
 * The rest timer as Android does it: an alarm, and a notification the system draws.
 *
 * **The alarm is the source of truth.** Not a coroutine, not a foreground service — a foreground
 * service would not even help, because the short-service cap is around three minutes and idle
 * mode ignores wake locks. The app may not be running when the rest ends; the alarm will be.
 *
 * Two routes, chosen by how long the rest is. An ordinary rest gets `setExactAndAllowWhileIdle`,
 * which is exact and permitted during doze. A long one gets `setAlarmClock`, the strongest thing
 * an app can ask for and the only one exempt from idle quotas outright — at the price of a clock
 * icon in the status bar, which is why short rests do not use it.
 *
 * **Counted on elapsed realtime, not on the wall clock.** A rest is ninety seconds from now; it
 * is not a time of day. A phone that crosses a timezone or has its clock corrected by the network
 * mid-session would otherwise move a timer that has already started.
 */
class RestAlarmScheduler(private val context: Context) : RestAlarms {

    private val alarms = requireNotNull(context.getSystemService<AlarmManager>())

    /**
     * Whether an alarm asked for now would actually be on time.
     *
     * Denied by default on a fresh install on Android 12 and later, and again after a backup
     * restore. The app asks rather than assumes, and says so when the answer is no: the fallback
     * is unbounded lateness, and a timer that silently never fires is discovered by missing a set.
     */
    override fun canWakeReliably(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun schedule(rest: ScheduledRest) {
        val ringing = pendingIntent(FLAG_REPLACE)
        val endsAt = rest.endsAt.toEpochMilli()
        val inThisLong = endsAt - System.currentTimeMillis()

        when {
            // Without the permission there is no honest way to be exact, and a late alarm is
            // worse than an inexact one that at least admits what it is. The product says so
            // elsewhere; this is the fallback rather than the plan.
            !canWakeReliably() -> alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + inThisLong,
                ringing,
            )

            // The one wall-clock route, because it has to be: what an alarm clock shows in the
            // status bar is a time of day, so that is the term it has to be asked for in.
            rest.byAlarmClock ->
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(endsAt, showRest()), ringing)

            else -> alarms.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + inThisLong,
                ringing,
            )
        }
    }

    override fun cancel() {
        // A no-create lookup: if nothing is scheduled there is nothing to cancel, and creating an
        // intent in order to cancel it is how a pending intent leaks.
        pendingIntentOrNull(FLAG_LOOK_ONLY)?.let(alarms::cancel)
        RestNotifications(context).clear()
    }

    /** What tapping the alarm-clock icon opens: the session the rest belongs to. */
    private fun showRest(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST,
        context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pendingIntent(flags: Int): PendingIntent = requireNotNull(pendingIntentOrNull(flags))

    private fun pendingIntentOrNull(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, RestOverReceiver::class.java),
        flags,
    )

    private companion object {
        /** One rest runs at a time, so one request code, and a new one replaces the old. */
        const val REQUEST = 1

        const val FLAG_REPLACE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        const val FLAG_LOOK_ONLY = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    }
}
