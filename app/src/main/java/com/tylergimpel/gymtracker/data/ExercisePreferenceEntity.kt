package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
/**
 * What a lifter has decided about one exercise, as opposed to what the bank says about it.
 *
 * A table of its own rather than columns on `exercise`, which ships inside the APK and is replaced
 * wholesale by a bank update — see `docs/adr/0009-user-choices-live-apart-from-the-shipped-bank.md`.
 * A row exists only where something has actually been chosen, so absence is the answer "nothing
 * yet" rather than a value that has to be maintained for 896 exercises.
 */
@Entity(
    tableName = "exercise_preference",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExercisePreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /**
     * `kg` or `lb` — how this exercise is read. Never what its weights are stored in.
     *
     * Nullable, like the other two, because this row holds only what has actually been chosen: a
     * lifter who renamed an exercise and said nothing about its unit has not chosen kilograms, they
     * have said nothing, and the default answers for them.
     */
    @ColumnInfo(name = "display_unit")
    val displayUnit: String? = null,

    /**
     * What the lifter calls this exercise, where they have renamed it.
     *
     * Here rather than on the bank row so that a rename survives an update correcting the name the
     * bank shipped — and so that "pec deck" keeps finding Butterfly, because aliases resolve
     * against the bank's name and the bank's name is still there.
     */
    @ColumnInfo(name = "name")
    val name: String? = null,

    /** "Seat notch 3, use the long bar" — in front of the lifter every time they train it. */
    @ColumnInfo(name = "note")
    val note: String? = null,

    /**
     * What the lifter decided this exercise advances by, where they decided anything.
     *
     * Nullable like the rest: the bank's own derivation answers until somebody says otherwise, and a
     * row here holds only what was actually chosen. Here rather than on the bank row so that an
     * update improving what the bank derives cannot overrule a lifter who said they do not want this
     * lift coached.
     */
    @ColumnInfo(name = "progression_model")
    val progressionModel: String? = null,

    /**
     * How long this lifter rests on this exercise, where they have said.
     *
     * A suggestion the app derives from what a set is, and a lifter who disagrees is right about
     * their own gym: they know the machine is in a corner nobody walks past, or that they need
     * longer than the table says. Here rather than on the bank row because it is theirs, and
     * nullable like the rest because most exercises will never be told anything.
     */
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int? = null,

    /**
     * The band this lifter works this exercise in, where they have said.
     *
     * Overrules the band the routine's goal seeded, and here rather than beside that seed because
     * the two answer different questions: a seed is what the app worked out from a goal, and this
     * is somebody saying they want their curls in eights whatever they are training for. Keeping
     * them apart is what stops a goal change quietly taking a decision back.
     *
     * Both ends or neither, which the two being written together is what enforces.
     */
    @ColumnInfo(name = "rep_range_bottom")
    val repRangeBottom: Int? = null,

    @ColumnInfo(name = "rep_range_top")
    val repRangeTop: Int? = null,

    /**
     * Whether the lifter never wants this exercise suggested again.
     *
     * A hard ban rather than a ranking penalty, and not nullable like the rest of this row: a
     * dislike is a yes or a no, and "not said" and "no" mean the same thing here. Reversible,
     * global, and with no effect whatever on logged history — excluding an exercise is not
     * destructive, and the sets already against it are a record of training that happened.
     */
    @ColumnInfo(name = "disliked", defaultValue = "0")
    val disliked: Boolean = false,

    /**
     * The smallest step this exercise can actually move by, where the lifter has said.
     *
     * A dumbbell rack going up in fours, a machine whose pin moves in sevens, a bar somebody
     * loads with fractional plates the inventory does not list. Null is the ordinary state and
     * means the equipment decides — the plate inventory for a barbell, the logged positions for
     * a machine, and the lifter's own unit's step for everything else.
     */
    @ColumnInfo(name = "increment_kg")
    val incrementKg: Double? = null,
)
