# Exercise dataset for the preloaded bank

Research for [#2](https://github.com/TylerGimpel/GymTrackingApp/issues/2). Investigated 2026-09-14 against repository `LICENSE` files, project docs, and live APIs — not blog summaries. Every row count and coverage percentage below was computed by downloading the dataset and counting, not read off a README.

> **Not legal advice.** This is an engineering assessment of what the licences say. The commercial-sale decision should be made with that caveat in mind.

## Verdict

**Take the structured metadata from [`yuhonas/free-exercise-db`](https://github.com/yuhonas/free-exercise-db) (Unlicense, 876 rows). Ship none of its images. Treat its instruction prose as suspect.**

If the app is to ship exercise demo images, **that decision cannot be satisfied by the primary source** — the only two candidates with cleanly licensed imagery are RepDB (bespoke vendor licence, attribution required) and everkinetic (CC-BY-SA, share-alike). See [Imagery](#imagery-the-decisive-split).

## The distinction that decides this

Every candidate here states one licence at the repo root. For three of the six, **that licence does not cover the images**, and in two cases the repo's own documentation says so. A reader who checks only the GitHub licence badge gets the wrong answer:

| Repo | Badge says | Actually covers images? |
|---|---|---|
| `yuhonas/free-exercise-db` | Unlicense | **No.** Owner states he does not know their origin. Upstream says they were scraped. |
| `wrkout/exercises.json` | Unlicense | **No.** `CONTRIBUTING.md` admits the scrape in writing. |
| `hasaneyldrm/exercises-dataset` | MIT ("NOASSERTION") | **No.** Explicit MEDIA EXCEPTION carve-out; media is © Gym visual. |
| `RepDB/exercise-dataset` | "NOASSERTION" | Yes — separate `LICENSE-DATA.md` covers data *and* images. |
| `everkinetic/data` | CC-BY-SA-4.0 | Yes — repo-wide, images included. |
| `wger-project/wger` | AGPL-3.0 | N/A — AGPL covers **code only**; data/images are per-entry CC. |

## Candidates

### 1. `yuhonas/free-exercise-db` — RECOMMENDED for data

**Licence: The Unlicense** (public domain dedication), verbatim at [`LICENSE.md`](https://github.com/yuhonas/free-exercise-db/blob/main/LICENSE.md). Confirmed the actual file text, not the badge — GitHub's API reports the path as `LICENSE.md`, there is no `LICENSE`.

Permits commercial redistribution in a closed-source APK with **no attribution requirement and no share-alike**. This is the only candidate with zero ongoing in-product obligations, which is precisely what a 99c paid app wants.

**Rows: 876** (`dist/exercises.json`, 1.0 MB, single JSON array). All 876 ids and all 876 names are unique — no deduplication needed. Filtered to resistance training (`strength` / `powerlifting` / `olympic weightlifting` / `strongman`): **678 rows**. The remaining 198 are `stretching` (123), `plyometrics` (61) and `cardio` (14), which may or may not be in scope.

**Field coverage** (measured across all 876):

| Field | Fill | Notes |
|---|---|---|
| `name`, `id`, `level`, `category`, `primaryMuscles` | 100% | |
| `instructions` | 99.4% | See provenance warning below |
| `force` | 96.6% | `push` / `pull` / `static`; 30 null |
| `equipment` | 91.2% | 13 values; 77 null, plus 122 are the useless `"other"` |
| `mechanic` | 90.1% | `compound` / `isolation`; 87 null |
| `secondaryMuscles` | **68.9%** | 272 rows have none — the weakest field |

Controlled vocabularies are small and clean: 17 muscles (`quadriceps`, `lats`, `middle back`, …), 13 equipment values, 7 categories, 3 levels. Maps to a Room schema with almost no massaging.

This covers **primary muscle, secondary muscle, equipment, mechanic, and force** — five of the seven fields the ticket asks for.

**Provenance — read this before relying on it.** This repo is a restructured fork of `wrkout/exercises.json`. Two problems:

1. **The images are not the uploader's to license.** In [issue #2](https://github.com/yuhonas/free-exercise-db/issues/2) the owner writes that he *"actually ha[s] no idea where the images are from or if they are royalty free so usage would be at your own risk"*. The README still does not carry this warning despite the owner saying three separate times ([#2](https://github.com/yuhonas/free-exercise-db/issues/2), [#12](https://github.com/yuhonas/free-exercise-db/issues/12), [#13](https://github.com/yuhonas/free-exercise-db/issues/13)) that he intends to add it. In [#13](https://github.com/yuhonas/free-exercise-db/issues/13) the proposed fix is to swap them for placeholders.
2. **The upstream repo admits the scrape in writing.** [`wrkout/exercises.json/CONTRIBUTING.md`](https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md) states: *"Currently all exercises have two images, these have been scrapped off the internet, therefore l do not own the copy right for these images and would advise against using them in comercial projects."* [wrkout #305](https://github.com/wrkout/exercises.json/issues/305) traces them by reverse image search to bodybuilding.com and quotes that site's terms prohibiting reproduction; a commenter in `free-exercise-db` #13 independently attributes some to ExRx.net.

**Consequence for the text.** The Unlicense is only as good as the uploader's right to grant it, and we now know that right was absent for part of the repo. That contaminates confidence in the instruction prose too. Mitigation: the *structured fields* — exercise name, which muscle it works, what equipment it needs, compound vs isolation — are facts and short phrases, which US copyright does not protect. The `instructions` free text is expressive prose and is the only part carrying real risk. **Take the fields; re-author or omit the prose.**

### 2. `RepDB/exercise-dataset` — RECOMMENDED if the app ships images

**Licence: bespoke "RepDB Free Tier License v1.0"** ([`LICENSE-DATA.md`](https://github.com/RepDB/exercise-dataset/blob/main/LICENSE-DATA.md)), covering data *and* images together. Not an OSI or CC licence. The seven terms that matter:

- **Free for commercial use inside applications** — explicitly permitted, at no cost.
- **Attribution required**: a visible *"Exercise data by RepDB (repdb.co)"* link in an about/credits screen. Satisfiable in one screen.
- **No redistribution as a dataset** — "In-app use only."
- No generative-AI derivation from the images.
- The `premium-samples/` animations are evaluation-only.

**Rows: 601** (509 resistance-training). Field coverage is the best of any candidate — **100% fill** on `force_type`, `mechanic`, `difficulty`, `primary_muscles`, `goals`, `instructions_en`, `met`, and `images`; 93.7% on `secondary_muscles`; 70% on `equipment` (56 values, finer-grained than free-exercise-db's 13).

Uniquely, it ships two fields that directly answer *"what is this exercise good for"* — the thing the ticket flags as hardest to source:

- `goals`: `hypertrophy` (421), `strength` (296), `endurance` (89), `mobility` (82), `power` (42), `rehabilitation` (19)
- `tags`: including `knee_safe` (300), `lower_back_safe` (270), `no_axial_load` (268), `shoulder_safe` (265), plus split hints (`push_day`, `pull_day`, `leg_day`)

Also carries `is_unilateral`, `is_bodyweight`, MET values, and EN/DE/ES names + instructions.

**Why it is not the primary recommendation.** The licence is a private contract from a single small vendor, not an irrevocable public dedication. The repo was created 2026-06-29 and has 36 stars; there is no guarantee the free tier persists. A public-domain source cannot be withdrawn; this can. For a hard licensing constraint on a product that may be sold, that asymmetry matters more than the better metadata.

**One ambiguity worth resolving before relying on it.** Term 3 forbids repackaging "as a dataset", while term 1 permits use "inside applications". A Room-prepopulated SQLite file inside an APK reads as in-app use — but RepDB sells SQLite delivery as a *paid-tier* feature ([`ATTRIBUTION.md`](https://github.com/RepDB/exercise-dataset/blob/main/ATTRIBUTION.md) lists "SQLite" alongside 1024px images and animations under "upgrade"). Whether building your own SQLite from the free JSON is permitted is **not settled by the licence text**. It is a one-email question to support@repdb.co and should be asked before committing.

### 3. wger — worst fit despite being the most legitimate project

The README is unusually careful and worth quoting exactly:

> - Application Code: AGPL-3.0-or-later
> - Exercise/Ingredient Data: Creative Commons (see individual entries)

**The AGPL does not infect the app** — it covers the Django server code, which this project would not use. Only the data is in question, and it is licensed **per entry**. Measured across all 862 entries from the live API (`https://wger.de/api/v2/exerciseinfo/`):

- **CC-BY-SA 4.0 — 709 (82.2%)**
- **CC-BY-SA 3.0 — 132 (15.3%)**
- CC0 — 21 (2.4%)

So **97.6% is share-alike**, which is the outcome the ticket warns about. CC-BY-SA has no GPL-style linking concept, so it would not force the Kotlin source open — but the shipped exercise database would itself be an adaptation requiring CC-BY-SA licensing, and attribution is owed to **241 distinct `license_author` values** (individual usernames like `clafal`, `Croak6728`, `Davidgj32`). An in-product credits screen listing 241 contributors is the realistic obligation.

**Metadata is also the weakest of the serious candidates.** Confirmed absent across all 862 entries: **no `force`, no `mechanic`, no `level`/difficulty.** Categories are body regions (Legs, Back, Arms…), not movement patterns. Muscles are anatomical Latin (`Obliquus externus abdominis`, `Biceps femoris`) and would need mapping to consumer-facing names. Coverage: equipment 77.8%, primary muscles 83.6%, secondary muscles 43.0%.

Its imagery *is* cleanly licensed — but only 374 images across 273 exercises (31.7% coverage) and 78 videos across 46 (5.3%), all CC-BY-SA. Sparse enough that the app would look half-finished.

### 4. `hasaneyldrm/exercises-dataset` — largest row count, unusable media, unverified data provenance

**Licence: MIT for data only**, with an explicit and commendably honest MEDIA EXCEPTION in [`LICENSE`](https://github.com/hasaneyldrm/exercises-dataset/blob/main/LICENSE):

> The MIT license above covers ONLY the code, tooling, dataset structure, and instruction text/translations… It DOES NOT cover the exercise media in the `images/` and `videos/` directories. That media is © Gym visual… **Cloning this repository does not grant you any license to the media; obtain your own from Gym visual.**

So the 1,324 animated GIFs — the repo's main draw — are off the table without a separate commercial licence from [gymvisual.com](https://gymvisual.com/).

**Rows: 1,324**, the largest offline set found. But the schema (`body_part`, `target`, `equipment`, `gif_url`, `instructions`) **has no `force`, no `mechanic`, and no difficulty** — it fails the ticket's metadata bar in the same way wger does.

Two cautions. First, that schema is field-for-field the ExerciseDB/RapidAPI shape, and the media is licensed from Gym visual — the same pipeline ExerciseDB is built on. Whether the *data* is an independent compilation or a re-publication of ExerciseDB's catalogue **I could not verify**; the MIT grant is asserted, not evidenced. Second, the repo shows 21,823 stars against 2,763 forks (a ~7.9:1 ratio, where data repos typically run 20–50:1) accumulated in under six months. That is not evidence of wrongdoing, but the star count should not be read as community vetting.

### 5. `everkinetic/data` — small, but the cleanest imagery

**Licence: CC-BY-SA 4.0**, repo-wide and genuinely covering the images — verified the full licence text at [`LICENSE.md`](https://github.com/everkinetic/data/blob/master/LICENSE.md). Original illustrations from everkinetic.com by Greg Priday, not scraped.

**Rows: 293** — too few to be the primary bank. Has `type` (compound/isolation) but no force and no level.

Its value is the artwork: **537 SVGs** (~15 KB each) and 541 PNGs, as relaxation/tension pose pairs. SVG at that size is far kinder to an APK than raster. The cost is share-alike plus attribution, same infection question as wger.

### 6. ExerciseDB / AscendAPI — disqualified on architecture

[`exercisedb.dev`](https://docs.exercisedb.dev), now branded AscendAPI, advertises 5,000+ exercises. It is a **hosted commercial API with paid plans**, which a fully-offline app cannot use by definition; there is no offline dataset distribution. The various `exercisedb-api` repos on GitHub are AGPL-licensed *server implementations* — that licence covers the code, and grants nothing in respect of the data or media. Its imagery derives from Gym visual's commercial catalogue.

## Imagery: the decisive split

The ticket asks whether the app should ship exercise demos. The honest answer is that **the image decision and the data decision have different winners**, and no single source gives both cleanly.

| Source | Images | Licence | Size | Usable commercially? |
|---|---|---|---|---|
| free-exercise-db | 1,746 JPG across 873 rows | **None valid** | ~60 KB each ≈ **105 MB** | **No — confirmed infringing** |
| RepDB | 1,056 WebP across 601 rows (512px, start/peak) | Free tier, attribution | ~18 KB each ≈ **19 MB** | Yes, with attribution |
| everkinetic | 537 SVG / 541 PNG across ~270 rows | CC-BY-SA 4.0 | SVG ~15 KB ≈ **8 MB** | Yes, with attribution + share-alike |
| wger | 374 images / 78 videos across 273 rows (31.7%) | CC-BY-SA 3.0/4.0 | varies | Yes, with attribution + share-alike |
| hasaneyldrm | 1,324 GIF | © Gym visual | 180×180 only | **No — needs separate licence** |

Note that free-exercise-db's images are not merely unlicensed but also impractical: at ~60 KB × 1,746 they would add roughly 105 MB to the APK, well past what is sensible even before the copyright problem.

**Recommendation on demos:** ship the app without images initially. The data recommendation is unaffected by this choice, so it does not block. If demos are later judged essential, RepDB is the cleanest route (19 MB, one attribution line, images are original works commissioned by the licensor) — subject to clearing the SQLite ambiguity above.

## Metadata the recommended source does NOT cover

This is the hand-authoring bill for free-exercise-db, over the 678-row resistance-training subset:

| Gap | Scale | Notes |
|---|---|---|
| **Movement pattern** | **All 678 rows** | Absent entirely. No squat/hinge/lunge/push/pull/carry axis. `force` (push/pull/static) is a different thing and is not a substitute. Needed by the routine generator. Largest single item. |
| **"What is this good for"** | **All 678 rows** | No goal or purpose tagging. `category` + `primaryMuscles` only approximate it. RepDB's `goals` and safety `tags` have no equivalent here. |
| `secondaryMuscles` | **211 rows** | 31% of the subset has none. Degrades search-by-muscle recall. |
| `equipment` | **78 rows** | 6 null + 72 literal `"other"`, which is unusable for an equipment filter. |
| `mechanic` | 4 rows | Near-complete. Trivial. |
| `force` | 12 rows | Near-complete. Trivial. |
| **Alternate names / aliases** | All rows | No alias field. "Bench Press" vs "Barbell Bench Press" vs "BB Bench" will not cross-match. Hurts search noticeably. |
| **Instruction prose** | 678 rows | Not a coverage gap but a **risk** gap — see provenance. Re-author or omit. |

Realistically: two fields need authoring from scratch across the whole set (movement pattern, goals), three need patching on 4–211 rows each, and aliases are a quality-of-life addition.

**Worth considering:** RepDB and free-exercise-db overlap on only 101 exercise names (exact normalised match) out of 876 and 601. RepDB could be used as a *reference* to hand-author movement pattern and goals onto the free-exercise-db rows — but note term 3 and the no-derived-dataset rule; deriving your shipped metadata from RepDB while claiming the Unlicense would not be honest. If RepDB's fields are wanted, take RepDB under its licence and attribute it.

## Converting to a Room-prepopulated SQLite

Low effort. `dist/exercises.json` is a single 1.0 MB JSON array of 876 flat objects, schema-validated in-repo against [`schema.json`](https://github.com/yuhonas/free-exercise-db/blob/main/schema.json), so the shape is stable and machine-checkable.

1. Build-time script (Python or a Gradle task) reads the JSON and writes a `.db` into `app/src/main/assets/`.
2. Load via Room's `createFromAsset()`.
3. **The known pitfall:** the prepopulated file's schema must match Room's expectation exactly, including the `room_master_table` identity hash. Generating the DB from Room's own exported schema JSON (`room.schemaLocation`) rather than hand-writing the DDL avoids a class of runtime crashes.
4. The three list-valued fields (`primaryMuscles`, `secondaryMuscles`, `instructions`) need either join tables or a `TypeConverter`. Join tables are the better call here — search-by-muscle wants to query secondary muscles directly, and a comma-joined string forces `LIKE` scans.
5. Text-only, the payload is ~1 MB uncompressed (574 KB of it instruction prose, which drops out if that text is omitted). Negligible APK impact.

No scraping, no network at build time, no runtime dependency — consistent with the fully-offline constraint.

## What I could NOT verify

Stated plainly, because these are the residual risks:

- **The origin of free-exercise-db's instruction text.** I confirmed the *images* are scraped, from the upstream repo's own written admission. I could not confirm or rule out that the prose was taken from the same source. Circumstantially: the `Title_Case_Underscore` ids and the recurring "Tip:" idiom match bodybuilding.com's exercise database conventions, and both came down the same pipeline. Against that, a commenter in issue #13 compared descriptions to ExRx.net and found them dissimilar — but nobody checked bodybuilding.com, and bodybuilding.com has since retired its public exercise database, so the comparison can no longer be made directly. **Unresolved.** This is why the recommendation is to take the fields and not the prose.
- **Whether `hasaneyldrm/exercises-dataset`'s MIT data grant is well-founded**, given the schema matches ExerciseDB and the media is licensed from Gym visual. The grant is asserted; no provenance evidence is published.
- **Whether RepDB's free tier permits a self-built SQLite in an APK.** The licence text does not settle it and the vendor sells SQLite as a paid feature. Needs a direct answer from RepDB.
- **Whether everkinetic's illustrations are cleared for the poses depicted.** The CC-BY-SA grant is repo-wide and appears sound, but I verified the licence, not the chain of title behind it.
- **No legal review of the share-alike question.** Whether shipping a CC-BY-SA exercise database inside a closed-source APK triggers ShareAlike on the *database only* (my reading) or is argued more broadly is a question for a lawyer, not an engineer. It is moot under the recommendation, since the Unlicense has no such term.

## Sources

All primary. Licence texts and data were fetched directly; issue threads read in full via `gh`.

- [yuhonas/free-exercise-db `LICENSE.md`](https://github.com/yuhonas/free-exercise-db/blob/main/LICENSE.md) · [`README.md`](https://github.com/yuhonas/free-exercise-db/blob/main/README.md) · [`schema.json`](https://github.com/yuhonas/free-exercise-db/blob/main/schema.json) · [`dist/exercises.json`](https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json)
- Image-licence threads: [#2](https://github.com/yuhonas/free-exercise-db/issues/2), [#12](https://github.com/yuhonas/free-exercise-db/issues/12), [#13](https://github.com/yuhonas/free-exercise-db/issues/13)
- [wrkout/exercises.json `CONTRIBUTING.md`](https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md) (the scrape admission) · [`LICENSE.md`](https://github.com/wrkout/exercises.json/blob/master/LICENSE.md) · [issue #305](https://github.com/wrkout/exercises.json/issues/305)
- [wger `README.md` licence section](https://github.com/wger-project/wger/blob/master/README.md) · live API: `https://wger.de/api/v2/exerciseinfo/?limit=900` and `https://wger.de/api/v2/license/`
- [RepDB `LICENSE-DATA.md`](https://github.com/RepDB/exercise-dataset/blob/main/LICENSE-DATA.md) · [`ATTRIBUTION.md`](https://github.com/RepDB/exercise-dataset/blob/main/ATTRIBUTION.md) · [`exercises.json`](https://raw.githubusercontent.com/RepDB/exercise-dataset/main/exercises.json)
- [hasaneyldrm/exercises-dataset `LICENSE`](https://github.com/hasaneyldrm/exercises-dataset/blob/main/LICENSE) · [`NOTICE.md`](https://github.com/hasaneyldrm/exercises-dataset/blob/main/NOTICE.md)
- [everkinetic/data `LICENSE.md`](https://github.com/everkinetic/data/blob/master/LICENSE.md) · [`exercises.json`](https://raw.githubusercontent.com/everkinetic/data/master/exercises.json)
- [ExerciseDB / AscendAPI docs](https://docs.exercisedb.dev)
- [Gym visual Terms & Conditions](https://gymvisual.com/content/3-terms-and-conditions-of-use) (governs hasaneyldrm's media)
