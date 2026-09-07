<!-- scope: reference -->
# UI Component Guidelines

> **Canonical patterns** for cards, expanders, toggles, sliders, and drawers across the app.
> Code wins over doc — when they disagree, update this file.

> **Tokens:** [`ui.properties`](../app/src/main/assets/ui.properties) (dimensions) +
> [`colors.properties`](../app/src/main/assets/colors.properties) (colors).
> All `${ui.*}` references below resolve to those files.

---

## 1. Decision Flow

```
New setting?
  ├─ Standalone toggle?          → SettingsToggleRow           (§2.1)
  ├─ Standalone slider?          → SliderRowContent on a Card   (§2.2)
  ├─ Toggle + sub-settings?      → Card + Expander + NestedCard (§2.3)
  │   └─ Sub controls (any type) → NestedCard                  (§2.4)
  ├─ Exclusive 2–3 choice?       → Segmented selector            (§2.7)
  ├─ Double-thumb value range?   → RangeSlider section           (§2.8)
  ├─ Feature w/ sub-settings?    → Grouped card: feature toggle + sibling expander (§2.3)
  ├─ Toggle group + slider?      → Grouped card (§2.3)
  └─ Drawer/Track card?          → Same card surface, specific rows (§5)
```

---

## 2. Components

### 2.0 Card Surface Primitive (authority)

The **card surface** is the shared primitive behind every card in the app — settings cards,
drawer cards, list-item cards, and popup section cards. This section is the single source of
truth for the surface; other docs point here instead of restating it.

| Aspect | Value |
|--------|-------|
| Background | `uiCardBackground` |
| Corner radius | 12dp |
| **Wide** density padding | 16×10dp — simple toggle/nav rows with single controls (e.g. menu slide panel) |
| **Tight** density padding | 8×4dp — data-dense cards (track history stats grid, wizard sliders, marker details) |

```kotlin
// Settings Main card — the `Card` composable (20% white, 12dp radius, 8dp vertical pad)
Card { /* description, expanders, and/or standalone controls */ }

// Wide (simple rows) — non-settings surfaces
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 16.dp, vertical = 10.dp)
) { /* simple rows */ }

// Tight (data-dense)
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 8.dp, vertical = 4.dp)
) { /* dense content */ }
```

> Drawer-specific row-height / divider-gap rules live in [`ui-drawer-guidelines.md` §8](ui-drawer-guidelines.md#8-card-pattern);
> the list-item card shell (accent bar variant) lives in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker).

### 2.1 Standalone Toggle — `SettingsToggleRow`

Self-contained card (`uiCardBackground`, 12dp radius, 16×8dp pad). Gap between: `${ui.spacing.card.gap}`.

🔴 Never nest inside a grouped card — it IS a card.

### 2.2 Standalone Slider — `SliderRowContent` on a `Card`

A standalone slider renders as bare `SliderRowContent` directly on a `Card` (no wrapper box of its own). Gap: `${ui.spacing.card.gap}`.

```kotlin
Card {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SliderRowContent(…)
    }
}
```

### 2.3 Grouped Card — `Card` + `Expander` + `NestedCard`

A section surface is a `Card` (20% white, 12dp radius) holding **inline toggle rows** (not `SettingsToggleRow`) and/or `Expander`s:

```
Card {
    Row(16×${ui.padding.toggle.vertical} pad) { Text + Switch }   ← inline toggle
    Spacer(${ui.spacing.grouped.row.gap})
    Row(16×${ui.padding.toggle.vertical} pad) { Text + Switch }   ← more toggles

    Spacer(${ui.spacing.grouped.after-expander})
    Box(pad h=16) {
        Expander(label) {
            Spacer(8dp)
            NestedCard { … content (see §2.4) }
        }
    }
    Spacer(${ui.spacing.grouped.after-expander})   ← after last expander (4dp)
}
```

🔴 **No settings visibility is conditional on another setting's state.** Settings are always shown; a toggle controls *behavior*, never *visibility*. E.g. the GPS-tuning expander is always visible regardless of GPS mode — the GPS mode toggle only controls whether GPS tuning takes effect, not whether the expander renders. Do not wrap a setting or expander in `if (someOtherSetting)`.

### 2.4 Inside-Expander Content — `NestedCard`

