<!-- scope: feature -->
# The corner pass: the three fixes the captures proved

Authorised by the user's `#impl` on 2026-10-13, on branch `feature/routing-angles`. The three scopes
below are the user's own three choices: stop slowing bends that need nothing, let a curve use the whole
side, and let the turn floor stop being a rule and become a target.

## What was measured, in plain words

A bend is smoothed only if the pass finds enough **straight line to sweep the curve in**, and today it
looks for that room **half as far as the shorter of the two legs beside the bend** — never at the water.
Where the line's own vertices sit close together, which is what keeps the drawn water precise near a
zone or the shore, the room runs out and the pass leaves an angle.

The room a curve needs is set by `r = v_floor² / a_lat`, so the user's look dial (`route.turn.lateralAccelMps2`,
deliberately lowered to buy wide gentle curves, reset to **0.5** on 2026-10-13) does two jobs at once: it
widens the radius the pass *draws*, and it lowers the speed a fitted radius *allows*. Lowering it grew the
room every curve needs — at a 48° bend, 19.8 m at `a_lat` = 0.15 against 8.9 m at the code default 0.33 —
which is why a bend with 17.0 m of room fell 2.8 m short.

### The evidence

| Where | Read |
|---|---|
| `_routes_missed_turn_/` captures, listing | 18 standing bends over 8 captures; every one refused for room, none for coast, depth or zone |
| 43.533054 / 7.110666 capture | 48.3° between a 33.9 m stub and a 1540.6 m leg, with a 790 m leg at 23.12 kn feeding the stub |
| 43.570255 / 7.131253 capture | 98.4° with 141.4 m in and 25.6 m out, drawn at exactly 5.00 kn |
| Same bend, device tally | `why=FLOOR_ROOM turn=98.4 ask=12.8 emitted=141.4 room=12.8 speed=5.00` — the pass's own figures, matching the listing to the metre |
| Device tally, shallow bends | `why=SHALLOW turn=9.6 ask=68.2 room=68.2 speed=5.00kn` and `turn=5.2 ask=70.7 room=70.7 speed=5.00kn` — room to spare, and the floor written anyway |
| Capture profile beside the shallow kink | 23.12 kn into a 3.3° kink capped at **8.69 kn**, then the 48.3° bend drawn at 24.17 kn |

## Scope 1 — no floor ceiling on a bend that needs nothing

**Today:** every bend the pass leaves standing is handed `min(route.turn.minSpeedKn, pace)` as its corner
ceiling — the shallow refusal included, which is a bend the pass never even asked a radius question about.
So a 5.2° kink on a 781 m leg caps that whole leg at 5 kn, and whole straights in the captures read
exactly 5.00 kn for hundreds of metres.

**Change:** write a corner ceiling only where the refusal is about the turn — the room refusals and the
bow-wall refusals. A bend refused for being **too shallow to round**, or for a degenerate leg, writes
**no** ceiling, so the enforced limit stands through it. `CornerVerdict.speedKn` follows the same rule so
the tally reports the pace, not the floor, for those two outcomes.

**Touch points:** `RouteCornerPass.round()`'s refusal branch, and `CornerVerdict.speedKn`'s assembly.

## Scope 2 — the room is the whole side, not half of it

**Today:** `halfSegment = min(legIn, legOut) / 2`, then `room = min(reachFraction × halfSegment, emittedIn)`.
A bend can therefore never use more than half of its shorter side, and two neighbouring bends can never
meet — a 50 % reserve that a wide-radius setting cannot afford.

