# Hydration: ArcLayout — fan-migration

**Baked:** 2026-06-14 18:54 UTC

## Micro-State Summary

Control stack refactored: hardcoded Column children replaced with `ControlId`/`ControlSection`/`ControlItem` data model. Non-fan controls (Settings, Zoom) now hide via `AnimatedVisibility` when a fan is expanded. The `expandedFanId: ControlId?` state replaces the old `layerFanExpanded: Boolean`.

### What changed
- **MapScreen.kt**: Added `ControlId`, `ControlSection`, `ControlItem` types + `ControlSectionContent` composable. Replaced static Column children with list-based `remember` block + `AnimatedVisibility` gate.
- **plan**: `plans/fan-btn-hide-ozers-plan.md`

### Key design
- `ControlId.SETTINGS` (TOP), `LAYER_FAN` (MIDDLE), `ZOOM` (BOTTOM) — sections preserve `SpaceBetween` layout
- Visibility: `item.isFan || !anyFanOpen || isExpanded`
- Adding a new control = one enum entry + one `ControlItem` in the list

### Next step
- Ready to add second fan button in control stack
