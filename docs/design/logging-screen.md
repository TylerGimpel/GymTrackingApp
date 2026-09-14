# Live logging screen

The settled design, from [Prototype: live logging screen](https://github.com/TylerGimpel/GymTrackingApp/issues/17). Four variants were built and driven on a phone; this is what survived.

The prototype itself is the primary source and lives on the throwaway branch `prototype/logging-screen` at `docs/prototypes/logging-screen.html`. It is not production code.

## The shape

A **vertical wheel of sets**. The focused set renders large — the prescription, at a size readable at arm's length on a rack. Its neighbours recede above and below with perspective tilt, scale and fade, showing the rest of the exercise's sets as context. Scrolling changes focus; logging a set advances it automatically.

Three layouts were rejected to get here: a single big card with no context, a dense list with no focus, and horizontal value pickers. The wheel is the only one that gives both the zoomed prescription and the surrounding sets at once.

**Controls stay fixed below the wheel**, in the thumb zone: weight and rep steppers, then the log button. They act on whatever is focused. They do not move with the card — a control that shifts position between sets is a control you have to look at.

**Auto-advance on log is what makes the wheel work.** Without it, scrolling to change sets would be more effort than tapping, and the whole shape would be wrong. The common path never involves scrolling at all.

## Entering a result

- Accepting the prescription is one tap on the log button.
- **Steppers** adjust by the display unit's own increment — 2.5 kg or 5 lb.
- **Tapping a value types it.** Necessary because stepping from 20 to 60 is absurd, and overriding is the *expected* path while calibrating, not the exception.
- **Tapping the unit toggles kg/lb for that exercise**, and the choice is remembered. Storage stays canonical kg; this is display conversion only, so a machine labelled in lb and one labelled in kg can sit in the same routine without putting a false step in the progress graph.

The unit is tappable but carries no underline or other decoration. The affordance is taught once during onboarding rather than advertised on every set.

## What the screen says, and when

**In the normal state the screen says nothing about itself.** No mode badge, no hint line, no instructional text. The wheel already prints the previous performance under every set, so a line repeating it is noise.

Two exceptions, both carrying information available nowhere else:

| Condition | Shows |
|---|---|
| Exercise is calibrating | A `calibrating` badge, and *"Calibrating — stop when it gets hard. Being wrong here is expected."* |
| Prescribed set is 15+ reps | *"Take this close to failure — high reps only work hard."* |

The second is not optional polish. Hypertrophy is load-independent above ~30% 1RM **only if effort is high**, so a high-rep prescription that does not convey the effort requirement is not the intervention the evidence supports.

An earlier draft labelled the normal state `prescribing`, then `from your history`. Both failed: the first was the data model's vocabulary leaking into the UI, the second was redundant. The lesson generalises — **the screen does not speak the model's language**. `prescribing` and `calibrating` are defined in `CONTEXT.md` for the code; only `calibrating` ever reaches the user, and only because a line underneath explains it.

## The routine drawer

The whole day's routine — every exercise with a completion bar and `done/total` — lives in a **left drawer**, not on the main screen. Tapping any row jumps straight to that exercise.

It opens by **tapping the handle** or by a **horizontal swipe anywhere**. Swiping left closes it.

**It must not depend on a left-edge swipe.** On Android 10+ gesture navigation, a swipe from the left edge *is* the system Back gesture. Apps can only carve out narrow exclusion rects via `setSystemGestureExclusionRects`, Google discourages it, and the gesture is taken before the app sees it. This was found the hard way: the first prototype used an edge swipe and it simply never fired.

A horizontal swipe anywhere is unambiguous here because the wheel scrolls vertically, and it never enters the OS gesture zone.

## Everything else on screen

- **Dual set counter** in the header: *"Set 4 of 7 · working 2 of 4"*. Overall position answers "how much is left"; working-set position answers "where am I in the real work". Both computed at render time from order and set type.
- **Elapsed and estimated remaining**, also in the header. The `~` prefix matters: it reads as an estimate rather than a deadline, which is what made it acceptable rather than stressful. The estimate spans same-day cardio, because the question being answered is total time in the gym.
- **Rest timer** slides up from the bottom on logging, labelled with the set type it follows, with `+30s` and `Skip`.
- **Superset auto-jump**: logging a set in a superset moves to the paired exercise rather than starting a rest.
- **Swap sheet** from the header, with candidates ranked so that exercises the user already has history for come first — those need no calibration.
- **Cardio** gets its own layout: duration, distance, machine setting, optional heart rate and RPE.

## Implementation notes for Compose

- The wheel renders once per exercise and updates focus **imperatively** on scroll. Re-rendering the list on every scroll frame fights the scroll position and feels broken. The same trap exists in Compose — hoist the focus index out of the scroll state rather than recomposing the list.
- Touch targets were checked one-handed at phone size and are adequate; the steppers are the smallest and are the override path, so they should not shrink.
