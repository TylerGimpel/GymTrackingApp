package com.tylergimpel.gymtracker.rest

import android.app.NotificationManager
import android.media.AudioAttributes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * That the rest alert asks to be an ordinary notification, and never an alarm.
 *
 * A deliberately reversed decision, and one that reverts easily by accident: `USAGE_ALARM` is
 * what every article about notification sounds reaches for, and it is what this channel used to
 * be. It survives a silent switch, which is exactly what a room full of other people does not
 * want (`docs/adr/0011-the-rest-alert-obeys-the-phone.md`). Asserted here rather than trusted,
 * because the symptom of getting it wrong is somebody else's gym being disturbed rather than
 * anything this project would ever see.
 */
@RunWith(AndroidJUnit4::class)
class RestChannelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val notifications: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Before
    fun createTheChannel() {
        // Posting is what creates it, and posting is allowed to do nothing: whether the runtime
        // permission was granted is not this test's business.
        RestNotifications(context).countDownTo(Instant.now().plusSeconds(60))
    }

    @Test
    fun theRestChannelIsANotificationRatherThanAnAlarm() {
        val channel = notifications.getNotificationChannel(RestNotifications.CHANNEL)
        assertNotNull("the rest channel was never created", channel)

        val usage = channel.audioAttributes?.usage
        assertNotEquals(
            "the rest alert must not talk through a silent phone",
            AudioAttributes.USAGE_ALARM,
            usage,
        )
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, usage)
    }

    @Test
    fun itStillArrivesInFrontOfTheLifter() {
        val channel = notifications.getNotificationChannel(RestNotifications.CHANNEL)

        // Importance is prominence, not permission to override the phone. A rest that ends in a
        // drawer is one the lifter finds by noticing the gym has gone quiet.
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }
}
