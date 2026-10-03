# Route — deferred discard toast

**Feature:** Route · **Created:** 2026-10-03 · **Status:** shipped 2026-10-03 — the two-phase discard and its review-fix hop, build green

## Goal

Every route-mode **discard** becomes a two-phase gesture: the display updates at once, and the real disposal is deferred until the undo toast is confirmed. The toast is **insurance** for accidental endings, so it covers every discarding path — the Discard doors, the toggle and the back key.

## The model

- **Phase 1 — the press.** A discarding action sets a `pendingDiscard` state instead of running the disposal. The display reads a completed discard: the acquisition panel and the route line leave, and the **toggle reads off** — the toggle is itself a trigger, so a press that left its face unchanged would read as broken. The mode stays live underneath (`routeArmed` and `routeState` untouched, the engine still running), so Undo restores exactly.
- **Phase 2 — confirmation.** The real disposal — `endRouteMode()` plus the engine's `cancelLookup` — runs when the toast is dismissed: the timeout, a horizontal swipe, any other dismissal, or a second back press. That second back also proceeds with its normal job, the pending window being transparent to it.
- **Undo** clears `pendingDiscard`; the panel, line, toggle and selection return with **nothing recomputed**.

## Triggers — every discarding ending

- The acquisition panel's `Discard route` — `MapScreen.kt` 2778 / 2817.
- The map fan's Discard child — `MapScreen.kt` 2496, covering acquisition and followed.
- The toggle-off — `toggleRouteOff`, `MapScreen.kt` 1932, inside the acquisition.
- The back key — `leaveRouteMode`, `MapScreen.kt` 1922, inside the acquisition.
- The exit dialog's `Discard Route` — `MapScreen.kt` 3462, while Following.

`Save+Exit` and `Select` are not discards and never toast.

## The two links

- **Undo** — cancel the pending disposal; the exact state returns.
- **New acquisition** — confirm the disposal, then re-arm the same ends through `routesToCompute`, skipping the R83 stored-route pull-back. `armRouteMode` gains the force-fresh flag.

## The toast

- A new `ActiveSnack` subtype for the route discard, carrying acquisition-versus-followed so the message is named honestly; its text comes from string resources in `MapSnackbarHost`.
- `SnackRow` gains an optional **second action** (label + callback), defaulting off so the three delete snacks are unchanged apart from the new gesture.
- `SnackRow` gains a **horizontal swipe** that takes the timeout's own path, so a delete toast still commits and the route toast simply keeps the discard.
- Strings, both locales: `Route acquisition discarded. Next step?` and `Route discarded. Next step?`, `New acquisition` / `Nouvelle acquisition`, reusing `route_acq_title`'s phrasing and `action_undo`.

## The pending window's rules

- **A new arming commits then arms** — the drawer's Route action and the fan's Arm both confirm the pending discard before arming.
- **A drawer open leaves it pending** — the window survives the menu being opened.
- **The fan's other children commit first** — Save and Select act on the live mode, so they confirm the pending discard before acting.
- **The toast jumps the queue** — the route toast must not sit behind two delete toasts, or the window silently lengthens.
- **The engine keeps running** through the window; that is the deliberate cost of the deferred model.

## Records

- Amend **R57** (back ends the mode), **R59/R60** (the exit dialog's doors) and **R79** (the fan's Discard asks nothing) — each describes an immediate ending — and add one new requirement for the two-phase discard.
- Update the snackbar dismiss rule wherever the UI docs state it.
