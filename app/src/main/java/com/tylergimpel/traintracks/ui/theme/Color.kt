package com.tylergimpel.traintracks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours of the new look, option 1 "Caution": yellow on warm charcoal, and the same yellow
 * darkened a touch on warm paper (#133).
 *
 * The prototype's roles, named the way it names them so a colour picked off it is found here by
 * the same word, plus [accentText]. Muscle groups keep their own colours apart from these.
 */
@Immutable
data class GymColors(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    // The accent where it is read rather than filled: words and lines. Dark keeps the yellow; on the
    // light page the yellow is too pale to read, so the text colour stands in for it.
    val accentText: Color,
    // The app guessing: the calibrating badge and its line, apart from every other colour so it
    // never reads as a selection or a warning.
    val calib: Color,
    // Something the lifter should know before going on: a conflict in what they asked for.
    val warn: Color,
)

internal val DarkGymColors = GymColors(
    bg = Color(0xFF14130F),
    surface = Color(0xFF1E1C17),
    raised = Color(0xFF2A2720),
    line = Color(0xFF3E392E),
    text = Color(0xFFF5F2EA),
    dim = Color(0xFFABA590),
    accent = Color(0xFFF5C518),
    onAccent = Color(0xFF1A1600),
    good = Color(0xFF6FCF7F),
    accentText = Color(0xFFF5C518),
    calib = Color(0xFFC9A7FF),
    warn = Color(0xFFFFB38A),
)

internal val LightGymColors = GymColors(
    bg = Color(0xFFF6F4EE),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFECE8DD),
    line = Color(0xFFD6CFBC),
    text = Color(0xFF1A1813),
    dim = Color(0xFF696350),
    accent = Color(0xFFEDB800),
    onAccent = Color(0xFF1A1600),
    good = Color(0xFF2E8B3E),
    accentText = Color(0xFF1A1813),
    calib = Color(0xFF6D3FC4),
    warn = Color(0xFFA6461A),
)

/**
 * For the roles Material has no slot for, `good` above all. Everything else is also reachable
 * through [androidx.compose.material3.MaterialTheme.colorScheme], which is what existing screens
 * already read.
 */
val LocalGymColors = staticCompositionLocalOf { DarkGymColors }

/**
 * The roles spread over Material's slots, so that components nobody styled by hand (dialogs,
 * menus, text fields, chips) land on them too.
 *
 * The page is [GymColors.bg] and a card is [GymColors.surface], the prototype's two levels, while
 * fills that have to stand off a card (a text field, a switch track) take
 * [GymColors.raised]. No tint: tonal elevation would wash the accent into every raised surface.
 */
internal fun GymColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        // A selected chip or segment: selection is marked in the accent, as the prototype marks today.
        secondaryContainer = accent,
        onSecondaryContainer = onAccent,
        tertiary = good,
        onTertiary = bg,
        tertiaryContainer = raised,
        onTertiaryContainer = text,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = dim,
        surfaceTint = Color.Transparent,
        surfaceDim = bg,
        surfaceBright = raised,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = raised,
        inverseSurface = text,
        inverseOnSurface = bg,
        outline = line,
        outlineVariant = line,
    )
}
