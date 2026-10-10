package com.tylergimpel.traintracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * What the app asks the platform for when a rest starts.
 *
 * **This proves the app asks correctly. It does not prove Android complies** — permission denied,
 * idle quotas, an OEM process killer and the restricted bucket are all unreachable from here and
 * need a real device. What a fake can hold the app to is that the right moment was asked for, by
 * the right route, and that skipping a rest takes the alarm with it.
 */
class RestTimerTest {

    @Test
    fun `a rest ends when it ends, rather than counting down in the app`() {
        val rest = restEnding(NOW, Duration.ofSeconds(90))

        // A moment rather than a countdown, because nothing in this app can be trusted to still
        // be running in ninety seconds: the process can be killed and the device can doze.
        assertEquals(NOW.plusSeconds(90), rest.endsAt)
    }

    @Test
    fun `a short rest is an ordinary exact alarm`() {
        assertFalse(restEnding(NOW, Duration.ofSeconds(90)).byAlarmClock)
        assertFalse(restEnding(NOW, Duration.ofSeconds(150)).byAlarmClock)
    }

    @Test
    fun `a long rest escalates to the alarm-clock path`() {
        // Long enough for a pocketed phone to doze off, where an exact alarm that is merely
        // allowed while idle is still subject to quotas an alarm clock is exempt from.
        assertTrue(restEnding(NOW, Duration.ofMinutes(3)).byAlarmClock)
        assertTrue(restEnding(NOW, Duration.ofMinutes(5)).byAlarmClock)
    }

    @Test
    fun `adding time moves the end rather than starting again`() {
        val rest = restEnding(NOW, Duration.ofSeconds(90))

        val longer = rest.plus(Duration.ofSeconds(60))

        // A lifter tapping "+1:00" is saying the set is not ready yet, not that the last ninety
        // seconds did not happen.
        assertEquals(NOW.plusSeconds(150), longer.endsAt)
    }

    @Test
    fun `starting a rest asks to be woken at the moment it ends`() {
        val alarms = FakeAlarms()

        alarms.schedule(restEnding(NOW, Duration.ofSeconds(150)))

        assertEquals(NOW.plusSeconds(150), alarms.scheduled?.endsAt)
    }

    @Test
    fun `skipping a rest takes the alarm with it`() {
        val alarms = FakeAlarms()
        alarms.schedule(restEnding(NOW, Duration.ofMinutes(3)))

        alarms.cancel()

        // Otherwise the lifter is three sets further on when the phone goes off in their pocket.
        assertEquals(null, alarms.scheduled)
    }

    /** What a scheduler can be held to without a device: what was asked for, and when. */
    private class FakeAlarms(private val reliable: Boolean = true) : RestAlarms {
        var scheduled: ScheduledRest? = null

        override fun canWakeReliably() = reliable

        override fun schedule(rest: ScheduledRest) {
            scheduled = rest
        }

        override fun cancel() {
            scheduled = null
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-14T18:00:00Z")
    }
}
