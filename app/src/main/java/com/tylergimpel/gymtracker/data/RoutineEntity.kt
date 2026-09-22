package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.RepPreference
import com.tylergimpel.gymtracker.domain.Routine
import com.tylergimpel.gymtracker.domain.RoutineDay
import com.tylergimpel.gymtracker.domain.RoutineGoal
import com.tylergimpel.gymtracker.domain.setsFor
import com.tylergimpel.gymtracker.domain.PlannedExercise

/**
 * The whole repeating training plan.
 *
 * Goal, cadence, equipment and rep preference sit here rather than on the day structure, so editing
 * any of them is not a revision of the rotation and does not disturb the cycle.
 */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "goal")
    val goal: String,

    /** Expected sessions per week. Translates volume targets; never schedules. */
    @ColumnInfo(name = "cadence")
    val cadence: Int,

    /** The equipment available, doubling as the gym checklist. */
    @ColumnInfo(name = "equipment")
    val equipment: List<String>,

    @ColumnInfo(name = "rep_preference")
    val repPreference: String,
)

/** One ordered position within a routine. Never a calendar day. */
@Entity(
    tableName = "routine_day",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routine_id")],
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "routine_id")
    val routineId: Long,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "name")
    val name: String,
)

fun RoutineDayEntity.toDomain() = RoutineDay(
    id = id,
    orderIndex = orderIndex,
    name = name,
)

fun RoutineEntity.toDomain(days: List<RoutineDayWithExercises>) = Routine(
    id = id,
    name = name,
    goal = RoutineGoal.valueOf(goal),
    cadence = cadence,
    equipment = equipment.toSet(),
    repPreference = RepPreference.valueOf(repPreference),
    days = days.map { it.toDomain(RoutineGoal.valueOf(goal)) },
)

/**
 * A day and what it holds, with a set count for every exercise in it.
 *
 * [goal] answers for an entry that has never had a number of its own, which is every entry until
 * the generator starts writing them (#74). Derived on read rather than stored, so a goal that
 * changes moves the counts with it — unlike a **Rep range**, a set count is not something a lifter
 * is mid-progression on, and a day that kept prescribing five sets after they stopped training
 * for strength would be stale rather than stable.
 */
fun RoutineDayWithExercises.toDomain(goal: RoutineGoal) = day.toDomain().copy(
    exercises = exercises
        .sortedBy { it.entry.orderIndex }
        .map { PlannedExercise(it.exercise.toDomain(), it.entry.sets ?: setsFor(goal)) },
)
