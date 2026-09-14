package com.tylergimpel.gymtracker.domain

/**
 * One unit of effort against an exercise, identified by its [setIndex] within that exercise in a
 * session rather than by [id].
 *
 * [id] is the row that happens to store it and carries no meaning: a set may one day be recorded by
 * more than one row, and counting rows instead of positions is what makes a drop set look like
 * three sets (ADR 0003).
 */
data class LoggedSet(
    val id: Long,
    val setIndex: Int,
    /** What this set was for, and therefore whether it earns any volume. */
    val type: SetType,
    val weight: Kilograms,
    val reps: Int,
    val note: String?,
)
