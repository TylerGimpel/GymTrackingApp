package com.tylergimpel.gymtracker.data

import androidx.room.withTransaction
import com.tylergimpel.gymtracker.domain.Exercise
import com.tylergimpel.gymtracker.domain.ImportedSet
import com.tylergimpel.gymtracker.domain.NeutralImport
import com.tylergimpel.gymtracker.domain.Performance
import com.tylergimpel.gymtracker.domain.matchExactly
import com.tylergimpel.gymtracker.domain.LoggingType
import com.tylergimpel.gymtracker.domain.NameDecision
import com.tylergimpel.gymtracker.domain.NewExerciseDraft
import com.tylergimpel.gymtracker.domain.loggingTypeOf
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** How an import went: done, or recognised as a file already brought in (#102). */
sealed interface ImportOutcome {
    data class Imported(val result: ImportResult) : ImportOutcome

    /** This exact file was imported before, when; nothing was written the second time. */
    data class AlreadyImported(val importedAt: Instant) : ImportOutcome
}

/** What an import did, in the lifter's terms. */
data class ImportResult(
    val batchId: Long,
    val sessions: Int,
    val sets: Int,
    /** Names with no exact match, and how many sets each held, left out rather than guessed at. */
    val unmatched: Map<String, Int>,
    /** Sets read but not yet brought across, by why. */
    val notYetSupported: Map<String, Int>,
    /** Exercises of the lifter's own created for unmatched names (#99). */
    val created: List<String> = emptyList(),
    /**
     * Of those, the ones the source could not say the muscles of. Their sets count toward the
     * exercise's own progression and history, and toward no muscle's volume.
     */
    val createdWithoutMuscles: List<String> = emptyList(),
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
    /** What the lifter reads in, which decides whether an imported unit is a choice at all. */
    private val profile: ProfileRepository = database.profileRepository(),
) {
    private val sessions = database.sessionDao()
    private val exercises = ExerciseRepository(database, database.exerciseDao(), database.sessionDao(), profile)

    /**
     * Brings [records] in, unless this exact file already was.
     *
     * **A repeat is caught before anything is written** (#102): the file's fingerprint is looked
     * up among the imports still standing, so importing the same backup twice does not double a
     * training history. An import that was undone is gone, so its file can come in again.
     */
    suspend fun import(
        records: NeutralImport,
        bank: List<Exercise>,
        /**
         * What the lifter decided each unmatched name means, once per name (#99). A name with no
         * decision is left out and counted, as it always was; nothing is ever guessed.
         */
        decisions: Map<String, NameDecision> = emptyMap(),
    ): ImportOutcome = database.withTransaction {
        database.importBatchDao().withFingerprint(records.fingerprint)?.let {
            return@withTransaction ImportOutcome.AlreadyImported(it.importedAt)
        }
        ImportOutcome.Imported(write(records, bank, decisions))
    }

    /**
     * Removes exactly what the import [batchId] brought in, and nothing else (#102).
     *
     * Its sessions go, and with them every exercise, set, drop and superset that hangs off one,
     * by cascade. Nothing the lifter logged — before the import or since — points at the batch,
     * so nothing of theirs is touched. What an import filled in about an exercise where there was
     * a gap (#101) stays: it is a setting now, and the lifter may have come to rely on it.
     */
    suspend fun undo(batchId: Long): Unit = database.withTransaction {
        database.importBatchDao().deleteSessionsOf(batchId)
        database.importBatchDao().delete(batchId)
    }

    /** Every import still standing, newest first, with how many sessions each brought in. */
    fun observeListed(): Flow<List<ListedImport>> = database.importBatchDao().observeListed()

    suspend fun listed(): List<ListedImport> = database.importBatchDao().listed()

    private suspend fun write(
        records: NeutralImport,
        bank: List<Exercise>,
        decisions: Map<String, NameDecision>,
    ): ImportResult {
        val batchId = database.importBatchDao().insert(
            ImportBatchEntity(
                source = records.source.id,
                fingerprint = records.fingerprint,
                importedAt = clock.instant(),
                volumeReliable = records.source.volumeIsReliable,
            ),
        )

        // Exactly or not at all, and then what the lifter decided for the rest: never a guess.
        val exact = matchExactly(records.sets.map(ImportedSet::exerciseName).toSet(), bank)
        val created = mutableListOf<Exercise>()
        val matches: Map<String, Exercise?> = exact.mapValues { (name, found) ->
            found ?: when (val decision = decisions[name]) {
                NameDecision.CreateOwn -> createFor(name, records).also { created += it }
                is NameDecision.SameAs -> bank.firstOrNull { it.id == decision.exerciseId }
                null -> null
            }
        }
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
            val byExercise = sets.groupBy({ (_, exerciseId) -> exerciseId }, { (set, _) -> set })
            val supersetIds = supersetsFor(sessionId, byExercise)
            // In the order they were first done, each with its own sets numbered from one — and
            // a superset's members together, since a superset here is neighbouring exercises.
            inOrderWithSupersetsTogether(byExercise).forEachIndexed { index, exerciseId ->
                val itsSets = byExercise.getValue(exerciseId)
                val performedId = sessions.insert(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        orderIndex = index + 1,
                        supersetGroupId = itsSets.firstNotNullOfOrNull { it.supersetGroup }?.let(supersetIds::get),
                    ),
                )
                itsSets.forEachIndexed { setIndex, set -> sessions.insert(set.toEntity(performedId, setIndex + 1)) }
            }
        }

        applyExerciseSettings(records, matches)

        return ImportResult(
            batchId = batchId,
            sessions = sessionCount,
            sets = matched.size,
            unmatched = unmatched.groupingBy(ImportedSet::exerciseName).eachCount(),
            notYetSupported = records.notYetSupported,
            created = created.map(Exercise::name),
            createdWithoutMuscles = created.filter { it.primaryMuscles.isEmpty() }.map(Exercise::name),
        )
    }

    /**
     * An exercise of the lifter's own for an unmatched [name], logging what its sets were and
     * training what the source says it trains, where the source could say.
     */
    private suspend fun createFor(name: String, records: NeutralImport): Exercise {
        val first = records.sets.first { it.exerciseName == name }
        val loggingType = loggingTypeOf(first.performance)
        return exercises.createOwn(
            NewExerciseDraft(
                name = name,
                primaryMuscles = records.exercises[name]?.primaryMuscles.orEmpty(),
                loggingType = loggingType,
                category = if (loggingType == LoggingType.DISTANCE_TIME) CARDIO else STRENGTH,
            ),
        )
    }

    internal suspend fun insertSuperset(sessionId: Long, orderIndex: Int): Long = sessions.insert(
        SupersetGroupEntity(sessionId = sessionId, orderIndex = orderIndex, autoJump = true, restSeconds = null),
    )

    /**
     * What the source kept about each exercise, applied only where this app has no choice yet.
     *
     * A note, a unit, an increment or a rest the lifter already chose here is theirs, and an
     * import does not overwrite it: the source is filling gaps, not having the last word.
     */
    private suspend fun applyExerciseSettings(records: NeutralImport, matches: Map<String, Exercise?>) {
        val lifterUnit = profile.displayUnit()
        records.exercises.forEach { (name, kept) ->
            val exercise = matches[name] ?: return@forEach
            if (kept.note != null && exercise.note.isNullOrBlank()) exercises.note(exercise.id, kept.note)
            if (kept.displayUnit != null && exercise.displayUnit == lifterUnit) {
                exercises.setDisplayUnit(exercise.id, kept.displayUnit)
            }
            if (kept.increment != null && exercise.chosenIncrement == null) exercises.setIncrement(exercise.id, kept.increment)
            if (kept.rest != null && exercise.chosenRest == null) exercises.setRest(exercise.id, kept.rest)
        }
    }

    private companion object {
        /** The bank's category values for a created exercise: cardio for a run, otherwise strength. */
        const val CARDIO = "cardio"
        const val STRENGTH = "strength"
    }
}

