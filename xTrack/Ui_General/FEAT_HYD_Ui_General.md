# Context Hydration — Ui_General — 2026-10-23

**Last Bake:** 2026-10-23 15:22 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#new toats-life-cycle`, the z-order discussion, then `#focus Ui_General`/`#bake`/`#commit`/`#merge`/`#push`), the device was not touched, and every claim about the code rests on a file read.

## State

On `feature/toats-life-cycle`, cut from `origin/develop` with `--no-track`. The map's bottom-band snackbar/toast stack now composes **before** the drawer ladder: `MapSnackbarHost` sits ahead of `OverlayLayer` in `MapScreen.kt`, so an open drawer and its scrim paint above a visible toast while the dialogs still paint above the drawer; the `MapStatusBanner` toasts already lay inside `MapContent`, below the menu. `docs/ui-drawer-guidelines.md` §1 lists the stack in Layer 0.

Gates: `:app:compileDebugKotlin` and `:app:assembleDebug` green; the device pass is the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `MapSnackbarHost` moved ahead of `OverlayLayer`, the call site carrying the z-order comment
- `docs/ui-drawer-guidelines.md` §1 — the bottom-band stack added to the Layer 0 diagram

## Next Step

Nothing owed on the z-order itself; the device looks at the menu opened over a route-ending or undo toast. Retirement candidates reported, not moved: `261004_FEAT_PLN_Ui_General_action-toasts.md` and `261005_FEAT_PLN_Ui_General_action-toast-alignment.md` — `#archive` does the moving.
