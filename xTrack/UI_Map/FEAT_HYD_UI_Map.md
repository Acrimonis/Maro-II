# UI_Map — Hydration Snapshot

**Baked:** 2026-06-20 09:56 UTC+2

## Active State
- **Subfeature:** decenter-map
- **Branch:** feature/map-migration

## What Changed This Session
1. **decenter-map subfeature created** — new subfeature for dynamic downward offset of map centre
2. **osmdroid API investigation** — confirmed no viewport offset or tilt API exists in osmdroid 6.1.18
3. **Full osmdroid usage audit** — all osmdroid APIs confined to `MapScreen.kt` (~3993 lines)
4. **Library upgrade research** — evaluated Google Maps SDK, Mapbox GL, MapLibre GL
5. **MapLibre GL deep-dive** — `CameraOptions.padding` solves decenter natively, `pitch` adds tilt. Zero wasted tiles.
6. **Migration plan written** — `docs/map-lib-migration-plan.md` with 13-step incremental migration, all design decisions confirmed

## Design Decisions
- OSM raster tiles (same visual, same perf)
- Skip offline MBTiles for now
- Depth bitmap unchanged (banding hack eliminated by MapLibre)
- Tilt: OFF default, 3-state (OFF/MANUAL/AUTOMATIC), manual has slider with live preview
- Decenter: OFF default, 25% max, ramp 5–15 kn
- Both GPS and demo mode
- Raw `AndroidView(MapView)` wrapper

## Target Files
- `docs/map-lib-migration-plan.md` — full migration plan
- `app/build.gradle.kts` — add MapLibre dependency
- `gradle/libs.versions.toml` — add MapLibre version
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — all migration changes
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — tilt/decenter settings fields

## Next Steps
- Switch to Ask mode for full plan review
- Then implement Step 1: MapLibre dependency + `MapLibreMapView` composable
