<!-- scope: reference -->
# UI Component Guidelines

> **Canonical patterns** for cards, expanders, toggles, sliders, and drawers across the app.
> Code wins over doc — when they disagree, update this file.

> **Tokens:** [`ui-tokens.properties`](../app/src/main/assets/ui-tokens.properties) (dimensions) +
> [`colors.properties`](../app/src/main/assets/colors.properties) (colors).
> All `${ui.*}` references below resolve to those files.

---

## 1. Decision Flow

```
New setting?
  ├─ Standalone toggle?          → SettingsToggleRow           (§2.1)
  ├─ Standalone slider?          → SettingsSliderRow            (§2.2)
  ├─ Toggle + sub-settings?      → Grouped card (§2.3)
  │   ├─ Sub = sliders?          → SettingsSliderGroup(nested=true)  (§2.4a)
  │   └─ Sub = text/toggles/etc? → Nested card inline              (§2.4b)
  ├─ Exclusive 2–3 choice?       → Segmented selector            (§2.7)
  ├─ Double-thumb value range?   → RangeSlider section           (§2.8)
  ├─ Feature w/ sub-settings?    → Grouped card: feature toggle + sibling expander (§2.3)
  ├─ Toggle group + slider?      → Grouped card (§2.3)
  └─ Drawer/Track card?          → Same card surface, specific rows (§5)
```

---

## 2. Components

### 2.1 Standalone Toggle — `SettingsToggleRow`

Self-contained card (`uiCardBackground`, 12dp radius, 16×8dp pad). Gap between: `${ui.spacing.card.gap}`.

🔴 Never nest inside a grouped card — it IS a card.

### 2.2 Standalone Slider — `SettingsSliderRow`

Wraps `SliderRowContent` inside `SettingsSliderGroup`. Standalone (top-level) = `uiCardBackground`, 16×8dp pad. Nested = `0x0DFFFFFF`, 16×2dp pad. Gap: `${ui.spacing.card.gap}`.

### 2.3 Grouped Card

Outer `Column(uiCardBackground, 12dp radius)` with **inline toggle rows** (not `SettingsToggleRow`):

```
Column(uiCardBackground, 12dp radius, ${ui.padding.card.vertical} pad) {
    Row(16×${ui.padding.grouped.toggle.vertical} pad) { Text + Switch }   ← inline toggle
    Spacer(${ui.spacing.grouped.row.gap})
    Row(16×${ui.padding.grouped.toggle.vertical} pad) { Text + Switch }   ← more toggles
    
    if (checked) {
        Spacer(${ui.spacing.grouped.before-expander})
        Box(pad h=16) {
            SettingsExpander(label) {
                Spacer(8dp)
                … content (see §2.4)
            }
        }
        Spacer(${ui.spacing.grouped.after-expander})   ← after last expander (4dp)
    }
}
```

### 2.4 Inside-Expander Content

🔴 **Limit encapsulation — prefer a collapsible section.** Depth is capped at `card → expander → flat sections`. When a card accumulates many related controls, split them into a collapsible `SettingsExpander` section rather than nesting another card. Collapsible = secondary detail; primary toggles stay inline. Expander content is a flat sequence of sections — never a sub-card, and never a `SettingsSliderGroup` around a `RangeSlider`.

**Expander state:** open state lives in `SettingsViewModel.expanderStates` — a `mutableStateMapOf<String, Boolean>` keyed by a stable per-expander id. Shared across the four tabs and preserved across rotation and settings reopen for the whole app session; cleared when the app exits, so every expander is collapsed on fresh launch. Never use local `remember`/`rememberSaveable` state for an expander.

All expander content uses the **same outlined card surface** — whether a slider group or inline content:

| Token | Value | Role |
|---|---|---|
| `ui.nested.card.bg` | `#0DFFFFFF` (5% white) | Subtle depth below parent |
| `ui.nested.card.border` | `#40FFFFFF` (25% white) | Nesting indicator |

> Why not `uiCardBackground`? Stacking 15%+15% white = ~28% effective — too light for `#1565C0` accent contrast.

**2.4a — Sliders:** `SettingsSliderGroup(nested = true)` — provides the nested surface automatically. Pad: 16×2dp.

```kotlin
SettingsSliderGroup(nested = true) {
    SliderRowContent(…)
    SliderRowDivider()
    SliderRowContent(…)
}
```

**2.4b — Text / toggles / swatches:** Build inline with the same tokens:

