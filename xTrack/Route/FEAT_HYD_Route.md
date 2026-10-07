# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 18:34 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-07 17:55 UTC) the work ran on the user's own words — a design review that corrected the third engine's mechanism against the code and then settled its four open points. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read.

## State

**The third engine is designed and decision-complete.** [`261007_FEAT_PLN_Route_selective-engine.md`](261007_FEAT_PLN_Route_selective-engine.md) is the outcome of the algorithm review's point 4 — `selective`, a third plan over the shared pipeline that spends fine detail only on the collars where decisions happen (the shoreline, the band's outer boundary, the zone rims, the shallow wall) and prices a per-metre depth gradient over a 25 m band beside the wall. The review corrected four mechanism claims against the code: the fine-water cut is the builder's, not the plan's, so `fineWater(...)` is added and [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:167) migrated; `evolutive` is a real engine class, so `selective` takes a `RouteSelectiveEngine` in its shape; the depth price needs a λ-free grid coefficient plus an A\* read-time scaling and the `withDepthBand` guard door; and the fine-window band-mask would erase the collars. The open points are settled: collar widths one key each at 100 m, the gradient riding the fine layer, their own `route.selective.*` keys, French `Sélective`, and not the default.

**Nothing shipped — the plan is in design.** No source file changed this session; the code's present state is [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) and the engines as they stand are [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md).

## Target Files

- `xTrack/Route/261007_FEAT_PLN_Route_selective-engine.md` — the third engine's design, corrected against the code and decision-complete
- `xTrack/Route/FEAT_DSC_Route.md` — the selective plan attached to `## Docs`, the point-1 and point-4 todos repointed
- `xTrack/GLOBAL_CONTEXT.md` — this bake's summary row and focus entry

## Next Step

Implement `selective` from the plan, starting at Phase 1 — move the fine-water cut onto the plan seam as `fineWater(...)`, with `evolutive`'s windows proved byte-identical.
