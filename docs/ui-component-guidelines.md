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
  ├─ One control (toggle/slider)? → control row on a Card         (§2.1, §2.2)
  ├─ Several related controls?    → Card + rows + SectionDividers  (§2.3)
  ├─ Control + sub-settings?      → Card + Expander + NestedCard   (§2.3)
  │   └─ Sub controls (any type) → NestedCard                     (§2.4)
  ├─ Exclusive 2–3 choice?        → SegmentedRow                   (§2.7)
  ├─ Double-thumb value range?    → RangeSliderRow                 (§2.8)
  └─ Drawer/Track card?           → Same card surface, specific rows (§5)
```

**Naming rule.** `<Control>Row` for a row, `<Thing>Group` for a group of rows. No `Settings`
prefix; name after the **control**, never the widget or the use case.

**Container-owned inset.** The container pads horizontally, the row pads **vertically only**.
`CardArea` and `NestedCard` own the horizontal inset (`ui.padding.card.horizontal`) — no child, row or
otherwise, adds its own horizontal padding (§2.0).

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
| **Wide** density padding | 16×10dp — simple toggle/nav rows with single controls (non-settings surfaces) |
| **Tight** density padding | 8×4dp — data-dense cards (track history stats grid, wizard sliders, marker details) |

```kotlin
// Settings Main card — the `CardArea` composable (20% white, 12dp radius, 16dp horizontal + 8dp vertical pad)
CardArea { /* description, expanders, and/or standalone controls */ }

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

**Horizontal inset is owned by the container.** The settings `CardArea`/`NestedCard` apply
`${ui.padding.card.horizontal}` (16dp) horizontally; **no child inside them may add horizontal
padding of its own** — rows pad vertically only (§1), and `SectionDivider()` carries no inset
(§2.6). The non-settings `Column` samples above show their own horizontal padding because they *are*
the container.

> Drawer-specific row-height / divider-gap rules live in [`ui-drawer-guidelines.md` §8](ui-drawer-guidelines.md#8-card-pattern);
> the list-item card shell (accent bar variant) lives in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker).

### 2.1 Toggle Row — `ToggleRow`

A toggle is a **row**, never a card. The rendering is identical whether the card holds one row or ten:

