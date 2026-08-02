# List → Detail Navigation with Scroll Preservation & Prev/Next

**Created:** 2026-08-02 10:33
**Status:** designed
**Feature:** Ui_General

## Problem

When a user selects an item from the tracks or markers list, the map animates to it and a bottom drawer opens with the item's details. Currently:

1. **No scroll preservation**: Dismissing the list destroys the `LazyColumn` composable. Going back from the detail drawer returns to the bare map — the list must be reopened manually from the menu, and it restarts at scroll position 0.
2. **No prev/next for tracks**: The track info bottom drawer has no prev/next navigation.
3. **Marker prev/next uses whereAmI matches**: The marker Viewing drawer's prev/next navigates `selectedMarkerIds` from `whereAmI()` results — not the full filtered/sorted list from `ListOverlayScaffold`.

## Desired Behavior

1. **Back to list with scroll preservation**: When viewing an item opened from the list, hitting the drawer's back icon (no other actions performed) reopens the list at the saved scroll position, with the same filters/sort active.
2. **Prev/next through the full list**: Both track info drawer and marker viewing drawer gain prev/next buttons that navigate through the full filtered/sorted item list (the same list shown in `ListOverlayScaffold`), not just whereAmI proximity matches.

## Current Architecture

### Track Flow
```
Menu → TrackHistoryOverlay (ListOverlayScaffold)
  → tap card → NavigateToItem(id)
    → showTrackHistory = false (list destroyed, LazyColumn state lost)
    → animate map to track
    → trackDrawerState = TrackDrawerState(isOpen=true, track=..., mapWasInteracted=false)
    → bottom drawer shows TrackCardContent (name, stats, no prev/next)
  → BackHandler: if !mapWasInteracted → restore map pos → close drawer
```

### Marker Flow
```
Menu → MarkerManagementOverlay (ListOverlayScaffold)
  → tap card → NavigateToItem(id)
    → showMarkerManagement = false (list destroyed)
    → animate map to marker (600ms)
    → whereAmI() → openEditDrawer(matchedIds, selectedId)
    → MarkerDrawer ViewingContent (prev/next through whereAmI matchedIds)
  → BackHandler: close drawer
```

### Key Observations
- `ListOverlayScaffold` uses a plain `LazyColumn(...)` without a hoisted `LazyListState` — state dies with the composable
- Both lists are toggled via simple `Boolean` flags (`showTrackHistory`, `showMarkerManagement`)
- `NavigateToItem` currently sets the flag to `false`, destroying the list composable
- Track info drawer (`TrackDrawerState`) lives in `MapScreen` as a data class; marker drawer lives in `MarkersViewModel` as `MarkerDrawerState`

## Design Approach

### Option A: Keep List Alive (Hide Instead of Destroy)
- Replace `showTrackHistory: Boolean` / `showMarkerManagement: Boolean` with a visibility state that hides rather than removes the list composable
- When `NavigateToItem` fires, the list stays in composition (just invisible/behind)
- Back from detail drawer → show list again, scroll position naturally preserved

**Pros:** Zero state plumbing, natural scroll preservation
**Cons:** List composable stays in memory; all its subscriptions (collectAsState, etc.) keep running

### Option B: Save/Restore LazyListState (Recommended)
- Hoist `LazyListState` out of `ListOverlayScaffold` (pass as parameter or remember at call site)
- On `NavigateToItem`, save the current `firstVisibleItemIndex` + `scrollOffset` before dismissing
- On back-to-list, pass saved state to restore scroll position

**Pros:** Lightweight, no leaked subscriptions
**Cons:** Requires threading state through MapScreen → OverlayLayer → ListOverlayScaffold

### Decision: Option B
Option B is preferred — it is lighter weight, doesn't keep unnecessary subscriptions alive, and integrates cleanly with the existing boolean-toggle pattern.

## Detailed Design

### 1. Scroll State Preservation

**Approach: Hoist `LazyListState` to `MapScreen`** (simpler than callback plumbing).

`MapScreen` creates two `LazyListState` instances via `rememberLazyListState()` and passes them down to `ListOverlayScaffold`. On `NavigateToItem`, `MapScreen` reads `firstVisibleItemIndex`/`firstVisibleItemScrollOffset` directly from the state, saves them as `SavedScrollState`, then dismisses the list. On reopen, the saved state is passed back to `ListOverlayScaffold` which restores scroll position via `LaunchedEffect`.

