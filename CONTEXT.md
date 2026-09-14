# Gym Tracking App

An offline-first Android workout tracker: it holds a training plan, recommends what to lift, and records what was actually lifted.

## Language

### Planning

**Routine**:
The whole repeating training plan a user follows.
_Avoid_: program, plan

**Routine day**:
One ordered position within a routine, such as "Push". Never a calendar day, and never a day of the week.
_Avoid_: workout day, training day

**Cycle**:
One full pass through every routine day in a routine. A cycle has no fixed duration in time.

**Cursor**:
The first routine day in the current cycle that has not yet been completed or skipped. Derived from cycle completion, never stored as a position.

**Cadence**:
The expected number of sessions per week for a routine, seeded by the user at setup and refined from logged session dates. It exists to translate volume targets; it never schedules anything.

**Split**:
The shape of a routine's rotation — full body, upper/lower, push/pull/legs. A property of how a routine was built, not a synonym for the routine itself.

**Direct set**:
A set counted in full toward the muscle the exercise primarily targets.

**Indirect set**:
A set counted at half toward a muscle the exercise works secondarily. The halving is a convention, and the volume targets chosen for this project assume it.

**Volume**:
Sets per muscle, counted directly and indirectly, over a stated period. The quantity the generator schedules against and the per-session cap limits.

### Exercises

**Exercise**:
One movement that can be logged, such as "Barbell Bench Press". The unit that progression is tracked against — history follows the exercise, not the routine it appeared in.

**Exercise bank**:
The full set of exercises available in the app: those shipped preloaded, plus any the user has added.

**Logging type**:
What a set of a given exercise records — one of `weight_reps`, `bodyweight_reps`, `assisted`, `time`, `distance_time`. Determines which inputs the logging screen shows.

**Progression model**:
How an exercise's recommendation advances over time, including `none` for exercises that are recorded but never prescribed.

**Unilateral**:
An exercise trained one side at a time. Logged as a single entry by default, expandable to per-side.

### Recording

**Session**:
One logged trip to the gym, date-stamped on completion. A session usually realises a routine day, but can exist without one.
_Avoid_: workout — ambiguous between the plan and the record, so it is not used as a term in this project

**Skip**:
Marking a routine day as dealt with without a session. Distinct from completion in history, because the user did not train.

**Cardio day**:
A routine position carrying cardio instead of resistance work. It takes its turn in the cycle like any other routine day, but contributes no training volume and is excluded when translating volume targets.

**Plan**:
The list of exercises a session was supposed to contain, copied from its routine day when the session begins. What makes a skipped exercise visible rather than merely absent.

**Revision**:
A change to which days are in the active routine, or to their order. Revising ends the current cycle and starts a new one; it can never affect logged history. Renaming a day, or editing the routine's goal, cadence, equipment or rep preference, is not a revision and leaves the cycle alone — see `docs/adr/0006-routines-are-not-versioned.md`.

**Pin**:
Marking an exercise to be kept through a revision, so regeneration treats it as a fixed constraint and works around it. Concerns the shape of the routine, never the safety of its history.

**Swap**:
Substituting one exercise for another for a single session, because the equipment is unavailable or the user does not want it today. Situational and temporary; making a substitution permanent is a revision instead.

**Dislike**:
A standing exclusion of an exercise from everything the app suggests. Global, reversible, and never affects logged history.

**Set**:
One unit of work against an exercise, identified by its position within that exercise in a session. A set is one set regardless of how many rows record it — a drop set and a per-side set are each still one set.

**Effort**:
One weight-and-reps within a set, and the row that stores it. A set has one, or one per side when a unilateral exercise is expanded. What progression reads is the first effort, never a drop.

**Set type**:
What a set is for — one of `warmup`, `working`, `calibration`, `backoff`, `amrap`. Determines whether the set counts toward training volume.

**Calibration set**:
A set performed to find out what the user can lift, rather than to train. Produced by the cold-start ramp on an exercise with no history. Never counts toward volume.

**Drop**:
A continuation of the same set at reduced weight with no rest. Belongs to the set it continues; never counted as a set of its own.

**Superset**:
Two or more exercises within a session performed alternately with no rest between them. Rest is taken after the group, not between its members.

**Prescription**:
What the app tells the user to do for a given set — weight, reps and rest, where the reps of a working set are a rep range rather than a single count. Recorded alongside what they actually did, so the two can be compared.

### Recommending

**Anchor**:
The app's current estimate of what a user can lift on one exercise, held as an estimated one-rep max and updated from their own logged sets for that exercise alone. Never inferred from a different exercise.

**Rep range**:
The span of reps a working set is prescribed in, such as 6–12. Held per exercise, because it is the exercise's own progression that moves through it: the weight stays while reps climb from the bottom of the range to the top, and only once the top is reached does the weight go up and the reps return to the bottom. A recommendation therefore targets the range, never one rep count.
_Avoid_: target reps as a name for what a set asks for. The bottom and the top of the range do different jobs, and one number hides that. (ADR 0004's load formula still takes a single rep count as its input; that input is the bottom of the range.)

**Plain rep count**:
What a set that is not progressed asks for instead of a rep range — warm-up sets ramping up to the working weight, and calibration sets finding out what it should be. Neither has a top to reach, so neither carries a range.

**Calibrating**:
The mode the app is in when it has no useful anchor for an exercise — a first session, or a return after a long layoff. It still suggests a weight, but expects to be wrong and to be corrected by what the user actually lifts.

**Prescribing**:
The mode the app is in when real history supports the recommendation.

**Personal record**:
The best set a user has performed at a given rep count for an exercise — best at 5, best at 8, and so on. Rep-specific because a single "best set" is ambiguous once weight and reps both vary.

**Increment**:
The smallest weight step actually achievable on a given exercise, set by the equipment rather than by arithmetic — plate pairs on a barbell, rack steps on dumbbells, and for machines, the stack positions the user has been observed to use.
