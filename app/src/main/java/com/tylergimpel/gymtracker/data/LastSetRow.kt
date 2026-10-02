package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo

/** One working set as the bank reads it: which exercise, and what it was (#148). */
data class LastSetRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "reps") val reps: Int?,
)
