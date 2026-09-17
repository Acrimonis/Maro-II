# UI_Map — Hydration Snapshot

**Baked:** 2026-09-17 18:10 UTC

## Active State
Inspect mode is fully designed and **not implemented**. The plan `260917_FEAT_PLN_UI_Map_inspect-mode.md` carries eight sections, five property keys and no open design item; the feature file's `## Walk` section holds the closed eight-point review that resolved it, and it is closed rather than parked.

No source file changed this session — the work was design plus a challenge pass — so the map behaves exactly as before. The design's spine: a ⊕ square in the top-left row arms a proximity pick; the sweep ranks the layer-visible map-filtered items by distance from the marker point; a quiet-map timer picks the nearest without moving the camera; the card then walks a frozen distance ladder through one new inspect cursor that can cross between marker and track cards.

Owed before shipping: two device checks (the commit swap's frame time at the 20-track render cap, and the card's slot against the anchor band in both orientations) and two code checks (whether `mapView.mapCenter` already carries the centre offset, and the non-consuming release observer).

## Target Files
- `xTrack/UI_Map/260917_FEAT_PLN_UI_Map_inspect-mode.md` — the plan and its 16 steps
- `ui/map/MapScreen.kt`, `MapTrackOverlayEffects.kt`, `MapOverlays.kt`, `MapControls.kt`, `MarkerOverlay.kt`, `MarkersViewModel.kt`
- `config/AppConfig.kt`, `assets/colors.properties`, `res/values*/strings.xml`

## Next Step
`#impl` — start at step S1, the pure `InspectRanking` with the points-and-lines metric and the dp → metres radius derivation.
