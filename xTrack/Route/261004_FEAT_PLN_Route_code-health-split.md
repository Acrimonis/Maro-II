<!-- scope: feature -->
# 261004_FEAT_PLN_Route_code-health-split

Topic: **the pass pipeline's own decomposition, and the `avoid` name two engines have outgrown** — the
2026-10-04 extraction moved the monolith rather than breaking it, so this plan splits it into named
collaborators and renames the package they live in. **Phases 0–7 have landed; only the record is owed.**

Status: Phases 0–7 landed — the pipeline dissolved into the four seats, then the package and its eight `Avoid*` types renamed to `multipass` on the user's order.

## What landed (2026-10-04, 08:00 to 08:10 UTC)

- **The baseline this plan was written against was already half-applied, and red.** `RoutePassPipeline.kt`
  had lost its models and its two formatters (Phase 1), while eight of its ten primitives were
  byte-identical twins of a new
  [`RoutePassPrimitives.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:28)
  — conflicting overloads in one package, so the module did not compile.
- **Phase 0 repaired it, declarations only** — the eight twins and the class's duplicate `limitAtFor` member
  went, and the now-unused imports with them; the primitives file was not touched.
- **The four seats then left the class, and the emptied `RoutePassPipeline.kt` was deleted.** No
  `RoutePassPipeline` reference survives in `app/src`, and the package now carries the seats beside the
  primitives, the models and the log formatters.
- **The engine composes them once** — `gridBuilder`
  ([`RouteAvoidEngine.kt:141`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:141)),
  `runner` [:144](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:144) and
  `finePass` [:147](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:147).
- **The gate, as the implementing session ran it** — the module builds, the spatial package runs
  `190 tests, 1 failed, 2 skipped` and the full unit suite `867 / 1 / 10`, the single red still
  [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:643),
  the parked ratio residue, its line shifted by an added import.
- **Four deviations from this plan's own text** — `RoutePassRunner` landed with `RouteFinePass` because the
  re-search owns the walk, so Phase 5 reports rather than builds a seat; three test call sites were
  re-pointed, not one; `RouteFinePass` takes the plan *and* the runner; and the emptied file was deleted.

## Why the split was the right pass — the measurements

- The class carried **three unrelated jobs** — build the grid, run a pass, keep the better line — which is
  why it reached 871 lines the moment the pipeline landed in one place.
- [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:627) ends
  at line 627 with its class closing at 600, and is still the ladder, the instrument, the repair, the
  readings and the rung set in one class; its class KDoc keeps the word *pipeline* for the algorithm as a
  process, left alone here.
- [`RouteAvoidEngineTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:1001)
  is 1 001 lines, so the seats' own gates are still read through the engine's test.
- Each seat is a phase the algorithm already has; none is speculative.

## The split as it shipped — four seats, composed by the engine

| seat | file | owns |
|---|---|---|
| `RouteGridBuilder` | [`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:22) | the corridor, the harvest, the one rasterize, the berth carve, the corner sets and the `GridContext` it returns |
| `RoutePassRunner` | [`RoutePassRunner.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:31) | one walk's A\*, taut pull, corner snap, clock and shares |
| `RouteFinePass` | [`RouteFinePass.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:17) | the refinement along the settled line, field first and raster second |
| `RoutePassRules` | [`RoutePassRules.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRules.kt:9) | the keep rules, pure and total — **no production caller, see the open questions** |

- **The primitives and the models keep one home each** — `RoutePassPrimitives.kt` (nine functions) and
  `RoutePassModels.kt` (four values) — and the earlier idea of splitting `bandLaw` off beside
  `RouteCostField` gave way to it, on `ONE HOME PER FACT`: one place to look rather than four.
- **The orchestrator is the engine's own composition**: the plan is the seats' one constructor argument and
  they are built once as fields, so no seat is constructed per pass.
- **The seats never own the emission sequence**: `publish` and `trace` reach them as lambdas exactly as
  they reached the class, which is why no `RouteUpdate` and no log line moved.

## What stays one place

- **The emission sequence is the engine's**, unchanged: the seats take `publish` and `trace` as lambdas
  exactly as the pipeline does today, so a split cannot move a `RouteUpdate` or a log line.
- **The ladder, the shared grid and the growth step** stay in the engine — the seats are called by it and
  never call back.
- **The repair, the world provider and the repaired pair** stay in the engine; nothing here touches them.
- **`plan.firstWalkGrid` and `plan.secondPassRegions` are unmoved** — the builder reads the first walk and
  the fine pass reads the second-pass region, and `avoid`'s plan still answers one tile and one box.

## The misnomer

- **Two names say `avoid`, not one**: the package `spatial/avoid/` and the types it holds —
  `AvoidWorld`, `AvoidGrid`, `AvoidSearch`, `AvoidPull`, `AvoidCellState`, `AvoidEdge` — while two engines
  (`avoid` and `evolutive`) derive from them. The plan's own words:
  [`261004_FEAT_PLN_Route_evolutive-engine.md`](261004_FEAT_PLN_Route_evolutive-engine.md:71) records it
  as accepted debt, deferred because it is churn with no behaviour behind it.
- **Decided by the user, 2026-10-04: the shared layer is called `multipass`** — the word names the code
  `avoid` and `evolutive` are both built from, the several passes over one grid, so the package becomes
  `ykws.android.maro.spatial.multipass` and `avoid` survives only where it means the algorithm: the id at
  [`RouteEngineChoice.kt:43`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:43)
  and `RouteAvoidEngine` against `RouteEvolutiveEngine`.
