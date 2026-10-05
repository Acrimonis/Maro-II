# Context Hydration — Route — 2026-10-05

**Last Bake:** 2026-10-05 18:23 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-05 16:35 UTC) the three changes all ran on orders — Phase 1
on *order the mark count's Phase 1 in Code — go!*, Phase 2 on the `#implement` pipeline the user invoked, and
the review's fixes on *fix identified issues*. No dependency was added, no machine-shaped data file was
opened, no work started without an order, the device was not touched by the agent, and every claim about the
code follows a file read. No device pass was taken, and none is owed on Phases 1–2 (R97) — it was the user's
own pass on the routes created that settled the bound.

## State

**The mark count's Phase 1 (the lattice) and Phase 2 (the memo) both landed on 2026-10-05.**
[`MarkLattice`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:73) stands a walk's
marks at `anchor + (j + 0.5) × step` with the last interval sized to the chord's remainder, and
[`MarkMemo`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:116) — made per walk at
[`pull()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:118), keyed on the mark's own
point and bound to its field so a rebuild wipes it — pays each mark's distance read and price read once, shared
by [`legClearCause()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:264) and
[`softPriceSec()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:375) while the span
proof's own read is untouched. The counting fixture reads **270 against the memo-less 410** with the pulled
line identical, the one-mark-twice fixture asserts identical doubles and a single read, `apk-build.bat` is
green and the suite stands at **923 / 1 / 11**, the single red the parked `route.avoid.fine.cellRatio` test.

**The bound the memo rests on, and who owns it.** The lattice can move a chord's price by at most **one
interval's price** (`price × stepM`, 25 m at the shipped margin), and exactly zero where a boundary lands on an
interval edge; **the user accepted it on their word of 2026-10-05**, their own pass having found no difference
in the routes created, and the three recorded routes in [`route-phase8.txt`](route-phase8.txt:1) are unchanged.

**The review is discharged.** The `#implement` Ask hop returned **no blocker** and six should-fixes; all six and
a stale KDoc claim were fixed on the user's order — the shared-stretch read is now proven, the count is
asserted rather than prose, the unproved stretch is pinned point for point, `MarkMemo` and `pull` are
`internal`, the memo can no longer answer another field, and the guard's claim reads *one step and one
grouping rule, not one partition*. Deliberately left undone: the memo's eight-signature threading refactor.

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `MarkLattice`, `MarkMemo`, `readHard`, `readPrice`, `softPriceSec`, `spanPriceSec`, `groupPriceSec`, `legClearCause`, `pull`
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt` — the lattice and memo fixtures, the counting fixture, the bound, coverage and position pins
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPullSamplingTest.kt` — the coarse marks' fixed-lattice fixture
- `xTrack/Route/261005_FEAT_PLN_Route_mark-count.md` — Phases 1–2 landed with the review folded; Phase 3 open
- `xTrack/Route/261005_FEAT_PLN_Route_chord-price-proof.md` — Phase 1 landed, Phase 2 closed unread
- `xTrack/Route/261004_FEAT_PLN_Route_price-walk-reads.md` — the one owed code-side item: the `runPass` seam test

## Next Step
**The mark count's Phase 3 reading is all that is left on this line — the one device pass the feature keeps,
and it is the user's own** — `priceReads` and `priceMs` on the phase-8 pairs for both engines, with the drawn
line and the clock as the check that nothing moved; the code-side `runPass` seam test is the only other open
item here, and no further pass is owed.
