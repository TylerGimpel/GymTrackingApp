package com.tylergimpel.gymtracker.rest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.tylergimpel.gymtracker.R
import java.time.Instant

/**
 * What a lifter sees and hears about a rest, with the phone in a pocket.
 *
 * Two notifications on one channel. The countdown is **drawn by the system** from a chronometer
 * the app hands it, which is why no foreground service is needed to keep it ticking: the app can
 * be dead and the number keeps going down. The alert is a second notification, posted when the
 * rest ends.
 *
 * **The alert obeys the phone.** Loud when the phone is loud, a buzz when it is set to vibrate,
 * and nothing at all when it is silent — see
 * `docs/adr/0011-the-rest-alert-obeys-the-phone.md`. A gym is a room full of other people, and
 * a timer that shouts through a silent switch is one that gets turned off altogether.
 *
 * The two notifications carry **different ids on purpose**. The countdown is posted silently and
 * ongoing; an alert that merely updated it would be an update to a silenced notification, which
 * Android is entitled to deliver without alerting anybody, and that is exactly the way this
 * feature fails invisibly.
 */
class RestNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Shows the rest counting down, where a lifter can read it without opening anything. */
    fun countDownTo(endsAt: Instant) {
        ensureChannel()
        manager.cancel(REST_OVER)
        val notification = base()
            .setContentTitle(context.getString(R.string.rest_running))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt.toEpochMilli())
            .setOngoing(true)
            // Silent while it counts: the sound belongs to the end of the rest, not the start.
            .setSilent(true)
            .build()

        notifyIfAllowed(COUNTDOWN, notification)
    }

    /**
     * Says the rest is over, as loudly as the phone allows and no louder.
     *
     * Posted under its own id rather than over the countdown, and the countdown is taken away
     * first: a notification that replaces a silenced ongoing one can be delivered without a
     * sound, which is the silent failure this whole feature is written against.
     */
    fun restIsOver() {
        ensureChannel()
        manager.cancel(COUNTDOWN)
        val notification = base()
            .setContentTitle(context.getString(R.string.rest_over))
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(REST_OVER, notification)
    }

    /**
     * Whether any of this would actually reach the lifter.
     *
     * False when notifications are off or the runtime permission was never granted, which on
     * Android 13 and later is the default. Worth asking rather than posting into nothing: the
     * countdown *is* the rest timer as far as a lifter with a phone in their pocket is concerned,
     * and a feature that fails by being invisible has to say so while the app is open.
     */
    fun canBeSeen(): Boolean = manager.areNotificationsEnabled()

    /** Takes both away: the rest was skipped, or the next set is already being logged. */
    fun clear() {
        manager.cancel(COUNTDOWN)
        manager.cancel(REST_OVER)
    }

    private fun base() = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        // A reminder rather than an alarm. The category is what the system reads when it is
        // deciding who may talk through Do Not Disturb, and a rest timer has not earned that.
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            },
        )

    /**
     * Posts, unless the lifter has said no to notifications.
     *
     * Checked rather than caught: on Android 13 and later posting without the runtime permission
     * throws, and a rest timer is not worth crashing a session over. The product tells them what
     * they are missing elsewhere.
     */
    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            manager.notify(id, notification)
        }
    }

    /**
     * The channel, created once and never edited afterwards.
     *
     * A channel is immutable to the app that made it: nothing here can change its sound or its
     * importance once a phone has seen it, and the lifter's own changes always win. That is why
     * the id carries a number — the first version of this channel was an alarm, and the only way
     * to stop a phone that already has it from shouting through a silent switch is to leave it
     * behind and make a new one (`docs/adr/0011-the-rest-alert-obeys-the-phone.md`).
     *
     * High importance, so it arrives in front of the lifter rather than in a drawer. Importance
     * is about prominence; it is the **usage** below that decides whether a silent phone stays
     * silent, and this one is an ordinary notification.
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notifications = context.getSystemService<NotificationManager>() ?: return

        // The alarm-stream channel this replaces. Deleted rather than left behind, so that an
        // upgraded phone does not show two rest timers in its notification settings, one of
        // which no longer does anything.
        notifications.deleteNotificationChannel(WAS_AN_ALARM)
        if (notifications.getNotificationChannel(CHANNEL) != null) return

        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.rest_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.rest_channel_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            // Left to the phone, which is the whole point: a device set to vibrate buzzes, and
            // one set to silent does nothing. Neither is this app's decision to make.
            enableVibration(true)
        }
        notifications.createNotificationChannel(channel)
    }

    companion object {
        /** The channel a lifter sees in their notification settings, and can change at will. */
        const val CHANNEL = "rest-notification"

        /** Its predecessor, on the alarm stream, kept only so it can be deleted. */
        private const val WAS_AN_ALARM = "rest"

        /** The countdown, replaced in place every time the end of the rest moves. */
        private const val COUNTDOWN = 1

        /** The alert, its own notification so that it is never a silent one being updated. */
        private const val REST_OVER = 2
    }
}
