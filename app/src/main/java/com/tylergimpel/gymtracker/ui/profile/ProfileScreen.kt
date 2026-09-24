package com.tylergimpel.gymtracker.ui.profile

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.profile_done)) }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_optional),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // First, and with its reason beside it: bodyweight is the one of these the engine
            // can actually use, and the lifter decides whether to give it knowing what for.
            Section(
                label = stringResource(R.string.profile_bodyweight),
                why = stringResource(R.string.profile_bodyweight_why),
            ) {
                Text(
                    text = state.bodyweight?.let {
                        stringResource(
                            R.string.profile_bodyweight_latest,
                            it.weight.asTextIn(state.preferredUnit),
                            stringResource(state.preferredUnit.label),
                        )
                    } ?: stringResource(R.string.profile_bodyweight_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onOpenBodyweight) {
                    Text(stringResource(R.string.profile_bodyweight_open))
                }
            }

            Section(
                label = stringResource(R.string.profile_sex),
                why = stringResource(R.string.profile_sex_why),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Sex.FEMALE to R.string.profile_sex_female,
                        Sex.MALE to R.string.profile_sex_male,
                    ).forEach { (sex, text) ->
                        // Tapping the chosen one again takes it back: not saying is an answer.
                        FilterChip(
                            selected = state.profile.sex == sex,
                            onClick = { onSex(if (state.profile.sex == sex) null else sex) },
                            label = { Text(stringResource(text)) },
                        )
                    }
                }
            }

            // Moved here from the routine screen: it is the lifter's, not any routine's, and the
            // one rule that reads it — steering a novice off high reps — reads it from here.
            Section(
                label = stringResource(R.string.routine_experience),
                why = stringResource(R.string.profile_experience_why),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Experience.NOVICE to R.string.experience_novice,
                        Experience.TRAINED to R.string.experience_trained,
                        Experience.ADVANCED to R.string.experience_advanced,
                    ).forEach { (experience, text) ->
                        FilterChip(
                            selected = state.profile.experience == experience,
                            onClick = { onExperience(experience) },
                            label = { Text(stringResource(text)) },
                        )
                    }
                }
            }

            Section(
                label = stringResource(R.string.profile_birth_year),
                why = stringResource(R.string.profile_birth_year_why),
            ) {
                NumberField(
                    stored = state.profile.birthYear?.toString(),
                    label = stringResource(R.string.profile_birth_year_field),
                    decimal = false,
                    // A year that could be somebody's, or blank to withdraw it. Anything else is
                    // left untyped rather than stored, so a half-typed year is never saved.
                    parse = { text -> text.toIntOrNull()?.takeIf { it in EARLIEST_YEAR..Year.now().value } },
                    onValue = { onBirthYear(it) },
                )
            }

            Section(
                label = stringResource(R.string.profile_height),
                why = stringResource(R.string.profile_height_why),
            ) {
                NumberField(
                    stored = state.profile.heightCm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
                    label = stringResource(R.string.profile_height_field),
                    decimal = true,
                    parse = { text -> text.toDoubleOrNull()?.takeIf { it in HEIGHT_CM } },
                    onValue = { onHeightCm(it) },
                )
            }
        }
    }
}

@Composable
private fun Section(label: String, why: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * A number the lifter may give or withdraw, saved as they type once it is a real one.
 *
 * Blank withdraws it. Text that is not yet a usable number stays in the field and is not saved,
 * so the stored answer is always either something plausible or nothing at all.
 */
@Composable
private fun <T : Any> NumberField(
    stored: String?,
    label: String,
    decimal: Boolean,
    parse: (String) -> T?,
    onValue: (T?) -> Unit,
) {
    var text by rememberSaveable(stored) { mutableStateOf(stored.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            when {
                typed.isBlank() -> onValue(null)
                else -> parse(typed.trim())?.let(onValue)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The earliest year of birth worth accepting, which is only there to catch a slipped digit. */
private const val EARLIEST_YEAR = 1900

/** Heights worth accepting, in centimetres, for the same reason. */
private val HEIGHT_CM = 100.0..250.0
