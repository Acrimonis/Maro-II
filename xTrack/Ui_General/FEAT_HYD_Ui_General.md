# Hydration: Ui_General

**Session:** Decouple auto-show overlay state from user settings — both 300m zone and regulated zones.

**State:**
- `reg speed zone [x]` — regulated zone overlay auto-show for speed enforcement (completed this session)
- `reg speed zone` — also covered: decouple auto-show overlay from user toggle (`_regulatedZoneOverlayVisible` StateFlow)
- 300m zone auto-show also decoupled: `_zone300OverlayVisible` StateFlow (separate from `zone300Visible`)

**What happened this session:**
- Added `_regulatedZoneOverlayVisible` StateFlow to CoastlineViewModel; auto-show writes to it instead of `regulatedZonesVisible` in AppSettings.
- MapScreen render gate: `appSettings.regulatedZonesVisible || regulatedZoneOverlayVisible`.
- Added `_zone300OverlayVisible` StateFlow for the 300m zone — same decoupling.
- MapScreen render gate for 300m: `appSettings.zone300Visible || zone300OverlayVisible`.
- Both auto-shows now control their own overlay state independently. Settings toggles and fan buttons always reflect user intent, never auto-show state.
- BUILD SUCCESSFUL.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `_zone300OverlayVisible`, `_regulatedZoneOverlayVisible`, both auto-show blocks
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — collect + pass + OR render gates
- `plans/auto-show-settings-decoupling-design.md` — design plan

**Last Bake:** 2026-06-17 13:57 UTC
