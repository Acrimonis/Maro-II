# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 11:32 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/route-n-floOow` (a `#new`, `#focus`, then ordered changes): the fan's close rule, the two-phase discard toast, and the force-fresh arming. Of the five covered action classes none ran unasked — no dependency was added, no machine-shaped data file was opened, no file was written without the user's word, and the device was never touched; every claim about the code followed its own read.

## State

**Every route discard is a two-phase gesture** (R92): a discarding press — the panel's Discard, the fan's Discard, the toggle-off, the back key in the acquisition, and the exit dialog's Discard — sets a `pendingDiscard` window, so the panel and line leave and the toggle reads off while the mode stays live underneath. The real disposal runs on the toast's dismissal (the 4 s timeout, a horizontal swipe, a second back press, or New acquisition); Undo clears the window and everything returns with nothing recomputed. A new arming commits then arms, the fan's Save/Select supersede the window, and the route toast jumps the snackbar queue.

**The route fan closes on acquire and follow** (R91): the `Route` and `Route auto` children close the arc, while `Discard`, `Save+Exit` and `Save` leave it open; otherwise it closes on the back key, the scrim or the parent anchor's own toggle.

**Every explicit arming re-searches** (R83, R92): the fan's `Route`, the toggle and the drawer's Route action pass `forceFresh = true`, so the stored-route pull-back belongs to the autoselect (`Route auto`) arming alone, which still follows a saved line directly. Build green (`apk-build.bat`); nothing device-validated.

## Target Files

- `MapScreen.kt` — `pendingDiscard`, `discardRoute()`, `commitPendingDiscard()` / `cancelPendingDiscard()`, the toast handlers, the fan's close rule and the force-fresh arming
- `MapDashboardController.kt` — the route toast's queue jump
- `MapSnackbarHost.kt` + `SnackRow` — the route-discard snack, its second action and the horizontal swipe
- `MapDialogHost.kt`, `RouteHost.kt`, `RouteConfirmPanel.kt` — the discard wiring
- `values/strings.xml` + `values-fr/strings.xml` — the toast and New-acquisition strings
- `FEAT_DSC_Route.md` — R91, R92 and the Delta

## Next Step

The device pass over the discard toast (its four confirmations and Undo), the fan's close rule and the force-fresh arming; the Delta's owed device passes and the parked swap control stand.
