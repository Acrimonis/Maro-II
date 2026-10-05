# Context Hydration — Ui_Menu — 2026-10-05

**Last Bake:** 2026-10-05 20:56 UTC — written by `#bake`; absence means never baked

**Directive trace:** none of the five covered action classes stopped — no dependency was added (`app/build.gradle.kts` untouched), no machine-shaped data file was opened, the work ran on the user's own order, the device was never touched, and every claim about the code rests on a file read.

## State
The per-kind map-visibility toggles are implemented and compile: `apk-build.bat` SUCCESSFUL on `feature/menu-kind-visibility` (stacked on `feature/list-tracks-routes`, not yet committed). The drawer's **ROUTES** and **TRACKS** headers now carry a map-visibility **eye** as their first trailing control, outside the filter-axes gate; the one `tracksVisible` gate split into `tracksVisible` (recorded tracks) and a new `routesVisible` (routes), with pinned items gated per kind. The route **Speed/Direction** copy and comments were then clarified to the settled law — the master Speed Colors / Arrows chips govern all kinds and the route switches only turn a route's own off — a pinned route now obeys the arrows veto, and a `SectionDivider` separates the track and routes collapsibles in the Layers card. The `ui.map` suite reads 388 with only the two pre-existing `TrackOutlineTest` failures caused by an uncommitted `maro.properties` dash edit that is not part of this work. Nothing is committed and the device pass is owed.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the two header eyes and the `KindVisibilityToggle` helper
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the per-kind `storedTrackSelection`, `bandedStrokeOnMap`, `routeTrackRenderPlan` and `pinnedTrackRenderPlan` gates
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the `routesVisible` field, key, load and save
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `toggleRoutesVisibility()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the Routes card's 2nd-section divider
- `app/src/main/java/ykws/android/maro/ui/icons/VisibilityOff.kt` — the new eye-off glyph
- `app/src/main/res/values/strings.xml` + `values-fr` — the two `cd_` strings and the route Speed/Direction description

## Next Step
The user's device pass: toggle each header eye to hide and show its kind, and confirm the route Speed/Direction switches only turn their own kind off under the master chips.
