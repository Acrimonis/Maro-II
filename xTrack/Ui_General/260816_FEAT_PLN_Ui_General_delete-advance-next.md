# Drawer Delete — Advance-to-Next + Queued Snackbar

**Created:** 2026-08-16
**Status:** implemented
**Feature:** Ui_General
**Parent:** 260802_FEAT_PLN_Ui_General_drawer-delete-undo

## Problem

Drawer delete defers deletion and shows a bottom snackbar with Undo, but leaves nothing selected.
Goal: keep the deferred delete + snackbar, but immediately open the adjacent item, and let Undo
restore + reopen the deleted item.

## Confirmed decisions

1. Scope: track drawer and marker drawer.
2. Adjacent fallback: next item, else previous, else close the drawer.
3. Ordering = the current list order:
   - Track: sorted + filtered `trackSummaries` (Track History order).
   - Marker: `selectedMarkerIds` order (management list order when opened from list).
4. Pending ids are excluded from navigation while their snackbar is live.
5. Marker Undo reopens the marker in view mode with its selection restored.
6. Snackbar moves to the bottom of the map area (portrait: above the dashboard; landscape: right of the dashboard).
7. Consecutive deletes stack as a FIFO queue (one snackbar at a time).

## Design

### Queued deferred delete

Replace the two single-slot `pendingTrackDelete` / `pendingMarkerDelete` states with:

- `Channel<PendingDelete>` (UNLIMITED) + a `pendingDeleteIds` set keyed by kind (`t:` / `m:`).
- `PendingDelete` sealed type:
  - `Track(id, name)`
  - `Marker(id, name, selectionSnapshot, source)`
- One `LaunchedEffect(Unit)` consumer loop: `for (pending in channel) { val result = snackbarHostState.showSnackbar(...); handle(result) }`.
  Material3 `showSnackbar` serializes internally, so items show sequentially.

Consumer handling per item:
- Undo → remove id from `pendingDeleteIds`, then restore (track: reopen view drawer; marker: `openEditDrawer(snapshot, selectedId = id, source)`).
- Timeout → remove id from `pendingDeleteIds`, then permanent delete (track: `trackViewModel.deleteTrack(id)`; marker: `markersViewModel.deleteMarker(id, closeDrawer = false)`).

### Track advance (on delete tap)

Compute the ordered non-live id list `fullIds` from `trackSummaries`; find `i = fullIds.indexOf(deletedId)`;
walk forward from `i+1` to the first id not in `pendingDeleteIds`; if none, walk backward from `i-1`;
if none, close the drawer. Reuse the existing next/prev sequence (load cached detail → `highlightedTrackId` →
`trackDrawerState` → `trackNavigateState`). Do NOT clear `preNavigationState`; preserve `trackOpenedFromList`.

### Marker advance (on delete tap)

Capture `selection = markersViewModel.selectedMarkerIds.value` and `source = markersViewModel.drawerSource`.
Compute the target as above over `selection` skipping pending ids. If found: `openEditDrawer(filtered, target, source)`,
set `highlightedMarkerId = target`, and center the map via a new public `centerOnMarker(id)`.
If none remain: `closeDrawer()`.

### Navigation exclusion

- Track: filter `pendingDeleteIds` out of `trackListIds`, `currentTrackIndex`, and the ids computed inside `onTrackPrev` / `onTrackNext`.
- Marker: the rebuilt `openEditDrawer(filtered, ...)` selection already excludes pending ids, so `viewNextMarker` / `viewPreviousMarker` stay correct.

### Snackbar position

`SnackbarHost` (bottom-center, top-level) gets:
- portrait: `bottom` padding = `portraitDashboardHeight`
- landscape: `start` padding = `landscapeDashboardWidth`

## Review findings

### Ask caveats

