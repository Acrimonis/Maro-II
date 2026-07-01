# UI Lists Guidelines

Canonical reference for `ListOverlayScaffold<T : ListableItem>` — the shared list
overlay composable used by [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)
and [`MarkerManagementOverlay`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt).

## Architecture

```
ListOverlayScaffold<T>
├── Header: back button + title
├── SortControl: dropdown (field selector + direction + pinned grouping)
├── LazyColumn
│   ├── isLive → liveCardContent(T)         (no swipe, always top)
│   └── !isLive → SwipeableItemCard
│       ├── SwipeState.CARD     → cardContent(T)
│       ├── SwipeState.SNACKBAR → SnackbarSlot
│       └── SwipeState.DELETED  → removed
└── onAction: (ListAction) → Unit
```

## API

```kotlin
@Composable
fun <T : ListableItem> ListOverlayScaffold(
    items: List<T>,
    title: String,
    sectionLabel: String,
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    accentColors: (List<T>) -> Map<String, Color>,
    cardContent: @Composable (T) -> Unit,
    liveCardContent: @Composable (T) -> Unit = {},
    emptyState: @Composable () -> Unit = {},
    onAction: (ListAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```

| Parameter | Role |
|-----------|------|
| `items` | Sorted list of `T : ListableItem` — scaffold sorts `isLive` first |
| `title` / `sectionLabel` | Header text |
| `sortState` / `onSortStateChange` | Persisted sort state, toggled by scaffold's `SortControl` |
| `accentColors` | Batch lambda — called once per items change, returns `Map<id, Color>` |
| `cardContent` | Standard card slot — wrapped in `SwipeableItemCard` |
| `liveCardContent` | Live item slot — no swipe, rendered for `isLive = true` |
| `emptyState` | Rendered when `items` is empty |
| `onAction` | Single callback for all scaffold-emitted actions |
| `onDismiss` | Close the overlay (back button or back handler) |

## Sort State

```kotlin
data class ListSortState(
    val field: ListSortField = ListSortField.UPDATED,
    val descending: Boolean = true,
    val pinnedGrouped: Boolean = false
)
```

Serialized as `"UPDATED:true:false"` → `field:descending:pinnedGrouped`.

### Sort Priority Chain

1. `isLive = true` → always top (hard override, not in dropdown)
2. `pinnedGrouped = true` → pinned items group at top within non-live
3. Chosen `field` + `descending`

## Sort Dropdown UI

`SortControl` renders:
- Sort icon button (26dp, `uiSettingsTextPrimary`)
- Dropdown menu items (one per `ListSortField.entries`)
  - Checked item: ✓ prefix + direction arrow (↓↑) suffix
  - Tap checked → toggle direction
  - Tap unchecked → select field
- Separator + "Group pinned items" toggle (☑/☐)

## Swipe-to-Delete

State machine: `CARD → SNACKBAR → DELETED`

| State | Trigger | Action |
|-------|---------|--------|
| `CARD → SNACKBAR` | Swipe left > 30% card width | Emit `SoftDelete`, add to `pendingDeletes` |
| `SNACKBAR → CARD` | Tap "Undo" | Emit `UndoDelete`, remove from `pendingDeletes` |
| `SNACKBAR → DELETED` | Swipe snackbar left > 30% | Emit `PermanentDelete`, remove from `pendingDeletes` |
| Back press | Dismiss with pending | Emit `PermanentDelete` for each pending ID, then `onDismiss()` |

Animations: card enter/exit `spring()` (Compose platform defaults), snackbar enter/exit `tween(250)`.

## Deferred Batch Delete

Scaffold owns `pendingDeletes: SnapshotStateList<String>` internally.
- Swipe adds to set, shows snackbar
- Undo removes from set, slides card back
- Dismiss commits: emits `PermanentDelete` per ID, consumer calls ViewModel

Both lists use this pattern — no per-list variation.

## ListAction

```kotlin
sealed class ListAction {
    data class SoftDelete(val id: String, val title: String) : ListAction()
    data class UndoDelete(val id: String) : ListAction()
    data class PermanentDelete(val id: String) : ListAction()
    data class SelectItem(val id: String) : ListAction()
    data class EditItem(val id: String) : ListAction()
    data class ExportGpx(val id: String) : ListAction()
    data class RefreshList(val sortState: ListSortState) : ListAction()
    data object RefreshLayer : ListAction()
}
```

## ListableItem Interface

```kotlin
interface ListableItem {
    val id: String
    val title: String
    val description: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
    val isPinned: Boolean
    val isLive: Boolean get() = false
}
```

Implementors: [`TrackSummary`](app/src/main/java/ykws/android/maro/data/track/Track.kt:52), [`UserMarker`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:32).

## Visual Tokens

| Element | Token | Value |
|---------|-------|-------|
| Overlay background | `uiSettingsBackground` | `#FF1A1A2E` |
| Card background | `uiCardBackground` | `#FF16213E` |
| Accent stripe | `accentColor` | Per-item (computed by batch lambda) |
| Header text | `uiSettingsTextPrimary` | `#FFFFFF` |
| Section label | `uiSettingsAccent` | `#1565C0` |
| Back button bg | `uiSettingsSwitchTrackInactive` | `#FF0F3460` |
| Divider | `uiSettingsDivider` | low-alpha white |
| Snackbar bg | `uiCardBackground` @ 7.65% alpha | |
| Undo text | `uiSettingsAccent` | `#1565C0` |

## Key Files

| File | Role |
|------|------|
| [`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt) | Generic scaffold composable |
| [`ListSortOrder.kt`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt) | `ListSortField` enum + `ListSortState` data class |
| [`ListAction.kt`](app/src/main/java/ykws/android/maro/data/model/ListAction.kt) | Action sealed class |
| [`ListableItem.kt`](app/src/main/java/ykws/android/maro/data/model/ListableItem.kt) | Common item interface |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | Track list consumer |
| [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) | Marker list consumer |
| [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) | Wiring layer |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | Track sort comparator |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | Marker sort comparator |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | Sort state persistence |
