package com.tylergimpel.traintracks.ui.routine

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.CardioPlacement
import com.tylergimpel.traintracks.domain.RepPreference
import com.tylergimpel.traintracks.domain.RoutineDayKind
import com.tylergimpel.traintracks.domain.RoutineGoal
import com.tylergimpel.traintracks.domain.SplitShape
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.conflictsFor
import com.tylergimpel.traintracks.ui.icons.AppIcons
import com.tylergimpel.traintracks.ui.icons.EquipmentIcons
import com.tylergimpel.traintracks.ui.icons.SetupIcons
import com.tylergimpel.traintracks.ui.theme.AppCard
import com.tylergimpel.traintracks.ui.theme.CardShape
import com.tylergimpel.traintracks.ui.theme.ControlShape
import com.tylergimpel.traintracks.ui.theme.LocalGymColors
import com.tylergimpel.traintracks.ui.theme.MainButton
import com.tylergimpel.traintracks.ui.theme.SectionLabel
import com.tylergimpel.traintracks.ui.theme.TextButton
import com.tylergimpel.traintracks.ui.theme.UppercaseText

/**
 * The welcome page on first run (#144): the app's name big, Get started, and the two quieter ways
 * in — building it by hand, or bringing history in from FitNotes.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onBuildMyself: () -> Unit, onImport: () -> Unit) {
    val colors = LocalGymColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        // The name big, its last word in the accent.
        val words = stringResource(R.string.app_name).split(' ')
        val big = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 80.sp)
        Column(Modifier.clearAndSetSemantics { contentDescription = words.joinToString(" ") }) {
            if (words.size > 1) UppercaseText(words.dropLast(1).joinToString(" "), style = big.copy(color = colors.text))
            UppercaseText(words.last(), style = big.copy(color = colors.accentText))
        }
        Text(stringResource(R.string.welcome_line), fontSize = 20.sp, color = colors.dim, modifier = Modifier.padding(bottom = 20.dp))
        MainButton(stringResource(R.string.welcome_get_started), onClick = onGetStarted, modifier = Modifier.fillMaxWidth().testTag(GET_STARTED))
        QuietButton(stringResource(R.string.welcome_build_myself), onBuildMyself)
        QuietButton(stringResource(R.string.start_import), onImport)
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        UppercaseText(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, color = LocalGymColors.current.dim))
    }
}

/**
 * Setup (#144): one question to a page, the usual answer already picked, and Next always working;
 * then a summary where any answer can be changed; then Build, which lands on Today.
 *
 * Back steps back a page and keeps every answer. Leaving from the first page leaves setup.
 */
@Composable
fun SetupScreen(
    onBuilt: () -> Unit,
    onLeave: () -> Unit,
    viewModel: RoutineViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var answers by rememberSaveable(stateSaver = SetupAnswersSaver) { mutableStateOf(SetupAnswers()) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var usualUnit by rememberSaveable { mutableStateOf<WeightUnit?>(null) }

    // The unit starts on, and calls usual, what the lifter already reads in: seeded from the
    // phone's locale until they say.
    LaunchedEffect(Unit) {
        if (usualUnit == null) {
            val preferred = viewModel.preferredUnit()
            usualUnit = preferred
            answers = answers.copy(unit = preferred)
        }
    }

    fun back() = when {
        editing -> {
            editing = false
            reviewing = true
        }
        reviewing -> reviewing = false
        answers.step != SetupStep.entries.first() -> answers = answers.back()
        else -> onLeave()
    }
    BackHandler { back() }

    val conflicts = conflictsFor(
        goal = answers.goal,
        preference = answers.repPreference,
        experience = state.experience,
        equipment = answers.toRoutineSetup().equipment,
    ).map { it.explanation }

    val colors = LocalGymColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // A short top bar: Back, then where they are, or what is being changed.
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clickable(onClickLabel = stringResource(R.string.setup_back)) { back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Back, contentDescription = stringResource(R.string.setup_back), tint = colors.text)
            }
            when {
                reviewing -> UppercaseText(stringResource(R.string.setup_review_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, color = colors.text))
                editing -> UppercaseText(stringResource(answers.step.shortName), style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, color = colors.text))
                else -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(answers.stepNumber.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp), color = colors.text)
                    Text(" / ${SetupAnswers.STEPS}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = colors.dim)
                }
            }
        }
        if (!reviewing && !editing) {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(4.dp).background(colors.raised, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth(answers.stepNumber / SetupAnswers.STEPS.toFloat()).height(4.dp)
                        .background(colors.accent, RoundedCornerShape(2.dp)),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reviewing) {
                Review(answers, conflicts) { step ->
                    answers = answers.copy(step = step)
                    reviewing = false
                    editing = true
                }
            } else {
                SetupQuestion(answers, onChange = { answers = it }, conflicts = conflicts, usualUnit = usualUnit ?: WeightUnit.DEFAULT)
            }
        }

        val routineName = shapeName(answers.shape)
        HorizontalDivider(color = colors.line)
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            when {
                reviewing -> MainButton(
                    stringResource(R.string.setup_build),
                    onClick = {
                        viewModel.buildFromSetup(answers, routineName)
                        onBuilt()
                    },
                    modifier = Modifier.fillMaxWidth().testTag(SETUP_BUILD),
                )
                editing -> MainButton(stringResource(R.string.setup_done), onClick = { back() }, modifier = Modifier.fillMaxWidth())
                answers.isLast -> MainButton(stringResource(R.string.setup_review), onClick = { reviewing = true }, modifier = Modifier.fillMaxWidth().testTag(SETUP_NEXT))
                else -> MainButton(stringResource(R.string.setup_next), onClick = { answers = answers.next() }, modifier = Modifier.fillMaxWidth().testTag(SETUP_NEXT))
            }
        }
    }
}

