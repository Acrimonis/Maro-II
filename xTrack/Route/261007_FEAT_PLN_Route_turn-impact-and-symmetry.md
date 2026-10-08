<!-- scope: feature -->
# Route — the turns: more reach, and symmetry

**Date:** 2026-10-07 · **Status:** in design, for discussion (point 3 of four) · **Order:** the user's word
of 2026-10-07 — *"I would like to review the turn computation. I would like them to have more impact: reach
farther in and out from the turns, to look more like a driving trajectory (for smoother driving and wider
corners). Right now there seem to be some weird artifacts generated when I lower the lateralAccelMps2
especially out of the turn and a lack of symmetry."*

## What is there today

[`RouteCornerPass.round(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt:53)
reads four keys once per solve and fits **each bend on its own**:

- [`route.turn.reachFraction`](../../app/src/main/assets/maro.properties:165) = **1.0** — the curve reaches this
  fraction of the **shorter half-segment** along each leg, and that reach is what sets the widest radius that
  fits. At 1.0 the fit already consumes the whole gap to the neighbouring apex's midpoint.
- [`route.turn.lateralAccelMps2`](../../app/src/main/assets/maro.properties:147) = **0.33** in the asset (the
  accessor's own default is **1.0**, so the two disagree) — the lateral acceleration the bend may impose, and
  therefore the radius at the pace in force.
- [`route.turn.minSpeedKn`](../../app/src/main/assets/maro.properties:161) = 5 — the floor a bend may not go
  below, where even the floor's radius cannot clear.
- [`route.turn.transitionSec`](../../app/src/main/assets/maro.properties:156) = 2.0 — the profile clock's own
  lead-in and lead-out at a bend ([`timeLineWithProfile`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt)).

A bulge is refused outright when any of its points fails the land margin, the depth gate, or a zone it did not
already touch — so a bend near the shore degrades to a sharper one rather than widening.

## Why more reach is not just a bigger number

The reach is capped by the **shorter** of the two half-segments, so the two sides of a bend are already fitted
against different lengths — that is structural asymmetry, not an artefact of tuning. And with
`reachFraction` at 1.0 there is no room left to extend into: lowering `lateralAccelMps2` asks for a larger
radius than the reach cap permits, so the lever stops having effect and, near the cap, the fit is refused or
degenerates at one end. That is the most likely shape of the "weird artifact out of the turn" the user sees —
but it must be **reproduced before it is explained**, and the two candidate causes separated:

- **geometry** — the corner fit refusing or degenerating when the radius outgrows the reach; and
- **clock** — the profile's own asymmetry, where deceleration into a bend and acceleration out of it are
  bounded over the same `transitionSec` but the water either side differs.

## What the change would be

1. **Reproduce and pin the artifact first.** A trace at a low `lateralAccelMps2` on a fixture bend, printing
   per corner: the radius asked, the radius the reach allows, whether the bulge was refused and why, and the
   profile's own speeds either side. Without that, any fix is a guess — and this is the cheap half.
2. **Reach against the neighbouring corners, not the half-segment.** The "driving trajectory" the user wants is
   the classic one: fit a single circle (or clothoid) through the incoming and outgoing legs such that
   **adjacent bends share their tangency**, so the curve reaches *past* the midpoint into the next straight and
   the two sides of a bend are built from the same pair of legs rather than independently. That removes the
   asymmetry by construction and is what makes a lower lateral acceleration actually widen the corner.
3. **Then the caps.** With the fit shared, the reach can be expressed as a distance along each leg with a
   factor that may exceed the midpoint (a new or re-defined `reachFraction`), and the clearance refusal
   becomes the only brake — stated, rather than silently sharpening the bend.

## Feasibility and risks

- **(1) is cheap and safe** and should precede everything else.
- **(2) is the largest single change in this four-point review**: it replaces the per-corner fit with a
  shared-tangency one, touching `RouteCornerPass` (and possibly `TangentCorners`, whose corner sets supply the
  candidate points). It changes every drawn line and therefore needs its own fixtures and a device pass.
- **The refused bulge is a real constraint.** On a narrow channel the shared fit may have nowhere to go; the
  fallback (a sharper bend at the floor speed) must stay, and the plan must say when it applies.
- **The clock follows the geometry**, so a wider bend also moves the reported time — the two must be judged
  together, and the pace/limit in force still wins wherever a zone is inside the bend.
- **Two keys are entangled with the audit** (point 2): `lateralAccelMps2`'s asset/code drift and
  `reachFraction`'s meaning. Decide their values here and align them there.

## Phases (sketch)

1. The trace and the fixture that shows the artifact, with the two causes told apart.
2. The shared-tangency fit, behind its own fixtures, with the refusal fallback preserved.
3. The reach's new meaning and its key, with the corner pass's doc and the engines' reference updated.
4. A device pass on a real coastal route — the user's own — reading the bend shapes and the reported times.

## Open questions

- Is the artifact **out of the turn only** (the profile) or also **into** it (the fit)? The user says
  *especially out of the turn*, which points at the clock — the trace must confirm it.
- Does "wider corners" mean a larger radius **wherever the water allows**, or a specific look — the racing
  line the model already claims, or a road-like clothoid with a visible lead-in?
- Should the reach be able to consume an entire leg on a long straight (i.e. one continuous curve through
  several bends), or stay per-bend with shared tangency only between neighbours?
- With the fit shared, what is the **fallback** when the water refuses it — sharpen the bend, slow it to the
  floor, or refuse the rung's line and let a coarser one stand?
