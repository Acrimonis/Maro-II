# Context Hydration — Route — 2026-10-05

**Last Bake:** 2026-10-05 11:30 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-05 10:19 UTC) every change ran on an order — the span
proof on *Start with the span proof, hand to Code*, and the questions that followed (the device
expectations) were answered, no file touched. No dependency was added, no machine-shaped data file was
opened, no work started without an order, the device was not touched by the agent, and every claim about
the code follows a file read. Phase 2's device pass is the user's own and has not run.

## State

**The span-level price proof's Phase 1 landed.** [`softPriceSec()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:330)
now reads the price as a recursion over the chord's own fine intervals: one clearance read at a span's
midpoint proves every interval where the declaration reaches the span's half-length, an unproved span
splits in half and each half is tested, and a span at or under `2 × priceStepM` falls to the group walk
[`groupPriceSec()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:405) still
walks. The sum is one in-order accumulator, so a proved span is **bit-identical** to the fine sum — a
`left + right` split re-associates the additions and moves the last bits — and the proof is skipped where
the price step cannot group, so the fine-step walk reads exactly as before. `apk-build.bat` is green;
[`AvoidPriceWalkTest`](app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:30) is
15/15 with four new fixtures and one read-count assertion retargeted from the group walk to the span
proof; the suite reads 908 / 1 / 10, the red the parked `route.avoid.fine.cellRatio` value test.

**What is owed on it, and in what order.** (1) **the reading** — its own
[Phase 2](261005_FEAT_PLN_Route_chord-price-proof.md:94): `priceReads` and `priceMs` on the `PULL` and
`FINAL` lines for both engines against **5 048 reads at 3.5 km and 87 176 at 13.3 km**, with `LINE
distance` and `duration` the check nothing moved; the user's own device pass. (2) the plan's own record.
Beside it, 4b's two owed items stay: a test driving `runPass` itself, and the saving's capture.

**The order after that.** [`261005_FEAT_PLN_Route_mark-count.md`](261005_FEAT_PLN_Route_mark-count.md)
— **the mark count, in design, second**: its marks must move onto a step lattice before a memo of their
reads can hit, so its lattice's error bound is the user's word before the memo is built.

**The walk stays closed by decision** — items 14 and 15 parked, `avoid`'s parked ratio test the user's
own, and both plans stay in design (or partial) until their pointers reach the epic's `## Implemented`.

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `softPriceSec`, `spanPriceSec` and `groupPriceSec`: the walk the span proof changed
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt` — the four new span-proof fixtures, the retargeted read-count test, and 4b's own five
- `xTrack/Route/261005_FEAT_PLN_Route_chord-price-proof.md` — Phase 1 landed, Phase 2 (the reading) owed
- `xTrack/Route/261005_FEAT_PLN_Route_mark-count.md` — in design, the mark count next
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — `priceStepFor`, the trace lines Phase 2 reads
- `xTrack/Route/261004_FEAT_PLN_Route_price-walk-reads.md` — the landed cut, its parked mark count, and 4b's two owed items

## Next Step
**The span proof's Phase 2 reading is the next action, and it is the user's own device pass** — the
`priceReads` and `priceMs` on the phase-8 pairs, both engines, against the record's baselines, with the
unmoved line as the control. After it: the plan's record, then the mark count's lattice and its error
bound.