/**
 * The source's supersets in one session, as the app's own: one for every group with at least two
 * of this session's exercises in it, source group id to the new group's id. A "superset" of one
 * exercise is not one.
 */
private suspend fun ImportRepository.supersetsFor(
    sessionId: Long,
    byExercise: Map<String, List<ImportedSet>>,
): Map<Long, Long> = buildMap {
    val members = byExercise.entries
        .mapNotNull { (exerciseId, sets) -> sets.firstNotNullOfOrNull { it.supersetGroup }?.let { it to exerciseId } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size >= 2 }
    members.keys.forEachIndexed { index, sourceGroup ->
        put(sourceGroup, insertSuperset(sessionId, index + 1))
    }
}

/**
 * The exercises in the order they were first done, with each superset's members pulled together
 * at the place its first member was done.
 */
private fun inOrderWithSupersetsTogether(byExercise: Map<String, List<ImportedSet>>): List<String> {
    val groupOf = byExercise.mapValues { (_, sets) -> sets.firstNotNullOfOrNull { it.supersetGroup } }
    val placed = LinkedHashSet<String>()
    byExercise.keys.forEach { exerciseId ->
        if (exerciseId in placed) return@forEach
        val group = groupOf[exerciseId]
        if (group == null) {
            placed += exerciseId
        } else {
            byExercise.keys.filter { groupOf[it] == group }.forEach { placed += it }
        }
    }
    return placed.toList()
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
