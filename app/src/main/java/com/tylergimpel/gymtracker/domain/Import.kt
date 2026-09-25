package com.tylergimpel.gymtracker.domain

import java.time.Duration
import java.time.LocalDate

/**
 * Where imported history came from, and what that source is able to say about it (ADR 0007).
 *
 * **A source declares what it can supply**, and reliability follows from the declaration rather
 * than from the fact of importing. FitNotes never recorded what a set was for, so its volume is
 * approximate; a source that exports set types would be as trustworthy as anything logged here.
 */
data class ImportSource(
    val id: String,
    /** Whether each set arrives saying what it was for: warm-up, working, drop. */
    val declaresSetTypes: Boolean,
    /** Whether sets arrive grouped into supersets. */
    val declaresSupersets: Boolean,
    /** What the lifter knows the source as — a product name, the same in every language. */
    val name: String = id,
) {
    /**
     * Whether volume counted from this source can be trusted.
     *
     * Only where set types were declared: counting sets of unknown purpose as working sets would
     * silently inflate every volume figure by however many warm-ups there were. Progression is
     * unaffected either way — the anchor reads top sets, and a top set is still a top set.
     */
    val volumeIsReliable: Boolean get() = declaresSetTypes
}

/**
 * FitNotes' `.fitnotes` backup: no set types, which is why its volume is approximate, and
 * supersets, which the backup keeps and its CSV silently drops.
 */
val FITNOTES = ImportSource(id = "fitnotes", declaresSetTypes = false, declaresSupersets = true, name = "FitNotes")

/** The source an import recorded as [id], or null for one this version does not know. */
fun importSourceWithId(id: String): ImportSource? = listOf(FITNOTES).firstOrNull { it.id == id }

/**
 * What a source kept about an exercise, beyond the sets done on it (#101).
 *
 * Every field is what the lifter set there, or null where they set nothing; an import only fills
 * a choice this app has not already been given (see `ImportRepository`).
 */
data class ImportedExercise(
    /**
     * What the source says it trains, where that is plain from what it kept.
     *
     * Only ever used for an exercise created from an unmatched name (#99), so that its sets count
     * toward the right muscle's volume. Empty where the source could not say.
     */
    val primaryMuscles: List<String> = emptyList(),
    val note: String? = null,
    /** The unit the lifter read it in there, where that was an explicit choice. */
    val displayUnit: WeightUnit? = null,
    val increment: Kilograms? = null,
    val rest: Duration? = null,
)

/**
 * One set as any source hands it over, before anything about this app is applied to it.
 *
 * **The fields a source may never fill are not dead code** (ADR 0007). [setType] is empty for
 * everything FitNotes exports, and exists so a source that does supply it is a new parser rather
 * than a rework of the pipeline — which is exactly what [supersetGroup] turned out to be when
 * FitNotes' supersets were read (#101).
 */
data class ImportedSet(
    /** The day it was trained, exactly as the source recorded it. */
    val date: LocalDate,
    /** The source's own name for the exercise, which the lifter wrote; matched exactly or not at all. */
    val exerciseName: String,
    /** The source's order, which is the order the sets were done in. */
    val order: Long,
    val performance: Performance,
    /** What the set was for, where the source said. Absent is not the same as working. */
    val setType: SetType? = null,
    /** Which superset it was in, where the source kept that. */
    val supersetGroup: Long? = null,
    val note: String? = null,
) {
    /** What it is logged as: its declared type, or working where the source never said. */
    val typeOnArrival: SetType get() = setType ?: SetType.WORKING
}

/**
 * Everything one file held, as neutral records, and whatever the parser could not turn into one.
 *
 * [fingerprint] identifies the file, so importing it again can be recognised (#102).
 */
data class NeutralImport(
    val source: ImportSource,
    val fingerprint: String,
    val sets: List<ImportedSet>,
    /** What the source kept about each exercise, by the source's own name for it. */
    val exercises: Map<String, ImportedExercise> = emptyMap(),
    /** Sets the parser could not read as any kind of set, left out and counted by the reason why. */
    val notYetSupported: Map<String, Int> = emptyMap(),
)

/**
 * Each of [names] matched to the exercise it exactly is, or to nothing.
 *
 * **Exactly or not at all** (ADR 0007). Case and spacing are forgiven, because they are not a
 * difference in what was lifted; nothing else is. A name that is merely close goes to the lifter
 * to decide (#99) — guessing that `Hammer Strength Row` is `Barbell Row` would fuse two lifts'
 * histories and corrupt progression for both.
 *
 * Matched against what the lifter calls an exercise as well as what the bank does, and never
 * against a **Tombstone**, which is there for history already pointing at it and is not somewhere
 * to put more.
 */
