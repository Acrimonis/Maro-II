# Context Hydration — Route — 2026-09-27

**Last Bake:** 2026-09-27 20:55 UTC — written by `#bake`

**Directive trace:** this session ran the covered action classes on the user's own word — the `#bake` and `#commit` they invoked and each Code hop they authorised — and none was taken without it; no dependency was added, no machine-shaped data file was opened, the device was never touched and nothing was deployed, so every claim about the code came from a file read or a command's own output.

## State

The avoid engine's outside-margin model is now unified across both speed-enforcement sources: the 300 m band and the speed zones each price an **outside margin in the search**, at their own `costFraction` (both 0.66). The speed zone's collar is stored per cell beside its interior limit (`AvoidGrid.collarLimitKn`) and priced `max(interior, collar × fraction)` by `zonePriceAtLimits`, read by the A* and the pull's guard alike; the band is split into core-at-full plus an outside ring at the fraction (`bandPriceAt`). The pull's hard standoff — the staged clearance F4 built — is gone, so both sources are purely priced and no clearance is kept off a ring. This session also shipped the progressive-draw plan, D8's fine re-search and F9(d)'s shore yield, the last of which the unified outside-margin model superseded. Gates green throughout: compile, the touched test classes one class per log, `apk-build.bat`.

## Target Files

- `app/src/main/assets/maro.properties` · `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the four outside-margin keys, `collarFraction` and `yieldShoreMargin` gone
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt` — the per-cell `collarLimitKn` beside `zoneLimitKn`
- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt` — `zonePriceAtLimits` and the band split `bandPriceAt`
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt` — the A* prices interior and collar through one lambda
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the field, the ring corner offset and the trimmed `PULLREF` line
- `app/src/test/java/ykws/android/maro/spatial/` — `RouteAvoidEngineTest`, `RouteZonePhase4Test`, `AvoidStage1Test`, `AvoidCostFieldTest`, `AvoidBandCostTest`

## Next Step

F1 and F2 — the refusal's honest sentence and the crossing-versus-forced claim gated on the box — the last remaining wording items; and the two long `GLOBAL_CONTEXT.md` Route lines still wait on a `findstr` extraction.
