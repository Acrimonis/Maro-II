# Markers — Hydration Snapshot

**Baked at:** 2026-06-24 09:13 UTC

## Session Summary

Implemented marker overlay improvements: wizard button restyling, corridor semi-circle caps, area-based tap detection with selected rendering, and dual-zone fills (geometry + proximity).

## Implemented Changes

### Wizard buttons
Replaced 48dp circle icon buttons (←, →, ✓) with text pills styled like SettingsLanguageRow. Container: RoundedCornerShape(12.dp) Row with accent background, bold white text, 14sp.

### Corridor semi-circle caps
Added `addSemiCircleCaps()` — 18-point arc caps at p1 and p2 closing the corridor band into a pill shape. p1 cap sweeps +180° through B+180° (outward), p2 cap sweeps −180° through B (outward). Both stroke and fill caps use same direction. `strokeCap = BUTT` on parallel edges for flush connection. Proximity preview corridor also uses semi-circle caps.

### Area tap + selected rendering
`MapEventsOverlay` with `onSingleTapConfirmedHelper` for area-based hit-testing. Uses `closestPointOnGeometry()` with per-marker proximity range (`proximityOverrideM` or config formula). Nearest-to-tap wins. Returns `false` on miss for gesture pass-through. `selectedMarkerId` param on `MarkerOverlay` — selected marker gets ×2 stroke highlighting. `MarkersViewModel` exposes `selectedMarkerId` StateFlow.

### Zone fills
Circles and corridors rendered with 20% alpha transparent fill (`ZONE_FILL_ALPHA_FRACTION`) via `Polygon` overlay beneath strokes. Proximity zone also has transparent fill (cyan-based) creating visible concentric shapes.

### Proximity preview for confirmed markers
Gate changed from `!confirmed` to `drawZones` — proximity rings now visible for confirmed markers when `markerZonesVisible` is enabled.

## Key Files Modified
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — WizardButtonRow text pills
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — caps, fills, area tap, selected rendering
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — selectedMarkerId StateFlow
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — selectedMarkerId pass-through

## Deferred
- Marker color settings in Settings page (collapsible section with color pickers)

## Next Step
Deploy and test on device. Verify corridor caps render correctly for both E-W and W-E orientations. Test area tap on all marker types.