🔴 **Limit encapsulation — prefer a collapsible section.** When a card accumulates many related controls, split them into a collapsible `Expander` section rather than nesting another card. Collapsible = secondary detail; primary toggles stay inline.

**Inception rule (applies to ALL controls):** a settings section is at most **Card → Expander → NestedCard → controls**.
- **Card** — top-level section surface (`uiCardBackground`, 20% white, 12dp radius).
- **Expander** — the collapsible disclosure row; it has **no box of its own** and sits directly on the Card.
- **NestedCard** — the single nested container revealed when the Expander is open (`ui.nested.card.bg` `#0DFFFFFF` + `ui.nested.card.border` `#40FFFFFF`). It holds the controls.
- Any control — toggles, one-knob sliders, two-knob `RangeSlider`s, text, swatches — may sit inside the NestedCard.
- **Forbidden:** a card inside the NestedCard (a third level), or using a full `uiCardBackground` card as the NestedCard.

**Expander state:** open state lives in `SettingsViewModel.expanderStates` — a `mutableStateMapOf<String, Boolean>` keyed by a stable per-expander id. Shared across the four tabs and preserved across rotation and settings reopen for the whole app session; cleared when the app exits, so every expander is collapsed on fresh launch. Never use local `remember`/`rememberSaveable` state for an expander.

All expander content uses the **same `NestedCard` surface** — a single uniform container for every control type:

| Token | Value | Role |
|---|---|---|
| `ui.nested.card.bg` | `#0DFFFFFF` (5% white) | Subtle depth below parent |
| `ui.nested.card.border` | `#40FFFFFF` (25% white) | Nesting indicator |

> Why not `uiCardBackground`? Stacking 20%+20% white = ~36% effective — too light for `#1565C0` accent contrast.

```kotlin
Expander(label, expanded, onToggle) {
    Spacer(8dp)
    NestedCard {
        SliderRowContent(…)   // or toggles / RangeSliders / text / swatches
        SectionDivider()
        SliderRowContent(…)
    }
}
```

### 2.5 Expander Labels

`Expander` defaults: `uiSettingsTextPrimary`, 16sp, Medium — matches the toggle (`SettingsToggleRow`) and slider (`SliderRowContent`) label font. Never override `labelStyle` per call site.

### 2.6 Section Dividers

**Visible divider** (`uiSettingsDivider`, 6dp gap above/below, 16dp horizontal inset) between distinct content blocks inside a card (e.g., "Colors" vs sliders in Track settings; toggle-only cards such as Regenerate Layers). **Spacer only** (8dp) between simple toggle rows that are not sections (e.g., Categories).

```
Spacer(6.dp)
Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(uiSettingsDivider))
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

Expander label: `"Regulated zones settings"`. Both toggles and sliders live in the same `NestedCard`, separated by a visible divider (§2.6).

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

Render as a **direct section** — header + description + value (`ui.settings.value.text`, right-aligned) + `RangeSlider`. A `RangeSlider` may sit directly on a `Card`, or inside the `NestedCard` of an `Expander` alongside one-knob sliders. What is forbidden is a card inside the `NestedCard` (see §2.4 inception rule).

- **Linear** (e.g. transparency 0–100): plain `valueRange` + `steps`.
- **Two-thumb transparency** (300 m band): left thumb = border (strong, low transparency), right thumb = fill (faint, high transparency); `value = border..fill`; commit on release via `onValueChangeFinished`.
- **Log-scale** for octave-spanning ranges (e.g. gap 4–640, speed 2–64): map position 0..1 → value with `lo × (hi/lo)^pos` (`logSliderFromValue` / `logSliderToValue`); ~24 positions.

**Transparency convention (app-wide):** all opacity/transparency settings use **TRANSPARENCY** semantics — **0 = opaque (fully visible), 100 = invisible**. Never expose "opacity" (inverted) wording. Label the control **"Transparency"** and format two-thumb values as **"Border X% · Fill Y%"** (border = outer stroke/strong, fill = inner/zone content/faint). Applies to tracks, marker halo, 300 m band, and low-depth warning.

### 2.9 Header Hierarchy

- `SectionHeader` — 17sp bold, `ui.settings.accent`; top-level sections only. `uppercase = true` (default) renders ALL-CAPS with 1sp letter-spacing; `uppercase = false` renders title case ("Layers", "Navigation") with no letter-spacing.
- `SubSectionHeader` — 16sp SemiBold, `ui.settings.text.muted` + optional 13sp `ui.settings.text.secondary` description; the standard header for any sub-section inside a card/expander.
- **Card description** (Layers tab) — 13sp `ui.settings.text.muted`, inside the card, horizontal 16dp pad with no extra vertical padding, followed by a 4dp spacer before the first expander.

### 2.10 Popup Styling (canonical)

Filter/sort and other popup menus follow the settings-page hierarchy. This is the canonical
popup-styling spec (moved from `ui-lists-guidelines`).

```
┌─ Popup → Surface (uiSettingsBackground, 12dp, 1dp 0x40FFFFFF border) ─┐
│  Section Title (SubSectionHeader style)                                 │
│  ┌─ Card → Surface (uiCardBackground, 12dp) ─────────────────────────┐ │
│  │  Row (16dp h-pad, 2dp v-pad): checkmark box (24dp) + text         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  Next Section Title                                                     │
│  ┌─ Card ... ────────────────────────────────────────────────────────┐ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

