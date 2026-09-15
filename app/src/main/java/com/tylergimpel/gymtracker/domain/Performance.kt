package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * What one **Effort** amounted to — whatever its exercise's **Logging type** records.
 *
 * A bench press effort is a weight and a rep count. A plank is a length of time and nothing else:
 * it has no weight, and a zero standing in for one is not "no weight" but a number every later
 * reader takes as real — the progression engine reading a first session on an exercise, a personal
 * best, a graph. Separate types rather than a row of nullable weight, reps and duration, so that an
 * effort recording nothing at all cannot be built and nothing downstream has to check.
 *
 * The shapes the remaining logging types need — added weight, assistance, distance — arrive with
 * their own tickets and belong here beside these two.
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
val Performance.weightOrNull: Kilograms? get() = (this as? Performance.WeightAndReps)?.weight

val Performance.repsOrNull: Int? get() = (this as? Performance.WeightAndReps)?.reps

val Performance.durationOrNull: Duration? get() = (this as? Performance.Timed)?.duration
