package com.tylergimpel.traintracks.ui.routine

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.rest.RestAlarmScheduler
import com.tylergimpel.traintracks.rest.RestNotifications
import com.tylergimpel.traintracks.ui.RestFix
import com.tylergimpel.traintracks.ui.icons.AppIcons
import com.tylergimpel.traintracks.ui.settingsFor
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.MainButton
import com.tylergimpel.traintracks.ui.theme.UppercaseText

/**
 * What the rest timer still cannot do on this phone, read from the platform now.
 *
 * Empty when it can do everything, which is when setup skips the page altogether.
 */
internal fun restFixesNeeded(alarmsAreReliable: Boolean, countdownCanBeSeen: Boolean): List<RestFix> = buildList {
    if (!countdownCanBeSeen) add(RestFix.NOTIFICATIONS)
    if (!alarmsAreReliable) add(RestFix.EXACT_ALARMS)
}

/**
 * Setup's last page (#161): the two things the rest timer needs, each with Allow, before the
 * first workout rather than in the middle of it.
 *
 * Only what is still missing is shown, the page is skipped when nothing is, and **Skip always
 * works**: the workout still says so, and Settings still offers both, for anyone who passes.
 * Read again whenever the lifter comes back, because the alarm one is granted on a settings page.
 */
@Composable
internal fun RestPermissionsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    fun read() = restFixesNeeded(
        alarmsAreReliable = RestAlarmScheduler(context).canWakeReliably(),
        countdownCanBeSeen = RestNotifications(context).canBeSeen(),
    )

    // What the page opened with, so a row that has just been allowed stays on screen as done.
    val asked = rememberSaveable { read().map(RestFix::name) }
    var missing by rememberSaveable { mutableStateOf(asked) }
    var promptedForNotifications by rememberSaveable { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) missing = read().map(RestFix::name)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        missing = read().map(RestFix::name)
    }

    RestPermissions(
        fixes = asked.map(RestFix::valueOf),
        missing = missing.map(RestFix::valueOf).toSet(),
        onAllow = { fix ->
            // The system asks about notifications once or twice and then never again, so the
            // first tap asks and any after it goes where the answer can still be changed.
            if (fix == RestFix.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !promptedForNotifications) {
                promptedForNotifications = true
                askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(settingsFor(fix, context))
            }
        },
        onDone = onDone,
    )
}

/** The page itself, given what to show: drawn without the platform so it can be tested. */
@Composable
internal fun RestPermissions(
    fixes: List<RestFix>,
    missing: Set<RestFix>,
    onAllow: (RestFix) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalGymColors.current
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UppercaseText(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, color = colors.text),
            )
            Text(stringResource(R.string.permissions_body), color = colors.dim, fontSize = 17.sp)
            Spacer(Modifier.size(6.dp))
            fixes.forEach { fix ->
                PermissionRow(
                    icon = if (fix == RestFix.NOTIFICATIONS) AppIcons.Bell else AppIcons.Alarm,
                    title = stringResource(if (fix == RestFix.NOTIFICATIONS) R.string.permissions_countdown else R.string.permissions_alarm),
                    sub = stringResource(if (fix == RestFix.NOTIFICATIONS) R.string.permissions_countdown_sub else R.string.permissions_alarm_sub),
                    allowed = fix !in missing,
                    onAllow = { onAllow(fix) },
                    tag = "$PERMISSION_ROW-${fix.name}",
                )
            }
        }
        HorizontalDivider(color = colors.line)
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            MainButton(
                stringResource(if (missing.isEmpty()) R.string.setup_done else R.string.permissions_skip),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().testTag(PERMISSIONS_DONE),
            )
        }
    }
}

/** One thing the timer needs: what it is for, and Allow, or a tick once it is allowed. */
@Composable
private fun PermissionRow(icon: ImageVector, title: String, sub: String, allowed: Boolean, onAllow: () -> Unit, tag: String) {
    val colors = LocalGymColors.current
    Surface(shape = ControlShape, color = colors.raised, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier.heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = colors.text, fontSize = 19.sp, style = MaterialTheme.typography.titleMedium)
                Text(sub, color = colors.dim, fontSize = 15.sp)
            }
            if (allowed) {
                Icon(AppIcons.Check, contentDescription = stringResource(R.string.permissions_allowed), tint = colors.accentText, modifier = Modifier.size(28.dp))
            } else {
                Surface(
                    onClick = onAllow,
                    shape = ControlShape,
                    color = colors.accent,
                    contentColor = colors.onAccent,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        UppercaseText(stringResource(R.string.permissions_allow), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                    }
                }
            }
        }
    }
}

const val PERMISSION_ROW = "permission-row"
const val PERMISSIONS_DONE = "permissions-done"
