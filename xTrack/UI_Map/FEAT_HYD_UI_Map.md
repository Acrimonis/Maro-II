# Context Hydration — UI_Map — 2026-10-10

**Last Bake:** 2026-10-10 12:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders, the device was never touched (the build and the scoped suites are named below), and every claim about the code rests on a file read.

## State

**Menu chevron order (2026-10-10, `feature/listery`).** The hamburger menu's three chevron doors walk the sort of the list each one opens — `trackListSort`, `routeListSort`, `markerListSort` — while membership stays the map filter, so the map's own drawing (`_mapMarkers`, the stored-track ranking, the render cap, the inspect candidates) is untouched. `sortMarkers` and `sortSummaries` left their view models for top-level pure homes both the list and the gate read; `menuTrackIdsOf` / `menuRouteIdsOf` take the sort state, and `MapScreen` derives `menuMarkerIds` for the chevron's walk world and `firstMarkerId`. The close rule moved with it: the `DrawerSource.MENU` arm of `scopeClosed` and the `TrackCardSource.MENU` arm of `trackScopeClosed` are now `listWorld || mapWorld`, so a list sort edit closes a menu-opened card of either kind, the map-filter arm's meaning unchanged. Both bare `setOnMarkerClickListener` suppressors left `MarkerOverlay` (with the now-dead `confirmed` parameter), so a marker tap reaches osmdroid's own handler — the Markers feature's `marker-click-remove` entry records it.

**Still live from the prior session.** The layer-fan master gate and the toggle-geometry / selection-escape pass are in `## Implemented`; the palette's `.floor` 0.25 / `.ms` 666 against `AppConfig`'s 0.33 / 555 keep two `MapPulseDotTest` assertions red as the user ordered, untouched.

**Review.** No review artifact for this session is on file in the corpus, so the bake records the gap rather than restating findings it cannot cite; no open should-fix is carried.

**Verification.** `apk-build.bat` SUCCESSFUL, and the scoped `ui.map` list, card-walk, selection-policy and marker suites green — the new `MenuMarkerOrderTest` pins `sortMarkers`, `ListScopeAndCountsTest` the sort-ordered referential, `DashboardScopeClosedTest` the widened close rule.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `sortMarkers` lifted to a top-level pure home; `scopeClosed`'s MENU arm
- `app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt` — `trackScopeClosed`'s MENU arm reads the list write too
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — `sortSummaries` lifted to a top-level pure home
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `menuTrackIdsOf` / `menuRouteIdsOf` take the sort state; `menuMarkerIds` derived
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — both bare tap suppressors and `confirmed` removed
- `app/src/test/java/ykws/android/maro/ui/map/MenuMarkerOrderTest.kt`, `ListScopeAndCountsTest.kt`, `DashboardScopeClosedTest.kt` — the pins
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — the `## Implemented` entry; `xTrack/Markers/FEAT_DSC_Markers.md` — `marker-click-remove`

## Next Step
The device pass, owed to the user: each chevron opening the head of its own list's order, a list sort edit closing an open menu card, and a marker tap now reaching osmdroid's handler — with the feature's older owed passes (the layer fan's gate, the five faces and the pulse mark, inspect live-acquire, marker zoom and the dp pass) standing beside it.