```kotlin
Column(
    Modifier.fillMaxWidth().clip(12dp)
        .background(${ui.nested.card.bg})
        .border(1.dp, ${ui.nested.card.border}, 12dp)
        .padding(16dp, ${ui.padding.content.comfortable})
) { /* content */ }
```

### 2.5 Expander Labels

`SettingsExpander` defaults: `uiSettingsTextPrimary`, 16sp, Medium — matches the toggle (`SettingsToggleRow`) and slider (`SliderRowContent`) label font. Never override `labelStyle` per call site.

### 2.6 Section Dividers

**Visible divider** (`#26FFFFFF`, 6dp gap above/below) between distinct content blocks inside a card (e.g., "Colors" vs sliders in Track settings; toggle-only cards such as Regenerate Layers). **Spacer only** (8dp) between simple toggle rows that are not sections (e.g., Categories).

```
Spacer(6.dp)
Box(Modifier.fillMaxWidth().height(1.dp).background(0x26FFFFFF))
Spacer(6.dp)
```

**Example — Regulated zones card (merged expander):**

```
┌─ Regulated zones ────────────────────────────────┐
│  Show regulated zones                    [Switch] │
│                                                    │
│  ▼ Regulated zones settings                       │
│  ┌──────────────────────────────────────────┐     │
│  │  Info text visible                [Switch]│     │
│  │  ─────────────────────────────────────   │     │  ← divider (§2.6)
│  │  Boat length slider                      │     │
│  └──────────────────────────────────────────┘     │
│                                                    │
│  ▼ Categories                                      │  ← expander 2
│  └──────────────────────────────────────────┘     │
└────────────────────────────────────────────────────┘
```

Expander label: `"Regulated zones settings"`. Both toggles and sliders live in the same nested card, separated by a visible divider (§2.6).

### 2.7 Segmented Selector

For 2–3 exclusive choices (e.g. Uniform / Speed-based). Same pattern as the language picker:

```
Row(uiCardBackground, 12dp radius, 6dp pad, 6dp gaps) {
    option → Box(weight 1f, 8dp radius,
                 bg = selected ? uiSettingsAccent : uiSettingsDivider,
                 vertical pad 10dp, centered) {
        Text(14sp, selected ? Bold/primary : Normal/muted)
    }
}
```

🔴 Do not hand-roll two `Text` rows with `clickable` — use this control.

### 2.8 RangeSlider (double-thumb)

Render as a **direct section** — header + description + value (`ui.settings.value.text`, right-aligned) + `RangeSlider` — never wrapped in `SettingsSliderGroup` (that double-nests the surface).

- **Linear** (e.g. opacity 0–100): plain `valueRange` + `steps`.
- **Two-thumb opacity** (300 m band): left thumb = zone content opacity, right thumb = border opacity; `value = fill..border`; commit on release via `onValueChangeFinished`.
- **Log-scale** for octave-spanning ranges (e.g. gap 4–640, speed 2–64): map position 0..1 → value with `lo × (hi/lo)^pos` (`logSliderFromValue` / `logSliderToValue`); ~24 positions.

**Opacity convention (app-wide):** all opacity/transparency settings use **OPACITY** semantics — **higher = more visible/opaque** (0% = invisible, 100% = opaque). Never expose "transparency" (inverted) wording. Label the control **"Opacity"** and format two-thumb values as **"Fill X% · Border Y%"** (fill = inner/zone content, border = outer stroke). Applies to tracks, marker halo, 300 m band, and low-depth warning.

### 2.9 Header Hierarchy

- `SectionHeader` — 17sp bold, `ui.settings.accent`; top-level sections only. `uppercase = true` (default) renders ALL-CAPS with 1sp letter-spacing; `uppercase = false` renders title case ("Layers", "Navigation") with no letter-spacing.
- `SubSectionHeader` — 16sp SemiBold, `ui.settings.text.muted` + optional 13sp `ui.settings.text.secondary` description; the standard header for any sub-section inside a card/expander.
- **Card description** (Layers tab) — 13sp `ui.settings.text.muted`, inside the card, horizontal 16dp pad with no extra vertical padding, followed by a 4dp spacer before the first expander.

---

## 3. Spacing Quick Reference

| Context | Token | Value |
|---|---|---|
| Card vertical (top/bottom) | `ui.padding.card.vertical` | 8dp |
| Card→card (standalone) | `ui.spacing.card.gap` | 12dp |
| Section→section | `ui.spacing.section.gap` | 24dp |
| Header→first card | `ui.spacing.header.bottom` | 8dp |
| Inline toggle→toggle | `ui.spacing.grouped.row.gap` | 8dp |
| Before expander (in grouped card) | `ui.spacing.grouped.before-expander` | 8dp |
| Expander header row (top/bottom) | `ui.padding.expander.vertical` | 6dp |
| Expander→content | header+8dp spacer | 8dp |
| Last expander→card close | `ui.spacing.grouped.after-expander` | 4dp |
| Visible divider gap (above/below) | `ui.divider.gap` | 6dp |

