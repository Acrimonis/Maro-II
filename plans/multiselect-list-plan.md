# Multiselect List — Implementation Plan

**Feature:** Ui_General → multi-select
**Branch:** feature/multiselect
**Date:** 2026-07-12

## Overview

Add long-press multiselect to `ListOverlayScaffold<T>`. Scaffold owns all selection state; consumers inject per-type multi-actions via `MultiActionSpec` list.

## Architecture

```
ListOverlayScaffold<T>
├── isMultiSelectMode: Boolean              (internal state)
├── selectedIds: SnapshotStateList<String>  (internal state)
├── multiActions: List<MultiActionSpec>     (consumer-injected)
│
├── Header (normal mode)
│   └── Back + Title + filter/sort row
│
├── Header (multiselect mode)
│   ├── Close (X) + "N selected"
│   ├── "Select all" / "Deselect all" chip
│   └── Filter/sort row hidden
│
├── LazyColumn
│   └── Cards with checkmark overlay + tonal shift + border on selected
│
└── Bottom action bar (multiselect mode only)
    └── Consumer-injected action buttons (scrollable Row)
```

---

## Common Framework — Complete Specification

### MultiActionSpec

```kotlin
// New file: app/src/main/java/ykws/android/maro/data/model/MultiActionSpec.kt
data class MultiActionSpec(
    val id: String,                              // "delete", "export", "pin", "unpin"
    val label: String,                           // displayed on button
    val icon: ImageVector,                       // button icon
    val action: (Set<String>) -> Unit,           // consumer lambda — receives selected IDs
    val enabled: (Set<String>) -> Boolean = { it.isNotEmpty() },  // dynamic dimming
    val isDestructive: Boolean = false           // uiDashboardZoneDanger tint when true
)
```

