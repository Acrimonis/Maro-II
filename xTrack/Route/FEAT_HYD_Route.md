# Context Hydration — Route — 2026-09-28

**Last Bake:** 2026-09-28 12:18 UTC — written by `#bake`

**Directive trace:** this session ran the covered action classes on the user's own word — the `#bake`, `#commit` and `#push` they invoked, the two mode switches they authorised and every Code hop they ordered — and none was taken without it; no dependency was added, no machine-shaped data file was opened, the device was never touched and nothing was deployed, so every claim about the code came from a file read or a command's own output.

## State

**Phase 6 — curve smoothing and turn rounding — is implemented and built, and it is NOT yet validated on the device.** Its first cut was reverted uncommitted and the phase rebuilt from scratch: `RouteCurveFitter` fairs a **run** of corners into straight → spiral → arc → spiral → straight, splits a run at the bend's own transition with a per-corner fallback when a joined run's curve cannot meet its legs, caps the radius at the pace's own, finds the clearing radius by a **24-step bisection**, and composes the corner speed as `min(cap, max(decelFloor, minSpeed))` — a slower corner the one lever, the radius never below `v²/a_lat`. Its walls read the coast margin, the depth gate's min **and** its new 20 m standoff, the grid's blocked set and the two carved approaches, reusing the pull's `marginWaived`, and it reads no soft price. `RouteEta` charges a cap at every arc point, and the answer keeps the **pre-fairing** line's distance and clock with only the caps' delta added, the last leg carrying the residual. Four keys: `route.turn.lateralAccelMps2` (**2.0**), `route.turn.transitionSec` (**2.5**), `route.turn.minSpeedKn` (5) and `route.avoid.depthGate.marginM` (20). Ask returned **revise** on the first build — the relaxation discarding the curvature guarantee, and the pace-wide run split — and both blockers and every should-fix were folded, but the fixes were **not re-reviewed**. `apk-build.bat` SUCCESSFUL and the route suites plus the full unit suite green. **The device pass is owed, and the user is about to make other functional changes to test the fairing in a better way — this is the point to resume from.**

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteCurveFitter.kt` — the post-processor (new this session)
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the call between `fineReSearch` and the clock, the base-and-delta figures, the pre-fairing probe
- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt` — the `CurveCap` point cap and `decelSpeedMps`
- `app/src/main/assets/maro.properties` · `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the four turn/depth keys
- `app/src/main/java/ykws/android/maro/data/model/RouteResult.kt` — the `durationSec` wording
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt` — `marginWaived`, the shared clearance
- `app/src/test/java/ykws/android/maro/spatial/avoid/RouteCurveFitterTest.kt` · `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt`

## Next Step

Resume from the **device pass**: a GPX showing chorded arc points through a bend with `lateralAccelMps2=2.0` and `transitionSec=2.5`, after the user's other functional changes land. Asked and not done: the Ask review's fixes are unreviewed; Phase 5 (marker weights) is untouched; the F1/F2 wording pair of the standoff register still waits; and the `GLOBAL_CONTEXT.md` Route summary row still needs a `findstr` extraction before it can be rewritten.
