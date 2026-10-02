package com.tylergimpel.gymtracker.ui.profile

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import com.tylergimpel.gymtracker.ui.settings.Pill
import com.tylergimpel.gymtracker.ui.theme.AppOutlinedButton
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import com.tylergimpel.gymtracker.ui.theme.SectionLabel
import com.tylergimpel.gymtracker.ui.theme.CardRow
import com.tylergimpel.gymtracker.ui.theme.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.domain.Experience
import com.tylergimpel.gymtracker.domain.Sex
import com.tylergimpel.gymtracker.domain.asTextIn
import com.tylergimpel.gymtracker.ui.label
import java.time.Year

/**
 * Who the lifter is, as far as they have chosen to say — and nothing in the app waits on it.
 *
 * **Every field is optional, and the screen says what each is for** (#87). The engine runs a
 * calibration ramp rather than a prediction, so a profile only shortens it: somebody who fills in
 * nothing trains exactly as well. Height has a field here for a lifter who wants it kept, and no
 * flow anywhere prompts for it.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenBodyweight: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onBack = onBack,
        onOpenBodyweight = onOpenBodyweight,
        onSex = viewModel::setSex,
        onExperience = viewModel::setExperience,
        onBirthYear = viewModel::setBirthYear,
        onHeightCm = viewModel::setHeightCm,
    )
}

/**
 * Profile (#148): all optional, and short. Bodyweight opens the weigh-ins; sex (tap again to clear),
 * training behind you, and steppers for the year born and height.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit = {},
    onOpenBodyweight: () -> Unit = {},
    onSex: (Sex?) -> Unit = {},
    onExperience: (Experience) -> Unit = {},
    onBirthYear: (Int?) -> Unit = {},
    onHeightCm: (Double?) -> Unit = {},
) {
    val colors = LocalGymColors.current
    Scaffold(topBar = { AppTopBar(stringResource(R.string.profile_title)) }) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.profile_all_optional), color = colors.dim)
            AppCard(Modifier.fillMaxWidth()) {
                CardRow(
                    title = stringResource(R.string.profile_bodyweight),
                    subtitle = state.bodyweight?.let {
                        "${it.weight.asTextIn(state.preferredUnit)} ${stringResource(state.preferredUnit.label)}"
                    } ?: stringResource(R.string.more_none_yet),
                    onClick = onOpenBodyweight,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.profile_sex))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Sex.FEMALE to R.string.profile_sex_female, Sex.MALE to R.string.profile_sex_male).forEach { (sex, text) ->
                        // Tapping the chosen one again takes it back: not saying is an answer.
                        Pill(stringResource(text), selected = state.profile.sex == sex) {
                            onSex(if (state.profile.sex == sex) null else sex)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.profile_training))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Experience.NOVICE to R.string.profile_training_new,
                        Experience.TRAINED to R.string.profile_training_some,
                        Experience.ADVANCED to R.string.profile_training_lots,
                    ).forEach { (experience, text) ->
                        Pill(stringResource(text), selected = state.profile.experience == experience) { onExperience(experience) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.profile_birth_year))
                ValueStepper(
                    text = state.profile.birthYear?.toString() ?: "—",
                    onStep = { by ->
                        val next = (state.profile.birthYear ?: FIRST_YEAR_OFFERED) + if (state.profile.birthYear == null) 0 else by
                        onBirthYear(next.coerceIn(EARLIEST_YEAR, Year.now().value))
                    },
                    onClear = { onBirthYear(null) }.takeIf { state.profile.birthYear != null },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.profile_height))
                ValueStepper(
                    text = state.profile.heightCm?.let { stringResource(R.string.profile_cm, it.toInt()) } ?: "—",
                    onStep = { by ->
                        val next = (state.profile.heightCm ?: FIRST_HEIGHT_OFFERED) + if (state.profile.heightCm == null) 0.0 else by.toDouble()
                        onHeightCm(next.coerceIn(HEIGHT_CM))
                    },
                    onClear = { onHeightCm(null) }.takeIf { state.profile.heightCm != null },
                )
            }
        }
    }
}

/** − value +, starting from an ordinary answer the first time either is pressed; Clear withdraws it. */
@Composable
private fun ValueStepper(text: String, onStep: (Int) -> Unit, onClear: (() -> Unit)?) {
    val colors = LocalGymColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppOutlinedButton("−", onClick = { onStep(-1) }, modifier = Modifier.width(60.dp))
        Text(text, style = MaterialTheme.typography.headlineSmall, color = colors.text, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
        AppOutlinedButton("+", onClick = { onStep(1) }, modifier = Modifier.width(60.dp))
        onClear?.let { TextButton(onClick = it) { Text(stringResource(R.string.profile_clear), color = colors.dim) } }
    }
}

/** Where the year stepper starts when nothing is stored. */
private const val FIRST_YEAR_OFFERED = 1990

/** Where the height stepper starts when nothing is stored, in centimetres. */
private const val FIRST_HEIGHT_OFFERED = 175.0

/** The earliest year of birth worth accepting, which is only there to catch a slipped digit. */
private const val EARLIEST_YEAR = 1900

/** Heights worth accepting, in centimetres, for the same reason. */
private val HEIGHT_CM = 100.0..250.0
