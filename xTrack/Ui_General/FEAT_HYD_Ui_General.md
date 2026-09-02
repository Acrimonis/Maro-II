# Hydration: Ui_General

**Session:** MapScreen modularization — split the 6584-line `MapScreen.kt` into 4 focused
sibling files in `ui/map/`: `TrackSharing.kt` (file-name sanitizing + GPX/ZIP share helpers),
`CoastlineMapView.kt` (`LoadingOverlay`, `ErrorOverlay`, `CoastlineMapView` OSMdroid view),
`MapOverlays.kt` (marker-sizing constants + `CenterMarkerOverlay`/`CapArrowOverlay`/`DirectionLine`),
`MapControls.kt` (`ControlId` + top-left/right controls). All moved symbols bumped
`private` → `internal`; `RIGHT_CONTROL_COLUMN_INSET` now internal. No behavior change.
`docs/maro-code.md` updated. Settings module intentionally left in `MapScreen.kt` for a later
tab/section reorganization + split. BUILD SUCCESSFUL.

**Target files:**
- `MapScreen.kt`, `TrackSharing.kt`, `CoastlineMapView.kt`, `MapOverlays.kt`, `MapControls.kt`, `docs/maro-code.md`

**Plans:**
- (pending) settings tabs/sections reorganization + settings module split

**Last Bake:** 2026-09-02 15:42 UTC
