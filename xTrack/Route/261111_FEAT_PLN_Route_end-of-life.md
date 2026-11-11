# Route — the end of a followed route

**Status:** implemented (2026-11-11, `feature/route-tweeks`); the design review and the implementation review are folded (§6); `apk-build.bat` green, 1 762 tests, 0 failures; the device pass is owed.
Decisions D1–D5 and D7 are the user's word of 2026-11-11; D6 (the stack) is built and moot as a decision.

**One line:** a followed route asks a question at three doors — the toggle's off and the back key (R59), the arrival
cue (R100) and the menu's Go over an unwritten line (R101) — and on the boat the user never wants to be asked. The
deferred window (R92) and its **Undo** already protect a discard, so the dialog is redundant: it goes, and arrival
becomes a notice that stands while the boat is at the destination.

## 1. What stands today (read from the code, confirmed by the review)

- The back key and the toggle's off call `leaveRouteMode()` / `toggleRouteOff()` ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2514)):
  inside the acquisition they already end through `discardRoute()` — the deferred window, no question; only
  `Following` raises the exit dialog (`requestRouteExit()`, `RouteExitReason.EXIT`).
- The arrival cue is `routeEtaStep`'s `arrivalCue` ([`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:301)):
  armed by a reading above `route.follow.arrival.etaSec` (60 s) and by every flip, fired on a new look-back low
  below it, disarmed on firing. The screen raises `RouteExitReason.ARRIVAL` from it (`MapScreen.kt:1234-1246`), and a
  flip closes the cue.
- `goRoute()` ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2794)) raises `RouteExitReason.RESTART`
  over a followed line that is not written (`routeFollowingSaved`); every other state restarts silently.
- The dialog is one block in [`MapDialogHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:289), its doors one pure
  function in [`RouteExitDoors.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteExitDoors.kt:66). The acquisition panel's ending
  door derives from the same function (`routeAcquisitionEndingDoor`), and the fan's Discard icon reads
  `route_exit_discard`.
- The **back handler** that matters is `RouteHost`'s `BackHandler(enabled = armed)` (`RouteHost.kt:146`, callback at
  `MapScreen.kt:3528`): it commits a standing window first and only then calls `leaveRouteMode()`. A press opens the
  window, the next commits it, and the one after reaches the shell's exit.
- `routeNoticeToast` is a 2 s transient (`LaunchedEffect(routeNoticeToastAt)`), painted by `MapStatusBanner` at
  `BottomStart`, all banners sharing one Box with no stacking (`MapScreen.kt:5691-5722`); it cannot hold a message.
