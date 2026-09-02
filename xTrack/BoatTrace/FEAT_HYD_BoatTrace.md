# BoatTrace — Hydration Snapshot

**Baked at:** 2026-09-02 10:01 UTC
**Active Subfeature:** marker-export-import
**Branch:** feature/next

## Session Summary

Track direction arrows implemented (Phases 1 + 2):

- Phase 1: custom `TrackDirectionOverlay` (osmdroid Overlay) drawing vector chevrons oriented by `bearingDeg` (segment-vector fallback), track-coloured, size ∝ stroke width, viewport-culled, one overlay per rendered track; "Show tracks direction on map" toggle in Settings + drawer; uniform pixel-spaced density.
- Phase 2: speed-based density — exponential spacing `lo × (hi/lo)^t` over floor/ceiling speed, speed derived from time + haversine when `speedMps` null; density selector (Uniform / Speed-based) + two log-scale RangeSliders (gap 6–512 dp, speed 2–64 kn).
- Fixes: overlay `mBounds` set; per-track cap 64→2000; `spacingPxForSpeed` clamped; UNIFORM spacing 24→48 dp.

BUILD SUCCESSFUL (assembleDebug).

## Next Step

Device E2E: verify arrows span the full track at all zooms, density reflects speed (exponential), drawer + settings toggles work.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/assets/maro.properties`
