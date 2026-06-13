---
name: ArcLayout
status: active
created: 2026-06-13 07:34
modified: 2026-06-13 12:51
active_subfeature: none
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
- [`app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt`](../../app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt)

## Rules
- Keep the plan at `plans/arclayout-feature-plan.md` as the single source of truth for design decisions
- No library dependencies — pure Compose custom layout

## Docs
- [`plans/arclayout-feature-plan.md`](../../plans/arclayout-feature-plan.md) — Full feature plan
