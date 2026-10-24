# Context Hydration — Tracks — 2026-10-24

**Last Bake:** 2026-10-24 12:51 UTC — written by `#bake` on `feature/list-filters`.

**Directive trace:** three covered action classes were met and none stopped — the branch was opened on the user's `#new`, implementation ran on the user's `#impl`, and `#bake` / `#commit` / `#push` were invoked; no dependency was added, no machine-shaped data file was opened (the device was not touched), and every claim about the code came from a file read.

## State

The track list gained a **Source** filter (All · GPS · Demo) that reads `TrackSummary.gpsSourced`: any point carrying an accuracy makes a track GPS, none makes it demo, replacing the sticky `Track.demo` flag as the filter's basis (the flag stays for the speed history). Routes and the live recording always pass, and the route list and menu popup now offer neither Source nor the On water / On land axis, a stored route `position` value being stripped on load. The summary index moved to version 2 so the first launch rebuilds it once. The scoped `data.model`, `data.track` and `RouteListSettingsTest` suites and `apk-build.bat` are green; the device pass is owed. `GpxImporter` still never reads `<accuracy>`, so a foreign or re-imported GPX reads as demo.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the `source` arm, the axis, `routeFilterAxes()`
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — `hasGpsFix()`, `TrackSummary.gpsSourced`, index version 2
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — `rebuildIndex()` projects `gpsSourced`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — route filters lose a stored `position` on load
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — list axes chosen by `isRoutes`
- `app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt` — the open gap: `<accuracy>` is not parsed

## Next Step

Device pass: open the track list under GPS and Demo on a known GPS track and a known demo track, and confirm the route list shows neither axis; then decide whether `GpxImporter` should read `<accuracy>`.
