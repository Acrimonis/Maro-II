# Ui_Settings — Hydration (2026-09-11 20:00 UTC)

## State — confirm-dialog normalization COMPLETE (uncommitted at bake time)

Branch **`feature/tracks-recording`** (from `origin/develop`), working tree dirty. The confirmation-dialog plan is fully delivered: one `ConfirmDialog` on the overlay ladder replaces every `ModalBottomSheet` confirmation and the merge / orphan-recovery `AlertDialog`s.

- **Component** — [`ConfirmDialog.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt): portrait-width panel (`min(maxWidth, maxHeight)`) both orientations, flush bottom, rounded top corners, open-bottom accent border, nav inset inside, IME retained, wrap + scroll, 450 ms panel slide, caller actions (primary/secondary/danger), optional bottom-most Cancel.
- **Scrim ownership** — the dialog owns its own `ui.scrim.alpha` layer; ladder plumbing deleted (`LocalDialogDismiss`, `activeDialogDismiss`, dialog-first `scrimDismiss`).
- **Hoist** — merge + batch delete raised via `ConfirmRequest` / `ConfirmRequestHost` ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)); source drawer stays open.
- **P4 (this session)** — all scrims are hard on/off toggles; the ladder scrim yields while any dialog is visible (`OverlayChrome.dialogScrimActive` in [`OverlayLayerParams.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt)), so dims never stack.
- Build `gradlew.bat assembleDebug` SUCCESSFUL.

## Next step

Commit + push `feature/tracks-recording`, then device-verify the on/off scrim toggle (dialog over an open drawer, both orientations) and open the PR into `develop`.

## Warts / follow-ups

- `SlideDirection.FADE_ONLY` in `DrawerSlot.kt` is now unreachable (dead code) — cleanup candidate.
- The instant toggle changes every drawer's dim feel (snaps around the slide), not just dialogs — accepted.
- Prior, still outstanding: `feature/refact-C12` holds 3 commits (`ec57458`, `a000c18`, `96259b5`) needing push + PR.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt` — the single confirmation dialog
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — ladder scrim (hard toggle, yields to dialogs)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` — `OverlayChrome.dialogScrimActive`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — dialog state, guard flag, flag feed
- `docs/ui-drawer-guidelines.md`, `docs/ui-component-guidelines.md` — scrim/dialog specs

## Plans of record

- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_confirm-dialog-normalization.md` — dialog normalization + scrim ownership (P1 closed, P2 + P4 implemented)
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md` — R1–R8 (implemented)
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_section-title-color.md` — item A (implemented)
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_tab-finalization.md` — phase 1 (implemented)
