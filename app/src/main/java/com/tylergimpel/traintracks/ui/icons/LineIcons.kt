package com.tylergimpel.traintracks.ui.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn from SVG path data: 24 units square, a 2-unit stroke, round ends.
 *
 * The app carries no icon library, and the few it needs are the prototypes' own outlines (and
 * Tabler's, which are drawn the same way), so they are kept as the path strings they were designed
 * in rather than translated by hand. Tinted by whoever draws them, like any `Icon`.
 */
fun lineIcon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

/** A circle as path data, for [lineIcon]. */
fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/** A rectangle with rounded corners as path data, for [lineIcon]. */
fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): String =
    if (r == 0f) {
        "M$x ${y}h${w}v${h}h${-w}z"
    } else {
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
    }

/** The app's own icons: the bottom nav's four and the handful of controls that are a picture. */
object AppIcons {
    val Today = lineIcon("today", "M3 11l9-7 9 7", "M5 10v10h14V10", "M10 20v-6h4v6")
    val Calendar = lineIcon("calendar", rect(3f, 5f, 18f, 16f, 2f), "M3 10h18M8 3v4M16 3v4")
    val Routine = lineIcon("routine", "M2 12h3M19 12h3M5 8v8M19 8v8M8 6v12M16 6v12M8 12h8")
    val More = lineIcon("more", "M4 7h16M4 12h16M4 17h16")
    val List = lineIcon("list", "M4 7h16M4 12h16M4 17h10")
    val Again = lineIcon("again", "M20 12a8 8 0 1 1-2.3-5.6", "M20 4v5h-5")
    val Swap = lineIcon("swap", "M7 4L3 8l4 4", "M3 8h14", "M17 20l4-4-4-4", "M21 16H7")
    val Chevron = lineIcon("chevron", "M9 6l6 6-6 6")
    val Back = lineIcon("back", "M15 6l-6 6 6 6")
    val Close = lineIcon("close", "M6 6l12 12M18 6L6 18")
    val Plus = lineIcon("plus", "M12 5v14M5 12h14")
    val Check = lineIcon("check", "M5 12l5 5L20 7")
    val Pin = lineIcon("pin", "M12 17v5", "M9 3h6l-1 6 4 4v2H6v-2l4-4z")
    val Search = lineIcon("search", circle(11f, 11f, 7f), "M21 21l-5-5")
    val Up = lineIcon("up", "M6 15l6-6 6 6")
    val Down = lineIcon("down", "M6 9l6 6 6-6")
    val Trash = lineIcon("trash", "M4 7h16", "M10 11v6M14 11v6", "M5 7l1 13h12l1-13", "M9 7V4h6v3")
    val Edit = lineIcon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
}
