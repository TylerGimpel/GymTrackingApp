# Cold start: recommending a starting weight with no training history

Research for [issue #4](https://github.com/TylerGimpel/GymTrackingApp/issues/4). Feeds the cold-start branch of
[#12 (weight recommendation engine)](https://github.com/TylerGimpel/GymTrackingApp/issues/12).

**Scope.** What weight the app should put in front of a user who has no history for a given exercise, given
bodyweight, height, sex, self-reported training experience, and age.

---

## Straight answers first

**1. Does height predict starting strength?** No. Not usefully, and not in a direction you can put in a formula.

Height correlates with *absolute* strength only because taller people are heavier. Once bodyweight is known,
height's remaining correlation with strength is approximately zero. In the cleanest available measurement
(Ferland et al. 2020, n=59, all three powerlifts measured at a drug-tested competition), height correlated
r = .27–.37 with absolute squat/bench/deadlift/total — but r = **.00, −.07, −.08, −.05** with the same lifts
after adjusting for bodyweight. Bodyweight itself correlated r = .64–.78 with the absolute lifts. Height is
carrying bodyweight's signal and nothing of its own.

Do not build a height term. Details and caveats in [What anthropometrics actually predict](#what-anthropometrics-actually-predict).

**2. Does an estimation model beat "start at the empty bar and ramp fast"?** No. Ramping wins, and it isn't close.

The best published anthropometric prediction of an untrained person's 1RM has a standard error of estimate of
about ±5.6 kg on a bench press whose true value is often 25–40 kg — a ~20% error, with a 95% interval wide
enough that the estimate could be double or half the truth. A directly measured 1RM in an untrained person has
a median test–retest CV of 5.5%. Measuring is roughly three to four times more precise than the best estimate
available, and it costs one session.

The model's legitimate job is therefore **not** to produce the working weight. It is to pick where the ramp
*starts*, so the ramp takes three sets instead of eight. Details in
[Model vs. ramp](#model-vs-ramp-the-quantitative-case).

**3. The fallback for machines, cables, accessories and unilateral work** — most of the exercise bank — is the
same protocol, not a worse version of it. There are no credible standards for these, and more fundamentally the
number on a weight stack is not a physical load: pulley ratios vary by manufacturer (1:1, 2:1, 4:1) and guide-rod
friction adds unlabelled resistance, so a "50" on one lat pulldown is not a "50" on another. Calibrate against
the machine in front of the user and treat its scale as a gym-local unit. Details in
[Exercises with no standards data](#exercises-with-no-standards-data).

---

## Published strength standards and their licensing

### How the credible tables express norms

Every serious table expresses strength as a multiple of bodyweight, segmented by sex and by training level, and
several adjust for age. **None of them use height.** That is itself evidence — see the height section.

The same is true of the sport's normalisation formulas. Wilks, IPF GL, and allometric scaling all take bodyweight
as the sole anthropometric input. The largest analysis of this to date (Frontiers in Physiology, 2026) regressed
total lifted against body mass across **457,471 athletes** from the OpenPowerlifting database and found scaling
exponents of b = 0.55 (male) and 0.50 (female) in the general competitive population, decaying to 0.47 and 0.41
at absolute-elite level — versus the theoretical square-cube value of 0.67. Height is not a variable in that
analysis, nor in any of the normalisation formulas it compares.

### Licensing — the part that matters for shipping

The map sets "legally clean" as a hard constraint. On that test, most of the well-known tables are unusable.

| Source | Content | Licence status | Verdict |
|---|---|---|---|
| **ExRx.net / Lon Kilgore** | Untrained→Elite tables for barbell lifts by bodyweight and sex | Content copyrighted and registered with the US Library of Congress; the exercise database is a proprietary compilation of ExRx.net, LLC. Terms prohibit modification, sale, or republication without express written permission, and explicitly prohibit automated scraping or manual copying "including compiling or reproducing large portions". | **Do not embed** |
| **Strength Level** | 195M+ lifts, 27.9M users, 287 exercises incl. machines/cables; percentile-defined tiers (Beginner = 5th pct, Novice 20th, Intermediate 50th, Advanced 80th, Elite 95th) | "© 2026 Strength Level Limited. All rights reserved." T&Cs state: "You will not replicate or create derivative versions of our calculators or standards" and "You will not systematically download, store, or copy any data or content from the Service." | **Do not embed** — the contract term bites regardless of copyright |
| **Symmetric Strength** | Lifter analysis and standards | No open licence found | **Do not embed** |
| **OpenPowerlifting** | Verified competition results, 200+ federations | Data (`*.csv` under `meet-data/`) **contributed to the public domain**, copyright and neighbouring rights waived; attribution encouraged but not required. Code is AGPLv3+ (the repo root `LICENSE` is the AGPL — the data waiver is stated separately in the README). | **Usable** — but see the sample problem below |
| **NHANES (CDC)** | Population grip strength, 13,676 participants aged 6–80 | US government work, public domain | Usable but **irrelevant** — grip strength does not convert to a barbell load |
| **ACSM position stand** | Loading recommendations, not a norms table | Text copyrighted; the *recommendations* are facts/ideas you can implement without copying expression | **Usable as guidance** |

**The OpenPowerlifting catch.** It is the only large, genuinely public-domain strength dataset, and it is the
wrong population. It contains people who entered a powerlifting meet. Even its bottom percentile is a person who
trained, cut weight, and competed. Using its 5th percentile as an "untrained" anchor would systematically
overestimate a true novice. It also covers only squat/bench/deadlift — nothing for the rest of the bank.

**A note on the "facts aren't copyrightable" argument.** It is true in US law that raw facts are not protected
and that a table's *selection and arrangement* is what attracts protection. It is also irrelevant here: both
ExRx and Strength Level bind you by terms of use, which are contract, not copyright, and bind independently of
whether the underlying numbers are protectable. This is not legal advice. Given the author's stated constraint,
the safe path is to **not ship a third-party standards table at all** — which, per the recommendation below, the
app does not need.

---

## What novice programs actually do

Three independent, widely-used novice programs were checked. All three do the same thing, and none of them
estimate:

**StrongLifts 5×5** (first-party): "Start with the empty bar on the Squat, Bench and Overhead Press. The empty
Olympic bar weighs 45lb/20kg." Deadlift 40–60 kg, barbell row 30–40 kg. Rationale given: "Starting light reduces
the risk of getting hurt as a new lifter. It makes it easier to focus on proper form", it "decreases soreness
that can make you want to skip the next workout", and it "gives you room to progress." Increments: 2.5–5 kg for
squat/deadlift, 1.25–2.5 kg for bench/press/row.

**r/Fitness Basic Beginner Routine** (first-party): "Start with an empty barbell and do a set of five reps. If
this is completed easily with good form, add 10-20 lbs to the bar for the next set." Continue until "form falters
or the bar speed slows significantly more than the preceding sets, whichever comes first. This is now your
starting weight." Explicitly: "The intent here is not to find your max, but to find a good starting point that
allows for quality work sets and ample room for improvement."

**Starting Strength** (Rippetoe): start with the empty bar doing sets of 5 and go up in small jumps; when you
reach a weight that feels heavy but has not changed your form, stay there for two more sets. Jump size is chosen
"whatever is appropriate for your age and strength". Subsequent per-workout increments differ by lift: squat
~10 lb for the first two weeks then 5 lb; press/bench 5 lb then 2–3 lb; deadlift 15 lb.

**The pattern.** Every one of them performs an in-session ramp with a subjective stopping criterion (form change
or bar-speed slowdown), and every one deliberately undershoots. This is also, structurally, the same thing the
NSCA 1RM test protocol does — warm up at a 5–10 rep load, add 5–10% (upper body) or 10–20% (lower body), repeat —
except the novice version stops well short of a maximum.

This is not folklore that happens to be widespread. It is the correct answer to a measurement problem: the
quantity you want is cheap to measure directly and expensive to predict.

---

## What anthropometrics actually predict

### Bodyweight: yes, strongly

In Ferland et al. (2020), bodyweight correlated r = .78 / .70 / .64 / .76 with absolute squat / bench / deadlift /
total. Lean bodyweight correlated r = .67 / .62 / .66 / .70. This is the well-established finding and it is what
every standards table and every normalisation formula is built on.

### Height: no

The full row from Ferland et al. Table 4 (n=59 male classic powerlifters). Columns S/B/D/T are absolute squat,
bench, deadlift and total; SW/BW/DW/TW are the Wilks-adjusted (i.e. bodyweight-normalised) equivalents. Note the
column header "BW" means *Bench Wilks*, not bodyweight:

| Variable | S | B | D | T | SW | BW | DW | TW |
|---|---|---|---|---|---|---|---|---|
| **Height** | .34 | .27 | .37 | .36 | **.00** | **−.07** | **−.08** | **−.05** |
| Bodyweight | .78 | .70 | .64 | .76 | .37 | .26 | .00 | .24 |
| Lean bodyweight | .67 | .62 | .66 | .70 | .28 | — | — | — |

Height's entire apparent relationship with strength evaporates when bodyweight is controlled for. The app already
collects bodyweight. Height therefore adds nothing.

**Corroboration from an untrained sample.** Scanlan, Ballmann, Mayhew & Lantz (1999) measured 18 anthropometric
variables plus 7 derived ones on 113 untrained college females to predict 1RM bench press. Highest zero-order
correlations were arm cross-sectional area (r = .45), flexed arm circumference (r = .45), mesomorphy (r = .44) and
forearm circumference (r = .42) — all measures of *muscle*, not *length*. Critically, "first-order partial
correlations holding constant body mass or FFM generally decreased most correlations with bench press (r < .30)."
Their stated conclusion: prediction of bench press strength from anthropometric dimensions "does not appear to be
practical or accurate in untrained females."

**Where height does have a real effect, it points the wrong way and flips sign by lift.** Ferland's stepwise
regressions found reach/height ratio (longer limbs for a given stature) *positively* predicted relative deadlift
strength (β = 0.37, p < .01) but *negatively* predicted relative bench press strength (β = −0.25, p = .02). That
is the moment-arm story: longer arms lengthen the bench bar path and shorten the deadlift one. It is real, it is
small, and it is not expressible as a single "taller ⇒ stronger" multiplier — a correct height term would have to
be *negative* for bench and *positive* for deadlift, which is the opposite of what a naive model would do.

**The resolution argument settles it independently.** Even granting height some effect, the app's output is
rounded to loadable increments — 2.5 kg on a barbell, often 5 kg on a machine stack. A height term would move the
estimate by less than one increment across the realistic height range, inside an estimate that already carries
~20% error. It would be rounded away before the user ever saw it.

**Caveats, stated honestly.** Ferland's sample is 59 trained competitors with restricted range on height
(174.2 ± 6.8 cm), which attenuates correlations; it is not a general-population sample. The Scanlan study is
untrained but female-only and bench-press-only. I did not find a large study directly regressing untrained 1RM on
height + bodyweight + sex in a general population — that ideal study does not appear to exist in accessible
literature. What exists is convergent rather than conclusive: two samples at opposite ends of the training
spectrum both showing height's signal collapsing under bodyweight, plus the negative evidence that no
normalisation formula in the sport has ever found height worth including.

**Verdict: do not use height in the cold-start formula.** It is not that height is forbidden — it is that it
contributes nothing the app can act on. If the author wants height collected, it has honest uses elsewhere: BMI
display, cardio energy-expenditure estimates, and FFM-normalised-to-height metrics (which did show up as a strong
correlate in the literature Ferland reviews). Keep the input if those are wanted. Do not let it into this formula.

### Sex: yes, but it is largely a proxy for muscle mass

Women's lower-body strength runs roughly 65–75% of men's and upper-body strength substantially lower. But when
expressed relative to fat-free mass the lower-body difference largely disappears while the upper-body difference
persists. The practical consequence for the app: sex needs a **different multiplier for upper vs. lower body**, not
one global factor. Since the app is not measuring FFM, sex remains a useful input — but only for seeding the ramp.

### Age: a modifier on the ramp, not on the target

Strength declines with age, and standards tables apply age adjustments. For cold start specifically, the safer
use of age is Rippetoe's: reduce the *jump size* and add a ramp set for older users. Don't lower the target — let
the ramp find it.

---

## Model vs. ramp: the quantitative case

| | Best anthropometric estimate | First-session ramp |
|---|---|---|
| Precision | R = 0.58, SEE ±5.6 kg on an untrained female bench press (Scanlan et al. 1999) | Median test–retest CV 5.5% in untrained individuals; ICCs mostly ≥ .90 (systematic review, 32 studies, pooled n = 1595) |
| Cost | Zero sessions | One session, partly absorbed by warm-up sets the user does anyway |
| Failure mode | Silently wrong by up to 2× in either direction | Stops on the user's own feedback before it gets heavy |

**And the target is wide anyway.** ACSM's position stand records that "loads of 45–50% of 1 RM (and less) have been
shown to increase dynamic muscular strength in previously untrained individuals", that meta-analytic data show
"60% of 1 RM produced the largest effect sizes for strength increases in novice individuals", and that "for novice
individuals, it has been suggested that moderate loading (50–60% of 1 RM or less) be used initially as learning
proper form, and technique is paramount." Its formal recommendation (Evidence category A) is 60–70% of 1RM for
8–12 repetitions.

So the productive novice zone spans roughly 45–70% of 1RM. That is a ±20% wide target. A ramp that stops on
feel lands inside it essentially always. An estimate with ±20% error lands inside it sometimes — and when it
misses, it can miss high.

**Conclusion: an estimation model does not beat starting light and ramping.** Say so in the spec. Reassign the
model to ramp-seeding, where its error budget is measured in wasted warm-up sets rather than in injuries.

---

## Exercises with no standards data

This is most of the bank: machines, cables, accessories, unilateral work.

### Why no table can help here, even a licensed one

1. **The stack number is not a load.** Cable machines have manufacturer-specific movement ratios — 1:1, 2:1, 4:1.
   A 2:1 ratio halves the resistance at the handle. Guide-rod friction and cable drag add resistance that is not
   on the label. The same "50" differs between two lat pulldowns in the same gym.
2. **Stack increments are not standard.** Plate denominations vary by machine and manufacturer.
3. **Geometry varies.** Two machines nominally training the same movement have different lever arms and strength
   curves.

There is therefore no transferable unit in which a standard could be expressed. Strength Level does publish
machine and cable data across 287 exercises — but besides being contractually off-limits, it is self-reported and
aggregated across machines whose scales are not comparable, so the number would be meaningless even if it were
free.

This dovetails with a decision the map has already made: the display unit is a *remembered per-exercise property*
because one gym has a lb lat pulldown and a kg one. Extend the same reasoning — **a machine's stack scale is its
own gym-local unit**, and the app should not attempt cross-machine conversion or cross-machine inference.

### What to do instead

ACSM's novice recommendation is already expressed as an **8–12 RM** — a repetition-maximum target, not a
percentage. An RM target is self-calibrating: it defines the load by the reps achieved, so it needs no standards
table at all. That is the fallback, and it is the same protocol used for barbells with a different seed.

**Unilateral work:** seed at roughly 50% of the calibrated bilateral load per limb and let the ramp correct
upward. (The bilateral deficit means the per-limb sum often exceeds the bilateral load, so 50% is a deliberate
undershoot — correct for a seed.)

**Dumbbell from barbell:** seed conservatively — a fraction of the calibrated barbell load per hand — and ramp.
Treat this as a ramp heuristic only. It is not a claim about the user's strength and should not be presented as one.

---

## Experience level

**Self-reported experience is weak data, and the literature says so.** The point at which someone becomes
resistance-"trained" is not well defined; there are no objective parameters for assessing training status; and
eligibility criteria based on self-reported training history typically go unverified even in published research.
One proposed classification model uses five parameters — current uninterrupted training time, time detrained,
previous experience, exercise technique, and strength level — precisely because a single self-reported label is
insufficient.

ACSM's own definitions are usable and worth matching so the app's vocabulary lines up with the literature:

- **Novice** — "untrained individuals with no RT experience *or who have not trained for several years*"
- **Intermediate** — "individuals with approximately 6 months of consistent RT experience"
- **Advanced** — "individuals with years of RT experience"

Note that ACSM's novice definition folds **detraining** in: a long layoff returns you to novice. That is the same
mechanism #12 needs for layoff decay, and the definitions should be shared rather than invented twice.

**How the level should map onto a number: it shouldn't.** Use experience to set *ramp behaviour*, not the target:

- **Ramp aggressiveness** — step size and number of ramp sets.
- **The plausibility cap** — the ceiling above which the app refuses to go on session one regardless of feedback.
- **Whether to offer a bypass.** An experienced user switching from another app is the one case where the app's
  estimate is strictly worse than the user's own knowledge. Offer "I already know my working weight" and take the
  number. This is cheaper and more accurate than any model, and it removes the main population for whom a
  novice-calibrated ramp is annoying.

---

## Safety posture

The costs are asymmetric and the evidence says the cold-start user sits in the highest-risk group.

A systematic review of 28 studies covering 13,127 practitioners found injury rates of <1 per 1000 h for
traditional strength training, rising to 3.2 (weightlifting), 4.0 (powerlifting), 4.2 (CrossFit/HIFT) and 5.5
(strongman). Most common sites were shoulder (15–46%), back/lower back (12–36%) and knee (8–21%). The risk factors
it identifies are **prior injuries, less training experience, inadequate form, and insufficient supervision** —
which is an exact description of a novice, alone, following an app, on their first session with an unfamiliar
movement.

Against that: ACSM records that loads of 45–50% of 1RM *and less* still increase strength in previously untrained
individuals. **A too-light first session is not a wasted session.** It is still a training stimulus, and it is
still a technique rehearsal.

So the loss function is genuinely one-sided:

- **Too light** — costs a fraction of one session's stimulus, in a population where almost any load works.
- **Too heavy** — form breakdown, a plausible injury, and a novice who quits.

**Design consequence: the seed must be biased low, not centred.** Target roughly the 5th percentile of the
plausible distribution rather than the median. An estimator whose job is to be *under* the truth ~90%+ of the
time is the correct estimator here, and that is a different object from a best-guess estimator.

---

## Recommended cold-start approach

**Cold start is a calibration protocol, not a prediction.** The app's first session with an exercise measures the
user rather than guessing at them. Bodyweight, sex, age and experience are used only to shorten the measurement.

### Tier A — barbell compounds

1. **Seed** at the empty bar (20 kg) for squat/bench/press/row; deadlift seeds higher (~40–60 kg) because the bar
   must sit at standard height. Optionally raise the seed using bodyweight and sex to skip obviously-trivial
   steps — a 95 kg male can start the squat ramp at 40 kg. Getting this wrong costs ramp sets, not safety.
2. **Ramp** in sets of 5, adding a jump each set: larger for squat/deadlift, smaller for bench/press. Shrink the
   jump as load rises. Reduce jump size for older users.
3. **Stop** on the first set the user marks as hard, or where they report form/bar-speed degradation. Ask after
   each ramp set with a three-way control: *easy / moderate / hard*.
4. **Working weight** = the last "moderate" load (equivalently, the "hard" load minus one jump).
5. **Cap the ramp** at ~6 sets so session one does not become forty minutes.

### Tier B — dumbbells and fixed-increment equipment

Same protocol, fewer steps, because the increments are chunkier. Seed conservatively from a calibrated related
lift where one exists.

### Tier C — machines, cables, accessories, unilateral (most of the bank)

1. Set a target rep range — ACSM novice default 8–12.
2. Seed at a low pin position. Do not attempt cross-machine inference.
3. One set, then *easy / moderate / hard*; adjust by one or two pin positions.
4. Converges within one to two sessions. Record the result against **that exercise**, in that exercise's
   remembered unit.

### Cross-cutting rules

- **Bias every seed low.** Undershoot by design.
- **Plausibility cap** from bodyweight (never height), used only to catch data-entry errors — not to set the
  recommendation.
- **Show confidence.** Session one should say it is a starting guess and invite correction, per the confidence
  question in #12. Do not present a calibration seed with the same authority as a progression recommendation.
- **Hand off at session two.** The calibration result becomes the exercise's first data point; the progression
  engine from [#3](https://github.com/TylerGimpel/GymTrackingApp/issues/3)/#12 takes over.
- **Offer the bypass.** "I already know my working weight" beats every model for experienced users.

### What this means for the spec

- **No third-party standards table ships.** Nothing needs to be licensed, which satisfies the map's hard
  constraint cleanly rather than narrowly.
- **Height is not used** in the recommendation. Keep the input only if BMI or cardio features want it.
- **A dependency on [#11](https://github.com/TylerGimpel/GymTrackingApp/issues/11):** the log model must be able to
  represent a *calibration/ramp set* distinctly from a working set, or session one corrupts both the volume
  figures and the progression engine's view of history. Worth raising on #11 explicitly — it is not currently in
  its set-type list.
- **Shared definitions with #12:** the novice/intermediate/advanced definitions and the "detraining returns you to
  novice" rule are the same mechanism as layoff decay. Define once.

---

## Where the evidence is thin

Flagged rather than papered over:

- No large general-population study regressing untrained 1RM on height + bodyweight + sex was found. The height
  conclusion rests on convergent evidence from two unrepresentative samples plus the negative evidence of
  normalisation formulas, not on one decisive study.
- Ferland's n=59 has restricted range and a trained population; correlations there are attenuated.
- The dumbbell-from-barbell and unilateral-from-bilateral seed fractions are engineering heuristics for starting a
  ramp, not evidence-based ratios. They are safe because the ramp corrects them, not because they are accurate.
- The machine stack-ratio and friction claims come from manufacturer and retailer documentation, not from a
  measurement study. The direction is not in doubt; the magnitude is undocumented.
- ExRx and Starting Strength pages returned HTTP 403 to direct fetching; their content was read via search
  indexing of those first-party pages rather than a direct retrieval. StrongLifts, the r/Fitness wiki,
  OpenPowerlifting, and the ACSM position stand PDF were all fetched directly.

---

## Sources

**Strength standards, scaling and licensing**
- [ExRx.net — Weightlifting Performance Standards](https://exrx.net/Testing/WeightLifting/StrengthStandards) · [Terms of Use](https://exrx.net/Notes/Legal) · [License Agreement](https://exrx.net/Notes/License)
- [Strength Level — About](https://strengthlevel.com/about) · [Terms & Conditions](https://strengthlevel.com/terms-and-conditions)
- [OpenPowerlifting — FAQ](https://www.openpowerlifting.org/faq) · [opl-data README (licence statement)](https://gitlab.com/openpowerlifting/opl-data/-/raw/main/README.md) · [repo LICENSE (AGPLv3+)](https://gitlab.com/openpowerlifting/opl-data/-/raw/main/LICENSE)
- [Symmetric Strength — Standards](https://symmetricstrength.com/standards)
- "An allometric large-scale analysis of the observed performance limits and sex differences in elite powerlifting", *Frontiers in Physiology*, 2026 — [full text](https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2026.1847605/full) (n = 457,471)
- [NHANES grip strength estimates (CDC)](https://stacks.cdc.gov/view/cdc/90691/cdc_90691_DS1.pdf)

**Loading guidance**
- ACSM Position Stand, "Progression Models in Resistance Training for Healthy Adults", *Med Sci Sports Exerc* 41(3):687–708, 2009 — [PDF](https://tourniquets.org/wp-content/uploads/PDFs/ACSM-Progression-models-in-resistance-training-for-healthy-adults-2009.pdf)
- [NSCA 1RM testing protocol (warm-up increments)](http://www.intervalathlete.com/blog/1-repetition-maximum-1rm-testing-protocol-nsca)

**Novice program protocols**
- [StrongLifts 5×5 — Workout Program](https://stronglifts.com/stronglifts-5x5/workout-program/)
- [r/Fitness Basic Beginner Routine](https://thefitness.wiki/routines/r-fitness-basic-beginner-routine/)
- [Starting Strength — Your First Two Weeks of Strength Training](https://startingstrength.com/article/your-first-two-weeks-of-strength-training-what-to-expect) · [Starting Strength Training Programs](https://startingstrength.com/article/programs)

**Anthropometry and strength prediction**
- Ferland P-M, Laurier A, Comtois AS. "Relationships Between Anthropometry and Maximal Strength in Male Classic Powerlifters." *International Journal of Exercise Science* 13(4): 1512–1531, 2020 — [PDF](https://pdfs.semanticscholar.org/efdb/fb5a92206c83b97d147686ab1d4f31062aa4.pdf) · [PubMed](https://pubmed.ncbi.nlm.nih.gov/33414873/)
- Scanlan JM, Ballmann KL, Mayhew JL, Lantz CD. "Anthropometric dimensions to predict 1-RM bench press in untrained females." *J Sports Med Phys Fitness* 39(1):54–60, 1999 — [PubMed](https://pubmed.ncbi.nlm.nih.gov/10230170/)
- Mayhew JL et al. "Relationships of structural dimensions to bench press strength in college males." *J Sports Med Phys Fitness*, 1991 — [PubMed](https://pubmed.ncbi.nlm.nih.gov/1753717/)
- ["Sex differences in upper and lower strength and their association with body composition among university students"](https://pubmed.ncbi.nlm.nih.gov/39501696/)

**Measurement reliability and safety**
- "Test–Retest Reliability of the One-Repetition Maximum (1RM) Strength Assessment: a Systematic Review", *Sports Medicine – Open*, 2020 — [PMC7367986](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7367986/) · [PubMed](https://pubmed.ncbi.nlm.nih.gov/32681399/)
- "Which resistance training is safest to practice? A systematic review", *J Orthop Surg Res* 18:296, 2023 — [PMC10099898](https://pmc.ncbi.nlm.nih.gov/articles/PMC10099898/)
- ["Classification and Determination Model of Resistance Training Status"](https://www.ovid.com/jnls/nsca-scj/abstract/10.1519/ssc.0000000000000627~classification-and-determination-model-of-resistance)

**Equipment**
- [Understanding Cable Machine Movement Ratio](https://gymequipment.co.uk/blog/understanding-cable-machine-ratios)
- [REP Fitness — What Do Cable Ratios Mean](https://repfitness.com/blogs/guides/what-do-cable-ratios-mean-and-why-is-that-important)
