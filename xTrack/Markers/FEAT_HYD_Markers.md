# Markers — Hydration Snapshot (2026-06-25 16:08 UTC)

**Baked at:** 2026-06-23 08:33 UTC

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — markers integration (OverlayLayer, MarkersViewModel, WizardDrawer)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — unified drawer/scrim framework
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt` — drawer slot abstraction
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — step-by-step marker creation wizard
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — map Canvas overlay rendering
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — marker edit drawer
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — marker list management
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — markers state management

Designed wizard-oriented creation/editing flow (`create-zones-flow` subfeature) replacing the crowded single-form `MarkerDrawer` CreationContent/EditContent. Plan locked after 6 Q&A rounds, review, and all workflow points resolved.

## Design Decisions

- **Wizard:** Step-by-step wizard replacing dashboard panel (same size/position). 7 `WizardStep` types sequenced per marker type. `AnimatedContent` slide transitions.
- **Buttons:** Cancel (←) top-left, [Previous] [Next] [Finish] bottom row. Previous omitted first step, Next omitted last. Finish dimmed for Corridor until P2.
- **Edit mode:** Same wizard, pre-filled. Position tracking extended to Editing state.
- **Keyboard:** Runtime `adjustNothing` toggle (not manifest) + `Modifier.offset(y = -imeHeight)` portrait. No offset landscape.
- **Finish:** Saves immediately with defaults. Post-save Snackbar with Undo.
- **Proximity defaults:** Pin=200m, Circle=radius, Corridor=width (not ×3).

## Added Items

- P6: Match highlighting on map (MarkerOverlay `matchedMarkerIds` param)
- P8: Explicit Edit button per row in management page (no tap-on-row)
- P10: Marker tap on map via MapEventsOverlay → Viewing → Edit → wizard
- Phase 5: Dead code removal (CreationContent, EditContent, CorridorPhase, old nav methods)
- Phase 6: Polish phase for P6/P8/P10

## Key Files (to be created/modified)

- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — remove CreationContent/EditContent
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — add WizardStep, nav methods, startWizard
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — dashboard replacement, keyboard offset, Edit tracking
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — matchedMarkerIds param
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — Edit button per row

## Next Step

Implement per `FEAT_PLN_Markers_create-zones-flow.md` — 6 phases.
