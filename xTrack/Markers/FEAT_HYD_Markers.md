# Markers — Hydration Snapshot (2026-06-28 16:17 UTC)

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

## Wizard Layout Normalization
| File | Change |
|------|--------|
| `WizardTopBar.kt` | Header padding: 16×12dp, better back-arrow breathing room |
| `TypeSelectStep.kt` | Icon+label on same Row (18dp+12sp), single-line, pin bar fully clickable |
| `PositionStep.kt` | Left-aligned, uiCardBackground card wrapper (Tight 8×4dp) |
| `SliderStep.kt` | Optional `comment` param, Tight 8×4dp padding |
| `WizardButtonRow.kt` | 3 slots always visible, dimmed when inappropriate (no layout jumps) |
| `TextInputStep.kt` | Outer padding → Tight 8×4dp |
| `TextInputStep.kt` | Focus bug fixed: `remember` without key, select-all only in LaunchedEffect(Unit) |
| `MarkerOverlay.kt` | Unconfirmed markers bypass drawGeometry/skipDots — always full geometry during wizard |
