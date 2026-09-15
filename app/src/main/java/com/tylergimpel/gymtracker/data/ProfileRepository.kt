package com.tylergimpel.gymtracker.data

import com.tylergimpel.gymtracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * What the lifter reads weights in.
 *
 * One place where "the unit they read in" is decided, because the answer has two halves that must
 * not come apart: what they chose, and what to assume until they choose. An exercise with a unit of
 * its own still overrides this — a machine carries its own label — but everything they have not
 * said anything about reads the way they think.
 *
 * [systemDefault] is the seed, taken from the device's locale by the caller. It is consulted only
 * while nothing has been chosen: once the lifter taps a unit chip, that is the answer, and a phone
 * that changes locale does not quietly re-read their whole history in another unit.
 */
class ProfileRepository(
    private val profiles: ProfileDao,
    private val systemDefault: WeightUnit,
) {
    fun observeDisplayUnit(): Flow<WeightUnit> =
        profiles.observe().map { WeightUnit.fromStored(it?.displayUnit) ?: systemDefault }

    suspend fun displayUnit(): WeightUnit =
        WeightUnit.fromStored(profiles.profile()?.displayUnit) ?: systemDefault

    /** Records that this lifter reads weights in [displayUnit] from now on. */
    suspend fun setDisplayUnit(displayUnit: WeightUnit) =
        profiles.upsert(ProfileEntity(displayUnit = displayUnit.stored))
}

/**
 * The profile as this device's lifter reads it, seeded from the device's own locale.
 *
 * The seeding lives here rather than in each screen that needs it, so that every part of the app
 * agrees about what a lifter who has chosen nothing reads in. Tests build the repository directly
 * with the unit they mean to test.
 */
fun GymDatabase.profileRepository(): ProfileRepository =
    ProfileRepository(profileDao(), WeightUnit.forLocale(Locale.getDefault()))
