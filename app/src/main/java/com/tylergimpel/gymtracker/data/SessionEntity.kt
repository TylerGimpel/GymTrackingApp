package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Session
import java.time.Instant
import java.time.LocalDate

/**
 * One logged trip to the gym.
 *
 * There is deliberately no routine column and no foreign key to one. A session records only what
 * happened; the routine it realised will later be copied onto it as a snapshot, so that revising a
 * routine can never reach logged history.
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Instant,

    /** Null while the session is still being logged. */
    @ColumnInfo(name = "finished_at")
    val finishedAt: Instant?,

    /** Stamped from [startedAt] when the session begins, and editable afterwards. */
    @ColumnInfo(name = "date")
    val date: LocalDate,

    @ColumnInfo(name = "note")
    val note: String?,
)

fun SessionEntity.toDomain() = Session(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    date = date,
    note = note,
)

fun Session.toEntity() = SessionEntity(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    date = date,
    note = note,
)