- **How the word is applied is mine, and it is the least that removes the false name** — the eight types
  carrying `Avoid` take the prefix: `MultipassWorld`, `MultipassGrid`, `MultipassSearch`, `MultipassPull`,
  `MultipassCell`, `MultipassCellState`, `MultipassEdge` and `LiveMultipassWorld`.
- **The shared pipeline family keeps its `Route*` names** — `RouteCostField`, `RouteEta`, `RouteGridPlan`,
  the `RoutePass*` four and the seats — because renaming those buys no clarity and would cost a second wide
  diff; the two collisions the earlier wording hunted (`RouteGrid` against `RouteGridPlan`, and
  `AvoidCellState` against the taken `RouteCellState`) dissolve under a prefix of its own.
- **The cost is mechanical and wide**: every import of `spatial.avoid.*` across main and test sources —
  the two engines, the choice row, the seats and the package's own cross-references. Behaviour-free, but a
  large diff, which is exactly why it was deferred once.
- **The order is split first, rename second**: the split is where the reading matters, and a package
  move landing first would bury the split's diff under renames in every file it touches.

## Objections to this recommendation

- **Phase 0 bought nothing but a green compile** — it changed declarations alone and was deliberately not
  bundled with a seat move, so a reader cannot tell the repair from the split's first step by its diff.
- **A walk from grid to timed line is genuinely cohesive.** Four classes sharing one `GridContext` spend
  indirection to buy file size, and the pipeline's story is no longer readable in one screen.
- **The plumbing has to be redone**: each seat needs the context passed in, and the three that publish
  need the injected lambdas, so the split's first diff is mostly signatures.
- **The tests are re-pointed a second time**, as they were for the extraction, and the suite's red stays
  the parked ratio test — so the proof is the count and the drawn line, never a new green.
- **All of it is unrequested by the algorithm work** — the adaptive grid (the grid plan's Phases 3–6)
  needs none of it, and this plan must therefore never block that one.
- **The counter-argument that wins**: none of the seats is speculative; each is a phase the algorithm
  already has, and the 871-line class is the outlier the user named. The indirection is one constructor
  argument per seat.

## Verification

- **The gate is the compiler plus the existing suite, and it is green again**: the module builds, and the
  spatial package runs `190 tests, 1 failed, 2 skipped` while the full unit suite runs `867 / 1 / 10`, the
  single red being
  [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:643)
  — untouched, as it was for the extraction.
- **The drawn line is proved equal**, never merely the count: a split that changes a `Double` argument
  order is the failure mode this feature has already named once.
- **The rename is proved by the compiler plus the same suite**: a package move has no runtime surface,
  so no other reading exists.

## Phases

0. **Landed — the red tree repaired, declarations only** — deleted the eight duplicated top-level
   primitives and the class's `limitAtFor` member from `RoutePassPipeline.kt` (since deleted whole),
   leaving [`RoutePassPrimitives.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:28)
   their single home; proved with the build and the suite (`867 / 1 / 10`, the parked ratio test). No seat moved.
1. **Landed — the models and the formatters left the file** — `RoutePassModels.kt` and
   `RouteLogFormat.kt`.
2. **Landed — `RouteGridBuilder`** — the context build and its instrument readers moved out; the engine composes it as `gridBuilder`.
3. **Landed — `RouteFinePass`** — the field-first refinement and the crossing re-solve moved out, with `RoutePassRunner` composed alongside as its re-walk collaborator.
4. **Landed — `RoutePassRules`** — the comparator, `PassCost` and the splice rule moved out; [`RouteAvoidEngineTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:763) re-pointed, and the emptied `RoutePassPipeline` deleted.
5. **Landed — nothing remained**: `RoutePassRunner` was already composed in Phase 3 as `RouteFinePass`'s re-walk collaborator, so what is left of the class is only the composition. No further seat was invented.
6. **Landed — the package rename** — `spatial/avoid/` → `spatial/multipass/`, imports only: both trees moved and
   34 files swept across main and test, the module building and the suite unchanged (`190 / 1 / 2` spatial,
   `867 / 1 / 10` full).
7. **Landed — the class vocabulary** — the eight `Avoid*` types took the `Multipass` prefix and their four
   files were renamed to agree; the `Route*` family and the seats keep their names, and no other type moved.
8. **Record** — bake, fold, and let the DSC pointers catch up.

## Open questions

- Whether `RoutePassRunner` is a seat at all or the orchestrator itself — if a pass owns the shares, the
  limit read and the corner snap, then the "pipeline" left behind is only the composition.
- Whether the engine's own 627 lines deserve a second pass (a `RouteInstrument` for the publishing and
  its readings, a `RouteEndRepair`), or whether the ladder is its honest whole.
- **`RoutePassRules` has no production caller** — `betterPass`, `PassCost` and `fineSpliceBetter` are held
  up by [`RouteAvoidEngineTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:765)
  alone, while the engine ranks by `forcedCrossingZoneNames.size` and keeps the fine line on priced cost;
  the seat is either dead code or a rule the ladder change dropped, and the tree cannot say which.
- **The DSC's dead pointers were repaired by hand in this pass**, and the code map with them:
  [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md:33) and [`docs/maro-code.md`](../../docs/maro-code.md:23) both
  read true now. Still naming the old package or the unwired seat: `FEAT_HYD_Route.md`,
  `FEAT_DOC_Route_avoid-algorithm.md` and the three live plans, left to the next bake, which is Phase 8.
