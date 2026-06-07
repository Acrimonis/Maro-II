---
name: Coastline
status: active
created: 2026-06-05
modified: 2026-06-07
active_subfeature: none
subs_total: 2
subs_done: 2
one_liner: Fold isolated offshore point hazards (La Fourmigue, Batéguier, …) into the coastline land-set so the spatial index, water/land ray-cast and 300 m band see them — sourced from OSM seamarks via SeamarkParser.
---

# Feature: Coastline

**Description:**
The coastline pipeline ingests continuous land polygons (OSM `natural=coastline`
via Overpass → mainland polyline + island rings). Isolated offshore hazards stored
as *point* features — La Fourmigue turret (Golfe Juan), the cardinal-marked
Batéguier/Jonquière shoal (NW of Sainte-Marguerite), Basses de la Chrétienne (off
Agay), Sec de la Tradelière (Lérins) — never appear in those vectors, so the spatial
index, water/land ray-cast and the 300 m band all ignored them.

Solution (shipped on `develop`): fetch the danger `seamark:type=*` point nodes in the
**same Overpass call** as the coastline, parse them with **`SeamarkParser`**, buffer
each into a small closed micro-circle ring (`HazardRings`), and union them into the
island set so every downstream consumer (spatial index, `isOnWater`, `Zone300Builder`)
treats them as land/obstruction.

**Decision (2026-06-07):** Data source = **OSM seamarks via `SeamarkParser`**, NOT the
Shom Aton WFS. The earlier hard-coded `HazardSeeds` + unconfirmed `ShomAtonClient` WFS
were **retired** in the Zone300-with-isolated merge: OSM seamark nodes carry charted
positions (sourced from Shom/NGA charts), arrive in the existing Overpass call (no
second network client), and are pure/unit-testable. Only `DANGER_TYPES`
(`beacon_isolated_danger`, `beacon_cardinal`, `rock`, `obstruction`, `wreck`) are
kept; harbour lights / lateral channel marks are excluded.

## Subfeatures

### Fix Prebake  [x]
Make `apk-bake.bat` (`Zone300AssetBaker`) survive slow Overpass servers. The bake was
dying on a 10 s `SocketTimeoutException` because every raced endpoint needs longer than
10 s to compute the coastline + seamark bbox response.

#### Todos
- [x] Make `CoastlineGenerator`'s HTTP timeout injectable (`httpTimeoutSeconds`, default 10 s = runtime fail-fast unchanged); baker passes 180 s.
- [x] Ran a successful networked bake → 229 957-byte `nice-frejus.bin` written (2026-06-07; `:app:testDebugUnitTest --tests *Zone300AssetBaker* -Dmaro.bake=true`, BUILD SUCCESSFUL 1m31s).

#### Rules
- Keep the runtime default at 10 s (fast on-device fail-over to the bundled asset); only the offline bake waits longer.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt` — injectable `httpTimeoutSeconds` on the shared OkHttp client.
- `app/src/test/java/ykws/android/maro/data/coastline/Zone300AssetBaker.kt` — constructs the generator with the 180 s bake timeout.

### Robust Fetch  [x]
Make the Overpass fetch reliable end-to-end: retry the whole endpoint race with jittered
exponential back-off so a transient disruption (socket timeout, 5xx, 429, dropped connection)
is recovered from instead of aborting the bake.

#### Todos
- [x] Extracted a network-agnostic, unit-tested retry layer (`OverpassRetry.kt`): `raceEndpoints` (race-to-first-success), `fetchWithRetry` (bounded retry + injected back-off/clock), `exponentialBackoffMs` (full jitter, honours `Retry-After`), `isRetryableOverpassError` (408/425/429/5xx + IOException + truncated-response retry; other 4xx fatal).
- [x] `CoastlineGenerator.fetchOverpass` delegates to it; `fetchFromEndpoint` throws typed `OverpassHttpException` (carries code + `Retry-After`). Runtime default `maxFetchAttempts = 1` (fail-fast unchanged); baker uses 4 attempts + 180 s.
- [x] 15 deterministic unit tests green (`OverpassRetryTest`); live bake green via the new path (2026-06-07, 58 s, 229 957-byte asset).

#### Rules
- Keep the retry/back-off policy network-agnostic (inject the HTTP call + the delay clock) so it stays unit-testable without a socket.
- On-device path stays fail-fast (1 attempt); only the offline bake retries.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/coastline/OverpassRetry.kt` — race + bounded retry + back-off + error classification (pure; injected I/O).
- `app/src/test/java/ykws/android/maro/data/coastline/OverpassRetryTest.kt` — 15 deterministic tests for the policy.

