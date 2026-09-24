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
val FITNOTES = ImportSource(id = "fitnotes", declaresSetTypes = false, declaresSupersets = true)

/**
 * What a source kept about an exercise, beyond the sets done on it (#101).
 *
 * Every field is what the lifter set there, or null where they set nothing; an import only fills
 * a choice this app has not already been given (see `ImportRepository`).
 */
data class ImportedExercise(
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
    /** Sets the parser read but cannot yet bring across, counted by the reason why. */
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
