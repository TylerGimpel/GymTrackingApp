package com.tylergimpel.gymtracker.domain

/** Which side of the body an effort was performed on. */
enum class Side(val stored: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        private val BY_STORED = entries.associateBy(Side::stored)

        fun fromStored(value: String?): Side? = value?.let {
            requireNotNull(BY_STORED[it]) { "Unknown side `$it`" }
        }
    }
}

/**
 * A continuation of the same set at reduced weight with no rest.
 *
 * Belongs to the effort it continues and is never a set of its own. Deliberately kept out of the
 * effort's own weight and reps: those are what progression reads, and the fatigued later drops must
 * not feed an estimated one-rep max (ADR 0003).
 */
data class Drop(
    val id: Long,
    /** Where this drop falls in the sequence, from 1. */
    val orderIndex: Int,
    val weight: Kilograms,
    val reps: Int,
)

/**
 * One effort within a set — what was done in one go, and the row that stores it.
 *
 * What "what was done" means depends on the exercise: a weight and a rep count for a bench press, a
 * length of time for a plank. That is [performance], and it is a type rather than a handful of
 * nullable columns so that an effort recording nothing cannot exist.
 *
 * [side] is null for a set logged as a single entry, which is how every set is logged by default.
 * A unilateral exercise can be expanded to per-side, and then a set has two efforts that may differ,
 * because tracking an imbalance is the reason to expand it at all.
 */
data class Effort(
    val id: Long,
    val side: Side?,
    val performance: Performance,
    val note: String?,
    /**
     * The drops continuing this effort, in the order they were performed.
     *
     * Empty for an effort there is nothing to drop from: a **Drop** is a continuation at reduced
     * weight, which is a sentence about a loaded exercise and not about a plank.
     */
    val drops: List<Drop> = emptyList(),
)

/**
 * One unit of effort against an exercise, identified by its position within that exercise.
 *
 * **One set, however many rows record it.** A drop set carries child rows and a per-side set is
 * written as two, and neither is more than one set — so counting sets here is counting this list,
 * with nothing for a caller to remember and nothing to filter (ADR 0003).
 */
data class PerformedSet(
    val setIndex: Int,
    val type: SetType,
    /** One effort, or one per side. Never empty. */
    val efforts: List<Effort>,
    /**
     * The unit this one set was read in, or null where it reads as its exercise does.
     *
     * Null rather than a copy of the exercise's setting, so that switching an exercise between
     * kilograms and pounds moves its whole history's display with it and leaves behind only the
     * sets a lifter deliberately logged off an odd machine (ADR 0009). Display only either way:
     * the weights below are canonical kilograms whatever this says.
     */
    val displayUnit: WeightUnit? = null,
) {
    /** What progression reads: the first effort, before any drop. */
    val leadingEffort: Effort get() = efforts.first()

    val isPerSide: Boolean get() = efforts.size > 1

    val hasDrops: Boolean get() = efforts.any { it.drops.isNotEmpty() }
}
