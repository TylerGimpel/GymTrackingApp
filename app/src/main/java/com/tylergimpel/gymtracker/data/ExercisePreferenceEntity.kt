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
)
