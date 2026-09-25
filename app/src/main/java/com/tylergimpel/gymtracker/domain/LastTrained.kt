package com.tylergimpel.gymtracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The last day an exercise had a set logged against it. */
data class ExerciseLastTrained(val exercise: Exercise, val on: LocalDate)

/** How long ago a muscle was last trained directly, in days. */
data class MuscleLastTrained(val muscle: String, val daysAgo: Long)

/**
 * How long since each muscle was trained, longest ago first (#127).
 *
 * **Information, never a warning** (ADR 0008): the question it answers is "what should I do next",
 * and the answer is a list of facts about what was done. Nothing here knows about a routine, a
 * skipped day or a plan, so nothing here can count a miss.
 *
 * **Direct work only**, as [MuscleLoad.sessions] counts it: a bench press trains the chest, and
 * reporting the triceps as trained by it would hide a week of no arm work behind a pressing day.
 * **Cardio trains no muscle here**, so a run the day after squats leaves the legs where the squats
 * left them — the calendar is where a cardio day shows.
 *
 * Longest ago first because that is the one being looked for; a muscle never trained is absent,
 * not "never", for the reason [Routine.whatItTrains] leaves untrained muscles out.
 */
fun lastTrainedByMuscle(trained: List<ExerciseLastTrained>, today: LocalDate): List<MuscleLastTrained> = trained
    .filterNot { it.exercise.isCardio }
    .flatMap { last -> last.exercise.primaryMuscles.map { muscle -> muscle to last.on } }
    .groupBy({ it.first }, { it.second })
    .map { (muscle, dates) -> MuscleLastTrained(muscle, ChronoUnit.DAYS.between(dates.max(), today)) }
    .sortedWith(compareByDescending<MuscleLastTrained> { it.daysAgo }.thenBy { it.muscle })
