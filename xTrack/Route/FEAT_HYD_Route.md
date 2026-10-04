# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 08:56 UTC — written by `#bake`; absence means never baked

**Directive trace:** This session resumed the code-health plan and ran its Phases 0–7, so of the five covered
classes three were touched and none stopped: two orders were given and acted on — the repair-and-finish choice,
then `#impl` — no dependency was added, no machine-shaped data file was opened and the device was never touched.
Every claim about the code in the record followed a read except the build and suite figures, which rest on the
Code hop's own run; that gap was named by the session's `#review` and has not been closed.

## State

**The pass pipeline is dissolved and the shared layer renamed.** `RoutePassPipeline.kt` is gone: `RouteGridBuilder`,
`RoutePassRunner`, `RouteFinePass` and `RoutePassRules` ship as seats composed once by `RouteAvoidEngine`
(`gridBuilder` at :141, `runner` at :144, `finePass` at :147), and `publish`/`trace` still arrive as the engine's
own lambdas, so no `RouteUpdate` and no log line moved. **The naming pass then ran on the user's word**:
`spatial/avoid/` became `spatial/multipass/` in both trees and the eight `Avoid*` types took the `Multipass`
prefix, while the `avoid` engine id, the two engine class names and every `route.avoid.*` key were left alone.

**The gate is the existing suite, as the implementing hop ran it** — `apk-build.bat` green, the spatial package at
`190 tests, 1 failed, 2 skipped` and the full unit suite at `867 / 1 / 10`, the single red still
`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, the parked ratio residue. The rename has no runtime surface,
so the compiler and that suite are its whole proof.

**Two things are open.** `RoutePassRules` has no production caller — `betterPass`, `PassCost` and
`fineSpliceBetter` are held up by `RouteAvoidEngineTest` alone, while the engine ranks by
`forcedCrossingZoneNames.size` and keeps the fine line on priced cost, so the seat is either dead code or a rule
the ladder change dropped. And the old name still stands in the record's own corners: four test classes keep
`Avoid` in their names inside `multipass`, and `FEAT_DOC_Route_avoid-algorithm.md` with the three live plans
still print `spatial/avoid/`.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_code-health-split.md` — the plan of record: Phases 0–7 landed, its open questions carrying the unwired seat and the record residue
- `app/src/main/java/ykws/android/maro/spatial/multipass/` — the shared layer, eighteen files, the eight `Multipass*` types among them
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the composition, the ladder of fixed-λ rungs and the instrument
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRules.kt` — the seat with no production caller
- `xTrack/Route/FEAT_DSC_Route.md`, `docs/maro-code.md` — repaired this session, alongside the epic's two folded `## Implemented` lines

## Next Step
Decide `RoutePassRules` — delete the unwired seat or restore the ranking rule the ladder change dropped — and fold
the old package's name out of this hydration's siblings: `FEAT_DOC_Route_avoid-algorithm.md` and the three live
plans.
