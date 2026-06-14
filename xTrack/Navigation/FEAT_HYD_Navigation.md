# Hydration: Navigation

**State:** Active — cap subfeature complete. Cap arrow and direction line fix applied.

**Session summary:** Fixed the cap arrow and direction line rendering bug — both were drawing at `bearingDeg` screen angle while the heading-up map rotation already aligned heading with screen-up. Removed `bearingDeg` from rendering math: arrow now always draws straight up from boat tip (speed-dependent length), direction line draws straight up from center to top edge. `bearingDeg` retained only for `cameraUpdates` → `mapOrientation = -bearingDeg` (map rotation). Build green.

**Target files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — CenterMarkerOverlay arrow + DirectionLine composable, ~6 lines changed

**Next step:** On-device visual verification of cap arrow and direction line.
