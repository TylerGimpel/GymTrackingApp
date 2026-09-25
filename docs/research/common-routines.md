# Common Routines and the Lifts They Use: Research Inputs for the Generator

> Research note for [issue #131](https://github.com/TylerGimpel/GymTrackingApp/issues/131), under the
> [UI overhaul map (#130)](https://github.com/TylerGimpel/GymTrackingApp/issues/130).
> **Question:** what do well-known beginner and intermediate routines actually look like, which lifts
> do they share and why, which lifts do they avoid, and where do the bank's names differ from the
> plain names these programs use?
>
> **Written:** 2026-09-25. Companion to `splits-and-volume.md`, which covers the *volume* inputs.
> This note covers *which exercises*. It does not design the generator change (that is #139's job).

---

## 0. Summary

1. **The programs agree closely.** Ten widely followed programs (section 2) are built from the same
   short list of about 25 movements. Squat, bench press, deadlift, overhead press and a vertical pull
   appear in 8 or 9 of the 10. Each day has **3 to 7 exercises**: barbell-only novice programs use 3,
   hypertrophy programs use 5 to 7.
2. **Every program works the same way.** Each day opens with one or two big compound lifts, then
   one or two more compounds, then isolation work for arms, delts, calves and hamstrings. Nothing
   in any of the ten comes from the Olympic, strongman, plyometric, stretching or
   accommodating-resistance (chains/bands) families.
3. **Why the generator picks strange lifts.** The generator
   (`app/src/main/java/com/tylergimpel/gymtracker/domain/Generator.kt`, `availableFor`) orders
   candidates **compound first, then by upstream id, alphabetically**. It does not filter
   `category` or `level`, and it has no signal for how common a lift is. So "first alphabetically"
   is the de-facto ranking. Replaying that rule against the shipped bank (section 1) gives
   *Alternating Floor Press* and *Around The Worlds* for chest, *Alternate Leg Diagonal Bound* (a
   plyometric) for quads, *Clean* (an Olympic lift) for hamstrings with a normal gym's equipment,
   and *Knee Circles* (a stretch) for calves. That is exactly the lifter's complaint.
4. **The bank's `level` field can't fix this on its own.** Staples of every beginner program are
   tagged `intermediate` or worse: Barbell Deadlift, Romanian Deadlift, Dumbbell Shoulder Press,
   Face Pull, Barbell Hip Thrust (intermediate); Front Barbell Squat and Hanging Leg Raise
   (expert). Meanwhile *Anti-Gravity Press* and *Smith Machine Upright Row* are tagged `beginner`.
   A "beginner-only" filter would remove the deadlift and keep the oddities. **The generator needs a
   hand-authored list of common lifts** (section 3). The data does not hold one.
5. **Names.** Search aliases (`data/aliases.json`) already map most plain names onto bank rows.
   But the *display* name is still the upstream one: "Barbell Bench Press - Medium Grip",
   "Butterfly", "Leverage Chest Press", "Pullups". Section 5 maps bank names to plain names.

---

## 1. What the generator does today (verified against the code and the bank)

`fillDays` builds its pool with `availableFor(equipment)`. That pool is `worthOffering` (not
tombstoned, not disliked, equipment in the gym's set or `null`), sorted
`compareByDescending { isCompound }.thenBy { id }`. For each muscle a day names, `buildDay` takes the
first *N* rows in that order whose `primaryMuscles` contain the muscle (rotated by the variety seed,
which is 0 on first generation). Nothing filters on `category` (`stretching`, `plyometrics`,
`olympic weightlifting`, `strongman`, `powerlifting`), on `level`, or on how common a lift is.

Replaying that ordering over the merged bank (upstream + authored, supersessions dropped, patches
applied). These are the first three candidates per muscle, seed 0:

| Muscle | Everything (no equipment filter) | A normal gym (barbell, dumbbell, cable, machine, body only, EZ bar) |
|---|---|---|
| chest | Alternating Floor Press; **Around The Worlds**; Assisted Dip Machine | **Around The Worlds**; Assisted Dip Machine; Barbell Bench Press - Medium Grip |
| lats | Assisted Pull-Up Machine; Band Assisted Pull-Up; Bent-Arm Barbell Pullover | Assisted Pull-Up Machine; Bent-Arm Barbell Pullover; Chin-Up |
| middle back | **Alternating Renegade Row** (expert); Bent Over Barbell Row; Bent Over One-Arm Long Bar Row | Bent Over Barbell Row; Bent Over One-Arm Long Bar Row; Bent Over Two-Arm Long Bar Row |
| shoulders | Alternating Cable Shoulder Press; Alternating Kettlebell Press; **Anti-Gravity Press** | Alternating Cable Shoulder Press; **Anti-Gravity Press**; Arnold Dumbbell Press |
| biceps | **Drag Curl**; High Cable Curls; Alternate Hammer Curl | **Drag Curl**; High Cable Curls; Alternate Hammer Curl |
| triceps | Bench Dips; Bench Press - Powerlifting; **Bench Press with Chains** (expert) | same |
| quadriceps | **Alternate Leg Diagonal Bound** (plyometric); **Backward Drag** (strongman); Barbell Full Squat | **Alternate Leg Diagonal Bound**; Barbell Full Squat; Barbell Hack Squat |
| hamstrings | **Alternating Hang Clean**; Band Good Morning; Band Good Morning (Pull Through) | **Clean** (Olympic); **Clean Deadlift**; **Dumbbell Clean** |
| glutes | Barbell Glute Bridge; Barbell Hip Thrust; Flutter Kicks | same |
| calves | **Balance Board**; **Knee Circles** (stretch); **Ankle Circles** (stretch) | **Knee Circles**; **Ankle Circles**; Barbell Seated Calf Raise |
| abdominals | 3/4 Sit-Up; Ab Roller; **Air Bike** (a bicycle crunch) | 3/4 Sit-Up; **Air Bike**; Barbell Ab Rollout |

Bold marks a row that none of the surveyed programs uses, and that section 4 steers beginners
away from. Rows with `equipment: null` (both stretches, several plyometrics) pass *every*
equipment filter, which is why they reach even a machines-only gym. This is a simplification:
the day's muscle order and the "already answered" rule can shift which rows win. But the ordering
it approximates is the real one.

---

## 2. The surveyed programs

**Source trust.** Sections 2.1 to 2.7 cite each program's author or its canonical community
home. Nippard's programs (2.8 to 2.9) are paid PDFs. His own product page states the splits and
intent, but the exercise lists here come from **user-uploaded Boostcamp transcriptions**. Two
editions of the full-body program circulate, and they differ. Treat those rows as *indicative of
Nippard's style*, not authoritative. I did not read the PDFs themselves (they are copyrighted and
only mirrored by third parties).

### 2.1 r/Fitness Basic Beginner Routine
- **Split:** full body, alternating A/B, 3 days a week. **Exercises per day:** 3 (optional accessories allowed).
- **A:** barbell row, bench press, squat. **B:** chin-up (or lat pulldown / assisted / band / negatives), overhead press, deadlift.
- **Why:** linear progression on a few barbell lifts. The wiki says to run it for at most three
  months, then move to GZCLP or 5/3/1 for Beginners. The chin-up substitutions are all ways to
  scale it for a beginner.
- Source: [thefitness.wiki, Basic Beginner Routine](https://thefitness.wiki/routines/r-fitness-basic-beginner-routine/)

### 2.2 StrongLifts 5×5
- **Split:** full body A/B, 3 days a week. **Exercises per day:** 3.
- **A:** squat, bench press, barbell row. **B:** squat, overhead press, deadlift.
- **Why (author):** five exercises keep the focus on learning technique rather than on many
  exercises. Free weights need only a bar, bench and rack, and the free-weight area is usually less busy.
- Source: [stronglifts.com, StrongLifts 5×5 guide](https://stronglifts.com/stronglifts-5x5/) and
  [workout program](https://stronglifts.com/stronglifts-5x5/workout-program/)

### 2.3 GZCLP
- **Split:** full body, 4-day rotation (run 3 or 4 times a week). **Exercises per day:** 3 (T1, T2, T3).
- **Days:** squat / bench / lat pulldown · OHP / deadlift / dumbbell row · bench / squat / lat pulldown · deadlift / OHP / dumbbell row.
- **Why:** the wiki says it balances squat against deadlift and adds enough back work. T3 is
  always a pull.
- Source: [thefitness.wiki, GZCLP](https://thefitness.wiki/routines/gzclp/)

### 2.4 5/3/1 for Beginners
- **Split:** full body, 3 days a week, two main lifts per day. **Exercises per day:** 2 main + 3 assistance categories.
- **Main:** squat + bench · deadlift + OHP · bench + squat.
- **Assistance menus (50 to 100 reps each):** *push:* dips, push-ups, flat/incline dumbbell bench,
  dumbbell OHP, triceps extension, triceps pushdown. *Pull:* chin-ups, pull-ups, inverted rows,
  dumbbell rows, machine rows, face pulls, lat pulldowns, curls, band pull-aparts, reverse hypers.
  *Single-leg/core:* ab work, back raises, lunges, step-ups, Bulgarian split squats, KB swings/snatches.
- **Why:** the assistance is about total volume. The wiki tells you not to agonise over the
  exact exercise.
- Source: [thefitness.wiki, 5/3/1 for Beginners](https://thefitness.wiki/routines/5-3-1-for-beginners/)

### 2.5 Metallicadpa's Reddit PPL
- **Split:** push/pull/legs, run twice a week (6 days; can be run as 3). **Exercises per day:** 5 to 6.
- **Pull:** deadlift *or* barbell row, pulldowns/pull-ups/chin-ups, seated cable row *or* chest-supported row, face pulls (or rear delt flyes / band pull-aparts), hammer curls, dumbbell curls.
- **Push:** bench *or* OHP, the other press lighter, incline dumbbell press, triceps pushdown + lateral raise superset, overhead triceps extension + lateral raise superset.
- **Legs:** squat, Romanian deadlift, leg press, leg curl, calf raise.
- **Why:** main lifts drive strength. The accessories are higher-rep bodybuilding work.
- Source: [thefitness.wiki archive of the original post](https://thefitness.wiki/reddit-archive/a-linear-progression-based-ppl-program-for-beginners/)

### 2.6 PHUL (Brandon Campbell)
- **Split:** upper/lower × power/hypertrophy, 4 days a week. **Exercises per day:** 5 to 7.
- **Upper power:** bench, incline dumbbell bench, barbell row, lat pulldown, OHP, barbell curl, skull crusher.
- **Lower power:** squat, deadlift, leg press, leg curl, seated calf raise.
- **Upper hypertrophy:** incline barbell bench, dumbbell fly, seated cable row, one-arm dumbbell row, dumbbell lateral raise, incline dumbbell curl, cable triceps extension.
- **Lower hypertrophy:** front squat, barbell lunge, leg extension, leg curl, seated calf raise, standing calf raise.
- Source: [Boostcamp, the author's coach page](https://www.boostcamp.app/coaches/brandon-campbell/phul-4-day-split);
  original at [Muscle & Strength](https://www.muscleandstrength.com/workouts/phul-workout) (it
  returned 403 to the fetcher, so the exercise list is from Boostcamp).

### 2.7 Lyle McDonald's Generic Bulking Routine
- **Split:** lower/upper, 4 days a week. Written for lifters past their beginner gains. **Exercises per day:** 6.
- **Lower:** squat, stiff-leg deadlift *or* leg curl, leg press, another leg curl, calf raise, seated calf raise.
- **Upper:** flat bench, row, incline bench *or* shoulder press, pulldown/chin, triceps, biceps.
- **Why:** the author swaps in deadlift + leg press on Thursday and says to vary exercises
  *between* cycles, never within one.
- Source: the author's forum post, as compiled in the
  [GBR FAQ (JCD Fitness mirror)](https://www.jcdfitness.com/wp-content/download/Lyle_McDonald_Generic_Bulking_Routine_FAQ.pdf), §1.1

### 2.8 Jeff Nippard: Fundamentals (full body, 3×/week)
- **Official:** three 8-week splits: full body 3×, upper/lower 4×, body-part 5×. For "your first
  year or two of training". Centred on the squat, bench and deadlift with linear progression.
  Exercise substitutions are provided (e.g. leg press for squat), and the program can be done in a
  home gym with a rack, bar, bench and dumbbells.
  [jeffnippard.com product page](https://jeffnippard.com/products/fundamentals-hypertrophy-program)
- **Transcribed week 1** (user upload, [Boostcamp](https://www.boostcamp.app/users/nVBQNg-jeff-nippard-full-body-hypertrophy-1)). **Exercises per day:** 7.
  - Day 1: squat, bench, wide-grip lat pulldown, Romanian deadlift, assisted dip, leg-press calf raise, dumbbell curl.
  - Day 2: deadlift, machine overhead press, machine chest-supported row, leg extension, pec deck, crunch, skull crusher.
  - Day 3: dumbbell walking lunge, incline dumbbell press, underhand lat pulldown, barbell hip thrust, reverse pec deck, dumbbell lateral raise, leg curl.
- A second circulating edition's day 1 is back squat, seated dumbbell shoulder press, single-arm
  pulldown, barbell hip thrust, pec deck, reverse pec deck and cable lateral raise. Same vocabulary,
  different arrangement.

### 2.9 Jeff Nippard: Fundamentals (upper/lower, 4×/week)
- **Transcribed week 1** (user upload, [Boostcamp](https://www.boostcamp.app/users/SPT8fC-jeff-nippards-fundamentals-upperandlower-split-4x)). **Exercises per day:** 7.
  - Upper A: bench, lat pulldown, barbell overhead press, T-bar row, cable fly, alternating dumbbell curl, single-arm cable triceps extension.
  - Lower A: squat, Romanian deadlift, hip thrust, leg extension, lying leg curl, hip abduction machine, decline crunch.
  - Upper B: incline dumbbell press, underhand lat pulldown, assisted dip, barbell row, dumbbell lateral raise, face pull, hammer curl.
  - Lower B: deadlift, dumbbell walking lunge, leg extension, single-leg leg curl, hip abduction machine, standing calf raise, plank.

### 2.10 r/bodyweightfitness Recommended Routine (no-equipment baseline)
- **Split:** full body, 3 days a week, three supersetted pairs: pull-up + squat, dip + hinge, row +
  push-up. Each pair has a progression ladder: incline → floor push-ups, assisted/negative →
  full pull-ups and dips, incline → horizontal rows, bodyweight squat and step-ups.
- Source: [r/bodyweightfitness wiki mirror](https://redditbwf.github.io/wiki/recommended_routine.html)

### 2.11 What real lifters log (popularity, not programs)
Fitbod's analysis of its users' logs (secondary, first-party data; no sample size or date given)
reports that the most-logged movements are **dumbbell rows and curls, lat pulldowns, leg presses and
dumbbell presses**. More users log the dumbbell bench than the barbell bench, and the barbell back squat
and deadlift rank only about 30th and 38th by number of lifters. Caveat: Fitbod generates its
users' workouts, so this partly reflects its own recommender.
[Fitbod blog](https://fitbod.me/blog/how-strong-is-the-average-gym-goer-we-analyzed-millions-of-real-workouts/)

### 2.12 Shape of a day, across all ten

| Program | Split | Days/wk | Exercises/day | Compounds first? | Isolation? |
|---|---|---|---|---|---|
| r/Fitness Basic Beginner | full body A/B | 3 | 3 | all compound | optional |
| StrongLifts 5×5 | full body A/B | 3 | 3 | all compound | none |
| GZCLP | full body ×4 rotation | 3–4 | 3 | yes | T3 pull |
| 5/3/1 for Beginners | full body | 3 | 2 + 3 assistance | yes | in assistance |
| Reddit PPL | push/pull/legs | 6 (or 3) | 5–6 | yes | arms, delts, calves, hams |
| PHUL | upper/lower | 4 | 5–7 | yes | arms, delts, calves, quads/hams |
| Lyle GBR | lower/upper | 4 | 6 | yes | arms, calves, hams |
| Nippard Fundamentals FB | full body | 3 | 7 | yes | delts, arms, calves, chest |
| Nippard Fundamentals U/L | upper/lower | 4 | 7 | yes | delts, arms, hams, quads, abductors, abs |
| BWF Recommended Routine | full body | 3 | 6 (3 pairs) | all compound | core |

The ACSM position stand agrees with this shape. It says to put large muscles before small and
multi-joint lifts before single-joint ones. For novices it recommends both single- and multi-joint
exercises, weighted towards multi-joint (evidence category A), and both **free weights and
machines** (category A). It notes that machines are regarded as safer and easier to learn.
([ACSM 2009, *Med Sci Sports Exerc* 41(3):687–708](https://tourniquets.org/wp-content/uploads/PDFs/ACSM-Progression-models-in-resistance-training-for-healthy-adults-2009.pdf), "Exercise Selection" and "Free Weights and Machines").

---

## 3. The common lifts, ranked

**Method.** Count how many of the ten programs in section 2 use the movement (an "or" choice
counts). Break ties with (a) the Fitbod popularity note, (b) where Nippard's tier-list videos
rank it (see below), and (c) whether it needs coaching to learn. This is a *convention*
ranking, not evidence: 2.1 to 2.3 are barbell-only by design, which inflates the barbell counts.

Nippard's tier lists judge exercises on tension in the stretched position, stability and
comfort, and how easily the load can be progressed. Primary sources are his videos:
[chest](https://www.youtube.com/watch?v=fGm-ef-4PVk), [back](https://www.youtube.com/watch?v=jLvqKgW-_G8),
[shoulders](https://www.youtube.com/watch?v=SgyUoY0IZ7A), [quads](https://www.youtube.com/watch?v=kIXcoivzGf8),
[biceps](https://www.youtube.com/watch?v=GNO4OtYoCYk), [triceps](https://www.youtube.com/watch?v=OpRMRhr0Ycc),
[glutes](https://youtu.be/3ryh7PNhz3E). I read the rankings through a
[community summary](https://hackmd.io/@dastratman/B1w4jGoDJx) and did not watch the videos, so
check any tier before quoting it.

### 3.1 Per muscle (program count out of 10 in brackets; the bank row that fits best in *italics*)

| Muscle | 1st | 2nd | 3rd | 4th | 5th |
|---|---|---|---|---|---|
| **Chest** | Bench press [9] *Barbell Bench Press - Medium Grip* | Incline dumbbell press [4] *Incline Dumbbell Press* | Dumbbell bench press [1+, most-logged] *Dumbbell Bench Press* | Machine chest press [Nippard S+] *Machine Bench Press / Leverage Chest Press* | Pec deck / cable fly / dumbbell fly [3] *Butterfly / Cable Crossover / Dumbbell Flyes* |
| **Chest (also)** | Dips [4] *Dips - Chest Version / Assisted Dip Machine* | Push-up [2] *Pushups* | Incline barbell bench [2] *Barbell Incline Bench Press - Medium Grip* | | |
| **Lats** | Lat pulldown [8] *Wide-Grip Lat Pulldown* | Pull-up / chin-up [5] *Pullups / Chin-Up* | Underhand / close-grip pulldown [2] *Underhand Cable Pulldowns / Close-Grip Front Lat Pulldown* | Assisted pull-up [scaling option in 2.1, 2.10] *Assisted Pull-Up Machine* | Single-arm pulldown [1] *One Arm Lat Pulldown* |
| **Mid back** | Barbell row [6] *Bent Over Barbell Row* | Seated cable row [3] *Seated Cable Rows* | Dumbbell row [3, most-logged] *One-Arm Dumbbell Row* | Chest-supported row [2] *Chest-Supported Row Machine* | T-bar row [1] *T-Bar Row with Handle*; inverted row [2] *Inverted Row* |
| **Shoulders (front)** | Overhead press [9] *Standing Military Press* | Dumbbell shoulder press [2] *Dumbbell Shoulder Press* | Machine shoulder press [1, Nippard A+] *Machine Shoulder (Military) Press* | | |
| **Shoulders (side)** | Dumbbell lateral raise [4] *Side Lateral Raise* | Cable lateral raise [1, Nippard S] *Cable Seated Lateral Raise* | Machine lateral raise *Machine Lateral Raise* | | |
| **Shoulders (rear)** | Face pull [3] *Face Pull* | Reverse pec deck [1] *Reverse Machine Flyes* | Rear delt fly [1] *Reverse Flyes / Cable Rear Delt Fly* | | |
| **Biceps** | Dumbbell curl [5] *Dumbbell Bicep Curl* | Hammer curl [2] *Hammer Curls* | Barbell / EZ curl [2] *Barbell Curl / EZ-Bar Curl* | Incline dumbbell curl [1] *Alternate Incline Dumbbell Curl* | Preacher curl [Nippard S] *Preacher Curl / Machine Preacher Curls* |
| **Triceps** | Triceps pushdown [4] *Triceps Pushdown* | Overhead cable extension [2, Nippard S+] *Cable Rope Overhead Triceps Extension* | Skull crusher [2] *EZ-Bar Skullcrusher* | Dips [4] *Dips - Triceps Version* | Close-grip bench [Nippard A] *Close-Grip Barbell Bench Press* |
| **Quads** | Squat [9] *Barbell Squat* | Leg press [3] *Leg Press* | Leg extension [3] *Leg Extensions* | Lunge / walking lunge [4] *Dumbbell Lunges / Barbell Walking Lunge* | Bulgarian split squat [1, Nippard S] *Bulgarian Split Squat*; hack squat [Nippard S+] *Hack Squat*; goblet squat *Goblet Squat* |
| **Hamstrings** | Romanian deadlift [4] *Romanian Deadlift / Stiff-Legged Dumbbell Deadlift* | Leg curl [5] *Lying Leg Curls / Seated Leg Curl* | Deadlift [8] *Barbell Deadlift* (a full-body hinge; programs count it as back and legs) | Back extension [1] *Hyperextensions (Back Extensions)* | |
| **Glutes** | Hip thrust [2] *Barbell Hip Thrust / Hip Thrust Machine* | (squat, lunge, RDL and deadlift above train glutes secondarily) | Hip abduction machine [1] *Thigh Abductor* | Glute bridge *Barbell Glute Bridge* | |
| **Calves** | Standing calf raise [4] *Standing Calf Raises* | Seated calf raise [2] *Seated Calf Raise* | Leg-press calf raise [1] *Calf Press On The Leg Press Machine* | | |
| **Abs** | Crunch / decline crunch [2] *Crunches / Decline Crunch* | Plank [1] *Plank* | Cable crunch *Cable Crunch* | Hanging leg raise *Hanging Leg Raise* | |

Program counts overlap rows (e.g. "curls" in 5/3/1's assistance menu counts once).

### 3.2 Per equipment type (the lifts to offer first when that is what the gym has)

| Equipment | Ranked common lifts |
|---|---|
| **Barbell** | squat · bench press · deadlift · overhead press · barbell row · Romanian deadlift · incline bench · hip thrust · barbell curl · close-grip bench · (front squat, lunge: intermediate programs only) |
| **Dumbbell** | dumbbell row · incline dumbbell press · dumbbell bench press · dumbbell curl · lateral raise · dumbbell shoulder press · hammer curl · walking lunge / split squat · dumbbell RDL · goblet squat · dumbbell fly |
| **Cable** | lat pulldown (wide / underhand / close) · seated cable row · triceps pushdown · face pull · overhead cable triceps extension · cable lateral raise · cable fly / crossover · cable curl · cable crunch |
| **Machine** | leg press · leg curl (lying / seated) · leg extension · machine chest press · pec deck · chest-supported row · machine shoulder press · calf raise (standing / seated / leg press) · hack squat · hip abduction · reverse pec deck · assisted pull-up / dip · hip thrust machine · Smith machine squat / bench |
| **EZ bar** | EZ-bar curl · skull crusher |
| **Body only** | push-up · pull-up / chin-up · dip · inverted row · bodyweight squat → split squat · walking lunge · plank · crunch · back extension · hanging leg raise |
| **Kettlebell** | goblet squat · kettlebell swing (5/3/1 assistance; see the note in 4) |

---

## 4. Lifts beginners are steered away from, and why

None of the ten programs uses anything below. Reasons are marked the way `splits-and-volume.md`
marks them: **[Evidence]** (research supports it) or **[Convention]** (practitioners agree, little
direct evidence). The "In the bank" column says how the generator could recognise them.

| Group | Why beginners avoid it | In the bank |
|---|---|---|
| **Olympic lifts and their variants** (clean, hang clean, snatch, clean and jerk, push press, clean pull) | Complex, fast technique that needs coaching. The ACSM stand places them under power and advanced training, not novice work [Evidence, for placement]. None of the surveyed programs uses them [Convention]. | `category: olympic weightlifting`, but some are mis-tagged as `strength` (e.g. *Dumbbell Clean*, *Alternating Hang Clean*) |
| **Accommodating resistance and powerlifting specialties** (chains, bands on the bar, board press, reverse-band, box squat with bands, deficit deadlift, rack pull) | Tools for a competition lifter's sticking points. Nothing for a beginner to gain [Convention]. Nippard ranks the above-knee rack pull low. | Mostly `level: expert` and/or `category: powerlifting`. But *Barbell Hip Thrust* and *Bench Press - Powerlifting* also carry `powerlifting`, so the category alone would throw away staples |
| **Plyometrics and jumps** (bounds, jump squats, depth jumps, plyo push-ups) | Conditioning and power work, not a hypertrophy/strength base. Nippard ranks jump squats F for quads [Convention] | `category: plyometrics` |
| **Strongman** (drags, yoke, car deadlift, axle) | Needs special equipment and is event-specific [Convention] | `category: strongman` |
| **Stretches and foam rolling** | Not resistance work at all. They should never fill a set budget | `category: stretching`, often `equipment: null`, so they pass every gym filter |
| **Unstable-surface lifting** (Bosu, balance board, lifting on an exercise ball) | Lower stability means less tension on the target muscle. Nippard ranks Bosu squats F- [Convention] | names containing *Bosu*, *Balance Board*, *Exercise Ball*; `equipment: exercise ball` |
| **Upright rows and lateral raises above shoulder height** | In a case-control study of recreational lifters, raising the arms above 90° in these two lifts was significantly associated with signs of shoulder impingement (p ≤ 0.004). Strengthening the external rotators had the opposite association. [Evidence, cross-sectional] ([Kolber et al. 2014, *JSCR* 28(4):1081–1089](https://elementssystem.com/wp-content/uploads/2018/06/Kolber.pdf)) | *Standing Dumbbell Upright Row*, *Smith Machine Upright Row*, *Upright Cable Row* and others. Lateral raises stay; the issue is the range of motion, not the lift |
| **Behind-the-neck press and pulldown** | Widely warned against [Convention]. **But** the same Kolber study found *no* significant association between these and impingement signs. Leave them out of generated routines because no common program uses them, not on safety grounds | *Wide-Grip Pulldown Behind The Neck*, *Push Press - Behind the Neck* |
| **Awkward or low-value variants** (renegade row, around-the-worlds, guillotine press, hex/plate press, dumbbell pullover, front raises, drag curl, kickbacks) | Ranked D to F by Nippard for poor tension or stability, or because they overlap with presses (front raises) [Convention] | *Alternating Renegade Row* (expert), *Around The Worlds*, *Barbell Guillotine Bench Press*, *Front Dumbbell Raise*, *Drag Curl*, pullovers |
| **Kettlebell swing** | Mixed: in 5/3/1's assistance menu, but ranked D for glutes by Nippard. It is a conditioning hinge that needs coaching to learn. Offer it only when the lifter asks | *Kettlebell Swing* |
| **Good mornings** | Not in any surveyed program. The RDL does the same job and is easier to learn [Convention] | *Good Morning* and variants |

**Deadlift and squat are not on this list.** Every barbell beginner program includes them from
day one. The evidence on danger is also reassuring: the injury rate in weight-training sports is
low compared with team sports, and bodybuilding-style training has the lowest rate,
0.24 to 1 injury per 1000 hours
([Keogh & Winwood 2017, *Sports Med* 47(3):479–501](https://pubmed.ncbi.nlm.nih.gov/27328853/)).
The shoulder is the most commonly injured area in resistance training (up to 36% of documented
injuries), and poor technique is a named risk factor
([Kolber et al. 2010, *JSCR* 24(6):1696–1704](https://pubmed.ncbi.nlm.nih.gov/20508476/)).
The case for easy-to-learn lifts rests on technique, not on danger. Where a machine version exists,
ACSM notes machines are "regarded as safer to use, easy to learn."

---

## 5. Bank name → plain name

Only rows where the bank's display name differs from what the programs call the lift. Most
of these plain names already exist as **search aliases** in `data/aliases.json`. What differs is
the name shown on screen. Whether to rename is the map's open question ("Exercise renames",
ADR-0009 interaction), and this note does not settle it.

| Bank name (display) | Plain name the programs use | Note |
|---|---|---|
| Barbell Squat | Squat / back squat | *Barbell Full Squat* is a near-duplicate row (intermediate) |
| Barbell Bench Press - Medium Grip | Bench press | |
| Barbell Incline Bench Press - Medium Grip | Incline bench press | |
| Standing Military Press | Overhead press (OHP) | *Barbell Shoulder Press* is the seated/other row |
| Seated Barbell Military Press | Seated overhead press | |
| Dumbbell Shoulder Press | Seated dumbbell shoulder press | tagged intermediate |
| Machine Shoulder (Military) Press / Leverage Shoulder Press | Machine shoulder press | two rows, one movement |
| Machine Bench Press / Leverage Chest Press | Machine chest press | two rows, one movement |
| Leverage Incline Chest Press / Machine Incline Chest Press | Incline machine press | authored row duplicates the upstream one |
| Bent Over Barbell Row | Barbell row | |
| One-Arm Dumbbell Row | Dumbbell row | |
| Seated Cable Rows | Seated cable row | plural |
| T-Bar Row with Handle | T-bar row | |
| Chest-Supported Row Machine | Chest-supported row | |
| Pullups | Pull-up | |
| Wide-Grip Lat Pulldown | Lat pulldown | |
| Underhand Cable Pulldowns | Underhand (reverse-grip) lat pulldown | |
| Close-Grip Front Lat Pulldown | Close-grip lat pulldown | |
| One Arm Lat Pulldown | Single-arm lat pulldown | |
| Butterfly | Pec deck / machine chest fly | |
| Reverse Machine Flyes | Reverse pec deck | |
| Reverse Flyes | Rear delt fly (dumbbell) | |
| Cable Crossover | Cable fly | |
| Dumbbell Flyes | Dumbbell fly | |
| Side Lateral Raise | Lateral raise | |
| Cable Seated Lateral Raise | Cable lateral raise | |
| Dumbbell Bicep Curl | Dumbbell curl | |
| Hammer Curls | Hammer curl | |
| Alternate Incline Dumbbell Curl | Incline dumbbell curl | |
| Machine Preacher Curls | Machine preacher curl | |
| Triceps Pushdown / Triceps Pushdown - Rope Attachment | Triceps pushdown / rope pushdown | |
| Cable Rope Overhead Triceps Extension | Overhead cable triceps extension | |
| EZ-Bar Skullcrusher / Lying Triceps Press | Skull crusher | two rows, one movement |
| Dips - Triceps Version / Parallel Bar Dip / Dips - Chest Version | Dip | three rows, one movement |
| Pushups | Push-up | |
| Leg Extensions | Leg extension | |
| Lying Leg Curls | Lying leg curl | |
| Standing Calf Raises | Standing calf raise | |
| Calf Press On The Leg Press Machine | Leg press calf raise | |
| Stiff-Legged Dumbbell Deadlift | Dumbbell Romanian deadlift | |
| Dumbbell Lunges / Barbell Walking Lunge / Bodyweight Walking Lunge | Lunge / walking lunge | no dumbbell walking lunge row, and it is the one Nippard uses |
| Split Squat with Dumbbells | Dumbbell split squat | |
| Thigh Abductor / Thigh Adductor | Hip abduction / hip adduction machine | |
| Hyperextensions (Back Extensions) | Back extension | |
| Crunches | Crunch | |
| Front Squat (Clean Grip) / Front Barbell Squat | Front squat | tagged intermediate / expert |

**Names that mislead outright** (a plain reader would guess wrong): *Air Bike* (a bicycle crunch,
not the fan bike), *Butterfly* (pec deck), *Leverage …* (plate-loaded machine), *Around The Worlds*,
*Anti-Gravity Press*, *Frankenstein Squat*, *Rocky Pull-Ups/Pulldowns*, *Bradford/Rocky Presses*,
*Gorilla Chin/Crunch*, *Balance Board*, *Backward Drag*.

---

## 6. What this implies for #139 (inputs, not a design)

- **Rank by a curated common-lift list, not by id.** Section 3 is small (about 60 rows across all
  muscles and equipment) and could be a hand-authored field or a data file. Upstream has no
  popularity signal to reuse, and `level` is unreliable (section 0, point 4).
- **Hard-exclude `stretching`, `plyometrics`, `olympic weightlifting`, `strongman`** and
  `level: expert` from *generation*. Leave them searchable and addable by hand. Do **not**
  exclude `powerlifting` wholesale: it would drop the hip thrust.
- **Keep the program shape.** 3 exercises per day for a barbell-novice full body, up to 5 to 7 for
  hypertrophy upper/lower or PPL. Big compound first, isolation after. This matches the
  existing compound-first ordering and the `splits-and-volume.md` §5 inputs.
- **Several bank rows are one movement** (machine chest press ×2, skull crusher ×2, dip ×3). A
  curated list should name one row per movement, so a routine never offers two versions of the
  same lift as if they were different exercises.

---

## Sources

Programs
- thefitness.wiki: [Basic Beginner Routine](https://thefitness.wiki/routines/r-fitness-basic-beginner-routine/), [GZCLP](https://thefitness.wiki/routines/gzclp/), [5/3/1 for Beginners](https://thefitness.wiki/routines/5-3-1-for-beginners/), [Metallicadpa PPL (archived original post)](https://thefitness.wiki/reddit-archive/a-linear-progression-based-ppl-program-for-beginners/), [recommended routines index](https://thefitness.wiki/routines/strength-training-muscle-building/)
- StrongLifts: [5×5 guide](https://stronglifts.com/stronglifts-5x5/), [workout program](https://stronglifts.com/stronglifts-5x5/workout-program/)
- PHUL: [Boostcamp, Brandon Campbell's coach page](https://www.boostcamp.app/coaches/brandon-campbell/phul-4-day-split); [Muscle & Strength original](https://www.muscleandstrength.com/workouts/phul-workout)
- Lyle McDonald GBR: [FAQ compilation quoting the author's forum post](https://www.jcdfitness.com/wp-content/download/Lyle_McDonald_Generic_Bulking_Routine_FAQ.pdf)
- Jeff Nippard: [Fundamentals product page](https://jeffnippard.com/products/fundamentals-hypertrophy-program); user transcriptions on Boostcamp ([full body](https://www.boostcamp.app/users/nVBQNg-jeff-nippard-full-body-hypertrophy-1), [upper/lower](https://www.boostcamp.app/users/SPT8fC-jeff-nippards-fundamentals-upperandlower-split-4x)), secondary
- Nippard tier-list videos: [chest](https://www.youtube.com/watch?v=fGm-ef-4PVk), [back](https://www.youtube.com/watch?v=jLvqKgW-_G8), [shoulders](https://www.youtube.com/watch?v=SgyUoY0IZ7A), [quads](https://www.youtube.com/watch?v=kIXcoivzGf8), [biceps](https://www.youtube.com/watch?v=GNO4OtYoCYk), [triceps](https://www.youtube.com/watch?v=OpRMRhr0Ycc), [glutes](https://youtu.be/3ryh7PNhz3E), rankings read via a [community summary](https://hackmd.io/@dastratman/B1w4jGoDJx)
- r/bodyweightfitness: [Recommended Routine (wiki mirror)](https://redditbwf.github.io/wiki/recommended_routine.html)
- Fitbod: [most-logged exercises](https://fitbod.me/blog/how-strong-is-the-average-gym-goer-we-analyzed-millions-of-real-workouts/)

Evidence
- American College of Sports Medicine (2009). Progression models in resistance training for healthy adults. *Med Sci Sports Exerc* 41(3):687–708. [PDF](https://tourniquets.org/wp-content/uploads/PDFs/ACSM-Progression-models-in-resistance-training-for-healthy-adults-2009.pdf)
- Kolber MJ, Cheatham SW, Salamh PA, Hanney WJ (2014). Characteristics of shoulder impingement in the recreational weight-training population. *J Strength Cond Res* 28(4):1081–1089. [PDF](https://elementssystem.com/wp-content/uploads/2018/06/Kolber.pdf)
- Kolber MJ, Beekhuizen KS, Cheng MS, Hellman MA (2010). Shoulder injuries attributed to resistance training: a brief review. *J Strength Cond Res* 24(6):1696–1704. [PubMed 20508476](https://pubmed.ncbi.nlm.nih.gov/20508476/)
- Keogh JW, Winwood PW (2017). The epidemiology of injuries across the weight-training sports. *Sports Med* 47(3):479–501. [PubMed 27328853](https://pubmed.ncbi.nlm.nih.gov/27328853/)

Repository
- `app/src/main/java/com/tylergimpel/gymtracker/domain/Generator.kt` (`fillDays`, `availableFor`, `buildDay`), `domain/ExerciseSearch.kt` (`worthOffering`)
- `data/upstream-exercises.json`, `data/authored-exercises.json`, `data/bank-patches.json`, `data/aliases.json`, `data/README.md`
- `docs/adr/0002-exercise-bank-source-and-omissions.md`, `docs/adr/0004-recommendation-engine.md`, `docs/research/splits-and-volume.md`
