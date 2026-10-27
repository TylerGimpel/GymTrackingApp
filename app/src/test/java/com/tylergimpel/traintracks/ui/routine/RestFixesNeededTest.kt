package com.tylergimpel.traintracks.ui.routine

import com.tylergimpel.traintracks.ui.RestFix
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of the rest timer's permissions setup's last page asks for (#161). */
class RestFixesNeededTest {

    @Test
    fun `a phone that already allows both skips the page`() {
        assertEquals(emptyList<RestFix>(), restFixesNeeded(alarmsAreReliable = true, countdownCanBeSeen = true))
    }

    @Test
    fun `only what is missing is asked for, countdown first`() {
        assertEquals(listOf(RestFix.EXACT_ALARMS), restFixesNeeded(alarmsAreReliable = false, countdownCanBeSeen = true))
        assertEquals(listOf(RestFix.NOTIFICATIONS), restFixesNeeded(alarmsAreReliable = true, countdownCanBeSeen = false))
        assertEquals(
            listOf(RestFix.NOTIFICATIONS, RestFix.EXACT_ALARMS),
            restFixesNeeded(alarmsAreReliable = false, countdownCanBeSeen = false),
        )
    }
}
