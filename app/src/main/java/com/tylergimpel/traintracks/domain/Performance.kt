package com.tylergimpel.traintracks.domain

import java.time.Duration

/**
 * What one **Effort** amounted to — whatever its exercise's **Logging type** records.
 *
 * A bench press effort is a weight and a rep count. A plank is a length of time and nothing else:
 * it has no weight, and a zero standing in for one is not "no weight" but a number every later
 * reader takes as real — the progression engine reading a first session on an exercise, a personal
 * best, a graph. Separate types rather than a row of nullable weight, reps and duration, so that an
 * effort recording nothing at all cannot be built and nothing downstream has to check.
 */
sealed interface Performance {

    /** One effort against an exercise loaded with a weight: the ordinary case. */
    data class WeightAndReps(val weight: Kilograms, val reps: Int) : Performance

    /**
     * One effort measured in time — a plank, a hang, a hold.
     *
     * No weight and no reps, deliberately and permanently: asking either of this type is a question
     * about an exercise it is not being performed on.
     */
    data class Timed(val duration: Duration) : Performance

    /**
     * One effort against an exercise whose load is the lifter's own body.
     *
     * [addedWeight] is a belt or a plate hung off them, and is absent far more often than not: a
     * pull-up and a weighted pull-up are one exercise, because putting a belt on must not split a
     * history in two.
     */
    data class BodyweightReps(val addedWeight: Kilograms?, val reps: Int) : Performance

    /**
     * One effort a machine helped with, and how much help it gave.
     *
     * Less of it is more progress, which is the one direction in this app that runs backwards
     * (ADR 0004). Nothing has to remember that: [loadOrNull] reports assistance as a negative load,
     * so every comparison built on it points the right way without being told.
     */
    data class Assisted(val assistance: Kilograms, val reps: Int) : Performance {
        init {
            // Help of nothing is an unweighted set, and would store as an added weight of zero —
            // which reads back as [BodyweightReps] and would not be this type at all.
            require(assistance.value > 0) { "An assisted effort recorded no assistance" }
        }
    }

    /**
     * One effort that covered ground — a treadmill, a rower, a run.
     *
     * Both halves together, because either alone is not the effort: five kilometres says nothing
     * about how hard it was and twenty-eight minutes says nothing about how far it went.
     */
    data class DistanceAndTime(val distance: Metres, val duration: Duration) : Performance
}

/**
 * What this performance recorded, for a reader that wants one measurement and can do without it.
 *
 * Extensions rather than members, and one per measurement rather than a weight and reps the timed
 * shape has to refuse: nothing here is part of what a performance promises, so adding a shape adds
 * an accessor instead of forcing every existing shape to answer for a measurement it never takes.
 * A reader that must handle every shape should match on the type instead, and be told by the
 * compiler when a new one arrives.
 */
val Performance.weightOrNull: Kilograms?
    get() = when (this) {
        is Performance.WeightAndReps -> weight
        // What was lifted on these two is a body, which this app never puts on the bar.
        is Performance.BodyweightReps, is Performance.Assisted -> null
        is Performance.Timed, is Performance.DistanceAndTime -> null
    }

val Performance.repsOrNull: Int?
    get() = when (this) {
        is Performance.WeightAndReps -> reps
        is Performance.BodyweightReps -> reps
        is Performance.Assisted -> reps
        is Performance.Timed, is Performance.DistanceAndTime -> null
    }

/**
 * What progression measures this effort by, signed so that **more is always more progress**.
 *
 * The one place the direction of every comparison is settled. A belt makes a pull-up harder, so it
 * counts upward from nothing; a machine's assistance makes one easier, so it counts downward from
 * nothing — which is what makes needing no help at all outrank any amount of it, and a lifter
 * weaning off the machine read as improving rather than collapsing.
 *
 * ADR 0004 records the danger this removes: less assistance is more progress, so beat, missed,
 * personal best and graph direction all flip, and getting it wrong means confidently coaching
 * someone to get weaker. Flipping is a rule every future reader would have to remember; a signed
 * number is not. Null where there is no load to compare — a hold and a run are not measured this way.
 */
val Performance.loadOrNull: Kilograms?
    get() = when (this) {
        is Performance.WeightAndReps -> weight
        is Performance.BodyweightReps -> addedWeight ?: Kilograms(0.0)
        is Performance.Assisted -> Kilograms(-assistance.value)
        is Performance.Timed, is Performance.DistanceAndTime -> null
    }

/** How long this took, off either shape that is measured in time. */
val Performance.durationOrNull: Duration?
    get() = when (this) {
        is Performance.Timed -> duration
        is Performance.DistanceAndTime -> duration
        is Performance.WeightAndReps, is Performance.BodyweightReps, is Performance.Assisted -> null
    }

val Performance.distanceOrNull: Metres?
    get() = when (this) {
        is Performance.DistanceAndTime -> distance
        is Performance.WeightAndReps, is Performance.Timed -> null
        is Performance.BodyweightReps, is Performance.Assisted -> null
    }
