<!-- scope: feature -->
# Route — the ladder's three λ move to the properties file, and the dial's role is stated

**Date:** 2026-10-14 · **Branch:** `feature/route-aqu-x3` · **Status:** in design — the wording of this plan is
the order; the code landing is the same session.

**Order:** the user's word of 2026-10-14 — *clarify the lever of each of the three acquisitions*, resolved on
the question of where that lever lives: **promote the three rung λ to `route.ladder.*` keys in
`maro.properties` with `AppConfig` accessors and property comments, and correct the avoid-algorithm doc too.**

## The fact, verified before anything moved

- **The ladder is three rungs, each a full solve at its own fixed λ over one shared grid** — `around` 5,
  `best` 2.5, `fast` 0 — declared by [`RoutePreference`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt:20)
  and published on the terminal as a running best.
- **The aversion dial seats a rung; it never prices a cell.** Its only reader on the engine side is
  [`RoutePassRanking.bestRungIndex(preference, budget, rungs)`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:471),
  where [`RoutePreference.of(aversionKn())`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt:41)
  snaps the stored value to a stop; the UI half is `routeRungIndex` / `routeRungLambda`
  ([`RouteOverlay.kt:354`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:354)).
- **The slow-water budget is Best's gate alone**, read as `slowWaterBudgetPct` at the same ranking call — a
  ratio a rung's zone share must stay within to be led on the clock.
- **There is no correction loop**: [`RouteAvoidEngine.kt:120`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:120)
  and its pass KDoc ([`:555`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:555)) both say
  a rung is computed at its own λ and never corrected.

## Why the values move

- **The rule.** `*.properties` is the source of truth for every value and the code follows it; the three λ
  are the one route value that lived in Kotlin, so the file is where they belong.
- **The price of leaving them.** Four sites still describe the dial as the price cursor and the loop as live —
  [`maro.properties:399`](../../app/src/main/assets/maro.properties:399), [`AppConfig.kt:471`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:471),
  [`SettingsManager.kt:153`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:153) and
  [`FEAT_DOC_Route_avoid-algorithm.md:66`](../../xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md:66) — and a
  reader who believes them cannot tell which lever each acquisition turns.

## The keys

| Key | Rung | Shipped | Clamp |
|---|---|---|---|
| `route.avoid.ladder.around.lambda` | `around`, index 0 | 5.0 | 0.0..5.0 |
| `route.avoid.ladder.best.lambda` | `best`, index 1 | 2.5 | 0.0..5.0 |
| `route.avoid.ladder.fast.lambda` | `fast`, index 2 | 0.0 | 0.0..5.0 |

- **They join the existing ladder family**, beside `route.avoid.ladder.collapse.toleranceM`, rather than a new
  `route.ladder.*` prefix: one banner, one family per fact, the file's own grouping kept. *Named here because
  the order's shorthand read `route.ladder.*`; renaming later is three keys and three reads.*
- **The clamp reuses the dial's own ends** — `ROUTE_SLOW_WATER_AVERSION_MIN` / `_MAX` — so 0..5 has one home and
  no second bound pair is minted.
- **The code fallback is the shipped ladder** (5.0 / 2.5 / 0.0), unlike the dial's own 1.0: a missing key must
  not reshape the ladder, and the ranking's thresholds are written around these three values.
- **Read once per arm.** `RoutePreference.lambda` becomes a per-entry getter over its own key, so
  `RouteAvoidEngine`'s `Computation.lambda` and every UI reader keep the shape they have; the read lands at
  rung setup, never per cell.

## The wording, site by site

- **`maro.properties`, the aversion block** — the dial is the *preference*: it names which of the three rungs
  the ranking seats. The `(pace/limit − 1) × K` law, the pre-phase-4 line and the loop's correction sentence go;
  the three λ are named as living in the ladder block above it.
- **`maro.properties`, the ladder block** — the three keys with their own comments, the collapse tolerance
  staying where it is.
- **`maro.properties`, the budget block** — Best's gate: the share a rung's zone time must stay under to be led
  on the clock; the ±20 % band, the two-pass cap and *calibrated to meet* go.
- **`AppConfig`** — the aversion and budget KDocs onto the same fact; the three new accessors with their KDoc.
- **`SettingsManager`** — `routeSlowWaterAversion` and `routeSlowWaterBudgetPct` and their two key constants:
  the dial seats a rung, the budget gates Best.
- **`FEAT_DOC_Route_avoid-algorithm.md`** — §*The λ loop and the budget* and the pipeline line that lists the
  loop among the post-search stages, rewritten onto the shipped model; what still reports a share out of the band
  is read off the code before a word of it is written.
- **`FEAT_DOC_Route_engines.md`** — the ladder section names the keys as the λ values' home; the Known-gaps
  line keeps its two providers and says what each feeds.
- **`FEAT_DSC_Route.md`** — the rule that names the dial and the ladder, and the Docs row for the
  avoid-algorithm doc, checked for the same claim.

## Stated limits

- **The seat thresholds stay code constants** (1.25 / 3.75, the midpoints of the shipped spacing) — the user's
  decision kept them out of the file, so a key edited to uneven values leaves the thresholds assuming 5 / 2.5 / 0.
  The properties comment says so; nothing validates the ordering.
- **No line, no clock and no ranking moves** while the three keys hold their shipped values — the fallbacks and
  the shipped values are the same numbers.

## Verification

- `:app:compileDebugKotlin` green, then the route suites; `RoutePassRankingTest`'s λ assertions
  (0.0 / 2.5 / 5.0) must still pass unchanged, since the file carries those very values.
- A sweep for the retired phrasing — `λ loop`, `±20 %`, `bends around`, `follows the corrected λ` — leaving
  only history files under `xxArchive/`.
