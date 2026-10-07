<!-- scope: feature -->
# Route — the fine-only walk's price step

**Date:** 2026-10-06 · **Status:** **parked** (2026-10-06) — Phase 1 landed and its subject walk then proved
**unreachable**: the address it fixes returns the incumbent line before the setup Phase 1 changed is built,
because the second pass is retired for both engines ([`UniformGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:90) and
[`EvolutiveGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:141) each answer `emptyList()`, so [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:233) never reaches
line 244).
**Resume:** the fine walk that does run is [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:71), which still prices at its own fine cell under
a comment asserting the opposite rule — so this change belongs there, or nowhere, and `finePass`'s two pulls
owe a `PullTiming` before any reading can name it.
**Order:** the user's word of 2026-10-06, after the mark count's Phase 3 reading was reviewed from
[`route-phase9.txt`](../../route-phase9.txt:1).

**Origin:** the Phase 3 section of [`261005_FEAT_PLN_Route_mark-count.md`](261005_FEAT_PLN_Route_mark-count.md:144),
whose instrument is what named this address.

## Why — the reading named the address

- **The counters split a pull three ways**, and the split holds on every line: `marks` = the reads actually
  paid + the memo's hits + the intervals a single read already covered — a group's own read, or a span proof's.
- **Where `priceStepM` is the walk's 100 m interior cell, that third term is 47–88 % of the marks** — 1 196 of
  2 544 ([`route-phase9.txt`](route-phase9.txt:61)), 2 554 of 4 672 ([`route-phase9.txt`](route-phase9.txt:267)),
  810 of 1 714 ([`route-phase9.txt`](route-phase9.txt:212)), 8 296 of 9 421 ([`route-phase9.txt`](route-phase9.txt:678)),
  12 513 of 14 804 ([`route-phase9.txt`](route-phase9.txt:632)).
- **Where `priceStepM` is the walk's own fine cell, that term is exactly zero** — 7 327 marks against 7 305 paid
  reads ([`route-phase9.txt`](route-phase9.txt:75)), 17 395 and 17 280 ([`route-phase9.txt`](route-phase9.txt:92)),
  62 090 and 62 009 ([`route-phase9.txt`](route-phase9.txt:657)), 38 652 and 38 413 ([`route-phase9.txt`](route-phase9.txt:709)).
- **Zero is the signature of a partition that cannot group.** The group walk pays `k − 2` a group of `k`, so a
  price step at or under one sampling interval leaves `k = 1` and every mark pays its own read; the fine cells
  are 20 m on `evolutive` and 33.3 m on `avoid`, both under the 25 m interval the walk samples at.
- **The site is named, and the comment standing on it states the rule the reading refutes** —
  [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:243) builds
  `PullSetup(marginM, fineCellM, fineCellM, …)` under *this pass walks one fine grid, so its price step is that
  grid's own cell*, and [`fineWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:305)
  builds its `GridWalk` with `fineCellM`, which [`priceStepFor()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:166)
  answers straight back as the price step.
- **The interior cell is already in the function's own hands** — `fineReSearch` reads `ctx.cellM` three lines
  above the setup it builds, so nothing has to be plumbed in from outside.
- **This is Phase 4b's collapse one level down**: the two-layer walk was given its interior cell as the price
  step, and the fine-only walk, having no interior cell of its own, kept the fine one.
- **And the price half is still where the pull's time sits** — 67–80 % of it across the arms, with the largest
  absolute figures belonging to the fine walks themselves.

## What changes

- **A fine-only walk's price step becomes the interior cell; its clearance step stays the fine cell.** The
  `PullSetup` at `fineReSearch` takes `cellM` as its price step, so the line costs it prices group at the coarse
  interval while the walk's own clearance reads stay at the fine one.
- **The walk states its price step rather than having it inferred.** `GridWalk` gains a named price step
  defaulting to `cellM`, and `priceStepFor` answers that field: the main walk needs no change, its cell already
  being the interior one, while the fine walk passes the interior cell and keeps `cellM` as the fine one.
- **The sampling step is untouched, and so is every mark.** The lattice stands on the fine sampling step, and
  the price step governs **grouping alone** — no mark moves, no chord verdict reads it, and the guard's two
  sides keep the one step they already share.
- **Nothing the user can see can move, by construction.** A proved group's product is identically the fine sum,
  the price error Phase 4b proved **zero**, so the line, its distance and its clock cannot change: this is the
  class that needs no bound accepted and no `Δ price = 0` re-argued.
- **One invariant becomes a line and a test** — the price step is never under one sampling interval, and never
  under the walk's own cell: a walk that breaks it has silently switched its price machinery off.

## Phases

1. **The step.** `GridWalk`'s price step, `priceStepFor` reading it, the fine pass's setup built with the
   interior cell, and both fixtures below. Exit: `apk-build.bat` green, the two fixtures green, no verdict
   assertion moved.
2. **The pass named on the line** — the pull's trace carries which pass it belongs to (main, candidate, fine
   research), because the capture could tell them apart only by `stepM == priceStepM`. Exit: a fixture-grade run
   separates them, at no per-chord cost.
3. **The reading.** The same arms as the phase-9 pass, both engines: the fine walks' `priceReads`, `marks`,
   `memoPriceHits` and `priceMs`, and the share of the pull they hold. The acceptance half is the same drawn
   line — `distance`, `duration`, the legs — which is what keeps the device pass a measurement.

## Verification

- **A fine-only walk prices at the interior cell**: a fixture whose walk's cell is the fine one asserts
  `priceStepFor` answers the interior cell, never the walk's own.
- **The group forms and the reads fall with the sum identical**: the counting fixture read at the fine step and
  at the interior step, the same price double to the bit and strictly fewer reads.
- **No verdict moves**: every existing fixture's refusals, pulled points and guard answers unchanged, and
  `LINE distance` and `duration` unmoved against the record.
- **The suite is green with the new fixtures and the same three reds**, and `apk-build.bat` green — the code
  side's whole acceptance.
- **The device reading is the measurement alone**, and it is the user's own.

## Risks

- **The win is unmeasured on a real pair until Phase 3** — the capture shows the fine walks' reads, not their
  share of a typical arm's wall time, which is exactly what Phase 2's line exists to answer.
- **A wider step is not a coarser line**: the group proof's declaration is metric and its error is zero by
  construction, so a wider group cannot inherit the fine cell's verdicts — a fixture that moves is a finding,
  never a tolerance to widen.
- **The memo's value on these walks is unsettled** — 1 812 hits of 3 866 marks on one arm
  ([`route-phase9.txt`](route-phase9.txt:330)) against 115 of 17 395 on another, so the step and the memo must be
  read apart in Phase 3 rather than summed.

## Open questions

- Whether `avoid`'s fine pass wants the engine's interior cell or one of its own, its fine cell being the
  corridor's 33.3 m already.
- Whether the memo should stay armed on the fine walk's price-only reads, given how unevenly it pays there.

## Parked / out of scope

- **The aligned shared grid** — the price-walk plan's Phase 5, which needs the user's word for its error; this
  change may take enough off the fine walks that the error is no longer worth accepting.
- Any change to the sampling step, the lattice or the marks: this plan moves the grouping alone.
