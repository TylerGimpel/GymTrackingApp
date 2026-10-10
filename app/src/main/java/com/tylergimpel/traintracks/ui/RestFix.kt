package com.tylergimpel.traintracks.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The two things that can stop a rest reaching a lifter, each of which the lifter can fix.
 *
 * Shared by the session screen, which says so where a lifter will read it mid-workout, and by
 * Settings, where they look when they want to know before a rest goes quiet on them.
 */
enum class RestFix { EXACT_ALARMS, NOTIFICATIONS }

/**
 * Where to send a lifter to let a rest reach them.
 *
 * Settings rather than a second permission dialog: the alarm one has no dialog at all, and the
 * notification one is shown at most once by the system, after which the only honest route is the
 * page that can still change the answer.
 */
internal fun settingsFor(fix: RestFix, context: Context): Intent = when (fix) {
    RestFix.EXACT_ALARMS -> Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    )

    RestFix.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