| Element | Value |
|---------|-------|
| Label | 16sp Medium, `uiTextPrimary` (`ui.font.toggle.size`) |
| Description | 13sp, `uiTextMuted` (`ui.font.desc.size`) — **optional**; omit the parameter for label-only rows |
| Leading icon (`leadingIcon`) | **optional** `@Composable (() -> Unit)?` slot rendered **before** the label column with the standard 8dp gap (precedent: `CategoryToggleGroup`'s icon/strike overlay) |
| Label→control gap | `${ui.spacing.label.control}` (16dp) |
| Control | `Switch` with accent colours (`uiAccent`) |
| Row padding | **vertical only** — 2dp (`${ui.padding.toggle.vertical}`); the `CardArea`/`NestedCard` owns the 16dp horizontal inset (§2.0) |
| Switch colour | `checkedColor` (default `uiAccent`); pass a dynamic value to recolour a live status row (e.g. GPS) — never `remember` it |

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),   // vertical only — container owns horizontal
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    leadingIcon?.let { it(); Spacer(Modifier.width(8.dp)) }        // optional slot
    Column(Modifier.weight(1f)) {
        Text(label,       color = uiTextPrimary, fontSize = 16.sp, fontWeight = Medium)
        if (description != null) Text(description, color = uiTextMuted, fontSize = 13.sp)
    }
    Spacer(Modifier.width(16.dp))
    Switch(checked, onCheckedChange, colors = …accent…)
}
```

The row carries **no** background, radius or surface of its own — the enclosing `CardArea` owns that (§2.3). A one-toggle card is therefore plain `CardArea { ToggleRow(…) }`; nothing special-cases it.

### 2.2 Slider Row — `SliderRow`

Same model as the toggle (§2.1): a slider is a **row**, not a card — label (16sp Medium `uiTextPrimary`), description (13sp `uiTextMuted`), right-aligned value (bold `ui.value.text`, `${ui.font.value.size}`) and the slider — with **no surface of its own**; the enclosing `CardArea`/`NestedCard` owns the box (§2.3).

Examples in Settings: Marker halo size and Point/icon zoom (Layers → Markers), Idle threshold / Min duration / Dedup radius (Markers), EMODnet cutoff (Layers → Depth), re-display distance and time (Navigation → Auto-show zones), boat offset (Navigation → Automatic map offset), recenter distance (Position), window and adaptive distance (System → Power saving), FPS (System).

Row padding: **vertical only** — like every row it carries **no horizontal padding** of its own; the container owns the inset (§2.0). Label-left / value-right share one line; for a **two-thumb** slider use `RangeSliderRow` (§2.8).

### 2.3 Card = rows + sections — `CardArea`, `SectionDivider`, `Expander`

A **`CardArea`** is one surface (20% white, 12dp radius, `${ui.padding.card.horizontal}` = 16dp horizontal + `${ui.padding.card.vertical}` = 8dp vertical padding) holding **1..N control rows**. The rows are the content; the card is the box (§2.1, §2.2) and it owns the horizontal inset for every child inside it (§2.0). The shared stencils (`CardArea`, `ToggleRow`, `SectionHeader`, `SectionDivider`) live in `ui/components` — non-Settings surfaces (e.g. the Menu drawer) reuse them rather than re-implementing.

The card is divided into **sections**, defined **functionally**: controls that belong together form one section; a control that stands alone is its own section.

- **Between sections** → `SectionDivider()` (§2.6).
- **Within a section** → `${ui.spacing.grouped.row.gap}` (8dp) between rows, no divider.

A card may additionally reveal optional or advanced content through one or more `Expander`s:

```
CardArea {
    ToggleRow(…)                               ← section 1
    SectionDivider()
    ToggleRow(…)
    ToggleRow(…)                               ← section 2: two related rows, 8dp apart

    Spacer(${ui.spacing.grouped.after-expander})
    Expander(label) {                          ← no h-pad wrapper: the Card owns the inset (§2.0)
        Spacer(8dp)
        NestedCard { … content (see §2.4) }
    }
    Spacer(${ui.spacing.grouped.after-expander})   ← after last expander (4dp)
}
```

**Worked examples:** Coastline = `CardArea { ToggleRow(…) }` — one section, no divider. Orientation aids = three rows with two `SectionDivider`s — one section per control. Auto-show zones = two rows 8dp apart (one section) + `SectionDivider` + one row (second section).

🔴 **No settings visibility is conditional on another setting's state.** Settings are always shown; a toggle controls *behavior*, never *visibility*. E.g. the GPS-tuning expander is always visible regardless of GPS mode — the GPS mode toggle only controls whether GPS tuning takes effect, not whether the expander renders. Do not wrap a setting or expander in `if (someOtherSetting)`.

### 2.4 Inside-Expander Content — `NestedCard`

**Depth cap (law):** a settings section is at most **`CardArea` → one `Expander` → `NestedCard` → controls**. `NestedCard` is a *surface treatment*, not a nesting tier — it is the paint on the panel the `Expander` reveals. Never place a card (or any full `uiCardBackground` surface) inside a `NestedCard`.

**Preference (not law):** when one card accumulates many related controls, prefer revealing the secondary ones behind an `Expander` rather than growing the card. Primary controls stay inline; secondary detail collapses. There is no count threshold — judge it by whether the content is optional.

- **Card** — top-level section surface (`uiCardBackground`, 20% white, 12dp radius).
- **Expander** — the collapsible disclosure row; it has **no box of its own** and sits directly on the Card.
- **NestedCard** — the single nested container revealed when the Expander is open (`ui.nested.card.bg` `#0DFFFFFF` + `ui.nested.card.border` `#40FFFFFF`). It holds the controls.
- Any control — toggles, one-knob sliders, two-knob `RangeSlider`s, text, swatches — may sit inside the NestedCard.
- **Forbidden:** a card inside the NestedCard (a third level), or using a full `uiCardBackground` card as the NestedCard.

