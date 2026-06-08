# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 21:36
**Feature Status:** active (3/8 subfeatures done)
**Active Subfeature:** none (toggle-danger-layer registered, not focused; impl done, build pending)

## State Summary
Added a map control button for the pink low-depth/danger overlay. New `DangerLayerButton`
(blue warning-triangle, mirrors `LayerButton`) sits just above the 300 m `LayerButton` in the
right-edge control stack, wrapped in an inner `Column(spacedBy 8.dp)` so the pair stays close and
centred by the parent `SpaceBetween`. Wired `onToggleLowDepthWarning` through `MapContent` →
new `CoastlineViewModel.toggleLowDepthWarningVisibility()` (plain flip of `lowDepthWarningVisible`,
no auto-reveal state). Visual polish this session: 300 m zone icon → circular ring (was the
two-stacked-layers glyph); all control-stack icons themed blue (`0xFF1565C0`); control-stack padding
tightened (top/bottom/right 12→6 dp, left unchanged). Branch `feature/toggle-danger-layer`, off
develop (which already carries the auto-show feature via PR #40).

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `DangerLayerButton`, `LayerButton` ring icon, control-stack grouping + padding
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `toggleLowDepthWarningVisibility()`

## Next Steps
- Build (`apk-build.bat`) + on-device verify: danger button toggles the pink layer; pair stays centred; ring/blue icons render; tighter padding looks right.
- Inherited from develop: low-depth pink-bleed fix (laps ~½ cell onto land at 25 m); "depth color" + "layer-zone" re-bake still pending.
