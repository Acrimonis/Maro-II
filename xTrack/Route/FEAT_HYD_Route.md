# Context Hydration — Route — 2026-10-23

**Last Bake:** 2026-10-23 14:47 UTC — written by `#bake` on `feature/route-tweeks`.

**Directive trace:** none of the five covered action classes was met since the last bake — no dependency added, no machine-shaped spatial file opened, the work was ordered, the device was untouched, and every claim came from a file read.

## State

The marker price's strength now ships in `maro.properties` alone: the **Marker pricing** section in Settings › Routing, its four strings in both locales and the persisted `route_marker_strength` value (field, key, read, write) were removed, so no screen adjusts what a grade is worth any more. The shipped value moved from 1.0 to `.2` — one grade a fifth of the mark's own crossing — and the engines, the auto-pick effect and the pre-warm read `AppConfig.routeMarkerCostPerStep` directly; the fine tile key still carries the strength. Two commits landed on `feature/route-tweeks`, `compileDebugKotlin` and the scoped marker and tile suites green.

## Target Files

- `app/src/main/assets/maro.properties` — the strength's only home, now `.2` with the crossing-time reading documented
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `routeMarkerCostPerStep` documented as the sole home; the two slider bounds removed
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the field, key, read and write removed
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt` — the factor law's wording now names the shipped value, no screen behind it
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapRouteEffects.kt` — the providers read `AppConfig.routeMarkerCostPerStep`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the section removed
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — the four keys removed

## Next Step

Judge the `.2` strength on the water — rating 1 leaning, rating 9 all but refusing — and move the single number in `maro.properties` if either end reads wrong.