**Single colour section (`SingleColorSubSection`):** a NestedCard group holding **exactly one** colour control keeps a
`SubSectionHeader`-style title on its own line, and the **description line carries the 24dp colour swatch on its
trailing edge** (tappable → colour picker). If a single-colour section has no description, the swatch sits on the
title line's trailing edge instead. Never a standalone swatch row, and never an empty-label `ColorRow` (an
empty-label row wastes a full-width line for a tiny square). `SubSectionHeader` + labeled `ColorRow` rows are
reserved for **multi-colour** groups under one heading (e.g. Marker halo `Colors` → Pinned / Not pinned). E.g. the
300 m band "Zone color" is a `SingleColorSubSection`.

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
        SliderRow(…)   // or toggles / RangeSliders / text / swatches
        SectionDivider()
        SliderRow(…)
    }
}
```

### 2.5 Expander Labels

`Expander` labels are fixed: `uiTextPrimary`, 16sp, Medium — matching the toggle row (§2.1) and slider row (§2.2) label font. There is **no** per-call style parameter; the style lives inside the composable.

### 2.6 Section Dividers

A card is divided into **sections**, defined **functionally**: controls that belong together form one section; a control that stands alone is its own section.

- **Between sections** → the visible divider: `uiDividerColor`, `${ui.divider.gap}` (6dp) above/below, and **no inset of its own** — the `CardArea`/`NestedCard` supplies the horizontal inset (§2.0).
- **Within a section** → `${ui.spacing.grouped.row.gap}` (8dp) between rows. No divider.

```
Spacer(6.dp)
Box(Modifier.fillMaxWidth().height(1.dp).background(uiDividerColor))   // inset comes from the container
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

### 2.7 Segmented Row — `SegmentedRow`

For 2–3 exclusive choices (e.g. Language, Arrow density, GPS frequency). One generic composable serves
all of them: `SegmentedRow(options, selected, onSelect, captions = null)`.

- **Connected segments** — no gaps between them; only the **outer ends** are rounded (`ui.radius.card`).
- **Unselected segments are outlined** (1dp `uiDividerColor`); the selected segment is **accent-filled**
  (`uiAccent`) with `uiTextPrimary` Bold text.
- **Accessibility** — `selectableGroup()` on the row plus `Role.RadioButton` per segment, so the control
  is announced as "n of m, selected" instead of as unrelated buttons.
- **`captions`** (optional) — one 12sp `uiTextMuted` line under each segment (per-stop numbers,
  e.g. GPS frequency "2 s · 5 m"), each weighted like its segment.
- **Surface-free** — the row paints **no** surface of its own (only its 1dp outline, no outer padding);
  the call site supplies the `CardArea`/`NestedCard` (§2.0).

🔴 Do not hand-roll two `Text` rows with `clickable` — use this control. Do not paint a surface on it.

### 2.8 Range Slider Row — `RangeSliderRow`

The two-thumb row: optional label/description, a **mandatory** value line, and the two-thumb slider.

- **Value line — mandatory**, on its **own line** below the label, **right-aligned and bold**
  (`ui.value.text`, `${ui.font.range.size}` = 14sp). A `RangeSlider` is never rendered without it.
- **Label / description optional** — pass `label = null` where a `SubSectionHeader` already supplies the
  heading (e.g. the 300 m band section).
- **No surface of its own** — it sits directly on a `CardArea` or inside an `Expander`'s `NestedCard`, which
  own the box and the inset (§2.0); the row pads vertically only. A card inside the `NestedCard` is
  forbidden (see §2.4 depth cap).

