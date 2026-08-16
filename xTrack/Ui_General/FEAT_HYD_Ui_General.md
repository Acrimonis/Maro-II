# Hydration: Ui_General

**Session:** delete-advance-next — implemented. Drawer delete advances to the adjacent
item (next → previous → close) instead of just closing. Deletes are deferred and surface
in a vertical snackbar stack (max 3 visible, FIFO overflow) shared with the marker-created
undo snackbar; each row has its own ~4s timer + Undo. `deleteMarker(closeDrawer=false)`
keeps the advanced drawer open on timeout. Stack positioned at the bottom of the map area,
never over the dashboard. BUILD SUCCESSFUL.

Hardening: unique per-snackbar uid key; track Prev/Next/delete-advance skip empty-point
tracks via `openFirstValidTrack`.

UI polish: snackbar + bottom overlays left-aligned, right control column reserved
(paint-only `RIGHT_CONTROL_COLUMN_INSET`); landscape drawers clear the status bar;
Undo refocuses the restored item.

**Target files:**
- `MapScreen.kt`, `MarkersViewModel.kt`

**Plans:**
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_delete-advance-next.md`

**Last Bake:** 2026-08-16 11:48 UTC