/** The short name a question goes by on the summary and at the top of its page when changed. */
@get:StringRes
internal val SetupStep.shortName: Int
    get() = when (this) {
        SetupStep.UNIT -> R.string.setup_short_unit
        SetupStep.GOAL -> R.string.setup_short_goal
        SetupStep.REP_PREFERENCE -> R.string.setup_short_reps
        SetupStep.CADENCE -> R.string.setup_short_days
        SetupStep.SHAPE -> R.string.setup_short_split
        SetupStep.CARDIO -> R.string.setup_short_cardio
        SetupStep.EQUIPMENT -> R.string.setup_short_equipment
    }

@get:StringRes
private val SetupStep.question: Int
    get() = when (this) {
        SetupStep.UNIT -> R.string.setup_q_unit
        SetupStep.GOAL -> R.string.setup_q_goal
        SetupStep.REP_PREFERENCE -> R.string.setup_q_reps
        SetupStep.CADENCE -> R.string.setup_q_days
        SetupStep.SHAPE -> R.string.setup_q_split
        SetupStep.CARDIO -> R.string.setup_q_cardio
        SetupStep.EQUIPMENT -> R.string.setup_q_equipment
    }

/**
 * One question's page: its question, large, and its answers as tiles. Shared with the routine's
 * own settings (#146), which open the same pages.
 */
@Composable
internal fun SetupQuestion(
    answers: SetupAnswers,
    onChange: (SetupAnswers) -> Unit,
    conflicts: List<String> = emptyList(),
    usualUnit: WeightUnit = WeightUnit.DEFAULT,
) {
    UppercaseText(
        stringResource(answers.step.question),
        style = MaterialTheme.typography.displaySmall.copy(color = LocalGymColors.current.text),
        modifier = Modifier.padding(bottom = 4.dp),
    )
    when (answers.step) {
        SetupStep.UNIT -> Tiles(
            listOf(
                TileOption(WeightUnit.KILOGRAMS, R.string.setup_kilos, bigText = "KG"),
                TileOption(WeightUnit.POUNDS, R.string.setup_pounds, bigText = "LB"),
            ),
            selected = answers.unit,
            usual = usualUnit,
        ) { onChange(answers.copy(unit = it)) }

        SetupStep.GOAL -> Tiles(
            listOf(
                TileOption(RoutineGoal.HYPERTROPHY, R.string.setup_goal_muscle, SetupIcons.Muscle),
                TileOption(RoutineGoal.STRENGTH, R.string.setup_goal_strength, SetupIcons.Strength),
                TileOption(RoutineGoal.ENDURANCE, R.string.setup_goal_stamina, SetupIcons.Stamina),
            ),
            selected = answers.goal,
            usual = RoutineSetup.ASSUMED_GOAL,
        ) { onChange(answers.copy(goal = it)) }

        SetupStep.REP_PREFERENCE -> Tiles(
            listOf(
                TileOption(RepPreference.LOW, R.string.setup_reps_heavy, SetupIcons.Heavy, R.string.setup_reps_heavy_sub),
                TileOption(RepPreference.MODERATE, R.string.setup_reps_between, SetupIcons.Between),
                TileOption(RepPreference.HIGH, R.string.setup_reps_light, SetupIcons.Light, R.string.setup_reps_light_sub),
            ),
            selected = answers.repPreference,
            usual = RoutineSetup.ASSUMED_REP_PREFERENCE,
        ) { onChange(answers.copy(repPreference = it)) }

        SetupStep.CADENCE -> NumberTiles(answers.cadence) { onChange(answers.withCadence(it)) }

        SetupStep.SHAPE -> Splits(answers) { onChange(answers.copy(shapeId = it)) }

        SetupStep.CARDIO -> Tiles(
            listOf(
                TileOption(CardioPlacement.NONE, R.string.setup_cardio_none, SetupIcons.NoCardio),
                TileOption(CardioPlacement.OWN_DAY, R.string.setup_cardio_own_day, SetupIcons.OwnDay),
                TileOption(CardioPlacement.AFTER_LIFTING, R.string.setup_cardio_after, SetupIcons.AfterLifting),
            ),
            selected = answers.cardio,
            usual = CardioPlacement.NONE,
        ) { onChange(answers.copy(cardio = it)) }

        SetupStep.EQUIPMENT -> {
            Equipment(answers, onChange)
            conflicts.forEach { Conflict(it) }
        }
    }
}

