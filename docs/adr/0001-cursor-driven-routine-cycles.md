# Routine cycles are cursor-driven, and the cursor holds position when you train out of order

A routine is an ordered list of training days with no calendar anchoring, so the app needs some notion of "what's next". We compute a **cursor**: the first routine day in the current cycle not yet completed or skipped. Training a day out of order does **not** advance past the day the cursor was holding — that day comes back next time.

## Considered options

**Date-anchored cycle** (`day = (today − start) mod N`). Rejected: it is exactly the calendar coupling the product deliberately avoids, and it degrades badly across layoffs — three weeks off and it insists fifteen workouts were missed.

**Stored integer cursor that follows what you actually did.** Rejected, though it was the recommendation. It is simpler and cheaper, but a day you keep avoiding silently disappears from the rotation: skip leg day three times and the app never mentions it again.

**Derived cursor over per-cycle completion** (chosen). A day skipped out of order keeps resurfacing until it is trained or explicitly skipped.

## Consequences

- **The cursor is derived state, not a stored position.** The app must track which routine days are complete within the current cycle, rather than incrementing an integer. This costs more storage and more logic than the rejected alternative; it was accepted deliberately.
- **An explicit "skip this day" action is structurally required.** Without it, the only way past a day is to train it, and a user who will never train day 2 as written has no escape short of editing their routine. A skip is recorded distinctly from a completion so history never claims training that did not happen.
- **The cursor advances on match, not per session.** Two sessions in one calendar day advance it twice only if each performed the day the cursor pointed at.
- **Backdated sessions never move the cursor.** Otherwise importing years of history would fling it somewhere arbitrary. The cursor is about what is next; dates are about what happened.
- Repeated skips of the same day become a useful signal for exercise swap and dislike recommendations.