## Todos
- [x] `SeamarkParser` — OSM `seamark:type` nodes → `List<PointHazard>` (danger types only, harbour lights excluded); pure, unit-tested (`SeamarkParserTest`).
- [x] `CoastlineGenerator.buildHazardSegments()` — parse seamark nodes (fetched in the same Overpass call) → `dedupeByProximity` → `HazardRings.toSegment` → unioned into `islands`.
- [x] `HazardRings` — buffer each hazard into a closed 16-gon micro-circle (type-dependent radius via `HazardType`).
- [x] Offline IT `SeamarkHazardIntegrationTest` (green): Fourmigue + Batéguier + Chrétienne become island rings the spatial index sees; harbour light excluded; ring closed/radius; south-ray enclosure; bbox/clip; Batéguier-NW-of-Sainte-Marguerite regression.
- [x] **Retired the HazardSeeds/Aton path** — deleted `HazardSeeds.kt`, `ShomAtonClient.kt`, `HazardSeedIntegrationTest.kt`; ported `queryColumn` + `ColumnCandidate` into `CoastlineSpatialIndex` so the gated `CoastlinePrebakeTest` still compiles.
- [x] Merged to **develop** (`fcf0019`, pushed) — `testDebugUnitTest` + `assembleDebug` green; 43 MB debug APK builds. WIP parked at `wip/seamark-water-oracle`.
- [x] ✅ **Bake fixed (Overpass SocketTimeout)** — `Zone300AssetBaker` now drives `CoastlineGenerator` with an injectable 180 s timeout (runtime path stays 10 s fail-fast); bake ran green (2026-06-07), so the seamark hazards are now in the baked `data/app-assets/coastlines/nice-frejus.bin`. Remaining: build into the APK + on-device verify.
- [~] Validate on device: Fourmigue/Batéguier/Tradelière/Chrétienne render + trip the 300 m / water-land readout (needs a successful networked bake first).
- [ ] ⚠️ Band donut around hazards: Zone300 uses signed distance assuming a winding; rings wound CCW to match — confirm on device.

## Rules
- Personal-use app — hazard data baked offline / fetched at runtime, not redistributed.
- Hazards come from the **coastline Overpass call** (no second network client); `SeamarkParser` is pure (no network/Android) → unit-testable.

## Key Files
- `app/src/main/java/ykws/android/maro/data/coastline/SeamarkParser.kt` — OSM seamark nodes → `List<PointHazard>` (danger types; `classify`/`DANGER_TYPES`).
- `app/src/main/java/ykws/android/maro/data/model/PointHazard.kt` — hazard model + `HazardType` radius policy.
- `app/src/main/java/ykws/android/maro/data/coastline/HazardRings.kt` — point → closed micro-circle ring (pure).
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt` — `buildHazardSegments()` (+ `dedupeByProximity`); seamark nodes fetched in the same Overpass call, merged into `islands`; resilient fetch via injectable `httpTimeoutSeconds` + `maxFetchAttempts`.
- `app/src/main/java/ykws/android/maro/data/coastline/OverpassRetry.kt` — resilient Overpass fetch: race-to-first-success + bounded retry with jittered exponential back-off (`fetchWithRetry`/`raceEndpoints`/`exponentialBackoffMs`); typed `OverpassHttpException`.
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — grid index; auto-includes micro-circles (island); `query` + `queryColumn` (ported for the prebake).
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt` — 300 m band; treats hazard rings as island barriers.
- `app/src/test/java/ykws/android/maro/data/coastline/{SeamarkHazardIntegrationTest,SeamarkParserTest}.kt` — coverage.
- `app/src/test/java/ykws/android/maro/data/coastline/Zone300AssetBaker.kt` — bakes coastline + 300 m band into `assets/coastline/nice-frejus.bin` (needs Overpass network; currently failing).
- `apk-bake.bat` — runs the asset baker.
