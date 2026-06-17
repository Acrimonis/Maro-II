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
