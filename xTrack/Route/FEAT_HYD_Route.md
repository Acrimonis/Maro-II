# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 21:01 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-07 20:56 UTC) the session ran on the user's own words — the seventeen-fix batch was committed under an explicit order, and a performance review of the acquisition was carried out and parked. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read.

## State

**`selective` has landed, the seventeen-fix batch is reviewed and committed, and the acquisition's slowness is parked.** The third engine is `RouteSelectiveEngine` over the shared pipeline — four fine collars (the shoreline, the band's outer boundary, the zone rims, the shallow wall) and a per-metre depth price over a 25 m band beside the gate's wall, its own `route.selective.*` keys, `fineWater(...)` on the plan seam, and `SelectiveMaskCache` holding the pre-snap law keyed on the band width and the four collar widths. The two-layer walk reads the fine cell over its coarse copy (any layer ratio, an even one settled by the `floor` convention), so `evolutive`'s ribbon and `selective`'s collars are fine-only water while `avoid` is untouched.

**The batch is committed as `6feac66e`** on `feature/route-algo-ya-more` (38 files; root artefacts excluded; not pushed). The independent review returned **no blocker and four should-fixes** — the band-time KDoc's constant-speed premise is wrong for the profile input; `MarkMemo`'s safety is misattributed to the world's window rather than its field-identity binding; `FEAT_DOC_Route_engines.md` still states the old mask-cache key; and the memo fixture never drives `runPass`. Their plan is [`261007_FEAT_PLN_Route_review-should-fixes.md`](261007_FEAT_PLN_Route_review-should-fixes.md) — **in design, nothing of it implemented**.

**The acquisition's slowness is parked, unmeasured.** One arming runs three complete passes (the ladder's Fast · Best · Fun), each re-running the A\*, pull, fine pass and clock with only the grid shared; the search box is grown `route.avoid.corridor.reachM` (3704 m) on every side and doubles on a failed first pass, and the pull's coast-distance reads are the priciest single step. The engine's timings print only on-device under the `MaroRoute` tag, so the leading stage must be read with `adb logcat -s MaroRoute` before a lever is chosen. The suite reads **961 / 2 / 11**, the two reds the pre-existing `TrackOutlineTest` asset-and-default disagreement.

## Target Files

- `xTrack/Route/FEAT_DSC_Route.md` — the state doc, the parked perf todo added
- `xTrack/GLOBAL_CONTEXT.md` — this bake's focus entry
- `xTrack/Route/261007_FEAT_PLN_Route_review-should-fixes.md` — the seven should-fixes, in design

## Next Step

Implement the seven should-fixes from [`261007_FEAT_PLN_Route_review-should-fixes.md`](261007_FEAT_PLN_Route_review-should-fixes.md); the acquisition's performance waits on the on-device `MaroRoute` trace.
