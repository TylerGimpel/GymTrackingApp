# Where equipment, exercise and muscle images can come from

Research for [#132](https://github.com/TylerGimpel/GymTrackingApp/issues/132), under the UI overhaul map [#130](https://github.com/TylerGimpel/GymTrackingApp/issues/130). Investigated 2026-09-25. Sources: licence files and source trees read through the GitHub API, file sizes measured, and bank coverage counted against `data/upstream-exercises.json` and `data/authored-exercises.json`. Builds on [`exercise-dataset.md`](exercise-dataset.md), which covered exercise imagery for #2. Licence facts from that note were re-checked here.

> **Not legal advice.** This is an engineering reading of what each licence says.

## Verdict

| Category | Recommendation | Licence duty |
|---|---|---|
| **Equipment icons** | **Tabler Icons** as the base set. Draw the missing ~9 in Tabler's own grid (24×24, 2px round stroke). | MIT: include the copyright and permission notice in the app's open-source licences screen. |
| **Exercise images** | **Ship no per-exercise pictures.** Each exercise shows its **equipment icon** plus the **muscle diagram** with its primary and secondary muscles lit. That gives 100% coverage from data the bank already has. | None beyond the two rows above. |
| **Muscle diagram** | **`Jsplice/MuscleMap` path data** (MIT, written provenance), converted to a Compose `ImageVector` at build time. Add a neck shape by hand. Fallback: `vulovix/body-muscles` (Apache-2.0). | MIT notice (or Apache-2.0 NOTICE for the fallback). |

All three are vectors whose colours come from the theme at draw time. Dark and light themes both work without separate assets, and the total APK cost is well under 250 KB.

## 1. The bank's own source: free-exercise-db images are not usable

Settled already, and nothing has changed. [ADR-0002](../adr/0002-exercise-bank-source-and-omissions.md) and [`data/README.md`](../../data/README.md) exclude upstream `images` because they are scraped:

- The owner of [`yuhonas/free-exercise-db`](https://github.com/yuhonas/free-exercise-db) says in [issue #2](https://github.com/yuhonas/free-exercise-db/issues/2) that he does not know where the images came from.
- The repo it was forked from admits the scrape in writing in [`wrkout/exercises.json` CONTRIBUTING.md](https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md).

Quality and size would disqualify them anyway. They are photos with baked-in backgrounds, so they do not adapt to a dark theme, and at about 60 KB × 1,746 they would add about 105 MB ([`exercise-dataset.md`](exercise-dataset.md#imagery-the-decisive-split)). Build rule 3 in `data/README.md` already rejects any row carrying `images`.

## 2. Equipment icons

These are the 19 equipment values the picker must show, from the closed vocabulary in `data/README.md`. The 13 upstream values have these row counts in the upstream snapshot: barbell 170, dumbbell 123, other 122, body only 111, cable 81, (none) 77, machine 67, kettlebells 56, bands 20, medicine ball 17, exercise ball 12, foam roll 11, e-z curl bar 9. The six cardio values were added by this project.

### Coverage by set

Each set's source tree was listed and searched by file name (✓ = a direct icon, ~ = a usable stand-in, blank = nothing).

| Equipment | Tabler (MIT) | Pictogrammers MDI (Apache-2.0) | Material Symbols (Apache-2.0) | Phosphor (MIT) | Lucide (ISC) | Healthicons (MIT) | game-icons (CC BY 3.0) |
|---|---|---|---|---|---|---|---|
| barbell | ✓ `barbell` | ~ `weight-lifter` | | ✓ `barbell` | | ~ `weights` | ~ `weight-lifting-up` |
| dumbbell | ✓ `dumbbell` | ✓ `dumbbell` | ✓ `fitness_center` | | ✓ `dumbbell` | | |
| kettlebells | ~ `weight` | ✓ `kettlebell` | | | ~ `weight` | | ~ `weight` |
| cable | | | | | | | |
| machine | | | | | | ~ `gym` | |
| body only | ✓ `stretching` / `gymnastics` | ✓ `gymnastics` | ✓ `sports_gymnastics` | ✓ `person-arms-spread` | ~ `biceps-flexed` | ✓ `exercise` | ~ `muscle-up` |
| bands | | | | | | | |
| medicine ball | | | | | | | ~ `throwing-ball` |
| exercise ball | ✓ `exercise-ball` | | | | | | |
| foam roll | | | | | | | |
| e-z curl bar | | | | | | | |
| treadmill | ✓ `treadmill` | | | | | | |
| stationary bike | ~ `bike` | ~ `bike` | ~ `directions_bike` | ~ `bicycle` | ~ `bike` | ✓ `exercise-bicycle` | ~ `aero-bike` |
| rower | | ~ `rowing` (a boat) | ~ `rowing` (a boat) | | | | |
| elliptical | | | | | | | |
| stair climber | ~ `stairs-up` | ~ `stairs-up` | ~ `stairs` | ~ `stairs` | ~ `stairs` | | |
| outdoor | ✓ `run` | ✓ `run` | ✓ `directions_run` | ✓ `person-simple-run` | | ✓ `running` | ~ `run` |
| other | ~ `weight` | | | | | | |

**No open set covers cable, machine, bands, foam roll, e-z curl bar, rower (the machine) or elliptical.** These are the gym-specific items. General-purpose icon sets stop at dumbbells and bikes. Whichever set is chosen, about 9 icons have to be drawn. The real decision is which set's grid to draw them in.

### Comparison

| Set | Licence (primary source) | Size of set | Style | Fit |
|---|---|---|---|---|
| **Tabler Icons** | MIT, [`LICENSE`](https://github.com/tabler/tabler-icons/blob/main/LICENSE) | 5,166 outline + filled | 24×24, `stroke-width="2"`, round caps and joins, `stroke="currentColor"` | **Best coverage**: it is the only set with barbell, treadmill and exercise ball. Actively maintained (pushed 2026-09-22, ~21.8k stars). |
| Pictogrammers MDI | Apache-2.0 per [`LICENSE`](https://github.com/Templarian/MaterialDesign-SVG/blob/master/LICENSE) ("free for commercial projects") | 7,447 | Filled 24×24 glyphs | The only one with a real kettlebell. Filled glyphs sit awkwardly beside the thin, big-type style the overhaul wants. |
| Material Symbols | Apache-2.0 ([`google/material-design-icons`](https://github.com/google/material-design-icons)) | Large; names checked against `fonts.google.com/metadata/icons` | Variable weight/fill | Fitness coverage is thin: `fitness_center`, `sports_gymnastics`, `exercise`, `directions_bike`, `rowing` (boat), `stairs`, `pool`. No barbell, kettlebell or treadmill. |
| Phosphor | MIT ([`phosphor-icons/core`](https://github.com/phosphor-icons/core)) | 1,512 per weight × 6 weights | Six weights | Has a barbell but no dumbbell. |
| Lucide | ISC ([`LICENSE`](https://github.com/lucide-icons/lucide/blob/main/LICENSE)), with some icons MIT from Feather | 1,856 | 24×24, 2px stroke | Close to Tabler's look, less coverage. |
| Healthicons | MIT ([`LICENSE`](https://github.com/resolvetosavelives/healthicons/blob/main/LICENSE)) | 48×48 medical set | Filled/outline | Aimed at health programmes. A few exercise pictograms, nothing gym-specific. |
| game-icons.net | **CC BY 3.0** per [`license.txt`](https://github.com/game-icons/icons/blob/master/license.txt), attribution per contributor folder | ~4,000 | Solid, game-art | Attribution is per artist (Lorc, Delapouite, …), and the style does not match. Avoid. |

### Recommendation

**Tabler, plus about 9 custom icons drawn on Tabler's grid:** cable (a pulley and handle), machine (a weight stack), bands, medicine ball, foam roll, e-z curl bar, rowing ergometer, elliptical and stair climber. Kettlebell can use Tabler `weight` (a bell with a ring handle) or a custom one drawn to match. MDI's kettlebell is Apache-2.0 and usable, but it is filled, so redrawing it as a stroke icon keeps the set consistent.

- **Licence:** MIT allows closed-source commercial shipping. The only duty is to carry the copyright and permission notice somewhere, usually an "Open-source licences" screen. Custom icons are our own work.
- **Size:** Tabler outline SVGs are 512–719 bytes each (measured: `barbell` 719 B, `dumbbell` 595 B, `treadmill` 620 B, `weight` 512 B, `exercise-ball` 565 B). As Android VectorDrawables or Compose `ImageVector`s, 19 icons come to roughly **10–20 KB**.
- **Theme:** Tabler strokes use `currentColor`, which becomes the icon tint in Compose, so one asset serves both themes. VectorDrawable supports `strokeWidth`, `strokeLineCap` and `strokeLineJoin`, so nothing is lost in conversion.
- **Consistency:** Tabler's other icons (navigation, calendar, settings) can come from the same set, so the whole app shares one stroke language.

## 3. Exercise images

### Candidates

| Source | Licence (primary source) | Coverage of our 896 rows | Style / theme | APK size |
|---|---|---|---|---|
| free-exercise-db | Scraped, **unusable** (§1) | — | Photos, fixed background | ~105 MB |
| **RepDB** free tier | Bespoke, [`LICENSE-DATA.md`](https://github.com/RepDB/exercise-dataset/blob/main/LICENSE-DATA.md): in-app commercial use allowed; visible **"Exercise data by RepDB (repdb.co)"** attribution; no redistribution as a dataset; no generative-AI derivation; images may be resized/cropped/recoloured | 601 RepDB exercises. Only **101** match our names exactly ([`exercise-dataset.md`](exercise-dataset.md)), so about 11% | Flat illustrations, 512px WebP. **Transparent backgrounds are a paid-tier feature**, so free images carry a background that will box up on a dark theme | ~18 KB each, ≈19 MB for all 1,056 |
| **everkinetic** | **CC BY-SA 4.0**, [`LICENSE.md`](https://github.com/everkinetic/data/blob/master/LICENSE.md), repo-wide including images | 293 exercises, **67** exact normalised name matches against our 896 (measured), so about 7.5% | Line art in `#333333` and `#FFFFFF` fills: needs recolouring for dark theme, which is a modification under share-alike | SVG ~15–31 KB per pose, 2 poses per exercise |
| wger | Per-entry CC BY-SA (code is AGPL) | 31.7% of wger's own 862 entries have any image (from `exercise-dataset.md`); lower against ours | Mixed contributors, no consistent style | Varies |
| Drawing our own | Ours | Whatever we draw | Whatever we choose | ~5–15 KB per vector |

Two new points since the #2 research:

- RepDB's licence now states that **"All images are original works generated with AI tooling commissioned by RepDB"** ([`LICENSE-DATA.md`](https://github.com/RepDB/exercise-dataset/blob/main/LICENSE-DATA.md), "Origin of the data").
- ADR-0002 rejected RepDB for the data because of its no-derived-dataset term. Taking only its images still means displaying its attribution line and keeping a name mapping from our rows to RepDB image files. It still covers only about 1 in 9 of our exercises.

### Recommendation: no per-exercise pictures

**No source gives clean, consistent pictures for more than about 11% of the bank.** A bank where one exercise in nine has a picture looks unfinished, and mixing two licensed sets breaks visual consistency. ADR-0002 also means the app shows no how-to text, so a picture would be the only instruction content anyway, and that is not what the overhaul is after.

What each exercise can show instead, from data already in the bank:

1. **Its equipment icon** (§2). `equipment` is 91% filled upstream. The 77 null rows and 122 `other` rows fall back to a generic icon.
2. **The muscle diagram with primary muscles highlighted strongly and secondary muscles lightly** (§4). `primaryMuscles` is **100%** filled and `secondaryMuscles` 68.9%.

This covers all 896 rows, including the 20 authored ones and any the lifter adds (they carry the same fields), and adds nothing to the APK per exercise. It also fits the map's "little text" direction.

If real demos are ever wanted, RepDB is the only candidate that ships commercially without share-alike. Its coverage gap and background problem mean it cannot be the main plan.

## 4. Muscle diagram

The diagram must light up each of the bank's 17 muscle values (`abdominals`, `abductors`, `adductors`, `biceps`, `calves`, `chest`, `forearms`, `glutes`, `hamstrings`, `lats`, `lower back`, `middle back`, `neck`, `quadriceps`, `shoulders`, `traps`, `triceps`). Primary-muscle row counts in the upstream snapshot: quadriceps 148, shoulders 129, abdominals 93, chest 84, hamstrings 79, triceps 73, biceps 53, lats 38, middle back 34, calves 28, lower back 27, forearms 25, glutes 22, traps 15, adductors 13, neck 8, abductors 8.

There is no Android or Compose muscle-map library. The search found only web, React Native and SwiftUI ones. Every option below means **taking SVG path data and converting it** into a Compose `ImageVector` (or `PathParser` at runtime) with one path per muscle, so fill colour can be set per muscle from the theme.

### Candidates

Each region list was read from the source files, not the README.

| Source | Licence | Maturity | Regions (read from source) | Our 17 mapped | Provenance | Path data size |
|---|---|---|---|---|---|---|
| **[`Jsplice/MuscleMap`](https://github.com/Jsplice/MuscleMap)** | MIT, [`LICENSE`](https://github.com/Jsplice/MuscleMap/blob/main/LICENSE) | Created 2026-06, 10 stars | [`surface-ids.ts`](https://github.com/Jsplice/MuscleMap/blob/main/packages/assets/src/surface-ids.ts): abductor, adductor, biceps, calves, chest, core, forearm, gluteus, hamstrings, **latissimus**, lower back, oblique, quadriceps, **rhomboid**, shoulder front/side/rear, trapezius, triceps, each left/right. A vector `BODY` outline is included. Male and female, front and back. | **16/17**, missing `neck` | **Written**: [`ASSET_PROVENANCE.md`](https://github.com/Jsplice/MuscleMap/blob/main/ASSET_PROVENANCE.md) says the paths were hand-traced in Inkscape for this project. The reference body photos (WebP, not needed) were AI-generated. | 52–75 KB of TS per body (4 bodies) |
| [`vulovix/body-muscles`](https://github.com/vulovix/body-muscles) | Apache-2.0 with [`NOTICE`](https://github.com/vulovix/body-muscles/blob/main/NOTICE) | Created 2026-04, 30 stars | [`muscles.front.ts`](https://github.com/vulovix/body-muscles/blob/main/src/data/muscles.front.ts) / [`muscles.back.ts`](https://github.com/vulovix/body-muscles/blob/main/src/data/muscles.back.ts): neck, deltoid front/side/rear, chest upper/lower, abs, obliques, serratus, biceps, triceps, forearm flexors/extensors, **traps upper/mid/lower**, **lats upper/mid/lower**, lower-back erectors/QL, glute max/medius, hip flexor, quads, adductors, hamstrings, calves, tibialis, plus head, hands, feet. One body (unisex). | **17/17 with approximations**: `abductors` → gluteus medius; `middle back` → mid traps (no rhomboid region) | **None stated** | ~25 KB total (low-poly paths) |
| [`HichamELBSI/react-native-body-highlighter`](https://github.com/HichamELBSI/react-native-body-highlighter) | MIT, [`LICENSE`](https://github.com/HichamELBSI/react-native-body-highlighter/blob/main/LICENSE) | Since 2019, 292 stars, the most established | [`assets/bodyBack.ts`](https://github.com/HichamELBSI/react-native-body-highlighter/blob/main/assets/bodyBack.ts), `bodyFront.ts`: chest, abs, obliques, biceps, triceps, forearm, deltoids, trapezius, **upper-back** (one region), lower back, gluteal, adductors, quadriceps, hamstring, calves, tibialis, neck | **14/17**: no abductors; `lats` and `middle back` share `upper-back`, so they cannot be told apart | **None stated**. [`react-body-highlighter`](https://github.com/giavinh79/react-body-highlighter) (MIT) and its fork [`lahaxearnaud/body-highlighter`](https://github.com/lahaxearnaud/body-highlighter) reuse these polygons | 21–32 KB of TS per body (4 bodies) |
| [`melihcolpan/MuscleMap`](https://github.com/melihcolpan/MuscleMap) (SwiftUI) | MIT | 264 stars | 22 base regions incl. rhomboids, serratus, rotator cuff, "upper back" | Similar to above | **None stated** | — |
| wger / Wikimedia [`Muscular_system.svg`](https://commons.wikimedia.org/wiki/File:Muscular_system.svg), [`-back.svg`](https://commons.wikimedia.org/wiki/File:Muscular_system-back.svg) (wger's [`SOURCES`](https://github.com/wger-project/wger/blob/master/wger/core/static/images/muscles/SOURCES) names these) | **CC BY-SA 3.0**, author Termininja | Stable | Anatomical, per-muscle overlays in wger | Most | Documented | 321 KB front SVG alone |

### Recommendation: Jsplice/MuscleMap paths

- **Licence and provenance:** it is MIT and the only candidate that says in writing where its paths came from. That matters for this project: ADR-0002 dropped a whole image set because an upstream licence did not match the uploader's rights. The others either say nothing (vulovix, react-native-body-highlighter) or are share-alike (Wikimedia/wger).
- **Coverage:** it separates **lats from rhomboids (middle back) from traps**. Those are three of our 17 values with 87 primary rows between them, and react-native-body-highlighter cannot tell them apart. Front, side and rear delts are separate too, which is useful if `shoulders` is ever split. Mapping: `abdominals` → core + oblique; `middle back` → rhomboid; `shoulders` → all three delt heads; the rest are one-to-one.
- **The gap:** no neck region. `neck` is the primary muscle in 8 rows. Draw one small neck/upper-trap shape into the outline ourselves.
- **Use one body, not four:** ship the male or female front and back only (~110–130 KB of path text, smaller once compiled to an `ImageVector`), or let a setting choose. Leave out the WebP photos. They are demo art the maintainer advises replacing.
- **Theme:** the outline and every muscle are plain paths with no baked colour. In Compose, draw the outline in `onSurfaceVariant`, unworked muscles in a low-contrast surface tone, secondary muscles in a mid-tone accent and primary muscles in full accent. Both themes then come from the same asset.
- **Maturity risk:** the repo is three months old with 10 stars. Vendor a snapshot of the path files (as `data/` already does with upstream) rather than depending on the package, so a later upstream change cannot shift anything.

**Fallback: `vulovix/body-muscles`** (Apache-2.0: keep its NOTICE line in the licences screen) if the prototype prefers its angular, low-poly look or wants a single unisex body. It covers all 17 with the approximations above and is the smallest. Ask the author where the paths came from before relying on it.

**Avoid** react-native-body-highlighter for this bank. It is the most proven, but lats and middle back cannot be highlighted separately, and those are two of the bank's most common back values. Avoid Wikimedia/wger too: CC BY-SA share-alike would attach to the modified per-muscle artwork, and the files are large and anatomical rather than the plain style the overhaul wants.

## Attribution duties, all together

If the recommendations are followed, the app's open-source licences screen carries:

- Tabler Icons: MIT notice, © Paweł Kuna.
- Jsplice/MuscleMap: MIT notice, © Jsplice / MuscleMap contributors.
- (If the fallback is used) body-muscles: Apache-2.0 and its NOTICE text, © Ivan Vulović.

Nothing needs to appear on the exercise screens themselves. RepDB would require its "Exercise data by RepDB (repdb.co)" link only if its images were adopted.

## Open questions

- **Male, female, or a choice of body** for the muscle diagram. Jsplice ships both. It is a product decision for the calendar and exercise prototypes.
- **Whether `shoulders` should light all three delt heads.** The bank has one `shoulders` value, so yes for now. Splitting it would be a bank-vocabulary change, not an image change.
- **vulovix provenance**, only if the fallback is chosen.

## Sources

- Bank: [`data/README.md`](../../data/README.md), [ADR-0002](../adr/0002-exercise-bank-source-and-omissions.md), [`exercise-dataset.md`](exercise-dataset.md)
- free-exercise-db: [repo](https://github.com/yuhonas/free-exercise-db), [issue #2](https://github.com/yuhonas/free-exercise-db/issues/2); [`wrkout/exercises.json` CONTRIBUTING.md](https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md)
- Icon licences: [Tabler](https://github.com/tabler/tabler-icons/blob/main/LICENSE) · [Pictogrammers MDI](https://github.com/Templarian/MaterialDesign-SVG/blob/master/LICENSE) · [Material Design Icons](https://github.com/google/material-design-icons) · [Phosphor](https://github.com/phosphor-icons/core) · [Lucide](https://github.com/lucide-icons/lucide/blob/main/LICENSE) · [Healthicons](https://github.com/resolvetosavelives/healthicons/blob/main/LICENSE) · [game-icons](https://github.com/game-icons/icons/blob/master/license.txt)
- Exercise images: [RepDB `LICENSE-DATA.md`](https://github.com/RepDB/exercise-dataset/blob/main/LICENSE-DATA.md) · [everkinetic `LICENSE.md`](https://github.com/everkinetic/data/blob/master/LICENSE.md), [`exercises.json`](https://github.com/everkinetic/data/blob/master/exercises.json)
- Muscle maps: [Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap) ([`LICENSE`](https://github.com/Jsplice/MuscleMap/blob/main/LICENSE), [`ASSET_PROVENANCE.md`](https://github.com/Jsplice/MuscleMap/blob/main/ASSET_PROVENANCE.md), [`surface-ids.ts`](https://github.com/Jsplice/MuscleMap/blob/main/packages/assets/src/surface-ids.ts)) · [vulovix/body-muscles](https://github.com/vulovix/body-muscles) ([`NOTICE`](https://github.com/vulovix/body-muscles/blob/main/NOTICE)) · [react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter) · [react-body-highlighter](https://github.com/giavinh79/react-body-highlighter) · [melihcolpan/MuscleMap](https://github.com/melihcolpan/MuscleMap) · Wikimedia [Muscular_system.svg](https://commons.wikimedia.org/wiki/File:Muscular_system.svg), [Muscular_system-back.svg](https://commons.wikimedia.org/wiki/File:Muscular_system-back.svg)
