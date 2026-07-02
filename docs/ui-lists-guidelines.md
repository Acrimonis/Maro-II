# UI Lists Guidelines

Canonical reference for `ListOverlayScaffold<T : ListableItem>` — the shared list
overlay composable used by [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)
and [`MarkerManagementOverlay`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt).

## Architecture

```
ListOverlayScaffold<T>
├── Header: back button + title
├── Filter + Sort row
│   ├── FilterControl: dropdown (sectioned filter axes)
│   ├── SortControl: dropdown (field selector, no direction arrow)
│   ├── DirectionToggle: ArrowDropUp/Down (instant toggle)
│   └── ResetButton: Refresh icon (clears filter + sort to defaults)
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
    customSortFields: List<CustomSortField> = emptyList(),
    customSortLabel: String = "Custom",
    filterState: ListFilter = ListFilter(),
    filterAxes: List<FilterAxisSpec> = emptyList(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
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
| `items` | Filtered + sorted list — scaffold sorts `isLive` first |
| `title` / `sectionLabel` | Header text |
| `sortState` / `onSortStateChange` | Persisted sort state |
| `customSortFields` | Per-type custom sort fields (track: distance/time, marker: origin) |
| `customSortLabel` | Context-aware section label ("Tracks" / "Markers") |
| `filterState` / `onFilterChange` | Filter state (Map-based `ListFilter`) |
| `filterAxes` | Per-type filter axis specs for dropdown rendering |
| `onReset` | Clears filter + sort to defaults |
| `accentColors` | Batch lambda — called once per items change, returns `Map<id, Color>` |
| `cardContent` | Standard card slot — wrapped in `SwipeableItemCard` |
| `liveCardContent` | Live item slot — no swipe, rendered for `isLive = true` |
| `emptyState` | Rendered when items is empty; shows "No items match filters" + clear button when filter active |

## Sort State

```kotlin
enum class ListSortField(val labelResId: Int) {
    TITLE(R.string.sort_common_title),
    CREATED(R.string.sort_common_created)
}

data class ListSortState(
    val field: ListSortField = ListSortField.CREATED,
    val descending: Boolean = true,
    val customFieldKey: String? = null   // non-null → custom field active
)
```

Serialized as `"CREATED:true"` or `"CREATED:true:distanceNm"` → `field:descending[:customFieldKey]`.

No `pinnedGrouped` — pinned grouping is a filter axis, not a sort modifier.
No `UPDATED` — removed, default is `CREATED` descending.

### Sort Priority Chain

1. `isLive = true` → always top (hard override, not in dropdown)
2. Chosen `field`/`customFieldKey` + `descending`

### Shared sort logic

[`ListSortState.applySort<T : ListableItem>()`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt:39) centralizes field dispatch, direction reversal, and `pinnedGrouped`-free sorting. Each ViewModel provides only its type-specific custom comparator lambda.

## Filter System

### ListFilter Model

```kotlin
data class ListFilter(val axes: Map<String, String> = emptyMap())
// Serialized: key1=value1;key2=value2 — absent key = default (ALL)
```

### Track Filter Axes

| Key | Options | Default |
|-----|---------|---------|
| `dateRange` | `ALL` / `THIS_YEAR` / `LAST_30_DAYS` / `LAST_7_DAYS` | `ALL` |
| `pinned` | `ALL` / `PINNED` | `ALL` |

Live track always exempt from filter + always first.

### Marker Filter Axes

| Key | Options | Default |
|-----|---------|---------|
| `pinned` | `ALL` / `PINNED` / `UNPINNED` | `ALL` |
| `geometry` | `ALL` / `PINS` / `ZONES` | `ALL` |
| `origin` | `ALL` / `MANUAL` / `AUTO` | `ALL` |

Cascade: `geometry=ZONES` → `origin` bypassed (zones always manual). UI: origin dropdown disabled.

### Date Range Semantics

Day-based from midnight — stable all day. No millisecond-precise boundaries.

```kotlin
fun dateInRange(startTimeMs: Long, range: String, todayMidnightMs: Long): Boolean
```

### Filter Predicates

Extension functions on `TrackSummary` and `UserMarker` in [`ListFilter.kt`](app/src/main/java/ykws/android/maro/data/model/ListFilter.kt).

### Filter Axis Spec

```kotlin
data class FilterAxisSpec(
    val key: String,
    val label: String,
    val options: List<FilterOptionSpec>,
    val dependsOn: String? = null,        // parent axis key
    val dependsOnValue: String? = null     // disables this axis when parent = this value
)
data class FilterOptionSpec(val value: String, val label: String, val isDefault: Boolean = false)
```

## Popup Menu Styling

Filter and sort popups follow settings page hierarchy per [`ui-component-guidelines.md`](ui-component-guidelines.md):

```
┌─ Popup → Surface (uiSettingsBackground, 12dp, 1dp 0x40FFFFFF border) ─┐
│  Section Title (SubSectionHeader style)                                 │
│  ┌─ Card → Surface (uiCardBackground, 12dp) ─────────────────────────┐ │
│  │  Row (16dp h-pad, 2dp v-pad): checkmark box (24dp) + text         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  Next Section Title                                                     │
│  ┌─ Card ... ────────────────────────────────────────────────────────┐ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