| Token | Value | Role |
|-------|-------|------|
| Popup bg | `uiSettingsBackground` | Outer Surface |
| Popup border | `0x40FFFFFF`, 1dp | Settings expander border style |
| Card bg | `uiCardBackground` | Per-section card |
| Section title | `uiDashboardTextMuted`, 16sp, SemiBold | SubSectionHeader style |
| Row text | `uiSettingsTextPrimary`, 15sp, Medium (selected: SemiBold) | |
| Checkmark | `uiSettingsAccent`, 16sp, SemiBold | ✓ for selected |
| Row v-padding | 2dp | Tight — matches settings toggle rows |
| Card v-padding | 8dp | |
| Card gap | 4dp | `Arrangement.spacedBy(4.dp)` |

All popup icons use `ButtonColors.icon` tint + `ButtonColors.iconSizeDp` (28dp) + `.alpha(activeAlpha/inactiveAlpha)` per [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt).

---

## 3. Spacing Quick Reference

| Context | Token | Value |
|---|---|---|
| Card vertical (top/bottom) | `ui.padding.card.vertical` | 8dp |
| Card→card (standalone) | `ui.spacing.card.gap` | 12dp |
| Section→section | `ui.spacing.section.gap` | 24dp |
| Header→first card | `ui.spacing.header.bottom` | 8dp |
| Inline toggle→toggle | `ui.spacing.grouped.row.gap` | 8dp |
| Before expander (in grouped card) | `ui.spacing.grouped.after-expander` | 4dp |
| Expander header row (top/bottom) | `ui.padding.expander.vertical` | 6dp |
| Expander→content | header+8dp spacer | 8dp |
| Last expander→card close | `ui.spacing.grouped.after-expander` | 4dp |
| Label→control (row) | `ui.spacing.label.control` | 16dp |
| Visible divider gap (above/below) | `ui.divider.gap` | 6dp |
| Drawer-internal card gap | — | 8dp |

Full token list: [`ui.properties`](../app/src/main/assets/ui.properties).

---

## 4. Anti-Patterns

- ❌ A card inside the `NestedCard` (a third level), or a full `uiCardBackground` card used as the `NestedCard` (stacked 20% white) — §2.4 inception rule
- ❌ Per-call `labelStyle` on `Expander`
- ❌ Visible dividers between top-level cards (use spacer)
- ❌ `SliderRowContent(label="", …)` (use inline Row+Slider)
- ❌ Hand-rolled two-`Text` toggle rows (use the segmented selector, §2.7)
- ❌ Mixed header styles in one card (use `SubSectionHeader` consistently, §2.9)
- ❌ Nesting deeper than `Card → Expander → NestedCard` (§2.4)
- ❌ Local `remember`/`rememberSaveable` state for expander open state (use `SettingsViewModel.expanderStates`, §2.4)

---

## 5. Non-Settings Surfaces

### 5.1 Drawer Cards (`MenuDrawerOverlay`)

Same `uiCardBackground` + 12dp radius. Rows: 16×10dp pad, `heightIn(min = 48dp)` touch target. Between cards: `${ui.spacing.header.bottom}`.

### 5.2 List Item Cards (`TrackHistoryOverlay` + `MarkerManagementOverlay`)

Canonical list-item card spec (accent-bar shell, shared tokens, per-type variations) lives in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker).

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
