# Gym Tracking App

An offline-first Android workout tracker: it holds a training plan, recommends what to lift, and records what was actually lifted.

## Language

### Planning

**Routine**:
The whole repeating training plan a user follows.
_Avoid_: program, plan

**Routine day**:
One ordered position within a routine, such as "Push". Never a calendar day, and never a day of the week. Holds an ordered list of exercises — what a session started from it copies as its **Plan**. Changing that list is not a **Revision**: the rotation still holds the same days in the same order.
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
One movement that can be logged, such as "Barbell Bench Press". The unit that progression is tracked against — history follows the exercise, not the routine it appeared in. Carries the name the bank shipped and, where the lifter has renamed it, the name they call it; the shipped name stays, because the search shorthand resolves against it. A standing note can be attached to it, and is shown wherever the exercise is used.

**Exercise bank**:
The full set of exercises available in the app: those shipped preloaded, plus any the user has added.

**User-authored exercise**:
One the lifter added, for a machine their gym has and the app has never heard of. Not to be confused with the bank's own `authored` rows, which are hand-written for this project and ship inside the APK (`data/README.md`); the distinction an update turns on is shipped against the lifter's, not upstream against authored. First-class — it appears in search, in filtering and in a routine exactly as a shipped one does — and it takes a UUID in an id space of its own, so a **Bank update** matching on the shipped string ids can never find it.

**Bank update**:
An app release improving the shipped exercises. It matches on the shipped id, never touches a user-authored exercise, and never deletes one with logged history — it **tombstones** it instead. What the lifter decided about an exercise is held apart from it (`docs/adr/0009-user-choices-live-apart-from-the-shipped-bank.md`) and survives.

**Tombstone**:
What a bank update does to a shipped exercise it has dropped that somebody has already trained. It stops being offered and stays exactly where it is, because the sets logged against it are a record of training that happened.
_Avoid_: delete, archive — one is what tombstoning exists to prevent and the other suggests the lifter did it.

**Logging type**:
What a set of a given exercise records — one of `weight_reps`, `bodyweight_reps`, `assisted`, `time`, `distance_time`. Held as the set of measurements it takes rather than a flag apiece, so the screen asks it what to show rather than deciding: a plank is offered seconds and no weight, because a weight field on a hold asks for a number that means nothing and one that reached storage would be read later as something the lifter lifted.

**Added weight**:
A belt or a plate hung off a lifter whose own body is the load. Optional and usually absent — a pull-up and a weighted pull-up are one exercise, because putting a belt on must not split a history in two.

**Assistance**:
How much of the work a machine did for the lifter. The one measurement in this project where less is better, so it is stored as **Added weight** with the sign reversed: weight taken off the lifter rather than hung on them. That makes "more is progress" true of every exercise by arithmetic, rather than a rule each future reader has to remember to invert (ADR 0004).

**Progression model**:
How an exercise's recommendation advances over time, including `none` for exercises that are recorded but never prescribed. Derived by the bank from the **Logging type** — anything with a load advances by the double progression ADR 0004 settles, and a hold or a run is recorded and never prescribed — and overridable per exercise, because a lift somebody does not want coached is their business and the bank cannot know. The lifter's choice lives apart from the bank row, so a **Bank update** improving the derivation cannot overrule it.

**Unilateral**:
An exercise trained one side at a time. Logged as a single entry by default, expandable to per-side.

**Display unit**:
The unit an exercise is read in — kilograms or pounds — remembered per exercise, because it is a property of the machine rather than of the person: the gym's lb lat pulldown is labelled in pounds whoever walks up to it. A departure from the lifter's **Preferred unit**, and held only where it actually departs — an exercise set to the unit its owner already reads in has chosen nothing, and storing the agreement would strand it there the day they switched. A single set can override it in turn without disturbing the exercise's own setting. Display only at every level: what is stored is canonical kilograms whatever any of them says, so the gym's kg lat pulldown and its lb one share one continuous history.
_Avoid_: unit on its own as a name for it, which reads as something the weight is stored in.

**Preferred unit**:
The unit this lifter reads weights in — theirs rather than any machine's, and the one thing on this list that is about the person. Seeded from the device's locale, settled the moment they tap kg or lb, and from then on theirs: a phone that changes locale does not re-read their history in a unit they did not ask for. Everything reads in it until something more specific says otherwise — an exercise with a **Display unit** of its own, or a single set overriding that.

