package com.tylergimpel.traintracks.ui.plates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.PlatePair
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.asAmountText
import com.tylergimpel.traintracks.domain.asTextIn
import com.tylergimpel.traintracks.ui.label
import com.tylergimpel.traintracks.ui.routine.DashedButton
import com.tylergimpel.traintracks.ui.routine.Tile
import com.tylergimpel.traintracks.ui.session.Sheet
import com.tylergimpel.traintracks.ui.session.SheetTitle
import com.tylergimpel.traintracks.ui.settings.Pill
import com.tylergimpel.traintracks.ui.theme.AppCard
import com.tylergimpel.traintracks.ui.theme.AppOutlinedButton
import com.tylergimpel.traintracks.ui.theme.AppTopBar
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.RowDivider
import com.tylergimpel.traintracks.ui.theme.SectionLabel
import com.tylergimpel.traintracks.ui.theme.UppercaseText

/**
 * The plates in the room and the bar they go on (#148).
 *
 * **One inventory, and the only place it is edited.** Every barbell recommendation is rounded to
 * what these can build, so a change here changes what the next set asks for. Pairs rather than
 * plates throughout, because a bar loaded at one end is not a lift.
 */
@Composable
fun PlatesScreen(onBack: () -> Unit, viewModel: PlatesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalGymColors.current
    val unit = state.unit
    val unitLabel = stringResource(unit.label)
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.plates_title)) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionLabel(stringResource(R.string.plates_bar))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    barsFor(unit).forEach { amount ->
                        Pill(
                            "${amount.asAmountText()} $unitLabel",
                            selected = state.bar.asTextIn(unit) == amount.asAmountText(),
                        ) { viewModel.chooseBar(amount) }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.plates_pairs), Modifier.padding(top = 8.dp)) }
            if (state.pairs.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        state.pairs.forEachIndexed { index, pair ->
                            if (index > 0) RowDivider()
                            PlateRow(pair, unit) { viewModel.setPairs(pair.weight, it) }
                        }
                    }
                }
            } else {
                // Empty means nobody has described their gym, never that there is nothing to lift
                // with: the engine falls back on an ordinary rack.
                item { Text(stringResource(R.string.plates_standard), color = colors.dim) }
            }
            item { DashedButton(stringResource(R.string.plates_add_plate)) { adding = true } }
            item {
                val heaviest = Kilograms(state.bar.value + 2 * state.pairs.sumOf { it.weight.value * it.pairs })
                Text(
                    stringResource(R.string.plates_heaviest, heaviest.asTextIn(unit), unitLabel),
                    color = colors.dim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (adding) {
        Sheet(onDismiss = { adding = false }) {
            SheetTitle(stringResource(R.string.plates_add_plate_title))
            platesFor(unit).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { amount ->
                        Tile(
                            selected = false,
                            onClick = {
                                viewModel.addPairOf(amount)
                                adding = false
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                        ) {
                            Text(amount.asAmountText(), style = MaterialTheme.typography.titleLarge)
                            Text(unitLabel, fontSize = 13.sp, color = colors.dim)
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One weight, and how many pairs of it are in the room, stepped; down to none removes it. */
@Composable
private fun PlateRow(pair: PlatePair, unit: WeightUnit, onChange: (Int) -> Unit) {
    val colors = LocalGymColors.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UppercaseText(
            "${pair.weight.asTextIn(unit)} ${stringResource(unit.label)}",
            style = MaterialTheme.typography.titleLarge.copy(color = colors.text),
            modifier = Modifier.weight(1f),
        )
        AppOutlinedButton("−", onClick = { onChange(pair.pairs - 1) }, modifier = Modifier.width(56.dp).testTag("fewer-${pair.weight.value}"))
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pair.pairs.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.text)
            Text(stringResource(R.string.plates_pairs_short), fontSize = 12.sp, color = colors.dim)
        }
        AppOutlinedButton("+", onClick = { onChange(pair.pairs + 1) }, modifier = Modifier.width(56.dp))
    }
}

/** The bars offered, in the unit read: a women's and a men's Olympic bar. */
private fun barsFor(unit: WeightUnit): List<Double> =
    if (unit == WeightUnit.POUNDS) listOf(35.0, 45.0) else listOf(15.0, 20.0)

/** The plate sizes offered to add, in the unit read. */
private fun platesFor(unit: WeightUnit): List<Double> =
    if (unit == WeightUnit.POUNDS) listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25) else listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
