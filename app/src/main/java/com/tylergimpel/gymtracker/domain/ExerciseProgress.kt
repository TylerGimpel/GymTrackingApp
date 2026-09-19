package com.tylergimpel.gymtracker.domain

/**
 * What the app has worked out about one exercise, as opposed to what the lifter decided about it.
 *
 * Computed state, held per exercise: the estimate of what they can do, the band the exercise is
 * being worked in, and the load that follows from the two. Stored apart from the bank row for the
 * reason ADR 0009 gives — `exercise` ships inside the APK and a **Bank update** replaces those rows
 * wholesale, so an app release improving the shipped exercises would otherwise throw away
 * everything the engine had learned about the lifter.
 *
 * Apart from `exercise_preference` too, and for a different reason: that table holds choices, and
 * nothing here was chosen. A lifter who switches an exercise to pounds has said something; an
 * anchor of 72 kg is the app talking to itself, and the two want different answers the day one of
 * them is exported, reset or asked "did you mean this?".
 *
 * [workingLoad] is a load rather than a weight, like a **Prescription**'s is: on an assisted
 * machine it is negative, because help is weight taken off the lifter.
 */
data class ExerciseProgress(
    val anchor: Anchor,
    val repRange: RepRange,
    val workingLoad: Kilograms,
)
