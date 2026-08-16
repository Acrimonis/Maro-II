# Hydration: Ui_General

**Session:** top-left-icons — implemented. Top-left status row reordered to
GPS → Tracking → Land/Water. GPS icon (`GpsStatusIcon`) now always visible: in demo
mode it renders the gray DEMO state, and it is clickable — `onClick` toggles GPS ↔ demo
via the permission-aware `onGpsModeChange` threaded through a new `onGpsModeToggle`
param on `MapContent`. Tracking icon emoji changed from 🚤 to 🐾 paw prints.
`RecenterButton` remains last and GPS-only. BUILD SUCCESSFUL.

**Target files:**
- `MapScreen.kt`, `TrackStatusIcon.kt`

**Plans:**
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md`

**Last Bake:** 2026-08-16 08:35 UTC
