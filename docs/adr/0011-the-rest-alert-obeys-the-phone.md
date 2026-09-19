# The rest alert obeys the phone's sound setting

Spec #25 decided the opposite, and said so plainly: *"Alert on an `IMPORTANCE_HIGH` channel with `USAGE_ALARM` audio attributes so it survives silent and vibrate, plus `CATEGORY_ALARM`."* #57 carried it into an acceptance criterion — *"The alert is heard with the device on silent."* Both are reversed here.

## What happened

The timer was verified on a real device, which is the one thing its tests were always going to be unable to prove. It fired correctly: unplugged, screen off, phone in a pocket, on the escalated alarm-clock path. The lifter did not hear it, went looking, and found it had gone off.

The interesting part is what the device said about why. The alarm stream was not muted and was sitting at 11 of 15; only the notification stream was. The channel was exactly what #25 asked for — `usage=USAGE_ALARM`, the device's alarm sound, `IMPORTANCE_HIGH`, `CATEGORY_ALARM`. Everything was configured to be loud, the stream was open, and nothing came out. Posting the same alert fresh, with the ringer on, was audible.

So there were two separate things wrong, and only one of them was a bug.

## The bug: an alert that updates a silenced notification need not alert

The countdown and the alert shared one notification id. The countdown is posted `ongoing` and `silent`, deliberately — the sound belongs to the end of a rest, not the start. The alert then replaced it, and an update to a silenced notification is one Android may deliver without alerting anybody.

They now carry different ids, and the countdown is cancelled before the alert is posted. This is worth stating rather than fixing quietly: it is the exact failure mode this feature was written against, and it is invisible in every test that checks whether a notification exists.

## The decision: a gym is a room full of other people

The original reasoning is sound as far as it goes. Most people's phones are on silent most of the time; a timer nobody hears is a timer that has failed; `USAGE_ALARM` exists precisely so that alarms survive the switch.

What it leaves out is everyone else in the room. A silent switch is a statement about a place, not a preference to be worked around, and a gym is exactly the place people mean by it. An app that shouts through it is not a better timer — it is an app that gets its notifications turned off after the first time it embarrasses somebody, which loses the lifter the feature altogether.

So the alert is an ordinary notification now: `USAGE_NOTIFICATION`, the device's notification sound, `CATEGORY_REMINDER` rather than `CATEGORY_ALARM`, and vibration left to the phone. Loud when the phone is loud, a buzz when it is set to vibrate, nothing when it is silent. Vibration is not forced through silent either; the same argument applies to a phone buzzing on a bench between sets.

`IMPORTANCE_HIGH` stays. Importance decides prominence — whether the alert arrives in front of the lifter or in a drawer — and is a different question from whether a silent phone stays silent.

## What this costs, stated plainly

**A lifter trains with their phone on silent and gets no alert.** That is now the intended behaviour and it is a real loss: the countdown is still there to be looked at, and looking at it is what the wheel and the notification were meant to save them from.

Two things make it acceptable. The alert is not the only channel — the countdown notification renders the remaining time on the lock screen the whole time it runs, drawn by the system. And the lifter chose silent; an app that treats that as an obstacle is making a decision that belongs to them.

The scheduling is untouched by all of this. The alarm is still exact, still `ELAPSED_REALTIME_WAKEUP`, still escalates to `setAlarmClock` for long rests, and still fires while the device is idle. **When** the timer goes off was never the question; only how loudly.

## The channel had to be replaced to do it

A notification channel is immutable to the app that created it. Nothing can move an existing channel off the alarm stream, because that would let an app quietly undo a setting the person had changed. The id is therefore new, and the old one is deleted on first use so that an upgraded phone does not end up with two rest timers in its settings, one of which does nothing.
