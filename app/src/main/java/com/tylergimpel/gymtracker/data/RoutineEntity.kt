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
import com.tylergimpel.gymtracker.domain.SPLIT_SHAPES
import com.tylergimpel.gymtracker.domain.shapeOf
import com.tylergimpel.gymtracker.domain.RoutineDayKind
import com.tylergimpel.gymtracker.domain.CardioPlacement
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

    /**
     * Where this lifter's cardio goes, if they do any.
     *
     * A property of the routine like the goal beside it, so changing it is not a revision of the
     * rotation — though moving cardio onto or off its own day does change which days exist, and
     * that part is a revision when it happens.
     */
    @ColumnInfo(name = "cardio", defaultValue = "NONE")
    val cardio: String = CardioPlacement.NONE.name,

    /**
     * The **Split shape** this routine was laid out from, where it was laid out from one.
     *
     * Stored rather than remembered on screen, because regenerating needs it and a lifter who
     * closed the app should not lose the ability to ask for a different routine. Null for one
     * built by hand or rebuilt from the log: regenerating those would be inventing a structure
     * nobody chose.
     */
    @ColumnInfo(name = "shape_id")
    val shapeId: String? = null,

    /**
     * Which of the routines [shapeId] could have produced this one is.
     *
     * Stored beside the shape because it answers the same question the shape does — how this
     * routine was arrived at — and because the seed was the one half of that answer the app could
     * not remember across a restart. Held only on screen, it reset to zero every launch, so the
     * next "build it again" reproduced the routine already showing and the lifter had to ask
     * twice to get anywhere.
     *
     * Null wherever [shapeId] is: a routine built by hand or rebuilt from the log was generated
     * from nothing, so no number reproduces it.
     */
    @ColumnInfo(name = "seed")
    val seed: Int? = null,

    /**
     * How long a session can run, in minutes (#146): the time ceiling a day is checked against.
     *
     * The lifter's to set now rather than a constant, and ninety until they do, which is what the
     * constant was: a routine that said nothing before says the same now.
     */
    @ColumnInfo(name = "session_minutes", defaultValue = "90")
    val sessionMinutes: Int = Routine.USUAL_SESSION_MINUTES,
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

    /**
     * Whether this position is for lifting or for cardio.
     *
     * Stored rather than inferred from what the day holds: an empty cardio day is still a cardio
     * day, and a lifter who has put a treadmill into a lifting day has not turned it into one.
     */
    @ColumnInfo(name = "kind", defaultValue = "LIFTING")
    val kind: String = RoutineDayKind.LIFTING.name,
)

fun RoutineDayEntity.toDomain() = RoutineDay(
    id = id,
    orderIndex = orderIndex,
    name = name,
    kind = RoutineDayKind.valueOf(kind),
)

fun RoutineEntity.toDomain(days: List<RoutineDayWithExercises>) = Routine(
    id = id,
    name = name,
    goal = RoutineGoal.valueOf(goal),
    cadence = cadence,
    equipment = equipment.toSet(),
    repPreference = RepPreference.valueOf(repPreference),
    cardio = CardioPlacement.valueOf(cardio),
    shape = shapeId?.let { id -> SPLIT_SHAPES.firstOrNull { it.id == id } ?: shapeOf(id) },
    seed = seed,
    sessionMinutes = sessionMinutes,
    days = days.map { it.toDomain(RoutineGoal.valueOf(goal)) },
)

/**
 * A day and what it holds, with a set count for every exercise in it.
 *
 * [goal] answers for an entry that has never had a number of its own, which is every entry until
 * the generator starts writing them (#74). Derived on read rather than stored, so a goal that
 * changes moves the counts with it — unlike a **Rep range**, a set count is not something a
 * lifter is mid-progression on, and a day still prescribing five sets after they stopped training
 * for strength would be stale rather than stable.
 */
fun RoutineDayWithExercises.toDomain(goal: RoutineGoal) = day.toDomain().copy(
    exercises = exercises
        .sortedBy { it.entry.orderIndex }
        .map {
            PlannedExercise(
                exercise = it.exercise.toDomain(),
                sets = it.entry.sets ?: setsFor(goal),
                pinned = it.entry.pinned,
            )
        },
)
