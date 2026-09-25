# Context Hydration — Route — 2026-09-25

**Last Bake:** 2026-09-25 19:15 UTC — written by `#bake`; absence means never baked

**Directive trace:** no dependency added, no machine-shaped data file opened, no device touched and nothing deployed; the session's writes were the focus entry, one new plan in `xTrack/Route/`, the epic's own pointer and the state files this bake rewrites — and no git write ran in this session.

## State

Nothing was built. The session answered whether the line can be drawn while the engine builds it, wrote the design, and had it independently reviewed: [`260925_FEAT_PLN_Route_progressive-draw.md`](260925_FEAT_PLN_Route_progressive-draw.md) specifies `RouteProgress(stage, points)` replacing the bare `stage` channel on the seam, one emission per boundary, geometry at `PULL` and `SNAP` alone, a dedicated `route_progress` overlay in the track band and one new appearance key — with the plan's table, `Confirm` and the ladder untouched, all three reading `state.plan`. The review returned **revise** on seven findings and **none is folded yet**: the brief-versus-delivery gap (nothing is drawn during the A\*, where the time goes), the answer being the snapped line pulled a second time, the corridor retry missing from the record, the overlay's title and insertion point left to the build, the dummy's silence under the seeded default, the chain mapping's allocation uncosted, and the two fakes the channel rename touches. The mode itself ships unchanged — the acquire-on-a-press workflow, the harness (`dummy` default, `avoid` selectable) and the avoid engine's stage 1 — with the route-filtered suite green at 103 tests from the 2026-09-24 build.

## Target Files

- `xTrack/Route/260925_FEAT_PLN_Route_progressive-draw.md` — the design of record for the drawing pass, in design, reviewed revise
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` · `RouteAvoidEngine.kt` · `RouteDummyEngine.kt` — the stage channel that becomes the progress channel, and where its five emissions land
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` · `RouteConfirmPanel.kt` · `RouteHost.kt` — the proxy, the panel's one parameter, and the drawing path with the pool's title trap
- `app/src/main/assets/maro.properties` — `route.progress.transparencyPct`, the one new drawing value
- `xTrack/Route/FEAT_DSC_Route.md` — the `## Docs` pointer and the `routing-engine` summary, rewritten onto the shipped two-engine state

## Next Step

Fold the review's seven findings into the plan, worst first, and put its one reopened question back to the user — nothing is drawn during the A\*, so an emission inside the A\* is the only lever that would meet the brief as read, and it is theirs to re-decide. Also standing from the 2026-09-24 review, untouched: the discarded engine search a re-entry's first `Acquire route` pays, the lead's freshness gate, the session's twin link table, and the two session members only tests call.
