package com.tylergimpel.gymtracker.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Every screen's top bar: 52 tall, directly under the status bar, the title left-aligned in
 * uppercase condensed type (#133).
 *
 * Material's own bar is 64 and centres a smaller title in it, which is what read as the bar sitting
 * too low. The page colour rather than a surface, so the bar is part of the screen rather than a
 * strip laid over it.
 *
 * [subtitle] is for the few screens that say one more thing under the title (a count, a date); it
 * is drawn small and dim so the bar keeps its height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                UppercaseText(title, style = TopBarTitle, maxLines = 1)
                if (subtitle != null) {
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        subtitle()
                    }
                }
            }
        },
        actions = actions,
        expandedHeight = TopBarHeight,
        windowInsets = WindowInsets.statusBars,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

val TopBarHeight = 52.dp
