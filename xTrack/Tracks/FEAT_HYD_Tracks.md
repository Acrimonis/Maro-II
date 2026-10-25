# Context Hydration — Tracks — 2026-10-25

**Last Bake:** 2026-10-25 08:15 UTC — written by `#bake` on `feature/route-mono`.

**Directive trace:** two covered action classes were met and none stopped — the branch was opened on the user's `#new`, and implementation ran on the user's order; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read.

## State

A route's auto name no longer carries a direction: both ends read `A <-> B` through `TrackNaming.ROUTE_CONNECTOR`, one named end reads alone, and the `route_name_from_fmt` / `route_name_to_fmt` strings are removed from both locales; a recorded trip keeps `A to B` with the language's own connector. The home-port radius is now 150 m (`tracking.homePort.geofenceRadiusM`), because the device log put the last search's destination 106–113 m from the port, outside the old 100 m circle, so the port's marker still named it; the radius is shared with the arrival prompt and the trip naming exclusion. `compileDebugKotlin` is green and the device pass is owed. The Source filter of `feature/list-filters` (All · GPS · Demo on `TrackSummary.gpsSourced`) stands as baked on 2026-10-24, with `GpxImporter` still not reading `<accuracy>`.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt` — `ROUTE_CONNECTOR`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the route save composes the name with it; the composable connector read is gone
- `app/src/main/assets/maro.properties` — `tracking.homePort.geofenceRadiusM` 100 → 150
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — the From / To strings removed
- `app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt` — the open gap: `<accuracy>` is not parsed

## Next Step

Save a route with both ends named and with one end named on the device and read its name in the list and the save toast; then decide whether `GpxImporter` should read `<accuracy>`.
