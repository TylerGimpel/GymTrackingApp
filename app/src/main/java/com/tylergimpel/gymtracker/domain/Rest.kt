package com.tylergimpel.gymtracker.domain

import java.time.Duration

/**
 * How long to rest after one set.
 *
 * **Derived from what the set is**, not from the exercise it belongs to. A heavy triple and a
 * lighter set of twelve on the same machine are different work and want different answers, so the
 * question is asked of the set: what type it is, the band it is worked in, and what the movement
 * demands.
 *
 * The goal reaches this through the **Rep range** rather than separately. A range is how a goal
 * shows up at the exercise — strength is trained low and endurance high — so a set prescribed
 * 1 - 6 is already telling this function what it needs to know about why the lifter is there.
 *
 * Every number here is a **judgement call read off the research summary in spec #25**, not a
 * derivation: the table gives bands, and these are the middles of them. They are the sort of thing
 * a lifter should be able to overrule, which is why they can.
 */
fun restFor(exercise: Exercise, setType: SetType, repRange: RepRange): Duration = when {
    // Standing around during a ramp is wasted gym time. Neither a warm-up nor a rung of a
    // calibration ramp is the work — they are the way up to it.
    !setType.isAtTheWorkingWeight -> RAMPING

    // Heavy, low-rep work is limited by how much of the nervous system has come back, and three
    // minutes is where the research stops arguing about it.
    repRange.top <= HEAVY_TOP -> HEAVY

    // The other end: sets of twelve and up are training local endurance, where short rests are
    // the point rather than a compromise.
    repRange.bottom >= ENDURANCE_BOTTOM -> ENDURANCE

    // In between, what governs is how much of the lifter the movement asks for. A squat takes
    // more putting back together than a curl does.
    exercise.mechanic == COMPOUND -> MULTI_JOINT

    else -> SINGLE_JOINT
}

/** A ramp rung or a warm-up: long enough to reset the grip, short enough not to cool down. */
private val RAMPING: Duration = Duration.ofSeconds(60)

/** Advanced strength work, 1 - 6 reps: three minutes or more. */
private val HEAVY: Duration = Duration.ofMinutes(3)

/** Multi-joint work at a real load: two to three minutes. */
private val MULTI_JOINT: Duration = Duration.ofSeconds(150)

/** Single-joint and machine work: 60 to 90 seconds. */
private val SINGLE_JOINT: Duration = Duration.ofSeconds(90)

/** Local endurance, 10 - 15 reps: under a minute. */
private val ENDURANCE: Duration = Duration.ofSeconds(45)

/** The top of a range at or below which a set is heavy enough to want the long rest. */
private const val HEAVY_TOP = 6

/** The bottom of a range at or above which a set is endurance work. */
private const val ENDURANCE_BOTTOM = 12

/** What the bank calls a movement that asks for more than one joint. */
private const val COMPOUND = "compound"

/**
 * The rest a **Superset** group takes, once, after all of it.
 *
 * The point of alternating is not stopping, so no member carries a rest of its own — and the
 * group's own rest is the longest any of its members would have asked for, because the hardest
 * thing in it is what the lifter is recovering from.
 */
fun restAfterSuperset(memberRests: List<Duration>): Duration? = memberRests.maxOrNull()
