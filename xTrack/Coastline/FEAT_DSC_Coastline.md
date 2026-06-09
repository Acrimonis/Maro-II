---
name: Coastline
status: active
created: 2026-06-05 00:00
modified: 2026-06-05 00:00
active_subfeature: none
---

# Feature: Coastline

**Description:**
The current coastline pipeline ingests only continuous land polygons (OSM
`natural=coastline` via Overpass → mainland polyline + island rings). Isolated
offshore hazards that are stored as *point* features — the Phare de la Fourmigue
turret in Golfe Juan, the Basses de la Chrétienne off Agay, the Sec de la
Tradelière near the Lérins — never appear in the line/polygon vectors, so the
spatial index, water/land ray-cast, and the 300 m band all ignore them.

Goal: ingest these point hazards (candidate source: Shom Aton / Aids-to-Navigation
WFS, or OSM `seamark:*` / `man_made=lighthouse`), buffer each into a small circle
("micro-circle"), and union them into the land-mass set so they are treated as
land/obstruction by every downstream consumer (spatial index, isOnWater,
Zone300Builder).

## Subfeatures

**Decision:** Data source = **Shom Aton WFS** (Aids-to-Navigation / danger_isolé).
No existing WFS client in repo (depth path uses WCS raster, not WFS) — build a
small WFS GetFeature client; reuse Overpass HTTP plumbing from CoastlineGenerator.

## Todos
- [ ] Discovery: `GetCapabilities` on Shom WFS to confirm the real Aton/danger_isolé typeName + whether `outputFormat=application/json` is supported. **(blocker for live data — `DEFAULT_TYPE_NAMES`/`DEFAULT_BASE_URL` in ShomAtonClient are placeholders)**
- [x] Build `fetchShomAton()` — WFS GetFeature, bbox = Nice–Fréjus, parse GeoJSON FeatureCollection → List<PointHazard>(lat, lon, type, name). → `ShomAtonClient.fetchHazards()`.
- [x] Define buffer-radius policy (type-dependent: LIGHT 15 m / BEACON 12 m / ISOLATED_DANGER 25 m / ROCK 20 m) — `HazardType` enum.
- [x] `bufferToRings()` — buffer each hazard into a closed 16-gon micro-circle ring with xM/yM + edge vectors (same refLat); appended to `islands`. → `CoastlineGenerator.buildHazardRing()` / `fetchAndBuildHazardRings()`.
- [x] Fold hazard rings into the Protobuf `.bin` cache (automatic — they live in `CoastlineData.islands`); degrade gracefully to coastline-only if Shom fetch fails (best-effort, swallowed).
- [x] Hard-coded seed fallback (`HazardSeeds`): Fourmigue (43.5400, 7.0833), Basses de la Chrétienne (43.4272, 6.9097), Sec de la Tradelière (43.5240, 7.0610), **Plateau du Batéguier (43.52655, 7.03046)** — always merged; WFS deduped against them (80 m).
- [x] **Bug fix:** danger NW of Sainte-Marguerite was missing on the build (Fourmigue showed, this did not). Identified via OSM as the West-cardinal Batéguier/Jonquière shoal (`seamark:type=beacon_cardinal`, cat west, 43.52655,7.03046); added as `HazardSeeds.BATEGUIER` (ISOLATED_DANGER, 25 m).
- [x] Offline integration test `HazardSeedIntegrationTest` (all green): rings → spatial index sees coast (≈radius, island polylineIdx≥1), south-ray enclosure (centre odd=LAND, outside even=WATER), ring closed + radius accurate, bbox/clip; **+ named regression test for the Batéguier danger NW of Sainte-Marguerite, + `CoastlineGenerator.mergeHazards` dedup/offline-baseline (empty fetch ⇒ seeds only = the `atonClient = null` path)**. `testDebugUnitTest` green.
- [~] Validate on device (APK built & ready: `app/build/outputs/apk/debug/app-debug.apk`, user testing): confirm Fourmigue/Batéguier/Tradelière/Chrétienne render + trip the 300 m / water-land readout. **NB: must tap "Côte" (full delete-cache + OSM refetch re-merges seeds); "Bande" only rebuilds the band from cached segments and won't pull in new seeds. Full regen needs network.**
- [x] UI: renamed map buttons — "Régénérer la côte" → **"Côte"** ("Côte…" while loading), "Bande 300 m" → **"Bande"** (`MapScreen.kt`).
- [ ] ⚠️ Band may show a donut around hazards: Zone300 now uses signed distance assuming a winding; rings are wound CCW to match — confirm on device (separate from the south-ray `isOnWater`, which is correct for land classification).

## Rules
- Personal-use app — hazard data fetched at runtime / baked offline, not redistributed.

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/PointHazard.kt` — NEW. Hazard model + `HazardType` radius policy.
- `app/src/main/java/ykws/android/maro/data/coastline/HazardSeeds.kt` — NEW. Hard-coded fallback hazards (always merged).
- `app/src/main/java/ykws/android/maro/data/coastline/HazardRings.kt` — NEW. Pure point→closed-ring (micro-circle) builder, unit-testable.
- `app/src/test/java/ykws/android/maro/data/coastline/HazardSeedIntegrationTest.kt` — NEW. Offline IT proving seeds become land the engine sees.
- `app/src/main/java/ykws/android/maro/data/coastline/ShomAtonClient.kt` — NEW. WFS GetFeature → GeoJSON → List<PointHazard>; best-effort, never throws. Endpoint/typeName placeholders pending GetCapabilities.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt` — `fetchAndBuildHazardRings()` + `buildHazardRing()`; hazards merged into `islands` in `generate()` (step 7b) using the coastline's refLat.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — cache-aside load + isOnWater ray-cast; consumes merged data unchanged.
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — grid index over all segments; auto-includes micro-circles (polylineIdx > 0 → island).
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt` — 300 m band builder; treats hazard rings as island barriers automatically.
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — map controls: "Côte" button → `viewModel.loadCoastline()` → `refreshCoastline()` (delete cache + OSM refetch, re-merges hazards); "Bande" → `regenerateBand()` (band only, no refetch).

## Docs
