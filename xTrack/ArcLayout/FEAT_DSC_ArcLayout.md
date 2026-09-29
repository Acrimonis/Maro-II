---
name: ArcLayout
status: active
created: 2026-06-13 07:34
modified: 2026-09-29 11:35
---

**Description:** Replace the two isolated layer toggle buttons on the map's right-edge control stack with a single anchor button that fans out into a pure-Compose arc menu to the left, exposing 4 layer toggles (low depth warning, 300m zone, depth layer, regulated zones) as a cohesive multi-toggle control.

**Reference:** [`260613_FEAT_PLN_ArcLayout_feature-plan.md`](260613_FEAT_PLN_ArcLayout_feature-plan.md)

## Sections

### fan-migration

FanLayout framework (θ-parameterized, parent-at-center, equidistance) ported the arc toggle to a multi-fan control — per-child toggle state, active badge, 70ms stagger expand / 200ms collapse.

#### Todos
- [ ] Add second fan button in control stack
- [ ] Replace hardcoded Spacer(136.dp) with computed value

#### Rules — STRONG
- **Primary params:** maxCount sets slot template; effectiveTheta = 180/currentCount for actual spacing when currentCount < maxCount
- **When currentCount < maxCount:** distribute children across FULL 180° arc with ½θ (= θ/2) empty at each end — "½ space, btn, btn, btn, btn, ½ space" = 5 slots total
- **When currentCount == maxCount:** use original θ-spaced template centered in 180° with 18° margins
- **Parent at center.** Children on arc at radius R. All parent→child = R (internally equal). All child↔child chords = 2R × sin(θ/2) (internally equal).
- **R formula:** R = (buttonSizeDp + edgeGapDp) / (2 × sin(effectiveTheta/2))
- **Angle convention:** 0°=top, 90°=right, 180°=bottom, 270°=left
- **Children on top** (Compose declaration order: parent first, children after)
- **Toggle mode:** children receive per-child activeStates; active=alpha 1.0, inactive=alpha 0.25
- **Active badge:** 18 dp blue circle at TopEnd of parent, bold white text
- **Animation:** 70ms stagger expand, 200ms simultaneous collapse
- **No scrim** — close on parent tap or BackHandler (scrim now at MapContent level)
- **Momentary mode and per-child enablement** (2026-09-29, the Route fan): `toggleChildren = false` makes a fan whose children are **actions** rather than toggles — every child receives `false` for `isActive`, so it draws at full tint and reads **`enabledStates`** instead, a `false` entry suppressing that child's press and dimming it to the inactive alpha (0.25) through `MapControlButton(enabled = …)`. Both new parameters default (`enabled = true`, `enabledStates = emptyList()`), so the layer fan and every other caller are untouched

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt`

#### Docs
- `260614_FEAT_PLN_ArcLayout_button-analysis.md` — original analysis
- `260614_FEAT_PLN_ArcLayout_extension-discussion.md` — toggle + badge + animation design
- `260614_FEAT_PLN_ArcLayout_equidistance-rule.md` — equidistance geometry rule
- `260614_FEAT_PLN_ArcLayout_child-centering-rule.md` — child button centering design rule

## Implemented

- **ArcLayout Core Implementation** — `depthLayerVisible` setting + toggle, `ArcLayoutToggle` (anchor + arc + Canvas icons), wired into MapScreen
- **scrim-dismiss** — transparent full-screen scrim dismisses the fan via `onDismissFan`; badge no longer dims
- **control-stack-button-swap** — moved Add Zone (add-marker) button above the layer FanLayout in the main-screen control stack (MapScreen.kt), so the fan sits below the add-marker control. **Superseded 2026-09-29** (Route's [`260929_FEAT_PLN_Route_map-fan-add-route.md`](../Route/260929_FEAT_PLN_Route_map-fan-add-route.md)): the Add Zone square is gone — the marker wizard's door moved to each markers header in `12b8338` — and a **route fan** now stands in that `cm` slot, above the layer fan as the square did, with a second `ControlId.ROUTE_FAN` and the layer fan's own fade form rather than the replaced button's

## Rules
- Keep the plan at `260613_FEAT_PLN_ArcLayout_feature-plan.md` as the single source of truth for design decisions
- No library dependencies — pure Compose custom layout

## Docs
- `xTrack/ArcLayout/260614_FEAT_PLN_ArcLayout_button-analysis.md` — Button analysis
- `xTrack/ArcLayout/260613_FEAT_PLN_ArcLayout_feature-plan.md` — Full feature plan
- `xTrack/ArcLayout/260614_FEAT_PLN_ArcLayout_fan-btn-hide-ozers-plan.md` — Fan button hide ozers plan
- `xTrack/ArcLayout/260614_FEAT_PLN_ArcLayout_child-centering-rule.md` — Child centering rule
- `xTrack/ArcLayout/260614_FEAT_PLN_ArcLayout_equidistance-rule.md` — Equidistance rule
- `xTrack/ArcLayout/260614_FEAT_PLN_ArcLayout_extension-discussion.md` — Extension discussion
- `xTrack/ArcLayout/260613_FEAT_PLN_ArcLayout_badge-clipping-fix.md` — Badge clipping fix
- `xTrack/ArcLayout/260625_FEAT_PLN_ArcLayout_fan-layer-button-visibility-rules.md` — Fan layer button visibility rules
- `xTrack/ArcLayout/260712_FEAT_PLN_ArcLayout_confirm-sheet-migration-plan.md` — Confirm sheet migration plan
- `xTrack/ArcLayout/260625_FEAT_PLN_ArcLayout_overlay-layer-framework-plan.md` — Overlay layer framework plan
