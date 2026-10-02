package com.tylergimpel.gymtracker.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.ui.theme.LocalGymColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** How lit a muscle is: not at all, partly (secondary, or short of its band), or fully. */
enum class MuscleShade { NONE, PARTIAL, FULL }

/**
 * The body, front and back, with muscles lit (#132, #146).
 *
 * Drawn from a vendored snapshot of MuscleMap's hand-traced paths (`data/musclemap`, MIT), in the
 * theme's colours at draw time, so one asset serves both themes. [shade] is asked about the
 * bank's own seventeen muscles; each drawn surface takes the strongest shade among the bank
 * muscles it stands for.
 */
@Composable
fun MuscleDiagram(
    shade: (String) -> MuscleShade,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 260.dp,
    description: String? = null,
) {
    val context = LocalContext.current
    val bodies by produceState<Bodies?>(Bodies.cached, context) { value = Bodies.load(context) }
    val colors = LocalGymColors.current
    val loaded = bodies ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { m -> if (description != null) m.semantics { contentDescription = description } else m },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(loaded.front, loaded.back).forEach { body ->
            Canvas(
                Modifier
                    .weight(1f)
                    .height(maxHeight)
                    .clipToBounds(),
            ) {
                val scale = minOf(size.width / body.width, size.height / body.height)
                val dx = (size.width - body.width * scale) / 2
                val dy = (size.height - body.height * scale) / 2
                withTransform({
                    translate(dx, dy)
                    scale(scale, scale, Offset.Zero)
                    translate(-body.x, -body.y)
                }) {
                    body.outline.forEach {
                        drawPath(it, colors.raised)
                        drawPath(it, colors.line, style = Stroke(width = 2f / scale))
                    }
                    body.muscles.forEach { muscle ->
                        val lit = muscle.bankMuscles.maxOfOrNull { shade(it) } ?: MuscleShade.NONE
                        drawPath(
                            muscle.path,
                            when (lit) {
                                MuscleShade.FULL -> colors.accent
                                MuscleShade.PARTIAL -> colors.accent.copy(alpha = 0.42f)
                                MuscleShade.NONE -> colors.line
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The shade for an exercise's own muscles: primary fully, secondary partly. */
fun shadeForExercise(primary: List<String>, secondary: List<String>): (String) -> MuscleShade = { muscle ->
    when (muscle) {
        in primary -> MuscleShade.FULL
        in secondary -> MuscleShade.PARTIAL
        else -> MuscleShade.NONE
    }
}

private class Body(val x: Float, val y: Float, val width: Float, val height: Float, val outline: List<Path>, val muscles: List<Surface>)

private class Surface(val path: Path, val bankMuscles: List<String>)

private class Bodies(val front: Body, val back: Body) {
    companion object {
        @Volatile
        var cached: Bodies? = null
            private set

        suspend fun load(context: Context): Bodies = cached ?: withContext(Dispatchers.Default) {
            Bodies(read(context, "front.json"), read(context, "back.json")).also { cached = it }
        }

        private fun read(context: Context, name: String): Body {
            val json = JSONObject(context.assets.open("musclemap/$name").bufferedReader().use { it.readText() })
            val view = json.getJSONArray("viewBox")
            val outline = json.getJSONArray("outline")
            val muscles = json.getJSONArray("muscles")
            return Body(
                x = view.getDouble(0).toFloat(),
                y = view.getDouble(1).toFloat(),
                width = view.getDouble(2).toFloat(),
                height = view.getDouble(3).toFloat(),
                outline = (0 until outline.length()).map { parse(outline.getString(it)) },
                muscles = (0 until muscles.length()).map { i ->
                    val muscle = muscles.getJSONObject(i)
                    Surface(parse(muscle.getString("d")), BANK_MUSCLES[muscle.optString("group")].orEmpty())
                },
            )
        }

        private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

        /** Which of the bank's muscles each MuscleMap group stands for. */
        private val BANK_MUSCLES: Map<String, List<String>> = mapOf(
            "CHEST" to listOf("chest"),
            "SHOULDERS_FRONT" to listOf("shoulders"),
            "SHOULDERS_SIDE" to listOf("shoulders"),
            "SHOULDERS_REAR" to listOf("shoulders"),
            "TRICEPS" to listOf("triceps"),
            "BICEPS" to listOf("biceps"),
            "FOREARMS" to listOf("forearms"),
            "CORE" to listOf("abdominals"),
            "OBLIQUES" to listOf("abdominals"),
            "QUADS" to listOf("quadriceps"),
            "HAMSTRINGS" to listOf("hamstrings"),
            "GLUTES" to listOf("glutes"),
            "CALVES" to listOf("calves"),
            "LATS" to listOf("lats"),
            "RHOMBOIDS" to listOf("middle back"),
            // MuscleMap draws no neck: it lights with the traps it moves with.
            "TRAPEZIUS" to listOf("traps", "neck"),
            "BACK_LOWER" to listOf("lower back"),
            "ABDUCTORS" to listOf("abductors"),
            "ADDUCTORS" to listOf("adductors"),
        )
    }
}
