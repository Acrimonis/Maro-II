---
name: isOnWaterAgain
status: done
created: 2026-06-07
modified: 2026-06-08
active_subfeature: none
---

# Feature: isOnWaterAgain

**Description:**
`isOnWater` occasionally returns the inverted water/land result, most often for points just
south (seaward) of a small near-vertical coastline segment. Root cause: the per-segment
crossing test `rayCrossesSegmentNorth` computes the intersection latitude by dividing by
`dLon` (longitude span of the segment); for a near-vertical segment `dLon → 0`, so the
division amplifies floating-point error and flips the even-odd parity. The fix rewrites that
test to be division-free (cross-product / orientation comparison) — same vertical-north ray,
same cap-closure architecture, same half-open vertex de-dup rule. Verification uses a
temporary `isWaterDbg` Logcat line driven by demo-mode panning to capture concrete failing
`lat/lon`, which become permanent regression cases.

## Subfeatures

### 300M Zone Rendering  [x]

300 m band rendering. **Spikes fixed**; a separate **pre-existing pinch** artifact remains (deferred).

#### Todos
- [x] `capOpenEnds` seals only region-clipped ends → the Golfe-Juan / Cap d'Antibes **spikes are gone** (confirmed visually + the `BandValidationTest` no-spike guard).
- [x] In-app builders + the offline prebake use `index.usableSegments` + `index.isWater`; rebaked `nice-frejus.bin`.
- [x] **Band PINCH — FIXED** in the `300m-pinch` subfeature (seaward-line distance filter in `Zone300Builder`). Original diagnosis kept for the record: Port breakwaters are traced as short near-closed loops (~5–53 m end-gap, `isClosed` flag set) the cap logic never handled (old or new build). The band contour penetrates the gap (classified *seaward* because the gap cell isn't flagged `land`), pinching the red line in to the coast. Diagnosed: 16 interior open coastline ends ↔ 73 band pinch vertices (e.g. boat-bay loop at 43.4523,6.9219). Tried sealing the flood gap (no effect) and dropping the loops from the band geometry (worse — 106) — both wrong levers; **reverted to the clean spike-fixed state.** Proper fix: **close these near-closed loops in `CoastlineGenerator`** (data quality — also tidies `isWater` there); OR a careful change to the seaward classification, which sits next to the documented Zone300 **land-mirror** regression (high risk — guard with island-present tests first).

#### Rules
- The band fill/lines derive from `index.isWater` + the **cleaned** coastline; never pass raw `allSegments` to `Zone300Builder`.

#### Key Files
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — band rendering (changed on develop)

### 300m-pinch  [x]

Marina **band pinch** (split out of `300M Zone Rendering`). The red **seaward** line dipped to 7–45 m from the coast at marinas — the 300 m band leaked into harbour channels. **v2 fix (shipped): filter the seaward line by distance-to-coast.** A genuine seaward vertex lies on the bandM isodistance contour (≈300 m from shore by construction); a pinch is a few tens of m. Drop any seaward vertex closer than `bandM/2` — robust because the two populations sit ~250 m apart with an empty gap between. Pure output filter; never touches the flood-fill or seaward classification, so zero land-mirror exposure. Fixes every marina uniformly. Done 2026-06-08.

#### Todos
- [x] `dropPinchedSeawardRuns(lines, minDistM, distToCoast)` top-level helper in `Zone300Builder.kt`; called in `build()` on `mergeLines(seaward)` with `bandM/2`. Filters within each line (bridges the red line across the harbour mouth).
- [x] Unit tests (pure, no asset): dip removed / clean line kept / fully-pinched line dropped (`Zone300BuilderTest`, 9 green incl. land-mirror).
- [x] Rebaked `nice-frejus.bin` (`CoastlinePrebakeTest -Dmaro.prebake=true`).
- [x] **Global** guard: NO seaward vertex anywhere < `bandM/2` from coast, + `>50` count (not over-clipped) — `BandValidationTest` green on the rebaked asset.
- [x] Regression green: `Zone300BuilderTest` (land-mirror invariants), `SpatialOperationsTest` (25), `CoastlineSpatialIndexWaterTest` (10).
- [x] **Reverted v1** (close-near-closed-loops in `CoastlineGenerator`): diagnostic proved residual pinches sat beside *already-closed* structures, so loop-closure was the wrong lever. `git checkout` of the 3 v1 files.
- [x] **Deployed + visually confirmed on device** (Pixel 7). Baked the app's real asset (`Zone300AssetBaker -Dmaro.bake=true` → gitignored `data/app-assets/coastlines/nice-frejus.bin`), `assembleDebug`, `adb install -r`, relaunch → screenshot shows the red line offshore, no pinch. Fresh bake's newer timestamp superseded the on-device cache (no data wipe).
- [x] **Footgun fixed:** `apk-build.bat`'s coastline prebake ran `CoastlinePrebakeTest` (committed *singular* `assets/coastline/`, which the app does NOT read) → green test but unchanged device. Repointed it to `Zone300AssetBaker` (the app's *plural* `coastlines/` path) + corrected the stale docstrings.
- [ ] **FOLLOW-UP (deliberate, not done here):** two parallel asset paths remain — committed `coastline/` (singular, `CoastlinePrebakeTest`, test fixture) vs gitignored `coastlines/` (plural, `Zone300AssetBaker`, what the app loads). Full unification (one baker / one path) touches the *adopted* prebake pattern + [[zone300-land-mirror]]-adjacent DepthMapping and risks a duplicate-asset build error — decide separately.