fun matchExactly(names: Set<String>, bank: List<Exercise>): Map<String, Exercise?> {
    val byName = buildMap {
        bank.filterNot(Exercise::isTombstoned).forEach { exercise ->
            putIfAbsent(normalised(exercise.bankName), exercise)
            exercise.userName?.let { putIfAbsent(normalised(it), exercise) }
        }
    }
    return names.associateWith { byName[normalised(it)] }
}

private fun normalised(name: String): String = name.trim().lowercase().replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")

/** A name from the source with no exact match here, and how many sets hang on it. */
data class Unmatched(val name: String, val sets: Int)

/**
 * What the lifter decided an unmatched name means, once for every set under it (#99).
 *
 * **Creating an exercise of their own is the default**, and for a gym full of machines the bank
 * has never heard of it is the correct outcome rather than a failure.
 */
sealed interface NameDecision {
    /** A new **User-authored exercise**, under the name the lifter gave it there. */
    data object CreateOwn : NameDecision

    /** The same exercise as one already here, which the lifter chose themselves. */
    data class SameAs(val exerciseId: String) : NameDecision
}

/**
 * Every name in [records] with no exact match in [bank], once each, most history first.
 *
 * Most first because that is where a wrong decision would cost the most; and once each, because
 * the mapping is a single chore against years of history rather than a question per set.
 */
fun unmatchedIn(records: NeutralImport, bank: List<Exercise>): List<Unmatched> {
    val matched = matchExactly(records.sets.map(ImportedSet::exerciseName).toSet(), bank)
    return records.sets.filter { matched[it.exerciseName] == null }
        .groupingBy(ImportedSet::exerciseName).eachCount()
        .map { (name, sets) -> Unmatched(name, sets) }
        .sortedWith(compareByDescending<Unmatched> { it.sets }.thenBy { it.name })
}

/**
 * What an import is about to bring in, shown before anything is written (#103).
 *
 * Counted as the import will do it — one session a date, every readable set — on the understanding
 * that every unmatched name is decided before it goes ahead, as the preview requires. [from] and
 * [to] are null for a file with nothing readable in it.
 */
data class ImportPreview(
    val from: LocalDate?,
    val to: LocalDate?,
    val sessions: Int,
    val sets: Int,
    /** Sets that will be left out because they could not be read as any kind of set. */
    val unreadable: Int,
    /** The names needing a decision, as [unmatchedIn] lists them. */
    val unmatched: List<Unmatched>,
    /** Sessions an earlier import from the same source already brought in, which this one skips (#116). */
    val alreadyHere: Int = 0,
)

/**
 * What going ahead would bring in, less the dates an earlier import of the same source already
 * did ([alreadyHere]): a newer backup of the same history is mostly the same history.
 */
fun previewOf(records: NeutralImport, bank: List<Exercise>, alreadyHere: Set<LocalDate> = emptySet()): ImportPreview {
    val fresh = records.without(alreadyHere)
    val dates = fresh.sets.map(ImportedSet::date)
    return ImportPreview(
        from = dates.minOrNull(),
        to = dates.maxOrNull(),
        sessions = dates.toSet().size,
        sets = fresh.sets.size,
        unreadable = records.notYetSupported.values.sum(),
        unmatched = unmatchedIn(fresh, bank),
        alreadyHere = records.sessionsOn(alreadyHere),
    )
}

/**
 * These records less every set on one of [dates] — the dates an earlier, still-standing import of
 * the same source filed a session on (#116).
 *
 * **Only the same source's history counts as already here.** A session the lifter logged in the
 * app on the same day is not the same session arriving twice; two sessions on one day happen.
 * Matched by date because a FitNotes session is a date and nothing else: it keeps no time and no
 * id that would survive into a second backup.
 */
fun NeutralImport.without(dates: Set<LocalDate>): NeutralImport =
    if (dates.isEmpty()) this else copy(sets = sets.filterNot { it.date in dates })

/** How many of [dates] these records have a session on. */
fun NeutralImport.sessionsOn(dates: Set<LocalDate>): Int = sets.map(ImportedSet::date).toSet().count(dates::contains)

/** The **Logging type** a set like this one is recorded in, for an exercise created to hold it. */
fun loggingTypeOf(performance: Performance): LoggingType = when (performance) {
    is Performance.WeightAndReps -> LoggingType.WEIGHT_REPS
    is Performance.BodyweightReps -> LoggingType.BODYWEIGHT_REPS
    is Performance.Assisted -> LoggingType.ASSISTED
    is Performance.Timed -> LoggingType.TIME
    is Performance.DistanceAndTime -> LoggingType.DISTANCE_TIME
}
