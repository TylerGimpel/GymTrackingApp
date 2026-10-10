package com.tylergimpel.traintracks

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exactly which platform permissions this app asks for, and which it must never ask for.
 *
 * Asserted rather than assumed, because a permission can arrive from a library's manifest without
 * anyone editing this project's own — and because two of the ones it could plausibly acquire
 * would put the app's release at risk.
 */
@RunWith(AndroidJUnit4::class)
class DeclaredPermissionsTest {

    private val declared: List<String> = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .let { context ->
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .filter { it.startsWith("android.permission.") }
        }

    @Test
    fun theAppAsksForTheThingsTheRestTimerNeedsAndNothingElse() {
        // All askable, all for the timer: schedule it, show it, and be felt through a pocket.
        // No network permission, because the exercise bank ships inside the APK and there is
        // nothing to fetch (ADR 0002).
        assertEquals(
            listOf(
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.VIBRATE",
            ),
            declared.sorted(),
        )
    }

    @Test
    fun neitherOfTheTwoStoreRiskyPermissionsIsDeclared() {
        // USE_EXACT_ALARM schedules exact alarms without asking, and
        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS asks to sit outside Doze. Both risk Play rejection
        // for an app that is not an alarm clock, and both are exactly what somebody reaches for
        // when a timer fires late. The honest answer is to tell the lifter, which the app does.
        assertFalse("android.permission.USE_EXACT_ALARM" in declared)
        assertFalse("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in declared)
    }
}
