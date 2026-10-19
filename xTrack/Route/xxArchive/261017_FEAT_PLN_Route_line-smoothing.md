<!-- scope: feature -->
# Route — a wall-checked Douglas–Peucker straightening on the pulled line

**Date:** 2026-10-17 · **Status:** implemented (its device pass open) · **Order:** *plan fix* after the 17:06 device pass (the line reads ragged), then *use a better line straightening method* after the 17:55 pass showed the first method added bends — the rule is *at least 20 m out, more when possible*, and the line must look straight without ever crossing the 20 m wall.

## Why

- The 17:06 Coastal pass was compliant — `gateEdge=20.0m minDepth=4.1m bodyEdge=25–30m` — but the body tracks the bank's outline at the 20 m fine resolution, so the line reads jagged.
- **The first method, Chaikin corner-cutting, made it worse.** It *adds* points (¼–¾ subdivision), so the 17:55 device pass read `SMOOTH points=10→19` and `14→21`, the corner pass then saw `CORNERS total=17..19` (12 before) with many `turn=0.0deg` SHALLOW micro-corners, and the closest approach to the water fell from `20.0m` to `5.0m` (`minDepth` 4.1 → 3.1 m).
- The right direction is to **drop** points, not add them: near-collinear waypoints are what the corner pass turns into micro-bends.

## The change (as shipped)

1. **Douglas–Peucker, after the pull and before the corner pass.** `straightenLine(setup, points, epsilonM = 10.0)` in [`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:398) replaces a run of waypoints by its chord when every dropped point lies within `epsilonM` of that chord; otherwise the farthest point is kept and the two halves are straightened in turn. It is called at the end of `RouteFinePass.finePass` (trace label `STRAIGHTEN`).
2. **It only ever drops points.** Every kept point is one the pull already proved clear, and no point is invented — so the straightener can never plant a point and can never add a bend.
3. **The wall and the price bound every chord.** `chordLegal` refuses a chord that `legClearCause` refuses (the wall) or that is dearer than the run it replaces (`softPriceSec`), so a straightening cannot cut the wall or trade into dearer water — the guard that also stopped the earlier Chaikin attempt entering a priced zone.
4. **Deviation measured in metres.** `perpendicularM` uses an equirectangular projection about the chord's start, so the tolerance is a real distance, not a degree.

## Review — 2026-10-17

- **Points only drop.** The 17:55 evidence is the reason: a subdividing method doubled the points and the bend count; Douglas–Peucker cannot do that.
- **Endpoints preserved, no invented point.** The recursion pins the first and last and keeps only original vertices.
- **The chord is the only new geometry**, so it alone is wall- and price-checked; the kept points were already proved by the pull.
- **Tolerance is the one open number.** 10 m is the starting value; a larger epsilon straightens more but risks the wall (refused by the guard) and the device pass tunes it.

## Tests

- `RouteStraightenTest` (in `RouteSmoothPassTest.kt`): a near-collinear run collapses to its chord; a point off the chord is kept; a chord that would cross the wall is refused; straightening never adds points and keeps the ends; a two-point line is untouched.

## Implemented — 2026-10-17

- `straightenLine` / `markStraight` / `chordLegal` / `perpendicularM` in [`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:398); the Chaikin `smoothLine`/`chaikinPass`/`markClear` removed.
- Wired at the end of `RouteFinePass.finePass` before `RouteCornerPass.round`.
- `RouteStraightenTest` — five tests. Build: 1317 tests, 1 pre-existing failure (`PullAllowancePropertiesTest`), 12 skipped.

## Open

- The device pass on the Douglas–Peucker line: does it read straight, and does `gateEdge` return to ≥ 20 m at the ends?
- The epsilon (10 m) and whether the straightener also runs on `avoid` (its fine pass runs too) — to confirm or gate.
- The test file keeps its old name `RouteSmoothPassTest.kt` while the class is `RouteStraightenTest`; rename the file.
- The `chordLegal` price branch has no direct test — add one where a dearer chord is refused while a cheaper one collapses.
- `chordLegal` uses a strict `<=` with no allowance, so it may under-straighten where the sweep's own allowance would drop more; the allowance is the second tuning lever beside epsilon.

## Outcome

Shipped as planned in its second form: the first method, Chaikin corner-cutting, added points and was replaced by a Douglas–Peucker `straightenLine` (10 m) between the pull and the corner pass. Its rule and open points are promoted to `FEAT_DSC_Route.md` ## Rules and `FEAT_DOC_Route_decisions.md` §8; the device pass is owed there.
