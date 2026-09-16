# Hydration: RegulatedZones

**Baked:** 2026-09-16 17:10 UTC
**Last Bake:** 2026-09-16 17:10 UTC
**Status:** active

## State

The bottom-left tag stack no longer rides the layer's visibility. Its set is derived from the Zone categories settings (`tagRegulatedZones` in `MapContent`), the stack is fed the marker point, and the 300 m sign carries its own band result (`markerInZone300`, the analytic `CoastlineRepository.isIn300mZone` at the marker) while the dashboard keeps `inZone300` for the boat. `MapContent`'s `boatPosition` parameter is gone and the strip's own parameter is now `markerPosition`. The 300 m sign stays unconditional by decision — it ignores the category toggles where every other tag follows them. Plan `260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md` is implemented: `apk-build.bat` SUCCESS, `DashboardPositionTest` green, device pass open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `tagRegulatedZones`, the strip and info-text wiring, the deleted `boatPosition` parameter
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — the `markerPosition` parameter and its marker-point KDoc
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `markerInZone300`
- `xTrack/RegulatedZones/260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md` — the plan, its review R1–R9 and the walk

## Next Step

Device pass: with the layer off over the Cap d'Antibes speed zone the tag shows, unticking the Speed limit category removes it, and dragging the map clear of the zone removes it too.
