package com.tylergimpel.gymtracker.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.ui.icons.AppIcons
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors

/** The four places the app is, in the order the bottom nav shows them (#133). */
enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, AppIcons.Today),
    CALENDAR(R.string.tab_calendar, AppIcons.Calendar),
    ROUTINE(R.string.tab_routine, AppIcons.Routine),
    MORE(R.string.tab_more, AppIcons.More),
}

/**
 * The bottom nav: 68 tall, an icon over a word, and the tab you are on marked by an accent bar
 * along its top edge with its icon in the accent (#133).
 */
@Composable
fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = LocalGymColors.current
    Column(Modifier.background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = colors.line)
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { tab ->
                val current = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 68.dp)
                        .selectable(selected = current, role = Role.Tab, onClick = { onSelect(tab) })
                        .testTag("tab-${tab.name.lowercase()}"),
                ) {
                    if (current) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.accent))
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (current) colors.accent else colors.dim,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = if (current) colors.text else colors.dim,
                        )
                    }
                }
            }
        }
    }
}
