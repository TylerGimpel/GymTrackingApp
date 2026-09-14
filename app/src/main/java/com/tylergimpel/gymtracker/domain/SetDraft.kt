package com.tylergimpel.gymtracker.domain

/**
 * A set part-way through being entered: what the screen currently holds, and what it means.
 *
 * Keeping the raw text here rather than parsed values lets the screen stay a dumb pair of fields
 * while the rules about what counts as a weight live somewhere they can be tested. Everything that
 * reads is canonical kilograms — the unit a lifter reads a machine in is a display concern that
 * arrives with its own ticket.
 */
data class SetDraft(
    val weightText: String = "",
    val repsText: String = "",
    val note: String? = null,
) {
    /** The weight entered, or null while it is blank or unreadable. Zero is a real answer. */
    val weight: Kilograms?
        get() = weightText.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let(::Kilograms)

    /** The reps entered, or null until they read as a whole positive count. */
    val reps: Int?
        get() = repsText.trim().toIntOrNull()?.takeIf { it > 0 }

    val isLoggable: Boolean get() = weight != null && reps != null
}
