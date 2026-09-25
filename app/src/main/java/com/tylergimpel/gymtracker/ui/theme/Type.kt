package com.tylergimpel.gymtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tylergimpel.gymtracker.R

/**
 * Bundled rather than downloaded: the app works with no network, and a font that arrives late
 * would redraw every screen the first time it is opened. Both are SIL OFL; the licence ships in
 * `assets/licenses`.
 */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/**
 * Condensed for anything read at a glance — headings, titles, big numbers, buttons — and Barlow at
 * 17 for everything read as text (#133).
 *
 * Compose has no text transform, so the uppercase the prototype puts on headings and buttons is
 * applied where the words are, by [UppercaseText].
 */
val GymTypography = Typography(
    displayLarge = condensed(64, 1.0),
    displayMedium = condensed(56, 0.95),
    displaySmall = condensed(40, 1.05),
    headlineLarge = condensed(34, 1.05),
    headlineMedium = condensed(30, 1.05),
    headlineSmall = condensed(26, 1.1),
    titleLarge = condensed(24, 1.1),
    titleMedium = body(18, FontWeight.SemiBold),
    titleSmall = body(16, FontWeight.SemiBold),
    bodyLarge = body(17, FontWeight.Normal),
    bodyMedium = body(16, FontWeight.Normal),
    bodySmall = body(14, FontWeight.Normal),
    // Small buttons and chips: body type made firmer. The big buttons are condensed, in [ButtonLabel].
    labelLarge = body(17, FontWeight.SemiBold),
    // The small uppercase labels: wide spacing is what lets them sit small and still be read.
    labelMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.em,
    ),
    labelSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
)

/** The top bar's title, larger than any Material slot gives it: 28, uppercase, letter-spaced. */
internal val TopBarTitle = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.03.em,
)

/** Every big button's label: condensed and a size above the text around it. */
internal val ButtonLabel = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.02.em,
)

/** The main button's label, a size up from every other button's. */
internal val MainButtonLabel = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.02.em,
)

private fun condensed(size: Int, lineHeight: Double) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = 0.01.em,
)

private fun body(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.4).sp,
)