| Token | Value | Role |
|-------|-------|------|
| Popup bg | `uiSettingsBackground` | Outer Surface |
| Popup border | `0x40FFFFFF`, 1dp | Settings expander border style |
| Card bg | `uiCardBackground` | Per-section card |
| Section title | `uiDashboardTextMuted`, 16sp, SemiBold | SubSectionHeader style |
| Row text | `uiSettingsTextPrimary`, 15sp, Medium (selected: SemiBold) | |
| Checkmark | `uiSettingsAccent`, 16sp, SemiBold | ✓ for selected |
| Row v-padding | 2dp | Tight — matches settings toggle rows |
| Card v-padding | 8dp | |
| Card gap | 4dp | `Arrangement.spacedBy(4.dp)` |

All popup icons use `ButtonColors.icon` tint + `ButtonColors.iconSizeDp` (28dp) + `.alpha(activeAlpha/inactiveAlpha)` per [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt).

## Header Row Icons

| Icon | Material | Role | Active state |
|------|----------|------|-------------|
| Filter button | `FilterAlt` (funnel, standalone .kt) | Opens filter popup | `activeAlpha` when `axes.isNotEmpty()` |
| Sort button | `FilterList` (3 descending bars, standalone .kt) | Opens sort popup | `activeAlpha` when field ≠ CREATED or customFieldKey ≠ null or !descending |
| Direction toggle | `ArrowDropUp` / `ArrowDropDown` (core) | Instant toggle | Same as sort (inactive at default) |
| Reset | `Refresh` (circular arrow, standalone .kt) | Clears filter + sort | Action — no state |

## ViewModel Pattern

Both ViewModels use an unfiltered backing list + filter→sort pipeline:

```
_allItems (unfiltered source of truth)
       │ settings.collect emission
       ▼
  filter → sort → _displayedItems StateFlow
```

- `TrackViewModel`: `_allSummaries` → filter → sort → `_summaries`
- `MarkersViewModel`: `_allMarkers` → filter → sort → `_markers`

## Swipe-to-Delete

State machine: `CARD → SNACKBAR → DELETED`

| State | Trigger | Action |
|-------|---------|--------|
| `CARD → SNACKBAR` | Swipe left > 30% card width | Emit `SoftDelete`, add to `pendingDeletes` |
| `SNACKBAR → CARD` | Tap "Undo" | Emit `UndoDelete`, remove from `pendingDeletes` |
| `SNACKBAR → DELETED` | Swipe snackbar left > 30% | Emit `PermanentDelete`, remove from `pendingDeletes` |
| Back press | Dismiss with pending | Emit `PermanentDelete` for each pending ID, then `onDismiss()` |

Animations: card enter/exit `spring()`, snackbar enter/exit `tween(250)`.

## Deferred Batch Delete

Scaffold owns `pendingDeletes: SnapshotStateList<String>` internally.
- Swipe adds to set, shows snackbar
- Undo removes from set, slides card back
- Dismiss commits: emits `PermanentDelete` per ID, consumer calls ViewModel

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
| Overlay background | `uiSettingsBackground` | Dark navy |
| Card background | `uiCardBackground` | 20% white |
| Accent stripe | `accentColor` | Per-item (computed by batch lambda) |
| Header text | `uiSettingsTextPrimary` | `#FFFFFF` |
| Section label | `uiSettingsAccent`, 17sp, Bold | Blue accent |
| Back button bg | `uiSettingsSwitchTrackInactive` | |
| Divider | `uiSettingsDivider` | Low-alpha white |
| Popup bg | `uiSettingsBackground` | Matches overlay |
| Popup border | `0x40FFFFFF`, 1dp | Settings expander style |
| Popup section title | `uiDashboardTextMuted`, 16sp, SemiBold | SubSectionHeader |
| Snackbar bg | `uiCardBackground` @ 7.65% alpha | |
| Undo text | `uiSettingsAccent` | Blue accent |

## Key Files

| File | Role |
|------|------|
| [`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt) | Generic scaffold + SortControl + FilterControl |
| [`ListSortOrder.kt`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt) | `ListSortField` enum + `ListSortState` + `applySort()` |
| [`ListFilter.kt`](app/src/main/java/ykws/android/maro/data/model/ListFilter.kt) | `ListFilter` model + predicates + axis specs |
| [`ListAction.kt`](app/src/main/java/ykws/android/maro/data/model/ListAction.kt) | Action sealed class |
| [`ListableItem.kt`](app/src/main/java/ykws/android/maro/data/model/ListableItem.kt) | Common item interface |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | Track list consumer |
| [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) | Marker list consumer |
| [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) | Wiring layer |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | Track filter + sort pipeline |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | Marker filter + sort pipeline |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | Sort + filter state persistence |
| [`FilterAlt.kt`](app/src/main/java/ykws/android/maro/ui/icons/FilterAlt.kt) | Funnel icon (standalone) |
| [`FilterList.kt`](app/src/main/java/ykws/android/maro/ui/icons/FilterList.kt) | 3-bar sort icon (standalone) |
| [`Refresh.kt`](app/src/main/java/ykws/android/maro/ui/icons/Refresh.kt) | Reset icon (standalone) |
| [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt) | `ButtonColors` — icon tint/size/alpha |
