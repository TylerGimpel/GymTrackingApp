package com.tylergimpel.traintracks.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylergimpel.traintracks.R
import com.tylergimpel.traintracks.domain.Exercise

/**
 * The ten groups the screens name muscles by, each with a colour of its own (#133).
 *
 * The bank speaks in seventeen upstream muscles, which is too fine for a chip or a calendar band:
 * nobody reading "middle back" and "lats" on one day wants two colours for their back. The colours
 * stand apart from the accent, so a muscle reads the same in either theme and never as a selection.
 */
enum class MuscleGroup(@StringRes val label: Int, val color: Color) {
    CHEST(R.string.muscle_chest, Color(0xFFF28B82)),
    BACK(R.string.muscle_back, Color(0xFF8AB4F8)),
    SHOULDERS(R.string.muscle_shoulders, Color(0xFFFDD663)),
    TRICEPS(R.string.muscle_triceps, Color(0xFFC58AF9)),
    BICEPS(R.string.muscle_biceps, Color(0xFF78D9EC)),
    QUADS(R.string.muscle_quads, Color(0xFF81C995)),
    HAMSTRINGS(R.string.muscle_hamstrings, Color(0xFFFCAD70)),
    GLUTES(R.string.muscle_glutes, Color(0xFFFF8BCB)),
    CALVES(R.string.muscle_calves, Color(0xFFA8C7A0)),
    CORE(R.string.muscle_core, Color(0xFFD7CCC8)),
    ;

    companion object {
        /**
         * The group an upstream muscle is read as, or null for one the bank has never named.
         *
         * The three the prototype left out go where a lifter would look for them: the neck with the
         * traps it moves with, the abductors with the glutes that do the same job, and the
         * adductors with the thigh they sit in.
         */
        fun of(muscle: String): MuscleGroup? = when (muscle.lowercase()) {
            "chest" -> CHEST
            "lats", "middle back", "traps", "lower back", "neck" -> BACK
            "shoulders" -> SHOULDERS
            "triceps" -> TRICEPS
            "biceps", "forearms" -> BICEPS
            "quadriceps", "adductors" -> QUADS
            "hamstrings" -> HAMSTRINGS
            "glutes", "abductors" -> GLUTES
            "calves" -> CALVES
            "abdominals" -> CORE
            else -> null
        }
    }
}

/**
 * The groups [exercises] train directly, the most-trained first.
 *
 * Primary muscles only: a chip says what a day is *for*, and every pressing day would otherwise
 * claim the triceps it merely brushes.
 */
fun muscleGroupsOf(exercises: List<Exercise>): List<MuscleGroup> =
    exercises.flatMap { exercise -> exercise.primaryMuscles.mapNotNull(MuscleGroup::of).distinct() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<MuscleGroup, Int>> { it.value }.thenBy { it.key.ordinal })
        .map { it.key }

/** One muscle group: its colour as a dot, and its name. */
@Composable
fun MuscleChip(group: MuscleGroup, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(group.color, CircleShape))
        Text(stringResource(group.label), fontSize = 15.sp, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChips(groups: List<MuscleGroup>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { MuscleChip(it) }
    }
}
