# BoatTrace — Hydration Snapshot

**Baked at:** 2026-09-02 12:03 UTC
**Active Subfeature:** marker-export-import
**Branch:** feature/next

## Session Summary

Track direction arrows (Phases 1 + 2) + settings UI polish:

- `TrackDirectionOverlay` (custom osmdroid Overlay): vector chevrons, bearing fallback, viewport culling, per-track; pixel-spaced (uniform) + exponential speed-based density; speed fallback from time+haversine.
- Settings: `tracksDirectionVisible` toggle in Settings + drawer; own "Direction Track Settings" collapsible with segmented density selector (Uniform/Speed-based, language-picker pattern) + log-scale RangeSliders (gap 4–640 dp, speed 2–64 kn).
- Headers normalized to `SubSectionHeader`.

BUILD SUCCESSFUL (assembleDebug).

## Next Step

Device E2E: verify arrows, density contrast, settings + drawer toggles.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/assets/maro.properties`
