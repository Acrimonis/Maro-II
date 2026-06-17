# Hydration: Ui_General

**Session:** Layer refactor — split CenterMarkerOverlay into 3 Compose layers (cap/arrow/marker).

**State:**
- 6/7 subfeatures complete (BackToExitConfirm, KeepScreenOn, page layout, immersive ui rework, ButtonColors, SVSpacing all [x]).
- map-print-layout [ ] — empty shell, newly created.

**What happened this session:**
- Branch renamed from `feature/btn--colors` to `feature/map-layers`.
- Extracted cap arrow from CenterMarkerOverlay into independent `CapArrowOverlay` composable (speed-gated, zoom-scaled).
- Stripped CenterMarkerOverlay to pure boat/dot Image only (removed `navigationState`, `showCapArrow` params).
- Arranged 3 layers in MapContent Box: DirectionLine (bottom), CapArrowOverlay (middle), CenterMarkerOverlay (top).
- Z-order: cap (direction line) → arrow (speed indicator) → marker (boat/dot).
- All changes BUILD SUCCESSFUL (47s).

**Next step:**
- Deploy and test on device — verify marker, arrow, and direction line render at correct z-order and no visual regression.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — 3-layer overlay split (lines ~748-763, ~1292-1420)

**Last Bake:** 2026-06-17 07:44 UTC