- **`enabled`** receives current `selectedIds` set. Consumer captures items in closure to inspect per-item state (e.g., pin status).
- **`isDestructive`** switches button tint to `uiDashboardZoneDanger` (#C62828). Delete only.
- **`action`** fires with selected IDs. Scaffold auto-exits multiselect after action (clears selection, returns to normal mode).
- **Select-all is scaffold-owned**, not a MultiActionSpec.

### Scaffold API Addition

```kotlin
@Composable
fun <T : ListableItem> ListOverlayScaffold(
    // ... existing params unchanged ...
    multiActions: List<MultiActionSpec> = emptyList(),  // NEW
    // ...
)
```

No other parameter changes. Selection state is entirely internal.

### Scaffold Internal State

```kotlin
// Inside ListOverlayScaffold:
var isMultiSelectMode by remember { mutableStateOf(false) }
val selectedIds = remember { mutableStateListOf<String>() }

// Derived:
val nonLiveCount = items.count { !it.isLive }
val selectedCount get() = selectedIds.size
val allSelected get() = selectedCount == nonLiveCount && nonLiveCount > 0
```

### State Machine

```
                         ┌── back_press / close(X) ──→ exit, clear selection
                         │
NORMAL ──long_press(id)──→ MULTISELECT (id selected)
  ↑        (non-live         │
  │         only)            ├── tap(id) → toggle selection
  │                          │     if was last selected → auto-exit to NORMAL
  │                          │
  │                          ├── long_press(id) → toggle selection (same as tap)
  │                          │
  │                          ├── "Select all" → select all non-live IDs
  │                          │     chip flips to "Deselect all"
  │                          │
  │                          ├── "Deselect all" → clear all, exit to NORMAL
  │                          │
  │                          ├── multi_action_fired → execute, exit to NORMAL
  │                          │
  │                          └── (items list becomes empty) → exit to NORMAL
  │
  └── back_press (normal mode) → commit pending deletes, dismiss overlay
```

### Entry Conditions

| Condition | Behavior |
|---|---|
| Items list has 1+ non-live item | Long-press enters multiselect |
| All items are live (`isLive=true`) | Long-press is no-op; multiselect cannot be entered |
| `multiActions` is empty | Long-press still enters multiselect — selection visuals work, bottom bar absent |

### Entry — Pending Deletes Handling

On entering multiselect mode, any existing `pendingDeletes` (from prior swipe-to-delete in normal mode) are **committed immediately** — `ListAction.PermanentDelete` emitted for each pending ID, then `pendingDeletes` cleared. Prevents entering multiselect with items in a half-deleted snackbar state.

### Tap Interception — Overlay Approach

During multiselect mode, each card gets a **transparent `.clickable` overlay** on top of the consumer's `cardContent`. The consumer's own tap handlers (e.g., `NavigateToItem` in `MarkerCardContent`) are NOT modified — the overlay consumes the touch first.

```kotlin
Box {
    cardContent(item)  // Layer 1: consumer's card (may have its own clickable)
    // Layer 2: selection visuals (tonal shift + checkmark) — only when selected
    if (isSelected) { /* tonal shift Box + checkmark circle */ }
    // Layer 3: tap interceptor — only in multiselect mode
    if (isMultiSelectMode) {
        Box(Modifier.matchParentSize().clickable { toggleSelection(item.id) })
    }
}
// Outer border: .border(if (isSelected) 1.dp else 0.dp, uiSettingsAccent, shape)
```

### Swipe-to-Delete During Multiselect

`SwipeableItemCard`'s `.pointerInput { detectHorizontalDragGestures(...) }` block is **gated** on `!isMultiSelectMode`. In multiselect mode, horizontal drags are ignored.

### Filter/Sort During Multiselect

Entire filter+sort+reset row is **hidden** when `isMultiSelectMode` is true. Filtering would change the item list and invalidate selection.

### Item List Changes During Multiselect

If `items` changes while in multiselect mode (external source, etc.):
- `selectedIds` entries that no longer exist in `items` are **silently dropped**
- If `selectedIds` becomes empty → auto-exit multiselect
- Handled via `LaunchedEffect(items)` reconciling `selectedIds` against `items.map { it.id }`

### BackHandler Behavior

```
Back press:
├── isMultiSelectMode == true  → exit multiselect, clear selection (do NOT dismiss overlay)
└── isMultiSelectMode == false → commit pendingDeletes, dismiss overlay (existing behavior)
```

### Select-All / Deselect-All Chip

Rendered in the header row, right-aligned:

| State | Chip text | Action |
|---|---|---|
| `selectedCount < nonLiveCount` | "Select all" | Add all non-live IDs to `selectedIds` |
| `selectedCount == nonLiveCount` (all selected) | "Deselect all" | Clear `selectedIds`, exit multiselect |

`TextButton` with `uiSettingsAccent` text color. Always visible in multiselect when there are non-live items.

### Bottom Action Bar

```
┌──────────────────────────────────────────────────────────┐
│  [icon Delete]  [icon Export]  [icon Pin]  [icon Unpin] │
└──────────────────────────────────────────────────────────┘
```

| Property | Value |
|---|---|
| Height | 56dp |
| Background | `uiCardBackground` (#16213E) at full opacity |
| Layout | `Row`, `Arrangement.SpaceEvenly`, `horizontalScroll` if >4 actions |
| Button | `TextButton` with icon (20dp) + label, `ButtonColors.icon` tint |
| Destructive | `uiDashboardZoneDanger` (#C62828) tint when `isDestructive = true` |
| Disabled | `ButtonColors.inactiveAlpha` (0.25) when `enabled(selectedIds)` is false |
| Animation | `AnimatedVisibility(enter = slideInVertically + fadeIn, exit = slideOutVertically + fadeOut)` |

**Auto-exit after action:** After `action(selectedIds)` fires, scaffold clears `selectedIds` and exits multiselect. If a future action needs "stay in mode", add `exitsAfterAction: Boolean = true` to `MultiActionSpec`.

### Pin/Unpin Semantics

Separate "Pin" and "Unpin" `MultiActionSpec` entries. Each uses `enabled` predicate:

```kotlin
// MarkerManagementOverlay — captures `markers` in closure
MultiActionSpec(
    id = "pin",
    label = stringResource(R.string.action_pin),
    icon = Icons.Filled.PushPin,
    enabled = { ids -> ids.any { id -> markers.any { it.id == id && !it.isPinned } } },
    action = { ids -> /* batch set isPinned = true */ }
),
MultiActionSpec(
    id = "unpin",
    label = stringResource(R.string.action_unpin),
    icon = Icons.Outlined.PushPin,
    enabled = { ids -> ids.any { id -> markers.any { it.id == id && it.isPinned } } },
    action = { ids -> /* batch set isPinned = false */ }
)
```

- "Pin" dimmed if ALL selected are already pinned
- "Unpin" dimmed if ALL selected are already unpinned
- Both active simultaneously for mixed selection

### Batch Delete — No Undo

Batch delete skips the snackbar undo pattern. Rationale: batch undo is complex (restore N items), and batch delete is a deliberate multi-step action (long-press + select + tap). Deletion is immediate.

---

## Visual Feedback (Option A — confirmed + tonal shift)

| Layer | Token / Spec |
|---|---|
| Tonal shift | `uiCardBackground` + `Color.White.copy(alpha = 0.15f)` overlay |
| Border | 1dp `uiSettingsAccent` (#1565C0), `RoundedCornerShape(12.dp)` |
| Checkmark circle | 24dp, `uiSettingsAccent` fill, `CircleShape` |
| Checkmark icon | White `Icons.Filled.Check`, 16dp, centered in circle |
| Position | `Alignment.TopEnd`, 4dp padding |
| Accent stripe | Unchanged — 4dp, per-item identity color |
| Animation | Tonal shift + border: instant. Checkmark: crossfade 200ms |

### Card Box Structure (selected + multiselect mode)

```
Box (outer, with conditional border)
├── Box (matchParentSize)
│   ├── cardContent(item)                    ← consumer's slot
│   └── if (isSelected):
│       ├── Box(matchParentSize, bg=White@15%)   ← tonal shift
│       └── Box(TopEnd, 4dp, 24dp, CircleShape, uiSettingsAccent)
│           └── Icon(Check, White, 16dp)         ← checkmark
└── if (isMultiSelectMode):
    └── Box(matchParentSize, .clickable { toggle })  ← tap interceptor
```

---

## Per-Consumer Multi-Actions

### MarkerManagementOverlay

```kotlin
val markerMultiActions = remember(markers) {
    listOf(
        MultiActionSpec(
            id = "delete",
            label = stringResource(R.string.action_delete),
            icon = Icons.Filled.Delete,
            isDestructive = true,
            action = { ids -> ids.forEach { onAction(ListAction.PermanentDelete(it)) } }
        ),
        MultiActionSpec(
            id = "pin",
            label = stringResource(R.string.action_pin),
            icon = Icons.Filled.PushPin,
            enabled = { ids -> ids.any { id -> markers.any { it.id == id && !it.isPinned } } },
            action = { ids -> /* batch pin via markersViewModel */ }
        ),
        MultiActionSpec(
            id = "unpin",
            label = stringResource(R.string.action_unpin),
            icon = Icons.Outlined.PushPin,
            enabled = { ids -> ids.any { id -> markers.any { it.id == id && it.isPinned } } },
            action = { ids -> /* batch unpin via markersViewModel */ }
        )
    )
}
```

### TrackHistoryOverlay

```kotlin
val trackMultiActions = remember(tracks) {
    listOf(
        MultiActionSpec(
            id = "delete",
            label = stringResource(R.string.action_delete),
            icon = Icons.Filled.Delete,
            isDestructive = true,
            action = { ids -> ids.forEach { onAction(ListAction.PermanentDelete(it)) } }
        ),
        MultiActionSpec(
            id = "export",
            label = stringResource(R.string.action_export),
            icon = Icons.Filled.Share,
            action = { ids -> ids.forEach { onAction(ListAction.ExportGpx(it)) } }
        ),
        MultiActionSpec(
            id = "pin",
            label = stringResource(R.string.action_pin),
            icon = Icons.Filled.PushPin,
            enabled = { ids -> ids.any { id -> tracks.any { it.id == id && !it.isPinned } } },
            action = { ids -> /* batch pin via trackViewModel */ }
        ),
        MultiActionSpec(
            id = "unpin",
            label = stringResource(R.string.action_unpin),
            icon = Icons.Outlined.PushPin,
            enabled = { ids -> ids.any { id -> tracks.any { it.id == id && it.isPinned } } },
            action = { ids -> /* batch unpin via trackViewModel */ }
        )
    )
}
```

---

## Key Implementation Details

1. **Long-press handler:** `Modifier.combinedClickable(onLongClick = { enterMultiselect(id) })` on the card wrapper. Gated: only when `!isMultiSelectMode && !item.isLive`.

2. **Tap dispatch in multiselect:** Transparent overlay Box above cardContent intercepts taps. Consumer's `.clickable` is untouched.

3. **Swipe-to-delete gate:** `SwipeableItemCard` drag gesture gated on `!isMultiSelectMode`.

4. **Live items:** No `combinedClickable`, no selection toggle, no checkmark. Already sorted first.

5. **Pending deletes commit on entry:** `pendingDeletes.forEach { onAction(PermanentDelete(it)) }; pendingDeletes.clear()`.

6. **Item reconciliation:** `LaunchedEffect(items) { selectedIds.retainAll { id -> items.any { it.id == id } }; if (selectedIds.isEmpty()) exitMultiselect() }`.

7. **Animation:** Bottom bar `slideInVertically + fadeIn`. Checkmark crossfade 200ms. Tonal shift instant.

---

## Files Changed

| File | Change |
|------|--------|
| `data/model/MultiActionSpec.kt` | **New** — data class with `id`, `label`, `icon`, `action`, `enabled`, `isDestructive` |
| `ui/components/ListOverlayScaffold.kt` | Internal state, long-press, overlay, header transform, bottom bar, select-all, back handler gating, pending commit on entry, item reconciliation |
| `ui/map/MarkerManagementOverlay.kt` | Inject `markerMultiActions` (delete, pin, unpin) |
| `ui/map/TrackHistoryOverlay.kt` | Inject `trackMultiActions` (delete, export, pin, unpin) |
| `docs/ui-lists-guidelines.md` | Multiselect framework section |
| `xTrack/Ui_General/FEAT_DSC_Ui_General.md` | Update multi-select subfeature (already scaffolded) |

## Phase 2 — Track Action Refinements

### 1. Deactivate Markers

`MarkerManagementOverlay`: `multiActions = emptyList()`. Long-press becomes no-op in marker list.

### 2. Batch Export

| Selection | Behavior |
|---|---|
| 1 track | Single `.gpx` (unchanged) |
| 2+ tracks | Zip into `maro-tracks-yyyy_MM_dd_HHmmss.zip` |

New method in `TrackViewModel` or `MapScreen`: collect GPX data for selected IDs, zip to cache dir, share intent.

### 3. Confirmation Dialog

```kotlin
// MultiActionSpec new field:
val confirmMessage: String? = null
```

Scaffold: if `confirmMessage != null`, show `AlertDialog` before firing `action`. Only delete gets confirmation. Pin/Export — no dialog.

### 4. Pin Multi-Choice

Replace Pin+Unpin buttons with one "Pin" button that opens `DropdownMenu`:

```kotlin
data class MultiActionSubSpec(
    val id: String,
    val label: String,
    val action: (Set<String>) -> Unit
)
```

```kotlin
// MultiActionSpec new field:
val subActions: List<MultiActionSubSpec> = emptyList()
```

Options: "Pin all", "Unpin all", "Toggle pins". Always enabled.

### Final MultiActionSpec

```kotlin
data class MultiActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val action: (Set<String>) -> Unit = {},
    val enabled: (Set<String>) -> Boolean = { it.isNotEmpty() },
    val isDestructive: Boolean = false,
    val confirmMessage: String? = null,
    val subActions: List<MultiActionSubSpec> = emptyList()
)
```

### Track Action Bar (final)

```
[Delete] [Export] [Pin]
   ^         ^       ^
 confirm    zip     popup: Pin all / Unpin all / Toggle
 dialog     2+
```

### Files Changed

| File | Change |
|---|---|
| `MultiActionSpec.kt` | +`confirmMessage`, +`subActions`, +`MultiActionSubSpec` |
| `ListOverlayScaffold.kt` | AlertDialog for confirm, DropdownMenu for subActions |
| `TrackHistoryOverlay.kt` | Pin multi-choice, delete confirm, export zip |
| `MarkerManagementOverlay.kt` | `multiActions = emptyList()` |
| `TrackViewModel.kt` / `MapScreen.kt` | Batch export zip |
| `strings.xml` + `values-fr/` | Confirm delete, pin sub-action labels |

## Open Questions

- **Batch pin/unpin at ViewModel level:** Does `TrackViewModel` / `MarkersViewModel` have batch pin methods? Will follow existing per-item pattern initially via `onAction` loop.
- **Undo after batch delete:** No undo for MVP. Could add confirmation dialog if desired.
- **Stay-in-mode after action:** Not needed for delete/export/pin. Add `exitsAfterAction` field to `MultiActionSpec` if future actions need it.
