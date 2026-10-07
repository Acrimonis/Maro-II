# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 10:53 UTC — written by `#bake`
**Branch:** feature/tracks-rotes-norm — the Tracks-owned path render engine's branch, on which the route's map half was migrated; the plan was then extended with the settled speed/arrow normalisation, in design only

**Directive trace:** no covered action stopped. No dependency was added, no machine-shaped data file was opened, and the device was the user's throughout — the route's map half changed but was never deployed or logged, that pass left owed. Every claim about the code came from a file read or a build's own output; the run's figures — the 653-test scoped suite and the green `apk-build.bat` — are the commands' own output. This session's plan extension touched no route code, so it carries no figures of its own, and no git write ran before the commit that closes the session.

## State

**The live route now draws through the one path render engine Tracks owns.** The plan [`../Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md`](../Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md) settled **D10** — the live route's lines are **rebuilt** each pass through the painter's `lineRendering(spec, …)` door, while the destination pin stays **attach-once** (a marker, mutated in place) — because speed banding yields a stroke count a fixed pool cannot hold. `RouteHost`'s hand-built polylines — the pool, the derived casing, the travelled run and the provisional line — are gone, replaced by painter output under the same `route_*` titles, so `OverlayZOrder`'s route tier is unchanged.

**Parity landed with it.** The route spec is built from a `RoutePlan`, and **D11** derives each point's speed from the leg it leaves (its haversine length over the leg's seconds), so the chevrons and the speed banding run on the live line exactly as on a stored route — the plan's partial or draft legs yield a neutral speed rather than a wrong one. The dash reads `path.line.dash.*`; the pin's ring colour is now `path.pin.ring.color`; and the two route gates (`path.gate.speedColor` / `speedArrows`) join the master Arrows/Colours chips through `routeLineRenderPlan`, read from `appSettings` passed into the host.

**Designed, not built — the route's gate pair would retire.** The same plan's `## Speed and arrow display` section records the next normalisation, and **no code for it exists**: the route gates `path.gate.speedColor` / `path.gate.speedArrows` give way to `path.route.heatmap.enabled` / `path.route.arrow.enabled`, the meaning narrows so each kind's persisted pair governs its own kind, and the Routing tab's rows are then owned by Route with no shared control. It stays in design until ordered.

**Open and recorded.** The **device pass is owed** — a followed route's chevrons and bands, the pool's rebuild not flickering, and the pin still landing on the resolved end. One behaviour change to confirm on the water: the live (unpinned) route now dashes like every route. The parked `route.avoid.fine.cellRatio` red is the scoped suite's single red, untouched by this pass. Named rather than lost: this feature's Feature-Summaries row in `GLOBAL_CONTEXT.md` could not be re-dated because its one line exceeds the editor's match limit — its front-matter's `modified` alone carries this bake.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — migrated onto the painter; `appSettings` threaded in; the remaining-run speed derivation
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the `lineRendering` door and the shared `directionSpacingProvider` the route reads
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the one `RouteHost(...)` call now passes `appSettings`
- `app/src/main/java/ykws/android/maro/ui/map/LineRenderSeam.kt` — the route adapter (`toRenderPoints(points, legTimesSec)`) the host reads
- `xTrack/Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md` — the plan, its in-design speed/arrow section naming the route gate retirement
- `docs/maro-code.md`, `docs/ui-drawer-guidelines.md` — the route's map half noted as the shared engine's

## Next Step

Take the owed device pass over the migrated route — chevrons, bands, the pool's rebuild and the pin — and confirm the live route's new dash against the user's eye; the route's gate retirement waits in the plan, in design, until it is ordered.
