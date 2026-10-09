<!-- scope: feature -->
# Route — the stored-route check leaves

**Date:** 2026-10-06 · **Status:** in design · **Order:** the user's word of 2026-10-06 — *Let's get rid of this
functionality, req and code*, then, correcting an interim reading, *do not check the stored routes, always rearm
an acquisition.* **The file keeps the name it was created under**, and the reading of this order as a *cut to the
plain case* is **withdrawn here** so no reader takes it for the current intent.

## Outcome

**Landed 2026-10-06 as written.** The stored-route check is gone: `storedRouteMatch`, `StoredRouteHit`,
`newestRouteBetween` and `mirroredPlanOf` deleted from `RouteOverlay`, `StoredRouteMatch` and `arm`'s
`storedMatch` branch from `RouteViewModel`, and `armRouteMode`'s summaries lookup, both match branches and the
match-promising comments from `MapScreen`. R83 to R87 are struck in place with the numbering kept, the two
fixtures are retired with `PerPointSpeedInvariantTest`'s mirrored case, and one new case,
`anArmingAlwaysAcquiresAndTakesNoStoredLine`, pins the rule that replaced them. **Deviations**: R82's persisted
end ids and the fan's `Route auto` child were left standing exactly as the plan asked, and the plan's own
prediction was confirmed — the retained per-door fresh flag is now a no-op, raised rather than removed.

## The rule that replaces the machinery

**Every arming runs a fresh acquisition.** No door reads the stored routes, nothing matches a saved line, and
nothing reuses one.

## What goes

- **R83, R84, R85, R86 and R87** in [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md:126) — each struck with one line
  naming why; the numbering is ordinal and cited by the code, so nothing is renumbered and the gap is the record.
- **The match path** — the stored-route lookup behind `armRouteMode`, `storedRouteMatch` with its second
  reverse-pair pass, `mirroredPlanOf`, and the per-door fresh flag's **match** branch. **The exact symbols and
  every call site come from reading [`RouteViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:349)
  and [`RouteOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:54) first** — this list
  states the intent, the read states the surface.
- **The fixtures** — `RouteStoredMatchTest` and `RouteMirrorPlanTest` whole, and any case beside them that reads
  a plan inverse for a matched line.

## What stays, and why

- **`routePlanOf` and the track card's follow door** — loading a saved route **the user picked** is a different
  door from matching one behind an arming, and it is untouched: the saved line still opens from the track
  card's resume slot and still draws as the active route.
- **The per-door fresh flag itself** — it is what makes an explicit arming fresh (R92) and stays; only its
  branch *for* the match leaves.
- **The acquisition, the ladder, the session link, the save predicate and the two-phase discard** — the removal
  takes the *match* alone.

## Two consequences, raised rather than taken

- **R82's persisted end ids lose their stated purpose** — the two ids on the track and its summary exist, in the
  feature's own words, *so the acquisition's match reads them without loading a track*. With the match gone they
  are a record nothing reads, and stripping them means a proto field pair, the index projection and the save
  path change together. **That is a data-model cut, not this order's surface**, so it is named and left.
- **The fan's `Route auto` child becomes redundant** — its one distinguishing behaviour was consulting the
  match (R83), and once every arming searches it arms exactly as its `Route` sibling does. Its *behaviour* is now
  the ordered one; whether two identical route children stay in the arc is **a change the user sees, so it is
  raised and not assumed** — and R91's own wording would need the clause about the `Route auto` child revisited
  either way.

## Verification

- **One fixture replaces the deleted pair**: an arming over a pair whose stored route *does* exist runs the
  acquisition — the assertion that no stored line is ever taken, which is the whole of the new rule.
- **No dead reference** — no KDoc, comment, `## Docs` line or Delta step still describes a match, and the
  feature file's R-rows and the plan pointers move with the removal.
- **`apk-build.bat` green**, and the suite triple unmoved but for the retired fixtures, with no verdict
  assertion moved in a suite the removal does not own.

## Risks

- **This is a visible change on the water**: a return trip, or a re-arming of the same pair, used to sometimes
  answer instantly from a stored line and now always searches — the user's own line and clock may differ from
  what they saw before, which is the price of the plain rule and theirs to accept.
- **The flag is load-bearing beyond the match**, so its call sites are read before any branch of it is deleted.
