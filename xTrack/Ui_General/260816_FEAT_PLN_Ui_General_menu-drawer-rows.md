# Menu drawer rows — Tracks/Markers caption + chevron shortcut

**Feature:** Ui_General
**Status:** designed
**Created:** 2026-08-16 11:57 UTC
**Review:** Ask audit 2026-08-16 — 3 fixes folded in.

## Request

1. Rename "Manage Tracks" → "Tracks", "Manage Markers" → "Markers".
2. Row click opens the list (unchanged).
3. Chevron click selects + opens the **first item of the list under the current sort order and filters** in the detail drawer.

## Decisions

| # | Decision |
|---|----------|
| D1 | Captions without ellipsis: "Tracks"/"Markers" (FR "Traces"/"Repères"). The trailing chevron already signals navigation; the ellipsis is reserved for actions requiring further input (dialogs/wizards), and the arrow is now a direct action. |
| D2 | Row body (text area) remains clickable → opens the list overlay (`showTrackHistory` / `showMarkerManagement`). |
| D3 | Chevron becomes its own `IconButton` → opens the detail drawer on the first item of the current filtered/sorted list. ID resolved in `MapScreen`: track = first `!isLive && id !in pendingDeleteIds`; marker = first of `markers`. |
| D4 | Arrow shortcut does NOT set `trackOpenedFromList` / `markerOpenedFromList` → back closes the drawer to the map (no list reopen). |
| D5 | Arrow shortcut still highlights + animates the map to the item (select), mirroring a list-card tap. |
| D6 | Chevron is disabled (grayed no-op) when there is no first item — empty list, only-live track, or filter returns zero matches. |

## Key facts

- Live track card is pinned to the top of the list but is **not tappable** ([`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:797)) → "first non-live" is the correct reading of "first item".
- `markers` passed to `OverlayLayer` is already filtered + sorted ([`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:236)) → `firstOrNull()` is correct.
- Marker drawer open is **event-driven**: `navigateToTarget` → `LaunchedEffect` → `whereAmI()` + `openEditDrawer(source = LIST)` ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2059)). The arrow reuses this path — keeps list prev/next; the extra `whereAmI` run is redundant but harmless (gate if desired).
- Nested clickable: an `IconButton` inside a clickable `Row` consumes its own tap — the parent row click does not fire.

## Files touched

| File | Change |
|------|--------|
| `app/src/main/res/values/strings.xml` | `menu_manage_tracks` → "Tracks"; `menu_manage_markers` → "Markers"; add `cd_open_first_track`, `cd_open_first_marker` |
| `app/src/main/res/values-fr/strings.xml` | `menu_manage_tracks` → "Traces"; `menu_manage_markers` → "Repères"; add FR for new content descriptions |
| `MenuDrawerOverlay.kt` | Add nullable `onOpenFirstTrack` / `onOpenFirstMarker` params; wrap chevron `Icon` in `IconButton(enabled = callback != null)` |
| `MapScreen.kt` | Resolve `firstTrackId` / `firstMarkerId`; add `openTrackDetail(id, fromList)` / `openMarkerDetail(id, fromList)` helpers; wire arrow callbacks with `fromList = false`; replace the duplicated `onNavigateToTrack` body with the shared helper |
| `OverlayLayer.kt` | Thread `firstTrackId` / `firstMarkerId` + the two callbacks; build nullable closures; `onDismissMenu()` before dispatch |

## Implementation notes

1. `firstTrackId = trackSummaries.firstOrNull { !it.isLive && "t:${it.id}" !in pendingDeleteIds }?.id`.
2. `firstMarkerId = mgmtMarkers.firstOrNull()?.id`.
3. Chevron `IconButton(enabled = firstId != null, onClick = …)` reuses `Icons.AutoMirrored.Filled.KeyboardArrowRight`, tint unchanged, new content description.
4. `openMarkerDetail(id, fromList)`: set `previousMarkerZonesVisible`, `showLayer()`, zones visible, `highlightedMarkerId`, `markerOpenedFromList = fromList`, `showMarkerManagement = false`, then `navigateToTarget = NavigateTarget(…)` (drawer opens via the existing effect).
5. `openTrackDetail(id, fromList)`: load cached detail, guard empty points, compute target/bbox, `preNavigationState`, `highlightedTrackId`, `trackDrawerState`, animate — `fromList = false` skips scroll-state save and the `trackOpenedFromList` flag.

## Implemented

2026-08-16 — all five steps coded. **BUILD SUCCESSFUL**. The R.jar lock was released via
`gradlew --stop`; the two open-detail helpers were converted from `val` lambdas to local
`fun` declarations (lambdas would have blocked named arguments and `return@` labels).
