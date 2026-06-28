# Markers — Hydration Snapshot (2026-06-28 14:22 UTC)

## State
- **Active subfeature:** icon
- **Status:** implemented
- **Branch:** feature/marker-icon

## Implemented
| File | Change |
|------|--------|
| `UserMarker.kt` | `icon: String?`, `createdAtEpochMs: Long` |
| `MarkersViewModel.kt` | `icon` in CreateFormState, `shortDateFormat`, default title `(date) icon color`, `setMarkerIcon()`, save/update wiring, edit pre-populate |
| `IconPickerDialog.kt` | New — 3×4 grid + "None" |
| `WizardDrawer.kt` | Icon picker in Title step |
| `MarkerDrawer.kt` | Pin→icon button, normalized markerFormatText |
| `MarkerManagementOverlay.kt` | Pin→icon button, `onSetIcon` callback |
| `MarkerOverlay.kt` | `drawGeometry` gate, `skipDots` flag, icon bitmap 64×64/48sp, `skipDots` params on helpers |
| `MapScreen.kt` | `markerLayerState`, SHOW_PINNED filter (`userMarkers.filter { it.pinned }`) |
| `OverlayLayer.kt` | `onTogglePin` → `onSetIcon` |
| `marker-pin-tri-state.md` | Updated rendering rules (Steps 5-6) |

## Rendering Rules (final)
| Mode | Pinned? | Selected? | Dots | Zones/Prox | Icon |
|------|---------|-----------|------|-----------|------|
| SHOW_ALL | No | — | ✅ | ✅ | ❌ |
| SHOW_ALL | Yes | — | ❌ | ✅ | ✅ |
| SHOW_PINNED | — | No | ❌ | ❌ | ✅ |
| SHOW_PINNED | — | Yes | ❌ | ✅ | ✅ |

## Build
- compileDebugKotlin ✅
