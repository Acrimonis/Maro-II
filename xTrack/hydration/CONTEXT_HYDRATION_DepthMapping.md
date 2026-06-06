# Context Hydration — DepthMapping — 2026-06-06

**Active Subfeature:** none

## State
Big session. **Rendering** wired into `MapScreen`: hypsometric colour-map `GroundOverlay` + isobath polylines (zoom-gated 11/13/15; z-order depth→isobaths→300 m band→coastline) + depth-at-centre readout + validation badge; `DepthBitmap` now one-shot `IntArray`→`createBitmap` off-thread; `MainActivity` wires `DepthViewModel`. `assembleDebug` + unit tests green. **Litto3D collision tier UN-BLOCKED**: it's open data on the **public SHOM INSPIRE pre-package download API** (no account) — scripted in `tools/fetch_litto3d_paca.ps1` (curl+tar, MD5, km-window focusable). Fetched the **Cannes→Antibes** focus (13 tiles, 5 m), baked (`bake_litto3d.bat`, env-overridable bbox), merged via `DepthPrebakeTest` → `assets/depth/nice-frejus.bin` (26,701 Litto3D cells). **Validation** rebuilt: 4 covered, in-range, EMODnet-REST/SeaDataNet-survey-backed control points → `passed=true`, overall RMSE 1.45 m, collision 0.092 m. Committed (`ed04c69`), merged develop's tooling refactor (`3bc4f65` — adopted AGENTS.md + new xtrack/YAML front-matter), pushed to `origin/feature/depth-mapping` (**PR now merges cleanly with develop**). Nothing device-verified yet.

## Target Files
- `ui/map/MapScreen.kt` — depth GroundOverlay + isobaths + dashboard badge
- `data/depth/validation/ControlPoints.kt` — 4 survey-backed points
- `tools/fetch_litto3d_paca.ps1` + `tools/bake_litto3d.bat` — public-API fetch + bake
- `app/build.gradle.kts` — test heap 4g (prebake parse)
- `app/src/main/assets/depth/nice-frejus.bin` — baked grid (deep + collision)

## Next Step
**On-device verify the depth rendering** — GroundOverlay N/S/E/W orientation; isobaths at zoom ≥13 (2 m ≥15); z-order; dashboard `🌊 Fond` + validation badge (~1.4 m). Then: convert `FEATURE_SCOPE_Coastline.md` to YAML front-matter; extend Litto3D east (Nice→Menton); tier-aware badge; `AsciiGridParser` streaming.
