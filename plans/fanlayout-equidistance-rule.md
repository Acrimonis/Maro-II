# FanLayout Equidistance Rule — Final

## The Geometry Constraint

**Parent at center. All distances equal.**

With parent at center of a circle and children on the arc at radius R:
- parent→child = R
- child↔child chord = 2R × sin(θ/2)

For **full numerical equidistance**: R = 2R × sin(θ/2) → sin(θ/2) = 0.5 → **θ = 60°**

So θ is **fixed at 60°** regardless of maxCount. This is a geometric necessity — there is no other angle that makes parent-at-center distances equal.

## The maxCount Parameter

Since θ = 180° / maxCount by our convention, and θ must be 60°:
- **maxCount = 3** (θ = 180°/3 = 60°)

This means the fan supports at most **3 children** in full equidistance mode. A 4th child would require θ < 60°, breaking equidistance.

## Derived Values (64 dp buttons, 8 dp gap)

| Property | Value |
|----------|-------|
| maxCount | 3 |
| θ | 60° |
| R | 72 dp |
| All distances | 72 dp |
| 3 children span | 180° (full semicircle) |
| 2 children span | 120° (centered) |
| 1 child | 0° (at direction center) |

## For the Layer Fan (currentCount=4)

This is incompatible with maxCount=3. You'd need to either:
- Reduce to **3 children** (remove one toggle)
- Or accept non-equidistant spacing for 4+ children

## Trade-off Summary

| Approach | maxCount | θ | Equidistant? | Max children |
|----------|----------|---|--------------|-------------|
| Full equidistance (this rule) | 3 | 60° | ✅ All = 72 dp | 3 |
| Previous rule (per-relationship) | 5 | 36° | ❌ Parent=116, Child=72 | 5 |

Pick one — either accept maxCount=3 with 3 children and full equidistance, or keep maxCount=5 with 4 children and the visual gap.
