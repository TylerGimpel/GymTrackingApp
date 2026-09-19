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
 * Two notifications and one channel. The countdown is **drawn by the system** from a chronometer
 * the app hands it, which is why no foreground service is needed to keep it ticking: the app can
 * be dead and the number keeps going down. The alert is the same channel firing with a sound.
 *
 * **The channel is an alarm.** High importance, the alarm category, and alarm audio attributes,
 * so that it is heard in a loud gym by somebody who has their phone on silent — which is most
 * people, most of the time, and the single most likely way for this feature to fail quietly.
 */
class RestNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Shows the rest counting down, where a lifter can read it without opening anything. */
    fun countDownTo(endsAt: Instant) {
        ensureChannel()
        val notification = base()
            .setContentTitle(context.getString(R.string.rest_running))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt.toEpochMilli())
            .setOngoing(true)
            // Silent while it counts: the sound belongs to the end of the rest, not the start.
            .setSilent(true)
            .build()

        notifyIfAllowed(notification)
    }

    /** Says the rest is over, loudly enough to be heard in a gym. */
    fun restIsOver() {
        ensureChannel()
        val notification = base()
            .setContentTitle(context.getString(R.string.rest_over))
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(notification)
    }

    /** Takes the countdown away: the rest was skipped, or the next set is already being logged. */
    fun clear() = manager.cancel(REST)

    private fun base() = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
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
    private fun notifyIfAllowed(notification: android.app.Notification) {
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            manager.notify(REST, notification)
        }
    }

    /**
     * The channel, created once and never downgraded.
     *
     * Android will not let an app raise a channel's importance after the fact, so getting this
     * right the first time is the whole game: alarm usage, alarm category, and the device's alarm
     * sound rather than its notification sound.
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = context.getSystemService<NotificationManager>()?.getNotificationChannel(CHANNEL)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.rest_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.rest_channel_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "rest"

        /** One rest at a time, so one notification that the countdown and the alert share. */
        const val REST = 1
    }
}