#### Rules
- Do **not** touch the seaward classification / `markSeaComponents` (the twice-regressed [[zone300-land-mirror]] zone). The pinch fix is a pure **output** filter on the red line.
- Threshold = `bandM/2`, derived not magic: real seaward vertices ≈ `bandM`, pinches ≪ that; any cut in the wide gap works.
- The app loads `assets/`**`coastlines`**`/` (plural, gitignored `data/app-assets/`, baked by `Zone300AssetBaker` / `apk-bake.bat`). The committed `assets/coastline/` (singular) is a **test fixture only** — baking it does NOT change the device.

#### Key Files
- `app/src/main/java/ykws/android/maro/spatial/Zone300Builder.kt` — `dropPinchedSeawardRuns` + call in `build()`.
- `app/src/test/java/ykws/android/maro/spatial/Zone300BuilderTest.kt` — filter unit tests.
- `app/src/test/java/ykws/android/maro/data/prebake/BandValidationTest.kt` — global pinch guard.

## Todos
- [x] Division-free crossing test (necessary baseline; insufficient alone).
- [x] Diagnose: residual errors are tiny coastline DATA artifacts at marinas (seg4323 13 m spike, degenerate & CW rings), not a classifier bug.
- [x] **Final classifier** — `isWater` = mainland-primary hybrid: nearest-MAINLAND side test (`signedSide`/`classifyWater`/`cornerWater`) OR inside a real CCW island (`insideRealIsland`, bbox-filtered PNPOLY).
- [x] Index cleaning: drop degenerate rings (≤3 pts / ~0 area) + tiny open fragments (<30 m); expose `usableSegments`.
- [x] Band: in-app builders + the offline prebake now use `index.isWater` + `index.usableSegments`; `capOpenEnds` seals only region-clipped ends (no interior-junction spikes).
- [x] Rebake the bundled asset `app/src/main/assets/coastline/nice-frejus.bin` with the new logic.
- [x] Tests: signedSide; convex/reflex corner; tiny-fragment / degenerate-ring / CW-basin isWater; oracle sweep; `BandValidationTest` (no-spike guard on the real asset). All green.
- [x] Strip all temporary diagnostics.
- [ ] (Deferred / optional) Clean the spikes upstream in the coastline generator too; on-structure (<30 m) points like #4 remain inherently ambiguous.

## Rules
- Approach pivoted to **Option 2 (nearest-segment side test)** — the vertical-ray + cap containment is structurally degenerate against near-vertical coast (confirmed via segment dump). The division-free ray fix is kept as a baseline but is insufficient alone.
- Preserve the half-open longitude straddle (`<=` lower, strict `<` upper) — it de-dups shared vertices.
- The division-free test must stay algebraically identical to the old `< rayLatStart` / `<= rayLatEnd` rules.
- Anomalies manifest as narrow, typically vertical bands a few metres wide in longitude — nudging slightly east/west gives the correct result. Signature of the vertical counting-ray degenerating against near-vertical coastline (breakwaters, piers, steep capes).

## Key Files
- `app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt` — `rayCrossesSegmentNorth` (the fix).
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — `isWater` containment caller.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — `isOnWater` (debug log spot).
- `app/src/test/java/ykws/android/maro/spatial/SpatialOperationsTest.kt` — ray unit tests.
- `app/src/test/java/ykws/android/maro/spatial/CoastlineSpatialIndexWaterTest.kt` — oracle sweep / regressions.

## Docs
- `xTrack/isOnWaterAgain/FEAT_DOC_isOnWaterAgain_nearest-segment-design.md` — Option 2 design + implementation plan (nearest-segment side test).
