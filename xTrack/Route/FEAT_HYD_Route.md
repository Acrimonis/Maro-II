# Context Hydration — Route — 2026-09-21

**Last Bake:** 2026-09-21 04:40 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last state of this feature — no dependency was added (the harness runs on what the project already ships), no machine-shaped data file was opened by hand (the three baked `.bin` assets are read by test code, never by the session), no device contact was made and no unbacked claim about the code was stated, each citation resting on a file read; work began only on the user's `#impl` order, and no class stopped.

## State

The 2026-09-21 `#implement` pass shipped the plan's **Stage 0 feasibility harness** into `app/src/test/java/ykws/android/maro/route/` — 8 sources and 6 test classes, 30 tests green through `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.route.*"`, and no change to the APK, to an asset or to the dependency list, which is what §13.1 requires of Stage 0. It runs the whole pipeline of §13.3 end to end over the real `nice-menton` region: corridor → standoff-dilated exclusion raster → tangent vertices → visibility edges → cost → turn-aware A* → fillets → speed profile → validator, with every attempt reported as a stated reason rather than a null. Design of record is [`260920_FEAT_PLN_Route_algorithm-design.md`](260920_FEAT_PLN_Route_algorithm-design.md) §10–§13; the plan's Stages 1–2 wait on these numbers, and the mesh epic text in [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) is **pre-plan** — it still names the prebaked navigation mesh as the baseline and a 20 kn cruise default, where the plan supersedes the mesh with the corridor's tangent-vertex graph (§10.6) and moves cruise to a `route.cruiseSpeedKn` property at 25 kn with the lateral ceiling at 0.2 g. Nothing is committed.

## Target Files

- `app/src/test/java/ykws/android/maro/route/RouteGraph.kt` — the visibility prune that dominates runtime (F1)
- `app/src/test/java/ykws/android/maro/route/RouteObstacles.kt` — the zone-hole-as-barrier rule to revisit (F2)
- `app/src/test/java/ykws/android/maro/route/RouteHarness.kt` — asset loading, the derived acceptance pair, both D3 variants
- `app/src/test/java/ykws/android/maro/route/RouteConfig.kt` — the three values' only home until the Stage-1 runtime reads `maro.properties`
- `app/src/test/java/ykws/android/maro/route/RouteExclusion.kt` — the corridor window, the mask, the standoff dilation
- `app/src/test/java/ykws/android/maro/route/RouteSpeedProfile.kt` — fillet geometry, the speed profile, the validator
- `xTrack/Route/260920_FEAT_PLN_Route_algorithm-design.md` — §9, §11.7 and §12.5 hold the decisions Stage 0 was built to answer

## Next Step

Pin the real acceptance pair — `-Dmaro.route.from=lat,lon -Dmaro.route.to=lat,lon`, or the pair the user has in mind — and re-run the harness, because every open decision in §9 waits on an acceptance run that actually returns a path; the derived pair is refused for two different reasons (the hard standoff's endpoint margin, the priced variant's graph connectivity), and only a pinned pair separates an implementation defect from a bad derivation.