It answers for distance too. A lifter reading pounds runs in miles, so nothing stores a distance unit anywhere: kilometres travel with kilograms and miles with pounds, and the two cannot fall out of step because there is only one of them.

**Stack number**:
What a machine's selector plate is labelled with. Treated as its own gym-local scale and converted against nothing: pulley ratios differ between manufacturers and are not labelled, so 60 on one machine is not 60 on another and no arithmetic can make it so.

### Recording

**Session**:
One logged trip to the gym, date-stamped on completion. A session usually realises a routine day, but can exist without one.
_Avoid_: workout — ambiguous between the plan and the record, so it is not used as a term in this project

**Skip**:
Marking a routine day as dealt with without a session. Distinct from completion in history, because the user did not train.

**Backdating**:
Filing a session on a day other than the one it was trained on — training on Saturday and remembering on Monday. The session and its sets are real history, and the routine day it realised is recorded on it; what comes *next* is not its business, so a backdated session never advances a cycle. Asked once, as the session is finished: correcting a date afterwards does not un-train the day.

**Cardio day**:
A routine position carrying cardio instead of resistance work. It takes its turn in the cycle like any other routine day, but contributes no training volume and is excluded when translating volume targets.

**Plan**:
The list of exercises a session was supposed to contain, copied from its routine day. Taken when the session begins, or when it names its routine day at the end if it did not start from one — either way a snapshot, with nothing pointing back at the live routine. What makes an unperformed exercise visible rather than merely absent.

**Unperformed**:
An exercise a session's plan asked for and the session did not contain. Deliberately not a **Skip**, which is a routine *day* dealt with on purpose: this is what quietly did not happen, and counting it across recent sessions is the signal swap and dislike read.
_Avoid_: skipped exercise, which borrows a word that already means something else here.

**Avoided**:
An exercise the recent sessions that asked for it kept not containing — counted from **Unperformed** over a window of the sessions that planned it, so one trained every week and one trained every third are held to the same standard.

An observation and never an action. The app notices and the lifter decides: people skip squats because squats are hard rather than because they dislike them, and inferring a **Dislike** from avoidance would quietly strip the most valuable work out of a programme. Every threshold in it is an invented, tuneable judgement call.

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
What was done in one go within a set, and the row that stores it. A set has one, or one per side when a unilateral exercise is expanded. What progression reads is the first effort, never a drop.

**Performance**:
What an effort amounted to — whatever its exercise's **Logging type** records. A weight and a rep count for a bench press; a length of time for a hold; how far and how long for a treadmill. Held as one of a closed set of shapes rather than as a weight, a rep count, a duration and a distance that may each be missing, so that an effort recording nothing cannot exist and no reader has to decide what a weight of zero meant. A **Drop** is a continuation at reduced weight, so an effort with no weight has none.

A distance is stored in canonical metres for the reason a weight is stored in kilograms: a treadmill labelled in miles and a track measured in kilometres are the same running, and storing whichever the machine showed would put a step change in the history the week a lifter changed gyms.

**Set type**:
What a set is for — one of `warmup`, `working`, `calibration`, `backoff`, `amrap`. Determines whether the set counts toward training volume.

**Calibration set**:
A set performed to find out what the user can lift, rather than to train. Produced by the cold-start ramp on an exercise with no history. Never counts toward volume.

**Drop**:
A continuation of the same set at reduced weight with no rest. Belongs to the set it continues; never counted as a set of its own.

**Superset**:
Two or more exercises within a session performed alternately with no rest between them. Rest is taken after the group, not between its members, so the group carries its own rest value and a member has nowhere to put one. Groups **across** exercises, where a **Drop** groups **within** one set — two structures rather than one, because collapsing them makes counting ambiguous again. Grouping says nothing about work: the sets are the same sets whether or not they were alternated.

**Auto-jump**:
Whether a superset moves to its next member on its own once a set is logged. A property of the group, because the point of a superset is not stopping — and off is a real answer for two machines at opposite ends of a gym.

**Grouping** / **Ungrouping**:
Forming a superset from neighbouring exercises, and breaking one up. Both are statements about rest and neither touches a logged set: ungrouping releases the members and leaves the training exactly as it was.

