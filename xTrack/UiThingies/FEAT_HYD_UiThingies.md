# Hydration: UiThingies → arc-layout-button

**Session:** 2026-06-14 11:43 UTC
**Last Bake:** 2026-06-14 11:43

## Micro State

- Designed the complete FanLayout framework geometry specification
- Confirmed: θ is primary parameter, parent at center, R derived from θ + button size, equidistance per relationship type, children on top, centered in directional arc
- Plan document: `plans/arclayout-button-analysis.md`
- 11 implementation todos + 10 strong rules in subfeature

## Target Files (to create)
- `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`

## Target Files (to modify)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — replace control stack with FanLayout instances

## Next Step
Switch to Code mode and implement the FanLayout framework + migrate the current layer toggle buttons into the first FanLayout instance.
