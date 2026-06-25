# UI_Map — Hydration Snapshot

**Baked:** 2026-06-25 16:55 UTC+2

## Active State
- **Subfeature:** overlay-layer
- **Branch:** feature/map-migration

## What Changed This Session
1. **DrawerSlot created** — reusable `AnimatedVisibility` wrapper: `SlideDirection` + `ShadowEdge` enums, spring enter + tween exit, 8dp gradient shadow via `drawBehind`
2. **OverlayLayer created** — unified Layer 1 compositor: all 7 transient surfaces + scrim in one self-contained composable
3. **Wizard steps extracted** to `markers/wizard/` — 6 new files: `WizardTopBar`, `WizardButtonRow`, `TypeSelectStep`, `PositionStep`, `SliderStep`, `TextInputStep`
4. **Wizard blank fixed** — `WizardDrawer` receives `step: WizardStep` as non-null parameter
5. **Drawer cleanup** — `MarkerDrawer`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `WizardDrawer` stripped of AnimatedVisibility/scrim/shadow → pure content
6. **MapScreen integrated** — `OverlayLayer(...)` call at line 1036 replaces inline drawer rendering
7. **Build: SUCCESS** — `assembleDebug` green, 41 tasks up-to-date
8. **`docs/ui-drawer-guidelines.md` updated** — §1–§5 new: architecture, DrawerSlot API, surfaces table, composable contract, how-to-add-a-drawer checklist; decisions I7–I11

## Design Decisions
- 9 copy-pasted `AnimatedVisibility` blocks → 9 `DrawerSlot` calls (~270 lines → ~72 lines)
- `Modifier.shadow()` (invisible on dark) → 8dp black@18% gradient via `drawBehind`
- Wizard steps are independent composables in `markers/wizard/steps/`; `WizardDrawer.kt` is a thin shell
- Layer 0 (dashboard/map/controls) is permanent; Layer 1 (OverlayLayer) is transient
- Any new overlay follows the 6-step checklist in `docs/ui-drawer-guidelines.md` §5

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardTopBar.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardButtonRow.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/TypeSelectStep.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/PositionStep.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/SliderStep.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/TextInputStep.kt` — NEW
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — modified (thin shell)
- `docs/ui-drawer-guidelines.md` — updated

## Next Steps
- On-device verify all 7 surfaces open/close with correct animations and shadows
