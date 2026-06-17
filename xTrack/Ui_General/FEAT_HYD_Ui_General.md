# Hydration: Ui_General

**Session:** Regulated zone overlay auto-show for speed enforcement zones.

**State:**
- 7/8 subfeatures complete (all [x] except map-print-layout [ ]).
- New subfeature `reg speed zone [x]` — auto-show for speed-enforced regulated zones.

**What happened this session:**
- Added regulated zone overlay auto-show block in CoastlineViewModel pipeline — uses `speedZoneQuery.distanceToBoundaryM` as trigger, targets `regulatedZonesVisible`.
- Added `regulatedZoneAutoShowGps`/`regulatedZoneAutoShowDemo` fields to AppSettings + persistence + Settings UI toggles.
- Wired `onToggleRegulatedZones` to `toggleRegulatedZonesVisibility()` for manual override.
- Fixed `armed` param: uses settings toggle instead of `regulatedZoneManuallyHidden` — reveals on first approach, not just after manual hide.
- Fixed `zoneAutoShowDecision()`: added `insideZoneReveal` (catches enter-between-ticks), `exitedZone` (outside past 100m), `locationUnknown` (dist=null on land).
- Created design plan at `plans/speed-enforcement-zone-auto-show-plan.md`.
- BUILD SUCCESSFUL. Show+hide cycle verified on device via logcat.

**Next step:**
- On-device visual verification by the user. Monitor log for edge cases.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — regulated zone auto-show block, `zoneAutoShowDecision()` reveal/hide fixes
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `regulatedZoneAutoShowGps`/`regulatedZoneAutoShowDemo` fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings toggles, `onToggleRegulatedZones` wiring
- `plans/speed-enforcement-zone-auto-show-plan.md` — design plan

**Last Bake:** 2026-06-17 13:37 UTC
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
