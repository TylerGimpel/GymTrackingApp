package com.tylergimpel.gymtracker.domain

/**
 * What a routine does to one muscle, said as counts.
 *
 * **Facts, never grades.** Every field here is a number a lifter can read and act on; none of them
 * is a verdict on the routine they built. The same rule the logging screen keeps for its mode
 * badge: the app says what is true and leaves the judging to the person who is training.
 */
data class MuscleLoad(
    val muscle: String,
    /**
     * How many lifting days in the cycle train it **directly**.
     *
     * Direct work only, because this is the number the sentence is made of: reporting "shoulders
     * 2x a cycle" for an upper/lower routine with no shoulder exercise in it hides exactly the
     * lopsidedness this report exists to surface. A muscle worked only as secondary reads as
     * zero sessions and whatever fractional volume it actually gets, which is the truth about it.
     */
    val sessions: Int,
    /** Fractional sets a week, which is the unit every volume target is stated in. */
    val weeklySets: Double,
    /** The most it gets in any single day of the cycle. */
    val mostInOneSession: Double,
) {
    /** Where [weeklySets] sits in **Pelland's tiers**. A location, not a mark. */
    val tier: VolumeTier get() = VolumeTier.of(weeklySets)

    /**
     * Whether one day asks more of this muscle than a single session can use.
     *
     * Worth saying because the work is there and some of it is not doing anything: past the cap,
     * more sets in the same session stop showing up, and the same sets spread over another day
     * would. A fact about distribution, and not a reason to refuse anything.
     */
    val pastSessionCap: Boolean get() = mostInOneSession > PER_SESSION_CAP
}

/**
 * What this routine trains, muscle by muscle, heaviest first.
 *
 * The report behind *"this cycle trains chest 3× and back 1×"* — which is a fact that helps
 * somebody decide, where *"your routine is unbalanced"* is a grade nobody asked for. Sorted by
 * weekly volume so the contrast is the first thing read: a lifter scanning this wants what is
 * getting the most and the least, not an alphabet.
 *
 * **A muscle the cycle never trains is absent rather than zero.** Forty rows of nothing would bury
 * the handful that say something, and what is missing entirely is a coverage question for the
 * generator rather than a line in a report of what is there.
 *
 * Nothing here can refuse a routine. It is run over hand-built ones as readily as generated ones,
 * and a lopsided routine somebody built on purpose comes back reported, never rejected.
 */
fun Routine.whatItTrains(): List<MuscleLoad> {
    val lifting = days.filter { it.kind == RoutineDayKind.LIFTING }
    if (lifting.isEmpty()) return emptyList()

    val weekly = weeklyVolumeByMuscle()

    // Per day, so that "how much in one go" is answerable. Counted with the same arithmetic as
    // everything else, because a report in one convention and a target in the other is the error
    // ADR 0005 is about.
    val perDay = lifting.map { plannedVolumeByMuscle(listOf(it)) }

    // And separately, the days that train each muscle directly — which is not the same question,
    // and answering it with the volume map would count a bench press as a shoulder session.
    val directDays = lifting.map { day ->
        day.exercises.filter { it.sets != null }.flatMap { it.exercise.primaryMuscles }.toSet()
    }

    return weekly.keys.map { muscle ->
        MuscleLoad(
            muscle = muscle,
            sessions = directDays.count { muscle in it },
            weeklySets = weekly.getValue(muscle),
            mostInOneSession = perDay.maxOf { it[muscle] ?: 0.0 },
        )
    }.sortedWith(compareByDescending<MuscleLoad> { it.weeklySets }.thenBy { it.muscle })
}
