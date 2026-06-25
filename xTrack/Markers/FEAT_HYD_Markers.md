# Markers — Hydration Snapshot (2026-06-25 23:43 UTC)

## State
- **Active subfeature:** debug-wia
- **Status:** done
- **Branch:** feature/debug-wia

## Changes
- Diagnosed mutual-nesting bug in containment tree via debug logcat: overlapping ZoneMatches destroyed each other when each contained the other's center
- Fixed: skip nesting when child `zoneSizeM >= outer.zoneSizeM` in `resolveAllMarkers()` (MarkerMatcher.kt:169)
- Added default marker name: type icon + color (📌 Blue, ⭕ Red, 📏 Green) — icons match `markerFormatText` in MarkerDrawer
- Added `colorIndex` to `CreateFormState` for name/color consistency
- All debug instrumentation removed from production code

## Implemented
| File | Change |
|------|--------|
| `MarkerMatcher.kt` | Mutual-nesting fix (line 169), `zoneCenterOf` → `internal` |
| `MarkersViewModel.kt` | `typeIcon()`, `colorName()`, default name in `startWizard()`, `colorIndex` in `CreateFormState` |
| `MarkerOverlay.kt` | Clean (debug code removed) |
| `MapScreen.kt` | Clean (debug code removed) |

## Build
- assembleDebug ✅
