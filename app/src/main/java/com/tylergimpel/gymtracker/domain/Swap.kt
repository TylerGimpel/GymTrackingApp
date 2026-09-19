package com.tylergimpel.gymtracker.domain

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
 * to switch to. An exercise the lifter added themselves is a first-class candidate here as it is
 * everywhere else.
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
): List<Exercise> {
    val muscles = replacing.primaryMuscles.toSet()

    return bank.asSequence()
        .filterNot { it.id == replacing.id }
        // A dropped exercise is still in the bank for the history that points at it, and is not
        // something to offer anybody.
        .filterNot { it.isTombstoned }
        // The same movement, in the sense that matters: what it trains and how much of the body
        // it asks for. A substitute that works a different muscle is not a substitute.
        .filter { candidate -> candidate.primaryMuscles.any { it in muscles } }
        .filter { it.mechanic == replacing.mechanic }
        .filter { it.loggingType == replacing.loggingType }
        // Equipment the lifter actually has. An empty checklist means nothing has been said about
        // their gym yet, and offering everything beats offering nothing.
        .filter { equipment.isEmpty() || it.equipment == null || it.equipment in equipment }
        .sortedWith(
            // Trained before first — those need no ramp and no calibration, so switching costs
            // the lifter nothing but a walk across the gym. Alphabetical within each group, so
            // the list is stable between openings rather than reshuffling under a thumb.
            compareByDescending<Exercise> { it.id in trainedBefore }.thenBy { it.name.lowercase() },
        )
        .toList()
}
