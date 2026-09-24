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
import com.tylergimpel.gymtracker.domain.NameDecision
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Unmatched
import com.tylergimpel.gymtracker.ui.exercise.ExercisePicker

/** An import waiting on the lifter to say what its unmatched names mean (#99). */
data class Mapping(
    val records: NeutralImport,
    /** Every name with no exact match, once each, most history first. */
    val unmatched: List<Unmatched>,
    /** What was decided for each, which starts as "my own" for every one. */
    val decisions: Map<String, NameDecision>,
    /** The bank, for choosing what a name is the same as. */
    val bank: List<Exercise>,
    /** The name whose "same as" is being chosen, while the picker is open. */
    val choosingFor: String? = null,
)

/**
 * The one-time decision about names FitNotes used that the bank does not have (#99).
 *
 * **The size of the chore comes first**, before any decision, so the lifter knows what they are
 * committing to. Every name starts as an exercise of their own — for a gym full of machines the
 * bank has never heard of, that is the right answer, not a failure — and any can be said to be
 * the same as one already here instead. Decided once per name, never per set.
 *
 * **Nothing is guessed.** No name is put under an exercise that merely looks close: silently
 * mapping `Hammer Strength Row` onto `Barbell Row` would fuse two lifts' histories.
 */
@Composable
fun MappingScreen(
    mapping: Mapping,
    insets: PaddingValues,
    onDecide: (String, NameDecision) -> Unit,
    onChooseSameAs: (String?) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(insets)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.mapping_count, mapping.unmatched.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.mapping_why),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(mapping.unmatched, key = Unmatched::name) { name ->
                val decision = mapping.decisions[name.name] ?: NameDecision.CreateOwn
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
                                        ?.let { chosen -> mapping.bank.firstOrNull { it.id == chosen.exerciseId }?.name }
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
            Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.mapping_import))
            }
        }
    }

    mapping.choosingFor?.let { name ->
        // The same picker the rest of the app uses, over the whole bank, with nothing to add:
        // the name is either the lifter's own exercise or one already here.
        ExercisePicker(
            title = stringResource(R.string.mapping_pick_title, name),
            bank = mapping.bank,
            equipment = emptySet(),
            onPick = { onDecide(name, NameDecision.SameAs(it.id)) },
            onAddNew = null,
            onCancel = { onChooseSameAs(null) },
        )
    }
}
