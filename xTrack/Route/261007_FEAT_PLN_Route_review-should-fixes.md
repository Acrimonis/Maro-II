<!-- scope: feature -->
# Route — the seventeen-fix review's should-fixes

**Date:** 2026-10-07 · **Status:** planned — the review of the seventeen-fix batch returned **no blocker and
four should-fixes** plus two record/hygiene should-fixes. None is blocking; this plan clears them.
**Order:** the user's word of 2026-10-07 — *"continue. fix the should fix"*.

## Scope

Seven corrections, no new key, no new dependency, no behaviour change to a shipped answer. Only the
fixtures gain tests; every other change is KDoc, dead-member removal or record text. The band-time
arithmetic, the cache key and the depth law are **not** touched — each was judged sound.

## The seven

- **SF1 — the band-time KDoc's constant-speed premise is wrong for its input.** [`slowTimeByLimit`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:256)
  is called by the engine with a **profile** line ([`RouteAvoidEngine.kt:427`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:427)), whose
  legs ramp, so charging `legSeconds × insideFraction` is a **length-proportional approximation** of the
  time inside, not the exact split the comment claims ("a leg at a constant speed spends its time in the
  same proportion"). Correct the KDoc on [`slowTimeByLimit`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:256) and [`insideSeconds`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:304)
  to state the approximation and its bound (exact for a constant-speed/regime-pure leg, approximate under
  the profile). Fix the stale class KDoc at [`SlowTimeByLimitTest.kt:11`](../../app/src/test/java/ykws/android/maro/spatial/multipass/SlowTimeByLimitTest.kt:11),
  which still says a leg is charged its full seconds.
- **SF1b — the one behaviour the change introduced has no fixture.** Every fixture in `SlowTimeByLimitTest`
  uses step-aligned predicates, so each leg is wholly inside or outside and `insideSeconds` reduces to
  full-or-none. Add a fixture with a predicate that **cuts a leg mid-span** and assert the entry's seconds
  is strictly between 0 and the leg's own seconds (and equals `legSeconds × insideFraction` at the
  bisected crossing). Depends on SF1.
- **SF2 — `MarkMemo`'s safety is misattributed to the world's window.** [`MultipassWorld.kt:104`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:104)
  states the mutation window as the reason the memo "needs no invalidation within a solve"; the memo is in
  fact safe because it **binds to its field's identity** and wipes on a rebuild
  ([`MultipassPull.kt:192`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:192)). Rewrite the window KDoc to say so, and to name the
  window's **real scope and limits**: it covers the grid swap ([`DepthRepository.kt:39`](../../app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt:39))
  and the index rebuild ([`CoastlineRepository.kt:56`](../../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:56)), while the world's
  depth answer also moves with the live EMODnet cutoff ([`MultipassWorld.kt:176`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:176)) and its
  zone answer with the live zone list ([`MultipassWorld.kt:181`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:181)) — neither of which the
  window claims to cover. Also state that the window is a **documented contract, not a code-enforced
  invariant** (no lock exists), as the honest form of "not silently tolerated". Depends on SF3.
- **SF3 — two dead stamps under a KDoc claim.** [`coastlineGenerationStamp`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:42) and
  [`depthGenerationStamp`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:48) have no reader (the mask cache dropped them; [`RasterCache`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:65)
  keys on `metadata.fetchTimestampMs` directly), so the KDoc's "the two stamps are what tell two solves
  apart" is unbacked. Delete both interface members and their `LiveMultipassWorld` overrides, and remove
  the sentence naming them. **Decision:** delete rather than wire — re-keying the mask cache on the stamps
  would undo the deliberate honest-key fix of the same day.
- **SF4 — the engines doc still states the old mask cache key.** [`FEAT_DOC_Route_engines.md:131`](../../xTrack/Route/FEAT_DOC_Route_engines.md:131)
  says the cache is keyed "on the depth grid's timestamp, the coastline's stamp, the EMODnet cutoff, the
  switches and the four widths", which the code ([`RouteGridPlan.kt:161`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:161)) and the plan's Outcome now
  contradict. Rewrite that sentence to the honest key — the band's width and the four `route.selective.*`
  collar widths — and keep "the per-arm snap-and-merge is the cheap half".
- **SF5 — `FineWaterQuery`'s unused harvest fields and a circular justification.** Nothing reads
  `box`, `edges` or `openCoast` (only the fixture constructs them), yet the feature todo says they are kept
  "because the `RouteSelectivePlanTest` fixture constructs them". Correct [`FineWaterQuery`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:46)'s KDoc to
  say the three are offered to a plan that may read the coast beside its widths and that **no shipped plan
  reads them yet** (the seam's shape, not a fixture accommodation), and correct the feature todo's reason
  in [`FEAT_DSC_Route.md:148`](../../xTrack/Route/FEAT_DSC_Route.md:148). **Decision:** keep the fields — the query is the
  seam's input and a third engine may need the coast; only the reason is wrong.
- **SF6 — the memo fixture is hollow where it matters.** [`eachPullGetsItsOwnMemoSoTwoWalksReadAlike`](../../app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:799)
  catches a **shared** memo but passes if the memo is removed (`memo = null`), and it never drives
  `runPass`, so the seam plan's own named blind spot — "a memo shared across a pass's two pulls"
  ([`261005_FEAT_PLN_Route_walk-context-seam.md:73`](../../xTrack/Route/261005_FEAT_PLN_Route_walk-context-seam.md:73)) — stays open. Strengthen it: keep the
  equal-tally assertion, and add the **counterfactual** — a third pull that explicitly passes the first
  walk's memo and shows strictly fewer `priceReads` — so the fixture's discriminating power is proven, not
  assumed. Then correct the record where it overclaims that the fixture closes the blind spot
  ([`FEAT_DSC_Route.md:157`](../../xTrack/Route/FEAT_DSC_Route.md:157), the seam plan's §Closed), naming the runPass path as
  still convention-only, or drive `runPass` if a clean assertion is found.
- **SF7 — the ribbon-mask fixture's name overclaims.** [`theWindowMaskReproducesEvolutivesCoastRibbon`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteSelectivePlanTest.kt:350)
  is a two-point spot check, not the byte-identity its name implies (the byte-identity lives in
  `thePlansAnswerReproducesTheWindowCutByteForByte`). Either rename it to what it checks (a
  ribbon-membership check) or add the ribbon-edge pair so it pins both sides of `marginM + baseCellM`.

## Verification

- `apk-build.bat` green and the unit suite with no new red; the one new fixture (SF1b) and the changed
  fixtures (SF6, SF7) name themselves in the report.
- Every KDoc change is read back against the code it describes; no claim left that a reader must reverse.
- No new key, no new dependency, no change to the band-time arithmetic, the depth law or the mask cache key.

## Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — SF1
- `app/src/test/java/ykws/android/maro/spatial/multipass/SlowTimeByLimitTest.kt` — SF1, SF1b
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt` — SF2, SF3
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt` — SF6
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteSelectivePlanTest.kt` — SF7
- `xTrack/Route/FEAT_DOC_Route_engines.md` — SF4
- `xTrack/Route/FEAT_DSC_Route.md` — SF5, SF6
- `xTrack/Route/261005_FEAT_PLN_Route_walk-context-seam.md` — SF6
