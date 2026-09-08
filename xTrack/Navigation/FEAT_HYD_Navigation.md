# Hydration: Navigation

**State:** Active — auto-show validation baked (2026-09-08), feature/auto-show-zones committed + pushed.

**Session summary:** Validated demo-mode auto-show of regulated zones + 300 m band (reported: demo
does not reveal though Settings on). Instrumented build (`AutoShow` tag) + on-device logcat proved
**no code defect**: `autoShowMasterOverride` (drawer "Auto-show zones" master switch) was OFF →
`globalEnabled=false` → auto-show suppressed in both modes. GPS "working" was the layers being
visible, not auto-show. Demo speed/cone/data were healthy. Legacy auto-show knowledge consolidated
into `FEAT_DOC_Navigation_auto-show.md`; ZoneTile legacy plan stubbed; Ui_Settings stays settings
owner. Stale code comments corrected (demoBearingDeg never set; demo heading always 0° north;
two-finger rotate writes bearingDeg for map orientation only). LOGCAT WORKFLOW rule added to
AGENTS.md + GLOBAL_CONTEXT.

**Target files (branch):**
- `xTrack/Navigation/*` — auto-show docs (new FEAT_DOC, plan, DSC section, hydration)
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — comment/KDoc corrections only
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — comment corrections only

**Outcome:** no functional change required; user enables the drawer master switch to use auto-show.

**Previous session:** Cap arrow + direction line fix complete (bearingDeg removed from render math).
