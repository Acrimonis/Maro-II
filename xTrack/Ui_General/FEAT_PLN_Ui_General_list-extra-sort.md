# Plan: List Extra Sort — Per-Type Sort Fields

**Feature:** [Ui_General](xTrack/Ui_General/FEAT_DSC_Ui_General.md) → [`list extra sort`](xTrack/Ui_General/FEAT_DSC_Ui_General.md:264)
**Branch:** `feature/list-extra-sort`
**Date:** 2026-07-01

## Problem

[`ListSortField`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt:4) is an enum with 3 common fields — `TITLE`, `CREATED`, `UPDATED` — sourced from the [`ListableItem`](app/src/main/java/ykws/android/maro/data/model/ListableItem.kt:4) interface. Both tracks and markers share these.

But [`TrackSummary`](app/src/main/java/ykws/android/maro/data/track/Track.kt:52) has additional sortable fields not present on `ListableItem`:

| Field | Type | Label | Formula |
|-------|------|-------|---------|
| `distanceNm` | Float | Distance | Direct field |
| `totalTimeSec` | Long (computed) | Total Time | `(endTimeMs ?: nowMs) - startTimeMs` |
| `movingTimeSec` | Long (computed) | Moving Time | `totalSec - idleDurationSec` |

> **Design note:** `Moving Time` is NOT the stored [`navigatingDurationSec`](app/src/main/java/ykws/android/maro/data/track/Track.kt:24) (which excludes paused time). It's total wall-clock minus idle — the boat was engaged (moving or paused). This matches the user's mental model: "moving time" = time not idling.

[`UserMarker`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:32) may benefit from:

| Field | Type | Label |
|-------|------|-------|
| `origin` | MarkerOrigin | Origin |

> **`colorIndex` intentionally excluded.** Colors are categorical, not ordinal — sorting by palette index has no practical meaning.

The scaffold's `SortControl` iterates `ListSortField.entries` — hardcoded to the 3 common fields.

## Design

### Approach: `CustomSortField` + `customFieldKey` in `ListSortState`

| Goal | Mechanism |
|------|-----------|
| Keep enum for common fields | `ListSortField` enum unchanged (TITLE, CREATED, UPDATED) |
| Add per-type fields | New `data class CustomSortField(val key: String, val label: String)` |
| Scaffold receives custom fields | Parameter `customSortFields: List<CustomSortField>` |
| State references custom field | `ListSortState.customFieldKey: String?` — null = common field selected |
| Serialization backward-compat | `"UPDATED:true:false"` → common; `"UPDATED:true:false:distanceNm"` → custom |

### `CustomSortField`

```kotlin
/** Per-type sort field not present on [ListableItem]. Key is used for serialization + ViewModel dispatch. */
data class CustomSortField(val key: String, val label: String)
```

### `ListSortState` — extended

```kotlin
data class ListSortState(
    val field: ListSortField = ListSortField.UPDATED,
    val descending: Boolean = true,
    val pinnedGrouped: Boolean = false,
    val customFieldKey: String? = null   // NEW — null = common field is active
) {
    companion object {
        fun parse(raw: String?): ListSortState {
            if (raw == null) return ListSortState()
            val parts = raw.split(":")
            val field = try { ListSortField.valueOf(parts[0]) } catch (_: Exception) { ListSortField.UPDATED }
            val descending = if (parts.size > 1) parts[1].toBooleanStrictOrNull() ?: true else true
            val pinnedGrouped = if (parts.size > 2) parts[2].toBooleanStrictOrNull() ?: false else false
            val customFieldKey = if (parts.size > 3) parts[3].ifBlank { null } else null  // NEW
            return ListSortState(field, descending, pinnedGrouped, customFieldKey)
        }
        fun format(state: ListSortState): String {
            val base = "${state.field.name}:${state.descending}:${state.pinnedGrouped}"
            return if (state.customFieldKey != null) "$base:${state.customFieldKey}" else base
        }
    }
}
```

### `SortControl` — extended

```kotlin
@Composable
private fun SortControl(
    state: ListSortState,
    customFields: List<CustomSortField>,   // NEW
    onStateChange: (ListSortState) -> Unit
) {
    // ... existing icon button + dropdown ...
    DropdownMenu(...) {
        // Common fields (existing)
        ListSortField.entries.forEach { field -> ... }

        // Custom fields (NEW — only if non-empty)
        if (customFields.isNotEmpty()) {
            HorizontalDivider(...)
            customFields.forEach { cf ->
                val isSelected = cf.key == state.customFieldKey
                DropdownMenuItem(
                    text = { Text(cf.label, ...) },
                    onClick = {
                        if (isSelected) onStateChange(state.copy(descending = !state.descending))
                        else onStateChange(state.copy(field = ListSortField.UPDATED, customFieldKey = cf.key))
                        expanded = false
                    },
                    leadingIcon = { if (isSelected) Text("✓", ...) else Spacer(...) },
                    trailingIcon = if (isSelected) { Text(dirArrow, ...) } else null
                )
            }
        }

        // Separator + pinned grouping toggle (existing)
        ...
    }
}
```

### Scaffold signature change

```kotlin
fun <T : ListableItem> ListOverlayScaffold(
    // ... existing params ...
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    customSortFields: List<CustomSortField> = emptyList(),  // NEW
    // ...
)
```

