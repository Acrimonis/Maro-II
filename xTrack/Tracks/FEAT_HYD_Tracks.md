# Context Hydration — Tracks — 2026-11-01

**Last Bake:** 2026-11-01 18:53 UTC — written by `#bake` on `feature/rooouting`.

**Directive trace:** the code moved on the user's `#impl` with this feature's card plan picked; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read; none of the covered action classes stopped.

## State

The slow-water card plan shipped P0–P4: track and route cards show `Open water · 300M · Zones` (`SlowWaterRow`, `slowWaterCells`), a recording's figure computed by `TrackSlowWater.of` on its stored points (stops removed by the shared `isIdleStretch`) and kept on `TrackSummary` fields 24/25 behind `attachSlowWaterClassifier`, gated on `NavigationViewModel.slowWaterStamp` and re-marked by a regulation stamp; a route's figure is the plan's own, written at save on a settled search. The map's dashboard card reads the stored summary too. `apk-build.bat` green, 1 717 tests green; the Ask hop returned no blocker and six should-fix findings, open in the plan's `## Outcome`; P5 not taken; the device pass is owed. The 2026-09-15 walk level is still open.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — the catch-up under `writeMutex` (finding 1), the pass flag (nit)
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `slowWaterAt`'s full zone query per point (finding 2), the stamp missing `zones.properties` (finding 4)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the stored-summary lookup reads the filtered lists (finding 3)
- `xTrack/Tracks/261101_FEAT_PLN_Tracks_stops-and-slow-water.md` — the Outcome and its findings

## Next Step

The device pass: a dive trip's card summing to Nav and a freshly saved route's card summing to Total.