**Change:** the policy reach becomes the **whole** shorter leg — `reachFraction × min(legIn, legOut)` with
the shipped `route.turn.reachFraction` staying 1.0, so the field keeps its own meaning ("the fraction of
the leg the racing line reaches along", 1.0 being the whole leg). The `emittedIn` cap stays, as the guard
that keeps a new bow from overlapping the one before it.

**Accepted cost, on the user's word:** where two bends sit close, their curves may meet with no straight
between them, and the line reads as one continuous S rather than two curves.

**Worked by the same bend:** the 48° bend's room goes from 17.0 m to 33.9 m, which fits a 44.1 m floor
radius needing 19.8 m; at the reset `a_lat` of 0.5 the need is only 5.9 m.

## Scope 3 — the turn floor becomes a target, not a rule

**Today:** `if (tangentOf(lo) > room) return refused(FLOOR_ROOM)`. Where the floor's own radius cannot fit
the room, the pass gives up and draws the angle, which is what the last approach into Salis does.

**Change:** that branch no longer refuses. The curve is drawn at the **largest radius the room fits** —
`r = room / tan(δ/2)`, no bisection needed — and its corner speed `v = √(a_lat · r)` is written as the
ceiling even where it falls **below** `route.turn.minSpeedKn`. A hard minimum stays in the pass: below
**1 kn** the bend is left standing as today, so a pathological geometry can never draw a near-static
curve. The bow-clearance shrink that already follows applies to the new speed unchanged.

**Worked by the same bend:** the Salis approach, 25.6 m out and a 98.4° turn, fits a 22.3 m radius. At
`a_lat` 0.15 that is 3.6 kn; at the user's reset 0.5 it is **6.5 kn**, above the floor, so the bend is
curved and no rule had to give.

**Meaning change to carry in three places, so the value has one home:**
`app/src/main/assets/maro.properties`'s comment block for `route.turn.minSpeedKn` (the source of truth),
[`AppConfig.routeTurnMinSpeedKn`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:518)'s KDoc, and
the pass's own header. All three must say: **the speed the drawing aims for, which the room may take it
below, down to the pass's own 1 kn minimum.** No user-facing string is involved, so neither locale moves.

## Fixtures each scope must ship with

1. A bend under ten degrees with room to spare writes **no** corner ceiling, and its verdict reads the
   pace rather than the floor.
2. A 48° bend with a 34 m stub between a 790 m and a 1541 m straight is **rounded**, not refused.
3. A bend whose room cannot fit the floor's radius is still **curved**, with a ceiling written below the
   floor and no angle left behind.
4. A bend whose room cannot fit any radius above 1 kn is still **left standing**, ceiling at the floor —
   the guard, pinned so scope 3 can never draw a near-static curve.
5. The existing `RouteCornerPassTest` cases and the engine-level suites still hold, and `assembleDebug` is
   green.

## Parked, out of scope, untouched

- **The doubling-back** (a 174° turn-back over 12.6 m): the user's word is that a route acquisition should
  never produce one, so it is parked rather than fixed here — no spur collapse in the pull or the snap.
- **The coast, depth and zone clearance exits**: nothing in any run was refused for them, so they are not
  touched.
- **The time side**: the floor speed and the 1 m/s² ramp own the clock — about 8 s per bend between 25 kn
  and the floor — and are left as they are; `route.turn.transitionSec` only shapes the entry.
- **The dials themselves**: `route.turn.lateralAccelMps2` is now the user's 0.5 and is not touched, and
  `route.turn.reachFraction` stays 1.0.

## Implemented

2026-10-13 — shipped the three corner-pass fixes the captures proved, on `feature/routing-angles`.

- **Scope 1 — no floor ceiling on a bend that needs nothing.** `round()` writes a corner ceiling only
  where the refusal is about the turn; the pass-local `Attempt.capsAtFloor` (true for `NO_ROOM`,
  `FLOOR_ROOM` and the three `BOW_*` refusals, false for `SHALLOW` and `DEGENERATE`) drives both the
  written ceiling and `CornerVerdict.speedKn`, so a bend too flat or too degenerate to round keeps its
  enforced limit and reports the pace. Side effect: the refusal ceiling is one value in `refused()` now,
  no longer recomputed in `round()`.
- **Scope 2 — the room is the whole side.** `reachAsk = reachFraction × min(legIn, legOut)`, the `/ 2`
  gone, `reachFraction` still 1.0 and the `emittedIn` cap untouched; the field's KDoc is true again in
  its three homes.
- **Scope 3 — the floor becomes a target.** Where the floor's radius cannot fit the room the pass no
  longer refuses: it draws `r = room / tan(δ/2)` at `v = √(a_lat · r)` (clamped to the pace) and writes
  that ceiling even below the floor. A new pass-local `MIN_CORNER_KN = 1.0` hard bottom still leaves a
  bend standing, and the wall shrink still runs — its lower bound the 1 kn bottom where the room drew
  the curve under the floor.
- **The min-speed meaning moved in three homes, no more:** the `maro.properties` comment (source of
  truth), the `AppConfig.routeTurnMinSpeedKn` KDoc, and the pass header — the speed the drawing
  **aims** for, which the room may take it below, down to the pass's own 1 kn minimum.

**Files:** `RouteCornerPass.kt`, `AppConfig.kt`, `maro.properties`, `RouteCornerPassTest.kt`.

**Fixtures:** a sub-10° bend with room to spare writes no ceiling and reports the pace; a 48° bend on a
34 m stub between a 790 m and a 1541 m straight is rounded, not refused; a 90° bend whose room cannot
fit the floor's radius is still curved, ceiling below the floor, no angle left; a 170° bend whose room
fits no radius above 1 kn is left standing at the floor. Each names the code default it assumed.

**Build:** `.\gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL (RouteCornerPassTest 9 tests, 0
failures); `apk-build.bat` → BUILD SUCCESSFUL → `app-debug.apk`.

**Deviations from plan:** none. Judgement calls outside it: `REVERSAL` keeps the floor cap (a turn
refusal, not a "needs nothing" bend — the plan's enumeration was silent on it); `vFitted` is clamped to
the pace as the only guard the plan's no-bisection formula did not state.

**Left un-touched by this hop, and corrected by the one below:** `RouteAvoidEngine.kt`'s comment, the
listing's room formula, and the two KDoc blocks inside the pass that this change made false.

2026-10-13 — follow-up Code hop on `feature/routing-angles`: the `#implement` pipeline's Ask review
found one real defect in the corner pass as it shipped, plus stale text and two weak fixtures.

- **The fitted radius did not fit the drawn curve — the review's catch.** The below-floor branch fitted
  `r = room / tan(δ/2)`, the **circular arc's** tangent, not the drawn clothoid → arc → clothoid's real
  tangent, which is longer by the two spirals' share; the curve then reached ~12.2 m past a 10 m room.
  The branch now bisects `v` over `[0, floor]` for the largest radius whose own
  `composite(r, l, δ).tangent` fits `room` — the same measure the rest of the pass reads — with the
  `emittedIn` cap, the 1 kn bottom and the wall shrink all untouched.
- **Tally truthfulness.** `attemptCorner` reads `cornerInZone` once, before its early exits, and passes
  it to every early `refused(...)` (shallow, degenerate, reversal and no-room), so a standing bend
  inside a zone no longer reports a default `false`.
- **Stale text corrected in place:** `RouteMissedTurnListingTest.kt`'s room formula (both homes, the
  `/2` gone), its refused-bend premise and its `ROOM` wording and legend; the pass's `FLOOR_ROOM`,
  `Attempt` and `CornerVerdict.speedKn` KDoc; `RouteAvoidEngine.kt`'s "even the floor's radius
  overshoots" comment; and `FEAT_DOC_Route_avoid-algorithm.md`'s bisection line. Historical plan files
  left untouched.
