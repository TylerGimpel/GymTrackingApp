package com.tylergimpel.gymtracker.domain

/**
 * The columns of the export for spreadsheets, in order.
 *
 * **No column can hold a superset**, and none holds anything beyond the log: routines, exclusions,
 * plates and the profile live only in the backup. That is the loss the settings screen states
 * where this is offered, and a test pins it so it stays a decision rather than an accident (#98).
 */
const val SPREADSHEET_HEADER =
    "Date,Exercise,Set,Type,Side,Weight,Assistance,Unit,Reps,Duration (s),Distance (m),Note"

/**
 * The lifter's training as CSV a spreadsheet opens: one row per effort, oldest session first.
 *
 * **Lossy, and it says so.** It is an export, not a backup — the backup is what restores (#97).
 * What flattens:
 *
 * - **Supersets** are not represented at all.
 * - **A drop set's drops** are rows of their own, typed `drop`, under the set they belong to.
 * - **A per-side set's sides** are a row each, with the side in its own column.
 *
 * What it keeps is everything a number means: the date, the set type — so a warm-up is never read
 * as training — and each weight **in the unit the lifter read it in**, because a pound stack
 * exported in kilograms is a number nobody ever saw on the pin. Assistance has its own column,
 * since help is not a negative weight to anybody reading a spreadsheet.
 */
fun spreadsheetExport(sessions: List<LoggedSession>): String = buildString {
    append(SPREADSHEET_HEADER)
    sessions.sortedWith(compareBy({ it.session.date }, { it.session.startedAt })).forEach { logged ->
        logged.exercises.sortedBy(SessionExercise::orderIndex).forEach { performed ->
            performed.sets.sortedBy(PerformedSet::setIndex).forEach { set ->
                val unit = performed.displayUnitFor(set)
                set.efforts.forEach { effort ->
                    row(logged, performed, set, set.type.stored, effort.side, effort.performance, unit, effort.note)
                    effort.drops.sortedBy(Drop::orderIndex).forEach { drop ->
                        row(
                            logged,
                            performed,
                            set,
                            DROP,
                            effort.side,
                            Performance.WeightAndReps(drop.weight, drop.reps),
                            unit,
                            note = null,
                        )
                    }
                }
            }
        }
    }
}

private fun StringBuilder.row(
    logged: LoggedSession,
    performed: SessionExercise,
    set: PerformedSet,
    type: String,
    side: Side?,
    performance: Performance,
    unit: WeightUnit,
    note: String?,
) {
    fun weight(load: Kilograms) = unit.fromKilograms(load).asAmountText()
    val (weight, assistance, reps, seconds, metres) = when (performance) {
        is Performance.WeightAndReps -> Cells(weight(performance.weight), reps = performance.reps)
        is Performance.BodyweightReps -> Cells(performance.addedWeight?.let(::weight), reps = performance.reps)
        is Performance.Assisted -> Cells(assistance = weight(performance.assistance), reps = performance.reps)
        is Performance.Timed -> Cells(seconds = performance.duration.seconds)
        is Performance.DistanceAndTime -> Cells(
            seconds = performance.duration.seconds,
            metres = performance.distance.value.asAmountText(),
        )
    }
    // A unit only where a weight was written in it; a plank's row saying "kg" would be a claim
    // about a number that is not there.
    val unitText = if (weight != null || assistance != null) unit.stored else null
    append('\n')
    append(
        listOf(
            logged.session.date.toString(),
            performed.exercise.name,
            set.setIndex.toString(),
            type,
            side?.stored,
            weight,
            assistance,
            unitText,
            reps?.toString(),
            seconds?.toString(),
            metres,
            note,
        ).joinToString(",") { csvCell(it) },
    )
}

/** What one row says about the work itself; everything else on the row is about where it sits. */
private data class Cells(
    val weight: String? = null,
    val assistance: String? = null,
    val reps: Int? = null,
    val seconds: Long? = null,
    val metres: String? = null,
)

/**
 * A cell as RFC 4180 has it: bare where it can be, quoted with inner quotes doubled where a comma,
 * a quote or a line break would otherwise split it.
 */
private fun csvCell(value: String?): String = when {
    value == null -> ""
    value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } -> "\"" + value.replace("\"", "\"\"") + "\""
    else -> value
}

/** What a drop's own row says it is: part of the set above it, not a set of its own. */
private const val DROP = "drop"
