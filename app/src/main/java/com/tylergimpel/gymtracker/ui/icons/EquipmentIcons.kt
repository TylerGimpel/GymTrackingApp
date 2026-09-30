package com.tylergimpel.gymtracker.ui.icons

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.tylergimpel.gymtracker.R

/**
 * An icon and a plain plural name for each piece of equipment the bank knows (#132, #144).
 *
 * **Tabler Icons** (MIT, © Paweł Kuna; notice in `assets/licenses`) where Tabler draws the thing:
 * barbell, kettlebell (`weight`), exercise ball, treadmill, bike, stairs, run. The gym
 * machines no open set covers (and a dumbbell that reads as one) — cable, machine, EZ bar, bands, medicine ball, foam roller, rower,
 * elliptical — are drawn here on Tabler's grid: 24 square, 2 stroke, round ends.
 */
object EquipmentIcons {
    private val barbell = lineIcon(
        "barbell",
        "M2 12h1", "M6 8h-2a1 1 0 0 0 -1 1v6a1 1 0 0 0 1 1h2",
        "M6 7v10a1 1 0 0 0 1 1h1a1 1 0 0 0 1 -1v-10a1 1 0 0 0 -1 -1h-1a1 1 0 0 0 -1 1",
        "M9 12h6",
        "M15 7v10a1 1 0 0 0 1 1h1a1 1 0 0 0 1 -1v-10a1 1 0 0 0 -1 -1h-1a1 1 0 0 0 -1 1",
        "M18 8h2a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-2", "M22 12h-1",
    )
    // Tabler's dumbbell reads as a kettlebell at this size, so this one is drawn.
    private val dumbbell = lineIcon("dumbbell", rect(4f, 8f, 4f, 8f, 1f), rect(16f, 8f, 4f, 8f, 1f), "M8 12h8M2 11v2M22 11v2")
    private val kettlebell = lineIcon(
        "kettlebell",
        "M9 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M6.835 9h10.33a1 1 0 0 1 .984 .821l1.637 9a1 1 0 0 1 -.984 1.179h-13.604a1 1 0 0 1 -.984 -1.179l1.637 -9a1 1 0 0 1 .984 -.821",
    )
    private val exerciseBall = lineIcon(
        "exercise-ball",
        "M5.59 18.31a15.57 15.57 0 0 1 4.51 -9.21a15.9 15.9 0 0 1 7.43 -4.19",
        "M11.55 21a9.34 9.34 0 0 1 2.79 -7.65a9.5 9.5 0 0 1 6.54 -2.85",
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
    )
    private val treadmill = lineIcon(
        "treadmill",
        "M10 3a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M3 14l4 1l.5 -.5", "M12 18v-3l-3 -2.923l.75 -5.077",
        "M6 10v-2l4 -1l2.5 2.5l2.5 .5", "M21 22a1 1 0 0 0 -1 -1h-16a1 1 0 0 0 -1 1", "M18 21l1 -11l2 -1",
    )
    private val bike = lineIcon(
        "bike",
        "M2 18a3 3 0 1 0 6 0a3 3 0 0 0 -6 0", "M16 18a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
        "M12 19v-4l-3 -3l5 -4l2 3h3", "M13.007 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    private val stairs = lineIcon("stairs-up", "M22 6h-5v5h-5v5h-5v5h-5", "M6 10v-7", "M3 6l3 -3l3 3")
    private val run = lineIcon(
        "run",
        "M11.007 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M4 17l5 1l.75 -1.5", "M15 21v-4l-4 -3l1 -6", "M7 12v-3l5 -1l3 3l3 1",
    )

    // Drawn for this app on Tabler's grid.
    private val cable = lineIcon("cable", "M4 3v18M4 3h9", circle(13f, 5f, 2f), "M15 5v10M12 18h6M15 15v3")
    private val machine = lineIcon(
        "machine",
        "M5 3v18M19 3v18M5 3h14M12 3v7", rect(8f, 10f, 8f, 3f), rect(8f, 14f, 8f, 3f), rect(8f, 18f, 8f, 3f),
    )
    private val ezBar = lineIcon("ez-bar", "M2 12h3l2-2 2.5 4 2.5-4 2.5 4 2.5-4 2 2h3M5 8v8M19 8v8")
    private val bands = lineIcon("bands", "M5 6c4 3 10 3 14 0M5 18c4-3 10-3 14 0M5 6c-2 4-2 8 0 12M19 6c2 4 2 8 0 12")
    private val medicineBall = lineIcon(
        "medicine-ball", circle(12f, 12f, 9f), "M3 12h18M12 3c3.5 3 3.5 15 0 18M12 3c-3.5 3-3.5 15 0 18",
    )
    private val foamRoller = lineIcon(
        "foam-roller", "M6 7a2.5 5 0 1 0 0 10a2.5 5 0 1 0 0 -10", "M6 7h12a2.5 5 0 0 1 0 10H6M11 7v10M15 7v10",
    )
    private val rower = lineIcon("rower", "M3 19h18M5 19v-3M19 19v-3", circle(18f, 12f, 3f), "M6 15h4M15 12l-7-3")
    private val elliptical = lineIcon(
        "elliptical", "M4 17a5 2.5 0 1 0 10 0a5 2.5 0 1 0 -10 0", "M14 17l3-11M17 6h3M17 6l-4 4M4 21h16",
    )
    private val bodyweight = lineIcon("bodyweight", circle(12f, 4.5f, 2f), "M12 7v7M7 10l5-1 5 1M12 14l-3 7M12 14l3 7")

    /** The icon for [equipment], a bank value; a figure for bodyweight, none known, or "other". */
    fun iconFor(equipment: String?): ImageVector = when (equipment) {
        "barbell" -> barbell
        "dumbbell" -> dumbbell
        "kettlebells" -> kettlebell
        "exercise ball" -> exerciseBall
        "treadmill" -> treadmill
        "stationary bike" -> bike
        "stair climber" -> stairs
        "outdoor" -> run
        "cable" -> cable
        "machine" -> machine
        "e-z curl bar" -> ezBar
        "bands" -> bands
        "medicine ball" -> medicineBall
        "foam roll" -> foamRoller
        "rower" -> rower
        "elliptical" -> elliptical
        else -> bodyweight
    }

    /** What [equipment] is called in the plural, as setup and the bank's filters name it. */
    @StringRes
    fun pluralFor(equipment: String?): Int = when (equipment) {
        "barbell" -> R.string.equipment_barbells
        "dumbbell" -> R.string.equipment_dumbbells
        "cable" -> R.string.equipment_cables
        "machine" -> R.string.equipment_machines
        "e-z curl bar" -> R.string.equipment_ez_bars
        "kettlebells" -> R.string.equipment_kettlebells
        "bands" -> R.string.equipment_bands
        "medicine ball" -> R.string.equipment_medicine_balls
        "exercise ball" -> R.string.equipment_exercise_balls
        "foam roll" -> R.string.equipment_foam_rollers
        "treadmill" -> R.string.equipment_treadmills
        "stationary bike" -> R.string.equipment_bikes
        "rower" -> R.string.equipment_rowers
        "elliptical" -> R.string.equipment_ellipticals
        "stair climber" -> R.string.equipment_stair_climbers
        "outdoor" -> R.string.equipment_outdoors
        "body only" -> R.string.equipment_bodyweight
        else -> R.string.equipment_other
    }
}

/** The pictures setup's other questions use, drawn on the same grid. */
object SetupIcons {
    val Muscle = lineIcon("muscle", "M4 16c1-6 3-10 7-11l2 3-3 2c2 1 4 1 6-1 3 1 4 4 3 7-2 3-8 4-15 0z")
    val Strength = lineIcon("strength", "M6 20V10M18 20V10M3 14h18M9 7v10M15 7v10")
    val Stamina = lineIcon("stamina", "M3 12h4l2-5 4 10 2-5h6")
    val Heavy = lineIcon("heavy", rect(5f, 4f, 4f, 16f, 1f), rect(15f, 4f, 4f, 16f, 1f), "M9 12h6M2 12h3M19 12h3")
    val Between = lineIcon("between", rect(6f, 7f, 3f, 10f, 1f), rect(15f, 7f, 3f, 10f, 1f), "M9 12h6M3 12h3M18 12h3")
    val Light = lineIcon("light", rect(7f, 9f, 2f, 6f, 1f), rect(15f, 9f, 2f, 6f, 1f), "M9 12h6M3 12h4M17 12h4")
    val NoCardio = lineIcon("no-cardio", circle(12f, 12f, 9f), "M6 18L18 6")
    val OwnDay = lineIcon("own-day", rect(3f, 5f, 18f, 16f, 2f), "M3 10h18M8 3v4M16 3v4", "M8 15h8")
    val AfterLifting = lineIcon("after-lifting", "M5 12h10M11 7l5 5-5 5", "M19 5v14")
}
