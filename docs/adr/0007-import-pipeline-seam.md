# The importer splits at `parse → neutral records`, and the neutral shape carries fields FitNotes never fills

Only FitNotes import ships in v1. The importer is nonetheless built in two parts: a **format-specific parser** that turns a file into neutral records, and a **format-agnostic pipeline** that does everything after. The neutral record carries an optional **set type** and **superset grouping** which the FitNotes parser never populates.

Those unused fields look like dead code. They are not — deleting them is the thing this record exists to prevent.

## Why the seam

Everything interesting about importing is downstream of parsing and has nothing to do with the source format: exercise-name reconciliation and its mapping screen, import-batch recording, normalising weight to canonical kg, duplicate detection, and undo. Entangling FitNotes' SQLite specifics with that pipeline would mean rewriting all of it to add a second source.

With the seam, a second source is one new parser and nothing else. The cost of building it this way now is roughly an hour; the cost of retrofitting it is the whole importer.

## Why the fields FitNotes never fills

FitNotes has no concept of warm-up or drop sets, so every set it exports arrives with its purpose unknown. That is why imported sets are typed `working` and marked `source: imported`, and why **volume statistics treat pre-import history as unreliable** — counting unknown warm-ups as working sets would silently inflate every historical volume figure.

The trap is generalising that into "imported data is unreliable". It isn't a fact about importing; it is a fact about **FitNotes**. Hevy exports an explicit `set_type` of `normal|warmup|dropset|failure`, and is the only surveyed format that exports supersets — so Hevy-imported history would have volume figures as trustworthy as anything logged in-app.

So reliability is recorded **per import source**, and the neutral record has somewhere to put a set type when a source supplies one. Bake the assumption in instead, and adding any better-quality source means unpicking the volume rules as well as writing a parser.

## Consequences

- **A source declares what it can supply.** The FitNotes parser declares no set types and no supersets; the pipeline marks its batches' volume as unreliable on that basis, not because they were imported.
- **The CSV path is not supported at all.** On the author's own file the FitNotes CSV drops 98 superset groups and 114 personal-record flags that the SQLite backup keeps, so supporting it would mean a second parser, two header generations, and a knowingly worse result. Someone holding only a CSV cannot import; that is accepted.
- **Imported rows are never silently merged into an existing exercise by name similarity.** Mapping `Hammer Strength Row` onto `Barbell Row` would fuse two lifts' histories and corrupt progression for both. Unmatched names go to a mapping screen whose default action is to create a user exercise.
- **Every imported row points at an import batch**, which is what makes a repeat import detectable rather than duplicating, and undo exact.
