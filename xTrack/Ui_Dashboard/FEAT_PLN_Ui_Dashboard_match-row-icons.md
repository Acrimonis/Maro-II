# Match Row Icons + Type Icon Update

## Final mapping
| Geometry | Icon | Unicode | Change |
|---|---|---|---|
| Pin | 📍 | `U+1F4CD` | 📌 → 📍 |
| Circle | ⭕ | `U+2B55` | unchanged |
| Corridor | 🔴 | `U+1F534` | 📏 → 🔴 |

## Files to update

### 1. [`MarkersViewModel.typeIcon()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:330)
Source of truth. Pin: `\uD83D\uDCCC` → `\uD83D\uDCCD`. Corridor: `\uD83D\uDCCF` → `\uD83D\uDD34`.

### 2. [`MarkerDrawer.buildMatchText()`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:496)
Add icon insertion: `marker.icon ?: typeIcon(marker.geometry)` between name and distance. Also needs `typeIcon()` accessible — currently private in MarkersViewModel. **Option:** duplicate the mapping or make it internal.

### 3. [`MarkerDrawer.markerFormatText()`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:588)
Uses inline emoji: "📌 / 200" etc. Update to match new icons.

### 4. [`MarkerManagementOverlay.markerFormatText()`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:528)
Same inline emoji update.

## Approach for `typeIcon` access
`typeIcon()` is private in MarkersViewModel. Options:
- **A**: Extract to a top-level or companion function in `MarkerGeometry` (cleanest)
- **B**: Duplicate in MarkerDrawer (DRY violation)
- **C**: Make `typeIcon` internal in MarkersViewModel

**Recommend A**: add a companion `fun iconFor(geometry: MarkerGeometry): String` to `MarkerGeometry` sealed class. Single source of truth, accessible everywhere.
