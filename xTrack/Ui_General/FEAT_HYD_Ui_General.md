# Hydration: Ui_General

**Session:** Gap asymmetry analysis for right-edge control stack.

**State:**
- 4/6 subfeatures complete (BackToExitConfirm, KeepScreenOn, page layout, immersive ui rework all [x]).
- ButtonColors [ ] — empty shell.
- SVSpacing [ ] — currently focused subfeature, empty shell.

**What happened this session:**
- User reported asymmetric gap between right-edge controls and map edges (bottom gap > top gap).
- Traced root cause to two factors in `MapScreen.kt`:
  1. `windowInsetsPadding(WindowInsets.systemBars)` on the control Column (line 942) — applies different heights at top (statusBars ~24dp) vs bottom (navigationBars ~48dp).
  2. `padding(bottom = 6.dp)` on the root Box (line 506) — adds 6dp extra at bottom only.
- Combined effect: bottom gap ≈ 54dp vs top gap ≈ 24dp on 3-button nav devices (2.25× ratio).
- Created `plans/right-edge-controls-gap-asymmetry-analysis.md` with full layout diagram, root cause breakdown, and recommended fix.

**Next step:**
- Discuss the analysis with the user. If approved, switch to Code mode to implement the fix (restructure control Column for symmetric insets, remove root `bottom = 6.dp`).

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — root Box line 506, right-edge Column line 942
- `plans/right-edge-controls-gap-asymmetry-analysis.md` — full analysis
- `xTrack/Ui_General/FEAT_DSC_Ui_General.md` — feature tracking

**Last Bake:** 2026-06-16 20:02 UTC