@Composable
private fun Conflict(text: String) {
    Text(text, fontSize = 15.sp, color = LocalGymColors.current.warn)
}

/** An answer as a tile: an icon or a big word, a short name, maybe a line under it. */
private data class TileOption<T>(
    val value: T,
    @StringRes val label: Int,
    val icon: ImageVector? = null,
    @StringRes val sub: Int? = null,
    val bigText: String? = null,
)

/** Two tiles across, and a third spanning the whole width. The usual answer says so. */
@Composable
private fun <T> Tiles(options: List<TileOption<T>>, selected: T, usual: T, onSelect: (T) -> Unit) {
    options.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { option ->
                val wide = row.size == 1
                Tile(
                    selected = option.value == selected,
                    usual = option.value == usual,
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.weight(1f).heightIn(min = if (wide) 96.dp else 140.dp),
                    horizontal = wide,
                ) {
                    when {
                        option.bigText != null -> Text(option.bigText, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 64.sp))
                        option.icon != null -> Icon(option.icon, null, modifier = Modifier.size(48.dp), tint = tileTint(option.value == selected))
                    }
                    Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
                        UppercaseText(stringResource(option.label), style = MaterialTheme.typography.titleLarge)
                        option.sub?.let { Text(stringResource(it), fontSize = 14.sp, color = LocalGymColors.current.dim) }
                    }
                }
            }
        }
    }
}

@Composable
private fun tileTint(selected: Boolean) = if (selected) LocalGymColors.current.accentText else LocalGymColors.current.dim

/**
 * A tile of the new look: surface, a line round it, an accent edge and a tick when chosen, and
 * `Usual` in the corner of the answer most people give.
 */
@Composable
internal fun Tile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    usual: Boolean = false,
    horizontal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (selected) colors.raised else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line),
        modifier = modifier.let { if (selected) it.testTag(SELECTED_TILE) else it },
    ) {
        Box {
            if (horizontal) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) { content() }
            }
            TileCorners(selected, usual)
        }
    }
}