- **Linear** (e.g. transparency 0–100): plain `valueRange` + `steps`.
- **Two-thumb transparency** (300 m band): left thumb = border (strong, low transparency), right thumb = fill (faint, high transparency); `value = border..fill`; commit on release via `onValueChangeFinished`.
- **Log-scale** for octave-spanning ranges (e.g. gap 4–640, speed 2–64): map position 0..1 → value with `lo × (hi/lo)^pos` (`logSliderFromValue` / `logSliderToValue`); ~24 positions.

**Transparency convention (app-wide):** all opacity/transparency settings use **TRANSPARENCY** semantics — **0 = opaque (fully visible), 100 = invisible**. Never expose "opacity" (inverted) wording. Label the control **"Transparency"** and format two-thumb values as **"Border X% · Fill Y%"** (border = outer stroke/strong, fill = inner/zone content/faint). Applies to tracks, marker halo, 300 m band, and low-depth warning.

### 2.9 Header Hierarchy

- `SectionHeader` — top-level sections only. **One style app-wide:** sentence case ("Layers", "Navigation"), 18sp bold, `ui.accent`, no letter-spacing (`ui.font.section.size`). There is no casing variant.
- `SubSectionHeader` — 16sp SemiBold, `ui.text.primary` + optional 13sp `ui.text.secondary` description; the standard header for a **titled** sub-section inside a card/expander. Headings are white like every other heading — hierarchy comes from **weight + spacing**, not a dimmed colour.
- **`CardDescription`** — 13sp `ui.text.muted`, one lead-in sentence placed **inside the card, before its first control**; it owns its trailing 4dp spacer (`${ui.spacing.grouped.after-expander}`) and has **no horizontal inset of its own** now that the `CardArea` supplies it (§2.0).

**`CardDescription` vs `SubSectionHeader(title, description = …)`:** `CardDescription` = an explanation under a `SectionHeader`, dropped into the top of a card before its controls. `SubSectionHeader` = a **titled** sub-section **inside** a card/expander, whose 13sp `ui.text.secondary` description labels that group. Both are kept.

### 2.10 Popup Styling (canonical)

Filter/sort and other popup menus follow the settings-page hierarchy. This is the canonical
popup-styling spec (moved from `ui-lists-guidelines`).

