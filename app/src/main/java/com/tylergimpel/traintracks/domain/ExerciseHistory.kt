package com.tylergimpel.traintracks.domain

import java.time.LocalDate

/**
 * One session's sets of one exercise, as that exercise's history lists them (#121).
 *
 * Every set as it was logged, imported ones included and marked as such: the raw table the lifter
 * asked for beside the charts, with nothing summarised away.
 */
data class ExerciseHistoryEntry(
    /** Which row this is, and where the next page of history starts after it. */
    val sessionExerciseId: Long,
    val date: LocalDate,
    /** Whether the session came from an import rather than being logged here. */
    val imported: Boolean,
    val sets: List<PerformedSet>,
)

/**
 * How many sessions of an exercise's history are read at a time. Invented: enough to fill a
 * screen and scroll a little, so years of history cost one small read per screenful (#121).
 */
const val HISTORY_PAGE = 20
