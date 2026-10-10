package com.tylergimpel.traintracks.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The buttons of the new look (#133): squared, at least 60 tall, and labelled in uppercase
 * condensed type.
 *
 * Wrappers rather than theme values because Material's buttons take neither their height nor their
 * shape from the theme: both are fixed in the component, a 40-tall pill.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        modifier = modifier.heightIn(min = ButtonHeight),
    ) {
        UppercaseText(text, style = ButtonLabel)
    }
}

/** The quieter of the two: a raised fill, the prototype's ghost button, rather than the accent. */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.heightIn(min = ButtonHeight),
    ) {
        UppercaseText(text, style = ButtonLabel)
    }
}

/** The one button a screen is for, such as Start workout: 72 tall, the label a size up. */
@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        modifier = modifier.heightIn(min = MainButtonHeight),
    ) {
        UppercaseText(text, style = MainButtonLabel)
    }
}

val ButtonHeight = 60.dp
val MainButtonHeight = 72.dp

/**
 * Material's text button with the text in the text colour rather than the accent, used everywhere
 * in its place by importing this one.
 *
 * The accent is a fill: yellow text on the light page is barely there, and the prototype never
 * writes in it. Same name and parameters as Material's, so a screen switches by its import alone.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding,
        content = content,
    )
}
