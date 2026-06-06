# Feature: Coastline

**Status:** Active
**Created:** 2026-06-05
**Last Modified:** 2026-06-06
**One-liner:** Extend the coastline dataset to include isolated offshore point hazards (e.g. Phare de la Fourmigue) so the spatial engine no longer misses standalone rocks/turrets that lie off the continuous trait de côte.
**Active Subfeature:** none

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

**Decision (updated 2026-06-06):** Data source = **OSM seamarks via Overpass**
(`seamark:type` danger marks) — **not** the Shom WFS. The hard-coded `HazardSeeds`
were guessed coordinates (validation found 2 of 4 ~1.3 km off — Tradelière,
Chrétienne) and the Shom WFS client never got past a placeholder. OSM seamarks are
authoritative (fed by Shom/NGA charts — the Fourmigue node carries `ref:inspire` /
`source` tags), are already fetched for the coastline, and cover every real danger
in the zone. See subfeature **OsmSeamarkSource**.

### OsmSeamarkSource  [x]
Replace hard-coded `HazardSeeds` + the inert `ShomAtonClient` with isolated dangers
fetched from OSM seamarks in the **same Overpass call** as the coastline.
Live-validated 2026-06-06 (Overpass, Nice–Fréjus bbox): La Fourmigue
(`beacon_isolated_danger`) + 4 cardinal beacons (Batéguier, La Chrétienne, La
Vaquette, Les Moines) + the Pinassen wreck — the real dangers, exactly; harbour
lights & channel marks excluded. Emergent islets/rocks (the **Tradelière**) need no
seed — already OSM `natural=coastline` (way 4548150).

