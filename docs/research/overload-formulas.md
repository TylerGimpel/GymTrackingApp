# Progressive overload formulas

Research note for [issue #3](https://github.com/TylerGimpel/GymTrackingApp/issues/3) — *What formula should compute the recommended weight for a user who has training history?*

Feeds the decision in [#12](https://github.com/TylerGimpel/GymTrackingApp/issues/12) (weight recommendation engine).

**Constraint this note is written against:** fully offline, single user, no coach, no network. Everything must be computable from the local set log. The app cannot assume the user rates RPE honestly, or at all. Full citations in [Sources](#sources).

> *File location note:* the repo had no `docs/research/` convention before this note; `docs/` previously held only `docs/agents/`. This is the first file here. It is a research note, not a decision — nothing here is binding on #12.

---

## Summary

**Recommended primary scheme: double progression inside a per-exercise rep range, anchored to a decayed, smoothed estimated 1RM (Epley), with a *graded* rep-count-driven response to misses (APRE-shaped), and no RPE requirement anywhere on the critical path.**

The single most important finding is in §1: **which 1RM formula you pick barely matters inside 1–10 reps.** Epley, Brzycki and Wathen disagree by about 1.6–2% of the recommended load in that range — less than one plate pair on a 100 kg lift. Meanwhile the *person* varies by ±2.5 reps at 80% 1RM, an untrained user misjudges their own proximity to failure by 3–5 reps (~8% of load), and a directly measured 1RM has a median test–retest CV of 4.2%. Formula choice is the smallest term in the error budget by a wide margin. Effort should go into the state machine around the formula, not the formula.

**Degradation when data is thin** (§10.2): full model → single-set anchor with no cross-rep-range extrapolation → cold start with a deliberately light calibration load shown as a *suggestion* → "no progression model", which issue #1 already establishes as a valid per-exercise setting.

**Where the evidence is weakest** (§11): deload necessity and timing (one RCT found a planned deload slightly *worse* for strength), the exact layoff decay rate (no usable published curve), cross-exercise strength ratios (folklore), the specific increments inside APRE and 5/3/1 (self-published, no derivation), anything above ~12 reps, and the widely assumed claim that returning too heavy after a layoff causes injury (mechanistically plausible, epidemiologically unevidenced).

---

## 0. The error budget

Everything below should be read against this. These are the competing sources of error in "what weight should this person lift today", largest first.

| Source of error | Magnitude | Evidence |
|---|---|---|
| Between-individual variation in reps at a given %1RM | SD **2.51 reps** at 80% 1RM; SD **4.36 reps** at 60% | Nuzzo et al. 2024 |
| Exercise-to-exercise variation in the reps–%1RM curve | Leg press ~**9 reps** at 90% 1RM vs bench press ~**4**; leg press 19 vs bench 14 at 70% | Nuzzo et al. 2024 |
| Training-background variation | Endurance runners did **39.9** reps at 70% 1RM leg press; weightlifters **17.9** | Richens & Cleather 2014 |
| User misjudging proximity to failure | Novices underpredict by **4–5 reps**; experienced by **1–2**; ≈**8% of load** at 3 reps of error | Steele et al. 2017; Armes et al. 2020 |
| Noise in a *directly measured* 1RM | median test–retest **CV 4.2%** (range 0.5–12.1%) | Grgic et al. 2020 |
| Plate/stack granularity | 2.5 kg on a 40 kg lift = **6.25%**; on a 20 kg lift = **12.5%** | arithmetic |
| **Choice of 1RM formula (1–10 reps)** | **1.6–2.0%** | computed, §1.2 |

The bottom row is the only one the app fully controls, and it is the smallest. **The app's accuracy ceiling is set by the person and the plates, not by the maths.** Any design that agonises over Epley-vs-Brzycki while ignoring layoff decay, miss handling, or rounding is optimising the wrong term.

---

## 1. Estimated 1RM formulas

### 1.1 Provenance — most of these are not research

This matters and is usually glossed over. Of the four the ticket names:

- **Epley (1985)** — extrapolated from a poundage chart in a University of Nebraska training manual. No documented empirical derivation, not peer-reviewed.
- **Brzycki (1993)** — a practitioner article in *JOPERD*. Presented as a practical tool; no reported sample or methodology.
- **Lombardi (1989)** — from a textbook, *Beginning Weight Training*.
- **Wathen (1994)** — practitioner manual / textbook chapter lineage (Baechle's *Essentials of Strength Training and Conditioning*).

Only **Mayhew et al. (1992)** among the classical set was fitted to a real sample (435 college students, bench press), and it is still one exercise in one population. Marzagão (2026) documents this lineage explicitly. Every one of them was derived from, at most, young trained people doing the bench press, then applied to hundreds of exercises for forty years.

So: these are conventions with decades of use behind them, not validated instruments. Treat them accordingly.

### 1.2 How they diverge

Computed directly from the published forms, at a 100 kg working weight:

| reps | Epley | Brzycki | Lombardi | Wathen | Mayhew | spread |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 103.3 | 100.0 | 100.0 | 101.3 | 108.9 | 8.9% |
| 3 | 110.0 | 105.9 | 111.6 | 109.0 | 114.0 | 7.7% |
| 5 | 116.7 | 112.5 | 117.5 | 116.6 | 119.0 | 5.8% |
| 8 | 126.7 | 124.2 | 123.1 | 127.7 | 126.3 | **3.7%** |
| 10 | 133.3 | 133.4 | 125.9 | 134.7 | 130.9 | 7.0% |
| 12 | 140.0 | 144.1 | 128.2 | 141.5 | 135.4 | 12.4% |
| 15 | 150.0 | 163.7 | 131.1 | 150.9 | 141.7 | 24.9% |
| 20 | 166.7 | **212.0** | 134.9 | 164.5 | 151.2 | 57.1% |
| 30 | 200.0 | **516.0** | 140.5 | 183.6 | 166.0 | 267% |

Forms used: Epley `w(1 + r/30)`; Brzycki `w / (1.0278 − 0.0278r)`; Lombardi `w · r^0.10`; Wathen `100w / (48.8 + 53.8e^(−0.075r))`; Mayhew `100w / (52.2 + 41.9e^(−0.055r))`. (Note: Mayhew et al. 2008 Table 2 prints Lombardi's exponent as 0.13; the widely published value is 0.10. Another small sign of how casually these have been transmitted.)

But the app never shows an e1RM as a load — it goes *back down* to a working weight. That round trip cancels most of the disagreement:

| observed set | prescribe | Epley | Brzycki | Wathen | spread |
|---|---|---:|---:|---:|---:|
| 60 kg × 8 | 5 reps | 65.1 | 66.2 | 65.7 | **1.6%** |
| 100 kg × 8 | 5 reps | 108.6 | 110.4 | 109.5 | **1.6%** |
| 60 kg × 8 | 10 reps | 57.0 | 55.9 | 56.8 | **2.0%** |
| 100 kg × 8 | 12 reps | 90.5 | 86.2 | 90.2 | 5.0% |
| 60 kg × 8 | 15 reps | 50.7 | 45.5 | 50.8 | 11.6% |

**Inside 1–10 reps, formula choice moves the recommendation by less than one plate pair.** It only starts to matter past ~12 reps, exactly where all of them are least trustworthy anyway (Mayhew et al. 2008 found equations were more accurate when fewer than 10 reps to fatigue were used; Reynolds et al. 2006 found accuracy degrading substantially above 5RM).

### 1.3 Guard rails the app must have

- **Brzycki has a pole at r = 36.97.** At 36 reps it returns 3703 kg from a 100 kg set; at 37 reps it returns a *negative* number. A user doing high-rep bodyweight-ish work will trigger this. If Brzycki is used at all, it must be hard-clamped. This alone is a good reason not to use it.
- **Lombardi compresses badly at high reps** — it reads a 30-rep set as a 1.4× multiplier, which will under-recommend to a light-high-rep user, permanently.
- **Epley and Wathen are monotone, smooth, and stay sane** across the whole range. Epley is also the only one simple enough to explain to a user in one line.
- **Clamp reps before applying any formula.** Recommend ignoring sets above ~12–15 reps for e1RM purposes entirely, or capping `r` at 12 and flagging the estimate as low-confidence.

### 1.4 Epley is systematically conservative — and that is useful

Inverting Epley gives the %1RM it implies for a rep target. Compared against the Nuzzo et al. 2024 meta-analytic table (the best empirical reps–%1RM data that exists, 7,289 individuals):

| rep target | Epley implies | Nuzzo main-model average | Epley is |
|---:|---:|---:|---|
| 5 | 85.7% | 90% | 4.3 pp lighter |
| 9 | 76.9% | 80% | 3.1 pp lighter |
| 15 | 66.7% | 70% | 3.3 pp lighter |
| 23 | 56.6% | 60% | 3.4 pp lighter |

Epley prescribes about 3–4 percentage points lighter than the average person's true capacity at that rep count. For an app that must never put a user under a bar they cannot handle, **a consistent conservative bias is a feature, not an error.** This is probably the strongest single argument for Epley over the alternatives.

### 1.5 The weight-dependent alternative

Marzagão (2026) fits `1RM = w · (1 + (r−1)^0.85 / (−2.55 + 4.58·ln w))` on 303,494 near-failure sets across 388 exercises, and reports 17–22% lower internal inconsistency than all four classical equations, positive for every one of 183 exercises with sufficient data. The idea — that the reps-to-1RM conversion factor should depend on the load, because a 10 kg lateral raise and a 100 kg squat do not sit on the same curve — is well supported by the independent evidence (Shimano et al. 2006; Richens & Cleather 2014; Nuzzo et al. 2024).

**Do not adopt it as the primary, yet.** Caveats, in the author's own terms and mine:

- It is a **pre-print**, not peer-reviewed.
- The author is at **Fitbod**, a commercial competitor, and the data is Fitbod's.
- There are **no measured 1RMs anywhere in it**. It is optimised against an *internal consistency* criterion — do different weight/rep pairs from the same person and window agree? A formula can be perfectly self-consistent and uniformly wrong. The author concedes this directly.
- Sets were filtered for near-failure but effort could not be verified.

Worth logging as a candidate to revisit if it clears peer review. Its practical effect is to recommend *heavier* than Epley on light isolation work (49.1 vs 46.7 kg from a 40 kg × 5) and *lighter* on heavy compounds (173.9 vs 175.0 from 150 kg × 5) — directionally sensible, magnitude unverified.

---

## 2. Double progression

Hold the weight, add reps until the top of a range is reached on all sets, then add weight and drop back to the bottom of the range.

**Input needed:** reps completed per set, and a rep range. That is all. No RPE, no 1RM, no percentages. It is the cheapest scheme in the log-data sense.

**This is the closest thing to an officially endorsed progression rule that exists.** ACSM's 2009 position stand recommends a 2–10% load increase when the trainee can perform the current workload for 1–2 reps over the desired number on two consecutive sessions — graded **B** (limited RCT body). Note the "two consecutive sessions": the official rule has hysteresis built in, which is a good guard against a single lucky session ratcheting the load up.

**Evidence it works:** Plotkin et al. 2022 randomised 43 trained people to load progression vs repetition progression for 8 weeks and found both viable, with no meaningful difference in hypertrophy, strength, or endurance. Double progression alternates between the two, so it inherits the support for both.

**On a miss:** nothing happens. The weight stays, the user tries for more reps next time. This is the scheme's great virtue for an unsupervised app — *a miss is not an error state.* It is just a session that did not advance. No deload logic is required to keep it stable.

**On a long layoff:** it degrades gracefully but slowly and stupidly. It will keep recommending the pre-layoff weight, the user will miss the rep range badly, and it will keep recommending it again. Double progression has **no mechanism to go backwards** — that has to be bolted on (§7).

**Goal fit:** excellent for hypertrophy and general health (any rep range, ACSM's 70–85% / 8–12 zone), workable for strength at the low end, poor for power (which ACSM prescribes as explicitly not-to-failure at 30–60% 1RM, where a rep range is not the controlling variable).

**Verdict:** the best-evidenced, least-demanding, most failure-tolerant scheme available. Its only real gap is that it cannot reduce load.

---

## 3. Linear progression

Add a fixed increment every session.

**Input needed:** success/failure of the prescribed sets, plus a per-exercise increment.

**Primary source** for the canonical form is Rippetoe & Kilgore, *Practical Programming for Strength Training* — a practitioner book, not research, but the most carefully specified version of the idea and unusually honest about its limits. Its stated increments for young 150–200 lb males: deadlift 15–20 lb/session, squat 10–15, bench/press/clean 5–10 — with the explicit warning that squats and deadlifts hold that rate for only three to four weeks, and presses for two to three, before halving. It also insists on microloading (1, 1.5, 2 lb; 0.25, 0.5, 0.75 kg) once standard 2.5 lb / 1.25 kg jumps stop being absorbable.

**Where and why it stops working:** by the book's own account, three to nine months, because the work needed to disrupt homeostasis eventually exceeds what can be recovered from between sessions. ACSM's framing is compatible: novices adapt in a short period, and progression demands change with training status.

There is also direct experimental evidence that the *rate* is not sustainable and not the point: Halonen et al. 2024 found that in a continuously-training group, progress clearly slowed after the first ten weeks. And Mann et al. 2010 found fixed linear periodisation was outperformed by autoregulation over 6 weeks in college athletes — the linear group's bench press *did not improve at all* (−0.40 ± 49.6 N vs +93.4 ± 103 N).

**On a miss:** this is LP's whole problem and its most instructive feature. *Practical Programming* does **not** prescribe a single blanket deload. It prescribes a **graded** response:

- Missed by ~1 rep, for two to three sessions → roughly **10% reduction** and progress back up. Their worked example: stuck at 172 lb for 4/4/3 reps → drop to 155, then 160, 165, back through.
- Missed *badly* (e.g. only 1–2 reps at the stall weight) → a 10% back-off is explicitly said to be insufficient; the first session back should be **very light and low volume** (three work sets reduced to one, or warm-ups only).
- Intensity should not sit more than 10% below PR for more than one session, because neuromuscular efficiency decays much faster than basic muscle strength. **Cut volume hard, keep intensity close.**
- A **second** back-off on the same lift without a new PR in between means LP is over for that lift.

That three-tier structure — *how badly did you miss* drives *how far back you go*, and *repeated* misses drive a structural change rather than another reset — is the single most transferable idea in this ticket. It should shape #12's miss handling regardless of which scheme wins.

**On a long layoff:** LP handles this better than any other scheme, almost by accident. Reset the weight down, and the fixed increment climbs back fast. The problem is that it needs to be *told* to reset; it has no time awareness.

**Goal fit:** strength, novices, heavy-low preference. Actively wrong for an experienced user, for hypertrophy-focused training, and for anyone training inconsistently (it assumes every session advances).

---

## 4. Percentage-based schemes off a training max

### 4.1 5/3/1

Jim Wendler's 5/3/1 (self-published, 2009). All work is computed from a **Training Max** deliberately set below the real 1RM (85–90%), on a 4-week wave: 65/75/85% ×5,5,5+, then 70/80/90% ×3,3,3+, then 75/85/95% ×5,3,1+, then a deload. The last set each week is an AMRAP. TM advances a fixed amount per cycle regardless of the AMRAP; failing the prescribed reps means the TM has outrun reality and gets cut ~10%.

**There is no peer-reviewed trial of 5/3/1.** I searched for one and found none. Every source describing it is the book, a commercial site, or a blog. Its design principles are individually defensible — a sub-maximal anchor, slow deliberate advancement, a built-in reset — but the specific numbers (90%, +2.5/5 kg, −10%) have no derivation anywhere.

What is genuinely valuable in it, and portable:

1. **The training max is a deliberate underestimate.** This is the right instinct for an app whose e1RM is uncertain by several percent.
2. **The anchor advances on a schedule, not on every good session.** It decouples the state variable from session-to-session noise, which matters when the log is noisy.
3. **A failed AMRAP is the reset trigger.** The AMRAP is the measurement; the TM is the model. That is exactly the structure a recommendation engine wants.

**Input needed:** a 1RM estimate (so it inherits every problem in §1), a cycle counter, and AMRAP rep counts. It also assumes the user trains in cycles on a schedule — a bad assumption for a user who trains when they can.

**On a long layoff:** nothing automatic. TM is a stored number that goes stale silently. This is a real hazard: week 3's top set is 95% of a TM that may no longer bear any relation to the user.

**Goal fit:** strength and sports performance, heavy-low. Poor fit for hypertrophy or general health as a primary driver.

### 4.2 Texas Method

From *Practical Programming*: a weekly contrast — high volume at moderate intensity Monday (5×5 across), light Wednesday for motor pathway maintenance, a single heavy set of 5 Friday. Progression is weekly rather than per-session. The book positions it as the first thing to use when simple linear progression stops working.

**Input needed:** a weekly schedule. That is the disqualifier here. The Texas Method's progression unit is the *week*, with a specific intra-week role for each session. An app that cannot assume the user trains three specific days a week cannot run it. It also requires a routine-level structure the recommendation engine does not own.

**Verdict:** out of scope as a recommendation engine. Potentially interesting as a *routine template* (issue #1's generator), not as a per-set weight formula.

---

## 5. Autoregulation: RPE / RIR

The Zourdos et al. 2016 RIR-based RPE scale (RPE 10 = 0 reps in reserve) is the standard. It has decent construct validity — velocity and RPE correlate strongly, r = −0.88 in experienced squatters.

**Efficacy:** genuinely promising but not decisive. Helms et al. 2018 matched sets and reps and found RPE-based loading gave a small 1RM advantage in the majority of individuals (79%, 57%, 72% chances of small effect-size advantages). Mann et al. 2010 found APRE beat linear periodisation over 6 weeks. But the meta-analysis that pools it all — Hickmott et al. 2022 — found **no significant difference** between autoregulated and standardised load prescription for strength (MD 2.07 kg, 95% CI −0.32 to 4.46, p = 0.09, SMD 0.21). Autoregulation is not a free win.

**What it demands of the user, and why that sinks it here:**

- Steele et al. 2017 (n = 141): systematic **underprediction** of reps to failure, ~1–2 reps in experienced trainees, **4–5 reps in less experienced** ones.
- Armes et al. 2020 (deception design, trained participants, ≥1 year experience): meta-analytic underprediction of **2.0 reps** (95% CI 0.0–4.0); the authors judged trained participants "not adequately accurate".
- Refalo et al. 2024 found *trained* subjects doing bench press at 75% 1RM were good (mean error 0.65 ± 0.78 reps). So accuracy is real but it is earned, and it is task-specific.

Three reps of RIR error is ~8% of load (Epley, around 8 reps) — **five times the error from picking the "wrong" 1RM formula.** An app that routes its recommendation through a self-reported RIR from an untrained user has made its accuracy strictly worse than one that ignores RPE entirely.

**And it will not be entered.** Issue #1 already models RPE as an *optional* field on a logged set. Any scheme that requires it has a hard dependency on a field the user may never touch.

**On a miss / layoff:** autoregulation is the best of any scheme in principle — a layoff shows up immediately as a higher RPE at the same load, and the scheme self-corrects within one session. This is exactly the property the app wants. The problem is purely that the input is unreliable.

**Verdict: must not be on the critical path. Should be used as a correction when present.** If the user does log an RPE, it is real information — use it to adjust, but never require it and never block a recommendation on it.

### 5.1 The velocity alternative, noted and ruled out

Velocity-based training is the technically correct way to autoregulate: González-Badillo & Sánchez-Medina 2010 found mean propulsive velocity tracked %1RM at R² = 0.98, and crucially the relationship held after a 9.3% strength gain — velocity reads *relative* intensity without knowing the max. Dorrell et al. 2020 found VBT matched or beat percentage-based training over 6 weeks.

It needs a linear position transducer or an accelerometer. Issue #1 rules out wearables and BLE. Even the phone-camera route would be a runtime dependency this app does not want. **Out of scope** — but worth naming in the decision so the option is closed deliberately rather than by omission. Note also Greig et al. 2023: even individualised load–velocity profiles carry a pooled SEE of ~9.8% of 1RM, so the hardware does not buy as much precision as it appears to.

---

## 6. APRE: autoregulation without RPE

This deserves its own section because it is the one scheme that autoregulates using **only rep counts** — exactly the data this app is guaranteed to have.

Mann et al. 2010 (JSCR, n = 23 D-I football players, 6 weeks): two warm-up sets (≈50% and ≈75% of the target RM), a third set to failure at the target RM, then a fourth set at a load adjusted *from the third set's rep count*, also to failure. Next session's starting load is adjusted from the fourth set's rep count. APRE beat linear periodisation on bench press (+93.4 ± 103 N vs −0.40 ± 49.6 N, p = 0.02) and estimated squat 1RM (p = 0.05).

**The structural insight is the important part:** *the AMRAP set is the measurement, and the rep count is the feedback signal.* No RPE, no percentages, no 1RM test. The user just does reps and the model reads them. This is the shape a fully-offline app should steal.

**Caveats:**

- The actual adjustment increments are **fixed pounds** (e.g. "+5–10 lb", "+10–15 lb") with no stated derivation, published in practitioner outlets. They do not scale with the lift — +10 lb is 3% of a 300 lb squat and 20% of a 50 lb curl. Any implementation must make them **proportional**, and that is an unevidenced change to the protocol.
- One 6-week study, 23 college athletes, one training age. Not a broad evidence base.
- It requires **every working set to be taken to failure**, twice per exercise per session. That is a real cost — see §9.

---

## 7. Failure and deload behaviour

*The safety-critical half of this ticket. What happens on a miss matters more than what happens on a success, because success is self-limiting and failure compounds.*

### 7.1 A miss must be graded, not binary

The strongest available guidance is from *Practical Programming* (§3) and it is not "missed → deload 10%". It is:

| what happened | response |
|---|---|
| Hit the range | advance (weight or reps, per §2) |
| Missed by 1 rep, once | **repeat the same load.** Not a failure state. |
| Missed by 1 rep, two–three sessions running | ~10% load reduction, then climb back |
| Missed badly (e.g. half the target reps) | **cut volume hard, keep intensity within ~10% of PR.** One work set instead of three, or warm-ups only. |
| Second back-off on the same lift with no PR between | structural change — the progression model itself is wrong for this exercise |

Two things in there are easy to get wrong and worth stating explicitly for #12:

1. **A single missed set is not a signal.** Test–retest CV on a real 1RM is 4.2% (Grgic et al. 2020); day-to-day readiness varies more than that. Reacting to one bad set will produce a recommendation that oscillates and a user who stops trusting it. ACSM's own progression rule requires the target to be exceeded on **two consecutive sessions** before advancing — the same hysteresis should apply downward.
2. **When you do back off, cut volume, not intensity.** The *Practical Programming* rationale is that basic muscle strength is fairly robust to reduced training but neuromuscular efficiency is not, so dropping the load a long way is counterproductive. Spiering et al. 2021 reaches the same conclusion from the maintenance literature: **intensity is the variable that must be preserved** — strength and muscle mass can be maintained for up to 32 weeks on one session per week and one set per exercise, provided intensity is held. This is directly useful: the app can cut sets aggressively and cut load timidly.

### 7.2 Scheduled deloads: the evidence does not support them

This is the clearest case in this whole ticket of gym folklore outrunning evidence.

Coleman et al. 2024 (PeerJ, n = 39, randomised): a 1-week deload at the midpoint of a 9-week high-volume programme. The deload group came out **worse** on isometric and dynamic lower-body strength than the group that just kept training, with no difference in hypertrophy, power, or local endurance — and the *continuous* group even scored slightly better on readiness-to-train. The authors' conclusion is that the deload negatively influenced strength and did nothing for anything else.

That is one study and should not be over-read. But it is the direct test, and it points away from the received wisdom. There is no RCT evidence I could find supporting a *scheduled* deload in the sense apps usually implement it (every Nth week, automatically).

**Implication for the app:** do not build a calendar-driven deload. Build a **reactive** back-off triggered by repeated missed performance (§7.1), which is the version that has at least practitioner-literature support and a sound rationale. If a scheduled deload is offered at all it should be a user-initiated option, not an automatic behaviour, and the app should not claim it is evidence-based.

### 7.3 Do not build an ACWR-style load-spike model

An obvious temptation for a "safety" feature is to flag when the recommendation jumps too fast relative to recent training load — the acute:chronic workload ratio. **Don't.** Impellizzeri et al. 2020 lay out the conceptual issues and statistical pitfalls (mathematical coupling, arbitrary binning, confounding); the original ACWR–injury "sweet spot" figure has been the subject of a retraction/correction request, and when the data are treated as continuous with outliers removed the relationship disappears. Building on it would give the app a confident-looking safety signal with nothing behind it.

A simple absolute cap on per-session load increase (§10) is defensible on measurement grounds and requires no injury-model claim at all.

---

## 8. Long layoffs

*The other safety-critical half, and the case where every scheme in §2–§6 fails silently, because they are all functions of the last logged set and none of them is a function of the calendar.*

### 8.1 What actually happens to strength during a break

The evidence here is better than the evidence for deloads, and it is reassuring:

| Time off | What the literature says |
|---|---|
| **< 4 weeks** | Strength performance is generally readily maintained. Highly trained athletes may lose eccentric force and sport-specific power; recently acquired isokinetic strength may decline. (Mujika & Padilla 2000) |
| **~2–3 weeks** | 1RM remains above baseline; strength and volitional drive maintained, though corticospinal excitability declines. |
| **10 weeks (untrained)** | Halonen et al. 2024: after a full 10-week break, **pre-break strength was regained within 5 weeks** of retraining, and after 20 total training weeks the periodic group was indistinguishable from the continuously-training group on leg press 1RM, biceps curl 1RM, muscle CSA and jump height. |
| **16–24 weeks** | Strength gains still significantly above a non-exercising control. (Encarnação et al. 2022) |
| **32–48 weeks** | Strength advantage over control gone. Muscle size significantly reduced only after 31–52 weeks. (Encarnação et al. 2022) |

Retraining is also faster than initial training — the "muscle memory" literature (myonuclear retention, epigenetic mechanisms) supports this practically, though the *mechanism* is genuinely contested: some studies show myonuclei persist through detraining, others show they are lost with long-term inactivity.

**The headline for the app:** a layoff costs much less than users (and app designers) assume, and it is recovered fast. Halonen et al. 2024 is the key citation — **ten weeks off cost nothing at the twenty-week mark.** A conservative reset is therefore cheap: the user loses a few sessions of ramp-up and ends in the same place. An aggressive non-reset risks a session that is demoralising at best.

### 8.2 What that means for the decay rule

The literature gives shape but **not a usable per-week decay curve.** Nobody has published one for e1RM in recreational lifters. Any concrete numbers here are a judgement call, and #12 should record them as such. A defensible shape, consistent with the table above:

- **≤ 2 weeks:** no decay. Well within the maintenance window.
- **2–4 weeks:** no decay of the estimate, but flag it as stale and prescribe the *bottom* of the rep range for the first session back.
- **1–3 months:** decay modestly (order of 2–3% per month past the first month), and drop to one working set for the first session back rather than cutting the load a long way — the §7.1 volume-over-intensity principle applies here too.
- **> 3 months:** decay substantially and stop pretending. The honest move is to **re-anchor from what the user actually does** in the first one or two sessions back rather than extrapolating a stale number. This is where the app should say it doesn't know.
- **> 6–12 months:** discard the estimate. Treat the exercise as a cold start (#4's territory).

The reason to decay at all is **not** a demonstrated injury risk — it is that the estimate has become a measurement of something that no longer exists. That framing is more honest and leads to better design: the response to "I don't know your strength any more" is *lower the load and re-measure*, not *lower the load because you might get hurt*.

### 8.3 On the injury argument

Worth being straight about this, because it is where an app is most tempted to overclaim.

Resistance training is safe. The epidemiology puts bodybuilding-style training at **0.12–0.7 injuries per lifter per year, 0.24–1 per 1000 hours** — lower than running and far lower than team sports (Keogh & Winwood 2017). There is no body of evidence showing that a too-heavy recommendation after a layoff causes injuries at any meaningful rate, because nobody has studied it.

What *is* documented is the exertional-rhabdomyolysis and severe-DOMS literature: unaccustomed, excessive eccentric work in a detrained person is the recognised risk profile, and the **repeated bout effect** — a light exposure a few days to a week before a hard one confers substantial protection — is the established mitigation. That supports a genuinely useful behaviour: after a long layoff, the first session back should be *deliberately easy* and the ramp should take a week or two, not because the app is predicting an injury but because that is the known way to buy protection cheaply.

Say it that way in the UI, if it is said at all. "This is your first session back — we've kept it light" is true and useful. "Lifting heavy after time off is dangerous" is not supported.

---

## 9. Goals and rep preference

Issue #1 has the app collecting a goal (strength / hypertrophy / sports performance / general health) and a heavy-low vs light-high rep preference. Two bodies of evidence bear on how much those should actually change the recommendation — and they point in slightly different directions.

**The traditional view (ACSM 2009 position stand):**

| Goal | ACSM loading | Grade |
|---|---|---|
| Strength, novice–intermediate | 60–70% 1RM, 8–12 reps | A |
| Strength, advanced | cycling 80–100% 1RM, 1–12 reps, majority 6–12RM | A |
| Hypertrophy, novice–intermediate | 70–85% 1RM, 8–12 reps, 1–3 sets | A |
| Hypertrophy, advanced | 70–100% 1RM, 1–12 reps, 3–6 sets | A |
| Power | 30–60% upper / 0–60% lower, 3–6 reps, **not to failure** | A |
| Local endurance | 10–15 reps (novice), 10–25+ (advanced) | A/C |

**The revisionist view (Schoenfeld et al. 2021):** the repetition continuum does not hold as cleanly as that. Similar whole-muscle growth is achievable across a wide spectrum of loads ≥ ~30% 1RM, provided sets are taken close to failure; effort, not load, is the critical variable for hypertrophy. Load remains dominant for dynamic 1RM. Local endurance load-specificity is described as equivocal and weak.

Reconciling them for this app:

- **Hypertrophy / general health / light-high preference:** load is largely a *preference* variable. The app can honour the user's rep preference almost freely without costing them results. This is a strong argument for letting rep preference drive the rep target and letting the formula follow.
- **Strength / heavy-low:** load genuinely matters. Rep targets should sit low (3–6) and the anchor should be tracked more carefully.
- **Power / sports performance:** the recommendation engine should arguably **not** apply a failure-driven progression model here at all — ACSM's power prescription is explicitly *not to failure*, which is incompatible with an AMRAP-driven scheme. "No progression model" (already valid per #1) or a simple percentage target may be the right default.

**One critical asymmetry, and it changes the design:** Robinson et al. 2024's meta-regressions found **strength gains were similar across a wide range of RIR** — proximity to failure had a negligible relationship with strength — while **hypertrophy increased as sets were terminated closer to failure**. So the near-failure AMRAP sets that an APRE-style engine needs *as a measurement* are:

- free for a hypertrophy user (they'd want them anyway),
- an unnecessary fatigue cost for a strength user, who does not need to approach failure to get stronger,
- actively contraindicated for a power user.

**This means the app cannot use the same data-gathering strategy for every goal.** A hypertrophy user will hand it near-failure sets constantly; a strength user will not, and forcing them to would be trading their results for the app's measurement precision. A strength-goal engine has to work from sets that stop short, which means wider confidence bands and slower, more conservative anchor updates. That asymmetry is a genuine design constraint for #12.

---

## 10. Recommendation

### 10.1 Primary scheme

**Double progression inside a per-exercise rep range, anchored to a decayed, smoothed e1RM (Epley), with APRE-shaped graded rep-count feedback, and RPE as an optional correction only.**

Concretely:

1. **State per exercise:** a smoothed `e1RM`, the date it was last updated, a confidence flag, the current working weight, and the rep range.
2. **Update the anchor** from logged sets via Epley (`w(1 + r/30)`), ignoring or heavily discounting sets above ~12 reps. Smooth it — an exponentially weighted update, not a jump to each new maximum. Rationale: 4.2% test–retest CV means single sessions are noise, and 5/3/1's most transferable idea is that the anchor should lag the measurement.
3. **Prescribe** `load = e1RM / (1 + target_reps/30)`, rounded **down** to a loadable increment. Epley's built-in 3–4 pp conservatism (§1.4) plus rounding down gives two independent margins of safety, both cheap.
4. **Advance** by double progression: all sets at the top of the range → smallest loadable increment up, reps back to the bottom. Follow ACSM in requiring the target to be beaten on two consecutive sessions before advancing, not one.
5. **Respond to misses** on the graded ladder in §7.1 — repeat, then reduce, then cut volume, then change the model. Never on a single miss.
6. **Respond to overshoot** (recommended 8, user did 12) symmetrically: that is information about the anchor being too low, so update the anchor, but let the same two-session hysteresis apply before jumping the load. A single big overshoot is as likely to be a mis-logged set as a real jump.
7. **Cap per-session increase** at a small absolute percentage. Defensible on measurement grounds — the anchor is not accurate enough to justify a large jump — and requires no injury claim (§7.3).
8. **Decay by elapsed time** per §8.2, and re-anchor from observed performance rather than extrapolation past ~3 months.
9. **RPE, if logged, adjusts. It never gates.** A logged RPE 10 on a set that hit the rep target is a signal not to advance; a logged RPE 6 is a signal the anchor is low. Absent RPE changes nothing.

Why this combination:

- Double progression is the best-evidenced core (ACSM grade B for the rule itself, Plotkin et al. 2022 for the equivalence of its two modes) and is intrinsically failure-tolerant — a miss is a non-event.
- The e1RM anchor is what lets it do the two things double progression alone cannot: prescribe a starting load for a new rep range, and **go backwards** after a layoff.
- The APRE-shaped feedback gives autoregulation's responsiveness from rep counts alone, which is the only autoregulation input this app can trust.
- Epley is chosen for conservatism, smoothness, and explicability — not accuracy, because within 1–10 reps no formula is meaningfully more accurate than another (§1.2).

### 10.2 Degradation ladder

| Data available | Behaviour |
|---|---|
| ≥ 2 recent sets, within rep range, < 2 weeks old | Full model. Show the number plainly. |
| 1 set ever on this exercise | Anchor from it, but **do not extrapolate to a different rep range** — prescribe at the rep count actually performed. Wide confidence. |
| History exists but is 3+ months stale | Decay, prescribe light, re-anchor from the first session back. Tell the user the estimate is stale. |
| History only on a *related* exercise | **Do not extrapolate.** Cross-exercise strength ratios are folklore (§11). Fall through to cold start. |
| No history at all | Cold start (issue #4's job). Prescribe a deliberately light calibration load, present it as a suggestion rather than a prescription. |
| User has set the exercise to "no progression model" | Show last-used weight; recommend nothing. Already a valid setting per #1. |

**On confidence display:** the app should show a recommendation it has little basis for, *provided it says so.* The alternative — a blank field — pushes the user to guess, which is worse than a conservative suggestion with a caveat. But the confidence state must be visible, because a user who discovers the number was invented and unmarked will stop trusting every number the app shows.

### 10.3 What to leave out

- **Scheduled/calendar deloads** — no supporting RCT, one RCT pointing the other way (§7.2).
- **ACWR-style load-spike safety models** — methodologically discredited (§7.3).
- **Texas Method as a recommendation engine** — requires a weekly schedule the app cannot assume (§4.2).
- **Any RPE requirement on the critical path** (§5).
- **Velocity-based autoregulation** — needs hardware ruled out by #1 (§5.1).
- **Brzycki**, unless hard-clamped — it has a pole at 37 reps (§1.3).

---

## 11. Where the evidence is genuinely weakest

Stated plainly, because the ticket asked for it and because most of this space is confidently asserted and thinly supported.

1. **Deloads.** The received wisdom (every 4–6 weeks, cut to 50–60%) has essentially no RCT support, and the one direct test found a planned 1-week deload made strength outcomes *worse* with no compensating benefit (Coleman et al. 2024). One study is not settled either, but the burden of proof currently sits with the folklore.

2. **Layoff decay rates.** There is good evidence on the *shape* (§8.1) and none on the *rate* for an individual's e1RM. Any specific "decay 3% per month" number in #12 is invented. It should be recorded as a tuneable judgement call and not defended as evidence-based.

3. **Cross-exercise strength ratios.** "Your overhead press should be 60% of your bench" and similar are gym folklore with no validated basis. Nuzzo et al. 2024 found exercise type was the *only* moderator that meaningfully shifted the reps–%1RM relationship, and that the literature is skewed — bench press alone accounts for 42% of all repetition-to-failure tests and leg press 14%, leaving rows, overhead presses, deadlift variants and every isolation movement without exercise-specific data. Do not build inference across exercises.

4. **The classical 1RM formulas themselves.** Three of the four the ticket names were never derived from a study (§1.1). Their forty years of use is convention, not validation. They are fine to use — just not fine to *defend*.

5. **APRE's and 5/3/1's specific numbers.** The structures are sound; the constants (+5–10 lb, TM = 90%, −10% reset) are undocumented. Any implementation must make them proportional rather than absolute, which is itself an unevidenced modification.

6. **Anything above ~12 reps.** Prediction accuracy degrades sharply (Mayhew et al. 2008; Reynolds et al. 2006), the formulas diverge by 12–267% (§1.2), and between-individual SD nearly doubles from 80% to 60% 1RM (Nuzzo et al. 2024). A light-high-rep user is in the worst-served part of the evidence base — which is uncomfortable, because #1 explicitly offers that preference.

7. **"Returning too heavy after a layoff causes injury."** Mechanistically plausible via unaccustomed eccentric load, supported indirectly by the repeated-bout-effect literature and rhabdomyolysis case reports, but not demonstrated epidemiologically — resistance training injury rates are low (0.24–1 per 1000 h) and nobody has studied recommendation-induced injury. Decay the estimate because it has become *uninformative*, not because it is *dangerous*. Both lead to the same behaviour; only one is honest.

8. **Autoregulation's superiority.** Individual trials look good (Helms et al. 2018; Mann et al. 2010) but the meta-analysis found no significant strength difference vs standardised loading (Hickmott et al. 2022). This is contested, not settled — which happens to be convenient, since the app cannot implement true autoregulation anyway.

9. **The load–hypertrophy relationship.** ACSM (2009) and Schoenfeld et al. (2021) genuinely disagree about how much load matters for muscle growth. The app should not pick a side in the UI; it should let rep preference drive and note that both camps agree effort near failure matters more than the specific load for that goal.

---

## Sources

**1RM estimation**

- Nuzzo JL, Pinto MD, Nosaka K, Steele J (2024). *Maximal Number of Repetitions at Percentages of the One Repetition Maximum: A Meta-Regression and Moderator Analysis of Sex, Age, Training Status, and Exercise.* Sports Medicine 54(2):303–321. [PMC10933212](https://pmc.ncbi.nlm.nih.gov/articles/PMC10933212/) · [Springer](https://link.springer.com/article/10.1007/s40279-023-01937-7)
- Mayhew JL, Johnson BD, LaMonte MJ, Lauber D, Kemmler W (2008). *Accuracy of prediction equations for determining one repetition maximum bench press in women before and after resistance training.* J Strength Cond Res 22(5):1570–1577. [PDF](https://www.unm.edu/~rrobergs/478PredictionAccuracy.pdf)
- Reynolds JM, Gordon TJ, Robergs RA (2006). *Prediction of one repetition maximum strength from multiple repetition maximum testing and anthropometry.* J Strength Cond Res 20(3):584–592. [PubMed](https://pubmed.ncbi.nlm.nih.gov/16937972/)
- Grgic J, Lazinica B, Schoenfeld BJ, Pedisic Z (2020). *Test–Retest Reliability of the One-Repetition Maximum (1RM) Strength Assessment: a Systematic Review.* Sports Med Open 6:31. [PDF](https://vuir.vu.edu.au/41955/1/s40798-020-00260-z.pdf)
- Marzagão T (2026). *A Weight-Dependent 1RM Prediction Equation Optimized on 303,494 Near-Failure Sets Across 388 Exercises.* **Pre-print, not peer-reviewed**; author affiliated with Fitbod, Inc. [arXiv 2603.17495](https://arxiv.org/pdf/2603.17495)

**Loading, progression, and goals**

- Ratamess NA et al. / American College of Sports Medicine (2009). *Progression Models in Resistance Training for Healthy Adults* (position stand). Med Sci Sports Exerc 41(3):687–708. [PubMed](https://pubmed.ncbi.nlm.nih.gov/19204579/) · [PDF](https://tourniquets.org/wp-content/uploads/PDFs/ACSM-Progression-models-in-resistance-training-for-healthy-adults-2009.pdf)
- Schoenfeld BJ, Grgic J, Van Every DW, Plotkin DL (2021). *Loading Recommendations for Muscle Strength, Hypertrophy, and Local Endurance: A Re-Examination of the Repetition Continuum.* Sports 9(2):32. [PMC7927075](https://pmc.ncbi.nlm.nih.gov/articles/PMC7927075/)
- Plotkin DL et al. (2022). *Progressive overload without progressing load? The effects of load or repetition progression on muscular adaptations.* PeerJ 10:e14142. [PeerJ](https://peerj.com/articles/14142/)
- Rippetoe M, Kilgore L. *Practical Programming for Strength Training.* Aasgaard Company. Practitioner text, not peer-reviewed. Source for novice LP increments, the graded back-off protocol, and the Texas Method.
- Wendler J (2009). *5/3/1: The Simplest and Most Effective Training System for Raw Strength.* Self-published. **No peer-reviewed trial of this program was found.**

**Autoregulation, RPE and proximity to failure**

- Zourdos MC et al. (2016). *Novel Resistance Training–Specific Rating of Perceived Exertion Scale Measuring Repetitions in Reserve.* J Strength Cond Res 30(1):267–275. [Journal](https://journals.lww.com/nsca-jscr/fulltext/2016/01000/novel_resistance_training_specific_rating_of.31.aspx)
- Steele J, Endres A, Fisher J, Gentil P, Giessing J (2017). *Ability to predict repetitions to momentary failure is not perfectly accurate, though improves with resistance training experience.* PeerJ 5:e4105. [PeerJ](https://peerj.com/articles/4105/)
- Armes C et al. (2020). *"Just One More Rep!" – Ability to Predict Proximity to Task Failure in Resistance Trained Persons.* Frontiers in Psychology 11:565416. [PMC7785525](https://pmc.ncbi.nlm.nih.gov/articles/PMC7785525/)
- Mann JB, Thyfault JP, Ivey PA, Sayers SP (2010). *The effect of autoregulatory progressive resistance exercise vs. linear periodization on strength improvement in college athletes.* J Strength Cond Res 24(7):1718–1723. [PubMed](https://pubmed.ncbi.nlm.nih.gov/20543732/)
- Helms ER et al. (2018). *RPE vs. Percentage 1RM Loading in Periodized Programs Matched for Sets and Repetitions.* Frontiers in Physiology 9:247. [Frontiers](https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2018.00247/full)
- Hickmott LM, Chilibeck PD, Shaw KA, Butcher SJ (2022). *The Effect of Load and Volume Autoregulation on Muscular Strength and Hypertrophy: A Systematic Review and Meta-Analysis.* Sports Med Open 8:9. [PMC8762534](https://pmc.ncbi.nlm.nih.gov/articles/PMC8762534/)
- Robinson ZP, Pelland JC, Remmert JF, Refalo MC, Jukic I, Steele J, Zourdos MC (2024). *Exploring the Dose-Response Relationship Between Estimated Resistance Training Proximity to Failure, Strength Gain, and Muscle Hypertrophy: A Series of Meta-Regressions.* Sports Medicine 54:2069–2090. [PubMed](https://pubmed.ncbi.nlm.nih.gov/38970765/)
- Dorrell HF, Smith MF, Gee TI (2020). *Comparison of Velocity-Based and Traditional Percentage-Based Loading Methods on Maximal Strength and Power Adaptations.* J Strength Cond Res 34(1):46–53. [Journal](https://journals.lww.com/nsca-jscr/fulltext/2020/01000/comparison_of_velocity_based_and_traditional.6.aspx)

**Detraining, deloads and layoffs**

- Mujika I, Padilla S (2000). *Detraining: Loss of Training-Induced Physiological and Performance Adaptations. Part I — short term insufficient training stimulus.* Sports Medicine 30(2):79–87. [Springer](https://link.springer.com/article/10.2165/00007256-200030020-00002) (Part II, long-term: [PubMed](https://pubmed.ncbi.nlm.nih.gov/10999420/))
- Halonen EJ et al. (2024). *Does Taking a Break Matter — Adaptations in Muscle Strength and Size Between Continuous and Periodic Resistance Training.* Scand J Med Sci Sports. [Wiley](https://onlinelibrary.wiley.com/doi/10.1111/sms.14739) · [PDF](https://trepo.tuni.fi/bitstream/handle/10024/212547/Scandinavian_Med_Sci_Sports_-_2024_-_Halonen_-_Does_Taking_a_Break_Matter_Adaptations_in_Muscle_Strength_and_Size_Between.pdf?sequence=1&isAllowed=y)
- Encarnação IGA et al. (2022). *Effects of Detraining on Muscle Strength and Hypertrophy Induced by Resistance Training: A Systematic Review.* [MDPI](https://www.mdpi.com/2813-0413/1/1/1)
- Coleman M et al. (2024). *Gaining more from doing less? The effects of a one-week deload period during supervised resistance training on muscular adaptations.* PeerJ 12:e16777. [PeerJ](https://peerj.com/articles/16777/)
- Spiering BA, Mujika I, Sharp MA, Foulis SA (2021). *Maintaining Physical Performance: The Minimal Dose of Exercise Needed to Preserve Endurance and Strength Over Time.* J Strength Cond Res 35(5):1449–1458. [Journal](https://journals.lww.com/nsca-jscr/fulltext/2021/05000/maintaining_physical_performance__the_minimal_dose.35.aspx)
- Psilander N et al. (2019). *Effects of training, detraining, and retraining on strength, hypertrophy, and myonuclear number in human skeletal muscle.* J Appl Physiol 126(6):1636–1645. [Journal](https://journals.physiology.org/doi/full/10.1152/japplphysiol.00917.2018)

**Safety and load management**

- Keogh JWL, Winwood PW (2017). *The Epidemiology of Injuries Across the Weight-Training Sports.* Sports Medicine 47(3):479–501. [PubMed](https://pubmed.ncbi.nlm.nih.gov/27328853/) · [Springer](https://link.springer.com/article/10.1007/s40279-016-0575-0)
- Impellizzeri FM, Tenan MS, Kempton T, Novak A, Coutts AJ (2020). *Acute:Chronic Workload Ratio: Conceptual Issues and Fundamental Pitfalls.* Int J Sports Physiol Perform 15(6):907–913. [Journal](https://journals.humankinetics.com/view/journals/ijspp/15/6/article-p907.xml)

**Cited via the literature reviews above** (not independently retrieved): Epley 1985; Brzycki 1993; Lombardi 1989; Wathen 1994; Mayhew et al. 1992; LeSuer et al. 1997; Shimano et al. 2006; Richens & Cleather 2014; González-Badillo & Sánchez-Medina 2010; Greig et al. 2023; Refalo et al. 2024; Willardson 2006; Senna et al. 2011.
