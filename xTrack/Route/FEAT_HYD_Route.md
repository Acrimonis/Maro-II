# Context Hydration — Route — 2026-11-01

**Last Bake:** 2026-11-01 18:53 UTC — written by `#bake` on `feature/rooouting`.

**Directive trace:** no dependency was added, no binary file opened and the device not touched; the code moved on the user's `#impl` with the Tracks card plan picked (*"Tracks card plan, P0 on"*), P0–P4 implemented and built, the `ask` hop reviewing once; a later *"add the row on route cards also"* was answered with a question the user declined, nothing written on it; no git write ran before this bake's `#commit`; none of the covered action classes stopped.

## State

`feature/rooouting` ships Evolutive + as the default engine and P2's tests. The route's published slow-water table now counts every second on water with a limit — every corridor ring, priced or not, the zone over the band through `SlowWater.of` — while `rungSlowWater`'s cost keeps the priced slow-legs-only walk (`slowLegsOnly`), so the crown is unmoved; a settled route's figure is saved on its track for the cards' `Open water · 300M · Zones` row (owned by Tracks). `apk-build.bat` green, the suite at 1 717 tests, 27 skipped, 0 failures. Six open tasks: any-angle P3/P5, the parked boxed end and swap-ends, the channel after its device pass, the multi-route save, the provisional mark.

## Target Files

- `xTrack/Route/261029_FEAT_PLN_Route_any-angle-search.md` — P3 (one pricing for steps and chords, the `bound`/`full`/`reopen` knobs) and P5
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `rungSlowWater`: the table's second `LegWater` re-reads the band per leg (review finding 5) and its doc still states the old meaning (finding 6)
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — `slowTimeByLimit` and `slowLegsOnly`
- `xTrack/Tracks/261101_FEAT_PLN_Tracks_stops-and-slow-water.md` — the Ask hop's open findings in `## Outcome`

## Next Step

The any-angle plan's P3.
