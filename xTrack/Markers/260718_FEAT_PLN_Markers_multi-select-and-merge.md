# Plan: Marker List Multi-Select & Merge

**Feature:** Markers
**Branch:** feature/markers-list
**Date:** 2026-07-18

## Summary

Re-enable multi-select on the marker management list with three actions:
- **Delete** — batch delete with confirmation (modeled on track delete)
- **Pin** — dropdown: pin/unpin/toggle (modeled on track pin)
- **Merge** — auto-markers only, consolidate at centroid with distance reporting + keep-originals option

## Design

### Multi-Select Actions

| Action | Icon | Behavior |
|--------|------|----------|
| Delete | `Icons.Filled.Delete` | Destructive tint, confirm dialog (`confirm_delete_markers`), `PermanentDelete` per ID |
| Pin | `Icons.Filled.PushPin` | Dropdown: Pin all / Unpin all / Toggle pins. Reuses `onTogglePin` callback |
| Merge | `Icons.AutoMirrored.Filled.MergeType` | Auto-markers only. Button enabled when ≥2 IDLE_AUTO markers selected. Non-auto markers in selection silently filtered out |

### Merge Dialog

```
┌─ Merge N Auto Markers ──────────────────────┐
│  Markers range from Xm to Ym apart.          │
│  One consolidated marker at the center.      │
│                                               │
│  Name: [Merged 2026-07-14             ]       │
│  ☑ Keep original markers                      │
│                                               │
│                         [Cancel]   [Merge]    │
└───────────────────────────────────────────────┘
```

- Distance shows min/max pairwise haversine between auto markers' centerPoints
- Name pre-filled with "Merged YYYY-MM-DD" (earliest marker date)
- Keep originals: ON by default. When unchecked, source markers are deleted

### Merge Logic (MarkersViewModel.mergeAutoMarkers)

1. Filter selected IDs to `MarkerOrigin.IDLE_AUTO` only
2. Compute centroid: average lat/lon of all centerPoints
3. Create new `UserMarker`:
   - geometry: `Pin(centroid)`
   - name: user-provided (default: "Merged YYYY-MM-DD")
   - description: "Merged from N auto markers: YYYY-MM-DD → YYYY-MM-DD"
   - confirmed: false, pinned: true, icon: 🕐, origin: IDLE_AUTO, keepable: false
   - proximityOverrideM: `AppConfig.boatMarkerAutoMarkerProximityM`
4. IO: `repo.add(merged)` + if !keepOriginals: `ids.forEach { repo.delete(it) }`
5. Reload _allMarkers → filter → sort → _markers

### Enabled Predicate

```kotlin
enabled = { ids ->
    ids.count { id -> markers.find { it.id == id }?.origin == MarkerOrigin.IDLE_AUTO } >= 2
}
```

### Signature Changes

```kotlin
// MarkerManagementOverlay — new param
onMergeMarkers: (Set<String>, String, Boolean) -> Unit = { _, _, _ -> },

// MarkersViewModel — new method
fun mergeAutoMarkers(ids: Set<String>, name: String, keepOriginals: Boolean)

// OverlayLayer — new param, pass through
onMergeMarkers: (Set<String>, String, Boolean) -> Unit = { _, _, _ -> },

// MapScreen — wire
onMergeMarkers = { ids, name, keep -> markersViewModel.mergeAutoMarkers(ids, name, keep) }
```

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` | Replace `multiActions = emptyList()` with 3 `MultiActionSpec`s; add `onMergeMarkers` param; add `MergeMarkersDialog` composable |
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | Add `mergeAutoMarkers(ids, name, keepOriginals)` |
| `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` | Add `onMergeMarkers` param, pass through to MarkerManagementOverlay |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Wire `onMergeMarkers` → ViewModel |
| `app/src/main/res/values/strings.xml` | Add `confirm_delete_markers` |

## New String Resources

```xml
<string name="confirm_delete_markers">Delete selected markers?</string>
```

## Reference

- Track multi-select: `TrackHistoryOverlay.kt:231-358` — reference implementation
- Multiselect framework: `docs/ui-lists-guidelines.md` §Multiselect Framework
- `MultiActionSpec`: `data/model/MultiActionSpec.kt`
