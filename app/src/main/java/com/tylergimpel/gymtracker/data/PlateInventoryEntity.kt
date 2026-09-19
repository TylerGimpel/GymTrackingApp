package com.tylergimpel.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.PlatePair

/**
 * One pair of plates the lifter owns, and how many of that pair.
 *
 * **Global, and one of them.** A lifter has the plates they have; the spec is explicit that this
 * is a single editable inventory rather than something held per exercise or per routine, because
 * the engine and the plate line both read it and two copies is two answers.
 *
 * Keyed by the weight, which is what makes an inventory a set rather than a list: there is no such
 * thing as two separate entries for 20 kg, only a count of how many pairs of them there are.
 *
 * Canonical kilograms like every other weight here. A lifter who entered 45 lb plates has 45 lb
 * plates — they read back as 45 in pounds, and what is stored underneath is nobody's business.
 */
@Entity(tableName = "plate_inventory")
data class PlateInventoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    /**
     * How many pairs, never how many plates.
     *
     * A bar loaded on one end is not a lift, so pairs are the only unit in which a plate is
     * usable, and counting them this way means nothing downstream has to remember to halve.
     */
    @ColumnInfo(name = "pairs")
    val pairs: Int,
)

/** This row as the domain has it. */
fun PlateInventoryEntity.toDomain(): PlatePair = PlatePair(Kilograms(weightKg), pairs)
