# Markers — Hydration Snapshot (2026-06-26 05:38 UTC)

## State
- **Active subfeature:** marker-pin
- **Status:** active
- **Branch:** feature/marker-pin

## Changes
- Added `pinned: Boolean = false` to `UserMarker` data class
- Added `MarkersViewModel.togglePin(markerId)` — toggles pinned state, persists via repo
- Added pin `IconButton` (LocationOn/LocationOff) to marker list card header row, left of Edit
- Added pin `IconButton` to marker detail drawer header, before Edit
- Callback chain: `MapScreen` → `OverlayLayer` → `MarkerManagementOverlay` → `SwipeToDeleteMarkerCard` → `MarkerCardContent`

## Implemented
| File | Change |
|------|--------|
| `UserMarker.kt` | `pinned: Boolean = false` field |
| `MarkersViewModel.kt` | `togglePin(markerId)` method |
| `MarkerManagementOverlay.kt` | `onTogglePin` callback chain + pin IconButton + imports |
| `MarkerDrawer.kt` | Pin IconButton in header + imports |
| `OverlayLayer.kt` | `onTogglePin` param + wiring to MarkerManagementOverlay |
| `MapScreen.kt` | `onTogglePin` wiring to `markersViewModel.togglePin()` |

## Build
- assembleDebug ✅
