# Hydration: ArcLayout → fan-migration

**Session:** 2026-06-14 14:55 UTC
**Last Bake:** 2026-06-14 14:55

## Micro State

- FanLayout framework fully implemented with FanConfig + FanLayout + MapControlButton + FanIconComponents
- Layer fan: maxCount=5, currentCount=4, direction=LEFT, toggleChildren=true, showActiveBadge=true
- Child centering fixed: effectiveTheta = 180/currentCount (45° for 4 buttons) for angles and radius
- Full-arc distribution: children span full 180° with ½θ at each end — "½ space, btn, btn, btn, btn, ½ space"
- R = 94.1dp — chord = btn+gap = 72dp maintained
- Z-order fixed: children declared FIRST in Box (behind parent), parent SECOND (on top) — children fan out from behind
- Remaining todos: second fan button, Spacer replacement

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt`

## Next Step
Add second fan button in control stack + replace hardcoded Spacer.
