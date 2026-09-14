# The recommendation engine uses Epley-anchored double progression, and deliberately avoids three things that look like improvements

Each working set is prescribed by double progression inside a rep range, anchored to a decayed, smoothed **Epley** estimated 1RM computed from the user's own logged sets for that exercise. Three choices in here will look wrong to a future reader and are deliberate: **Epley despite better-fitting formulas existing**, **no scheduled deload**, and **never extrapolating strength across exercises**.

## Why Epley, when it isn't the most accurate

Because accuracy is not the binding constraint. Measured across 1–10 reps, Epley, Brzycki and Wathen disagree by **1.6–2.0% of the recommended load** — less than one plate pair on a 100kg lift. The other terms in the error budget dwarf it:

| Error source | Magnitude |
|---|---|
| Formula choice | 1.6–2.0% of load |
| Between-individual variation at 80% 1RM | 2.51 reps |
| A novice misjudging proximity to failure | 4–5 reps ≈ 8% of load |
| Test–retest of a *directly measured* 1RM | 4.2% |

So the formula is chosen on robustness instead. Epley is monotone and bounded; **Brzycki has a pole at 37 reps** — it returns 3703kg at 36 reps and goes negative at 37. Epley is also explicable to a user, and runs 3–4 percentage points conservative against the Nuzzo meta-analytic table, which is the safe direction. Swapping it for a "more accurate" formula would move recommendations by less than the rounding to the nearest plate.

## Why no scheduled deload

Planned deloads have no RCT support and one RCT against: Coleman 2024 found a scheduled 1-week deload made strength *worse* with no compensating benefit. Back-off is **reactive only**, triggered by missed sets through a graded ladder. A calendar deload is the single most likely "improvement" someone will try to add later.

## Why never extrapolate across exercises

Cross-exercise strength ratios are folklore, and exercise type is the only moderator that meaningfully shifts the reps-to-%1RM curve. An exercise with no history gets a calibration ramp, never a number inferred from a related lift.

## Consequences

- **Decay after a layoff is framed as the estimate becoming *uninformative*, not the user becoming fragile.** The injury framing is unsupported — resistance-training injury rates are 0.24–1 per 1000 hours, and Halonen 2024 found pre-break strength returned within 5 weeks of a 10-week break. So decay's real job is to **trigger re-calibration**, not to compute a smaller number: the threshold matters, the rate barely does. Any rate is an invented, tuneable judgement call and must never be presented as derived.
- **Sets to failure are never required.** Proximity to failure has a negligible relationship with strength gain and a real one with hypertrophy, and ACSM prescribes power work explicitly not-to-failure. Measurement sets are opt-in and never prescribed for a strength or power goal — which means those users supply less information and their recommendations climb more cautiously. That is correct behaviour, not a deficiency to fix.
- **A high rep preference carries an effort requirement.** Hypertrophy is load-independent above ~30% 1RM *only if* effort is high. Prescribing 20-rep sets without conveying that they must approach failure is not the intervention the evidence supports.
- **`assisted` exercises invert every comparison.** Less assistance is more progress, so beat/miss, personal bests and graph direction all flip. Getting this wrong means confidently coaching someone to get weaker.
