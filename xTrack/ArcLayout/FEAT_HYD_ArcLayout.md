# Hydration: ArcLayout → fan-migration

**Session:** 2026-06-14 14:50 UTC
**Last Bake:** 2026-06-14 14:50

## Micro State

- FanLayout framework fully implemented with FanConfig + FanLayout + MapControlButton + FanIconComponents
- Layer fan: maxCount=5, currentCount=4, direction=LEFT, toggleChildren=true, showActiveBadge=true
- Fixed child centering: effectiveTheta = 180/currentCount (45° for 4 buttons) used for angles and radius
- Full-arc distribution: children span full 180° with ½θ at each end — "½ space, btn, btn, btn, btn, ½ space"
- R = 94.1dp (down from 116.5dp) — chord = btn+gap = 72dp maintained
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
