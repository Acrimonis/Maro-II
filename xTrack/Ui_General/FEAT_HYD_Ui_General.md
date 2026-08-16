# Hydration: Ui_General

**Session:** menu-drawer-rows — implemented. Menu drawer rows renamed to "Tracks"/
"Markers" (EN+FR, no ellipsis). Row tap opens the list; the trailing chevron is now
its own IconButton that opens the detail drawer on the first item of the current
filtered/sorted list (track: first non-live + non-pending-delete; marker: first of
`markers`), disabled when there is no first item. BUILD SUCCESSFUL.

**Target files:**
- `MenuDrawerOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `strings.xml` (EN+FR)

**Plans:**
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_menu-drawer-rows.md`

**Last Bake:** 2026-08-16 12:13 UTC
