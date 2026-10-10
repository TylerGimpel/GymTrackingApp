package com.tylergimpel.traintracks.data

import com.tylergimpel.traintracks.domain.Increment
import com.tylergimpel.traintracks.domain.Kilograms
import com.tylergimpel.traintracks.domain.PlatePair
import com.tylergimpel.traintracks.domain.WeightUnit
import com.tylergimpel.traintracks.domain.rackFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The barbell, as one answer assembled from the bar and the plates.
 *
 * **One inventory, read by two things.** The engine rounds a recommendation to what these plates
 * can build, and the plate line under the prescription says how to build it. They are the same
 * question asked twice, so they are answered from one place and can never disagree (#51).
 */
class PlateRepository(
    private val plates: PlateDao,
    private val profile: ProfileRepository,
) {
    /**
     * The bar and the pairs, as the engine wants them.
     *
     * **An empty inventory means nobody has described their gym**, not that there is nothing to
     * lift, so the standard rack for the unit they read in stands in until they say otherwise.
     * That is also the only thing that can work on a fresh install, which arrives already built
     * from the asset and runs no migration that could have seeded it.
     */
    suspend fun barbell(readIn: WeightUnit? = null): Increment.Plates = assembled(
        bar = profile.bar(),
        pairs = plates.all().map(PlateInventoryEntity::toDomain),
        unit = readIn ?: profile.displayUnit(),
    )

    /** The same, watched, for a settings screen that edits it while the engine reads it. */
    fun observeBarbell(): Flow<Increment.Plates> =
        combine(plates.observeAll(), profile.observeDisplayUnit()) { rows, unit ->
            assembled(profile.bar(), rows.map(PlateInventoryEntity::toDomain), unit)
        }

    /**
     * The inventory as the engine reads it, with the standard rack standing in for what is missing.
     *
     * The bar and the plates fall back separately: a lifter who has corrected their bar and not
     * their plates has said something true about one and nothing about the other, and overwriting
     * either with the other's default would be the app inventing equipment.
     */
    private fun assembled(
        bar: Kilograms?,
        pairs: List<PlatePair>,
        unit: WeightUnit,
    ): Increment.Plates {
        val standard = rackFor(unit)
        return Increment.Plates(
            bar = bar ?: standard.bar,
            plates = pairs.ifEmpty { standard.plates },
        )
    }

    /** Records how many pairs of [weight] the lifter has, or removes it at none. */
    suspend fun setPairs(weight: Kilograms, pairs: Int) =
        if (pairs <= 0) {
            plates.remove(weight.value)
        } else {
            plates.put(PlateInventoryEntity(weight.value, pairs))
        }

    /** Records what the bar weighs empty. */
    suspend fun setBar(weight: Kilograms) = profile.setBar(weight)

    /** Every pair, heaviest first, for a screen that lists them. */
    suspend fun pairs(): List<PlatePair> = plates.all().map(PlateInventoryEntity::toDomain)
}

/** The plates in the room, assembled the one way, so nothing has to remember the two halves. */
fun GymDatabase.plateRepository(): PlateRepository =
    PlateRepository(plateDao(), profileRepository())
