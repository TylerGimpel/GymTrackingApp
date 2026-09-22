# Live logging screen

The settled design, from [Prototype: live logging screen](https://github.com/TylerGimpel/GymTrackingApp/issues/17). Four variants were built and driven on a phone; this is what survived.

The prototype itself is the primary source and lives on the throwaway branch `prototype/logging-screen` at `docs/prototypes/logging-screen.html`. It is not production code.

## The shape

A **vertical wheel of sets**. The focused set renders large — the prescription, at a size readable at arm's length on a rack. Its neighbours recede above and below with perspective tilt, scale and fade, showing the rest of the exercise's sets as context. Scrolling changes focus; logging a set advances it automatically.

Three layouts were rejected to get here: a single big card with no context, a dense list with no focus, and horizontal value pickers. The wheel is the only one that gives both the zoomed prescription and the surrounding sets at once.

**Controls stay fixed below the wheel**, in the thumb zone: weight and rep steppers, then the log button. They act on whatever is focused. They do not move with the card — a control that shifts position between sets is a control you have to look at.

**Auto-advance on log is what makes the wheel work.** Without it, scrolling to change sets would be more effort than tapping, and the whole shape would be wrong. The common path never involves scrolling at all.

## The rep range sits above the number, not in place of it

A working set is prescribed inside a **rep range** (`CONTEXT.md`), because that is what double progression asks for: hold the weight, climb from the bottom of the range to the top, then add weight and drop back to the bottom.

**The big value stays one number.** A set is logged as a single rep count, so the headline has to read as what the next tap will record — a large `82.5 kg × 6 - 8` was tried and looked bad on a phone, and it makes the one thing you glance at from the rack the one thing you cannot act on.

So the range is a **small dim line directly above the value**:

```
        WORKING
      6 - 8 reps
    82.5 kg × 6
      was 80 × 8
```

It reads top to bottom as the band, the number to go and do, and what you did last time. Written with a **spaced hyphen** — `6 - 8`, never an en or em dash — which is what scans as a span at a glance rather than as punctuation.

**The number recommended is the conservative one: last session's reps if the weight has not changed, otherwise the bottom of the range.** That is double progression stated as a default — same weight means beat last time, new weight means start at the bottom. Recommending the top instead would make the one-tap path claim an advance that may not have happened, and the spec is explicit that confidently wrong in the direction of too heavy is the one outcome that can hurt someone. Under-recording costs a session, and the two-consecutive-sessions hysteresis absorbs it.

Warm-up and calibration sets get **no range line at all**. Neither is progressed, so neither has a top to reach, and a range on a warm-up would invite the lifter to chase it.

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
| The **top** of the prescribed rep range is 15+ | *"Take this close to failure — high reps only work hard."* |

The second is not optional polish. Hypertrophy is load-independent above ~30% 1RM **only if effort is high**, so a high-rep prescription that does not convey the effort requirement is not the intervention the evidence supports.

It keys off the **top** of the range rather than the bottom: a 12–15 set is asking the lifter to reach 15, and 15 is the rep count the effort requirement attaches to. Keying off the bottom would silence the line on exactly the ranges where a lighter load is being justified by high reps.

An earlier draft labelled the normal state `prescribing`, then `from your history`. Both failed: the first was the data model's vocabulary leaking into the UI, the second was redundant. The lesson generalises — **the screen does not speak the model's language**. `prescribing` and `calibrating` are defined in `CONTEXT.md` for the code; only `calibrating` ever reaches the user, and only because a line underneath explains it.

## The routine drawer

The whole day's routine — every exercise with a completion bar and `done/total` — lives in a **left drawer**, not on the main screen. Tapping any row jumps straight to that exercise.

It opens by **tapping the handle** or by a **horizontal swipe anywhere**. Swiping left closes it.

**It must not depend on a left-edge swipe.** On Android 10+ gesture navigation, a swipe from the left edge *is* the system Back gesture. Apps can only carve out narrow exclusion rects via `setSystemGestureExclusionRects`, Google discourages it, and the gesture is taken before the app sees it. This was found the hard way: the first prototype used an edge swipe and it simply never fired.

A horizontal swipe anywhere is unambiguous here because the wheel scrolls vertically, and it never enters the OS gesture zone.

## Everything else on screen

- **Dual set counter** in the header: *"Set 4 of 7 · working 2 of 4"*. Overall position answers "how much is left"; working-set position answers "where am I in the real work". Both computed at render time from order and set type.

  **What shipped is the left-hand half of each: _"set 4 · working 2"_.** The denominators are not a rendering detail — they are a claim about how many sets this exercise gets, and nothing in the app makes that claim yet. The prototype could print `of 7` because its sets were fixture data; the real session is open-ended, and a lifter logs as many as they decide to. `of 7` arrives with the generator that prescribes a set count (#26), and until then a number invented for the denominator would be the screen stating a plan nobody made.
- **Elapsed and estimated remaining**, also in the header. The `~` prefix matters: it reads as an estimate rather than a deadline, which is what made it acceptable rather than stressful. The estimate spans same-day cardio, because the question being answered is total time in the gym.
- **Rest timer** slides up from the bottom on logging, labelled with the set type it follows, with `+1:00` and `Skip`. The prototype drew `+30s`; a minute is what shipped. Prescribed rests run from 45 seconds to three minutes, and a lifter reaching for more time between heavy sets is not reaching for half a minute of it. Anything finer is what editing the length is for, which is the control for arguing with the number rather than nudging it.
- **Superset auto-jump**: logging a set in a superset moves to the paired exercise rather than starting a rest.
- **Swap sheet** from the header, with candidates ranked so that exercises the user already has history for come first — those need no calibration.
- **Cardio** gets its own layout: duration, distance, machine setting, optional heart rate and RPE.

## Implementation notes for Compose

- The wheel renders once per exercise and updates focus **imperatively** on scroll. Re-rendering the list on every scroll frame fights the scroll position and feels broken. The same trap exists in Compose — hoist the focus index out of the scroll state rather than recomposing the list.
- Touch targets were checked one-handed at phone size and are adequate; the steppers are the smallest and are the override path, so they should not shrink.
