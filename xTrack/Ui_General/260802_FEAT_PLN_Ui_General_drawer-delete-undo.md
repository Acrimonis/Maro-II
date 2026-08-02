# Drawer Delete — Snackbar + Undo + Reopen

**Created:** 2026-08-02 14:55
**Status:** designed
**Feature:** Ui_General
**Parent:** list-detail-navigation

## Problem

Delete from drawer has no undo:
- **Track drawer**: immediate delete, no confirmation
- **Marker drawer**: `ConfirmSheet` dialog (blocking, inconsistent with list pattern)

## Desired Behavior

Single pattern: **delete → close drawer → snackbar "Deleted" + Undo → timeout = permanent**.

If Undo pressed → restore item → **reopen drawer** showing the same item (Option B).

## Design

### Deferred Delete Pattern (mirrors list `pendingDeletes`)

```
[Delete icon]
  → save item ID to pendingDelete state
  → close drawer
  → show snackbar: "Track/Marker 'X' deleted" [Undo]
  
  ┌─ Snackbar dismissed (timeout) ─┐     ┌─ Undo pressed ──────────────┐
  │ → execute actual delete        │     │ → clear pendingDelete       │
  │ → refresh summaries/markers    │     │ → reopen drawer with item   │
  │ → (item gone)                  │     │ → snackbar dismisses        │
  └────────────────────────────────┘     └─────────────────────────────┘
```

### State

```kotlin
// MapScreen.kt
var pendingTrackDelete by remember { mutableStateOf<PendingDelete?>(null) }
var pendingMarkerDelete by remember { mutableStateOf<PendingDelete?>(null) }

data class PendingDelete(
    val id: String,
    val name: String  // for snackbar text
)
```

### Snackbar

Uses existing `SnackbarHost` at MapScreen level. Already used for marker creation undo.

```kotlin
LaunchedEffect(pendingTrackDelete) {
    pendingTrackDelete?.let { pending ->
        val result = snackbarHostState.showSnackbar(
            message = "Track '${pending.name}' deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            // Undo: reopen drawer
            trackScope.launch {
                val track = trackViewModel.loadTrackDetailCached(pending.id)
                if (track != null) {
                    highlightedTrackId = pending.id
                    trackDrawerState = TrackDrawerState(isOpen = true, track = track)
                }
            }
        } else {
            // Timeout: permanent delete
            trackViewModel.deleteTrack(pending.id)
        }
        pendingTrackDelete = null
    }
}
```

Same pattern for `pendingMarkerDelete` — uses `markersViewModel.deleteMarker(id)` on timeout, or `markersViewModel.openEditDrawer(id)` on undo.

### Marker ConfirmSheet

**Removed.** The snackbar+undo pattern replaces it. The undo is the safety net — no need for a blocking dialog.

## Files Touched

| File | Change |
|------|--------|
| `MapScreen.kt` | `PendingDelete` data class, `pendingTrackDelete`/`pendingMarkerDelete` state, `LaunchedEffect` snackbar orchestration, update delete handlers to defer |
| `OverlayLayer.kt` | Track drawer delete: remove `onTrackDrawerClose()` call (close is handled by MapScreen state change) |
| `MarkerDrawer.kt` | Remove `ConfirmSheet` and `showDeleteConfirm` state; delete button now emits action directly |

## Edge Cases

| # | Case | Behavior |
|---|------|----------|
| E1 | Undo, then delete again from reopened drawer | New `pendingDelete` replaces old one |
| E2 | Navigate prev/next while delete pending | Pending delete ID stays on original item — prev/next does not affect it |
| E3 | App killed while delete pending | Item survives (delete never executed) — safe default |

## Validation (Ask review 2026-08-02)

- 🔴 **E2 fixed**: Pending delete ID no longer updates on prev/next — stays on original deleted item.
- 🟡 **E4 removed**: "Undo after simultaneous list delete" — unrealistic edge case, removed.
- ✅ Deferred pattern consistent with list `pendingDeletes`.
- ✅ `SnackbarHost` reused — no new infrastructure.
