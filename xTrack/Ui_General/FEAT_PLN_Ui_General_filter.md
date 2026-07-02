# Plan: List Filters + Sort UX Normalization

## Summary

Add filter dropdowns to track and marker lists. Remove `pinnedGrouped` from sort (migrate to filter). Add dedicated sort direction toggle to eliminate 2-click sort problem. Add reset action. Icons show active/non-default state via `ButtonColors.icon` + `ButtonColors.iconSizeDp` (28dp) + `.alpha(activeAlpha/inactiveAlpha)` per [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:28).

---

## Filter Model — Extensible `Map<String, String>`

```kotlin
data class ListFilter(val axes: Map<String, String> = emptyMap())
// Serialized: key1=value1;key2=value2
// Absent key = default (ALL)
// Adding a new axis = new key, no model/serialization changes
```

### Track filter axes

| Key | Options | Default |
|-----|---------|---------|
| `dateRange` | `ALL` / `THIS_YEAR` / `LAST_30_DAYS` / `LAST_7_DAYS` | `ALL` |
| `pinned` | `ALL` / `PINNED` | `ALL` |

Live track always shown + always first in list (via `isLive ||` guard + `sortedByDescending { it.isLive }`).

### Marker filter axes

| Key | Options | Default |
|-----|---------|---------|
| `pinned` | `ALL` / `PINNED` / `UNPINNED` | `ALL` |
| `geometry` | `ALL` / `PINS` / `ZONES` | `ALL` |
| `origin` | `ALL` / `MANUAL` / `AUTO` | `ALL` |

**Cascade:** when `geometry` = `ZONES` → `origin` filter bypassed (zones always manual). UI: origin dropdown disabled + grayed.

### Date range semantics (day-based from midnight)

```kotlin
fun dateInRange(startTimeMs: Long, range: String, todayMidnightMs: Long): Boolean = when (range) {
    "LAST_7_DAYS" -> startTimeMs >= todayMidnightMs - 7 * 86_400_000L
    "LAST_30_DAYS" -> startTimeMs >= todayMidnightMs - 30 * 86_400_000L
    "THIS_YEAR" -> startTimeMs >= yearStartMs(todayMidnightMs)
    else -> true // ALL
}
```

Stable all day — midnight boundary no longer an issue.

### Filter predicates

```kotlin
// Track — live track always exempt
fun TrackSummary.matchesFilter(f: ListFilter, todayMidnightMs: Long): Boolean =
    isLive || f.axes.all { (key, value) ->
        when (key) {
            "dateRange" -> dateInRange(startTimeMs, value, todayMidnightMs)
            "pinned" -> value == "ALL" || (value == "PINNED") == this.pinned
            else -> true
        }
    }

// Marker — geometry=ZONES bypasses origin
fun UserMarker.matchesFilter(f: ListFilter): Boolean =
    f.axes.all { (key, value) ->
        when (key) {
            "pinned" -> value == "ALL" || (value == "PINNED") == this.pinned
            "geometry" -> value == "ALL" || geometryMatches(this.geometry, value)
            "origin" -> f.axes["geometry"] == "ZONES" || value == "ALL" || originMatches(this.origin, value)
            else -> true
        }
    }
```

---

## UI Layout

```
RECORDED TRACKS / YOUR MARKERS   [⏳] [☰] [▲/▼] [↺]
                                  filt sort dir   reset
```

| Icon | Material | Action | Active state |
|------|----------|--------|-------------|
| ⏳ Filter | `FilterList` / `FilterAlt` | Opens combined filter dropdown | Filled `FilterAlt` + `ButtonColors.activeAlpha` when `axes.isNotEmpty()` |
| ☰ Sort | `Icons.AutoMirrored.Filled.Sort` | Opens sort dropdown (fields + custom fields, no pinned grouping) | `ButtonColors.activeAlpha` when field ≠ CREATED or customFieldKey ≠ null or descending ≠ true |
| ▲/▼ Direction | `KeyboardArrowUp` / `KeyboardArrowDown` | Instant toggle | Always visible, shows current |
| ↺ Reset | `Refresh` | Instant — clears filter + sort to defaults | Always same (action, no state) |

