# Hydration: Ui_General

**Session:** Filters-link — List/Map filter referential decoupling. Design locked + Ask review
passed; partial foundation on `feature/filters-link` (checkpoint commit b4693df).

1. **Filters-link decoupling (open, partial)** — decouple the List filter referential from the Map
   filter referential behind a per-type link toggle (joined = shared/linked default, broken =
   independent). Full design and implementation steps in the plan file.
   - Done: `SettingsManager` Option B — prefs versioning removed; new `trackMapFilter`,
     `markerMapFilter`, `trackFilterLinked`, `markerFilterLinked` keys with defaults Map = All and
     link = ON; idempotent defunct-key cleanup; load/persist.
   - Done: `MarkersViewModel` — `DrawerSource.MAP` (clamps at edges), reactive `mapMarkers` stream
     over `allMarkers`, drawer lookups read `allMarkers`.
   - Done: icons `Link`/`LinkOff` (+ icon renames AddLocationAlt/LocationOn, FanIconComponents).
   - Not implemented (next pass): OverlayLayer menu→Map / list→List filter split + link toggles;
     MapScreen map-filter render over unfiltered `allTrackSummaries` + reveal-on-select + menu map
     counters + map-tap `DrawerSource.MAP`; MenuDrawer Link/LinkOff toggle UI; track delete-advance
     per world. Then build + Ask review.

**Next:** finish remaining wiring on `feature/filters-link`, build via `apk-build.bat`, run Ask review.

**Target files:**
- `data/settings/SettingsManager.kt`
- `ui/map/MapScreen.kt`, `ui/map/OverlayLayer.kt`, `ui/map/MenuDrawerOverlay.kt`,
  `ui/map/MarkersViewModel.kt`
- `ui/icons/Link.kt`, `ui/icons/LinkOff.kt`

**Plans:**
- `xTrack/Ui_General/260909_FEAT_PLN_Ui_General_filters-link-decoupling.md` (status: In progress —
  design locked, partial implementation)

**Last Bake:** 2026-09-09 09:53 UTC
