# Feature: Zone300

**Status:** Active
**Created:** 2026-06-03T13:00:00.000Z
**Last Modified:** 2026-06-06
**Active Subfeature:** drawZone
**Description:**
Identify and render a regulatory band 300 m from the coastline (all coasts,
islands included) within which a 5-knot speed limit applies. The app already
computes distance-to-coast; this feature derives the 300 m boundary from that
and makes the zone visually identifiable on the map.

**One-liner:** Render the 300 m regulatory 5-knot speed-limit band along all coastlines.

## Subfeatures
### drawZone  [x]
Derive, draw, and query the 300 m band — geometry, APIs, rendering. (Consolidates
the former empty `trace` stub, the `drawZone` design notes, and the `300mDesign`
implementation plan.)

#### Todos
- [x] chaikin() smoothing + marchingSquares() contour (+ tests)
- [x] Coastal-strip mask sampler (0–500 m ribbon, 15 m grid) + Zone300Data model
- [x] groupRings() fill holes (+ small-hole drop for noise)
- [x] Band cache (protobuf, cache-aside in loadCoastline)
- [x] isIn300mZone() / distanceTo300mZone() repo APIs (+ tests)
- [x] ViewModel: zone300 + zone-status flows
- [x] drawZone300() + overlay lifecycle + zoom gate (11)
- [x] Dashboard label + "Bande 300 m" fast-rebuild button (with progress)
- [x] Fix red-line breaks — out-cell seaward classification (robust in narrow bands)
- [x] Fix fill chords / dark overlaps — simple per-vertex polygon (no sub-path stitch)
- [x] Fix harbour/bay holes — **flood-fill water mask + end-caps (current approach)**
- [x] Tried signed-distance band → **REVERTED**: mainland orientation unreliable (OSM stitching reverses segments) → band inverted onto land. Orientation methods need a generator orientation fix first. Cleanup (signedDistance/audit) deleted.
- [x] Final on-device visual pass — **validated on device**: water-side only, islands donut, no land-side mirror. Fixed by keeping only the open-sea flood component anchored at the deepest-water cell (`Zone300Builder.markSeaComponents`) — inland pockets fed by ray-cast misclassification are separate components and get dropped. Zone300 band done. **(Refined 2026-06-06 — see next.)**
- [x] **Isolated-hazard band fix (2026-06-06):** far-offshore closed rings (e.g. La Fourmigue, ~2.8 km out) got **no** 300 m band — the deepest-water anchor filter kept only the anchor's flood component, and an isolated islet's ≤500 m ribbon is a *disconnected* component. Fix: `markSeaComponents` re-includes each *isolated* closed ring's own sea component; inland mainland pockets (not closed rings) stay pruned. Regression test in `Zone300BuilderTest` (big island holds the anchor + far small ring still banded). `testDebugUnitTest` green.
- [ ] ⚠️ **Land-mirror — ROOT CAUSE FOUND + fixed in code 2026-06-06; pending on-device regen confirm.** On-device regen (after the containment `isWater` fix) STILL showed two complete 300 m bands (water + mirrored land). **Root cause:** `Zone300Builder` builds the final band mask from the **flood-fill** (`seaComp`), which bleeds past a coast-barrier gap onto the land side and fills the inland ribbon too — and the per-cell water test (the now-correct containment `isWater`) was **never re-applied to the final mask** (only to flood seeds + anchor). **FIX:** gate the final mask by the WATER bit — `mask = seaConnected && WATER && NEAR` (Zone300Builder step 4) — removing exactly the land mirror, water band unchanged. So BOTH layers are now correct: (1) containment `isWater`, (2) builder mask-guard. Rebuild APK (`apk-build.bat`) → install → "Bande 300 m" regen → the land band must vanish. Unit: rewrote the obsolete "flood recovers misclassified water" test → "band never covers a cell the water test calls land" (full suite 104 green). Prior on-device/EMODnet cross-check (offline harness via `CoastlineGenerator.buildFromElements`) shows the 300 m band sits on **actual land** along much of the mainland (e.g. 43.5481,7.0134 @ **+1.4 m** elevation, banded). It is **identical with ring re-inclusion disabled** (`RING_SURROUND_MIN=99`) → the cause is the **core anchor/flood water-classification**, NOT the hazard/ring work; my v1/v2 never touched it. Hypothesis: the 6 NM **south-ray `isOnWater` misclassifies land beside ~north-south coast stretches & harbours** (the ray runs parallel to the coast → 0 crossings → "water"), seeding an inland flood the anchor filter can't contain. The surround-guard only adds isolated-islet bands (Fourmigue); it does NOT fix this. **Needs a redesign of the water/land test.** Attempt 2026-06-06: orientation test (point on the right of the nearest OSM `land-on-left` segment = water; added `CoastlineSpatialIndex.nearestSegment` + extracted `nearestRef`). Validated vs **EMODnet on real data** → **only ~79% agreement** (cross>0=land beats inverse 79:21, so orientation is broadly preserved, BUT: corner/vertex ambiguity at capes where "nearest segment" flips side, partial orientation inconsistency, + EMODnet ~115 m near-shore coarseness). Then implemented the **corner-safe pseudonormal** signed-side test (`CoastlineSpatialIndex.isWater`, experimental) → STILL **~77 %** vs EMODnet. Corner handling did NOT help, and all disagreements are one-directional ("classifier=water, EMODnet=land") → the errors are **systematic orientation reversal**, i.e. the assembled coastline **winding is inconsistent (~23 %)**, not corner ambiguity. **CONCLUSION: a side-of-coast test cannot be clean until the generator NORMALIZES coastline winding** (land-on-left for the mainland + every island). That's non-trivial: closed rings are orientable via signed area, but the **open clipped mainland needs a reliable global reference** the south-ray can't give. Two real paths remain: **(A)** generator winding-normalization (root fix, R&D), or **(B)** EMODnet land-mask backstop (reliable, online at band-build, ~115 m coarse). Groundwork kept: `CoastlineGenerator.buildFromElements`. **FIX (2026-06-06, app-wide, user-approved): closed-polygon containment.** Replaced the open-polyline south-ray with even-odd point-in-polygon in `CoastlineSpatialIndex.isWater` (now the single classifier; `CoastlineRepository.isOnWater` keeps only the >6 NM open-water short-circuit and delegates). The open mainland is closed by a virtual **inland (north) cap** (bbox-top edge is on land here); water/land = even-odd **north-ray** cast +1 for the cap → **winding-independent**, so the whole orientation saga is moot (signed-side `isWater`/`nearestSegment` + south `queryColumn`/`ColumnCandidate` deleted; added `SpatialOperations.rayCrossesSegmentNorth`). **Key insight:** for a *clean single* mainland the closed-polygon test is provably **equivalent** to the old south-ray → the real mirror must come from **open ends / mainland fragmentation**; the new test counts **all geometrically-open coast pieces** (ring ⇔ first≈last, deliberately NOT the generator's `isClosed` flag, which mislabels reclassified fragments as closed) and closes them with one shared cap — directly fixing fragment-induced mirror. Tests: `CoastlineSpatialIndexWaterTest` (ground-truth point-in-polygon sweep on single AND fragmented coasts + N–S/island/empty) and north-ray primitives in `SpatialOperationsTest`; full `testDebugUnitTest` = **104 green**. **VALIDATION PENDING (the gate):** `Zone300WaterOracleHarness` (@Ignore; run online) cross-checks `isWater` vs EMODnet along the real coast — could NOT run here (no network/device). If a mirror persists after the harness passes, the next suspect is the band builder's own flood/`classifySeaward` (`Zone300Builder`), not the classifier.
- [ ] **Confirm on device after regenerating the band with a fix-containing APK** (the photo was the stale cached band — NOT the fixed output). Then optionally run `Zone300WaterOracleHarness` (EMODnet) for a quantitative agreement %. Topology auto-logged via `logCoastlineTopology` (`open>1` ⇒ fragmentation).
- [ ] **Build-time band bake + bundle — TOOLING DONE 2026-06-06; run the baker on a networked machine.** Design: the generated band is a build artifact, **never committed** — it's baked into the gitignored `data/` tree and incorporated into the APK at build. Pieces: (a) `apk-bake.bat` → `Zone300AssetBaker` (flag-gated, **online**, `-Dmaro.bake=true` forwarded by `build.gradle.kts`; uses prod's containment `isWater` + cell size) writes `data/app-assets/coastlines/nice-frejus.bin`; (b) `build.gradle.kts` adds `data/app-assets` as an **assets srcDir** → packaged to `assets/coastlines/nice-frejus.bin` (validated via `mergeDebugAssets`); absent ⇒ ignored, app falls back to live fetch; (c) `CoastlineRepository.loadCoastline` = **disk cache → bundled asset → live fetch**, newer-by-timestamp wins (`readFromAsset`/`appContext`). **TO SHIP:** `apk-bake.bat` (networked) → `apk-build.bat` → `apk-deploy.bat`. **Nothing to commit** (the `.bin` is gitignored). Couldn't run the baker here (no Overpass HTTPS in this env).

