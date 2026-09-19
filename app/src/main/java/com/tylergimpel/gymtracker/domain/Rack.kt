package com.tylergimpel.gymtracker.domain

/**
 * What the app assumes is in the room, until the lifter says otherwise.
 *
 * Two racks rather than one converted, because **20 kg and 45 lb are different bars**. A gym
 * stocked in pounds has 45s and 25s and 10s on the rack; converting a metric inventory into
 * pounds produces 44.09 and 33.07 and a prescription with two decimal places on it, which is a
 * number nobody can load and nobody asked for (#64).
 *
 * Seeded rather than left empty: an empty inventory can build nothing, and a lifter who has never
 * opened the settings should still be offered weights that exist. Both are ordinary commercial
 * racks — one pair of most things and two of the heaviest — and both are the lifter's to correct
 * the moment they are wrong.
 */
val METRIC_RACK = Increment.Plates(
    bar = Kilograms(20.0),
    plates = listOf(
        PlatePair(Kilograms(20.0), pairs = 2),
        PlatePair(Kilograms(15.0), pairs = 1),
        PlatePair(Kilograms(10.0), pairs = 1),
        PlatePair(Kilograms(5.0), pairs = 1),
        PlatePair(Kilograms(2.5), pairs = 1),
        PlatePair(Kilograms(1.25), pairs = 1),
    ),
)

/** The same rack in the units it is actually cast in, for a lifter who reads in pounds. */
val IMPERIAL_RACK = Increment.Plates(
    bar = pounds(45.0),
    plates = listOf(
        PlatePair(pounds(45.0), pairs = 2),
        PlatePair(pounds(35.0), pairs = 1),
        PlatePair(pounds(25.0), pairs = 1),
        PlatePair(pounds(10.0), pairs = 2),
        PlatePair(pounds(5.0), pairs = 1),
        PlatePair(pounds(2.5), pairs = 1),
    ),
)

/**
 * The rack to start somebody off with, by the unit they read in.
 *
 * The only place the app guesses at equipment, and it guesses at the commonest thing rather than
 * the safest: a wrong plate is corrected in the settings in a few seconds, and an empty rack
 * offers nothing at all.
 */
fun rackFor(unit: WeightUnit): Increment.Plates =
    if (unit == WeightUnit.POUNDS) IMPERIAL_RACK else METRIC_RACK

/** A weight cast in pounds, stored in kilograms like everything else. */
private fun pounds(amount: Double): Kilograms = WeightUnit.POUNDS.toKilograms(amount)
