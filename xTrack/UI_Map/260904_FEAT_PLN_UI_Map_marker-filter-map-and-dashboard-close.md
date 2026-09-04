---
feature: UI_Map
topic: Marker filter on map + menu/fan closes selected-item dashboard
created: 2026-09-04 19:07 UTC
status: planned
---

# Plan: Marker filter on map + dashboard auto-close

## Goal

1. Marker filter must hide non-matching pins on the map, not just the drawer (match track behaviour).
2. Opening the side menu or a fan closes the selected-item dashboard (marker + track detail).

## Change 1 — one marker display list drives drawer + map

- [`MapScreen.kt:2104`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2104): pass the filtered `userMarkers` (from `markersViewModel.markers`) to `MarkerOverlay` instead of `allMarkers`. The `DisposableEffect` is already keyed on `markers`, so the map rebuilds on filter change.
- Keep `allMarkers` / `allMarkerIds` for `whereAmI` proximity and ghost-pin existence checks — these must stay exhaustive.
- When the active filter excludes the currently-viewed marker, close the drawer (reuse the `onMarkerDrawerClose` sequence) so the dashboard never describes a pin that is hidden from the map.

## Change 2 — menu/fan open closes the dashboard

- [`MapScreen.kt:1976`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1976): on `showTrackDrawer` false→true, run the marker close sequence (`highlightedMarkerId = null`, `navigationZonesVisible = false`, `markersViewModel.closeDrawer()`) and the track close sequence (`highlightedTrackId = null`, `trackDrawerState = TrackDrawerState()`, `preNavigationState = null`).
- [`MapScreen.kt:2046`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2046): on `expandedFanId` null→non-null, run the same close sequences.
- Fire only on OPEN — closing the menu/fan must not reopen the dashboard.

## Verify

- Build: `apk-build.bat`.
- Marker filter hides pins on the map too.
- Opening menu or fan closes an open marker/track dashboard.
- Closing menu/fan leaves the dashboard closed.

## Review notes

- Marker map rendering is the only consumer of `allMarkers`; `whereAmI` and ghost-pin checks already read the unfiltered backing list, so swapping the render list is safe.
- The unconfirmed create/edit preview is passed to `MarkerOverlay` separately (id `__unconfirmed__`), so it stays visible under any filter.
- Filtering out the viewed marker would otherwise show a "Marker not found" panel ([`MarkerDrawer.kt:136`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:136)). Close only in `Viewing` state, not during Creating/Editing.
- Menu/fan close must NOT reuse the full `onMarkerDrawerClose` lambda (it reopens the management list when `markerOpenedFromList`); replicate only the 3 close lines.
- Track close = mirror the existing BackHandler block ([`MapScreen.kt:2199`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2199)).
- Both entry points are single: menu = `onOpenTrackDrawer` ([`MapScreen.kt:1976`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1976)); fan = `onToggleFan` ([`MapScreen.kt:2046`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2046)).

## Decisions (confirmed 2026-09-04)

- Filtered-out viewed marker → auto-close the panel (Viewing state only).
- Menu/fan close → copy the 3 close lines only; skip the list-reopen side effect.
- Track close → mirror the BackHandler block.
- Fire only on open: menu `false→true`, fan `null→non-null`.

## Change 3 — remove list-context stacking

Opening an item from a list, then closing it, goes straight back to the map — no return to the list. Prev/Next buttons remain the way to move between items.

Remove:
- State `trackOpenedFromList`, `markerOpenedFromList`, `trackListScrollState`, `markerListScrollState` ([`MapScreen.kt:430`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:430)).
- Reopen-on-close blocks: [`MapScreen.kt:2305`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2305) (marker) and [`MapScreen.kt:2407`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2407) (track).
- `fromList` branches + now-unused `fromList` params in `openTrackDetail` / `openMarkerDetail` ([`MapScreen.kt:2217`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2217), [`MapScreen.kt:2271`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2271)).
- Scroll-restore plumbing `trackRestoredScrollState` / `markerRestoredScrollState` through `OverlayLayer` → `TrackHistoryOverlay` / `MarkerManagementOverlay` `restoredScrollState` params.
- Flag resets that no longer apply: [`MapScreen.kt:2180`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2180), `:2186`, `:1843`, `:2310`, `:2386`.

Keep:
- `DrawerSource` + `selectedMarkerIds` / `selectedMarkerIndex` — the Prev/Next navigation source, not list context.

Note: once the reopen block is gone, `onMarkerDrawerClose` is just the 3 close lines, so menu/fan can reuse it directly.

## Other context-stacking cases

1. Track map-viewport restore — `preNavigationState` saves zoom/center when a track opens and restores it on close. Map context, not list context; recommend keeping.
2. Track bbox navigation — `trackNavigateState` zooms to the track extent. Part of track viewing; keep.
