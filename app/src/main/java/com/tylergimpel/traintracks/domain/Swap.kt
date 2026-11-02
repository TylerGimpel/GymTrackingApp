package com.tylergimpel.traintracks.domain

/**
 * What to train instead, when a machine is busy.
 *
 * A **Swap** is for this session only and by definition situational: the rack being occupied is
 * not a statement about the routine. Making a substitution permanent is a **Revision**, which
 * belongs to the routines spec and not here — which is why nothing about a swap is written down.
 * Training something else *is* the swap, and the session's **Plan** already records what the
 * evening was for, so the exercise that got skipped reads as **Unperformed** rather than absent.
 *
 * Candidates share the primary muscle, the mechanic, and the lifter's own equipment — and the
 * ones they have trained before come first, because those need no calibration and cost nothing
 * to switch to. After them come the lifts programs use for that muscle, ranked as the generator
 * ranks them (ADR 0013), then the rest. A lift that is never generated — Olympic, plyometric,
 * strongman, specialty — is offered only once the lifter has trained it, which is them choosing it.
 * An exercise the lifter added themselves is a first-class candidate here as it is everywhere else.
 *
 * @param replacing the exercise that cannot be done.
 * @param bank everything the app could offer, tombstones and all — filtered here.
 * @param equipment what the lifter's gym has, which is the routine's checklist.
 * @param trainedBefore the exercises they already have history for.
 */
fun swapCandidatesFor(
    replacing: Exercise,
    bank: List<Exercise>,
    equipment: Set<String>,
    trainedBefore: Set<String> = emptySet(),
    includeDisliked: Boolean = false,
): List<Exercise> {
    val muscles = replacing.primaryMuscles.toSet()

    return bank.asSequence()
        .filterNot { it.id == replacing.id }
        // A dropped exercise is still in the bank for the history that points at it, and is not
        // something to offer anybody.
        .filterNot { it.isTombstoned }
        // A hard ban, respected here as everywhere: offering one back because the alternatives
        // scored worse would break the only promise a dislike makes.
        .filter { includeDisliked || !it.isDisliked }
        // The same movement, in the sense that matters: what it trains and how much of the body
        // it asks for. A substitute that works a different muscle is not a substitute.
        .filter { candidate -> candidate.primaryMuscles.any { it in muscles } }
        .filter { it.mechanic == replacing.mechanic }
        // Equipment the lifter actually has. An empty checklist means nothing has been said about
        // their gym yet, and offering everything beats offering nothing.
        .filter { equipment.isEmpty() || it.equipment == null || it.equipment in equipment }
        .filter { it.id in trainedBefore || !it.isNeverGenerated }
        .sortedWith(
            // Trained before first — those need no ramp and no calibration, so switching costs
            // the lifter nothing but a walk across the gym. Then the lifts programs use, best
            // first, then beginner lifts before the rest, and by name last, so the list is stable
            // between openings rather than reshuffling under a thumb.
            compareByDescending<Exercise> { it.id in trainedBefore }
                .thenBy { candidate -> commonRankIn(candidate, muscles) ?: Int.MAX_VALUE }
                .thenBy { if (it.level == "beginner") 0 else 1 }
                .thenBy { it.name.lowercase() },
        )
        .toList()
}

/** Why a lift is offered in a swap, said under its name so the order is not a mystery. */
enum class SwapReason {
    /** Trained before, so there is nothing to calibrate. */
    DONE_BEFORE,

    /** One of the lifts programs use for the muscle (ADR 0013). */
    COMMON,

    /** It works the same muscle, and that is all that can be said for it. */
    SAME_MUSCLE,
}

/** Why a candidate is offered, and the muscle it shares with what it replaces. */
data class SwapWhy(val reason: SwapReason, val muscle: String?)

/** Why [candidate] is offered in place of [replacing]. */
fun swapWhyFor(candidate: Exercise, replacing: Exercise, trainedBefore: Set<String>): SwapWhy = SwapWhy(
    reason = when {
        candidate.id in trainedBefore -> SwapReason.DONE_BEFORE
        commonRankIn(candidate, replacing.primaryMuscles.toSet()) != null -> SwapReason.COMMON
        else -> SwapReason.SAME_MUSCLE
    },
    muscle = candidate.primaryMuscles.firstOrNull { it in replacing.primaryMuscles },
)

/** Where [candidate] ranks among the common lifts for any of [muscles], best taken; null if none. */
private fun commonRankIn(candidate: Exercise, muscles: Set<String>): Int? =
    muscles.mapNotNull { commonRankFor(it, widened = true)[candidate.id] }.minOrNull()