- **Not in scope:** the home-port arrival prompt (`showArrivalPrompt`, the recording's three doors) shares the word
  *arrival* and nothing else.

## 2. Decisions

- **D1 — Arrival is a notice, not a cue.** While the route is `Following`, displayed (no discard window) and the
  boat is at the destination, a notice stands; it leaves when either stops being true. "At the destination" is the
  existing threshold read as a state: the followed plan's `remainingFrom(boat).durationSec < route.follow.arrival.etaSec`.
  No prompt, no arming, no new low.
- **D2 — Leaving a followed route is the deferred window.** The toggle's off and the back key end a followed route
  through the window R92 already owns: `leaveRoute()` once the line is written (`routeFollowingSaved`), otherwise
  `discardRoute()`; both raise their toast with **Undo** (4 s), which brings the exact state back with nothing
  recomputed. Planning already behaves this way and is untouched. Saving stays manual (the panel's `Save to track`,
  the fan's `Save`).
- **D3 — Go restarts silently from any state** (R101 loses its `RESTART` branch). The unwritten followed line is
  dropped with no Undo; the pair-too-short refusal and the in-flight-write guard stay.
- **D4 — Arrival never ends the route.** The loss mirror (R99) turns a followed route round when the boat heads back,
  so a route that died at the destination would break the round trip. Reaching the destination changes a notice, not
  the mode.
- **D5 — The notice is derived in composition, not stored.** `atDestination` is a pure function of the plan and the
  boat's position, as `RouteHost` already derives the split. No `routeAtDestination` state, no effect re-key, no
  stationary-boat case; `routeEtaStep` loses `arrivalCue` and `cueArmed` outright and keeps only the loss flip.
- **D6 — The notice is one more status pill in a stack.** `MapStatusBannerStack` (built 2026-11-11, on the user's
  word) draws every standing status message one above the other, so the arrival notice is a fifth entry in its list
  beside the transient toasts and nothing paints over anything; supersedes the review's `routeNoticeToast ?:
  arrivalNotice` single-banner fix. Whether the stack and the Undo snack column (`MapSnackbarHost`) share a band is
  not verified.
- **D7 — A one-second buffer, and no special case for short routes (the user's word, 2026-11-11).** The notice
  flips on or off only after the raw reading has held its new value for `route.follow.arrival.settleSec` (1 s, a
  new key beside `route.follow.arrival.etaSec`), in both directions, so GPS wobble at the 60 s point cannot blink
  it. The buffer is a time hold on the composed value, not a second threshold. A route shorter than the threshold
  is treated like any other: it reads "at the destination" once the buffer has run, as does a saved route opened
  with the boat at its end.

## 3. Phases

- **P0 — the pure core, tested first.**
  - [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt): `RouteEtaDecision` keeps `state` and `flip`;
    `RouteEtaState.cueArmed` and `arrivalCue` go (`routeEtaStep` lines 309, 317–329) together with the `arrivalEtaSec`
    parameter; the history, loss and debounce logic is untouched.
  - New pure `routeAtDestination(plan: RoutePlan?, boat: RoutePoint, thresholdSec: Double): Boolean` — false without a
    plan or on a non-finite reading, else `remainingFrom(boat).durationSec < thresholdSec` — and
    `routeArrivalNoticeShown(displayArmed, following, atDestination)`.
  - [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt): `onBoatFix` stops passing `arrivalEtaSec`, its early
    return (line 1012) builds the reduced decision, and its KDoc (1006–1008) drops the cue.
  - [`RoutePlanTest`](../../app/src/test/java/ykws/android/maro/ui/map/RoutePlanTest.kt): the three tests reading `arrivalCue` or
    `cueArmed` (lines 697, 717, 729) are rewritten as `routeAtDestination` — below the threshold is, above is not,
    no plan is not, a short route is from its first frame, the notice hidden while a window stands or the mode is not
    `Following`; the flip and debounce tests lose the `arrivalEtaSec` argument only.
- **P1 — the wiring in `MapScreen`.**
  - `leaveRouteMode()` and `toggleRouteOff()` become identical and merge into one door: for `Following`,
    `leaveRoute()` when `routeFollowingSaved`, else `discardRoute()`; the acquisition keeps `discardRoute()`.
    `requestRouteExit()` is deleted. A followed ending **ignores the press while `routeSaveInFlight`** (as Go does):
    the dialog's *Save and Leave* sequenced its ending behind the write through `afterWrite`, and without it a window
    committed mid-write would clear the armed ids the write reads.
  - `goRoute()` drops the `RESTART` branch: `restartRoute()` follows the refusal guards directly.
  - Deleted: the `routeExitReason` state (line 829) and its assignments at 2365 (`endRouteMode`), 2389
    (`discardRoute`) and 2404 (`leaveRoute`); the flip-closes-cue block and the `arrivalCue` raise in the boat-fix
    effect (which keeps its flip feed); `saveRouteTrack`'s `afterWrite` parameter (2755), its only user being
    `onSaveAndRestartRoute` (4578).
  - The notice: `val arrivalNotice = if (routeArrivalNoticeShown(...)) <string> else null` computed in composition and
    appended to the `messages` list of the `MapStatusBannerStack` call in `MapContent`; the string is
    `route_arrived_notice` in both locales. The raw reading passes a one-second hold (D7) before it shows or
    clears — a `LaunchedEffect` on the raw value that delays `route.follow.arrival.settleSec` then writes the
    shown value — and the key is added to `maro.properties` and read through `AppConfig`.
- **P2 — the removals.**
  - [`MapDialogHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt): the dialog block (289–344), the
    `RouteExitReason` enum, and the params only it read — `routeExitReason`, `routeState`, `routeViewModel`,
    `onDismissExit`, `onLeaveRoute`, `onDiscardRoute`, `onSaveRoute`, `onSaveAndRestartRoute`, `onRestartRoute` —
    with their call-site arguments (`MapScreen.kt` ~4571–4580); the stale KDoc at lines 26–32 and 44–48 is rewritten.
    `anyConfirmDialogOpen` (1938) never read `routeExitReason` and is untouched.
  - [`RouteExitDoors.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteExitDoors.kt): `RouteExitDoors` / `routeExitDoors` go;
    `RouteExitDoor` and `routeAcquisitionEndingDoor` stay, the latter inlining its two doors (written:
    `route_exit_leave`, accent; unwritten: `route_acq_discard`, red).
    [`RouteExitDoorsTest`](../../app/src/test/java/ykws/android/maro/ui/map/RouteExitDoorsTest.kt) is **rewritten**, not trimmed — line 82
    asserts the derivation from `routeExitDoors(...).loss` that no longer exists.
  - Strings, both locales: delete `route_exit_title`, `route_exit_save`, `route_exit_continue`,
    `route_restart_title|save|stay`, `route_arrival_title` (no reader outside the deleted files); keep
    `route_exit_discard` (`FanIconComponents.kt:256`), `route_exit_leave`, `route_left_toast`; rewrite the comment
    blocks in both files that describe the dialog.
- **P3 — the docs, after the build is green.** `FEAT_DSC_Route.md` (Concept's *three outcomes* bullet, the mode's
  flow, R59, R60 — whose "second press" is already the **third** — R100, R101, Key Files' `RouteExitDoors`) and
  [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) §5.6's route paragraph (line ~926) and the panel table's footer line.
- **Done when:** `apk-build.bat` and the unit suite are green, the new tests pass, and `grep` over `app/src` finds no
  `RouteExitReason`, `routeExitReason`, `arrivalCue`, `cueArmed`, `route_arrival_title`, `route_exit_title`,
  `routeExitDoors(`, `afterWrite`.

## 4. Consequences, stated

- A followed, unwritten line lost to the back key or the toggle is recoverable for the 4 s of the Undo window;
  lost to Go it is not recoverable at all. The save door is the user's, by decision (D2, D3).
- `AppConfig.routeFollowArrivalEtaSec` and `route.follow.arrival.etaSec` stay; their meaning changes from a prompt's
  threshold to the notice's.
- About 150 lines leave `MapScreen`, `MapDialogHost` and `RouteExitDoors`; two pure functions and one string arrive.

## 5. Left alone, named

- `goRoute()` commits a standing window (line 2795) **before** its in-flight-write guard (2800), so a window opened
  while a write runs is committed mid-write. It exists today and this plan does not widen it for Go; the new
  followed-ending guard in P1 closes the same race for the two doors it adds.

## 6. Review log

One `ask` review (2026-11-11), §1 confirmed line by line. Taken: the unlisted compile-time readers (assignments at
2365/2389/2404, `arrivalCue = false` at `RouteViewModel` 1012, `afterWrite`), the dead `MapDialogHost` params and the
false `anyConfirmDialogOpen` clause, the derived-in-composition simplification (D5), the single-banner overlap fix
(D6), the short-route case (D7), the in-flight guard, the test count (three, not five) and the `RouteExitDoorsTest`
rewrite, R60's "third" press, the merged door. Dropped: a pure `routeFollowedEnding` — `discardRoute()` already
chooses its own kind, so the wiring is two lines. Not taken: a hysteresis carried in `RouteEtaState` — the user took
a one-second time hold instead (D7).

## 7. Implementation review log

One `ask` review of the built change (2026-11-11): no functional defect in the wiring. Taken: the stale comments and KDoc that named the removed dialog or cue (`MapScreen`, `RouteViewModel`, `RouteConfirmPanel`, both `strings.xml`, `maro.properties`, `AppConfig`, `RoutePlanTest`); the at-the-destination reading wrapped in `remember` so the plan scan runs on a change of plan or position only; the settled reading cleared at once when the mode leaves `Following`; `onBoatFix` kept returning its decision, now documented as for the tests. Not taken: a test of the one-second hold (it lives inline in the composable; `routeAtDestination` and `routeArrivalNoticeShown` are covered); a toast when a back press is ignored during a followed write. Unverified: the status stack against the Undo snack column, and every on-device behaviour.
