# Context Hydration — 2026-06-05

**Active Feature:** Zone300 — Render the 300 m regulatory 5-knot band along all coasts.
**Active Subfeature:** distancetocoast

## distancetocoast (current focus — FIXED + tested)
Bug found in `CoastlineSpatialIndex.query`: it stopped at the **first non-empty
grid ring** and returned the nearest among only those, so a closer segment one ring
further out was missed → over-estimates and **discontinuous "jumps"** in the value
as the boat moves across cell boundaries. Fix: keep expanding, accumulate the running
nearest, and stop only when **provably safe** (`bestDist ≤ ring·cellSize·0.95`, since
the nearest point in any unexplored cell is ≥ ring·cellSize away). Added
`CoastlineSpatialIndexTest`: a dense grid sweep comparing `query` to brute-force
nearest (tol 0.5 m) — would have failed on the old code. 73 tests green, APK builds.

## State (Zone300 band — done/working)
**Band is implemented and renders on device** (confirmed by screenshot). 9/10
`300mDesign` todos done; 38 unit tests green; APK builds. The line + translucent
fill follow the crenellated coast correctly.

**Device-confirmed rendering**; readout validated (366−300=66, 434−300=134).

**Root-cause fix for red-line breaks (the big one):** seaward/landward
classification was by absolute distance (≥150 m), which breaks in NARROW bands
(marinas/inlets) where the water-facing edge is <150 m from the opposite shore.
Now classified by **what's OUTSIDE each contour edge** — deep water → seaward
(red), land → landward (snap, no red) — robust at any band width, and faster (no
per-vertex query). Implemented via a per-cell `landArr` (set in `sampleMask`) +
`classifySeaward`/`outCellOf` over the grid rings; `processRing` now takes the
flags.

Secondary cleanup still on top: `denoiseLabels` (run<3), `mergeLines`
(BRIDGE_M=45 m), `groupRings` drops holes <`minHoleAreaM2` (6000 m²),
`ZONE_MIN_ZOOM`=11, `Zone300` logcat.

**UI:** two buttons — *Régénérer la côte* (blue, OSM refetch + band) and
*Bande 300 m* (red, rebuilds band only from cached coastline, fast). The band is
cached, so after installing a build, tap **Bande 300 m** to apply algorithm changes.

If a break ever remains, it's a real band whose mouth is wider than BRIDGE_M.
Fallback for any LARGE fill gap: flood-fill water mask from open sea (not needed yet).

## Target Files
- `spatial/Zone300Builder.kt` — mask → marching squares → classify/denoise → snap/smooth → groupRings.
- `ui/map/MapScreen.kt` — `drawZone300` + `ZONE_MIN_ZOOM` (11) + overlay lifecycle.
- `data/coastline/CoastlineRepository.kt` — `buildBandInBackground` (progressive + Zone300 logging), `isIn300mZone`/`distanceTo300mZone`.
- Docs: `docs/300MLineDesign.md`, `docs/300MLinePlan.md`.

## Next Step
Reinstall APK; verify the breaks/gaps are reduced. If large gaps remain, capture a
zoomed screenshot of one → implement flood-fill water classification. Then tune
`minHoleAreaM2` / `DP_EPSILON_M` / `CHAIKIN_ITERATIONS` and re-verify.
