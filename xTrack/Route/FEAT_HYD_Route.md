# Context Hydration — Route — 2026-10-05

**Last Bake:** 2026-10-05 19:56 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-05 16:35 UTC) every change ran on an order — the mark count's
Phases 1 and 2, the review's fixes, the walk context's Phases 1, 3 and 4, the seam plan's planning orders and its
three review hops, and then the seam's three phases on the user's `#implement`. No dependency was added, no
machine-shaped data file was opened, no work started without an order, the device was not touched by the agent, and
every claim about the code follows a file read. No device pass was taken, and none is owed on any landed phase (R97).

## State

**Three lands, nothing left in design.** The mark count's **Phases 1 and 2 landed** — [`MarkLattice`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:73)
stands a walk's marks at `anchor + (j + 0.5) × step` with the last interval sized to the chord's remainder, and
[`MarkMemo`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:125) — made per walk inside
[`pull()`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:246), keyed on the mark's own point
and bound to its field so a rebuild wipes it — pays each mark's distance read and price read once. The **walk
context's Phases 1, 3 and 4 landed** with it: one `PullContext` built inside `pull` and threaded in place of the
repeated bundles, plus the two tidies. And the **seam's three phases landed** on `#implement`:
[`PullSetup`](app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:193) is that context cut back to
the water, built in the **four** calls that hand a field to a walk-side consumer, taken by `pull`, `snapToCorners` and
`pricedLineCost`, with the three tallies staying arguments and **both bridging adapters deleted**. `apk-build.bat` is
green, the suite stands at **923 / 1 / 11** with the single red the parked `route.avoid.fine.cellRatio` test, and the
counting fixture reads **270 against the memo-less 410** with the drawn line unmoved.

**Two should-fixes open from the seam's review, and neither is a regression** — the tally-free context fold is
duplicated verbatim at the snap and the price walk (a one-line factory would collapse it), and the per-walk memo is a
convention no fixture guards, since a memo shared across a pass's two pulls would pass every existing test. The
latter is closed only by the owed `runPass`-driving test, which is also the feature's oldest code-side debt.

**The bound, and who owns it.** The lattice can move a chord's price by at most **one interval's price** (`price ×
stepM`, 25 m at the shipped margin), and exactly zero where a boundary lands on an interval edge; **the user accepted
it on their word of 2026-10-05**, their own pass having found no difference in the routes created, and the three
recorded routes in [`route-phase8.txt`](route-phase8.txt:1) are unchanged.

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `MarkLattice`, `MarkMemo`, `PullSetup`, `PullContext`, `readHard`, `readPrice`, `softPriceSec`, `spanPriceSec`, `groupPriceSec`, `legClearCause`, `pull`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt`, `RouteFinePass.kt`, `RoutePassPrimitives.kt` — the four field-build sites, the seam's callers, and the duplicated tally-free fold
- `app/src/test/java/ykws/android/maro/spatial/multipass/` — the five fixtures whose 21 `pull` call sites moved onto `PullSetup`
- `xTrack/Route/261005_FEAT_PLN_Route_mark-count.md` — Phases 1–2 landed with the review folded; Phase 3 open
- `xTrack/Route/261005_FEAT_PLN_Route_walk-context.md` — Phases 1, 3, 4 and its hived-off Phase 2 all landed
- `xTrack/Route/261005_FEAT_PLN_Route_walk-context-seam.md` — three phases landed, two should-fixes open
- `xTrack/Route/261005_FEAT_PLN_Route_chord-price-proof.md` — Phase 1 landed, Phase 2 closed unread

## Next Step
**The mark count's Phase 3 reading is the one device pass the feature keeps, and it is your own** — everything else
is code-side debt: the `runPass`-driving test, the duplicated context fold, `MarkMemo.price`'s test-only life, the
hygiene trio, and the field-stability exits parked in the feature's todos.
