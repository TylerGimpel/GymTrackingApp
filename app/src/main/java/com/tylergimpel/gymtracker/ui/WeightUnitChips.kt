package com.tylergimpel.gymtracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.domain.WeightUnit

/**
 * Which unit the number beside these is being read in.
 *
 * One row wherever a weight is entered, because what it does is the same everywhere: it rewrites a
 * reading and never touches what was lifted or weighed. Where it is *remembered* differs — a set's
 * chip settles the exercise, because a machine carries its label whoever walks up to it, and a
 * weigh-in's settles the screen — so that decision stays with the caller in [onReadIn].
 *
 * [trailing] is for a chip that belongs in the same scrolling row, such as the set-only override.
 */
@Composable
internal fun WeightUnitChips(
    selected: WeightUnit,
    onReadIn: (WeightUnit) -> Unit,
    modifier: Modifier = Modifier,
    /** A test tag for each chip, where a screen holds more than one row of these. */
    chipTag: (WeightUnit) -> String? = { null },
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeightUnit.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == selected,
                onClick = { onReadIn(candidate) },
                label = { Text(stringResource(candidate.label)) },
                modifier = chipTag(candidate)?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
        trailing()
    }
}
