# Hydration: UI_Map — rotate subfeature

**Baked:** 2026-06-14 18:18 UTC

## Micro-State Summary

Implementation complete. Two-finger rotation gesture in demo mode with `demoHeadingUp` toggle (default OFF). All changes build green.

### What changed
- **SettingsManager**: `demoHeadingUp: Boolean = false` persisted toggle
- **CoastlineViewModel**: `setDemoBearing(deg)` — public entry for two-finger rotation
- **MapScreen**:
  - Touch listener detects `ACTION_POINTER_DOWN` → tracks angle between 2 fingers, applies delta to `NavigationState.bearingDeg`
  - Relaxed `mapOrientation = 0f` hard-lock (gated behind `!demoHeadingUp`)
  - New LaunchedEffect: watches `navigationState` → `mapOrientation = -bearingDeg` in demo heading-up mode
  - Settings toggle in Display → Navigation section
- **strings**: EN/FR toggle labels
- **SpatialOperations**: `initialBearing()` added

### Key design decisions
- Bearing source = two-finger rotation gesture (not pan-direction-derived)
- Rotation pivots around screen center (= boat marker position via Compose overlay)
- `setDemoBearing()` delegates to `setMapBearing()` (jitter gate via `MIN_BEARING_DELTA_DEG`)
- Demo speed (pan-velocity) is independent of rotation — both features coexist

### Verified
- `assembleDebug` — BUILD SUCCESSFUL
- Branch: `feature/rotate-map-demo-mode`
