package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ImportedSet
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.matchExactly
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId

/** What an import did, in the lifter's terms. */
data class ImportResult(
    val batchId: Long,
    val sessions: Int,
    val sets: Int,
    /** Names with no exact match, and how many sets each held, left out rather than guessed at. */
    val unmatched: Map<String, Int>,
    /** Sets read but not yet brought across, by why. */
    val notYetSupported: Map<String, Int>,
)

/**
 * The format-agnostic half of importing: neutral records in, sessions out (ADR 0007).
 *
 * Knows nothing about FitNotes. A second source is a second parser that hands this the same
 * records, which is the whole point of the seam.
 *
 * **Merges, never replaces.** Every imported session is new and points at the batch that brought
 * it; nothing the lifter logged first is touched. That batch is also what makes a repeat import
 * recognisable and an undo exact (#102).
 */
class ImportRepository(
    private val database: GymDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val sessions = database.sessionDao()

    suspend fun import(records: NeutralImport, bank: List<Exercise>): ImportResult = database.withTransaction {
        val batchId = database.importBatchDao().insert(
            ImportBatchEntity(
                source = records.source.id,
                fingerprint = records.fingerprint,
                importedAt = clock.instant(),
                volumeReliable = records.source.volumeIsReliable,
            ),
        )

        // Exactly or not at all: an unmatched name's sets are left out and counted, never put
        // under an exercise that merely looks close (#99 gives them somewhere to go).
        val matches = matchExactly(records.sets.map(ImportedSet::exerciseName).toSet(), bank)
        val matched = records.sets.mapNotNull { set -> matches[set.exerciseName]?.let { set to it.id } }
        val unmatched = records.sets.filter { matches[it.exerciseName] == null }

        var sessionCount = 0
        matched.sortedBy { (set, _) -> set.order }.groupBy { (set, _) -> set.date }.forEach { (date, sets) ->
            // FitNotes keeps no times, so an imported session is filed at midday on its own date:
            // the date is what the lifter recorded, and midday keeps it on that date in any zone.
            val filedAt = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
            val sessionId = sessions.insert(
                SessionEntity(
                    startedAt = filedAt,
                    finishedAt = filedAt,
                    date = date,
                    note = null,
                    routineName = null,
                    routineDayName = null,
                    importBatchId = batchId,
                ),
            )
            sessionCount++
            // In the order they were first done, each with its own sets numbered from one.
            sets.groupBy({ (_, exerciseId) -> exerciseId }, { (set, _) -> set })
                .entries.forEachIndexed { index, (exerciseId, itsSets) ->
                    val performedId = sessions.insert(
                        SessionExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, orderIndex = index + 1),
                    )
                    itsSets.forEachIndexed { setIndex, set -> sessions.insert(set.toEntity(performedId, setIndex + 1)) }
                }
        }

        ImportResult(
            batchId = batchId,
            sessions = sessionCount,
            sets = matched.size,
            unmatched = unmatched.groupingBy(ImportedSet::exerciseName).eachCount(),
            notYetSupported = records.notYetSupported,
        )
    }
}

/**
 * One imported set as a row, filling the columns [toPerformance] reads back, so a set comes back
 * out of the log exactly as it went in.
 */
private fun ImportedSet.toEntity(sessionExerciseId: Long, setIndex: Int): SetEntryEntity {
    val base = SetEntryEntity(
        sessionExerciseId = sessionExerciseId,
        setIndex = setIndex,
        type = typeOnArrival.stored,
        note = note,
        side = null,
        displayUnit = null,
    )
    return when (val done = performance) {
        is Performance.WeightAndReps -> base.copy(weightKg = done.weight.value, reps = done.reps)
        is Performance.BodyweightReps -> base.copy(reps = done.reps, addedWeightKg = done.addedWeight?.value)
        // Help is stored with the sign reversed, which is how the row says which way it went.
        is Performance.Assisted -> base.copy(reps = done.reps, addedWeightKg = -done.assistance.value)
        is Performance.Timed -> base.copy(durationSeconds = done.duration.seconds.toInt())
        is Performance.DistanceAndTime -> base.copy(
            distanceM = done.distance.value,
            durationSeconds = done.duration.seconds.toInt(),
        )
    }
}
