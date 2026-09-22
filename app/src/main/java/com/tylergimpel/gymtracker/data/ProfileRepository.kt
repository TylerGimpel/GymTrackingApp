package com.tylergimpel.gymtracker.data

import com.tylergimpel.gymtracker.domain.Kilograms
import com.tylergimpel.gymtracker.domain.Experience
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

    /**
     * How much training this lifter has behind them.
     *
     * Trained until they say otherwise, because that is the answer that overrules no preference:
     * assuming somebody is new and steering them is a worse mistake than assuming they are not.
     */
    fun observeExperience(): Flow<Experience> = profiles.observe().map {
        it?.experience?.let(::experienceOf) ?: Experience.TRAINED
    }

    private fun experienceOf(stored: String): Experience? =
        Experience.entries.firstOrNull { it.name == stored }

    /**
     * Records that this lifter reads weights in [displayUnit] from now on.
     *
     * Read before written, because the row holds more than this and the upsert replaces it
     * whole: building a fresh entity here would quietly take the lifter's barbell away with it.
     *
     * The plate inventory is deliberately left alone. Reading in pounds is not the same claim as
     * owning pound plates — plenty of people prefer one unit at a gym stocked in the other — and
     * silently restocking somebody's rack because they tapped a chip would be the app deciding
     * what is in a room it has never seen.
     */
    suspend fun setDisplayUnit(displayUnit: WeightUnit) = profiles.upsert(
        profiles.profile()?.copy(displayUnit = displayUnit.stored)
            ?: ProfileEntity(displayUnit = displayUnit.stored),
    )

    /** What the lifter's bar weighs, empty, or null until anything has said. */
    suspend fun bar(): Kilograms? = profiles.profile()?.barKg?.let(::Kilograms)

    /** Records what the bar in the lifter's gym weighs. */
    suspend fun setBar(weight: Kilograms) = profiles.upsert(
        profiles.profile()?.copy(barKg = weight.value) ?: ProfileEntity(barKg = weight.value),
    )

    /** Records how much training the lifter says they have behind them. */
    suspend fun setExperience(experience: Experience) = profiles.upsert(
        profiles.profile()?.copy(experience = experience.name)
            ?: ProfileEntity(experience = experience.name),
    )
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
