# UI_Map — Hydration Snapshot

**Baked:** 2026-07-14 08:40 UTC+2

## Active State
- **Subfeature:** decenter-map
- **Branch:** feature/map-offset-smmothness

## What Changed This Session
1. **Decenter animation smoothed** — replaced `spring(dampingRatio=0.8, stiffness=200)` with `tween(2000ms, FastOutSlowInEasing)` at `MapScreen.kt:1421`
2. **Root cause:** Spring overshoot + GPS speed jitter made boat marker visibly bounce and wobble
3. **Fix:** 2-second tween acts as natural low-pass filter — GPS micro-fluctuations don't accumulate enough displacement to be perceivable
4. **Build: SUCCESS** — `assembleDebug` green, 0 new warnings

## Design Decisions
- Tween chosen over critically-damped spring: predictable, physics-free, no oscillation possible
- Retargets mid-flight via `animateFloatAsState` — no snap on sudden speed changes, always smooth
- All offset consumers (CoastlineMapView, CenterMarkerOverlay, CapArrow, DirectionLine) unchanged — same Dp value, different interpolation curve

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — line 1421 modified
