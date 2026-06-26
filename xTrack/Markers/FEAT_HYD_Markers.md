# Markers — Hydration Snapshot (2026-06-26 06:07 UTC)

## State
- **Active subfeature:** marker-pin-tri-state
- **Status:** active
- **Branch:** feature/marker-pin

## Changes
- Added `UserMarker.pinned: Boolean = false` — serialization-safe
- Added `MarkersViewModel.togglePin(markerId)` — toggles pinned state
- Pin `IconButton` (LocationOn/LocationOff) in marker list card + drawer header
- Tri-state fan layer toggle: HIDDEN / SHOW_ALL / SHOW_PINNED
- `MarkerLayerState` enum replacing `userMarkersVisible` Boolean
- SettingsManager migration: `user_markers_visible` Boolean → `marker_layer_state` String (CURRENT_VERSION 3)
- `where_to_vote` Material Symbol icon for SHOW_PINNED state
- Marker list filtered by `.pinned` when SHOW_PINNED is active

## Implemented
| File | Change |
|------|--------|
| `UserMarker.kt` | `pinned: Boolean = false` field |
| `MarkersViewModel.kt` | `MarkerLayerState` enum, `markerLayerState` + `userMarkersVisible` StateFlows, `cycleMarkerLayerState()`, `togglePin()` |
| `SettingsManager.kt` | `markerLayerState: MarkerLayerState`, migration v2→v3, persistence |
| `MarkerManagementOverlay.kt` | `onTogglePin` callback chain + pin IconButton |
| `MarkerDrawer.kt` | Pin IconButton in header |
| `OverlayLayer.kt` | `onTogglePin` param |
| `MapScreen.kt` | Tri-state fan child + pinned marker filter + `onCycleMarkerLayer` wiring |
| `FanIconComponents.kt` | `WhereToVoteIcon` composable |
| `ui/icons/WhereToVote.kt` | Standalone Material Symbol icon |

## Build
- assembleDebug ✅
