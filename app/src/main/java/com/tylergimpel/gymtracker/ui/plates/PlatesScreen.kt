package com.tylergimpel.gymtracker.ui.plates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.PlatePair
import com.tylergimpel.gymtracker.domain.WeightUnit
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.ui.label

/**
 * The plates in the room.
 *
 * **One inventory, and the only place it is edited.** Every barbell recommendation is rounded to
 * what these can build, and the plate line under a set says how to build it, so a change here
 * changes what the next set asks for with nothing to keep in step.
 *
 * Pairs rather than plates throughout, because a bar loaded at one end is not a lift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatesScreen(onBack: () -> Unit, viewModel: PlatesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.plates_title),
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.plates_done)) }
                },
            )
        },
    ) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
            item {
                Text(
                    text = stringResource(R.string.plates_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                OutlinedTextField(
                    value = state.barText,
                    onValueChange = viewModel::typeBar,
                    label = { Text(stringResource(R.string.plates_bar)) },
                    suffix = { Text(stringResource(state.unit.label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = viewModel::saveBar,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.session_save))
                }
                HorizontalDivider()
            }

            items(state.pairs, key = { it.weight.value }) { pair ->
                PlateRow(pair, state.unit) { viewModel.setPairs(pair.weight, it) }
                HorizontalDivider()
            }

            item {
                // Empty means nobody has described their gym, never that there is nothing to
                // lift with — which is why the engine falls back on an ordinary rack.
                if (state.pairs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plates_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.adding,
                        onValueChange = viewModel::typeAdding,
                        label = { Text(stringResource(R.string.plates_add_weight)) },
                        suffix = { Text(stringResource(state.unit.label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::addPair) {
                        Text(stringResource(R.string.plates_add))
                    }
                }
            }
        }
    }
}

/** One weight, and how many pairs of it are in the room. */
@Composable
private fun PlateRow(pair: PlatePair, unit: WeightUnit, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "${pair.weight.asTextIn(unit)} ${stringResource(unit.label)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (pair.pairs == 1) {
                        R.string.plates_pair_count
                    } else {
                        R.string.plates_pair_count_many
                    },
                    pair.pairs,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Down to none takes the row away: an inventory lists what somebody has, and a count
            // of zero is a way of writing "none" that leaves clutter behind.
            TextButton(onClick = { onChange(pair.pairs - 1) }) {
                Text(stringResource(R.string.plates_fewer))
            }
            TextButton(onClick = { onChange(pair.pairs + 1) }) {
                Text(stringResource(R.string.plates_more))
            }
        }
    }
}
