# Test fixtures held outside the repo

The FitNotes importer has a real test fixture, and it is **deliberately not in this repository**.

## What it is

A `.fitnotes` SQLite backup and its companion CSV export, covering **2,234 sets across 112 training days, February 2024 to April 2026**. Its shape is documented in [`research/fitnotes-export-actual.md`](research/fitnotes-export-actual.md) — schema, parsing traps, and the two predictions it disproved.

## Why it is not committed

It is real personal training data. This repository is private today, but a repository can be made public and **git history cannot easily be unmade** — so the file stays out entirely rather than relying on the repo's visibility never changing. `.gitignore` blocks `*.fitnotes` and `FitNotes_Export_*.csv` anywhere in the tree as a second line of defence.

## Where it lives

`C:\Users\tyler\Documents\GymTrackingApp\fixtures\`

Kept there rather than in `Downloads` (which gets emptied) or inside the working tree (where `git clean -xdf` would remove it, ignored files included).

Note that this is one of only two copies of that history, the other being the phone. Whatever backs up `Documents` is what protects it — which is the same problem this app's own export feature exists to solve.

## What it is for

Building and testing the importer. The research document records what was verified against it, but the file itself answers questions the document cannot anticipate — and several of its findings were only possible because the real file existed rather than a guessed shape.
