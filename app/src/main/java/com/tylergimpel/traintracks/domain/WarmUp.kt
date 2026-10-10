package com.tylergimpel.traintracks.domain

import kotlin.math.abs

/**
 * The ramp up to a working weight, generated so that nobody works one out in their head at the
 * rack.
 *
 * **Warm-ups belong to the muscle, not the exercise.** The third chest movement of a session does
 * not need a ramp, because the first two already warmed the chest — so what decides the length of
 * this one is both how heavy the work is and how much of the session came before it.
 *
 * Every set it produces is a **Warm-up**: it carries that **Set type**, earns no **Volume**, and
 * asks for a **Plain rep count** rather than a **Rep range**, because a warm-up is not progressed
 * and has no top to reach.
 *
 * @param workingLoad what the ramp is climbing to.
 * @param exercise what is being warmed up, which decides how much of a ramp it deserves.
 * @param alreadyWorkedTheMuscle how many exercises earlier in this session trained the same
 *   primary muscle. Each of them takes a rung off, because the muscle is already warm.
 */
fun warmUpsFor(
    workingLoad: Kilograms,
    exercise: Exercise,
    alreadyWorkedTheMuscle: Int = 0,
    rounding: Increment = Increment.DEFAULT,
): List<Prescription> {
    // A machine helping a lifter up has nothing to ramp: the ramp *is* more help, and the way to
    // warm up on one is to do a set on it.
    if (workingLoad.value <= 0.0) return emptyList()

    val rungs = (rungsFor(workingLoad, exercise) - alreadyWorkedTheMuscle).coerceAtLeast(0)
    return RAMPS.getOrElse(rungs) { emptyList() }.map { rung ->
        Prescription(
            load = rounding.roundDown(Kilograms(workingLoad.value * rung.fraction)),
            reps = rung.reps,
        )
    }
}

/**
 * How many rungs a working set of this weight deserves, before the session gets a say.
 *
 * Counted in **empty bars**, because that is the unit a ramp is actually measured in: the first
 * rung of every barbell ramp is the bar itself, and a lift that is barely heavier than the bar
 * has nothing to ramp through. Capped at four however heavy the work is — a first set is a
 * warm-up, not the work itself, and five rungs before the real thing is how a session runs long.
 *
 * Isolation and machine work is worth two fewer, which is what makes light isolation work get
 * none at all: a curl is not a movement anybody needs three rehearsals of, and the shoulder is
 * already warm from whatever pressed before it.
 */
private fun rungsFor(workingLoad: Kilograms, exercise: Exercise): Int {
    val bars = abs(workingLoad.value) / EMPTY_BAR
    val full = when {
        bars <= 1.0 -> 0
        bars <= 2.0 -> 1
        bars <= 3.0 -> 2
        bars <= 4.0 -> 3
        else -> MOST_RUNGS
    }
    // Isolation by mechanic, or anything on a stack. A machine holds the lifter on its path, so a
    // compound on one asks for less rehearsal than the same pattern does under a bar.
    val smallMovement = !exercise.isCompound || exercise.isStacked
    return if (smallMovement) full - RUNGS_OFF_FOR_SMALL_MOVEMENTS else full
}

/** One rung: how much of the working weight, and how many reps of it. */
private data class Rung(val fraction: Double, val reps: Int)

/**
 * The ramps, by how many rungs they have.
 *
 * Roughly 40/60/80% with descending reps, spread to fit however many rungs there are. The
 * percentages are the ones every novice protocol uses and the reps come down as the weight goes
 * up, because the point of the last rung is to feel the weight rather than to do work.
 */
private val RAMPS: List<List<Rung>> = listOf(
    emptyList(),
    listOf(Rung(0.60, 5)),
    listOf(Rung(0.50, 5), Rung(0.75, 3)),
    listOf(Rung(0.40, 5), Rung(0.60, 3), Rung(0.80, 2)),
    listOf(Rung(0.40, 8), Rung(0.55, 5), Rung(0.70, 3), Rung(0.85, 2)),
)

/** An Olympic bar, which is where every barbell ramp starts and the unit the rest is counted in. */
private const val EMPTY_BAR = 20.0

/** The cap. Four rungs is a ramp; five is the session. */
private const val MOST_RUNGS = 4

/** What isolation and machine work gives up, which is what leaves light ones with no ramp. */
private const val RUNGS_OFF_FOR_SMALL_MOVEMENTS = 2
