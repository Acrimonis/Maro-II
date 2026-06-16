# Hydration: Ui_General

**Session:** Layout refactor — 2-column Row overlay structure with symmetric 6dp margins.

**State:**
- 4/6 subfeatures complete (BackToExitConfirm, KeepScreenOn, page layout, immersive ui rework all [x]).
- ButtonColors [ ] — empty shell.
- SVSpacing [ ] — currently focused subfeature, empty shell.

**What happened this session:**
- Restructured MapContent from ad-hoc overlays to a clean 2-column Row layout.
- Removed ControlItem/ControlSection/ControlSectionContent machinery (~70 lines).
- Removed Spacer(136.dp) placeholder for 2nd fan.
- Removed root bottom = 6.dp band-aid.
- Replaced systemBars inset on right Column with per-section insets (statusBars on ct, padding(6dp) on cb).
- Consolidated 4× navigationBars insets into 1 conditional (landscape only).
- Set uniform 6dp margins on all edges (portrait) / navigationBars (landscape bottom).
- Added orientation-aware top inset: statusBarHeight - 6dp (portrait), full statusBarHeight (landscape).
- Reduced zoom button spacing from 8dp to 6dp.
- Added isLandscape parameter to MapContent.
- All changes BUILD SUCCESSFUL.

**Next step:**
- Deploy and test on device for both portrait and landscape orientations.
- Track any visual tweaks needed after on-device validation.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — all layout changes
- `plans/map-overlay-layout-rationalization.md` — full design plan
- `plans/map-overlay-layout-inventory.md` — overlay inventory
- `plans/right-edge-controls-gap-asymmetry-analysis.md` — original gap analysis

**Last Bake:** 2026-06-16 21:18 UTC
