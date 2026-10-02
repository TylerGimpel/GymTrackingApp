# MuscleMap snapshot

The muscle diagram's path data, vendored from [Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap)
at commit `170cfacda79376dd59ac0cf2f7dfab8ff9f4443a` (MIT, see `LICENSE`; where the paths came from is in
`ASSET_PROVENANCE.md`). Chosen in [#132](https://github.com/TylerGimpel/GymTrackingApp/issues/132)
and first drawn by the Routine tab (#146).

Only the male front and back bodies are kept, and none of the reference photos. `to_json.py` turns
them into `app/src/main/assets/musclemap/{front,back}.json`, which is what the app reads. A snapshot
rather than a dependency, so a change upstream can never move anything here without a commit.

MuscleMap draws no neck. The bank's `neck` is lit with the traps it moves with.
