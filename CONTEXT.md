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

### Recording

**Session**:
One logged trip to the gym, date-stamped on completion. A session usually realises a routine day, but can exist without one.
_Avoid_: workout — ambiguous between the plan and the record, so it is not used as a term in this project

**Skip**:
Marking a routine day as dealt with without a session. Distinct from completion in history, because the user did not train.