```kotlin
// MapScreen.kt
val trackListState = rememberLazyListState()
val markerListState = rememberLazyListState()
var trackListScrollState by remember { mutableStateOf<SavedScrollState?>(null) }
var markerListScrollState by remember { mutableStateOf<SavedScrollState?>(null) }

data class SavedScrollState(
    val firstVisibleItemIndex: Int,
    val scrollOffset: Int
)
```

**On NavigateToItem (track):**
```kotlin
is ListAction.NavigateToItem -> {
    trackListScrollState = SavedScrollState(
        trackListState.firstVisibleItemIndex,
        trackListState.firstVisibleItemScrollOffset
    )
    showTrackHistory = false
    // ... existing navigate logic ...
}
```

**ListOverlayScaffold changes:**
- Accept `lazyListState: LazyListState` parameter (default: internal `rememberLazyListState()`)
- Accept optional `restoredScrollState: SavedScrollState?` — if non-null, `LaunchedEffect` scrolls to it after first composition
- No callback needed — parent reads state directly from the hoisted `LazyListState`

### 2. Prev/Next Through Full List

#### Track Info Drawer
Add prev/next buttons (matching MarkerDrawer's wizard-style pills) below the track stats:

```kotlin
// New parameter on OverlayLayer / track info drawer
val trackListIds: List<String>  // full filtered/sorted track IDs
val currentTrackIndex: Int      // index of current track in trackListIds
```

When prev/next is pressed:
- Compute new index with clamping (no wrap): `(currentIndex - 1).coerceAtLeast(0)` / `(currentIndex + 1).coerceAtMost(lastIndex)`
- "Previous" button disabled at index 0, "Next" disabled at last index
- Load new track detail via `trackViewModel.loadTrackDetailCached(id)`
- Update `trackDrawerState` with new track
- Animate map to new track position (reuse existing `computeTrackNavigateTarget` + `TrackNavigateState` flow)

#### Marker Viewing Drawer
Dual-mode prev/next — source-aware via `DrawerSource` enum (see D5 in Resolved Decisions):

- **Opened from list** (`source = LIST`): prev/next navigates the full filtered/sorted marker list
- **Opened from whereAmI** (`source = WHERE_AM_I`): prev/next navigates whereAmI proximity matches (unchanged)

The `ViewingContent` prev/next rendering is identical — only the ID list and clamping differ. Map animation via existing `animateTo` flow.

### 3. Back Behavior

- **Back icon on drawer → always reopen the list** at saved scroll position (when the item was opened from the list).
- `mapWasInteracted` still controls map position restoration (unchanged logic).
- Delete auto-closes the drawer; subsequent back reopens the list. List refreshes naturally.
- No `itemWasModified` tracking needed — see Back-After-Delete section.

### 4. Data Flow Summary

```
┌─────────────────────────────────────────────────────────────┐
│ MapScreen                                                    │
│   trackListState: LazyListState    ← hoisted, passed down    │
│   markerListState: LazyListState   ← hoisted, passed down    │
│   trackListScrollState: SavedScrollState?                    │
│   markerListScrollState: SavedScrollState?                   │
│   trackListIds: List<String>        ← from TrackViewModel    │
│   markerListIds: List<String>       ← from MarkersViewModel  │
│                                                              │
│   NavigateToItem: read LazyListState → save → dismiss list   │
│                                                              │
│   Track/Marker Drawer:                                       │
│     prev/next → find index in listIds → load new item        │
│     back icon → reopen list with restoredScrollState         │
└─────────────────────────────────────────────────────────────┘
```

## Files Touched

| File | Change |
|------|--------|
| `MapScreen.kt` | Create hoisted `LazyListState`s; `SavedScrollState` holders; `trackListIds`/`markerListIds` derived lists; updated `NavigateToItem` + back-close + prev/next + share/delete handlers |
| `ListOverlayScaffold.kt` | Accept `lazyListState: LazyListState` param (default: internal); accept `restoredScrollState: SavedScrollState?` for scroll restore |
| `OverlayLayer.kt` | Thread `trackListIds`/`markerListIds` + current index; `onPrev`/`onNext`/`onShareTrack`/`onDeleteTrack` callbacks for track drawer |
| `TrackHistoryOverlay.kt` | Thread `lazyListState` param to `ListOverlayScaffold` |
| `MarkerManagementOverlay.kt` | Thread `lazyListState` param to `ListOverlayScaffold` |
| `MarkersViewModel.kt` | Add `DrawerSource` enum + stored field; `openEditDrawer(source)`; `viewPreviousMarker()`/`viewNextMarker()` source-aware (clamp vs wrap); `togglePin(id)` if not present; `_mapCenterRequest` for LIST-mode map animation |
| `MarkerDrawer.kt` | Add pin toggle to `headerActions`; disable prev/next buttons at edges when `source == LIST` (gray + non-clickable at first/last) |

## Resolved Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Option B: save/restore LazyListState | Lighter weight than keeping list alive; no leaked subscriptions |
| D2 | `SavedScrollState` with `firstVisibleItemIndex` + `scrollOffset` | Standard Compose LazyListState serialization; sufficient for scroll restoration |
| D3 | Prev/next clamp at edges with button disable | No wrap-around. "Previous" disabled at first item, "Next" disabled at last. Clearer UX than wrapping. |
| D4 | Map animates to each item on prev/next | Map follows the selected item when navigating prev/next through the list |
| D5 | Marker prev/next source-aware | If drawer opened from list → prev/next follows filtered list order. If opened from whereAmI or other trigger → prev/next follows whereAmI matches (existing behavior). |
| D6 | Back always reopens list after edit/delete | No `itemWasModified` tracking. Delete auto-closes drawer → back reopens list at saved scroll. List refreshes naturally. |

## Marker Prev/Next Dual Mode

`MarkersViewModel.openEditDrawer()` gains a `source: DrawerSource` parameter:

```kotlin
enum class DrawerSource { LIST, WHERE_AM_I }

fun openEditDrawer(
    markerIds: List<String>,
    selectedId: String? = null,
    source: DrawerSource = DrawerSource.WHERE_AM_I  // default = existing behavior
)
```

**Navigation behavior per source:**
- `source = LIST` → `viewPreviousMarker()`/`viewNextMarker()` **clamp** at edges (no wrap), buttons disabled at first/last. Map animates to each marker.
- `source = WHERE_AM_I` → existing **wrapping** behavior preserved (first ↔ last). No map animation on prev/next. No button disable.

The `DrawerSource` is stored as a field in the ViewModel so `viewPreviousMarker()`/`viewNextMarker()` can branch on it.

## Review Notes (Ask audit 2026-08-02)

| # | Finding | Resolution |
|---|---------|------------|
| R1 | `viewPreviousMarker()`/`viewNextMarker()` currently wrap — clamping would regress WHERE_AM_I mode | Source-aware: clamp for LIST, wrap for WHERE_AM_I (see D5) |
| R2 | `onScrollStateChanged` callback over-engineered | Hoist `LazyListState` to `MapScreen`, read directly — simpler, no callback needed |
| R3 | Track prev/next map animation needs `TrackNavigateState` re-trigger | Reuse existing `LaunchedEffect(trackNavigateState)` — set navigate state on prev/next |
| R4 | Marker prev/next map animation needs `NavigateTarget` trigger | For LIST mode: emit navigate target alongside index change |
| R5 | Live track in `trackListIds` | Exclude live track ID from prev/next list |

## Edge Cases (verify at implementation)

| # | Case | Mitigation |
|---|------|------------|
| E1 | `trackViewModel.deleteTrack(id)` — does method exist? | Verify; mirror `MarkersViewModel.deleteMarker` if absent |
| E2 | `markersViewModel.togglePin(id)` — does method exist? | Marker list has pin toggle; verify it's on ViewModel, not just overlay |
| E3 | Track prev/next after delete — `trackListIds` stale | `refreshSummaries()` should update; adjust index if current item was deleted |
| E4 | `drawerSource` field — must reset on drawer close | `closeDrawer()` should reset `drawerSource` to null/default |

## Missing Drawer Actions (gap fill)

| Drawer | Action | Status | Implementation |
|---|---|---|---|
| Track | 📤 Share GPX | **Done** | Wired to `MapScreen.shareTrackGpx()` |
| Track | 🗑️ Delete | **Designed** — snackbar + Undo + reopen | `PendingDelete` deferred, `SnackbarHost` at MapScreen level |
| Marker | 🗑️ Delete | **Designed** — snackbar + Undo + reopen (replaces `ConfirmSheet`) | Same deferred pattern, `openEditDrawer(id)` on undo |
| Marker | 📌 Pin toggle | **Done** | `PushPin` icon in `headerActions` → `markersViewModel.togglePin(id)` |

## Delete UX — Normalized

All single-item deletes follow snackbar + Undo:

Context | Undo behavior |
|---|---|
List (swipe) | Card slides back |
Drawer | Restore + **reopen drawer** |

**Flow:** Delete → save to `pendingDelete` → close drawer → snackbar "X deleted" [Undo] → Undo restores + reopens drawer, timeout = permanent.

## Back-After-Delete (resolved)

- Back always reopens the list when item was opened from list.
- Delete is deferred (pending until snackbar timeout).
- No `itemWasModified` tracking.
