# Routines are mutated in place, not versioned

A routine has no version history. Revising it changes it, and only the immediately-previous state is retained — for undo, until the next session is logged.

## Why this is safe, and why versioning would be redundant

The two things version history would normally protect are already covered elsewhere.

**Logged history cannot be damaged by revision**, because a session references the *exercise* with a real constraint and carries only a denormalized snapshot of routine name and routine day name, with no constraint back to the live routine. Deleting or regenerating a routine cannot reach a single logged set. This is structural, not a matter of careful coding.

**"What was I actually following in March" is answerable from the log itself**, because a session snapshots its planned exercise list when it begins. That snapshot was added for a different reason — making skipped exercises visible rather than merely absent — but it happens to preserve the full contents of every routine day the user ever trained.

So versioning would add an active/inactive version chain, migration of cycle state across versions, and a second source of truth for routine contents, in exchange for capabilities that already exist. The cost is real and the benefit is not.

## Consequences

- **Undo is one step, not a chain.** The real use case is "I regenerated and I hate it", which surfaces within seconds. A longer history would be carried indefinitely to serve a case that does not occur.
- **A past routine is recoverable but not switchable.** `Rebuild routine from a past session` reads the plan snapshots and creates a routine from them, which is what makes "one active routine" genuinely non-lossy rather than approximately so. It covers the seasonal case — a bulk routine and a cut routine — without concurrent routines, each of which would need its own cursor, cycle state and cadence.
- **Revising mid-cycle ends the current cycle and starts a new one at position 1.** Attempting to map completions from old days onto new ones sounds helpful, but "matching" becomes ambiguous the moment a day is renamed or reordered, and it fails in ways that cannot be explained to a user in a sentence.
- **A routine is always editable, so revision, regeneration and manual building are one mechanism.** Regeneration replaces every unpinned day; manual building starts from empty. The generator's validation pass therefore covers hand-edits too, rather than leaving a back door through which unsound routines arrive.
