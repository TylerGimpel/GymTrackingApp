# Splits and Volume Landmarks — Research Inputs for the Routine Generator

> Research note for [issue #5](https://github.com/TylerGimpel/GymTrackingApp/issues/5).
> **Scope:** the training-science *inputs* a routine generator consumes. This document does **not**
> design the generation algorithm (issue #13 / #10).
>
> **Written:** 2026-09-14. Filed under `docs/research/`, alongside the other wayfinder research
> notes for this milestone.

---

## 0. How to read this document

Three kinds of statement appear below, and the generator should treat them very differently:

| Marker | Meaning |
| --- | --- |
| **[Evidence]** | Supported by meta-analysis, position stand, or multiple RCTs. Numbers are still uncertainty ranges, not constants. |
| **[Convention]** | Widely used practitioner framework with little or no direct experimental support. Defensible as a default; not defensible as a claim. |
| **[Gap]** | The literature does not answer this. The generator must make a design choice and the choice should be recorded as an ADR, not presented to the user as science. |

Nearly everything about **split taxonomy**, **day-count validity**, and **the weekly→cycle
translation** is **[Convention]** or **[Gap]**. Nearly everything about **volume**, **load**,
and **frequency** is **[Evidence]** with wide and genuinely contested intervals.

---

## 1. The structural problem: weekly landmarks in an N-day cycle

This app's routines are a repeating **N-day cycle**, deliberately not anchored to days of the week.
Essentially all published volume guidance is expressed **per week**. This section is the most
important part of the document because the mismatch is real, unstudied, and the generator cannot
avoid it.

### 1.1 The literature knows the week is arbitrary and says so

The most recent and most granular dose-response meta-analysis states the limitation explicitly:

> "volume and frequency were quantified on a weekly time scale, but any choice of time period for
> quantifying volume and frequency is arbitrary."
> — Pelland et al. (2025), *Sports Medicine*, Limitations and Considerations

The same group's follow-up paper exists precisely because of that limitation: it re-runs the
dose-response on **per-session** volume instead of per-week, and explicitly reconciles the two by
multiplication (Remmert et al., 2025).

**Implication:** the week is a reporting unit, not a biological one. The physiologically motivated
units are (a) the **per-session dose** and (b) the **interval between sessions that hit the same
muscle**. Both are natively expressible in an N-day cycle. "Sets per week" is a derived quantity.

### 1.2 The three quantities the generator must keep distinct

Weekly literature targets collapse three separate things that an N-day cycle pulls apart:

1. **Sets per session per muscle** — directly evidenced, and *cycle-length independent*.
2. **Sessions hitting a muscle per cycle** — a structural property of the split and N.
3. **Cycle length in calendar days** — the only thing that converts (1)×(2) into "per week".

The conversion, if the app knows cycle length in calendar days:

```
sessions_per_week(muscle) = sessions_per_cycle(muscle) × 7 / cycle_length_days
weekly_sets(muscle)       = sets_per_cycle(muscle)     × 7 / cycle_length_days
```

### 1.3 The input ambiguity that has to be resolved before anything else — **[Gap]**

"Day count" as a user input is ambiguous in a way that changes the volume math by a factor of
**~2.3x**:

- If a "5-day cycle" means *5 training days plus unspecified rest*, cycle length in calendar days
  is unknown, and weekly volume is **not computable**.
- If a "5-day cycle" means *5 calendar days, all of them training days*, the user trains 7/5 = 1.4
  cycles per week. A 3-day PPL cycle run back-to-back gives **2.33 sessions per muscle per week**,
  not 1 — the same structure that, week-anchored, would give 1x/week.

Worked contrast, same split, same per-session dose (9 sets/muscle/session):

| Interpretation | Sessions/muscle/week | Weekly sets/muscle |
| --- | --- | --- |
| 3-day PPL, week-anchored (Mon/Wed/Fri) | 1.0 | 9 |
| 3-day PPL, continuous 3-day cycle | 2.33 | 21 |

Both are "a 3-day PPL". One sits at the bottom of the evidenced range, the other near the top.
The generator cannot pick a volume target without disambiguating this.

**Three viable positions, none of them free:**

- **(a) Capture expected cycle duration.** Ask for, or infer, rest days / expected days-per-cycle.
  Weekly landmarks become computable. Costs an extra input the product deliberately avoided.
- **(b) Target per-cycle volume and per-session volume only.** Fully honest to the cycle model,
  fully avoids fictional weekly numbers — but severs the link to every published landmark, so the
  app can no longer claim its volumes are evidence-based.
- **(c) Assume a nominal cycle duration** (e.g. cycle length = N calendar days, or N training days
  + a fixed assumed rest cadence). Keeps the landmark link, but the assumption silently determines
  the dose. If taken, it must be an explicit, recorded assumption.

### 1.4 What *is* transferable without knowing calendar days — **[Evidence]**

Two constraints survive the translation intact, because they are per-session:

- **Per-session ceiling for hypertrophy:** the point of undetectable outcome superiority (PUOS) is
  **~11 "fractional" sets per session** for a muscle (Remmert et al., 2025). The IUSCA position
  stand independently recommends capping per-session volume at **~10 sets per muscle** and adding
  sessions rather than sets beyond that (Schoenfeld et al., 2021).
- **Per-session ceiling for strength:** PUOS **~2 "direct" sets per session** for the assessed
  exercise (Remmert et al., 2025) — i.e. strength-specific per-session volume saturates very early.

These are the cleanest cycle-native constraints available. A generator can enforce them with no
knowledge of the calendar at all.

### 1.5 Recovery interval — the other cycle-native constraint — **[Evidence, weak]**

Muscle protein synthesis is meaningfully reduced ~48 h after a resistance training bout in untrained
individuals and may return to baseline even sooner in trained individuals (cited in Pelland et al.,
2025, §4.2, refs 112–114). This is the mechanistic basis for the conventional 48 h same-muscle
spacing. It is mechanistic, not outcome, evidence — the frequency outcome literature (§4) does
**not** show a hypertrophy benefit to respecting it at equal volume.

Usable as a soft constraint on where a muscle's sessions land within the cycle; **not** usable as a
claim that violating it costs gains.

### 1.6 The divisibility rule — a cycle-specific structural hazard — **[Convention, derived]**

This one is specific to repeating cycles and does not exist in week-anchored programming.

A week-anchored 3-day/week upper/lower split self-corrects: `U L U` one week, `L U L` the next,
balanced over 14 days. A **repeating N-day cycle does not self-correct.** A 3-day cycle of
`U / L / U` repeats as `U L U | U L U | U L U …` — upper is trained twice per cycle, lower once,
**permanently**, at a 2:1 imbalance that never washes out.

**Constraint:** for a split with rotation length `R` (full body R=1, upper/lower R=2, PPL R=3,
5-way body-part split R=5), balanced coverage requires

```
N mod R == 0
```

where N is the number of training slots in the cycle. Where `N mod R != 0`, the generator must
either reject the combination, pad to the next multiple, or deliberately assign the surplus day(s)
to a prioritised muscle group and *say so*. Silently producing a permanent 2:1 imbalance is the
failure mode.

---

## 2. Split taxonomy and day counts

**Status: [Convention] throughout.** No study has compared "which split at which day count".
The closest evidence is a handful of split-vs-full-body RCTs at matched weekly volume, all of which
found **no difference** (§4.2). The practical reading is that **splits are a scheduling device for
distributing volume, not an independent training variable** — so the validity of a (split, day count)
pair is a *structural* question (does it produce balanced coverage at a feasible per-session dose?),
not an empirical one.

Rotation length `R` = number of distinct session types before the pattern repeats.

| Split | R | Session types | Balanced day counts (N mod R == 0) | Sessions/muscle/cycle |
| --- | --- | --- | --- | --- |
| Full body | 1 | FB | 1, 2, 3, 4, 5, 6, 7 | N |
| Upper / Lower | 2 | U, L | 2, 4, 6 | N/2 |
| Push / Pull | 2 | Push, Pull | 2, 4, 6 | N/2 (legs must be assigned) |
| Push / Pull / Legs | 3 | P, P, L | 3, 6 (9) | N/3 |
| Upper / Lower / Full | 3 | U, L, FB | 3, 6 | upper & lower 2N/3 each |
| 4-way body-part | 4 | e.g. Chest+Tri, Back+Bi, Legs, Shoulders+Arms | 4 | N/4 |
| 5-way body-part ("bro split") | 5 | Chest, Back, Shoulders, Arms, Legs | 5 | N/5 |

### 2.1 Where each one breaks down

**Full body.**
- *Low end (N=1–2):* per-session volume ceiling binds. At the ~10–11 sets/muscle/session cap, a
  1-day cycle can deliver at most ~10 sets/muscle/cycle. That is at the *bottom* of the evidenced
  hypertrophy range (§3) and cannot be raised by adding sets.
- *High end (N=5–6):* structurally fine, but per-muscle per-session volume must fall to ~2–3 sets
  to stay under weekly ceilings, and session length balloons because every session must touch every
  muscle group (many muscles × ≥1 exercise each × rest intervals). Practical failure, not
  physiological.
- Best supported by direct evidence at low day counts for novices (ACSM 2009: novices train the
  entire body 2–3 d/wk).

**Upper / Lower.**
- Breaks at odd N (3, 5) via the divisibility rule (§1.6). A "5-day upper/lower" is incoherent as a
  repeating cycle: it is a 3:2 permanent imbalance.
- At N=6 each half is trained 3x/cycle; per-session volume must be low (~3–4 sets/muscle) or weekly
  volume overshoots.
- ACSM 2009 explicitly pairs upper/lower splits with 4 d/wk at intermediate level.

**Push / Pull / Legs.**
- Incoherent at N=2 (cannot cover three session types).
- Incoherent at N=4, 5 by divisibility.
- At N=3, each muscle is hit once per cycle, so the entire cycle's dose for a muscle must fit in one
  session — pinned against the ~10–11 set per-session ceiling. Viable, but caps volume.
- At N=6, 2x/cycle — the most comfortable configuration: ~5–9 sets/session × 2 reaches the middle of
  the evidenced weekly range without breaching the per-session cap.
- Note push/pull/legs has a built-in imbalance risk even when balanced: front delts and triceps
  accumulate indirect volume on push day, rear delts and biceps on pull day. Whether that counts
  toward their volume depends entirely on the set-counting convention (§3.4).

**Push / Pull (no leg day).**
- Requires an explicit decision about where legs go (appended to one day, alternated, or omitted).
  The generator must not leave this implicit.

**Body-part splits (4- and 5-way).**
- Each muscle once per cycle. The whole cycle's dose for that muscle must fit in one session, so
  weekly volume is hard-capped near the per-session PUOS (~10–11 fractional sets).
- This is *defensible*: the IUSCA stand says significant hypertrophy is achievable training a muscle
  as infrequently as once per week at low-to-moderate volume. But it is the configuration least able
  to reach the upper volume ranges, and the one where the frequency-vs-volume debate bites hardest.
- Incoherent at N < R.

### 2.2 Derived feasibility constraint

Combining §1.4 and §2:

```
weekly_target(muscle) ≤ per_session_cap × sessions_per_cycle(muscle) × 7 / cycle_length_days
```

If a requested (goal, training age) volume target exceeds what a (split, N) can deliver under the
per-session cap, the combination is infeasible and something has to give: lower the target, change
the split, or add days. **[Evidence]** for the cap; **[Convention]** for how to resolve the conflict.

---

## 3. Weekly volume per muscle group

This is the most contested area in the document. Below are the actual published numbers, with the
disagreement preserved rather than averaged away.

### 3.1 The numbers on the table

| Source | Population | Recommendation (sets/muscle/week) | Counting |
| --- | --- | --- | --- |
| Pelland et al. 2025, *Sports Med* (67 studies, 2058 participants) | mixed, mean age 25 | **Minimum effective dose ≈ 4**; efficiency tiers 5–10, 11–18, 19–29, 30–42; **no clear plateau** | fractional (indirect = 0.5) |
| ACSM Position Stand 2026 (Currier et al., 137 systematic reviews, >30,000 participants) | healthy adults | **≥10 sets/wk** for hypertrophy | unspecified |
| IUSCA Position Stand 2021 (Schoenfeld et al.) | athletic | **~10 sets/wk as a general minimum**; benefits to higher volumes, esp. lagging muscles | 1:1 |
| Baz-Valle et al. 2022, *J Hum Kinet* (7 studies) | trained men, 18–35, ≥1 yr | **12–20 sets/wk** optimum; >20 better **only** for triceps | 1:1 |
| Iversen et al. 2021, *Sports Med* (time-efficient training) | time-constrained | **minimum 4 sets/wk**, up to 10+ when time allows | 1:1 |
| Spiering et al. 2021, *JSCR* (maintenance) | young adults | **1 session/wk maintains** muscle size; intensity is the variable that must be preserved | n/a |
| Renaissance Periodization "volume landmarks" (MV/MEV/MAV/MRV) | trained | MV ≈ 6 sets/wk; MEV/MAV/MRV per-muscle tables | 1:1 |
| Buckner, Moreno & Baxter 2023, *J Trainology* | trained | **disputes the dose-response itself** | n/a |

### 3.2 Pelland et al. 2025 — the most usable structure, and its caveats — **[Evidence]**

The efficiency-tier table is the closest thing to an algorithm-ready volume model. For **hypertrophy**
(smallest detectable effect size = 2.05%):

| Tier | Fractional weekly sets | Sets needed for the *next* detectable increment |
| --- | --- | --- |
| Minimum effective dose | 4 | — |
| Higher efficiency | 5–10 | ~6 more |
| Intermediate efficiency | 11–18 | ~8.5 more |
| Lower efficiency | 19–29 | ~10.75 more |
| Lowest efficiency | 30–42 | ~12.5 more |
| Unclear | 43+ | insufficient data |

For **strength** (SDES = 3.96%): minimum effective dose = **1** fractional weekly set; detectable
increments continue only to **~4** fractional weekly sets, "but not beyond this point."

Marginal slopes: hypertrophy **+0.24% per additional set** (95% CrI 0.15–0.33) at the sample mean of
12.25 weekly sets; strength **+0.21%** (95% CrI 0.16–0.26).

**Caveats the authors themselves raise, all of which matter to a generator:**
- "No clear plateau" for hypertrophy — but **few studies exceed ~25 fractional weekly sets**, so the
  upper tiers are extrapolation.
- Fixed effects explained only ~22–26% of variance. Multiple functional forms (plateau, inverted-U)
  remain compatible with the data.
- Cohorts were 79.1% male, mean age 25.2, adults >70 excluded. Mean intervention **10.4 weeks**.
- The tiers measure *detectability*, not *worthwhileness*: sets beyond a tier boundary still produce
  gains, just gains smaller than the SDES.

### 3.3 The live disagreement — report it, do not resolve it

**The dose-response camp** (Schoenfeld, Pelland, Zourdos, Baz-Valle, ACSM 2026) holds that weekly
set volume is a primary driver of hypertrophy with diminishing but positive returns well past 20
sets/week.

**The sceptics** (Buckner, Moreno & Baxter 2023) argue the supporting studies report muscle-thickness
changes "of a magnitude not typically observed" elsewhere in the literature — e.g. 0.6–0.72 cm
quadriceps thickness change where 0.1–0.25 cm is typical — that several trained-participant studies
show **no** difference between lower and higher volumes, and that limitations are applied
asymmetrically (scrutinising null results harder than positive ones). Their conclusion:

> "While some threshold of training volume is likely necessary for muscle growth, the current
> recommendations may exaggerate its importance."

**A third, quieter disagreement** is about *frequency-vs-per-session distribution* (§4), and a fourth
about *counting* (§3.4). A generator that hard-codes any single set of numbers is picking a side in
a live argument. Framing volume as a configurable default with a stated provenance is the honest
engineering response.

### 3.4 Set counting is unresolved and swings every number by ~30% — **[Gap]**

Does a bench press set count as 1 set of triceps, 0.5, or 0?

- **Pelland et al. 2025** tested all three ("total" = 1, "fractional" = 0.5, "direct" = 0) and found
  **strong to very strong** Bayesian support for **fractional**. Their landmark numbers are therefore
  *fractional* numbers.
- **Schoenfeld et al. 2019** (*Sports* 7:177), reviewing the same question, recommends practitioners
  "view set-volume prescription on a 1:1 basis" — precisely *because* the existing landmark
  literature assumes 1:1, so switching conventions would decouple prescriptions from the evidence
  they cite.
- Pelland et al. themselves call fractional "a heuristic to improve the accuracy of dose-response
  modeling, rather than a definitive standard for practical application."

**Consequence for the generator:** the same routine yields materially different volume numbers under
the two conventions, and the two most-cited target ranges (Pelland's tiers vs Baz-Valle's 12–20) are
expressed in *different units*. The counting convention is a first-class design decision that must
be fixed before any volume target is meaningful, and it must be applied consistently to whichever
landmark table is chosen.

### 3.5 Training age — **[Evidence, weak]**

- Pelland et al. adjusted all models for training status but flag their moderator analyses as
  underpowered and hypothesis-generating only.
- Lopez et al. 2021 (network meta-analysis, *MSSE*): untrained participants exhibit greater
  hypertrophy overall; those with prior training experience gain more from **more sessions**.
- Baz-Valle's 12–20 range is specifically for **young trained men (≥1 yr experience)** and should not
  be applied to novices.
- ACSM 2009 encodes training status as novice / intermediate / advanced with distinct set and
  frequency prescriptions (§6).
- IUSCA: novices achieve robust gains **without** training close to failure; the need for high effort
  rises with experience.

**There is no validated per-muscle, per-training-age volume table.** Per-muscle landmark tables
(e.g. "delts need more than triceps") are **[Convention]** — the RP-style MV/MEV/MAV/MRV framework
is explicitly presented by its own publisher as "starting points, not gospel," assessed subjectively
via pump, soreness and perceived challenge, and is not accompanied by supporting studies for the
specific numbers.

### 3.6 Volume ramping — **[Convention]**

IUSCA suggests limiting incremental set increases to **≤20% of previous volume** within a ~4-week
cycle, while noting "empirical evidence is lacking." Usable as a progression guardrail; not a finding.

---

## 4. Frequency

### 4.1 The headline result — **[Evidence]**

Pelland et al. 2025, on a volume-adjusted basis:

- **Hypertrophy:** posterior probability of a positive slope **91.3%** — compatible with negligible
  effects. Contrast-based and direct-effects-only models showed **no** frequency effect. Consistent
  with Schoenfeld et al. 2019's volume-equated meta-analysis (ES = 0.07, 95% CI −0.08 to 0.21).
- **Strength:** posterior probability **100%**, with diminishing returns. The large step is 1→2
  sessions/week (ES 12.72% → 17.32%); "beyond this point, accelerating diminishing returns occur."

**Reading:** for hypertrophy, frequency is a *distribution mechanism* for volume, not an independent
driver. For strength, going from 1 to 2 sessions is worth something; going from 2 to 5 much less.

### 4.2 The volume-equated split/frequency RCTs all agree — **[Evidence]**

| Study | Comparison | Result |
| --- | --- | --- |
| Evangelista et al. 2021, *Einstein (São Paulo)* — 67 untrained men, 8 wk, 16 sets/muscle/wk both arms | Split 2x/wk (8 sets/session) vs full-body 4x/wk (4 sets/session) | No difference in strength or muscle thickness |
| Saric et al. 2019, *JSCR* — 27 trained men, 6 wk | 3x/wk vs 6x/wk, volume equated | No difference in strength or muscle thickness |
| Pedersen et al. 2022, *BMC Sports Sci Med Rehabil* — 50 untrained women, 12 wk | Full-body 2x/wk vs upper/lower split | No difference in strength, explosive strength, or muscle mass |
| Zaroni et al. 2019, *JSCR* — 18 trained men | 1x/wk body-part split vs 5x/wk whole-body | **Greater** muscle thickness for elbow flexors and vastus lateralis with 5x/wk; no difference for triceps or in strength |

Zaroni is the notable dissent, and is also the study whose per-session volumes differed most — which
is exactly the confound the per-session literature (§1.4) addresses.

### 4.3 Where position stands disagree with each other — **[Evidence, contested]**

- **ACSM 2026:** train "all major muscle groups **at least twice a week**."
- **IUSCA 2021:** "Significant hypertrophy can be achieved when training a muscle group as
  infrequently as **once per week** in lower to moderate volume protocols; there does not seem to be
  a hypertrophic benefit to greater weekly per-muscle training frequencies **provided set volume is
  equated**."

These are reconcilable — ACSM's ≥2x is a public-health-simple heuristic that also happens to make
adequate weekly volume easier to accumulate under the per-session cap — but they are not the same
recommendation, and a generator quoting "twice a week" as settled science would be overstating.

### 4.4 The constraint frequency actually imposes — **[Evidence]**

The IUSCA stand states it directly: cap per-session volume at ~10 sets per muscle and **increase
weekly frequency to distribute additional volume**. Combined with §1.4:

```
sessions_per_cycle(muscle) ≥ ceil( target_sets_per_cycle(muscle) / per_session_cap )
```

That is the single most actionable frequency rule available, and it is cycle-native.

---

## 5. Exercise selection

### 5.1 Ordering — **[Evidence]**

- **ACSM 2009** (all training statuses): large muscle group before small; **multi-joint before
  single-joint**; higher-intensity before lower-intensity; *or* rotation of upper/lower body or
  agonist/antagonist pairs.
- **Nunes et al. 2021** (*Eur J Sport Sci*, systematic review + meta-analysis): strength gains are
  **largest in whichever exercises are performed first in the session** — multi-joint-first favours
  multi-joint strength, single-joint-first favours single-joint strength. **No significant effect of
  order on hypertrophy.**

**Reading:** compound-before-isolation is well supported *for strength*, and is best understood as
"put first what you most want to improve." For a hypertrophy goal, ordering is a fatigue-management
and preference matter, not a driver of outcome.

### 5.2 How many exercises per muscle per session — **[Gap]**

There is no dose-response evidence on exercise *count* per muscle per session, only on **set** count.
What exists:

- IUSCA: include "a variety of exercises that work muscles in different planes and angles of pull,"
  combine multi- and single-joint, and where applicable favour exercises loading muscles at **long
  lengths**.
- Exercise-variation research (Kassiano et al.; Baz-Valle et al. variation review): **systematic**
  variation may aid regional hypertrophy and dynamic strength, while **excessive or random** rotation
  may compromise gains — exercises providing a redundant stimulus add nothing.
- Iversen et al. 2021 minimum-coverage floor: at least one **leg press pattern** (e.g. squat), one
  **upper-body pull** (e.g. pull-up), one **upper-body push** (e.g. bench press).

So: "1–3 exercises per muscle per session" is a **[Convention]** the generator may adopt, but it
cannot be cited to a source. What *can* be cited is the constraint on redundancy (don't select two
exercises with the same stimulus) and the set cap that bounds it.

### 5.3 Antagonist pairing — **[Evidence, for efficiency only]**

- Robbins et al. 2010 (*JSCR*, brief review): agonist–antagonist paired sets are an efficacious and
  efficient means of developing strength and power; alternating agonist/antagonist does not degrade
  acute agonist performance.
- Iversen et al. 2021: supersets, drop sets and rest-pause "roughly halve training time compared to
  traditional training, while maintaining training volume," and favour hypertrophy over pure strength.

Antagonist pairing is a **session-duration lever**, not a hypertrophy lever. Relevant if the
generator ever optimises for session length; not a reason to prefer push/pull structures on
physiological grounds.

### 5.4 Equipment constraints — **[Evidence]**

Equipment turns out to constrain *which exercises are available* far more than *what results are
possible*:

- **Free weights vs machines** (Haugen et al. 2023, *BMC Sports Sci Med Rehabil*, 13 studies,
  n=1016): when each modality is tested in its own test, **no difference** in hypertrophy
  (SMD −0.055, p=0.751), dynamic strength (p=0.387), isometric strength (p=0.660) or CMJ (p=0.290).
  Free-weight training was superior **on free-weight tests** (SMD −0.210, p=0.023) and machines
  trended superior on machine tests (p=0.064). Conclusion: "strength changes are specific to the
  training modality, and the choice between free-weights and machines are down to individual
  preferences and goals."
- **Elastic bands vs conventional** (Lopes et al. 2019, *SAGE Open Med*, 8 studies): no superiority
  either way for upper- or lower-limb strength.
- **Progressive bodyweight vs barbell squat** (Nature *Sci Rep* 2023, sedentary young women): similar
  increases in isokinetic peak torque and muscle thickness when bodyweight difficulty was
  progressed.
- **ACSM 2026** explicitly endorses "elastic bands, bodyweight exercises, and home-based routines."
- **ACSM 2009:** free-weight *and* machine exercises for novice-to-intermediate; emphasis on free
  weights for advanced strength training, with machines complementing.

**Algorithmic reading:** equipment availability is a **filter on the exercise pool plus a constraint
on achievable load/rep combinations**, not a modifier on volume targets. The one real interaction is
with *rep preference*: a "low reps / heavy" preference is close to unsatisfiable with bands or
bodyweight for most muscle groups, and needs an explicit conflict rule (§6.4).

---

## 6. How goal and rep preference change everything above

### 6.1 Load / rep ranges by goal

| Goal | Load & reps | Source |
| --- | --- | --- |
| **Strength** | ≥80% 1RM, 2–3 sets/exercise, full ROM, ≥2 sessions/wk | ACSM 2026 |
| **Strength** | 60–70% 1RM × 8–12 reps (novice–intermediate); cycling 80–100% 1RM (advanced) | ACSM 2009 |
| **Hypertrophy** | 70–85% 1RM × 8–12 reps, 1–3 sets/exercise (novice–intermediate); 70–100% 1RM × 1–12 reps, 3–6 sets, majority at 6–12 RM (advanced) | ACSM 2009 |
| **Hypertrophy** | "similar whole muscle growth … across a wide spectrum of loading ranges ≥ ~30% 1RM" | Schoenfeld et al. 2021, *Sports* 9:32 |
| **Hypertrophy** | comparable hypertrophy across loading zones; prefer moderate loads for the majority of sets; some benefit to mixing ranges | IUSCA 2021 |
| **Local endurance** | 10–15 reps (novice–intermediate); 10–25+ reps (advanced), higher total volume at lighter loads | ACSM 2009 |
| **Local endurance** | evidence for a rep-range-specific endurance effect is "rather weak and seems more relevant to the lower body musculature" | Schoenfeld et al. 2021 |
| **Power** | 30–70% 1RM, explosive intent, 1–3 sets × 3–6 reps **not to failure** | ACSM 2026 / 2009 |
| **Time-efficient / general** | 6–15 RM range; 15–40 reps acceptable **if taken to volitional failure** | Iversen et al. 2021 |

**The load result that matters most for a rep-preference feature:** hypertrophy is largely
**load-independent above ~30% 1RM**, whereas 1RM strength is **not** — heavy loads are superior for
1RM (Lopez et al. 2021: high- and moderate-load superior to low-load, SMD 0.60–0.63 and 0.34–0.35;
high vs moderate non-significant, SMD 0.26–0.28, p=0.068). Note the specificity caveat: the
heavy-load strength advantage "dissipates when testing is carried out on a modality different than
that used in the study" (Schoenfeld et al. 2021).

### 6.2 Proximity to failure by goal — **[Evidence]**

Robinson et al. 2024 (*Sports Med* 54:2209–2231), meta-regression on estimated repetitions in reserve:

- **Hypertrophy improves as sets are terminated closer to failure**; the slope flattens past ~2 RIR.
- **Strength gains are similar across a wide range of RIR** — closer to failure was, if anything,
  marginally worse.

Layered with IUSCA: novices achieve robust gains without close-to-failure training; the need rises
with experience; highly trained lifters may benefit from *some* sets to failure, best confined to the
last set, and preferentially on single-joint / machine exercises to protect the stimulus-to-fatigue
ratio. Older trainees should use failure more sparingly.

**And critically:** low-load training only matches high-load hypertrophy *when effort is high*
(Schoenfeld et al. 2021). A high-rep preference therefore carries an implicit effort requirement that
a low-rep preference does not. If the app lets users pick high reps without conveying proximity to
failure, the prescription is not equivalent to the evidence it rests on.

### 6.3 Rest intervals by goal and exercise type — **[Evidence]**

| Context | Rest | Source |
| --- | --- | --- |
| Multi-joint / core exercises, heavy load | **≥2 min**, 2–3 min for heavy loading | IUSCA 2021; ACSM 2009 |
| Single-joint and machine exercises | **60–90 s** | IUSCA 2021 |
| Assistance exercises | 1–2 min | ACSM 2009 |
| Untrained | 1–2 min | Iversen et al. 2021 |
| Trained | **≥2 min** | Iversen et al. 2021; Grgic et al. |
| Local endurance, 15–20+ reps | 1–2 min | ACSM 2009 |
| Local endurance, 10–15 reps | **<1 min** | ACSM 2009 |
| Advanced strength, 1–6 RM | **≥3 min** | ACSM 2009 |

Grgic et al.'s review: both short and long rest are viable for untrained individuals; **trained**
individuals may benefit from longer rest, plausibly by preserving volume-load across multiple sets.
A 2024 Bayesian meta-analysis found the effect on arm and whole-body hypertrophy in untrained people
negligible, with a slight benefit of longer rest for quadriceps.

Rest interval is the main determinant of **estimated session duration**, which in turn bounds how
many sets can be scheduled per session — the practical ceiling that usually binds before the
physiological one.

### 6.4 Interaction matrix — what each user input actually modifies

| Input | Modifies | Does **not** modify |
| --- | --- | --- |
| **Goal = strength** | Load (≥80% 1RM), reps (1–6), rest (≥3 min), *lower* per-muscle volume needed (MED 1, detectable increments to ~4 fractional sets/wk), higher frequency helps, multi-joint + free-weight emphasis, exercise **specificity** to the lift being improved, order (put the target lift first) | — |
| **Goal = hypertrophy** | Volume is the primary lever (≥10 sets/wk, tiers to 30+), per-session cap ~10–11, proximity to failure matters, wide rep latitude, exercise variety across planes/long lengths | Frequency (no independent effect at equal volume); exercise order (no effect) |
| **Goal = endurance** | Reps 15+, rest <1–2 min, higher total volume at light load | Evidence base is weakest here — treat as least confident |
| **Rep preference = low (1–6)** | Requires loadable heavy equipment; longer rest → fewer sets per unit time; favours multi-joint; fewer sets needed for strength but **more** sets needed to reach equivalent hypertrophy volume within a time budget | Hypertrophy potential per set (load-independent ≥~30% 1RM) |
| **Rep preference = high (15+)** | Compatible with bands/bodyweight/minimal equipment; shorter rest; **requires proximity to failure** to match hypertrophy of heavier loads; inferior for 1RM strength | — |
| **Equipment** | Exercise pool; achievable load ranges; feasibility of low-rep preference; free-weight vs machine only matters for *test specificity*, not hypertrophy | Volume targets; frequency targets |
| **Day count + split** | Sessions/muscle/cycle; per-session set budget; feasibility of the volume target | Total volume *target* (which is goal- and training-age-derived) |

### 6.5 The conflict cases the generator will hit

These combinations are over-constrained and need explicit resolution rules — **[Gap]**, no literature
answers them:

1. **Low-rep preference + bands/bodyweight only.** Load ceiling makes 1–6 RM unreachable for most
   patterns.
2. **High volume target + low day count + body-part split.** Per-session cap binds; target
   unreachable.
3. **Strength goal + high-rep preference.** Directly opposed by the load evidence (§6.1).
4. **Odd day count + even-rotation split.** Divisibility rule (§1.6).
5. **Hypertrophy goal + high-rep preference + novice.** High reps need high effort; novices are
   advised away from close-to-failure training (IUSCA). Internally tense.

---

## 7. Where the evidence is weakest

Ordered by how much damage a wrong assumption does to this app specifically.

1. **Weekly→cycle translation (§1).** Literally unstudied. No trial has manipulated cycle length
   independently of weekly frequency. The only support for treating the week as arbitrary is the
   authors' own admission that it is. Everything downstream inherits this uncertainty.
2. **Split taxonomy × day count (§2).** No direct evidence. The validity of any (split, N) pair is a
   structural argument, not an empirical one. The volume-equated split RCTs suggest the choice may
   simply not matter much for outcomes.
3. **Set counting convention (§3.4).** Unresolved, and it changes every volume number by roughly a
   third. The two leading target ranges are stated in incompatible units.
4. **The upper end of the volume curve (§3.2).** Few studies above ~25 fractional weekly sets; the
   19–29 and 30–42 tiers are extrapolation. Buckner et al. dispute the dose-response outright on
   grounds of implausible effect magnitudes and asymmetric scrutiny.
5. **Per-muscle volume differentiation.** No validated table. Baz-Valle found triceps the lone muscle
   benefiting from >20 sets/wk — a single finding from 6 studies, not a general principle. Per-muscle
   MEV/MRV tables are practitioner estimates.
6. **Training-age modifiers on volume.** Moderator analyses are explicitly underpowered and
   hypothesis-generating.
7. **Exercises per muscle per session.** No dose-response data at all; only sets have been studied.
8. **Local muscular endurance prescriptions.** Authors describe the rep-range-specific endurance
   effect as "rather weak" and possibly lower-body-specific.
9. **Rep preference as an independent variable.** Never studied as a user-chosen constraint; all
   inference here is assembled from load and effort literature.
10. **Population generalisability.** Pelland: 79.1% male, mean age 25.2, adults >70 excluded, mean
    intervention 10.4 weeks. Baz-Valle: young trained men only. Nothing here speaks confidently to
    older users, to women specifically, or to timescales beyond ~3 months.
11. **Frequency position stands disagree** (≥2x/wk vs 1x/wk sufficient) even among current
    authorities (§4.3).

---

## 8. Summary of constraints an algorithm could act on

Grouped by confidence. Detail and provenance in the sections referenced.

**Well-supported (act on these):**

- Per-session cap ≈ **10–11 sets per muscle per session** for hypertrophy; add sessions rather than
  sets beyond it. (§1.4, §4.4)
- Per-session strength-specific volume saturates at ≈ **2 direct sets per session** for a given lift.
  (§1.4)
- Hypertrophy weekly floor ≈ **10 sets/muscle**; minimum effective dose as low as **4**; upper
  evidenced range **12–20+**, with genuine disagreement above 20. (§3.1, §3.2)
- Strength weekly volume per muscle is **much lower** — detectable increments only to ~4 sets/week —
  but responds to **load (≥80% 1RM)** and, mildly, to frequency. (§3.2, §4.1, §6.1)
- Hypertrophy is **load-independent above ~30% 1RM** provided effort is high; 1RM strength is not.
  (§6.1)
- **Proximity to failure** drives hypertrophy (flattening past ~2 RIR) and is near-irrelevant for
  strength. (§6.2)
- **Frequency has no independent hypertrophy effect** at equal volume; it is a distribution mechanism
  bounded by the per-session cap. It does help strength, mostly 1→2 sessions. (§4.1)
- **Multi-joint before single-joint** matters for strength in the exercises placed first; not for
  hypertrophy. (§5.1)
- **Equipment does not change achievable hypertrophy** (machines ≈ free weights ≈ bands ≈ progressive
  bodyweight); it filters the exercise pool and constrains reachable load ranges. Strength transfer
  is modality-specific. (§5.4)
- Rest: **≥2 min multi-joint**, **60–90 s single-joint/machine**, ≥3 min for heavy low-rep work,
  <1–2 min for endurance work. Drives session duration, which is usually the binding constraint.
  (§6.3)
- Minimum exercise coverage: at least one leg press pattern, one upper push, one upper pull. (§5.2)
- Maintenance floor: ~1 session/week preserves size in young adults if intensity is preserved. (§3.1)

**Structural constraints derived here (defensible, not published):**

- `N mod R == 0` for balanced coverage in a repeating cycle. (§1.6)
- `weekly_target ≤ per_session_cap × sessions_per_cycle × 7 / cycle_length_days`. (§2.2)
- `sessions_per_cycle(muscle) ≥ ceil(target_sets_per_cycle / per_session_cap)`. (§4.4)
- Same-muscle spacing ≈48 h as a soft preference on session placement within the cycle, on
  mechanistic grounds only. (§1.5)

**Decisions the literature will not make for you:**

- Interpretation of "day count" (training days vs calendar days). Blocks all weekly math. (§1.3)
- Set-counting convention (fractional vs 1:1). Determines which landmark table is even applicable.
  (§3.4)
- Resolution rules for over-constrained inputs. (§6.5)
- Exercises-per-muscle-per-session. (§5.2)
- Whether to expose weekly-equivalent volume to the user at all, given that the app's own model does
  not contain a week. (§1.3)

---

## Sources

Primary literature, position stands, and — clearly labelled — practitioner frameworks.

**Volume dose-response**
- Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. The Resistance Training Dose Response:
  Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and
  Strength Gains. *Sports Medicine*, 2025. https://doi.org/10.1007/s40279-025-02344-w
  (preprint: https://sportrxiv.org/index.php/server/preprint/view/460)
- Remmert JF, Pelland JC, Robinson ZP, Hinson SR, Zourdos MC. Is There Too Much of a Good Thing?
  Meta-Regressions of the Effect of Per-Session Volume on Hypertrophy and Strength. *SportRxiv*,
  2025. https://sportrxiv.org/index.php/server/preprint/view/537
- Baz-Valle E, Balsalobre-Fernández C, Alix-Fages C, Santos-Concejero J. A Systematic Review of The
  Effects of Different Resistance Training Volumes on Muscle Hypertrophy. *J Hum Kinet*.
  2022;81:199–210. https://doi.org/10.2478/hukin-2022-0017
- Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training
  volume and increases in muscle mass. *J Sports Sci*. 2017;35(11):1073–82.
- **Dissent:** Buckner SL, Moreno EN, Baxter HT. The dose-response relationship between resistance
  training volume and muscle hypertrophy: There are still doubts. *J Trainology*. 2023;12(2):29–36.
  https://www.jstage.jst.go.jp/article/trainology/12/2/12_29/_article
- **Counting convention:** Schoenfeld BJ, Grgic J, Haun C, Itagaki T, Helms ER. Calculating
  Set-Volume for the Limb Muscles with the Performance of Multi-Joint Exercises. *Sports*.
  2019;7(7):177. https://doi.org/10.3390/sports7070177

**Position stands**
- Currier BS, D'Souza AC, Singh MAF, Lowisz CV, Rawson ES, Schoenfeld BJ, Smith-Ryan AE, Steen JP,
  Thomas GA, Triplett NT, Washington TA, Werner TJ, Phillips SM. ACSM Position Stand: Resistance
  Training Prescription for Muscle Function, Hypertrophy, and Physical Performance in Healthy Adults:
  An Overview of Reviews. *Med Sci Sports Exerc*. 2026. (137 systematic reviews, >30,000 participants)
- American College of Sports Medicine. Position Stand: Progression Models in Resistance Training for
  Healthy Adults. *Med Sci Sports Exerc*. 2009;41(3):687–708. (evidence-graded recommendation tables)
- Schoenfeld BJ, Fisher JP, Grgic J, Haun CT, Helms ER, Phillips SM, Steele J, Vigotsky AD.
  Resistance Training Recommendations to Maximize Muscle Hypertrophy in an Athletic Population:
  Position Stand of the IUSCA. *Int J Strength Cond*. 2021.
  https://doi.org/10.47206/ijsc.v1i1.81

**Frequency and splits**
- Schoenfeld BJ, Grgic J, Krieger J. How many times per week should a muscle be trained to maximize
  muscle hypertrophy? *J Sports Sci*. 2019.
- Saric J, Lisica D, Orlic I, Grgic J, Krieger JW, Vuk S, Schoenfeld BJ. Resistance Training
  Frequencies of 3 and 6 Times Per Week Produce Similar Muscular Adaptations in Resistance-Trained
  Men. *J Strength Cond Res*. 2019;33:S122–S129.
- Zaroni RS, Brigatto FA, et al. High Resistance-Training Frequency Enhances Muscle Thickness in
  Resistance-Trained Men. *J Strength Cond Res*. 2019;33(Suppl 1):S140–S151.
- Evangelista AL, Braz TV, La Scala Teixeira CV, et al. Split or full-body workout routine: which is
  best to increase muscle strength and hypertrophy? *Einstein (São Paulo)*. 2021;19:eAO5781.
- Pedersen H, Fimland MS, Schoenfeld BJ, Iversen VM, Cumming KT, Jensen S, Saeterbakken AH,
  Andersen V. A randomized trial on the efficacy of split-body versus full-body resistance training
  in non-resistance trained women. *BMC Sports Sci Med Rehabil*. 2022;14:87.
  https://doi.org/10.1186/s13102-022-00481-7

**Load, effort, rest, ordering**
- Lopez P, Radaelli R, Taaffe DR, Newton RU, Galvão DA, Trajano GS, Teodoro JL, Kraemer WJ,
  Häkkinen K, Pinto RS. Resistance Training Load Effects on Muscle Hypertrophy and Strength Gain:
  Systematic Review and Network Meta-analysis. *Med Sci Sports Exerc*. 2021;53(6):1206–1216.
- Schoenfeld BJ, Grgic J, Van Every DW, Plotkin DL. Loading Recommendations for Muscle Strength,
  Hypertrophy, and Local Endurance: A Re-Examination of the Repetition Continuum. *Sports*.
  2021;9(2):32.
- Robinson ZP, Pelland JC, Remmert JF, Refalo MC, Jukic I, Steele J, Zourdos MC. Exploring the
  Dose-Response Relationship Between Estimated Resistance Training Proximity to Failure, Strength
  Gain, and Muscle Hypertrophy: A Series of Meta-Regressions. *Sports Med*. 2024;54(9):2209–2231.
- Nunes JP, Grgic J, Cunha PM, et al. What influence does resistance exercise order have on muscular
  strength gains and muscle hypertrophy? A systematic review and meta-analysis. *Eur J Sport Sci*.
  2021;21(2):149–157.
- Grgic J, Schoenfeld BJ, et al. Effects of Rest Interval Duration in Resistance Training on Measures
  of Muscular Strength: A Systematic Review. *Sports Med*.
- Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest
  interval duration on muscle hypertrophy. *Front Sports Act Living*. 2024;6:1429789.
- Robbins DW, Young WB, Behm DG. Agonist-antagonist paired set resistance training: a brief review.
  *J Strength Cond Res*. 2010.

**Equipment and time-efficiency**
- Haugen ME, Vårvik FT, Larsen S, Haugen AS, van den Tillaar R, Bjørnsen T. Effect of free-weight vs.
  machine-based strength training on maximal strength, hypertrophy and jump performance. *BMC Sports
  Sci Med Rehabil*. 2023;15:103. https://doi.org/10.1186/s13102-023-00713-4
- Lopes JSS, Machado AF, Micheletti JK, de Almeida AC, Cavina AP, Pastre CM. Effects of training with
  elastic resistance versus conventional resistance on muscular strength: A systematic review and
  meta-analysis. *SAGE Open Med*. 2019.
- Iversen VM, Norum M, Schoenfeld BJ, Fimland MS. No Time to Lift? Designing Time-Efficient Training
  Programs for Strength and Hypertrophy: A Narrative Review. *Sports Med*. 2021;51(10):2079–2095.
- Spiering BA, Mujika I, Sharp MA, Foulis SA. Maintaining Physical Performance: The Minimal Dose of
  Exercise Needed to Preserve Endurance and Strength Over Time. *J Strength Cond Res*.
  2021;35(5):1449–1458.
- Effects of progressive body-weight versus barbell back squat training on strength, hypertrophy and
  body fat among sedentary young women. *Sci Rep*. 2023;13:13910.

**Practitioner frameworks — [Convention], not peer-reviewed**
- Renaissance Periodization. Training Volume Landmarks for Muscle Growth (MV / MEV / MAV / MRV).
  https://rpstrength.com/blogs/articles/training-volume-landmarks-muscle-growth
  The publisher describes the numbers as "starting points, not gospel," assessed via subjective cues
  (pump, soreness, perceived challenge), and cites no studies for the specific per-muscle values.
- NSCA. Determination of Resistance Training Frequency (Kinetic Select), drawn from *Essentials of
  Strength Training and Conditioning*: total-body 2–3x/wk for novices; 3–4 d/wk at intermediate;
  split routines spread 4+ workouts across the week to raise frequency while preserving recovery.