```
┌─ Popup → Surface (uiBackground, 12dp, 1dp 0x40FFFFFF border) ─┐
│  Section Title (popup title style)                                      │
│  ┌─ CardArea → Surface (uiCardBackground, 12dp) ─────────────────────┐ │
│  │  Row (16dp h-pad, 2dp v-pad): checkmark box (24dp) + text         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  Next Section Title                                                     │
│  ┌─ Card ... ────────────────────────────────────────────────────────┐ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

| Token | Value | Role |
|-------|-------|------|
| Popup bg | `uiBackground` | Outer Surface |
| Popup border | `0x40FFFFFF`, 1dp | Settings expander border style |
| Card bg | `uiCardBackground` | Per-section card |
| Section title | `uiDashboardTextMuted`, 16sp, SemiBold | Popup-only: deliberately dimmer than the settings `SubSectionHeader`, which is `uiTextPrimary` (§2.9). Sharing the dashboard muted token here is a known token-scope wart — a future pass may migrate popups to `uiTextPrimary`. |
| Row text | `uiTextPrimary`, 15sp, Medium (selected: SemiBold) | |
| Checkmark | `uiAccent`, 16sp, SemiBold | ✓ for selected |
| Row v-padding | 2dp | Tight — matches settings toggle rows |
| Card v-padding | 8dp | |
| Card gap | 4dp | `Arrangement.spacedBy(4.dp)` |

All popup icons use `ButtonColors.icon` tint + `ButtonColors.iconSizeDp` (28dp) + `.alpha(activeAlpha/inactiveAlpha)` per [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt).

---

### 2.11 Settings Tab Strip

The Settings overlay tab bar is Material 3's **`SecondaryScrollableTabRow`** — same-size tabs that fail soft by scrolling rather than clipping:

| Aspect | Value |
|--------|-------|
| Row | `SecondaryScrollableTabRow(selectedTabIndex = …, containerColor = uiBackground, edgePadding = 24.dp, divider = {})` |
| `@OptIn` | `ExperimentalMaterial3Api::class` on the enclosing composable |
| Cells | **custom**, never M3 `Tab` — `Box` + `selectable(selected = …, role = Role.Tab, onClick = …)`, padding 8dp horizontal × 14dp vertical |
| Indicator | M3's default secondary indicator — spans the whole cell and animates |
| Label | `${ui.font.tab.size}` (18sp) **SemiBold**; accent + **Bold** when selected, `uiTextSecondary` otherwise |

Why custom cells: M3 `Tab` adds its own horizontal padding plus a 90dp minimum width, which wrapped the "Navigation" label and left side gaps, and `PrimaryTabRow`'s default indicator is a fixed ~24dp stub. Cells sized to their label keep the whole strip visible on a 360dp screen, with horizontal scrolling acting only as the safety net for large accessibility font scale.

---

## 3. Spacing Quick Reference

| Context | Token | Value |
|---|---|---|
| Card vertical (top/bottom) | `ui.padding.card.vertical` | 8dp |
| Card→card (standalone) | `ui.spacing.card.gap` | 12dp |
| Section→section | `ui.spacing.section.gap` | 14dp |
| Header→first card | `ui.spacing.header.bottom` | 6dp |
| Inline toggle→toggle | `ui.spacing.grouped.row.gap` | 8dp |
| Before expander (in grouped card) | `ui.spacing.grouped.after-expander` | 4dp |
| Expander header row (top/bottom) | `ui.padding.expander.vertical` | 6dp |
| Expander→content | header+8dp spacer | 8dp |
| Last expander→card close | `ui.spacing.grouped.after-expander` | 4dp |
| Label→control (row) | `ui.spacing.label.control` | 16dp |
| Visible divider gap (above/below) | `ui.divider.gap` | 6dp |

Full token list: [`ui.properties`](../app/src/main/assets/ui.properties).

---

## 4. Anti-Patterns

- ❌ A card inside the `NestedCard` (a third level), or a full `uiCardBackground` card used as the `NestedCard` (stacked 20% white) — §2.4 depth cap
- ❌ A row that pads itself **horizontally** or paints its own surface (background/radius) — the `CardArea`/`NestedCard` owns the inset and the box (§2.0, §2.1, §2.3)
- ❌ Hand-rolled label + `Switch` rows — use `ToggleRow` (§2.1); its description is optional
- ❌ Hand-rolled divider markup (`Spacer` + `Box(background)`) — use `SectionDivider()` (§2.6)
- ❌ Hand-rolled `RangeSlider` blocks — use `RangeSliderRow` (§2.8); its value line is mandatory
- ❌ Visible dividers between top-level cards (use spacer)
- ❌ Hand-rolled two-`Text` toggle rows (use `SegmentedRow`, §2.7)
- ❌ Mixed header styles in one card (use `SubSectionHeader` consistently, §2.9)
- ❌ Nesting deeper than `CardArea → Expander → NestedCard` (§2.4)
- ❌ Local `remember`/`rememberSaveable` state for expander open state (use `SettingsViewModel.expanderStates`, §2.4)

---

## 5. Non-Settings Surfaces

### 5.1 Drawer Cards (`MenuDrawerOverlay`)

The Menu drawer body uses the **same render model as a Settings tab**, not a bespoke shell: the shared
`SectionHeader` / `CardArea` / `SectionDivider` / `ToggleRow` stencils from `ui/components`, with the
Settings spacing rhythm (`ui.spacing.header.bottom` 6dp header→card, `ui.spacing.section.gap` 14dp
section→section). Section titles are sentence case and reuse the `settings_section_*` strings. Nav rows are
surface-free, pad vertically only and keep an explicit `heightIn(min = 48dp)` touch target; the Import/Export
pair sits in one card with the same 48dp floor. The tracks/markers headers host their link / filter / reset
controls in the `SectionHeader` `trailing` slot.

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
