# Context Hydration — Ui_Menu — 2026-10-05

**Last Bake:** 2026-10-05 19:05 UTC — written by `#bake`; absence means never baked

**Directive trace:** none of the five covered action classes stopped — no dependency was added, no machine-shaped data file was opened, the work ran on the user's own order, the device was never touched, and every claim about the code rests on a file read.

## State
The route list separation is implemented and compiles: `apk-build.bat` SUCCESSFUL. The drawer's **ROUTES section** now mirrors the TRACKS section (D11, superseding D1): its `SectionHeader` carries the Link / Filter / Reset on the **route map referential** and its card holds the **Routes row** — label, count and chevron — opening a **kind-locked routes list**, while the Tracks list holds recorded tracks alone; the ROUTING card keeps the ends, the quick access and the gated summary, and the **Kind filter axis is retired**. The route settings moved to the Routing tab and a **pinned route** gained its own transparency and colour pair. `gradlew :app:testDebugUnitTest` reads 927 green with one pre-existing Route-engine failure (`RouteAvoidEngineTest.theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`) that predates and is unrelated to this work. Nothing is committed and the device pass is owed.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the ROUTES section mirroring TRACKS: the Routes header with its Link / Filter / Reset on the route map referential, and the card holding the Routes row
- `app/src/main/java/ykws/android/maro/ui/map/ListScope.kt` — `ListScope` and `listScopeOf`, the two chrome flags resolved to one scope
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the one scoped list surface and its gates
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — the scope-bound list: title, section, empty state, no live card or Merge on the routes list
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — `routeSummaries` beside the recorded-track `summaries`
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — two map filters, one pinned path for both kinds
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` + `app/src/main/assets/maro.properties` — the four route list settings and the pinned-route appearance pair
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the route rows' new home in the Routing tab's Appearance block

## Next Step
The user's device pass: open both lists, move the two filters independently, and confirm a pinned route reads apart from a pinned track and from an unpinned route.
