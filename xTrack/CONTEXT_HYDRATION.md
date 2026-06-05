# Context Hydration — 2026-06-06

**Active Feature:** Zone300
**Active Subfeature:** drawZone (now complete)

## State
Fixed the user-reported defect: the 300 m band was painted on **both sides** of the
mainland (mirrored onto land). Root cause = the per-cell ray-cast (`isOnWater`, south
ray) calls cells **behind headlands** water (even crossing count) → false inland flood
**seeds** → the whole inland ribbon floods → mirror band. `capOpenEnds` was verified
sound (8-connected barrier reaching past the ribbon), so this was a seed problem, not a
cap leak.

Fix in `Zone300Builder.sampleMask`: after the flood, keep **only the flood component
connected to the deepest-water cell** (max distance-to-coast = unambiguous open sea →
the new `markSeaComponent` BFS). Inland pockets are separate components (divided by the
coast barrier) and are dropped. Backward-compatible: simple single-sea geometry is one
component → identical output; `Zone300BuilderTest` green. **Validated on device**:
water-side only, islands donut, no land mirror.

## Target Files
- `spatial/Zone300Builder.kt` — `dist[]` capture, deepest-water **anchor** pick,
  `markSeaComponent()` (keep only open-sea component), assembly prefers `seaComp`.

## Next Step
drawZone done → Zone300 **band is complete**. Optional: set `FEATURE_SCOPE_Zone300.md`
Status → Done; reconcile the stale drawZone rule (~line 49 claims signed-distance
supersedes flood-fill, but shipped = flood-fill **+ component filter**). Nothing
committed; branch `feature/300M-Claude`.

## Still open
- Trim/reconcile Zone300 drawZone rules to match the shipped flood-fill + component approach.
- GLOBAL_CONTEXT active pointer reads **DepthMapping** (parallel session) — left as-is, not reverted.
- Coordination: parallel sessions edit this tree (Coastline/Hazard, DepthMapping) — use per-feature git worktrees to avoid file races.

---
_Prior bake (Coastline / Batéguier) archived below for reference._

## (archived) Coastline — Batéguier hazard seed — 2026-06-05
Fixed the user-reported bug: the isolated danger **NW of Île Sainte-Marguerite** was
missing on the build (La Fourmigue showed, this did not). Root cause: never seeded.
Identified via OSM as the **West-cardinal Plateau du Batéguier / Jonquière shoal**
(`seamark:type=beacon_cardinal`, cat west) at **43.52655, 7.03046**; added as
`HazardSeeds.BATEGUIER` (ISOLATED_DANGER, 25 m) in `NICE_FREJUS`. Extracted pure
`CoastlineGenerator.mergeHazards(seeds, fetched)` (companion, `internal`): seeds win,
80 m dedup; empty fetch ⇒ seeds-only = the `atonClient = null`/offline baseline.
`HazardSeedIntegrationTest` extended. UI: map buttons → **"Côte"** / **"Bande"**
(`MapScreen.kt`). `assembleDebug` green.
- Target: `data/coastline/HazardSeeds.kt`, `CoastlineGenerator.kt`,
  `test/.../HazardSeedIntegrationTest.kt`, `ui/map/MapScreen.kt`.
- Next (Coastline): tap **"Côte"** (full delete-cache + OSM refetch re-merges seeds;
  **"Bande"** only rebuilds the band, won't pull new seeds). Verify Batéguier renders
  ~43.5266 N / 7.0305 E. Still open: Shom WFS `GetCapabilities` placeholders.

## (archived) Zone300 — drawZone [pre-component-filter, flood-fill baseline]
Flood-fill + end-caps `Zone300Builder`. Signed-distance was tried and **REVERTED**
(band inverted onto land — mainland polyline orientation unreliable, OSM stitching
reverses segments; orientation methods need a generator orientation fix first).
Flood-fill pipeline: ribbon mask (`isOnWater` guess) → rasterize coast barrier → seed
open water 180–300 m → 4-conn flood → end-caps seal clipped mainland ends → marching
squares → out-cell seaward classify → DP/Chaikin smooth → per-vertex landward snap fill
→ groupRings holes → mergeLines red line. (Superseded 2026-06-06 by the deepest-water
component filter above.)