**Prescription**:
What the app tells the user to do for a given set — weight, reps and rest, where the reps of a working set are a rep range rather than a single count. Recorded alongside what they actually did, which is the only way overriding a recommendation and falling short of one can ever be told apart: they are identical in a log that holds only the actual, and they want opposite responses. A set answers its prescription as **followed**, **overridden** — a different weight, which is a decision — or **fell short**, the weight asked for and fewer reps than it asked for.

**Rest**:
How long to wait after a set, and the third thing a **Prescription** carries. Derived per *set* rather than per exercise, because what governs it is what the set was: a ramp rung rests briefly, heavy low-rep work rests longest, endurance work least, and in between it goes by how much of the lifter the movement asks for.

Three answers in order of who gets the last word. A **Superset** member has none of its own — the point of alternating is not stopping, so the rest belongs to the group and is taken once the group has come round. Otherwise the lifter's own standing answer for the exercise wins if they gave one, and the derived answer stands if they did not. A rest set for one set only says nothing about the next.

Every number in the derivation is an invented, tuneable judgement call.

**Bodyweight entry**:
What the lifter weighed, and when they weighed it. Profile data rather than training: it sits on the same calendar as the sessions and belongs to none of them, contributes nothing to **Volume** and is never a **Set** — it carries no session, no exercise and no set index, so there is nothing for a volume or set query to reach rather than something to filter out. Stored in canonical kilograms like every other weight, and read in the lifter's **Preferred unit**.

A day holds as many readings as were taken. Bodyweight swings a kilogram or two between morning and evening, so a lifter who stepped on the scale twice measured that swing rather than corrected themselves; each reading carries the moment it was recorded, which is what tells two on one day apart and orders them. The day it is filed on is its own value, correctable like a session's date, so a weigh-in remembered on Monday can still be Saturday's. The latest bodyweight is the latest reading on the most recent day, never the row written last.
_Avoid_: weight on its own as a name for it, which already means what is on the bar.


### Recommending

**Training history**:
One exercise's own logged sets, gathered into the sessions they were trained in — what every recommendation is computed from, and nothing else. A session contributes one point however many sets it took, and a set is one set however many rows recorded it (ADR 0003). Never contains another exercise's work: that is the constraint that keeps a bench press from deciding a dumbbell press.

**Anchor**:
The app's current estimate of what a user can lift on one exercise, held as an estimated one-rep max and updated from their own logged sets for that exercise alone. Never inferred from a different exercise.

Smoothed rather than moved to each new best, so one lucky set does not become next week's prescription, and **signed like a load**: negative on an assisted machine, where needing less help is the better estimate. Recomputed from the log rather than accumulated, so correcting a mis-logged set corrects it (`docs/adr/0010-the-anchor-is-recomputed-from-the-log.md`).

