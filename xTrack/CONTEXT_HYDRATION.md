# Context Hydration — 2026-06-05

**Active Feature:** Zone300 — Render the 300 m regulatory 5-knot band along all coasts.
**Active Subfeature:** none (work tracked under `300mDesign`)

## State
**Band is implemented and renders on device** (confirmed by screenshot). 9/10
`300mDesign` todos done; 38 unit tests green; APK builds. The line + translucent
fill follow the crenellated coast correctly.

**Open (todo #10 — on-device polish):** user reports (a) **breaks in the red
line** and (b) **gaps in the <300 m fill**. Diagnosis: (a) per-vertex seaward/
landward classification noise at the 150 m threshold splits runs; (b) small holes
from `isOnWater` ray-cast false-negatives in deeply-indented geometry.
**Mitigations just shipped (Zone300Builder):** `denoiseLabels()` flips
classification runs < 3 verts (de-jitters the red line); `groupRings` now drops
holes < `minHoleAreaM2` (6000 m²) so small spurious water-gaps get filled while
real island holes are kept. Also lowered `ZONE_MIN_ZOOM` 13→11 (band was gated
out at the default zoom) and added `Zone300` logcat lines in `buildBandInBackground`.

If gaps persist and are LARGE (not small holes), the real fix is replacing the
per-cell `isOnWater` south-ray with a flood-fill water mask from the open-sea
border. Awaiting a zoomed device sample to decide.

## Target Files
- `spatial/Zone300Builder.kt` — mask → marching squares → classify/denoise → snap/smooth → groupRings.
- `ui/map/MapScreen.kt` — `drawZone300` + `ZONE_MIN_ZOOM` (11) + overlay lifecycle.
- `data/coastline/CoastlineRepository.kt` — `buildBandInBackground` (progressive + Zone300 logging), `isIn300mZone`/`distanceTo300mZone`.
- Docs: `docs/300MLineDesign.md`, `docs/300MLinePlan.md`.

## Next Step
Reinstall APK; verify the breaks/gaps are reduced. If large gaps remain, capture a
zoomed screenshot of one → implement flood-fill water classification. Then tune
`minHoleAreaM2` / `DP_EPSILON_M` / `CHAIKIN_ITERATIONS` and re-verify.