- **Fixtures:** the scope-3 fixture now pins the drawn curve's own tangent (corner-to-endpoint
  distances within the 10 m room, the assertion the old arc-only formula breached) beside the corrected
  ≈ 3.16 kn ceiling; a new `aBendAtTheHardBottomCurvesAndAHairLessStands` brackets the guard at 1 kn
  with a plain 140° bend — curved at 1.02 kn, standing at 0.98 kn — which the parked near-reversal
  alone could not pin.

**Files:** `RouteCornerPass.kt`, `RouteCornerPassTest.kt`, `RouteMissedTurnListingTest.kt`,
`RouteAvoidEngine.kt`, `FEAT_DOC_Route_avoid-algorithm.md`.

**Build:** `.\gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL (RouteCornerPassTest 10 tests, 0
failures; RouteMissedTurnListingTest 1 test, 0 failures); `apk-build.bat` → BUILD SUCCESSFUL →
`app-debug.apk`.

**Limitation stated, not fixed:** the suite runs on `AppConfig`'s code default `lateralAccelMps2` 0.33
while `maro.properties` ships 0.5, so every fixture figure here (the ≈ 3.16 kn ceiling and the 1 kn
bracket rooms) is the code-default geometry; each fixture names the default it assumes.

2026-10-13 — later hops, from the device runs of the two saved routes (one `selective`, one `evolutive`).

- **The instrument grew two readings and one rule.** Every tally line now carries `limit=`, the speed
  limit in force at the bend, and `band=`, whether it stands in the 300 m band, and a curved bend now
  reports where it carries a real slowdown **or** takes a `shared` part of its leg — a curve taken at the
  pace with no share still says nothing. The summary gained `slowed=n` and each detail line `shared=`.
- **The shared leg — the scope the run's own figures asked for.** Two bends the pass will curve on one
  leg now **share** it, each taking a share weighted by its own turn (`tan(δ/2)`, capped at 8), which is
  the radius at which the pair meets as one continuous S. The run that asked for it read
  `idx=3 at=43.53305,7.11067 turn=39.1deg ask=55.2m emitted=3.9m room=3.9m speed=2.56kn`: the bend 55 m
  before it had taken the whole leg, leaving a micro-curve at 2.56 kn with the pace back on the next
  leg. A stub under ten degrees is **not** a bend and shares nothing, so the 48° bend on its 34 m stub
  keeps the whole-leg reach the earlier scope exists for; the trade accepted for that scope — two bends
  meeting with no straight between them — is what sharing now delivers.
- **The fixture proves the rule:** two 45° bends on a 55.16 m leg give rooms of 27.58 m and 28.65 m — the
  weighted half each, the bisection's leftover passing to the bend after it — with the pair drawn at one
  radius and neither squeezed below the floor.
- **The belt, read from the code rather than guessed:** [`limitAtFor()`](app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:189)
  answers the strictest of a zone's limit and the 300 m band's own limit inside the band's width, and
  both stand **whatever their `enabled` switch says** — "a switch prices the search, it never suspends
  the limit". So the 5 kn held over that southern water is a limit in force: `route.avoid.zone300.enabled=false`
  does not lift it, and only `route.avoid.zone300.limitKn` or the band's width would.
- **The compound turn — the scope the fifth run asked for.** Where two bends the pass would curve lie
  closer than the tangent a **pace-speed** curve needs for their summed angle, the pair is replaced by the
  **crossing of its two outer legs**: the summed angle is drawn once over the outer legs, through the same
  clearance tests as every other bend, so the water still has its say. Only a pair turning the **same way**
  is merged, and a near-parallel pair — or one whose crossing falls beyond its outer legs — is left alone.
  Fixtures: two 45° bends 55 m apart become one 90° corner taken fast with both original vertices gone from
  the drawn line, while two 45° bends 800 m apart stay two, the first capped at its own weighted share.
- **The significant leg, and the relative shore wall — the two the sixth log asked for.** A bend's room is
  now measured to the **nearest vertex the pass will consider** on each side, the line's own end counting as
  one, never to the adjacent point — so a 3° kink 15 m ahead no longer caps the bend beside it. The kink
  keeps its own drawn point, and a bow whose room reaches past it takes back the points it has superseded, so
  the drawn line never folds back on itself. The coast test is now **relative to the corner it replaces**: a
  bow may not come closer to the shore than the corner itself, so a bend the walk placed inside the 50 m
  margin can be rounded while every bow from a corner standing clear still keeps the margin whole; the
  shallow wall stays absolute, being a lawful ceiling rather than a distance. Both changes arrived beside a
  **second writer's** variant of the same scope, whose fixtures disagreed with its own drawing branch; the
  contradictions were settled in favour of keeping every point the line was given, which is this file's own
  older promise that the pass never churns a line's points.
- **The overlap guard measures bend to bend.** The seventh log showed both aims met and neither room moved:
  `43.53305,7.11067` read `ask=1398.8m` with `emitted=33.9m room=33.9m speed=8.94kn`, and `43.54471,7.15744`
  read `ask=890.7m` with `emitted=14.9m room=14.9m speed=5.70kn` — the guard, written when every drawn point
  was a curve or the line's origin, was measuring back to the **kept kink** itself. It now tracks the tail of
  the last bow actually drawn, or the line's origin where no bow stands, leaving a bare vertex out of it: a
  kink that is not a curve is nothing for a new bow to overlap. Fixture `aKinkTooFlatToBeACornerDoesNotCapTheBendBesideIt`
  (a 6° kink 12 m before a 48° bend on a 400 m feed and a 500 m exit) reads `reachAsk` 412 m, `roomM` 412 m
  and a 28 kn corner where the old guard read 12 m. The Scope 2 fixture's `roomM = 34` assertion was updated
  with its comment, since it pinned the very defect this removed.
- **Still open:** why the line places a corner inside the 50 m margin at all — the twin of the relative
  shore rule, and the one that would correct the line rather than forgive it.
