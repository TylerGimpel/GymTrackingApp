package com.tylergimpel.gymtracker.domain

/**
 * The best a lifter has done on one exercise at one rep count.
 *
 * **Rep-specific, because a single "best set" is ambiguous** once weight and reps both vary: 100
 * for three and 80 for ten are not comparable as achievements, and calling either of them *the*
 * best means picking a formula and presenting its opinion as a fact. Best at three and best at
 * ten are two records, and both are true.
 *
 * [load] is signed like every other load here, which is what makes this right on an assisted
 * machine without a word of special-casing: a record there is *less help than last time*, and the
 * larger signed number is the better set whichever kind of exercise it came from.
 */
data class PersonalRecord(val reps: Int, val load: Kilograms)

/**
 * The best set at each rep count, from an exercise's own logged sets.
 *
 * **Recomputed rather than flagged.** ADR 0008 settles this: FitNotes' own imported PR flags are
 * discarded, because one definition spanning the whole history is worth more than two definitions
 * meeting at the import seam. A record that can be recomputed cannot go stale when a set is
 * corrected, either.
 *
 * Only sets at the working weight count. A warm-up is a rung on the way up and a calibration set
 * is the app finding out; neither is a lifter's best anything, and a ramp that happened to be
 * heavy would otherwise take the record for a weight nobody trained at.
 */
fun personalRecords(history: TrainingHistory): List<PersonalRecord> = history.sessions
    .asSequence()
    .flatMap(TrainedSession::sets)
    .filter { it.type.isAtTheWorkingWeight }
    .mapNotNull { set ->
        val load = set.performance.loadOrNull ?: return@mapNotNull null
        val reps = set.performance.repsOrNull ?: return@mapNotNull null
        PersonalRecord(reps, load)
    }
    // The larger signed load wins, which is more weight on a bar and less help on a machine.
    .groupBy(PersonalRecord::reps)
    .mapNotNull { (_, atThatCount) -> atThatCount.maxByOrNull { it.load.value } }
    .sortedBy(PersonalRecord::reps)

/**
 * Whether [set] is a better set than the best this exercise had at that rep count.
 *
 * The comparison every graph, badge and congratulation is built on, in one place so that there is
 * one place to get it wrong — and it cannot be got wrong here, because the load it compares is
 * signed. Anybody "fixing" this to read the other way breaks the assisted machines loudly rather
 * than quietly, which is the point of having it at all.
 */
fun TrainingHistory.beatsRecord(set: PersonalRecord): Boolean {
    val standing = personalRecords(this).firstOrNull { it.reps == set.reps } ?: return true
    return set.load.value > standing.load.value
}
