package com.tylergimpel.traintracks.domain

import java.time.LocalDate

/**
 * One session on an exercise's strength chart (#122): its best set as an estimated one-rep
 * maximum, and the **Anchor** just after it.
 *
 * The point is the achievement and the line is what the app does with it. Plotting both is what
 * makes the smoothing legible — a great session spikes as a point while the line only moves part
 * of the way, which is why the next prescription did not jump to it.
 */
data class StrengthPoint(val date: LocalDate, val best: Kilograms, val anchor: Kilograms)

/**
 * This exercise's strength chart, across **all** of its history, imports included (#122).
 *
 * Strength crosses the import seam with no caveat: it reads top sets, which imported history
 * records accurately (ADR 0008). Signed like every load, so an assisted machine's line climbs as
 * the help comes off.
 *
 * Where the lifter reset the estimate ([resetOn], #128) the line starts again from the session
 * after it, as the engine does, and the points before it stay where they were.
 */
fun TrainingHistory.strengthSeries(resetOn: LocalDate? = null): List<StrengthPoint> {
    val (before, after) = sessions.partition { resetOn == null || !it.date.isAfter(resetOn) }
    return Anchor.series(before) + Anchor.series(after)
}

/** One session on an exercise's volume chart: how many of its sets counted (#125). */
data class VolumePoint(val date: LocalDate, val sets: Int)

/**
 * An exercise's volume chart, and whether it starts later than the history does.
 *
 * [startsAfterHistory] is what earns the chart its one line of explanation: where it is false the
 * chart simply begins with the history, and there is nothing to explain.
 */
data class VolumeSeries(val points: List<VolumePoint>, val startsAfterHistory: Boolean)

/**
 * Counted sets per session, **from where set types became known** (#125, ADR 0008).
 *
 * A source that never recorded set types counts every warm-up as work, so a volume chart spanning
 * it would step down on the day the lifter switched apps and read as a collapse that never
 * happened. No correction exists for information that was never recorded, so the chart does not
 * draw it: it begins after the last session whose volume cannot be trusted.
 *
 * The boundary is **the source's reliability, never "was this imported"**: history from a source
 * that said what each set was counts from its beginning.
 */
fun TrainingHistory.volumeSeries(): VolumeSeries {
    val ordered = sessions.sortedBy(TrainedSession::date)
    val lastUnknown = ordered.indexOfLast { !it.volumeReliable }
    return VolumeSeries(
        points = ordered.drop(lastUnknown + 1).map { session ->
            VolumePoint(session.date, session.sets.count { it.type.countsTowardVolume })
        },
        startsAfterHistory = lastUnknown >= 0,
    )
}