@Composable
private fun BoxScope.TileCorners(selected: Boolean, usual: Boolean) {
    val colors = LocalGymColors.current
    if (usual) {
        Text(
            stringResource(R.string.setup_usual).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colors.dim,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
    if (selected) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
    }
}

/** Seven number tiles, four across. */
@Composable
private fun NumberTiles(selected: Int, onSelect: (Int) -> Unit) {
    (1..7).chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { n ->
                Tile(
                    selected = n == selected,
                    usual = n == SetupAnswers.USUAL_CADENCE,
                    onClick = { onSelect(n) },
                    modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                ) {
                    Text(n.toString(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp))
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * The splits as cards, a name and a row of day blocks, the best fit for the week first. Four show,
 * and More splits shows the rest.
 */
@Composable
private fun Splits(answers: SetupAnswers, onSelect: (String) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    val offered = answers.shapesOffered()
    val chosen = answers.shape.id
    (if (more) offered else offered.take(4)).forEachIndexed { index, shape ->
        SplitCard(
            shape = if (answers.cardio == CardioPlacement.OWN_DAY) shape.withCardioDay() else shape,
            name = shapeName(shape),
            selected = shape.id == chosen,
            bestFit = index == 0,
            onClick = { onSelect(shape.id) },
        )
    }
    if (!more && offered.size > 4) {
        TextButton(onClick = { more = true }) {
            UppercaseText(stringResource(R.string.setup_more_splits, offered.size - 4), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        }
    }
}

@Composable
internal fun SplitCard(shape: SplitShape, name: String, selected: Boolean, bestFit: Boolean, onClick: () -> Unit) {
    val colors = LocalGymColors.current
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (selected) colors.raised else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line),
        modifier = Modifier.fillMaxWidth().let { if (selected) it.testTag(SELECTED_TILE) else it },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UppercaseText(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (bestFit) SectionLabel(stringResource(R.string.setup_best_fit))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                shape.days.forEach { day ->
                    val cardio = day.kind == RoutineDayKind.CARDIO
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .let {
                                if (cardio) it.border(1.dp, colors.line, RoundedCornerShape(4.dp))
                                else it.background(if (selected) colors.bg else colors.raised, RoundedCornerShape(4.dp))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            blockName(day.name).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.tylergimpel.traintracks.ui.theme.BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = if (cardio) colors.dim else colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** A day's name short enough for its block: `Full A` for `Full body A`, `Up A` for `Upper A`. */
private fun blockName(name: String): String = name
    .replace("Full body", "Full")
    .replace("Upper", "Up")
    .replace("Lower", "Low")
    .replace("Shoulders", "Shldr")

/** A split's short name, as the tiles and the summary say it. */
@Composable
internal fun shapeName(shape: SplitShape): String = when (shape.id.removeSuffix("-cardio")) {
    "full-body-1" -> stringResource(R.string.split_full_body)
    "full-body-2" -> stringResource(R.string.split_full_body_2)
    "full-body-3" -> stringResource(R.string.split_full_body_3)
    "upper-lower-2" -> stringResource(R.string.split_upper_lower)
    "upper-lower-4" -> stringResource(R.string.split_upper_lower_2)
    "upper-lower-6" -> stringResource(R.string.split_upper_lower_3)
    "upper-lower-full-5" -> stringResource(R.string.split_upper_lower_full)
    "push-pull-legs-3" -> stringResource(R.string.split_ppl)
    "push-pull-legs-6" -> stringResource(R.string.split_ppl_2)
    "body-part-5" -> stringResource(R.string.split_body_part)
    "upper-lower-arms-3" -> stringResource(R.string.split_upper_lower_arms)
    "full-body-upper-lower-4" -> stringResource(R.string.split_full_upper_full_lower)
    "push-pull-legs-upper-lower-5" -> stringResource(R.string.split_ppl_upper_lower)
    else -> shape.description
}

/** Equipment as tiles, three across, All and None, and cardio machines only with cardio on. */
@Composable
private fun Equipment(answers: SetupAnswers, onChange: (SetupAnswers) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        SmallButton(stringResource(R.string.setup_all)) { onChange(answers.withAllEquipment()) }
        SmallButton(stringResource(R.string.setup_none)) { onChange(answers.withNoEquipment()) }
    }
    EquipmentTiles(SETUP_LIFTING_EQUIPMENT, answers, onChange)
    if (answers.cardio != CardioPlacement.NONE) {
        SectionLabel(stringResource(R.string.setup_cardio_group), Modifier.padding(top = 10.dp))
        EquipmentTiles(SETUP_CARDIO_EQUIPMENT, answers, onChange)
    }
}

@Composable
private fun EquipmentTiles(items: List<String>, answers: SetupAnswers, onChange: (SetupAnswers) -> Unit) {
    items.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                val ticked = item in answers.equipment
                Tile(
                    selected = ticked,
                    onClick = { onChange(answers.toggle(item)) },
                    modifier = Modifier.weight(1f).heightIn(min = 116.dp),
                ) {
                    Icon(EquipmentIcons.iconFor(item), null, modifier = Modifier.size(44.dp), tint = tileTint(ticked))
                    UppercaseText(
                        stringResource(EquipmentIcons.pluralFor(item)),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ControlShape, color = LocalGymColors.current.raised, modifier = Modifier.height(40.dp)) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/** The summary: one row per answer, any of which opens its page; then Build. */
@Composable
private fun Review(answers: SetupAnswers, conflicts: List<String>, onOpen: (SetupStep) -> Unit) {
    val shape = answers.shapeToBuild()
    AppCard(Modifier.fillMaxWidth()) {
        ReviewRow(SetupStep.GOAL, stringResource(answers.goal.setupLabel), onOpen = onOpen)
        ReviewRow(SetupStep.REP_PREFERENCE, stringResource(answers.repPreference.setupLabel), onOpen = onOpen)
        ReviewRow(SetupStep.CADENCE, stringResource(R.string.setup_days_value, answers.cadence), onOpen = onOpen)
        ReviewRow(SetupStep.SHAPE, shapeName(answers.shape), shape.days.joinToString(" · ") { it.name }, onOpen)
        ReviewRow(SetupStep.CARDIO, stringResource(answers.cardio.setupLabel), onOpen = onOpen)
        ReviewRow(SetupStep.EQUIPMENT, equipmentSummary(answers), onOpen = onOpen)
        ReviewRow(SetupStep.UNIT, stringResource(if (answers.unit == WeightUnit.POUNDS) R.string.setup_pounds else R.string.setup_kilos), onOpen = onOpen, last = true)
    }
    conflicts.forEach { Conflict(it) }
}

@Composable
private fun ReviewRow(step: SetupStep, value: String, sub: String? = null, onOpen: (SetupStep) -> Unit, last: Boolean = false) {
    val colors = LocalGymColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(role = Role.Button) { onOpen(step) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel(stringResource(step.shortName), Modifier.padding(bottom = 6.dp))
            UppercaseText(value, style = MaterialTheme.typography.headlineSmall.copy(color = colors.text))
            sub?.let { Text(it, fontSize = 15.sp, color = colors.dim) }
        }
        Icon(AppIcons.Chevron, null, tint = colors.dim)
    }
    if (!last) HorizontalDivider(color = colors.line)
}

/** `Full gym`, `Bodyweight only`, or how many things. */
@Composable
internal fun equipmentSummary(answers: SetupAnswers): String {
    val ticked = answers.equipmentOffered.count { it in answers.equipment }
    return when {
        answers.fullGym -> stringResource(R.string.setup_full_gym)
        ticked == 0 -> stringResource(R.string.setup_bodyweight_only)
        else -> pluralStringResource(R.plurals.setup_things, ticked, ticked)
    }
}

@get:StringRes
internal val RoutineGoal.setupLabel: Int
    get() = when (this) {
        RoutineGoal.HYPERTROPHY -> R.string.setup_goal_muscle
        RoutineGoal.STRENGTH -> R.string.setup_goal_strength
        RoutineGoal.ENDURANCE -> R.string.setup_goal_stamina
    }

@get:StringRes
internal val RepPreference.setupLabel: Int
    get() = when (this) {
        RepPreference.LOW -> R.string.setup_reps_heavy
        RepPreference.MODERATE -> R.string.setup_reps_between
        RepPreference.HIGH -> R.string.setup_reps_light
    }

@get:StringRes
internal val CardioPlacement.setupLabel: Int
    get() = when (this) {
        CardioPlacement.NONE -> R.string.setup_cardio_none
        CardioPlacement.OWN_DAY -> R.string.setup_cardio_own_day
        CardioPlacement.AFTER_LIFTING -> R.string.setup_cardio_after
    }

/** Keeps the answers through a rotation or the process being reclaimed, as plain values. */
internal val SetupAnswersSaver = listSaver<SetupAnswers, Any?>(
    save = {
        listOf(it.unit.name, it.goal.name, it.repPreference.name, it.cadence, it.shapeId, it.cardio.name, ArrayList(it.equipment), it.step.name)
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        SetupAnswers(
            unit = WeightUnit.valueOf(it[0] as String),
            goal = RoutineGoal.valueOf(it[1] as String),
            repPreference = RepPreference.valueOf(it[2] as String),
            cadence = it[3] as Int,
            shapeId = it[4] as String?,
            cardio = CardioPlacement.valueOf(it[5] as String),
            equipment = (it[6] as List<String>).toSet(),
            step = SetupStep.valueOf(it[7] as String),
        )
    },
)

/** Handles for the tests. */
const val GET_STARTED = "get-started"
const val SETUP_NEXT = "setup-next"
const val SETUP_BUILD = "setup-build"
const val SELECTED_TILE = "selected-tile"