`SortControl` call passes `customSortFields` through.

### ViewModel comparators

**TrackViewModel:**

```kotlin
private fun sortSummaries(
    summaries: List<TrackSummary>,
    state: ListSortState
): List<TrackSummary> {
    val nowMs = System.currentTimeMillis()
    val comparator: Comparator<TrackSummary> = when {
        state.customFieldKey != null -> when (state.customFieldKey) {
            "distanceNm" -> compareBy { it.distanceNm }
            "totalTimeSec" -> {
                // Total wall-clock time (ms), live track uses nowMs
                compareBy<TrackSummary> { (it.endTimeMs ?: nowMs) - it.startTimeMs }
            }
            "movingTimeSec" -> {
                // Total time minus idle — boat was engaged (moving or paused)
                compareBy<TrackSummary> { s ->
                    val totalMs = (s.endTimeMs ?: nowMs) - s.startTimeMs
                    totalMs - s.idleDurationSec * 1000L
                }
            }
            else -> compareByDescending<TrackSummary> { it.updatedAtEpochMs }
        }
        else -> when (state.field) {
            ListSortField.TITLE -> compareBy { it.title.lowercase() }
            ListSortField.CREATED -> compareBy { it.createdAtEpochMs }
            ListSortField.UPDATED -> compareBy { it.updatedAtEpochMs }
        }
    }
    val directed = if (state.descending) comparator.reversed() else comparator
    return if (state.pinnedGrouped) {
        val (pinned, unpinned) = summaries.partition { it.pinned }
        pinned.sortedWith(directed) + unpinned.sortedWith(directed)
    } else {
        summaries.sortedWith(directed)
    }
}
```

> **Note:** `isLive` pre-sort is handled by the scaffold ([`ListOverlayScaffold` line 273](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:273)), not the ViewModel comparator. Keep that separation — ViewModel knows nothing about live state ordering.

**MarkersViewModel** — same pattern for marker-specific fields (`origin` only).

### Consumer wiring

**TrackHistoryOverlay** provides:
```kotlin
val trackCustomSortFields = listOf(
    CustomSortField("distanceNm", "Distance"),
    CustomSortField("totalTimeSec", "Total Time"),
    CustomSortField("movingTimeSec", "Moving Time")
)
```

**MarkerManagementOverlay** provides:
```kotlin
val markerCustomSortFields = listOf(
    CustomSortField("origin", "Origin")
)
```

### Serialization backward compatibility

| Old format | New format | Meaning |
|------------|------------|---------|
| `"UPDATED:true:false"` | `"UPDATED:true:false"` | Common field (customFieldKey=null) — identical |
| — | `"UPDATED:true:false:distanceNm"` | Custom field active |

Old persisted values parse correctly — `parts.size == 3` → `customFieldKey = null`.

### Dropdown UX

```
┌─────────────────────┐
│ ✓ Title             │  ← common fields
│   Created           │
│ ✓ Updated        ↓  │  ← checked + direction arrow
├─────────────────────┤
│   Distance          │  ← track custom fields
│   Total Time        │
│   Moving Time       │
├─────────────────────┤
│ ☑ Group pinned items│  ← existing toggle
└─────────────────────┘
```

When a custom field is selected, the common field section shows no checkmark. Tapping a different custom field selects it. Tapping the already-selected custom field toggles direction.

When a common field is selected, `customFieldKey` reverts to `null`.

## Files Touched

| File | Change |
|------|--------|
| [`ListSortOrder.kt`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt) | Add `CustomSortField` data class, extend `ListSortState` (+`customFieldKey`, updated `parse`/`format`) |
| [`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt) | `SortControl` + `ListOverlayScaffold` accept `customSortFields: List<CustomSortField>`, render in dropdown |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | Extend `sorted()` with custom field comparators |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | Extend `sortMarkers()` with custom field comparators |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | Provide `trackCustomSortFields`, thread to scaffold |
| [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) | Provide `markerCustomSortFields`, thread to scaffold |
| [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) | Thread `customSortFields` parameters |

## Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | **Serialization break** — `customFieldKey` appended to format string, old parse must handle 3-part strings | `parse()` explicitly checks `parts.size > 3`; 3-part = backward compat |
| 2 | **Common field visible when custom selected** — "Updated" still shows in dropdown even though `customFieldKey = "distanceNm"` is active | Design decision: common fields always visible; selecting one clears `customFieldKey` |
| 3 | **Comparator dispatch performance** — nested `when` for custom fields | Trivial — called once per sort change, not per frame |
| 4 | **Empty custom fields** — markers may have no useful custom fields | `customSortFields = emptyList()` default; divider + section hidden when empty |

## Implementation Order

1. Extend `ListSortState` (+`customFieldKey`, updated `parse`/`format`)
2. Add `CustomSortField` data class
3. Extend `SortControl` + `ListOverlayScaffold` signature
4. Thread `customSortFields` through `OverlayLayer` → `TrackHistoryOverlay` / `MarkerManagementOverlay`
5. Extend `TrackViewModel.sorted()` with custom comparators
6. Extend `MarkersViewModel.sortMarkers()` with custom comparators
7. Build & verify
