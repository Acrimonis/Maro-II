# Context Hydration — Route — 2026-09-26

**Last Bake:** 2026-09-26 10:52 UTC — written by `#bake`

**Directive trace:** three action classes ran, each on the user's own word — the branch was cut on the `#new menu-route` invocation (a fetch and `checkout --no-track -b`, run in Code mode, carrying the one modified state file across), the record was moved on the explicit `#archive` invocation (a plain filesystem `move`, nothing staged), and one deletion was declined for want of that word ([`ui/icons/Route.kt`](../../app/src/main/java/ykws/android/maro/ui/icons/Route.kt:1) stays until it comes). No dependency was added, no machine-shaped data file was opened, the device was never touched and nothing was deployed; every claim about the code came from a file read or a tool's own output.

## State

The session designed a change rather than building one: the drawer's Route action group leaves and a read-only summary of the mode takes its place, with the boat-relative pair measured from the nearest point on the line instead of the nearest vertex. The design of record is [`260926_FEAT_PLN_Route_menu-mode-summary.md`](260926_FEAT_PLN_Route_menu-mode-summary.md), written this session and independently reviewed by Ask to **revise** — all twelve findings are folded, with the removal inventory and the arithmetic's invariants holding as written. In the record: one plan was retired through `#archive` (the 2026-09-22 build order, both gates passed, its five live references swept) and the parked duplicate-save point was closed by the user's word — the disabled button is the answer, prevention chosen over recognition — so the epic's `## Todos` no longer carries it. Nothing is built: the plan awaits its build order, and the one wording still the user's is the new sub-title the left-over pair needs (proposed *Remaining* / *Restant*).

## Target Files

- `xTrack/Route/260926_FEAT_PLN_Route_menu-mode-summary.md` — the design of record: the removal inventory, the summary's rows and gate, the bundle swap, and the geometry change with its tests
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the Route group, its `RoutePill` and its seven parameters, all leaving; the summary block arriving
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `remainingFrom` projected onto the nearest leg, its KDoc rewritten with it
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` · `OverlayLayer.kt` · `MapScreen.kt` — `RouteSummaryData` in place of `RouteOverlayData`, the chrome flag, and the three men-only action aliases deleted
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the ETA split given one home with the drawer reading it
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — five `menu_route_*` keys retired, one new sub-title added in both locales
- `xTrack/Route/FEAT_DSC_Route.md` — the walk level closed this session, and the seam sentences the implementation amends
- `app/src/test/java/ykws/android/maro/ui/map/RoutePlanTest.kt` — the leg-times pin amended and the halfway case added at the projection's stated tolerance

## Next Step

Build the plan in its own order, geometry first: project `remainingFrom` onto the nearest leg and pin the halfway case, then swap the bundle and add the chrome flag, then the drawer's block and the strings, then the record — the epic's seam sentences, the plan's `## Docs` pointer and the snap line at [`260924_FEAT_PLN_Route_acquisition-and-route-workflow.md:56`](260924_FEAT_PLN_Route_acquisition-and-route-workflow.md:56) — with `apk-build.bat` and the route-filtered suites green. Settle the sub-title's word with the user before the strings are written, or land on the proposal and name the deviation. Standing from earlier and untouched: the progressive-draw plan's seven findings unfolded, the fine band's mechanism and its width, the pin's hiding flag, and the owed device pass.