1. Preserve `markerOpenedFromList` / `trackOpenedFromList` (do not clear on delete).
2. Do not clear `preNavigationState` on advance (mirror next/prev).
3. Marker advance must update `highlightedMarkerId` and map center (see Debug D2).
4. No-adjacent close via `closeDrawer()` bypasses `onMarkerDrawerClose` cleanup — accepted, same as today.
5. Rotation loses the in-memory queue — accepted pre-existing limitation (deletion is deferred, items survive).
6. Shared snackbar host: a delete snackbar may queue behind the marker-created Undo snackbar — acceptable.
7. Async advance flash while `loadTrackDetailCached` runs — same as next/prev today.
8. Manual next→prev→close ignores `WHERE_AM_I` wrap semantics (per spec) — apply uniformly and pass the current `DrawerSource`.
9. Double-delete race — confirmed idempotent (repo filters/removes absent id as no-op).
10. `openEditDrawer` silently no-ops if the target marker is absent — low risk.

### Debug regressions

- D1 (critical): `deleteMarker` sets `_drawerState = Hidden` on every delete; add `closeDrawer: Boolean = true` and pass `false` in the deferred timeout path.
- D2 (critical): marker advance must set `highlightedMarkerId` and center the map without reusing `navigateToTarget` (which would reintroduce pending ids).
- D3: `refreshSummaries` already returns sorted + filtered summaries — "next" matches the visible list.
- D4: `UserMarkerRepository.delete` and `TrackRepository.delete` are idempotent.
- D5: prefix `pendingDeleteIds` keys by kind (`t:` / `m:`).
- D6: remove the id from `pendingDeleteIds` in both Undo and timeout branches before handling.
- D7: `deleteTrack` does not touch the drawer; `refreshSummaries` only re-sorts behind it.

## Files touched

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | `PendingDelete` sealed type, queue + consumer, track/marker advance, Undo handlers, navigation exclusion, SnackbarHost padding |
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | `deleteMarker(closeDrawer: Boolean)` flag + public `centerOnMarker(id)` |

## Steps

1. Introduce queued deferred-delete model in MapScreen.kt.
2. Track delete: enqueue + advance (next → previous → close), skip pending ids, preserve `trackOpenedFromList`, keep `preNavigationState`.
3. Track undo: reopen deleted track in view mode.
4. Marker delete: enqueue (snapshot + source) + advance + `highlightedMarkerId` + map center; `closeDrawer` when none remain.
5. Marker undo: restore selection snapshot and reopen in view mode.
6. MarkersViewModel: `closeDrawer` flag on `deleteMarker` + public `centerOnMarker(id)`.
7. Exclude pending ids from track navigation.
8. Reposition SnackbarHost to bottom of map area.
9. Build and smoke-test.

## Validation

- Delete last item → previous opens; delete only item → drawer closes.
- Track and marker paths; track zoom and marker highlight follow the advanced item.

## Revision 2 — vertical snackbar stack (2026-08-16)

Replaces the sequential FIFO snackbar with a spatial stack so multiple snackbars are visible at once.

### Decisions

- All snackbars stack: track delete, marker delete, and the marker-created undo.
- Cap 3 visible; overflow queues FIFO and promotes into view as slots free (undo preserved for all).
- Timeout-only dismissal (no close button); newest row at the bottom, nearest the map bottom.
- Per-row ~4s timer; each row animates in/out independently.

### Design

- `ActiveSnack` sealed type: `TrackDelete`, `MarkerDelete`, `CreateUndo`.
- `activeSnacks` (visible, max 3) + `queuedSnacks` (FIFO overflow).
- Custom `Column` at the bottom of the map area replaces `SnackbarHost` / `snackbarHostState`.
- Row resolution: Undo → restore (track/marker reopen, creation undo) + remove row; timeout → permanent delete (or `dismissLastSaved`) + remove row.
- `pendingDeleteIds`, `deleteMarker(closeDrawer=false)`, navigation exclusion, and advance logic unchanged.

### Steps

1. Replace Channel + sequential consumer with `ActiveSnack` stack + overflow queue.
2. Render vertical snackbar rows with per-row timer + Undo + `AnimatedVisibility`.
3. Fold marker-created undo into the stack.
4. Remove `SnackbarHost` / `snackbarHostState`.
5. Build + smoke-test cap, overflow promotion, per-item undo, and timer expiry.