Full token list: [`ui-tokens.properties`](../app/src/main/assets/ui-tokens.properties).

---

## 4. Anti-Patterns

- ❌ Inner content card using `uiCardBackground` (stacked 15% white)
- ❌ Per-call `labelStyle` on `SettingsExpander`
- ❌ Visible dividers between top-level cards (use spacer)
- ❌ `SliderRowContent(label="", …)` (use inline Row+Slider)
- ❌ `RangeSlider` wrapped in `SettingsSliderGroup` (double-nested surface)
- ❌ Hand-rolled two-`Text` toggle rows (use the segmented selector, §2.7)
- ❌ Mixed header styles in one card (use `SubSectionHeader` consistently, §2.9)
- ❌ Nesting deeper than `card → expander → sections` (§2.4)
- ❌ Local `remember`/`rememberSaveable` state for expander open state (use `SettingsViewModel.expanderStates`, §2.4)

---

## 5. Non-Settings Surfaces

### 5.1 Drawer Cards (`MenuDrawerOverlay`)

Same `uiCardBackground` + 12dp radius. Rows: 16×10dp pad, `heightIn(min = 48dp)` touch target. Between cards: `${ui.spacing.header.bottom}`.

### 5.2 List Item Cards (`TrackHistoryOverlay` + `MarkerManagementOverlay`)

Unified pattern documented in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker). Shell: `Row(height(IntrinsicSize.Min), clip(12dp), uiCardBackground)` + `Box(4dp, fillMaxHeight, accentColor)` + `Column(weight 1f, pad 8×4dp)`. Shared tokens for header (11sp muted), title (15sp SemiBold white), detail (14sp white), comment (13sp muted), action icons (`IconButton(36dp)` + `Icon(24dp, tint=ButtonColors.icon)`). Per-type variations in accent color source and metadata format.

### 5.3 Dashboard Tiles (`DashboardCard`)

Three-line `Column` inside a rounded card (`8dp` radius, `4×2dp` pad, `uiCardBackground`). Used in the 2×2 dashboard grid (Distance, Zone, Depth, Speed).

```
┌────────────────────┐
│  TITLE (15.sp)     │  ← SemiBold, textPrimary (#E0E0E0), uppercase
│                    │
│  VALUE (auto)      │  ← AutoSizeValue, weight(1f), Bold, textPrimary, 14–64.sp
│                    │
│ subtitle (13.sp)   │  ← Medium, textMutedBright (#B0BEC5)
└────────────────────┘
```

| Line | fontSize | weight | color | Token |
|------|:--------:|:------:|-------|-------|
| Title | 15.sp | SemiBold | `#E0E0E0` | `ui.dashboard.text.primary` |
| Value | auto (14–64.sp) | Bold | `#E0E0E0` | `ui.dashboard.text.primary` |
| Subtitle | 13.sp | Medium | `#B0BEC5` | hardcoded in `DashboardColors.textMutedBright` |

🔴 The value uses `Modifier.weight(1f)` — it fills all remaining space after title + subtitle measure. Any font size increase on title or subtitle reduces the value's auto-sized ceiling. Keep title + subtitle combined height ≤ ~34dp to preserve value readability.

