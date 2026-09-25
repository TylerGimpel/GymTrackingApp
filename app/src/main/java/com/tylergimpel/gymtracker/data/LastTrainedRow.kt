package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.LocalDate

/** An exercise, and the last day it had a set logged against it. */
data class LastTrainedRow(
    @Embedded val exercise: ExerciseEntity,
    @ColumnInfo(name = "last_date") val lastDate: LocalDate,
)
