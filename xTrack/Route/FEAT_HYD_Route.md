# Context Hydration — Route — 2026-09-30

**Last Bake:** 2026-09-30 19:15 UTC — written by `#bake`; absence means never baked

**Directive trace:** The implement and commit sessions ran on the user's own commands; no dependency was added, no machine-shaped data file was opened, and the device was never touched. Every claim about the code followed the file read that settled it; the commit and push are the user's own.

## State

**The branch is `feature/route-confirm`; the fan's dual-purpose child and the two acquisition regressions are fixed.** The fan's second-from-top "route" child arms the route while idle and selects/confirms it once the acquisition has a settled line. The update stream is no longer torn down per arm — `distinctUntilChanged` keeps the one `updates` subscription alive — and the followed route is painted again: the pool reads the `Following` plan's own points and pin once `selectRoute()` clears the page set. `gradlew :app:assembleDebug :app:testDebugUnitTest` is BUILD SUCCESSFUL with the whole unit suite green; nothing is device-validated.

## Target Files

- `ui/map/MapScreen.kt` — the fan's route child enablement and action (dual purpose)
- `ui/map/RouteViewModel.kt` — `distinctUntilChanged` on the update collection
- `ui/map/RouteHost.kt` — the pool and pin read the `Following` plan
- `FEAT_DSC_Route.md` + `GLOBAL_CONTEXT.md` — the record

## Next Step

The device pass — the user's own: arm via the fan's route child, read the acquiring stage and the line building, then select and read the painted followed line.
