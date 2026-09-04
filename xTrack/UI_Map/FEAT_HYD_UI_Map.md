# UI_Map — Hydration Snapshot

**Baked:** 2026-09-04 19:30 UTC

## Active State
- **Subfeature:** marker filter + dashboard close
- **Branch:** feature/ui-map-filter

## What Changed This Session
1. **Marker filter drives the map** — `MarkerOverlay` now renders from the filtered `markers` list, so the filter hides pins on the map too (was the unfiltered `allMarkers`).
2. **Auto-close on filtered-out** — `applyFilterSort()` closes the viewing panel when the active filter excludes the selected marker (Viewing only).
3. **Menu/fan close dashboards** — `closeSelectedItemDashboards()` closes the open marker/track dashboard when the side menu opens or a layer fan expands (open only, wizard preserved).
4. **List-context stacking removed** — deleted `OpenedFromList` flags, `ListScrollState` saves, reopen-on-close blocks, `fromList` params, and `restoredScrollState` plumbing. Closing an item returns to the map; Prev/Next remains the navigation path.

## Design Decisions
- `_allMarkers` kept as the unfiltered source of truth for `whereAmI` proximity + ghost-pin checks.
- `preNavigationState` map-viewport restore for tracks kept (map context, not list context).

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`

## Next Step
- On-device verify: filter hides map pins; opening menu/fan closes the detail panel; closing an item returns to the map.
