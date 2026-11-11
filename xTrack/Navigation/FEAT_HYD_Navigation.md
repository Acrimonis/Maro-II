# Context Hydration — Navigation — 2026-11-11

**Last Bake:** 2026-11-11 16:17 UTC

**Directive trace:** none of the five covered action classes stopped this session; the work ran on `#new`, `#impl` and `#track` orders, no dependency was added, no data file was opened, and the device was touched only by the user.

## State

On `feature/track-dash-arriving-not-updated`. Inside the 300 m band the dashboard's `currentZone` is now the band, decided by position: with no exit along the heading it is `bandZoneExitUnknown` (infinite exit, `BeyondType.UNKNOWN`), so the speed-limit card and speed colouring show 5 kn on a landward harbour approach — read OK on the device. The look-ahead to a SHOM zone then stops at land (`landCrossedBefore`, 10 m step, `isWaterOrNull`), so a zone behind a headland raises no amber; 1 774 tests and `apk-build` green, its device pass owed and tracked in the `Ozers` feature. The bottom-left tag follows the map centre by design and was not changed. The four Navigation plans were archived (`xxArchive/`, with `INDEX.md`); the position-source decisions live in `FEAT_DOC_Navigation_decisions.md` and the dashboard-position device pass is still open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — shore pipeline, `zonesAhead`, `bandZoneExitUnknown`, `landCrossedBefore`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — distance and speed-limit cards
- `app/src/test/java/ykws/android/maro/ui/map/BandZoneExitUnknownTest.kt`, `LandCrossedBeforeTest.kt` — the two new suites
- `xTrack/Navigation/FEAT_DOC_Navigation_decisions.md` — which position feeds which surface

## Next Step

Device pass of the land stop: a harbour approach with a lower-limit zone behind a headland shows no amber, and a zone on open water ahead still does.
