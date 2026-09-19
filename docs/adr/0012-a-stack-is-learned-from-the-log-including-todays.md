# A machine's stack is learned from the log, and today's pin counts toward it

Two acceptance criteria written in different tickets contradicted each other on machines, and the app resolved it by accident rather than by decision. This is the decision.

**#51 says** a machine recommendation is only ever a stack position that exercise has been logged at before — because a **Stack number** is a gym-local scale with no physical meaning, so nothing can be inferred about the gaps in one stack from any other machine.

**The ladder says** that beating the top of the rep range on two consecutive sessions earns one **Increment**.

At the heaviest pin a machine has ever been logged at, those cannot both hold. There is no known notch above, so the advance does nothing: the lifter beats the target twice, the app agrees they have earned more, and offers the same number again.

## What was rejected

**Stepping by the smallest observed gap.** Tempting, because the gaps in a stack are usually even, and it infers from that machine's own evidence rather than from another's. Rejected because "usually even" is exactly the assumption #51 exists to refuse — pulley ratios are unlabelled and plates are not always uniform, and an app that invents a notch is an app that asks a lifter to load something that is not there. The whole tier is built on not doing this.

**Leaving it.** The lifter can always select a heavier pin themselves and the app learns it. But it learned it *late*: recommendations are computed from every session except the one being logged into, because today is not history (ADR 0010), so a pin set tonight was invisible until the session was filed. A lifter who had outgrown a setting was offered the old one for the rest of the evening with no reason given.

## What was decided

**A pin position is not a claim about what the lifter has proved.** That distinction is the whole of it. ADR 0010 keeps today's sets out of the recommendation because a good set must not raise the weight within the same session — advancing takes two consecutive sessions rather than one good one, and letting today count would arrive at the advance sideways within minutes.

None of that reasoning applies to *where the pin was*. A stack position is a fact about the machine, true the moment somebody sets it, and it says nothing whatever about whether they earned it. So the stack is read from every session including the open one, while everything the ladder and the anchor are computed from still excludes it.

**And the app says when it is stuck.** A weight that has not moved after two good sessions reads as the engine having quietly given up. The screen breaks its silence here for the same reason it does for the two rungs of the **Graded ladder** and for a re-anchor: the number alone would be read as something it is not. What it says is the one thing that fixes it — move the pin up one and log what you lift.

The two together close the loop in a single set rather than a session: the lifter is told, they put the pin where they want it, the app sees that position immediately and offers it from then on.

## The exception is narrower than the grid it creates

Worth stating precisely, because the first draft of this decision claimed more separation than it has. What today's pin changes is the **set of notches**, and that set is read by more than the ladder: the working load is rounded to it, and a calibration ramp is built on it. So a pin logged tonight can move a number on screen tonight — not because today's *performance* counted, but because the app now knows the machine has a position it did not know about, and rounds to it.

That is the intended behaviour and it is still not what ADR 0010 rules out. What 0010 prevents is a good set raising the weight within the session, arrived at sideways in minutes; nothing here lets a set do that. Rounding a held weight to a notch that exists, rather than to one the app had not heard of, is the app being less wrong.

It costs a second read of the exercise's sessions on stacked exercises — one excluding the open session for the ladder and the anchor, one including it for the notches. Cheap, and the alternative is one read that is wrong for one of its two readers.

## What this costs

The first time a lifter outgrows a machine, the app asks them to do something rather than doing it for them. That is a real cost and it is the point: the alternative is guessing at a notch, and a guess that is wrong is a lifter standing at a machine that does not have the setting the app just asked for.

It also means a mistyped pin joins the stack. A set logged at 63 on a machine with no 63 teaches the app a position that does not exist, and it will be offered again. Correcting the set corrects the stack, because the stack is recomputed from the log rather than accumulated (ADR 0010's reasoning, applied to a second thing) — but until it is corrected, the app believes what it was told. That is the same trust the rest of the log gets.
