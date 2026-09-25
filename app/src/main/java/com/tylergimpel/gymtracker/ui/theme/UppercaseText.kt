package com.tylergimpel.gymtracker.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Words drawn in capitals but read as written.
 *
 * Compose has no text transform, so the capitals have to be in the string; kept out of the
 * semantics, because a screen reader spells a short word in capitals out letter by letter, and
 * because what the screen says is what a test looks for.
 */
@Composable
internal fun UppercaseText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
    )
}