Source: [`DashboardPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) — `DashboardCard` composable, `DashboardColors` object.
---

### 5.4 Map Overlay Highlight — Dual-Outline Pattern

When a map element (track polyline, marker geometry) enters a highlighted state (click-n-move), it renders a **dark under-stroke before the gold geometry** — a drop-shadow technique that guarantees contrast against any map background.

**Constants** (in [`MarkerOverlay.kt`](../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt)):

| Constant | Value | Role |
|----------|-------|------|
| `COLOR_HIGHLIGHT_UNDER` | `0xCC000000` | Dark under-stroke (black at 80% opacity) |
| `COLOR_HIGHLIGHT` | `0xFFFFD700` | Gold core stroke |
| `HIGHLIGHT_UNDER_STROKE_ADD` | `6f` | Extra width added to under-stroke vs core |

**Render-order contract:** Under-stroke FIRST, gold SECOND. The dark layer is wider (`baseStrokeWidth + HIGHLIGHT_UNDER_STROKE_ADD`) so it peeks out from behind like a frame. No click listeners on under-stroke elements — interaction only on gold layer.

**Applied to these geometry types:**

| Geometry | Under-stroke | Gold |
|----------|-------------|------|
| Pin dot | 1.5x radius dark dot (`_ul` suffix, no click) | Normal dot |
| Circle outline | Dark polyline at `4f x multiplier + 6f` | Gold polyline at `4f x multiplier` |
| Corridor centerline | Dark polyline at `2f x multiplier + 6f` | Gold polyline at `2f x multiplier` |
| Corridor parallels | Dark left+right at `strokeWidth + 6f` | Gold left+right at `strokeWidth` |
| Corridor caps | Dark p1+p2 caps at `strokeWidth + 6f` | Gold p1+p2 caps at `strokeWidth` |

**How to add to a new geometry type:**

1. Add `isHighlighted: Boolean = false` parameter (defaults to off — no behavior change for non-highlighted elements)
2. When `isHighlighted` is true, emit dark versions BEFORE gold versions using `COLOR_HIGHLIGHT_UNDER` and `baseWidth + HIGHLIGHT_UNDER_STROKE_ADD`
3. Compute `isHighlighted` at call site: `val isHighlighted = element.id == highlightedElementId`

🔴 Never apply under-strokes to proximity previews or fill polygons — highlights only.

Source: [`MarkerOverlay.kt`](../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) + track polyline rendering in [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt).

---

### 5.5 Top-Left Status Icons (`GpsStatusIcon` / `TrackStatusIcon` / `EarthWaterIcon` / `LockScreenButton`)

44×44dp rounded square (8dp radius) with a 22sp emoji glyph, one slot each in the top-left
status row ([`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)). Every
icon family declares its own colour tokens in
[`colors.properties`](../app/src/main/assets/colors.properties) (alias-interpolated from the
semantic palette) and exposes them via
[`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt).

**Visual recipe:**

| Aspect | Value |
|---|---|
| Size / radius | 44dp / 8dp |
| Glyph | emoji, 22sp |
| Active bg alpha | `status.*.alpha.active` = 0.75 |
| Dimmed bg alpha | `status.*.alpha.dimmed` = 0.50 |
| Inactive content alpha | 0.50 (emoji dimmed) |
| Inactive bg | `${semantic.inactive}` (#33FFFFFF) |

**State → colour mapping:**

| Icon | Off / inactive | Active states |
|---|---|---|
| GPS | `semantic.inactive` | acquiring=`semantic.caution`, healthy=`semantic.compliant`, idle=`semantic.info`, stale/weak=`semantic.danger` |
| Tracking | `semantic.inactive` | moving=`semantic.compliant`, idle=`semantic.info` |
| Earth/Water | `semantic.inactive` | water=`semantic.info`, land=`semantic.compliant` |
| Screen lock | `semantic.inactive` (📵) | locked=`semantic.info` (📵) |

> Exception: `EarthWaterIcon` keeps its emoji at full alpha in the inactive state (no
> contentAlpha dimming) and reuses `statusGpsAlphaActive` for its active bg alpha.

🔴 New icons must: declare a `status.<name>.*` token family, parse it in `AppConfig`, and follow
the alpha/contentAlpha recipe above — never hardcode hex in the composable.

**Lock-screen overlay placement:** the lock toggle sits right of the Earth/Water icon in the
top-left status row (GPS → Tracking → Earth/Water → Lock → Recenter). When locked, the overlay
recreates the same controls above the input-blocking scrim (duplicate unlock button, `ZoomControls`,
`LockBanner`); those duplicates must live inside a `Box` padded exactly like `MapContent`'s
dashboard padding (portrait: bottom = `portraitDashboardHeight`; landscape: start =
`landscapeDashboardWidth`) so they align over the originals in both orientations. The locked
zoom controls accept a double-tap only (single splash taps are ignored).

---

## 6. Global Layout Rules

### 6.1 Screen Bottom Padding

Add `Modifier.padding(bottom = 10.dp)` on the root `Box` in the screen composable to prevent content from touching the bottom edge. This applies to all screens — the app uses `enableEdgeToEdge()` so the map draws full-screen behind the nav bar, but overlay content needs breathing room.

```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .padding(bottom = 10.dp)   // ← global bottom breathing room
) {
    // screen content
}
```

🔴 Do not apply padding at a higher level (e.g. `Surface` in `MainActivity`) — it would offset the full-screen map. Apply at the screen root `Box` only.
