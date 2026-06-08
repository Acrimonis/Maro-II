# Context Hydration — isOnWaterAgain — 2026-06-08

**Active Subfeature:** 300m-pinch (DONE)

## State
Earlier work shipped (commits `94c31ac` + merge `b864270`): `isOnWater` mainland-primary classifier +
index cleaning + band **spikes** fixed. This session: the marina band **pinch** (subfeature `300m-pinch`)
is **DONE, deployed, and visually confirmed on the Pixel 7**.

**Pinch fix (v2, shipped):** a pure **output filter** on the red seaward line —
`dropPinchedSeawardRuns(lines, minDistM, distToCoast)` (top-level in `Zone300Builder.kt`), called in
`build()` on `mergeLines(seaward)` with `minDistM = bandM/2`. A real seaward vertex sits ≈`bandM` (300 m)
from shore by construction; a pinch is a few tens of m — so dropping `< bandM/2` removes pinches and the
line bridges across the harbour mouth. **Zero** land-mirror exposure (never touches flood/classification).
v1 (close near-closed loops in `CoastlineGenerator`) was **reverted** — wrong lever (residual pinches sat
beside already-CLOSED structures).

**Deploy footgun (fixed):** the app loads `assets/`**`coastlines`**`/` (plural) from gitignored
`data/app-assets/`, baked by **`Zone300AssetBaker -Dmaro.bake=true`** / `apk-bake.bat`. The committed
`assets/coastline/` (singular, `CoastlinePrebakeTest`) is a **test fixture only**. `apk-build.bat` used to
bake the singular path (green test, unchanged device); repointed it to `Zone300AssetBaker` + fixed docs.

## Target Files
- `spatial/Zone300Builder.kt` — `dropPinchedSeawardRuns` + call in `build()`.
- `spatial/Zone300BuilderTest.kt`, `data/prebake/BandValidationTest.kt` — tests.
- `apk-build.bat`, `data/prebake/CoastlinePrebakeTest.kt` — footgun fix + docs.

## Next Step
Commit + push (this session). PR target is **develop**. Optional FOLLOW-UP: unify the dual asset paths
(one baker / one path) deliberately — mindful of the duplicate-asset build risk + the DepthMapping parallel.
All tests green; nothing else open on `300m-pinch`.
