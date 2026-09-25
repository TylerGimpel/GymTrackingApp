package com.tylergimpel.gymtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ImportPreview
import com.tylergimpel.gymtracker.domain.NameDecision
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Unmatched
import com.tylergimpel.gymtracker.ui.exercise.ExercisePicker
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A file read and waiting on the lifter, with nothing yet written (#103). */
data class Previewing(
    val records: NeutralImport,
    /** What going ahead will bring in, and the names needing a decision first. */
    val preview: ImportPreview,
    /** What was decided for each unmatched name, which starts as "my own" for every one (#99). */
    val decisions: Map<String, NameDecision>,
    /** The bank, for choosing what a name is the same as. */
    val bank: List<Exercise>,
    /** The name whose "same as" is being chosen, while the picker is open. */
    val choosingFor: String? = null,
)

/**
 * What an import is about to do, agreed to before any of it happens (#103), and the one-time
 * decision about names FitNotes used that the bank does not have (#99).
 *
 * **Nothing unknown is agreed to.** The date range, sessions and sets come first, then what will be
 * left out and that volume from before the import is approximate, and then the size of the naming
 * chore, so the lifter knows what they are committing to before they commit.
 *
 * **Every name starts as a User-authored exercise** — for a gym full of machines the bank has
 * never heard of, that is the right answer, not a failure — and any can be said to be the same as
 * one already here instead. Decided once per name, never per set. **Nothing is guessed**: silently
 * mapping `Hammer Strength Row` onto `Barbell Row` would fuse two lifts' histories.
 */
@Composable
fun ImportPreviewScreen(
    previewing: Previewing,
    insets: PaddingValues,
    onDecide: (String, NameDecision) -> Unit,
    onChooseSameAs: (String?) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    val preview = previewing.preview
    Column(modifier = Modifier.fillMaxSize().padding(insets)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            Text(
                text = if (preview.from != null && preview.to != null) {
                    stringResource(
                        R.string.preview_what,
                        preview.sessions,
                        preview.sets,
                        preview.from.format(dates),
                        preview.to.format(dates),
                    )
                } else {
                    stringResource(R.string.preview_nothing)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (preview.alreadyHere > 0) {
                Text(
                    text = stringResource(R.string.preview_already_here, preview.alreadyHere),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (preview.unreadable > 0) {
                Text(
                    text = stringResource(R.string.preview_unreadable, preview.unreadable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (preview.sets > 0 && !previewing.records.source.volumeIsReliable) {
                Text(
                    text = stringResource(R.string.import_volume_approximate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.unmatched.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.mapping_count, preview.unmatched.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.mapping_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(preview.unmatched, key = Unmatched::name) { name ->
                val decision = previewing.decisions[name.name] ?: NameDecision.CreateOwn
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.mapping_name, name.name, name.sets),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = decision == NameDecision.CreateOwn,
                            onClick = { onDecide(name.name, NameDecision.CreateOwn) },
                            label = { Text(stringResource(R.string.mapping_own)) },
                        )
                        FilterChip(
                            selected = decision is NameDecision.SameAs,
                            onClick = { onChooseSameAs(name.name) },
                            label = {
                                Text(
                                    (decision as? NameDecision.SameAs)
                                        ?.let { chosen -> previewing.bank.firstOrNull { it.id == chosen.exerciseId }?.name }
                                        ?.let { stringResource(R.string.mapping_same_as_chosen, it) }
                                        ?: stringResource(R.string.mapping_same_as),
                                )
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.mapping_cancel)) }
            Button(onClick = onImport, enabled = preview.sets > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.mapping_import))
            }
        }
    }

    previewing.choosingFor?.let { name ->
        // The same picker the rest of the app uses, over the whole bank, with nothing to add:
        // the name is either the lifter's own exercise or one already here.
        ExercisePicker(
            title = stringResource(R.string.mapping_pick_title, name),
            bank = previewing.bank,
            equipment = emptySet(),
            onPick = { onDecide(name, NameDecision.SameAs(it.id)) },
            onAddNew = null,
            onCancel = { onChooseSameAs(null) },
        )
    }
}
