# Context Hydration — Route — 2026-09-25

**Last Bake:** 2026-09-25 18:28 UTC — written by `#bake`

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code came from a file read or an Ask hop's output. Delegations are named: the band tangent and phase 4 implementations ran through `new_task(code)` subtasks, the reviews through `new_task(ask)`, and the plan and feature-file edits landed in Architect (`.md`-only).

## State

Stage 1 (avoid land, islands and hazard rings) is shipped. Phases 2–4 shipped: the 3 m depth gate with its `route.avoid.depthGate.enabled` switch; the 300 m band as a priced soft source with its `route.avoid.zone300.enabled` switch, the field's one soft-price home, and the band tangent look-ahead that chords a concave bay instead of diving it; and the speed-limit zones as priced, excludable soft sources — even-odd fill, strictest-limit cost `cellM × (pace/limit − 1) × softCostAversion`, zone-aware ETA with leg splitting and `forcedCrossingZoneNames`. The keys are normalized to the dot taxonomy (`route.avoid.obstacle.marginM` · `grid.cellM` · `corridor.reachM` · `depthGate.minM` · `zone300.marginM` · `speedZone.softCostAversion`). Remaining: Change 4 (coarse-to-fine precision) — the 50 m grid quantizes a curved coast into long chords, which the device export showed — then markers (phase 5) and curves (phase 6).

## Target Files

- `xTrack/Route/260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md` — the canonical phases 2–6 plan, carrying the amendments, the challenge findings and the normalized keys
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` · `spatial/avoid/` (world, grid, search, pull, TangentCorners, RouteCostField, RouteEta, ZoneGeometry) — the shipped internals
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — the keys
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` · `ui/map/MapScreen.kt` · `ui/map/NavigationViewModel.kt` — the excluded-zone persistence and the live wiring

## Next Step

Implement Change 4 — coarse-to-fine precision: a fine grid over only the coarse path's cells, so the line hugs the curved band instead of long chords.
