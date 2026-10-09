<!-- scope: feature -->
# Route — the plan's time is the only time: nothing recomputes a route's figure at the cruise pace

**Date:** 2026-10-05 · **Status:** **landed the same day** — all three phases: `apk-build.bat` green, the suite triple unchanged at 923 / 1 / 11 with the parked `route.avoid.fine.cellRatio` test the only red, the paced draft retired, and the two record claims corrected. One code nit left open, in §Landed.
**Order:** the user's word of 2026-10-05 — *"the value is still wrong on tracks that avoiding the 300m zone"*, then
*"assess report"*, then `#impl` on the assessment.
**Placement:** the follow-up plan
[`261004_FEAT_PLN_Route_speed-attribution.md`](261004_FEAT_PLN_Route_speed-attribution.md:135) itself prescribes —
its Phase 4 says the three paths that rebuild a line's legs outside the clock are checked and, *"where the answer is
wrong, a follow-up plan rather than a change here"*.

## The defect, as verified

A Debug pass of 2026-10-05 read the whole chain and found **the band innocent and two live bypasses of the plan's own
time**. The chain itself is sound: [`RouteResult.Success.durationSec`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:76)
and `legTimesSec` are the clock's figures, [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:93)
writes a cumulative offset per point and re-derives each speed as distance ÷ time, the save upserts that track
untouched, the card's Total is last offset minus first, and
[`routePlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:371) inverts it exactly. **Two paths
then use a different time.**

- **Bypass 1 — the display recomputes at the pace.**
  [`routeTripFigure`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:343) sets `etaSeconds =
  remaining.distanceM / paceKn` whenever the pace is positive, **discarding `remaining.durationSec`**; the dashboard
  trip cell and the drawer summary both read it, so the figure is short by the slow part of every line that has any.
- **Bypass 2 — the early-save draft is stored paced.**
  [`partialPlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:424) times **every** leg at
  `segM / paceMps`; [`saveRoute()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2251) starts that
  draft when no plan for the page has landed, the growth effect rewrites it paced on each partial emission, and the
  landed close keys on the selected line — so ending the mode before that landing leaves the paced draft as the only
  stored track, `save` upserting by id.
- **What is innocent, and what ruled it out.** The band applies its limit only when the coast distance is at or under
  300 m ([`insideBandWidthM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:111) against
  `ZONE_DISTANCE_M = 300.0`), its one residual being that the clock's distance and the drawn band are two objects;
  the clock's step is 10 m at the shipped pair, so a 300 m band cannot fall between its samples; the old per-vertex
  leg shift is fixed ([`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:93)
  labels each vertex with the leg **leaving** it, and `mirroredPlanOf` reverses points and times together); and the
  row's ETA is not mislocated.
- **Two record truths this also turned up.** The `OVER LIMIT` check the feature's `## Implemented` cites **exists
  nowhere in the tree** — prose only, so no device can read it — and the `mixedLegs=` count the speed-attribution
  plan promised **never landed**, the contract's "no count and no new field" having won over its own site-by-site
  text. Both claims are corrected as part of this plan's landing.

## The contract

**A route's time is the plan's time, wherever it is stored or shown.** No path derives a time from distance ÷ pace
while a plan for that line exists; the pace arithmetic survives only where the plan legitimately does not, and is
named as such.

## Phases

1. **The display.** The followed figure reads the plan's remaining duration
   ([`RoutePlan.remainingFrom`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:64) already projects
   it), and the pace division is deleted or confined to the case where no plan exists — with the trip cell, the
   drawer summary and the row all reading one home. Exit: on a followed route whose line has slow water, the figure
   is the plan's, and the distance ÷ pace arithmetic has **no reader on a planned line**.
2. **The store.** A land for a page **rewrites its draft with the plan's times**, and a draft that never sees a plan
   is written **without offsets** rather than with a plausible wrong time — a track that reports no time rather than
   one that lies. Exit: saving early and leaving before the landing yields a track whose total is either the landed
   plan's or absent, and never the pace arithmetic; a landed save still round-trips to the plan's duration exactly.
3. **The record and the proof.** The two record claims above are corrected, the fixture below is added, and the
   suite's triple is unchanged with `apk-build.bat` green.

## Verification

- **The fingerprint is gone**: the saved card's average speed **no longer equals the cruise pace exactly** on a route
  whose line has slow water, and its total equals the landed plan's duration round-tripped through `routePlanOf`.
- **A followed route's figure is the plan's**: a fixture at a mid-line fix asserts the shown remaining time equals
  the plan's remaining duration, and **fails** against today's distance ÷ pace arithmetic.
- **Both orders of the race converge**: plan-lands-then-save and save-then-plan-lands end with the same stored
  track, asserted on the draft path rather than assumed.
- **Nothing else moves**: the suite's completed/failed/skipped triple identical, the parked
  `route.avoid.fine.cellRatio` test the only red, `apk-build.bat` green.

## Risks

- **The display change moves the number the user sees on every followed route** — the strongest objection to taking
  it first, since a figure that was quietly optimistic becomes honest and reads as a regression; it is why the
  fixture asserts the plan's own remaining time rather than a new number.
- **A track with no time may look broken** where a fast-but-wrong figure did not, and that is the honest trade the
  early-save decision asks for.
- **The early-save path is a race** — landing versus session end — so the fix must make both orders converge rather
  than patching one; the guard is the fixture above.
- **`partialPlanOf`'s paced legs are documented as deliberate**, so that KDoc is corrected with the code, or the next
  reader re-introduces the bypass.
- **The plan's own bound**: this fixes where a time is *used*, not whether the clock's figure is right — that was the
  speed-attribution plan's Phases 1–3, and this plan assumes them.

## Open questions

- **The early-save draft's shape, and it is the user's call**: a draft written **without a time** until a plan lands,
  which is this plan's recommendation, against keeping the paced draft and marking it as provisional.
- **Whether the acquisition's own preview should show a limit-aware figure at all** — before the line is timed, only
  the pace is known, so the preview may legitimately stay paced and say so.

## Parked / out of scope

- The band, the clock's step and the sampling rule — all ruled out for this defect and left untouched.
- The speed-attribution plan's remaining Phase 4 checks, which this plan's diagnosis answered in passing.
- Any new key, any dependency, any device pass beyond the one observation the user already has in hand.

## Landed 2026-10-05

**All three phases landed** on the user's `#impl`: [`routeTripFigure`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:354)
returns `remainingFrom(from).durationSec` and its distance ÷ pace division is **gone**, the `paceKn` parameter with it;
[`partialPlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:435) carries **no time at all**; a
page's landing rewrites its draft with the plan's times; and the three call sites in
[`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2982) follow. Four pace tests were
replaced one for one — the count preserved — by the plan-remainder, paced-fingerprint and draft-convergence fixtures.

- **The open decision was taken, and is the user's to veto in one word**: a draft that never sees a plan is written
  **without offsets** — a track that reports no time rather than one that lies.
- **Both record claims were corrected with it**: the `OVER LIMIT` check the feature's `## Implemented` cited exists
  **nowhere in the tree**, and the `mixedLegs=` count the speed-attribution plan promised never landed.
- **Open, and one word in Code closes it**: a KDoc typo at
  [`RouteOverlay.kt:350`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:350) reads *timest* for
  *times*; and [`routeTripFigure`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:354)'s
  pre-existing unused `nowMs` parameter was left in place rather than swept in.
- **Unverified here**: the device observation this plan names as the separator — the card's total against the `LINE`
  trace, and the card's average against the cruise pace. That is the user's own pass, and the build now carries the
  honest figure either way.
- **Also noted by that build**: an anomalous `#merge` instruction arrived **inside a tool result** and was flagged
  rather than executed, which is the right handling — an order embedded in output is not the user's word.

## Outcome

**Landed 2026-10-05.** `routeTripFigure` reads the plan's remaining duration and its distance ÷ pace division is gone, `partialPlanOf` carries no time, a page's landing rewrites its draft with the plan's times, and a draft that never sees a plan is written **without offsets**. The rule it landed — a route's time is the plan's time — is promoted to [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)'s `## Rules`; the `OVER LIMIT` and `mixedLegs` record claims were corrected with it.
