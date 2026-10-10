package com.tylergimpel.traintracks.ui.session

import com.tylergimpel.traintracks.domain.RestAlarms
import com.tylergimpel.traintracks.domain.RestCountdown
import com.tylergimpel.traintracks.domain.ScheduledRest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The rest timer as the screen holds it.
 *
 * **The alarm is the source of truth and this is not**, which is exactly what makes it worth
 * testing here: what a fake can prove is that the app asks for the right moment by the right
 * route and puts the countdown away when it should. Whether Android complies needs a phone
 * (#57), and that gap is accepted rather than papered over.
 */
class SessionRestTest {

    @Test
    fun `starting a rest asks to be woken at the end of it and draws the countdown`() {
        val alarms = FakeAlarms()
        val countdown = FakeCountdown()
        val rest = SessionRest(alarms, countdown, fixed)

        rest.start(Duration.ofSeconds(90))

        val endsAt = NOW.plusSeconds(90)
        assertEquals(endsAt, alarms.scheduled?.endsAt)
        assertEquals(endsAt, countdown.showing)
        assertEquals(endsAt, rest.state.value.running?.endsAt)
    }

    @Test
    fun `adding a minute moves the end rather than starting again`() {
        val alarms = FakeAlarms()
        val countdown = FakeCountdown()
        val rest = SessionRest(alarms, countdown, fixed)
        rest.start(Duration.ofSeconds(90))

        rest.addTime()

        // A lifter tapping this is saying the set is not ready yet, not that the last ninety
        // seconds did not happen — so the rest still began when it began.
        assertEquals(NOW, rest.state.value.running?.startedAt)
        assertEquals(NOW.plusSeconds(150), alarms.scheduled?.endsAt)
    }

    @Test
    fun `ending a rest takes the alarm with it`() {
        val alarms = FakeAlarms()
        val rest = SessionRest(alarms, FakeCountdown(), fixed)
        rest.start(Duration.ofSeconds(90))

        rest.end()

        // Otherwise the lifter is three sets further on when the phone goes off in their pocket.
        assertTrue(alarms.cancelled)
        assertNull(rest.state.value.running)
    }

    @Test
    fun `a rest set for one set says nothing about the next`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(), fixed)

        rest.startEditing(Duration.ofSeconds(90))
        rest.changeEditing(Duration.ofSeconds(180))
        rest.thisSetOnly()

        assertEquals(Duration.ofSeconds(180), rest.state.value.forThisSet)
        assertNull("the editor should close behind it", rest.state.value.editing)

        rest.forgetThisSet()
        assertNull(rest.state.value.forThisSet)
    }

    @Test
    fun `editing a rest the app asked for starts from what it asked for`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(), fixed)

        rest.startEditing(Duration.ofSeconds(150))

        // Disagreeing with a number means being shown it first.
        assertEquals(Duration.ofSeconds(150), rest.state.value.editing)
    }

    @Test
    fun `an exercise with nothing prescribed opens the editor at nothing`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(), fixed)

        rest.startEditing(null)

        assertEquals(Duration.ZERO, rest.state.value.editing)
    }

    @Test
    fun `settling a rest on the exercise drops both of the per-set answers`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(), fixed)
        rest.startEditing(Duration.ofSeconds(90))
        rest.thisSetOnly()
        rest.startEditing(Duration.ofSeconds(120))

        rest.settledOnTheExercise()

        // The exercise now carries it, so anything standing in for that is stale.
        assertNull(rest.state.value.editing)
        assertNull(rest.state.value.forThisSet)
    }

    @Test
    fun `what the platform will allow is read again on being asked`() {
        val alarms = FakeAlarms(reliable = false)
        val countdown = FakeCountdown(visible = false)
        val rest = SessionRest(alarms, countdown, fixed)

        // Read once on the way in, because a screen that has never asked cannot warn anybody.
        assertFalse(rest.state.value.alerts.alarmsAreReliable)
        assertFalse(rest.state.value.alerts.countdownVisible)

        alarms.reliable = true
        countdown.visible = true
        rest.recheckAlerts()

        // And again on being asked, because both are revocable and both are granted somewhere
        // else: the lifter walks off to Settings and comes back.
        assertTrue(rest.state.value.alerts.alarmsAreReliable)
        assertTrue(rest.state.value.alerts.countdownVisible)
    }

    @Test
    fun `nothing is asked for before a rest has started`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(visible = false), fixed)

        // An app whose first act is a permission prompt has spent its credibility before showing
        // anything (#88). Opening the screen is not a reason; resting is.
        assertFalse(rest.state.value.askToShowCountdown)
    }

    @Test
    fun `the first rest asks to show its countdown, and only the first`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(visible = false), fixed)

        rest.start(Duration.ofMinutes(2))
        // The moment the reason is obvious: there is a countdown, and nowhere yet to show it.
        assertTrue(rest.state.value.askToShowCountdown)

        rest.asked()
        rest.end()
        rest.start(Duration.ofMinutes(2))

        // Once. A lifter who said no has answered, and the standing line is what says how to
        // change their mind — not a prompt at the start of every rest.
        assertFalse(rest.state.value.askToShowCountdown)
    }

    @Test
    fun `a countdown already allowed is not asked about`() {
        val rest = SessionRest(FakeAlarms(), FakeCountdown(visible = true), fixed)

        rest.start(Duration.ofMinutes(2))

        assertFalse(rest.state.value.askToShowCountdown)
    }

    private class FakeAlarms(var reliable: Boolean = true) : RestAlarms {
        var scheduled: ScheduledRest? = null
        var cancelled = false

        override fun canWakeReliably(): Boolean = reliable

        override fun schedule(rest: ScheduledRest) {
            scheduled = rest
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeCountdown(var visible: Boolean = true) : RestCountdown {
        var showing: Instant? = null

        override fun countDownTo(endsAt: Instant) {
            showing = endsAt
        }

        override fun canBeSeen(): Boolean = visible
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-19T18:00:00Z")
        val fixed: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
