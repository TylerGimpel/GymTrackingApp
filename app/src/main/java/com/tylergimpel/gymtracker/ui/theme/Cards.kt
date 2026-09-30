package com.tylergimpel.gymtracker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.gymtracker.ui.icons.AppIcons

/** A card of the new look: the surface colour, a 1px line round it, 10 corners (#133). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGymColors.current
    val shape = CardShape
    val border = BorderStroke(1.dp, colors.line)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = colors.surface, border = border) {
            Column(content = content)
        }
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = colors.surface, border = border) {
            Column(content = content)
        }
    }
}

/** The small uppercase label over a section: 13, letter-spaced, dim. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    UppercaseText(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(color = LocalGymColors.current.dim),
        modifier = modifier,
    )
}

/**
 * One row of a card: at least 64 tall, a title and an optional dim line under it, and whatever
 * sits at the end. A chevron by default where the row opens something.
 */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = if (onClick != null) { { Chevron() } } else null,
) {
    val colors = LocalGymColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.text)
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = colors.dim, style = MaterialTheme.typography.bodyMedium)
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun Chevron() {
    Icon(AppIcons.Chevron, contentDescription = null, tint = LocalGymColors.current.dim, modifier = Modifier.size(22.dp))
}

/** The line between two rows of a card. */
@Composable
fun RowDivider() {
    HorizontalDivider(color = LocalGymColors.current.line)
}