#### Todos
- [x] `SeamarkParser` (pure/testable): seamark node JSON → `List<PointHazard>`; keep only danger types {`beacon_isolated_danger`,`beacon_cardinal`,`rock`,`obstruction`,`wreck`} → HazardType/buffer; exclude lights + lateral/channel marks (the "no fake ones" filter).
- [x] Fold seamark nodes into the coastline Overpass query (`(way[natural=coastline]; node["seamark:type"~"…"];); out body geom;`) — one fetch, same endpoint race; split ways/nodes in `fetchFromEndpoint` (`OverpassData`).
- [x] `generate()` 7b: rings from parsed seamarks (clip to `LON_WEST..LON_EAST`, proximity-dedupe); drop the seed+WFS path.
- [x] Delete `HazardSeeds.kt` + `ShomAtonClient.kt`; remove `atonClient` ctor param, `mergeHazards`, `fetchAndBuildHazardRings`.
- [x] Tests: new `SeamarkParserTest` (danger filter incl. light/lateral exclusion + Fourmigue/Batéguier mapping); refactor integration test onto parsed fixtures; drop `mergeHazards` tests. `testDebugUnitTest` green + APK builds.
- [x] Source label → OpenSeaMap seamarks.
- [x] Live-validated (2026-06-06) the exact production query on Overpass: 363 coastline ways + 6 in-zone dangers (Fourmigue, Batéguier, Les Moines, Chrétienne, Vaquette, Pinassen wreck); 3 out-of-zone cardinals correctly lon-clipped. `testDebugUnitTest` BUILD SUCCESSFUL.
- [ ] On-device: rebuild «Côte» (full network regen — «Bande» alone won't refetch) and confirm the 6 dangers render at correct positions + the Tradelière now appears via `natural=coastline`.

## Todos
- [~] **SUPERSEDED → OsmSeamarkSource** — was: `GetCapabilities` discovery on Shom WFS. OSM seamarks chosen instead (they already carry Shom/NGA-sourced marks); `ShomAtonClient` deleted.
- [x] Build `fetchShomAton()` — WFS GetFeature, bbox = Nice–Fréjus, parse GeoJSON FeatureCollection → List<PointHazard>(lat, lon, type, name). → `ShomAtonClient.fetchHazards()`.
- [x] Define buffer-radius policy (type-dependent: LIGHT 15 m / BEACON 12 m / ISOLATED_DANGER 25 m / ROCK 20 m) — `HazardType` enum.
- [x] `bufferToRings()` — buffer each hazard into a closed 16-gon micro-circle ring with xM/yM + edge vectors (same refLat); appended to `islands`. → `CoastlineGenerator.buildHazardRing()` / `fetchAndBuildHazardRings()`.
- [x] Fold hazard rings into the Protobuf `.bin` cache (automatic — they live in `CoastlineData.islands`); degrade gracefully to coastline-only if Shom fetch fails (best-effort, swallowed).
- [x] ~~Hard-coded seed fallback (`HazardSeeds`)~~ **REMOVED → OsmSeamarkSource.** Validation proved 2 of 4 were ~1.3 km off (Tradelière, Chrétienne); replaced by live OSM seamark fetch. Tradelière needs no seed (already OSM `natural=coastline` way 4548150).
- [x] **Bug fix:** danger NW of Sainte-Marguerite was missing on the build (Fourmigue showed, this did not). Identified via OSM as the West-cardinal Batéguier/Jonquière shoal (`seamark:type=beacon_cardinal`, cat west, 43.52655,7.03046); added as `HazardSeeds.BATEGUIER` (ISOLATED_DANGER, 25 m).
- [x] Offline integration test `HazardSeedIntegrationTest` (all green): rings → spatial index sees coast (≈radius, island polylineIdx≥1), south-ray enclosure (centre odd=LAND, outside even=WATER), ring closed + radius accurate, bbox/clip; **+ named regression test for the Batéguier danger NW of Sainte-Marguerite, + `CoastlineGenerator.mergeHazards` dedup/offline-baseline (empty fetch ⇒ seeds only = the `atonClient = null` path)**. `testDebugUnitTest` green.
- [~] **SUPERSEDED → OsmSeamarkSource on-device todo.** (was: validate the hard-coded seeds on device — seeds now removed; the seamark-sourced dangers need the same full-regen "Côte" check.)
- [x] UI: renamed map buttons — "Régénérer la côte" → **"Côte"** ("Côte…" while loading), "Bande 300 m" → **"Bande"** (`MapScreen.kt`).
- [ ] ⚠️ Band may show a donut around hazards: Zone300 now uses signed distance assuming a winding; rings are wound CCW to match — confirm on device (separate from the south-ray `isOnWater`, which is correct for land classification).

## Rules
- Personal-use app — hazard data fetched at runtime / baked offline, not redistributed.

## Key Files
- `app/src/main/java/ykws/android/maro/data/coastline/SeamarkParser.kt` — **NEW.** Pure/testable OSM seamark node → `PointHazard`; danger-type filter (the "no fake ones" gate) + `DANGER_TYPES` (Overpass regex source). Replaces HazardSeeds + ShomAtonClient.
- `app/src/main/java/ykws/android/maro/data/model/PointHazard.kt` — Hazard model + `HazardType` radius policy.
- `app/src/main/java/ykws/android/maro/data/coastline/HazardRings.kt` — Pure point→closed-ring (micro-circle) builder, unit-testable.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt` — Overpass query fetches `(way[natural=coastline]; node[seamark:type~…])` in one call → `OverpassData`; `buildHazardSegments()` parses + lon-clips + dedupes seamarks into island rings in `generate()` 7b.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — cache-aside load + isOnWater ray-cast; consumes data unchanged.
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — grid index over all segments; auto-includes micro-circles (polylineIdx > 0 → island).
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt` — 300 m band builder; treats hazard rings as island barriers automatically.
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — map controls: "Côte" → full delete-cache + OSM refetch; "Bande" → band only (no refetch).
- `app/src/test/java/ykws/android/maro/data/coastline/SeamarkParserTest.kt` — **NEW.** Danger-type filter + light/lateral exclusion + Fourmigue/Batéguier mapping.
- `app/src/test/java/ykws/android/maro/data/coastline/SeamarkHazardIntegrationTest.kt` — **NEW.** Parsed seamarks → rings → spatial index (offline fixtures).
- **DELETED:** `HazardSeeds.kt`, `ShomAtonClient.kt`, `HazardSeedIntegrationTest.kt` (hardcoded seeds + placeholder WFS retired).
