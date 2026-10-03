# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 13:57 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/route-render` (a `#new`, `#focus`, then `#impl` through the pipeline): of the five covered classes none ran unasked — no dependency was added, no machine-shaped data file was opened, no work started without an order, the device was never touched, and every claim about the code followed a read — the one gap the review's own sweep named being the `#focus` state write made without a verdict line, which sits outside the five.

## State

**The selected route is reinforced by shape and opacity** (R93): an edge derived from the line's own colour is drawn beneath the selected rung during the acquisition and beneath the followed line while `Following`, its colour the line's own pushed toward black by the one lever `ui.reinforce.darkenPct` (55, clamped 0..100) and its width `route.line.casing.widthDp` (8 dp over the 6 dp core); the edge takes the line's own transparency, so **no colour, brightness or alpha key** was added.

**While following, the line splits at the boat** — slot 0 draws the split's remaining run alone, the casing mirrors exactly what slot 0 draws, and `route_travelled` draws the travelled run only when slot 0 actually carries the remaining run, at the shared `route.dimmed.transparencyPct`; where fewer than two points remain the whole line stays at full strength with the travelled overlay off, which is the mode's own arrival rule. `RoutePlan.splitAt` is the one nearest-leg projection and `remainingFrom` reads it, so the trip cell and the fade cannot disagree.

**One generic lever and one pure helper** — `reinforcedColor(color, darkenPct)` in `ui/color/ColorReinforcement.kt` is pure and RGB-only with the caller reading the lever, and the app-wide rule, that a colour needing reinforcement is derived rather than given a second key, sits beside the transparency convention in `docs/ui-component-guidelines.md` with a pointer from `docs/color-scheme.md`.

Build green (`apk-build.bat`) and the 23 route suites green over 185 tests; the review's one clean-up, the arrival threshold written twice, now has a single home in `RouteHost` read by both the paint key and the run selection, and **the user's own device pass on 2026-10-03 confirmed** the edge's contrast, the arrival repaint, the paint order inside the route tier, the split's join, the degenerate start and the demo-mode marker.

## Target Files

- `RouteHost.kt` — `route_casing` and `route_travelled` attached before the pool, the split under `remember` on its identity, the arrival discriminator in the paint key, and the new `boatPosition`
- `RouteViewModel.kt` — `RoutePlan.splitAt`, `RouteSplit.bestLegIndex`, and `remainingFrom` delegating to the one projection
- `MapScreen.kt` — `routeBoatPosition` fed to the single `RouteHost(...)` call
- `ColorReinforcement.kt` + `ColorReinforcementTest.kt` — the pure helper and its suite
- `AppConfig.kt`, `ui.properties`, `maro.properties` — the lever, the casing width with its bounds, and the two repaired sentences
- `RoutePlanTest.kt` — the split's cases and the exposed leg index
- `docs/ui-component-guidelines.md`, `docs/color-scheme.md` — the app-wide reinforcement rule and the pointer
- `FEAT_DSC_Route.md` — R93, the drawing bullet, the decoupling rule, the Key Files rows and the plan pointer

## Next Step

The open walk's item 1 — **D4**, the fine band: change the code or change the record — with its parked sibling at item 15 under the closed 2026-09-28 level.
