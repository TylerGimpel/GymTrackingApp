package com.tylergimpel.traintracks.ui.more

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.data.BodyweightRepository
import com.tylergimpel.traintracks.data.ExerciseRepository
import com.tylergimpel.traintracks.data.GymDatabase
import com.tylergimpel.traintracks.data.plateRepository
import com.tylergimpel.traintracks.data.profileRepository
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.asTextIn
import com.tylergimpel.traintracks.domain.excluded
import com.tylergimpel.traintracks.ui.icons.EquipmentIcons
import com.tylergimpel.traintracks.ui.icons.circle
import com.tylergimpel.traintracks.ui.icons.lineIcon
import com.tylergimpel.traintracks.ui.icons.rect
import com.tylergimpel.traintracks.ui.label
import com.tylergimpel.traintracks.ui.theme.AppCard
import com.tylergimpel.traintracks.ui.theme.AppTopBar
import com.tylergimpel.traintracks.ui.theme.CardRow
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.RowDivider
import com.tylergimpel.traintracks.ui.theme.UppercaseText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One fact for each tile on More. */
data class MoreUiState(
    val bankSize: Int = 0,
    val latestWeight: Kilograms? = null,
    val bar: Kilograms? = null,
    val excluded: Int = 0,
    val unit: WeightUnit = WeightUnit.DEFAULT,
)

class MoreViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GymDatabase.getInstance(application)
    private val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), database.profileRepository())

    val state: StateFlow<MoreUiState> = combine(
        exercises.observeBank(),
        BodyweightRepository(database, database.bodyweightDao()).observeHistory(),
        database.plateRepository().observeBarbell(),
        database.profileRepository().observeDisplayUnit(),
    ) { bank, weighIns, barbell, unit ->
        MoreUiState(
            bankSize = bank.size,
            latestWeight = weighIns.latest?.weight,
            bar = barbell.bar,
            excluded = bank.excluded().size,
            unit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())
}

/**
 * More (#138): four big tiles — Exercises, Weigh-ins, Plates, Profile — each with an icon, a name
 * and one fact, then rows for excluded exercises, settings, and the lifter's data.
 */
@Composable
fun MoreScreen(
    onOpenExercises: () -> Unit,
    onOpenBodyweight: () -> Unit,
    onOpenPlates: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExcluded: () -> Unit = {},
    onOpenData: () -> Unit = {},
    viewModel: MoreViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = stringResource(state.unit.label)
    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_more)) }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoreTile(EquipmentIcons.iconFor("dumbbell"), stringResource(R.string.more_exercises), stringResource(R.string.more_in_bank, state.bankSize), onOpenExercises, Modifier.weight(1f))
                MoreTile(
                    SCALE,
                    stringResource(R.string.more_weigh_ins),
                    state.latestWeight?.let { "${it.asTextIn(state.unit)} $unit" } ?: stringResource(R.string.more_none_yet),
                    onOpenBodyweight,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoreTile(
                    EquipmentIcons.iconFor("barbell"),
                    stringResource(R.string.more_plates),
                    state.bar?.let { stringResource(R.string.more_bar, it.asTextIn(state.unit), unit) }.orEmpty(),
                    onOpenPlates,
                    Modifier.weight(1f),
                )
                MoreTile(PERSON, stringResource(R.string.more_profile), stringResource(R.string.more_optional), onOpenProfile, Modifier.weight(1f))
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                CardRow(
                    stringResource(R.string.more_excluded),
                    onClick = onOpenExcluded,
                    trailing = {
                        Text(state.excluded.toString(), color = LocalGymColors.current.dim, fontSize = 15.sp)
                        com.tylergimpel.traintracks.ui.theme.Chevron()
                    },
                )
                RowDivider()
                CardRow(stringResource(R.string.more_settings), onClick = onOpenSettings)
                RowDivider()
                CardRow(stringResource(R.string.more_data), onClick = onOpenData, modifier = Modifier.testTag(MORE_DATA))
            }
        }
    }
}

@Composable
private fun MoreTile(icon: ImageVector, name: String, fact: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalGymColors.current
    AppCard(modifier.heightIn(min = 130.dp), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = colors.accentText, modifier = Modifier.size(32.dp))
            UppercaseText(name, style = MaterialTheme.typography.headlineSmall.copy(color = colors.text))
            Text(fact, fontSize = 15.sp, color = colors.dim)
        }
    }
}

private val SCALE = lineIcon("scale", rect(3f, 4f, 18f, 16f, 3f), "M8 9a4 4 0 0 1 8 0z", "M12 9l1.5-2")
private val PERSON = lineIcon("person", circle(12f, 8f, 4f), "M5 21v-1a7 7 0 0 1 14 0v1")

/** A handle for the tests. */
const val MORE_DATA = "more-data"

