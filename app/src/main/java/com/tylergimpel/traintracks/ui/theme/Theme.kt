package com.tylergimpel.traintracks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The new look (#133), in both themes. No dynamic colour: the wallpaper's palette would replace the
 * accent the whole style is built around.
 */
@Composable
fun TrainTracksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkGymColors else LightGymColors
    CompositionLocalProvider(LocalGymColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = GymTypography,
            shapes = GymShapes,
            content = content,
        )
    }
}
