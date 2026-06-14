---
name: ArcLayout
status: active
created: 2026-06-13 07:34
modified: 2026-06-14 18:53
active_subfeature: fan-migration
---

**Description:** Replace the two isolated layer toggle buttons on the map's right-edge control stack with a single anchor button that fans out into a pure-Compose arc menu to the left, exposing 4 layer toggles (low depth warning, 300m zone, depth layer, regulated zones) as a cohesive multi-toggle control.

**Reference:** [`plans/arclayout-feature-plan.md`](../../plans/arclayout-feature-plan.md)

## Subfeatures

### ArcLayout Core Implementation  [x]

#### Todos
- [x] Add `depthLayerVisible` to `AppSettings` + `SettingsManager` (load/update/keys)
- [x] Add `toggleDepthLayerVisibility()` to `CoastlineViewModel`
- [x] Create `ArcLayoutToggle.kt` — `ArcAnchorButton` + `ArcButtonOverlay` + animated arc buttons + Canvas icons
- [x] Wire into `MapScreen.kt` — replace middle Column anchor, wire through MapContent, add `BackHandler` for arc dismiss
- [x] Add visibility gates for `depthLayerVisible` and `regulatedZonesVisible` in `MapContent`
- [x] Fix layout: anchor in Column, scrim+arc at top-level Box with absolute positioning via `onGloballyPositioned`
- [x] Fix arc spacing: R=96dp (300% of 32dp), sweep=180°, chord=96dp → 32dp gaps
- [x] Add smooth retraction (collapse) animation — animate arc buttons back to staging point below anchor simultaneously (~200ms) on dismiss
#### Rules
- All new settings follow the existing `AppSettings` pattern
- Use `remember { Animatable() }` for smooth fly-in animation (re-read `.value` every frame)
- Scrim at top-level Box, arc buttons use absolute root coordinates from anchor's `positionInRoot()`
- Canvas-drawn icons for all 4 buttons, no material-icons-extended dependency
- Regulated zones toggle hooks into existing full data model (`regulatedZonesVisible` already existed)

#### Key Files
- [`app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt)
- [`app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)
- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)
- [`app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt](../../app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt)

### fan-migration  [ ]

#### Todos
- [x] Design FanLayout framework: parameterised θ, parent-at-center, equidistance per relationship type
- [x] Create FanConfig, FanLayout, MapControlButton, FanIconComponents
- [x] Port old ArcAnchorButton + ArcButtonOverlay to FanLayout
- [x] Add badge, animation, toggle support to FanLayout
- [x] Move 4 Canvas icons from ArcLayoutToggle to FanIconComponents
- [x] Set maxCount=5, direction=LEFT for layer fan (maxCount=5, currentCount=4)
- [x] Add per-child toggle state via activeStates parameter
- [x] Center maxCount template (not currentCount) in 180° semicircle
- [x] Fix child centering: use effectiveTheta=180/currentCount for angles+R, full-arc distribution with ½θ at each end
- [x] Refactor control stack: replace hardcoded Column children with dynamic ControlItem list; non-fan controls hide via AnimatedVisibility when any fan is expanded
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
- **No scrim** — close on parent tap or BackHandler

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt`

#### Docs
- `plans/arclayout-button-analysis.md` — original analysis
- `plans/fanlayout-extension-discussion.md` — toggle + badge + animation design
- `plans/fanlayout-equidistance-rule.md` — equidistance geometry rule
- `plans/fanlayout-child-centering-rule.md` — child button centering design rule

## Rules
- Keep the plan at `plans/arclayout-feature-plan.md` as the single source of truth for design decisions
- No library dependencies — pure Compose custom layout

## Docs
- [`plans/arclayout-feature-plan.md`](../../plans/arclayout-feature-plan.md) — Full feature plan
