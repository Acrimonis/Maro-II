# BoatTrace — Phase 3: Cross-navigation between track and marker definitions

**Date:** 2026-08-31
**Phase:** 3 of 3 — UI cross-navigation + stack/back behavior

## Goal

1. **Track detail** ([`TrackInfoDrawer`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:345)) lists the markers belonging to that track, each with a `>` that opens the marker definition.
2. **Marker detail** (`MarkerDrawer` Viewing) shows its track with a `>` that opens the track definition.

## Current navigation (findings)

- **Flag-based, no backstack** — `showTrackHistory`, `showMarkerManagement`, `showTrackInfoDrawer`, `drawerState` booleans in `MapScreen`.
- Drawers are [`DrawerSlot`](app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt:47)s composed in a fixed order in `OverlayLayer`.
- **Back = composition-ordered `BackHandler`s** — each drawer registers its own while visible; `MapScreen` has a fallback double-back-to-exit at the end, enabled only when nothing else is open ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1704)).
- Existing list→detail: `TrackHistoryOverlay` → `onNavigateToTrack` → `TrackInfoDrawer` (prev/next via `trackListIds`/`currentTrackIndex`); `MarkerManagementOverlay` → `openEditDrawer` → `MarkerDrawer(Viewing)` (prev/next via `selectedMarkerIds`/index).
- Scroll preserved via `trackListState`/`markerListState` + `SavedScrollState`.

## Implications (stack & back behavior)

1. **No backstack exists** — cross-navigation (track↔marker) needs one, or the two detail drawers collide.
2. **Drawer overlap** — `TrackInfoDrawer` (left/bottom) and `MarkerDrawer(Viewing)` are separate `DrawerSlot`s; opening both at once overlaps them. Only one detail must be visible.
3. **Back-handler conflict** — both drawers register `BackHandler`s; cross-nav must make the top detail's back pop to the *previous* detail, not close to list. The double-back exit must fire only when no list/detail is open.
4. **Prev/next + scroll preservation** — `currentTrackIndex` and `selectedMarkerIndex` (and list scroll states) must survive a jump and restore on back.
5. **Live recording track** — its provisional temp marker should not be cross-navigable (it can disappear mid-view).
6. **Dangling targets** — Phase 2's delete-cascade means a track/marker can be deleted while its detail is open; the open detail must close/refresh gracefully.

## Proposal (recommended) — minimal detail backstack

Introduce `OverlayBackStack` (a `SnapshotStateList<OverlayScreen>` in `MapScreen`):

- `sealed OverlayScreen`: `TrackDetail(trackId)` / `MarkerDetail(markerIds: List<String>, selectedIndex: Int)`.
- Open a detail from a list → **clear stack, push**.
- Cross-nav (track→marker, marker→track) → **push** the target.
- Back / close (X) → **pop**; when empty → return to the originating list.
- Detail visibility derives from the stack top; the list-level flags stay as the base layer.

## UI wiring

- `TrackInfoDrawer`: new "Markers on this track" section — `UserMarker`s filtered by `trackId`, rows `name + ">"`, tap → push `MarkerDetail`.
- `MarkerDrawer(Viewing)`: "Belongs to track: {title}" row with `">"`, tap → push `TrackDetail`.

## Open decisions

- Close (X) on a detail with a non-empty stack: pop one level, or dismiss the whole stack? (recommend: pop one level).
- Loop depth: `track→marker→track→…` — allow unlimited push, or collapse same-type? (recommend: allow; it's a natural stack).

## Verification

- E2E: track detail → marker detail → back → track detail → back → track list.
- E2E: marker detail → track detail → back → marker detail.
- E2E: scroll position preserved after a cross-nav round-trip.
- E2E: delete a track while its marker detail is open → detail closes gracefully.
- E2E: double-back still exits only from the map root.