#### Rules
- **INVARIANT (regressed twice — guard it):** the 300 m band must appear on the **water side only**, never mirrored onto land. As of 2026-06-06 the water/land decision is **closed-polygon containment** in `CoastlineSpatialIndex.isWater` (even-odd north-ray + inland cap, winding-independent) — NOT the old emergent flood-over-ray-cast. Any change to `isWater` (or to `Zone300Builder.markSeaComponents`) MUST keep green: `CoastlineSpatialIndexWaterTest` (ground-truth point-in-polygon sweep, **incl. the fragmented-mainland case**) AND the island/hazard `Zone300BuilderTest` cases. ⚠️ **Not yet confirmed on device** — the on-device band is **cached**, so the fix only shows after regenerating with a fix-containing APK. Unit tests alone were never sufficient (`CoastlineSpatialIndexWaterTest`'s ground-truth sweep guards regressions); the on-device regen is the real gate.
- Merge overlapping/intersecting 300 m zones into a single union (opposite shores, clustered islands).
- Performance is a hard constraint: precompute + cache the geometry, no per-frame recompute, keep vertex count low enough to stay smooth during pan/zoom.
- Smooth/simplify the 300 m line so it is clean and practical for navigation, not jagged.
- Expose `isIn300mZone()` and `distanceTo300mZone()` as part of this subfeature.
- Grid: uniform `min(coastline-gen resolution, 15 m)`, no variable resolution.
- Sample only the 0–500 m near-coast ribbon; ignore open sea / beyond 500 m for drawing.
- Smooth the seaward (300 m) edge only; snap the landward edge to coastline vertices — fill stays flush with the coast, never on land.
- Render red line + transparent fill via the same renderer/style path as the coastline overlay.
- Accuracy target ~5 % (≈ ±15 m).
- Readout in metres, switch to km above 1000 m; metric is analytic/grid-independent, valid at any range.
- Every closed coastline ring (mainland or islet) gets a band — no minimum-size filter.
- Cache/rebuild via the same logic as coastline generation.
- Band water/land via **closed-polygon containment** (`CoastlineSpatialIndex.isWater`): even-odd **north-ray** vs the coastline closed by a virtual inland cap; a cell is banded only if water **and** within 300 m. Winding-independent. (Superseded the per-cell ray-cast, the flood-only mask, and the abandoned signed-distance/orientation attempts.)

#### Key Files
- `docs/300MLineDesign.md` — design notes: distance-field isoline, line+fill rendering, smoothing, single-source metric.
- `docs/300MLinePlan.md` — implementation plan (build order, signatures, file anchors).
- `spatial/CoastlineSpatialIndex.kt` — `isWater` closed-polygon containment (even-odd north-ray + inland cap) + uniform-grid nearest-segment `query`.
- `spatial/SpatialOperations.kt` — `rayCrossesSegmentNorth` (+ `rayCrossesSegmentSouth`) ray-cast primitives.
- `spatial/Zone300Builder.kt` — band builder (flood-fill water mask + end-caps → marching squares → seaward classify → fill/line), consuming `isWater`.
- `data/coastline/CoastlineRepository.kt` — `isOnWater` (>6 NM short-circuit → delegates to `isWater`); `buildBandInBackground`/`regenerateBand` (+ topology diagnostic log).
- `app/src/test/.../spatial/CoastlineSpatialIndexWaterTest.kt` — water/land ground-truth point-in-polygon sweep (single + fragmented coast).
- `app/src/test/.../spatial/Zone300WaterOracleHarness.kt` — @Ignore EMODnet oracle cross-check; run online to validate the real coast.

### distancetocoast  [x]
Validate/fix the core nearest-coast distance query (`CoastlineSpatialIndex.query`).
Symptom: occasional invalid jumps in the distance value along curves.

**Root cause + fix:** query stopped at the first non-empty grid ring → could miss a
closer segment one ring out → over-estimates / discontinuous jumps at cell
boundaries. Now expands with a provably-safe stop (`bestDist ≤ ring·cellSize·0.95`)
so it always returns the true nearest. Validated by a brute-force grid-sweep test.

#### Todos
- [x] Analyse + fix early-stop nearest-neighbour bug in `query`
- [x] Add `CoastlineSpatialIndexTest` (brute-force grid sweep + edge cases)

#### Rules
- Nearest-segment search must expand until provably safe, never stop at the first
  non-empty ring.

#### Key Files
- `spatial/CoastlineSpatialIndex.kt` — uniform-grid nearest-segment query.
- `app/src/test/.../spatial/CoastlineSpatialIndexTest.kt` — correctness sweep.

## Todos

## Rules

## Key Files
