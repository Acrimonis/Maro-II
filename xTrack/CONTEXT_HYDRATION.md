# Context Hydration — 2026-06-06

**Active Feature:** Zone300
**Active Subfeature:** drawZone
**Next-session goal:** confirm the land-mirror fix on device (regen the band), then **bake the band at build time and bundle it**.

## State — TWO-layer land-mirror fix in code, not yet confirmed on device
Two fixes landed this session:
1. **Classifier:** replaced the open-polyline 6 NM **south-ray** with **closed-polygon containment**
   in `CoastlineSpatialIndex.isWater` (even-odd **north-ray** + inland cap; winding-independent).
   `CoastlineRepository.isOnWater` keeps the >6 NM short-circuit and delegates.
2. **Band builder (the one that actually killed the mirror):** on-device regen after fix #1 STILL
   showed two complete bands. Root cause — `Zone300Builder` took the final mask from the **flood**
   (`seaComp`), which bleeds past a barrier gap onto land, and **never re-applied** the per-cell
   water test. FIX: `mask = seaConnected && WATER && NEAR` (gate the band by the WATER bit).
Deleted dead orientation code; added `SpatialOperations.rayCrossesSegmentNorth`. Rewrote the
obsolete "flood recovers misclassified water" test → "band never covers a cell the water test calls
land". Full `testDebugUnitTest` = **104 green**. Uncommitted; branch `feature/300M-Claude-II`.

⚠️ **The band is CACHED** (protobuf) — fixes only show after rebuilding the APK and regenerating
the band ("Bande 300 m"). NOT yet confirmed on device after fix #2.

## Next steps (in order)
1. **Confirm the fix on device:** `apk-build.bat` (build APK with the fix) → install → tap
   **"Bande 300 m"** (in-app `regenerateBand`, no OSM refetch) → the **land band must disappear**.
   If it persists → cause is the band builder/fill (`Zone300Builder`), not the classifier.
2. **Build-time bake + bundle — PLUMBING DONE; run the baker (online).** `Zone300AssetBaker`
   (@Ignore JUnit) fetches OSM + builds the band (same containment `isWater`/cell size as prod) +
   serializes to `app/src/main/assets/coastlines/nice-frejus.bin`. `loadCoastline` now prefers
   disk cache → **bundled asset** → live fetch. To ship correct data with no on-device regen:
   `:app:testDebugUnitTest --tests "*Zone300AssetBaker*"` (networked) → commit the `.bin` →
   `apk-build.bat`. Couldn't run the baker here (no network).

## Target files
- `data/coastline/CoastlineRepository.kt` — `loadCoastline` (add bundled-asset path); `isOnWater` (done).
- `data/coastline/CoastlineGenerator.kt` / `CoastlineSerializer.kt` — reuse for the baker.
- `spatial/CoastlineSpatialIndex.kt` — `isWater` containment (done).

## Don't redo / gotchas
- Classifier fix is done + unit-guarded (`CoastlineSpatialIndexWaterTest`, incl. fragmented coast).
  `Zone300WaterOracleHarness` (@Ignore) = online EMODnet check. Topology auto-logs each band build.
- Memory: `zone300-land-mirror.md`. Don't claim fixed until the on-device **regen** is verified.
