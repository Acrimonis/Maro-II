# Marker Proximity Rendering + Wizard Normalization Plan

## A. Fix Now: Proximity Rendering

### Root cause
[`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:46) `COLOR_PROXIMITY_PREVIEW = 0x4D4FC3F7` (~30% alpha cyan) combined with `DashPathEffect(12f, 8f)` from [`buildPolyline()`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:386) makes proximity previews nearly invisible — dashed + low alpha + thin 2px stroke.

### Fix
In [`buildPolyline()`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:386), add a `dashed: Boolean = true` parameter. Pass `dashed = false` for all proximity preview overlays (Pin/Circle/Corridor proximity calls). Also bump alpha to ~50% for `COLOR_PROXIMITY_PREVIEW`.

| File | Change |
|------|--------|
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:46) | `COLOR_PROXIMITY_PREVIEW` alpha: 0x4D → 0x80 (50%) |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:386) | Add `dashed: Boolean = true` param to `buildPolyline()`; skip `DashPathEffect` when `!dashed` |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:169) | Pin proximity: `buildPolyline(…, dashed = false)` |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:184) | Circle proximity: `buildPolyline(…, dashed = false)` |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:199) | Corridor proximity parallels: pass `dashed = false` through `addCorridorParallels` → `buildPolyline` chain |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:362) | `addCorridorParallels`: add `dashed: Boolean = true` param, forward to `buildPolyline` |

Side note: The proximity `strokeWidth` is currently `2f`. Bumping to `3f` would also help visibility — optional.

---

## B. Fix Now: Marker Zone Display Settings

### Feature
Add a **"Markers"** entry under the **Display** section of [`GeneralSettings`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2348), near the existing Layers toggles.

### UI layout
```
┌─────────────────────────────────────────┐
│ DISPLAY                                 │
│                                         │
│ Layers                                  │
│ ┌─────────────────────────────────────┐ │
│ │ Markers                        [⚫] │ │  ← toggle (zone visibility)
│ │ [▶ Zone colors               ]     │ │  ← collapsible (only when on)
│ │   ┌─ Pin ────────────────────────┐  │ │
│ │   │ Center  ●  [■■■■■■■■■■]    │  │ │  ← color picker / swatch
│ │   │ Proxim  ○  [■■■■■■■■■■]    │  │ │
│ │   └──────────────────────────────┘  │ │
│ │   ┌─ Circle ──────────────────────┐  │ │
│ │   │ Center  ●  [■■■■■■■■■■]    │  │ │
│ │   │ Radius  ◯  [■■■■■■■■■■]    │  │ │
│ │   │ Proxim  ○  [■■■■■■■■■■]    │  │ │
│ │   └──────────────────────────────┘  │ │
│ │   ┌─ Corridor ────────────────────┐  │ │
│ │   │ Center  ●  [■■■■■■■■■■]    │  │ │
│ │   │ Width   ═  [■■■■■■■■■■]    │  │ │
│ │   │ Proxim  ○  [■■■■■■■■■■]    │  │ │
│ │   └──────────────────────────────┘  │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### Data model
Add to `AppSettings`:
```kotlin
val markerZonesVisible: Boolean = true,
val markerCenterColor: Int = AppConfig.semanticInfo,        // blue
val markerRadiusColor: Int = AppConfig.semanticInfo,        // blue (circle edge / corridor width)
val markerProximityColor: Int = 0x804FC3F7.toInt(),         // cyan @ 50%
```

### Wiring
- [`AppConfig`](app/src/main/java/ykws/android/maro/config/AppConfig.kt) gets new properties (`markerCenterColor`, `markerRadiusColor`, `markerProximityColor`) seeded from `maro.properties`
- `SettingsManager` persists these in SharedPreferences
- [`GeneralSettings`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2348) renders the toggle + collapsible color rows
- [`MarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:49) reads colors from `AppConfig` instead of hard-coded constants
- Zone visibility toggle gates `markerLayerVisible` already exists — reuse or add a dedicated `markerZonesVisible` that disables only zone shapes (Corridor parallels, Circle outlines) but keeps center dots

### Scope note
Start with the toggle + a simple color preview (swatch box, no full color picker). Full Android color picker is a separate feature.

---

## C. Plan: Wizard Flow Normalization

### 1. Wizard Button Styling
- Current: standard `Button` with `RoundedCornerShape(6.dp)`
- Target: match [`SettingsOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2253) button style — `CircleShape`, `48.dp` icon buttons, accent-colored action buttons
- Change `WizardButtonRow` to use `FilledTonalButton` or match the settings button pattern

### 2. TypeSelect Step — Tighter + More Evident Selection
- **Current**: [`TypeCard`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:395) uses `borderColor` + `bgColor` but the border isn't drawn (only background change on selection). Selected bg uses `0.08f` alpha accent — barely visible.
- **Fix**:
  - Add `Modifier.border(2.dp, borderColor, RoundedCornerShape(10.dp))` on the card
  - Increase selected background alpha from `0.08f` → `0.15f`
  - Reduce vertical padding from `12.dp` → `8.dp`
  - Reduce icon size from `28.sp` → `24.sp`

### 3. Position Step — Crosshair Instead of Boat
- **Current**: [`PositionStep`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:448) just shows text instructions. The map's center marker (`CenterMarkerOverlay`) shows the boat icon normally. During position step, the boat is inappropriate — user is placing a marker, not navigating.
- **Fix**: When `drawerState is Creating || drawerState is Editing` AND `wizardStep is Position || PositionP2`, replace the boat `CenterMarkerOverlay` with a crosshair/target icon (e.g., `＋` or `⊕`). Pass a flag from MapScreen to `CenterMarkerOverlay`.

### 4. Step Counter Header
- Add a generic wizard header: `"Create Marker — Step 3 of 6"`
- Replace the per-step title (currently in `WizardTopBar`) with this step-counter format
- Each step's specific instruction ("Drag the map…", "Choose radius…") becomes the body text, not the title

### 5. Consistent Layout Normalization
- **SliderStep**: already card-style. Reduce outer padding: `12.dp` → `8.dp`.
- **TextInputStep** ([`WizardDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:551)):
  - Label: left-align instead of center
  - `OutlinedTextField`: add `Modifier.focusRequester` + auto-focus + select-all on entry
- **PositionStep**: already clean. Reduce padding: `12.dp` → `8.dp`.
- **Proximity step**: same as SliderStep — good.

### Normalized page template
```
┌─────────────────────────────────────┐
│ ← Cancel    Step 3 of 6            │  ← header (like settings)
├─────────────────────────────────────┤
│                                     │
│  Instruction text (14sp, centered)  │  ← body: brief guidance
│                                     │
│  ┌─────────────────────────────────┐│
│  │ Card-style input area          ││  ← slider / text field / type cards
│  │ (12dp corner, card bg)         ││
│  └─────────────────────────────────┘│
│                                     │
├─────────────────────────────────────┤
│ [Previous]      [Next]    [Finish]  │  ← button row
└─────────────────────────────────────┘
```

---

## Implementation Order

| # | Item | Effort | Dependencies |
|---|------|--------|-------------|
| 1 | Proximity rendering fix (solid lines + alpha) | Small | None |
| 2 | Marker zone toggle in settings (no colors yet) | Medium | #1 (reads same colors) |
| 3 | TypeSelect tighter + border | Small | None |
| 4 | Crosshair during position step | Small | None |
| 5 | Step counter header | Small | None |
| 6 | TextInputStep normalization (left-align, select-all) | Small | None |
| 7 | SliderStep padding reduction | Trivial | None |
| 8 | Wizard button restyling | Small | None |
| 9 | Marker zone color customization (swatches) | Medium | #2 |
