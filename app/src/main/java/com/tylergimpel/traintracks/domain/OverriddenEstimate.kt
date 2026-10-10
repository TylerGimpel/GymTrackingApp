package com.tylergimpel.traintracks.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Whether the lifter has **overridden** the weight on this lift in every session for a month,
 * which is the **Anchor** being wrong rather than the lifter being difficult (#128).
 *
 * **A prompt, never a statistic** (ADR 0008). What this answers is whether to *offer* to find the
 * estimate again; nothing here is a rate or a count to show, and nothing should be built on it
 * that is.
 *
 * A session **overrode** when it was asked for a working weight and none of its working sets was
 * lifted at it. A session asked for nothing — imported history, a lift recorded rather than
 * coached — says nothing either way and is passed over. One session in the stretch that took the
 * weight offered means the estimate was right at least once, and the stretch starts again after it.
 *
 * The stretch has to cover [A_MONTH] and hold at least [FEWEST_SESSIONS], so a lift trained once a
 * month is not judged on two sessions.
 */
fun TrainingHistory.keepsOverriding(): Boolean {
    val stretch = sessions.sortedBy(TrainedSession::date)
        .mapNotNull { session -> session.overrodeTheWeight()?.let { overrode -> session.date to overrode } }
        .takeLastWhile { (_, overrode) -> overrode }
        .map { (date, _) -> date }
    if (stretch.size < FEWEST_SESSIONS) return false
    return ChronoUnit.DAYS.between(stretch.first(), stretch.last()) >= A_MONTH
}

/**
 * This history from [date] on, that day included, or the whole of it where [date] is null.
 *
 * What a reset estimate is found again from (#128): the log itself is never touched, so the sets
 * before a reset still count toward records, charts and everything else that reads history.
 */
fun TrainingHistory.from(date: LocalDate?): TrainingHistory =
    if (date == null) this else TrainingHistory(sessions.filter { !it.date.isBefore(date) })

/** True when asked for a working weight and never lifted it, false when it was, null when never asked. */
private fun TrainedSession.overrodeTheWeight(): Boolean? {
    val asked = sets.filter { it.type.isAtTheWorkingWeight && it.prescription.load != null }
    if (asked.isEmpty()) return null
    return asked.none { set ->
        val lifted = set.performance.loadOrNull ?: return@none false
        abs(lifted.value - set.prescription.load!!.value) < SAME_WEIGHT
    }
}

/** Invented: a month is four weeks, the span a stretch of overrides must cover. */
private const val A_MONTH = 28L

/** Invented: the fewest overridden sessions that are a pattern rather than a stubborn fortnight. */
private const val FEWEST_SESSIONS = 4

/**
 * Invented: two weights this close are the same weight (kilograms). Enough to absorb the rounding
 * of a weight typed in pounds and stored in kilograms, and far below any step a lifter can load.
 */
private const val SAME_WEIGHT = 0.01
