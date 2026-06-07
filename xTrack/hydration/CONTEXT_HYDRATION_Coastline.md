# Context Hydration — Coastline — 2026-06-07

**Active Subfeature:** none

## State
Both Subfeatures **Fix Prebake** and **Robust Fetch** are complete (`[x]`).
**Overpass SocketTimeout blocker is resolved** — `CoastlineGenerator` now accepts
injectable `httpTimeoutSeconds` (runtime 10 s fail-fast, bake 180 s) and delegates
to `OverpassRetry` (race-to-first-success + bounded jittered exponential back-off;
bake uses 4 attempts). A successful networked bake ran green on 2026-06-07,
producing a 229_957-byte `nice-frejus.bin` containing seamark hazards (Fourmigue,
Batéguier, Chrétienne, Tradelière) as micro-circle rings. The `HazardSeeds`/`ShomAtonClient`
path is retired; source is OSM `seamark:type` nodes via `SeamarkParser` inside the
coastline Overpass call. Everything merged to `develop` (`fcf0019`, pushed).

**Remaining:**
1. On-device verification — Fourmigue/Batéguier/Tradelière/Chrétienne render + trip
   the 300 m / water-land readout (needs a successfully-baked APK).
2. Band donut around hazards — confirm Zone300 signed-distance winding matches the
   CCW-wound hazard rings.

## Target Files
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt` — injectable timeout + `buildHazardSegments()`.
- `app/src/main/java/ykws/android/maro/data/coastline/OverpassRetry.kt` — retry/back-off layer.
- `app/src/main/java/ykws/android/maro/data/coastline/SeamarkParser.kt` — OSM → `PointHazard`.
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — grid index with `queryColumn`.
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt` — 300 m band.
- `data/app-assets/coastlines/nice-frejus.bin` — baked asset.

## Next Step
Build the APK with the current baked asset, deploy to device, and visually confirm
that seamark hazards render correctly and the 300 m band / water-land readout trips
appropriately. Then address the band-donut winding check.
