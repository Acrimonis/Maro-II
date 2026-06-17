# Hydration: Ui_General

**Session:** Layer overlay rebuild cleanup — replaced monolithic overlayKey with per-layer OverlayTracker.

**State:**
- 6/7 subfeatures complete (BackToExitConfirm, KeepScreenOn, page layout, immersive ui rework, ButtonColors, SVSpacing all [x]).
- map-print-layout [ ] — empty shell.

**What happened this session:**
- Branch renamed from `feature/btn--colors` to `feature/map-layers`.
- Extracted cap arrow from CenterMarkerOverlay into independent `CapArrowOverlay` composable (speed-gated, zoom-scaled).
- Stripped CenterMarkerOverlay to pure boat/dot Image only (removed `navigationState`, `showCapArrow` params).
- Arranged 3 layers in MapContent Box: DirectionLine (bottom), CapArrowOverlay (middle), CenterMarkerOverlay (top).
- Replaced monolithic `overlayKey` mechanism in CoastlineMapView with per-layer `OverlayTracker`.
- Each of the 6 OSMdroid overlay layers (depth, lowDepth, isobaths, regulatedZones, zone300, coastline) now independently tracks its data reference and zoom level. Only dirty layers are rebuilt on change.
- All 6 draw functions + addBandedOverlay refactored to accept a `sink: MutableList<...>` parameter.
- Removed `zoneVisible`/`depthVisible`/`isobathVisible` local variables and blanket `removeAll` + rebuild pattern.
- Zone300 auto-show now triggers a single-layer rebuild instead of full repaint.
- Both changes BUILD SUCCESSFUL (47s + 32s).

**Next step:**
- Deploy and test on device — verify all layer toggles and auto-show work without stutter.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — OverlayTracker class, CoastlineMapView refactor, 3-layer Compose split, all draw function sink params
- `plans/overlay-rebuild-full-cleanup.md` — full cleanup design plan

**Last Bake:** 2026-06-17 08:09 UTC
