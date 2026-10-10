package com.tylergimpel.traintracks.domain

/**
 * What a lifter has said about their body, which the calibration ramp may read.
 *
 * Two values because the cold-start research found a difference worth a starting weight and no
 * more; nothing reads it as anything else. **Null is an ordinary answer**, and the one everybody
 * starts with.
 */
enum class Sex { FEMALE, MALE }

/**
 * Who the lifter is, as far as they have chosen to say.
 *
 * **Every field is optional and none gates anything** (#29). The engine runs a calibration ramp
 * rather than a prediction, so what is here only shortens it: a lifter who fills in nothing trains
 * exactly as well, and starts a few sets further from their working weight.
 *
 * Bodyweight is not here. It has a history of its own, kept by the weigh-ins, and the latest of
 * those is what anything wanting a bodyweight reads.
 */
data class Profile(
    val sex: Sex? = null,
    /**
     * The year they were born, which is how an age is kept without going stale.
     *
     * An age stored as a number is wrong a year later and nobody would think to update it; a year
     * of birth is still right.
     */
    val birthYear: Int? = null,
    /**
     * Kept for whoever wants it later, and **never asked for**.
     *
     * The cold-start research measured it against bodyweight-adjusted lifts and found nothing — r
     * of .00 to −.08 — so asking for it up front would be friction for data the engine will not
     * use. It is here because a lifter may want it recorded, not because anything reads it.
     */
    val heightCm: Double? = null,
    val experience: Experience = Experience.TRAINED,
)