**Rep range**:
The span of reps a working set is prescribed in, such as 6–12. Held per exercise, because it is the exercise's own progression that moves through it: the weight stays while reps climb from the bottom of the range to the top, and only once the top is reached does the weight go up and the reps return to the bottom. A recommendation therefore targets the range, never one rep count.
_Avoid_: target reps as a name for what a set asks for. The bottom and the top of the range do different jobs, and one number hides that. (ADR 0004's load formula still takes a single rep count as its input; that input is the bottom of the range.)

**Plain rep count**:
What a set that is not progressed asks for instead of a rep range — warm-up sets ramping up to the working weight, and calibration sets finding out what it should be. Neither has a top to reach, so neither carries a range.

**Calibrating**:
The mode the app is in when it has no useful anchor for an exercise — a first session, or a return after a long layoff. It still suggests a weight, but expects to be wrong and to be corrected by what the user actually lifts. It says so on screen, where **Prescribing** says nothing at all about itself: knowing when the app is guessing is what makes the rest of the time trustworthy. The only other things the screen breaks that silence for are the two rungs of the **Graded ladder** that a number alone would be read wrongly without.

**Calibration ramp**:
The short climb a first session makes to find a working weight, rather than predicting one. Starts at a **seed** chosen from what the exercise is loaded with — an empty bar, a light dumbbell, a low pin — and steps up until the lifter stops, biased low throughout and capped so session one does not become forty minutes. Every rung is a **Calibration set**, and each is harder than the last: more weight on a bar, less help on an assisted machine.

The seed is the only part of cold start a model does: measuring what somebody can lift is several times more precise than estimating it and costs one session, so the estimate's job is to shorten the ramp rather than to produce the answer (`docs/research/cold-start-weights.md`).

**Warm-up ramp**:
The climb up to a working weight, as distinct from a **Calibration ramp**, which climbs to find out what that weight is. Rungs are **Warm-up sets** and count toward no volume.

**Belongs to the muscle rather than to the exercise.** The third chest movement of an evening has had its warming-up done for it by the first two, so the ramp shortens by what came earlier in the session, and a light or single-joint movement gets fewer rungs than a heavy compound. Once the working sets have started the ramp is over: offering one to a lifter three sets in is the app asking them to warm up for something they are in the middle of.

**Layoff**:
Time away from one exercise, and what it does to the **Anchor**. The estimate decays because it has stopped being informative, not because the lifter has become fragile — the research is explicit that pre-break strength returns within weeks — so the number that matters is the one past which the app stops prescribing from it and goes back to **Calibrating**. Tracked per exercise, with a grace period, a modest rate after it, and every number injected rather than hard-coded, because no usable decay curve exists to copy.

**Prescribing**:
The mode the app is in when real history supports the recommendation.

**Graded ladder**:
What the app does about sessions that keep going the same way, in both directions and never in one step. Two sessions past the top of the **Rep range** earn one **Increment**; a single short session costs nothing, two running take about a tenth off the weight, three hold that weight and cut the work instead, and a lift that keeps coming up short stops being prescribed for at all rather than guessed at forever.

Judged per session rather than per set — one hard set at the end of a session is fatigue — and **against the range rather than against the reps asked for**: the count asked for is last session's, so a lifter drifting down inside their band would otherwise read as falling short twice running. Coming up short means failing to reach the bottom of the range at the weight asked for.

Only sessions that answered what was asked are counted. A weight the lifter chose instead is a decision rather than a failure, and backing off a weight nobody failed is its own kind of wrong; a session the app said nothing about is invisible to the ladder rather than an answer to it, which is what keeps a lift it has stopped prescribing for stopped.

Cutting the volume is advice rather than an action, because nothing in the app prescribes a number of sets yet. Every rate in the ladder is an invented, tuneable judgement call; ADR 0004 supports the shape, not the numbers.

**Personal record**:
The best set a user has performed at a given rep count for an exercise — best at 5, best at 8, and so on. Rep-specific because a single "best set" is ambiguous once weight and reps both vary.

**Working load**:
The load an exercise is currently being worked at — the **Anchor** read back down to the bottom of the **Rep range** and rounded down to an **Increment**. A load rather than a weight, because on an assisted machine it is how much help the machine is giving.

What double progression moves: the weight holds while the reps climb through the range, and steps by one **Increment** once the top has been beaten on two consecutive sessions. Held rather than followed — once the app has asked for a weight, that weight is what holds, and not whatever the **Anchor** currently says. The app's own working rather than anything the lifter chose, which is why it lives apart from both the shipped bank row and the lifter's preferences.

**Plate inventory**:
The plates the lifter owns and the bar they go on: one global, editable set, counted in **pairs** because a bar loaded at one end is not a lift. The single thing both the engine and the **Plate line** read, which is what stops the weight and the instructions for building it ever disagreeing.

Empty means nobody has described their gym, not that there is nothing to lift: an ordinary rack for the unit they read in stands in until they say otherwise. Stored in canonical kilograms like every other weight, and seeded as the objects they are rather than as conversions — a 20 kg bar and a 45 lb bar are different bars.

**Plate line**:
What goes on one end of the bar to make the weight above it — `20 kg bar · 20, 10, 1.25 per side`. Sits under the prescription on barbell exercises, always visible and never interactive: it is not a choice, it is the rounding the engine already did, shown. Heaviest plate first and as few as will do it, because every plate is a trip to the rack.

**Increment**:
The smallest weight step actually achievable on a given exercise, set by the equipment rather than by arithmetic — plate pairs on a barbell, rack steps on dumbbells, and for machines, the stack positions the user has been observed to use.

Distinct from the **step** a weight stepper takes, which is a round number in the display unit — 2.5 kg or 5 lb — and belongs to how a weight is read rather than to what the equipment can do. Converting one unit's step into the other is what the separate numbers exist to avoid.

Where none of the three tiers knows better, the fallback is that same step in the unit the lifter reads: rounding in kilograms and converting afterwards is what produces 137.79 lb, a number that is exactly correct and exists nowhere. The lifter can also say outright what one exercise moves by, and their answer beats anything the app can work out — a rack going up in fours is a fact about their gym, and nothing can infer it.
