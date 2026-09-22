<!-- scope: feature -->
# Route interaction — the build plan

**The requirements are not here.** [`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md)
is the master book and holds them all — **R1 to R28** for the route mode and **R29 to R42** for the trace a saved
route becomes, with their reasons, the engine's interface, both phases state by state, and the technical details
it settles — while the epic's `## Walk` records the decisions level by level. This file adds only what a build
needs and a book must not carry: **the files each step owns, the order they land in, and what counts as done.**

## 1. What already exists, so the build does not rebuild it

- The placeholder engine at one expression in `MapScreen`, the toggle, the screen-centred aim with its live
  preview, the confirmation panel in the dashboard slot, the pin, the trip figure at the pace in force, and the
  save as an ordinary track — all whole, all untouched by this plan except where a step says otherwise.
- The line and pin keys already live in `maro.properties` behind `AppConfig`; the **nine** keys this plan adds join
  them — the two ask keys, the crosshair's three, the refresh gate's two and the ladder's two — and no literal
  decides a value the file holds.
- **The two engines that searched the water are already gone**, with their bake, their proto, their artifact and
  their tests, removed in this branch's earlier delivery and held in the two documents `xTrack/Route/` carries —
  so nothing here retires an algorithm, and the placeholder of step 3 is what that history left behind.

## 2. The steps, in the order they land — each with the files it owns

1. **The ask keys** — `route.ask.minTargetMoveM=25` and `route.ask.settleMs=300`: `app/src/main/assets/maro.properties`
   plus their accessors, bounds and KDocs in `app/src/main/java/ykws/android/maro/config/AppConfig.kt`. The 25 m
   literal moves home; the leading-edge preview leaves code and KDoc together, so nothing is asked on the arming
   frame.
2. **The worker and the two phases** — `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt`: one `Job`
   cancelling and restarting per new aim, the slot keeping the newest aim alone, the standing plan deliberately
   surviving an abort, and the phase the mode is in (choosing, following) becoming explicit state. The refresh of
   step 7 fires its call on that same worker, and the sharing is **pinned by test** — one job, no double cancel,
   no dropped answer.
3. **The seam as a session** — `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` gains the two entry
   points, the validity question and `isReadyToRecompute()`; `RouteDummyEngine.kt` answers them without judging
   anything and stays ready on construction; `MapScreen.kt` keeps building it at one expression. The
   placeholder's own fiction is fixed at **15 kn on every leg**, its leg times following from that speed and its
   direction from the origin to the destination — a constant of the placeholder's own, deleted with it, so the
   free-water pace setting does not move a dummy route while the placeholder ships.
4. **Validity and the crosshair** — `RouteOverlay.kt` draws the aim ring's refused state from `route.target.color`,
   `route.target.widthDp` and `route.target.pulseMs`, `RouteConfirmPanel.kt` shows the sentence naming the reason
   with the outcomes hidden while no plan exists, and the origin is asked once at arming.
5. **The two phases' slot content** — `RouteConfirmPanel.kt` for the draft's outcomes and the following panel with
   its status and actions, `RouteOverlay.kt` for the toggle's two on-phases, one shared home for the pulsing dot
   that `TrackStatusIcon.kt` draws today, and `MapScreen.kt` for the slot itself.
6. **The one exit dialog on both doors** — `MapScreen.kt` hosts it and `RouteConfirmPanel.kt` asks it: Continue ·
   End without saving · End and save, the save carrying the last/all scope, with the draft's exit still silent.
7. **The refresh cycle** — `RouteViewModel.kt` gates on `route.refresh.intervalSec` or `route.refresh.offRouteM`
   and consults the engine's veto, `RouteConfirmPanel.kt` carries Freeze/Resume and Abort, and `MapScreen.kt`
   raises the failure toast on the app's own snackbar surface with nothing on the map unstaled. Leaving the mode
   cancels the call in flight, and a failure arriving after the mode has gone is not toasted.
8. **The ladder and the drawing path** — `RouteHost.kt` attaches its objects once and mutates them in place, so a
   refresh repaints rather than rebuilds, within `route.ladder.oldest.nb` and `route.ladder.latest.nb` painted
   oldest 20 % to newest 80 %.
9. **The aim's two couplings** — `PanResumeTimer.kt` gains the draft's hold, `NavigationViewModel.kt`'s
   `setRouteAiming` becomes phase-keyed, and `MapScreen.kt` releases both at confirmation **in that same frame**,
   the centre returning to the current fix in GPS mode and to the origin coordinate in demo mode.
10. **The save: one file per route, the set's names, and no duplicate** — `MapScreen.kt` hands `TrackFromCourse`
    the instant the route was **generated and finalised** rather than the save's, which is what dates and names the track, and
    `TrackViewModel.kt`'s KDocs follow the rename of the flag the trace work owns. `RouteViewModel.kt` keeps the session's
    routes as **the ladder**, active and stale alike, and the exit dialog's all-scope writes every one of them,
    named `Route <creation> · n/N` in creation order, the option disabled while fewer than two routes exist. A
    route already saved individually is **renamed, never rewritten** — its track takes its index in the set, the
    rename unconditional and a track the user has renamed by hand included — the
    already-saved ones are pinned as the set is saved, and the route-to-track link lives in `RouteViewModel.kt`'s
    own map
    rather than on the track, which `TrackFromCourse`'s own KDoc deliberately refused. Saving all twice writes
    nothing the second time.
11. **Tests and build** — the seam's session in `app/src/test/java/ykws/android/maro/ui/map/RouteEngineSeamTest.kt`,
    the aim's rule in `RoutePlanTest.kt`, the placeholder's contract in
    `app/src/test/java/ykws/android/maro/spatial/RouteDummyEngineTest.kt`, plus one new suite for the gate and the
    abort.

## 3. What every step must respect

- **No new dependency**, no new surface beyond the seams the isolation design names, and no literal where the
  properties file holds the value — the nine keys above are the only values this work adds.
- **Every user-facing line is a `@StringRes` id carried by both locales**, the action labels and the two phases'
  sentences included; brand names and log tags stay as they are.
- **One home per fact:** the exit dialog is one dialog reached by two doors, the resume hold is one list, and the
  pulsing dot becomes one home shared with the recording toggle rather than two copies of four values.

## 4. Verification

- **The tests pin the behaviours the spec argues about:** a new ask aborting the one in flight read through a
  gated engine, the plan surviving that abort, the gate firing on its two keys and the veto delaying it, the
  origin asked once at arming and never on a refresh, the failure leaving the standing line unstaled, and the one
  dialog reached from both doors.
- **The build is green twice:** `gradlew :app:compileDebugKotlin` clean, then `apk-build.bat` and
  `:app:testDebugUnitTest` green with no failure.
- **The device pass stays the user's**, and it is the one thing the plan cannot settle: the aim under a dragging
  finger, the fling's cancellation, the panel in the dashboard slot, and the saved course appearing in the list as
  a route.

## 5. What this plan is not

- **Not the requirements:** the spec holds them and wins on any conflict, so a change to what the mode does is a
  change to that file first and to this one second.
- **Not the trace work:** the flag's rename, the filter, the card and the Settings rows are Tracks' own, written
  up in [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md),
  and the only joint is the rename this plan's step 10 follows.
- **Not a corpus entry yet:** the epic's `## Docs` pointer lands with the build, so this file stays the plan in
  design until its pointer appears in the feature's `## Implemented`.
