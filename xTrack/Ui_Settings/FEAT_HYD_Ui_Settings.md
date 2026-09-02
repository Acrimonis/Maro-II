# Ui_Settings — Hydration (2026-09-02 20:15)

## State
Approach re-display reworked: single "Auto-show zones" section (2 global mode switches + 3 type switches + collapsible "When to reveal"); per-type speed/non-speed reveal split; non-speed proximity added; master override resets overlay; dead radial branch removed; drawer "Position mode" + accent-blue master switch.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — master-override reset, non-speed proximity, per-type split, zonesAhead rename, dead radial branch removed
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Auto-show zones section, collapsible When to reveal, both-buckets render + memoized isNear
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — isNear + hasNonSpeedCategory
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — zonesAhead rename
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` + `OverlayLayer.kt` — master switch
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — approachAutoShow* + per-type + autoShowMasterOverride fields

## Next Step
Follow-up (deferred): zoneStatus/boundaryInCone primitive unification + directional cone trigger (reveal still omni nearest). BUILD SUCCESSFUL.
