package com.tylergimpel.gymtracker

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app ships its exercise bank inside the APK and has nothing to fetch, so it declares no
 * platform permission at all — network least of all.
 *
 * Asserted rather than assumed, because a permission can arrive from a library's manifest without
 * anyone editing this project's own.
 */
@RunWith(AndroidJUnit4::class)
class NoNetworkPermissionTest {

    @Test
    fun theAppDeclaresNoPlatformPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .filter { it.startsWith("android.permission.") }

        assertEquals(emptyList<String>(), declared)
    }
}