All icons: tint `ButtonColors.icon`, size `ButtonColors.iconSizeDp` (28dp), inside `IconButton(40.dp)`.

### Empty state

When filter is active and list is empty → show "No items match filters" + "Clear filters" button. When filter is inactive and list is empty → show existing empty state.

### Filter dropdown

Single combined dropdown with sections. Tracks: Date range + Pinned. Markers: Pinned + Geometry + Origin (origin disabled when geometry=ZONES). Active filter axes summarized in closed label: `[Filters ▼]` when all=ALL, `[Pinned, Manual ▼]` when axes active.

### Sort dropdown

As-is but: remove pinned grouping checkbox, remove direction arrow from items (direction lives on toggle button).

---

## Data Model Changes

### `ListSortOrder.kt` — Remove `pinnedGrouped`

- Remove `pinnedGrouped: Boolean` field from `ListSortState`
- Remove `pinnedGrouped` from `parse()`/`format()` 
- Remove pinned partition logic from `applySort()`
- Legacy sort keys cleared on upgrade (SettingsManager version bump)

### New: `ListFilter.kt`

- `ListFilter` data class (`axes: Map<String, String>`)
- `matchesFilter()` extension functions on `TrackSummary` and `UserMarker`
- `dateInRange()` helper (day-based from midnight)
- `geometryMatches()` / `originMatches()` helpers
- `FilterAxisSpec` / `FilterOptionSpec` for UI rendering

### ViewModels — Unfiltered backing list pattern

Both ViewModels use same pattern:
```
_allItems (unfiltered source of truth)
       │ settings.collect
       ▼
  filter → sort → _displayedItems StateFlow
```

- `MarkersViewModel`: add `_allMarkers`, compute `_markers` from it on every settings emission
- `TrackViewModel`: add `_allSummaries`, compute `_summaries` from it on every settings emission

### Settings persistence

Add to `AppSettings` (in `SettingsManager.kt`):
- `trackListFilter: ListFilter`
- `markerListFilter: ListFilter`

Serialized as `key=value;...` strings.

---

## Files Touched

| File | Change |
|------|--------|
| `ListSortOrder.kt` | Remove `pinnedGrouped` field + parse/format + applySort partition |
| `ListFilter.kt` | **New** — `ListFilter` model, predicates, axis specs, helpers |
| `ListOverlayScaffold.kt` | Add filter dropdown, direction toggle, reset; remove pinned grouping checkbox; remove direction arrow from sort items; conditional empty state; normalize icons to `ButtonColors` |
| `TrackViewModel.kt` | Unfiltered backing list + filter→sort pipeline + reactive settings collect |
| `MarkersViewModel.kt` | Unfiltered backing list + filter→sort pipeline + reactive settings collect + geometry→origin cascade |
| `TrackHistoryOverlay.kt` | Pass filter state + callbacks + filter axis specs |
| `MarkerManagementOverlay.kt` | Pass filter state + callbacks + filter axis specs |
| `OverlayLayer.kt` | Pass filter state + callbacks |
| `MapScreen.kt` | Wire filter state to SettingsManager |
| `SettingsManager.kt` | Add `trackListFilter` + `markerListFilter` fields, bump `CURRENT_VERSION`, clear legacy sort keys on upgrade |

---

## Order of Operations

1. Remove `pinnedGrouped` from `ListSortState` + clear legacy on upgrade
2. Add `ListFilter.kt` (model + predicates + axis specs + helpers)
3. Add unfiltered backing list + filter→sort pipeline to both ViewModels
4. Update `ListOverlayScaffold.kt` (filter dropdown, direction toggle, reset, remove pinned grouping, conditional empty state, icon normalization)
5. Wire through OverlayLayer → MapScreen → SettingsManager
6. Add filter persistence to `SettingsManager.kt`
7. Build + verify

---

## Defaults

| Setting | Default |
|---------|---------|
| Filter axes | empty map (all = ALL) |
| Sort field | CREATED |
| Sort direction | Descending |
| Custom field | null |
