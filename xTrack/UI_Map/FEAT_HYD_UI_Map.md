# UI_Map — Hydration Snapshot

**Baked:** 2026-07-04 16:48 UTC+2

## Active State
- **Subfeature:** map refresh
- **Branch:** feature/fan

## What Changed This Session
1. **Depth produceState over-keying fixed** — removed `lowDepthWarningMaxM`, `lowDepthWarningMinOpacityPct`, `coastlineReady` from depth bitmap keys (only affects low-depth warning)
2. **Dead params removed** — `boatPosition` and `headingDeg` dropped from `CoastlineMapView` signature (passed but never read)
3. **Monolithic update split** — `AndroidView.update` replaced with 6 per-layer `LaunchedEffect` blocks, each keyed on only its layer's data + `zoomLevel`
4. **OverlayTracker per-layer zoom** — single `lastZoom`/`lastDepthBox` replaced with per-layer zoom fields
5. **Coastline zoom independence** — coastline LaunchedEffect keyed on `segments` only; `drawCoastline()` takes no zoom param (osmdroid auto-scales)
6. **Build: SUCCESS** — `assembleDebug` green (twice — initial + coastline fix)

## Design Decisions
- `LaunchedEffect` (async, 1-frame delay) chosen over `SideEffect` (sync but no key mechanism)
- Per-layer zoom tracking: each layer independently decides whether its zoom changed since last draw
- All runtime settings flow through data parameters (visibility gates, produceState, filterRegulatedZones) — no additional LaunchedEffect keys needed
- Coastline excluded from zoom dependency: polylines are static, osmdroid handles scaling

## Net Effect
| Trigger | Before (checks) | After (LaunchedEffects) |
|---------|----------------|--------------------------|
| Zoom gesture | 6 | 5 (coastline excluded) |
| GPS tick (1 Hz) | 6 | 0 |
| depthBitmap loads | 6 | 1 (depth only) |
| zone300 toggle | 6 | 1 (zone300 only) |
| boatSizeM slider | 6 | 1 (regulatedZones only) |
| Low-depth warning slider | 6 + depth produceState | 1 (lowDepth only) |

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — modified
- `app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt` — modified

## Plan
- `plans/coastline-mapview-per-layer-update.md`
