# Context Hydration — Tracks — 2026-11-11

**Last Bake:** 2026-11-11 08:58 UTC — written by `#bake` on `feature/tracks-slow-water`.

**Directive trace:** the code moved on the user's `#impl`; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read; none of the covered action classes stopped.

## State

The slow-water card plan's Ask findings are fixed and green (`apk-build.bat`, 1 770 tests): the catch-up that fills a recording's `Open water · 300M · Zones` figure walks off the recorder's write lock and merges by compare-and-set, a pass is owed whenever the attached source differs from the one the last pass completed under, the walk reads zones through a box-rejected containment read whose stamp and test arrive as one `slowWaterAttachment`, the map's dashboard card reads the unfiltered summaries, and the route table reads the band once per leg. Findings 4, 7b–d and 8 stand dropped; the `zones.properties` and coastline stamp gaps stay named. Three nits are open in the plan's Outcome. The auto-start work (step 1 shipped, step 2 parked) is untouched, and the 2026-09-15 walk level in the feature file is still open.

## Target Files

- `xTrack/Tracks/261101_FEAT_PLN_Tracks_stops-and-slow-water.md` — the plan; `## Remediation` holds the triage, R1–R5, both reviews and the Outcome
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — `listTracks`, `attributeSlowWater`, `slowWaterPassDoneFor`
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `slowWaterAttachment`, `slowWaterAt`, the publication order
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the dashboard's stored-summary lookup
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — `LegWater.withZone`

## Next Step

The device pass is the user's: a filtered-out recording opened from the map keeps its row, and a first launch after a regulation bake while recording checkpoints without a stall.
