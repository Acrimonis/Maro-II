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
  ├─ Independent on/off choices?  → MultiSelectRow                 (§2.7b)
  ├─ Controls that fit one line?  → Card + SectionRow              (§2.14)
  ├─ Choice list that may grow?   → DropdownRow                    (§2.12)
  ├─ Two choice lists side by side? → DropdownPairRow              (§2.16)
  ├─ Double-thumb value range?    → RangeSliderRow                 (§2.8)
  └─ Drawer/Track card?           → Same card surface, specific rows (§5)

New action, not a setting?
  ├─ The surface's forward outcome (one per surface)? → ConfirmActionButton PRIMARY    (§5.6)
  ├─ What withholds the work?                         → ConfirmActionButton DANGER     (§5.6)
  ├─ Neither writes nor loses?                        → ConfirmActionButton SECONDARY  (§5.6)
  ├─ A bare icon control?                             → IconButton, 40–48 dp           (§5.9, tier 2)
  └─ A mode's own on/off?                             → MapToggleSquare family          (§5.9, tier 3)
```

**The actions branch is §5.9's** ([Actions — the four tiers](#59-actions--the-four-tiers-authority)): the
full table, and when each tier is used.

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
| **Tight** density padding | 8×4dp — data-dense cards (track history stats grid, marker details) |

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
| Label style (`labelStyle`) | `ToggleLabelStyle.ROW` (**default**) — 16sp Medium `uiTextPrimary`; `ToggleLabelStyle.COMMENT` — the name drawn in the **description's own typography**, 13sp `uiTextMuted` regular, for a row whose name comments under a title the block already carries (§2.9). Its one use is the two route rendering gates in the Layers tab's Tracks and Routes card, whose own blocks moved there on 2026-10-05. |
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
        Text(label,       color = labelColor, fontSize = labelSize, fontWeight = labelWeight)  // ROW: primary/16sp/Medium · COMMENT: muted/13sp/Normal
        if (description != null) Text(description, color = uiTextMuted, fontSize = 13.sp)
    }
    Spacer(Modifier.width(16.dp))
    Switch(checked, onCheckedChange, colors = …accent…)
}
```

The row carries **no** background, radius or surface of its own — the enclosing `CardArea` owns that (§2.3). A one-toggle card is therefore plain `CardArea { ToggleRow(…) }`; nothing special-cases it.

### 2.2 Slider Row — `SliderRow`

Same model as the toggle (§2.1): a slider is a **row**, not a card — label (16sp Medium `uiTextPrimary`), description (13sp `uiTextMuted`), right-aligned value (bold `ui.value.text`, `${ui.font.value.size}`) and the slider — with **no surface of its own**; the enclosing `CardArea`/`NestedCard` owns the box (§2.3).

Examples in Settings: Marker halo size and Point/icon zoom (Layers → Markers), Idle threshold / Min duration / Dedup radius (Markers), EMODnet cutoff (Layers → Depth Map), re-display distance and time (Navigation → Auto-show Speed Zones), boat offset (Navigation → Automatic map offset), recenter distance (System → GPS tuning), window and adaptive distance (System → Power saving), FPS (System).

Row padding: **vertical only** — like every row it carries **no horizontal padding** of its own; the container owns the inset (§2.0). Label-left / value-right share one line; for a **two-thumb** slider use `RangeSliderRow` (§2.8).

**The marker wizard's slider steps use this row.** Radius, Proximity and Routing cost are a `CardArea` holding this very `SliderRow` (`ui/components/SliderRow.kt`), so the two surfaces share one implementation rather than resembling one; `SliderStep` is only the wizard's thin seam over it, and it passes the two optional end readings — `startLabel` and `endLabel` — that the settings rows leave off. The container owns the inset (§2.0) and the row pads vertically only, as everywhere.

### 2.3 Card = rows + sections — `CardArea`, `SectionDivider`, `Expander`

A **`CardArea`** is one surface (20% white, 12dp radius, `${ui.padding.card.horizontal}` = 16dp horizontal + `${ui.padding.card.vertical}` = 8dp vertical padding) holding **1..N control rows**. The rows are the content; the card is the box (§2.1, §2.2) and it owns the horizontal inset for every child inside it (§2.0). The shared stencils (`CardArea`, `ToggleRow`, `SectionHeader`, `SectionDivider`) live in `ui/components` — non-Settings surfaces (e.g. the Menu drawer) reuse them rather than re-implementing.

The card is divided into **sections**, defined **functionally**: controls that belong together form one section; a control that stands alone is its own section.

- **Between sections** → `SectionDivider()` (§2.6).
- **Sections stack or sit side by side** → stacking is the default (a `Column`); side by side is a
  `SectionRow` (§2.14), whose sections divide by a **vertical** rule.
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

**Worked examples:** Coastline = `CardArea { ToggleRow(…) }` — one section, no divider. Orientation aids = three rows with two `SectionDivider`s — one section per control. Auto-show zones = two rows 8dp apart (one section) + `SectionDivider` + one row (second section). The Routing tab = the *Route algorithm* block (the engine dropdown) then the *Route* block (the pace and preference sliders), each in its own `CardArea` (§2.1) — a block per purpose, a card per block.

**No settings visibility is conditional on another setting's state.** Settings are always shown; a toggle controls *behavior*, never *visibility*. E.g. the GPS-tuning expander is always visible regardless of GPS mode — the GPS mode toggle only controls whether GPS tuning takes effect, not whether the expander renders. Do not wrap a setting or expander in `if (someOtherSetting)`.

### 2.4 Inside-Expander Content — `NestedCard`

**Depth cap (law):** a settings section is at most **`CardArea` → one `Expander` → `NestedCard` → controls**. `NestedCard` is a *surface treatment*, not a nesting tier — it is the paint on the panel the `Expander` reveals. Never place a card (or any full `uiCardBackground` surface) inside a `NestedCard`.

**Preference (not law):** when one card accumulates many related controls, prefer revealing the secondary ones behind an `Expander` rather than growing the card. Primary controls stay inline; secondary detail collapses. There is no count threshold — judge it by whether the content is optional.

- **Card** — top-level section surface (`uiCardBackground`, 20% white, 12dp radius).
- **Expander** — the collapsible disclosure row; it has **no box of its own** and sits directly on the Card.
- **NestedCard** — the single nested container revealed when the Expander is open (`ui.nested.card.bg` `#0DFFFFFF` + `ui.nested.card.border` `#40FFFFFF`). It holds the controls.
- Any control — toggles, one-knob sliders, two-knob `RangeSlider`s, text, swatches — may sit inside the NestedCard.
- **Forbidden:** a card inside the NestedCard (a third level), or using a full `uiCardBackground` card as the NestedCard.

**`NestedCard` also stands without an `Expander`** (2026-10-04): the Menu drawer's two live blocks — the recording's
and the route's — ride a `NestedCard` inside their `CardArea` as **depth without disclosure**, always open, the
sub-card being the paint that sets the block apart rather than a panel a row reveals. The depth cap still holds
there: card → sub-card → controls, never a third level, and the nested padding is the shared token's, so an inner
block is narrower than the card by twice `ui.padding.card.horizontal`.

**Single colour section (`SingleColorSubSection`):** a NestedCard group holding **exactly one** colour control keeps a
`SubSectionHeader`-style title on its own line, and the **description line carries the 24dp colour swatch on its
trailing edge** (tappable → colour picker). If a single-colour section has no description, the swatch sits on the
title line's trailing edge instead. Never a standalone swatch row, and never an empty-label `ColorRow` (an
empty-label row wastes a full-width line for a tiny square). `SubSectionHeader` + labeled `ColorRow` rows are
reserved for **multi-colour** groups under one heading (e.g. Marker halo `Colors` → Pinned / Not pinned). E.g. the
300 m band "Zone color" is a `SingleColorSubSection`.

**A colour control is the swatch alone — never a text action beside it**, in a labelled `ColorRow` as much as in a
`SingleColorSubSection`: the tappable swatch is the whole control, and its `contentDescription` carries the row's own
label so assistive readers still name it.

**Tapping a swatch opens the shared preset grid, and the presets are one list.** The dialog is a 4-column
`LazyVerticalGrid` of 48dp swatches read from
[`MarkerColors.all`](../app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt) — the same list the marker colour
dialog offers — so no picker carries presets of its own and the hues change in one place, the current colour marked
by a 3dp accent border. The two dialogs return different things on purpose: the settings picker
([`ColorPickerDialog`](../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt)) emits an ARGB int,
because `ColorRow` and `ColorPairRow` persist ARGB, while the marker picker
([`MarkerColorPickerDialog`](../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)) emits a palette index,
because a marker persists `colorIndex`. Since the settings rows keep raw ARGB, a stored colour that is not a palette
member draws no selected swatch — expected, never patched with a fallback index. **The presets are not a free
choice:** no surface adds a swatch or a hue of its own, because the sixteen satisfy constraints stated once with the
palette in [`docs/color-scheme.md`](color-scheme.md) — map contrast, no hue near the water, and pair separation.

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
- **Between horizontal sections** (§2.14) → the same divider turned on its side, drawn by `SectionRow`: `${ui.divider.height}` (1dp) **wide** and full height, `${ui.divider.gap}` either side, no inset of its own.
- **Within a section** → `${ui.spacing.grouped.row.gap}` (8dp) between rows. No divider.

```
Spacer(6.dp)
Box(Modifier.fillMaxWidth().height(1.dp).background(uiDividerColor))   // inset comes from the container
Spacer(6.dp)

Box(Modifier.fillMaxHeight().width(1.dp).background(uiDividerColor))   // SectionRow draws this (§2.14)
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
- **Unselected segments are outlined** (1dp `uiDividerColor`); the selected segment wears the **tonal
  container** (`ui.select.container`, the accent at 30 %), a **1dp `ui.accent` border on its own cell** —
  square by design, the parent's rounded clip shaping its outer end — `uiTextPrimary` Bold text and a
  **check glyph in `ui.value.text`**. The accent **fill** is the **action's**, reserved for it (§5.6, §5.9),
  so a selected choice never wears the same face as a primary button — see the note below the two components.
- **Accessibility** — `selectableGroup()` on the row plus `Role.RadioButton` per segment, so the control
  is announced as "n of m, selected" instead of as unrelated buttons.
- **`captions`** (optional) — one 12sp `uiTextMuted` line under each segment (per-stop numbers,
  e.g. GPS frequency "2 s · 5 m"), each weighted like its segment.
- **Surface-free** — the row paints **no** surface of its own (only its 1dp outline, no outer padding);
  the call site supplies the `CardArea`/`NestedCard` (§2.0).

**Do not hand-roll two `Text` rows with `clickable`** — use this control. Do not paint a surface on it.

### 2.7b Multi-Select Row — `MultiSelectRow`

For choices that do **not** exclude each other — two or more flags, each on or off by itself (the menu
drawer's Arrows and Colours are the shipped case): `MultiSelectRow(options, isOn, onToggle)`.

- **The same connected control as §2.7, and deliberately so**: **one bar is the app's norm for a set of choices**, whether or not they exclude
  each other — the language selector's own shape, which every multiple-choice control follows. What differs
  between the two is what they **do**, never how they look.
- **The on-face is the app's taken-choice face**, shared with §2.7's selected segment on purpose —
  `ui.select.container`, a **1dp `ui.accent` border on the on half** and a **check glyph in
  `ui.value.text`**, the label rising to `uiTextPrimary`. Off is the inactive face: no fill, an
  `uiTextMuted` label. No combination is refused, including none.
- **Accessibility** — deliberately **no** `selectableGroup()`: each half is `toggleable` with
  `Role.Checkbox`, so it is announced as "check box, checked/unchecked" rather than as "n of m, selected" —
  the one place the two controls part, and it is in what is announced, not in what is drawn.
- **Surface-free**, exactly as §2.7 is, so the call site supplies the `CardArea`/`NestedCard`.

**Use §2.7 when the options exclude one another, and this one when they are independent.** Both live in
`ui/components` so a change to the outline they share is seen once.

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

**Transparency convention (app-wide):** all opacity/transparency settings use **TRANSPARENCY** semantics — **0 = opaque (fully visible), 100 = invisible**. Never expose "opacity" (inverted) wording. Label the control **"Transparency"** and format two-thumb values as **"Border X% · Fill Y%"** (border = outer stroke/strong, fill = inner/zone content/faint). Applies to tracks, marker halo, 300 m band, regulated zone polygons, and low-depth warning; the percentage → alpha derivation is one function, `transparencyPctToAlpha` in [`MapOverlayRenderer.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt).

**Reinforcement convention (app-wide):** when a colour needs reinforcing — a selected line's edge, an under-stroke — **derive it from the base** with `reinforcedColor` and the one lever `ui.reinforce.darkenPct`; never add a per-feature colour key for a reinforcement. The rule is scoped to **a derived variant of a colour the user can pick** (the *Active route* colour's own edge today): a designed token pair stays a token pair, and the palette's deliberately hand-picked near-duplicates (e.g. `ui.dashboard.zone.caution` against `semantic.caution`) are not read against by it. The lever counts the **strength of the effect** — 55 means pushed 55 % toward black — and the helper is pure with the **caller** reading the lever (`reinforcedColor` in [`ColorReinforcement.kt`](../app/src/main/java/ykws/android/maro/ui/color/ColorReinforcement.kt)).

### 2.9 Header Hierarchy

- `SectionHeader` — top-level sections only. **One style app-wide:** sentence case ("Layers", "Navigation"), 18sp bold, `ui.accent`, no letter-spacing (`ui.font.section.size`). There is no casing variant. It is always followed by `Spacer(uiSpacingHeaderBottom)` (`ui.spacing.header.bottom`, 6dp) before its card — one header-to-card gap for every section in every tab (2026-10-06).
- `SubSectionHeader` — 16sp SemiBold, `ui.text.primary` + optional 13sp `ui.text.secondary` description; the standard header for a **titled** sub-section inside a card/expander. Headings are white like every other heading — hierarchy comes from **weight + spacing**, not a dimmed colour.
- **`CardDescription`** — 13sp `ui.text.muted`, one lead-in sentence placed **inside the card, before its first control**; it owns its trailing 4dp spacer (`${ui.spacing.grouped.after-expander}`) and has **no horizontal inset of its own** now that the `CardArea` supplies it (§2.0).

**`CardDescription` vs `SubSectionHeader(title, description = …)`:** `CardDescription` = an explanation under a `SectionHeader`, dropped into the top of a card before its controls. `SubSectionHeader` = a **titled** sub-section **inside** a card/expander, whose 13sp `ui.text.secondary` description labels that group. Both are kept.

**A comment-styled row name is not a heading.** `ToggleRow(labelStyle = ToggleLabelStyle.COMMENT)` draws a row's *name* in the description's typography because the block's own `SectionHeader` heads it — the name comments, so no `SubSectionHeader` is stacked above it. The white-heading sentence above is untouched: no heading is ever dimmed, and this dims a row name that was never a heading.

### 2.10 Popup Styling (canonical)

Filter/sort and other popup menus follow the settings-page hierarchy. This is the canonical
popup-styling spec (moved from `ui-lists-guidelines`).

```
┌─ Popup → Surface (uiBackground, 12dp, 1dp uiAccent rim) ────────────┐
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
| Popup border | `uiAccent`, 1dp | The edge the dropdown box and the bars' taken cells wear too |
| Card bg | `uiCardBackground` | Per-section card |
| Section title | `uiDashboardTextMuted`, 16sp, SemiBold | Popup-only: deliberately dimmer than the settings `SubSectionHeader`, which is `uiTextPrimary` (§2.9). Sharing the dashboard muted token here is a known token-scope wart — a future pass may migrate popups to `uiTextPrimary`. |
| Row text | `uiTextPrimary`, 15sp, Medium (selected: SemiBold) | |
| Checkmark | `uiAccent`, 16sp, SemiBold | ✓ for selected |
| Row v-padding | 2dp | Tight — matches settings toggle rows |
| Card v-padding | 8dp | |
| Card gap | 4dp | `Arrangement.spacedBy(4.dp)` |

All popup icons use `ButtonColors.icon` tint + `ButtonColors.iconSizeDp` (28dp) + `.alpha(activeAlpha/inactiveAlpha)` per [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt).

**One row serves the whole family.** Every member draws its rows through `PopupRow`, its
groups through `PopupSectionCard`, its section titles through `PopupSectionTitle` and its outer surface
through **`PopupSurface`** — `uiBackground` on a 12dp corner behind the 1dp **`uiAccent` rim**, an 8dp
shadow, the family's **12dp inset on all four sides** — which the dropdown's wheel opts out of, its card
filling the surface so no `uiBackground` ring shows (2026-10-05) — and a height bound it scrolls past — all in
[`PopupFamily.kt`](../app/src/main/java/ykws/android/maro/ui/components/PopupFamily.kt), together with the
geometry above as constants: the 240dp width, the 16dp/2dp row padding, the 24dp check box holding the `✓`
in `uiAccent` at 16sp SemiBold on the selected row alone, the 15sp Medium–SemiBold label and the 0.4 dim of
a switched-off row. The dropdown's menu ([§2.12](#212-dropdown-row--dropdownrow)) reads them all; the two
list popups read the rows and the constants but still state their own outer surface inline, and
`PopupSurface` is there for them to move onto.

**The dropdown's list is a wheel, and it is this family's one exception**: `DropdownWheel`
draws a snapped column of three to five rows where the centre slot is marked by the taken-choice face rather
than a `✓`, and only that label is bold — so the wheel's row is not `PopupRow`, deliberately, and a change to
the row family does not reach it. **The rows' alignment is the control's, not the wheel's own** (2026-10-05):
it arrives as a parameter, so the wheel's labels and the field's value are drawn on one axis. **Its slot is
the box's own measured height and the popup is centred on the box** (2026-10-05), so the banded slot is
exactly the box's rectangle at every count and font scale. Its
arithmetic is in
[`WheelPolicy.kt`](../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt) and its rule of entry
is §2.15's. **It lands the entry it is given**: the popup scrolls by that entry's own distance on
the slot grid, from the rest frame it opens in, and then compares the row the band names with the entry the
caller holds — once per open, keyed on the open, nothing being written while the popup is open.
**The commit is a tap on a row** (§2.15): the drag only scrolls and snaps and an outside click cancels, so
nothing is written while the popup is open. §2.12 states what the control shows.

**Overflow.** A popup wraps its own height and **scrolls** once its content exceeds the space it can
occupy — a popup whose content fits stays exactly as tall as that content, and nothing is pushed past
the screen edge. The bound is the window's own height less a reserve for the chrome the popup hangs
below, never a fixed dp, which breaks across densities and rotation; [`popupMaxHeightDp()`](../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt)
is that derivation, shared by the filter and sort popups, floored at `POPUP_MIN_HEIGHT_DP` so a window
shorter than the reserve still yields a usable popup — the one case where the bound can exceed the
window. The track filter's three axes — 13 rows, about 490dp — are the case that exposes a clip in
landscape.

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
| Tabs | `SettingsTab` (`ui/map/SettingsTab.kt`) — the enum is a tab's one home: its `labelRes`, its display order, the pager's `pageCount` (`SettingsTab.entries.size`) and the scroll-state mapping all derive from it, so a tab is never addressed by a bare index |
| State | **`selectedTab: SettingsTab` is the single source of truth**: `pageCount` derives from `SettingsTab.entries`, and **no pager→tab write-back exists** while `userScrollEnabled = false` — a returning swipe needs one, reading `settledPage` and never `currentPage` |

Why custom cells: M3 `Tab` adds its own horizontal padding plus a 90dp minimum width, which wrapped the "Navigation" label and left side gaps, and `PrimaryTabRow`'s default indicator is a fixed ~24dp stub. Cells sized to their label keep the whole strip visible on a 360dp screen, with horizontal scrolling acting only as the safety net for large accessibility font scale.

### 2.12 Dropdown Row — `DropdownRow`

For a single choice whose option list may grow past the two or three segments a `SegmentedRow` fits
(e.g. the route algorithm list):
`DropdownRow(label, options, selected, onSelect, accessibleName, description = null, sizing = DropdownSizing.Fill)`.

```
┌─ Column ──────────────────────────────────────────────────────────────────┐
│  optional label (16sp Medium uiTextPrimary)                               │
│  optional description (13sp uiTextMuted)                                  │
│  ┌─ the bars' base: uiRadiusCard + 1dp uiAccent rim, 8×10dp padding ─────┐ │
│  │  value (uiTextPrimary, Normal)                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
```

- **The box is its own component** (`DropdownBox`, a file of its own) — a `Row` on the bars' base holding the
  value (**no arrow**, 2026-10-05), and **it is the control, the anchor and the tap target**: it carries
  `clickable`, the call site's `accessibleName` as its `contentDescription` and `Role.DropdownList`, and it
  reports its own measured size, so nothing about the list's placement is left to a library — and the wheel
  takes that same size as its slot. **Its metrics and the style its value reads are that file's own**, and
  that same style measures it (`dropdownBoxWidth`), so the width a caller fixes and the width drawn cannot
  drift apart. `DropdownRow` composes it and owns the label, the description, the popup and the wheel.
- **What a caller sets is the behaviour, never a width** — `DropdownSizing.Fill` takes the width the caller
  gives (the default) and `DropdownSizing.Content` takes the width this field's longest entry needs, which the
  field asks of the box. No call site spells a dp, an arrow or a padding.
- **Label and description sit above the field** — same type as §2.1/§2.2, both optional (`null` at every
  call site today, where a section header or the drawer's comment already names the control). The row
  paints nothing of its own: the call site supplies the `CardArea`/`NestedCard` (§2.0).
- **The box is the bars' own base** — `uiRadiusCard` behind the **1dp `uiAccent` edge** a
  `MultiSelectRow`'s on half or a `SegmentedRow`'s selected cell wears, with their **10dp vertical
  padding** and no surface of its own. The value reads `uiTextPrimary` at `${ui.font.value.size}` Normal on one
  line, **on the axis the control's one `TextAlign` names — centred by default and shared with the wheel's
  rows** (2026-10-05), ellipsised when a marker's own name is long. **Its height is that padding's consequence,
  not a number** — the same way the bars get theirs, and the wheel's slot reads it — which is what M3's
  `OutlinedTextField` could not give: its internal padding, 56dp floor, caret and theme selection highlight are
  all gone with it, and its `KeyboardArrowDown` arrow is gone too (2026-10-05).
- **Its horizontal chrome is deliberately small** — **8dp** of padding (2026-10-04): a box's chrome is paid
  **twice** in a row of two, and a wider field is what cut the second word of the pair. Its vertical padding
  stays the bars' 10dp, and the rim's 1dp is still added on each side of a measured width as that answer's
  rounding slack. **The 24dp arrow and its 4dp gap are gone** (2026-10-05), so the width a field asks for is
  the value's own plus that chrome.
- **The list is a §2.10 popup the box itself positions, and it is centred on the box** (2026-10-05) — a
  `Popup` with `alignment = TopStart`, its width the box's own measured width, and an offset that closes the
  card's own vertical inset and the wheel's end padding, so the banded slot is exactly the box's rectangle and
  the rows either side spill over the panel. **Its surface carries no inset** (2026-10-05): the wheel passes
  `contentPadding = 0.dp` to `PopupSurface`, so the section card fills the popup and no `uiBackground` ring
  shows around it. Its height is bounded by
  `popupMaxHeightDp()`, which caps the slot count. **Why it is not M3's menu:** `ExposedDropdownMenu` sized
  itself from the anchor and then shifted to stay inside the window, which showed as a horizontal offset
  against the box in landscape — placement neither review could see in the source and no parameter could
  override. The content is the family's own — `PopupSurface` outside, `PopupRow` for every option
  ([§2.10](#210-popup-styling-canonical)) — so 16dp/2dp padding, the 15sp Medium–SemiBold label and the 24dp
  `✓` box in `uiAccent` on the current option are one implementation for all three lists. **Its body is a
  wheel**, so that sentence is narrowed: `DropdownWheel` draws a snapped column of three to five rows whose
  centre slot is the choice — `uiSelectContainer` behind an accent rule above and below, only the centred
  label bold — **the commit being a tap on a row** (2026-10-05, revision 1), so the drag only scrolls and
  snaps, a row tap writes its own row and closes, and an outside click cancels. `PopupSurface` is its outer
  surface with `scrollable = false` and `contentPadding = 0.dp`, the wheel owning the only scroll; its
  arithmetic lives in `WheelPolicy.kt`, unit-tested beside it.
- **The selection is resolved once, and the popup names the row it lands on** — one index feeds
  the box's word and the wheel's entry alike, and the wheel reaches that entry by scrolling *by* its own
  distance on the slot grid from the popup's rest frame, then comparing the row the band names with the entry
  the caller holds, once per open. A value the options do not carry therefore reads the same on both surfaces
  instead of leaving a blank box beside a banded first row; the anchors the library's layout reports stay the
  device pass's to confirm, this guard being a detector of disagreement rather than a proof of the convention.
- **Options** — `List<Pair<T, String>>`, the `CustomSortField` shape: the generic `T` is the value the
  caller persists and the strings are already-resolved labels, so the row never holds user-facing text. The
  order they arrive in is the **caller's** own — the alphabetical order a list and a flag-driven selector
  share, ignored articles and all, lives in [`ui-lists-guidelines.md`](ui-lists-guidelines.md).
- **The box carries a name, and takes no caret** — a plain `Row` rather than a focusable
  field, so nothing enters the surface's traversal with a caret. A **required `accessibleName`** is the one
  string each call site hands it, set as the node's `contentDescription`, because a label-less box would
  otherwise announce nothing at all; what is announced with it is the device pass's to confirm.

- **Two of them side by side are the pair control** — [`DropdownPairRow`](#216-dropdown-pair--dropdownpairrow),
  §2.16: one row inside a card's inset holding two label-less boxes, 4dp apart, each side's width set by the
  caller's `DropdownPairWidth`. `dropdownBoxWidth(words)`, in the box's own file, is what measures a side that
  must never trim.

**Do not hand-roll a label + tap-to-open `DropdownMenu`** — use this control. What a control paints, and
where its own list goes, is the control's business: this one draws the bars' rim and positions its list from
its own measured bounds rather than leaving the placement to a library. A call site wraps it in a card and
needs nothing else.

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
| Between two dropdown fields of a group, either axis | `ui.spacing.dropdown.gap` | 4dp |
| Visible divider gap (above/below) | `ui.divider.gap` | 6dp |
| Vertical divider width (side-by-side sections) | `ui.divider.height` | 1dp |

Full token list: [`ui.properties`](../app/src/main/assets/ui.properties).

---

## 4. Anti-Patterns

- ❌ A card inside the `NestedCard` (a third level), or a full `uiCardBackground` card used as the `NestedCard` (stacked 20% white) — §2.4 depth cap
- ❌ A row that pads itself **horizontally** or paints its own surface (background/radius) — the `CardArea`/`NestedCard` owns the inset and the box (§2.0, §2.1, §2.3). **The bar-shaped controls are the deliberate exception** (§2.7, §2.8, §2.12): `SegmentedRow`, `MultiSelectRow` and `DropdownRow` draw their own rim and pad themselves inside the card's inset, because their box *is* the control
- ❌ Hand-rolled label + `Switch` rows — use `ToggleRow` (§2.1); its description is optional
- ❌ Hand-rolled divider markup (`Spacer` + `Box(background)`) — use `SectionDivider()` (§2.6)
- ❌ Hand-rolled `RangeSlider` blocks — use `RangeSliderRow` (§2.8); its value line is mandatory
- ❌ Visible dividers between top-level cards (use spacer)
- ❌ A hand-rolled `Row` of card sections, or a hand-drawn vertical divider (use `SectionRow`, §2.14)
- ❌ A `SectionRow` nested inside another `SectionRow` (§2.14)
- ❌ Hand-rolled two-`Text` toggle rows (use `SegmentedRow`, §2.7)
- ❌ Hand-rolled label + tap-to-open `DropdownMenu` rows (use `DropdownRow`, §2.12)
- ❌ A hand-rolled `Row` of two dropdowns (use `DropdownPairRow`, §2.16)
- ❌ Mixed header styles in one card (use `SubSectionHeader` consistently, §2.9)
- ❌ Nesting deeper than `CardArea → Expander → NestedCard` (§2.4)
- ❌ Local `remember`/`rememberSaveable` state for expander open state (use `SettingsViewModel.expanderStates`, §2.4)

---

### 2.13 Text Field — inline editors

One rendering for a text field the app edits in place: a **transparent `TextField`** — no container
colour and no indicators, so it reads as the text it replaces — the cursor in `uiTextPrimary`, and the
field **opening with its text selected**, so typing replaces the value while a tap still places the
caret. Name lines are 15sp SemiBold `uiTextPrimary`, comment lines 13sp `uiTextMuted`, and the action is
`Done`, or `Next` where a step continues.

Its callers are the track card's name and comment fields (`TrackHistoryOverlay.kt`), the marker card's
two (`MarkerManagementOverlay.kt`) and the marker wizard's Title and Description steps
(`ui/markers/wizard/steps/TextInputStep.kt`). The wizard is the one that keeps a label line above the
field — a step has no card around it to name the field — and a muted placeholder for the empty case. No
surface adds a border, a fill or an indicator of its own.

### 2.14 Side-by-Side Sections — `SectionRow`

A card's sections stack by default (§2.3). When two controls genuinely belong on one line,
`SectionRow(weightLeft, left, right)` lays them side by side inside the same `CardArea`: two `Column`
sections divided by the vertical rule (§2.6), each side taking the share of the width it is weighted.

- **Unequal shares are the norm** — `weightLeft` states the left side's share; equal halves are `0.5f`,
  and a slider generally wants the wider side. `RoutingCostStep` is the shipped case: Route role left,
  Routing cost right.
- **Same laws as everywhere** — the `CardArea` owns the surface and the horizontal inset (§2.0); each
  side pads vertically only; a card inside a card stays forbidden (§2.4).
- **Depth cap** — one `SectionRow` per card, never a `SectionRow` inside a `SectionRow`.
- **The rule and the height are the primitive's** — `SectionRow` draws the vertical divider and the row
  is as tall as its taller side; no caller hand-rolls a `Row` of sections with a divider of its own.
- **Headings top, controls bottom** — each side leads with its heading block, then gives the slack to
  `Spacer(Modifier.weight(1f))` and any minimum gap before its control, so two controls of different
  heights share one bottom line while the headings stay aligned at the top. The sides fill the row's
  height because `SectionRow` gives it to them; the spacer is the caller's, which is what lets a heading
  and its control be drawn by different pieces — `RoutingCostStep`'s right side is that case.
- **A comment wraps rather than being cut** — no `maxLines` on a section's comment; keep the wording
  short enough for the column instead of trimming it with an ellipsis.

### 2.15 Rule of Entry — a wheel in a popup

The dropdown's list is a **wheel** ([§2.12](#212-dropdown-row--dropdownrow)): a snapped column of three to
five rows inside a popup, and **the commit is a tap** (2026-10-05, revision 1) — the drag only scrolls and
snaps, so **nothing is written while the popup is open** and the box carries the value it had; a tap on any
row, the banded one included, writes that row and closes, and **an outside click cancels**, writing nothing
because nothing was pending.

- **Opening is a drag or a tap, and the drag is taken unconditionally** (2026-10-05): a vertical drag
  beginning on the box opens the popup and that same drag spins the wheel — the popup being a separate window,
  the field's own detector scrolls the hoisted wheel state and snaps it to the nearest slot on release.
  **A tap on the box still opens it**, so the control is never drag-only.
- **The enclosing scroller loses that drag, by decision:** the page under a finger that lands on a box does
  not scroll with it. The lost scroll is the accepted cost, not a defect.
- **The band is the box's rectangle:** the popup is centred on the box and its centre slot is the same width
  and height as the box and covers it exactly, so the chosen entry is drawn *on* the control and the rows
  either side spill over the panel.

So a list of choices is **a dropdown, a bar, or a wheel in a popup**, and the wheel's commit is **a tap on a
row**. **A drag that commits on its own stays out** — that is where this gesture parted from the retired
roller — and the guard is that the tap reads the same band the wheel draws, so the marked entry and the
committed value are one read. **The root cause of that old fault was never established**, so the claim is that
the commit is *guarded*, not that the shape is proven unrelated. R70 stays retired.

### 2.16 Dropdown Pair — `DropdownPairRow`

Two of these controls side by side, for the shape a row of two settings wears:
`DropdownPairRow(left, right, modifier, leftWidth, rightWidth, gap, verticalPadding)`, each side a
`DropdownField(options, selected, onSelect, accessibleName)` — a `DropdownRow`'s own inputs without its label
and without its width.

- **The width is the pair's capability, set per side** — `DropdownPairWidth.Content` sizes a box to its
  **longest option**, so no entry of that list can be cut and the box holds that width whatever the row does;
  `DropdownPairWidth.Remainder` gives it whatever the row leaves, which makes it the **elastic** side, its
  value trimming on one line; `DropdownPairWidth.Proportional` gives it a share of the row **in proportion to
  its own longest option** (2026-10-05), so a wider word earns a wider box. The defaults are `Content` left and
  `Remainder` right; two `Remainder` sides share the row evenly, two `Content` sides leave the row's tail empty,
  and two `Proportional` sides split the row in the ratio of the words they must hold.
- **The content width is asked of the box** — `dropdownBoxWidth(words)`, in `DropdownBox.kt`, answers it from
  the box's own metrics (its padding, its arrow gap, its arrow and its rim) and from the style its value really
  reads — `LocalTextStyle` merged with the size and the weight, the box's one statement of it. A caller
  re-spells neither, and a width cut from a style that skipped the theme measures short and ellipsises the word
  it was measured for. The pair tells a side this as a behaviour (`DropdownSizing.Content` through the field),
  so it hands over no number.
- **No side carries a label** — the comment above the pair names both, and each side's `accessibleName` is what
  a screen reader announces. **No vertical rule stands between them either**: §2.14's `SectionRow` lays out two
  sections, and this is two controls in one.
- **The gap and the padding are the primitive's** — the pair's gap is the shared token
  `ui.spacing.dropdown.gap` (**4dp**) and its vertical padding is `ui.padding.toggle.vertical`, so a call site
  hands over two fields and nothing else. It is that small because the two boxes' chrome is paid twice in one
  row, and it is the **same token the route ends' two rows take vertically** (2026-10-04): one value for the
  space between two fields of a group, whichever way they sit.

---

## 5. Non-Settings Surfaces

### 5.1 Drawer Cards (`MenuDrawerOverlay`)

The Menu drawer body uses the **same render model as a Settings tab**, not a bespoke shell: the shared
`SectionHeader` / `CardArea` / `SectionDivider` / `ToggleRow` stencils from `ui/components`, with the
Settings spacing rhythm (`ui.spacing.header.bottom` 6dp header→card, `ui.spacing.section.gap` 14dp
section→section). Section titles are sentence case and reuse the `settings_section_*` strings. Nav rows are
surface-free, pad vertically only and keep an explicit `heightIn(min = 48dp)` touch target; the Import/Export
pair sits in one card with the same 48dp floor. The readings a drawer card prints follow
[`docs/ui-drawer-guidelines.md`](ui-drawer-guidelines.md) §9, and a figure the mode does not hold yet prints the
pending mark that section's own authority — §5.8 — names. The tracks/markers headers host their link / filter / reset
controls in the `SectionHeader` `trailing` slot, and the markers header opens that slot with the shared
create action, outside the filter-axes gate; the tracks header takes none. The order the action takes there,
and the separator's own shape, are stated once in `MarkerCreateAction`'s KDoc.

### 5.2 List Item Cards (`TrackHistoryOverlay` + `MarkerManagementOverlay`)

Canonical list-item card spec (accent-bar shell, shared tokens, per-type variations) lives in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker).

### 5.3 Dashboard Tiles (`DashboardCard`)

Three-line `Column` inside a rounded card (`8dp` radius, `4dp` pad, `uiCardBackground`). Used in the 2×2 dashboard grid (Distance, Zone, Depth, Speed).

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

**The value uses `Modifier.weight(1f)`** — it fills all remaining space after title + subtitle measure. Any font size increase on title or subtitle reduces the value's auto-sized ceiling. Keep title + subtitle combined height ≤ ~34dp to preserve value readability.

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

**Never apply under-strokes to proximity previews or fill polygons** — highlights only.

Source: [`MarkerOverlay.kt`](../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) + track polyline rendering in [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt).

---

### 5.5 Top-Left Status Squares (`GpsStatusIcon` / `TrackStatusIcon` / `EarthWaterIcon` / `LockScreenButton` / `RecenterButton`)

44×44dp rounded square with a 22sp emoji glyph, one slot each in the top-left status row
([`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)). Every square's paint comes
from one path in [`MapSurface.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt):
`MapSurface` paints the fill, clips the corner, draws the border and applies the padding, and
`MapToggleSquare` layers the row's own size and the tap on it. A square declares only its own state colours
in [`colors.properties`](../app/src/main/assets/colors.properties) (alias-interpolated from the semantic
palette) and exposes them via
[`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt).

**Visual recipe — one surface block (`ui.map.surface.*`) for every box that paints a background:**

| Aspect | Key |
|---|---|
| Fill | `ui.map.surface.inactive`, painted whole: the fill's own weight is in the token |
| Corner / padding / border | `ui.map.surface.corner.radius` / `.padding` / `.border.color` + `.border.width` |
| Active face | the square's own state colour at `ui.map.surface.active.alpha` |
| Inactive content alpha | `ui.map.surface.inactive.content.alpha` — the content dims, never the box |
| Square geometry | `ui.map.toggle.square` / `ui.map.toggle.gutter` / `ui.map.toggle.icon.size` |
| Overlay-card text | `ui.map.overlay.text.color` + `.weight` (bold) + `.size` |

**State → colour mapping:**

| Square | Off / inactive | Active face |
|---|---|---|
| GPS | `mapSurfaceFaceInactive()` (DEMO) | acquiring/weak=`semantic.caution`, healthy=`semantic.compliant`, idle=`semantic.info`, stale=`semantic.danger` |
| Tracking | `mapSurfaceFaceInactive()` (OFF) | moving=`semantic.compliant`, idle=`semantic.info` |
| Earth/Water | — (always a resolved face) | water=`semantic.info`, land=`semantic.compliant` |
| Screen lock | `mapSurfaceFaceInactive()` (📵) | locked=`semantic.info` (📵) |
| Recenter | absent when there is nothing to recenter | `ui.accent` = `semantic.info` |

The face is resolved by the square itself — `mapSurfaceFace()`, `mapSurfaceFaceInactive()` or
`mapSurfaceFaceActive(colour)` in `MapSurface.kt` — and the surface only paints it, so no state logic lives
in the painting path. Because the fade sits on the content, the tracking-OFF and lock-OFF squares paint the
fill whole and dim their glyph alone; they no longer fade the box.

**The collapsed legend square** is the same `MapToggleSquare` read directly by `MapScreen.kt`: one square on
the shared surface carrying the ⏱ stopwatch written `\u23F1\uFE0F` (`Emoji_Presentation=No`, so the selector
is what asks for the colour form) and **no** active face — the control's active form is the expanded scale
card, so the two faces are never on screen together.

**New squares must:** paint through `MapToggleSquare`/`MapSurface`, resolve one face from their own state,
declare their own colours as a `status.<name>.*` token family parsed in `AppConfig`, and never hardcode a
fill, a corner, a border or a padding in the composable.

**Lock-screen overlay placement:** the lock toggle sits right of the Earth/Water icon in the
top-left status row (GPS → Tracking → Earth/Water → Lock → Recenter). Earth/Water is that row's one
setting-driven conditional slot — the Layers tab's "Show Land/Water Icon" (`showLandWaterIcon`,
default on) hides the square, and the row's `Arrangement.spacedBy` closes the gap;
`lockMirrorStartOffset()` reads the same flag, because the row's order is what the duplicate's offset
depends on. When locked, the overlay recreates the same controls above the input-blocking scrim
(duplicate unlock button, `ZoomControls`, `LockBanner`); those duplicates must live inside a `Box`
padded exactly like `MapContent`'s dashboard padding (portrait: bottom = `dashboardBaseHeight`;
landscape: start = `landscapeDashboardWidth`) so they align over the originals in both orientations.
The locked zoom controls accept a double-tap only (single splash taps are ignored).

### 5.6 Confirmation Dialog — `ConfirmDialog`

Canonical modal confirmation surface. Replaces every `ModalBottomSheet` confirmation (`ConfirmSheet`,
recording exit, resume, import conflict, GPS source-switch) and the merge / orphan-recovery
`AlertDialog`s. No framework sheet, no platform dialog window.

**Contract**

- **One component, own scrim, above the ladder.** Rendered on the overlay ladder **above every
  drawer and the map** — drawer-hosted confirmations (merge, batch delete) via the ladder
  `ConfirmRequestHost`, the rest by their `MapDialogHost`/`MapImportConflictHost` hosts, all composed
  above `OverlayLayer`; it draws **its own** full-screen `ui.scrim.alpha` layer directly beneath its
  panel, and a tap on that scrim dismisses (see `docs/ui-drawer-guidelines.md` §1/§3). The overlay
  ladder's scrim is drawer/settings/wizard only and is **suppressed while any `ConfirmDialog` is
  visible**, so the two dims never stack; both are hard on/off toggles sharing the `ui.scrim.alpha`
  token. OS-consent prompts (background location, GPS permission, battery optimisation) stay
  `AlertDialog` — only the app's own confirmations migrate.
- **Structure (top → bottom):** title (18sp bold, exposed as a heading) → message (14sp
  `uiTextPrimary`) → optional `options` slot → 0.5dp `uiDividerColor` divider → stacked full-width
  actions, 8dp apart; 24dp horizontal padding, 16dp bottom padding.
- **Geometry:** width = `min(maxWidth, maxHeight)` (the device's portrait width) in **both**
  orientations, horizontally centred, with no cap token; bottom-anchored flush with the bottom
  edge, rounded top corners only (`RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`), the
  navigation-bar inset applied *inside* the panel; height wraps its content and scrolls when taller
  than the space left above the IME and the navigation bar; the IME offset is retained.
- **Accent border:** the `ui.accent` 1dp outline is stroked on the **top and both sides only — no
  line on the bottom edge**, so the flush panel keeps reading as a pull-up drawer (a `Surface`
  `border` would trace all four sides and close the shape at the bottom).
- **Motion:** the **panel** slides over **450 ms** (`ConfirmDialogAnimMs`) in both directions; the
  scrim is a **hard on/off toggle** (no fade) and does not share the panel's window. While the dialog
  is visible the ladder scrim yields to it, so dims never stack; every other `DrawerSlot` caller keeps
  its own timings.
- **Actions:** `ConfirmAction(label, role, enabled, onClick)` rendered in order, stacked full width.
  `PRIMARY` = `uiAccent` filled, white bold label; `DANGER` = `semanticDanger` filled, white bold
  label; `SECONDARY` = a **full action button on the accent at 50 %** (`ui.action.neutral.background`) with a
  **2 dp `ui.accent` rim at full opacity** and the same **white bold label** — the fill's weight states the
  rank, and the rim, accent against grey, states that the control can be taken.
- **A disabled action is the same control with a different face — the whole app's rule, not this
  surface's**: any action with `enabled = false` reads as the
  **outlined role, its label in `uiTextMuted` and its outline in `uiDividerColor`, and no accent
  surviving it** — which is what keeps the accent meaning *the surface's own outcome* rather than being
  dimmed into ambiguity. **The live faces carry the accent in fill and rim, and those two tokens are the
  whole of the disabled signal**: a control that cannot be taken is the only one **with
  neither a fill nor a drop of accent**, its 1 dp grey outline against the middle action's **2 dp accent
  rim**. One implementation draws it: `ConfirmActionButton`
  ([`ui/components/ConfirmDialog.kt`](../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt)),
  the app's only rendering of an action, which the ladder's confirmation panel, the route panel and the
  marker wizard's footer all host. No surface dims its own control into a disabled look. The two tokens
  are §2.7's own unselected-segment ones, so the face adds no palette entry, and Compose announces
  `enabled = false` natively, so nothing custom rides accessibility. A greyed button promises nothing,
  saying only *not yet*.
- **A button's colour states its role, never its importance**: the **accent** is the
  surface's own outcome — the action the surface exists for, one per surface; the **red** is the action
  that withholds the work; the **50 % accent under an accent rim** is the middle door, **whatever is not the surface's own
  outcome and not a loss, whether it writes elsewhere or not — the door that leaves a mode included. The order
  **affirmative → neutral → destructive** governs a surface's
  **stacked** actions; where a requirement fixes a row's own order, that order stands — the route
  panel's two grids fix theirs, `Acquire route` · `Confirm` · `Save track` · `Exit` in the acquisition
  and `Save track` · `Reroute` · `New route` · `Exit` while followed, each drawn as two rows of two
  with the accent on the **one enabled forward action** at every instant (R16, R17). The recording exit
  dialog is what the whole family is read against — `Save track` accent · `Continue recording` outlined
  · `Discard track` red.
- **A compact size, for a host off the dialog's stack** (2026-10-04): `ConfirmActionButton(action,
  modifier, compact = false)`; when `compact` the control **wraps its label** (no `fillMaxWidth` stretch)
  and takes an **8 dp corner** against the dialog's 12 dp, an **explicit 28 dp height** against 40 dp,
  `PaddingValues(horizontal = 12.dp)` content padding and a **12 sp** label against 14 sp. Every face keeps
  its own colours exactly — the compact size moves the geometry, never the role — so the action row's
  commands (§5.7) wear this same control's compact `SECONDARY` face and no host hand-rolls a second button.
- **Cancel is optional** and is just another action, passed **last** — where present it is the
  bottom-most button and calls `onDismiss`. Offer one only where dismissal unambiguously means
  "abort, nothing happens" (resume, import conflict, merge, batch delete). No Cancel where dismissing
  has a side effect (orphan recovery saves the checkpoint) or where the stacked actions already cover
  the space (recording exit, stop recording).
- **Dismissal:** scrim tap, back and the caller's `onDismiss` all run the same lambda — that lambda
  may carry a side effect and must be preserved verbatim.
- **Hosts** own every string, the checkbox/field state and the side effects; they keep the component
  mounted with `visible = false` while it animates out.
- **The options slot's checkbox row is `OptionRow`**
  ([`ui/components/OptionRow.kt`](../app/src/main/java/ykws/android/maro/ui/components/OptionRow.kt)) —
  one checkbox and its label, the checkbox's own target inset serving as the gap, so every option row in
  every dialog reads the same distance.

**Tokens**

| Token | Default | Use |
|---|---|---|
| `ui.scrim.alpha` | 0.50 | Dim shared by the drawer ladder and the dialog |

> There is no `ui.dialog.bottom.lift` token — the panel is flush with the bottom edge.

---

### 5.7 Bottom-Band Banners — `MapBanner`

The map's bottom band carries six banner instances — the exit toast (`Press back again to exit` /
`Appuyez à nouveau pour quitter`), `LockBanner`, `MapStatusBanner`, `LoadingOverlay`, `ErrorOverlay`
and the action and undo rows of the snackbar stack (`SnackRow`, painted by `MapSnackbarHost`) — and all
six paint one skin through one control. This entry is the family's **only** home, and every instance
reads these rules — none is exempt.

Source: [`MapControls.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt) (`MapBanner`,
`bannerLineStyle`, `MapBannerText`, `bannerStartInset`, `LockBanner`, `MapStatusBanner`),
[`CoastlineMapView.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt)
(`LoadingOverlay`, `ErrorOverlay`),
[`MapSnackbarHost.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt) and
[`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) (`SnackRow`), with the
remaining call sites in `MapScreen.kt`.

**Contract**

- **One control, caller-owned content.** `MapBanner(borderColor, tagsDrawn, modifier,
  reservesControlColumn) { content }` owns the skin, the band clearance and the border colour; the
  instance's content arrives as the slot — the pill passes one `MapBannerText`, while `LoadingOverlay`
  and `ErrorOverlay` pass their own column and keep their own interiors (spinner, title, phase,
  percentage; title, message, Retry) and roles.
- **Skin — one definition, six users.** 14 dp corner, a 2 dp border in `borderColor` over
  `ui.card.background`, `ui.button.background` as the fill and an 8 dp shadow. No face carries a copy.
- **Border colour is the caller's**: `ui.dashboard.zone.danger` while recording and
  `ui.dashboard.background` otherwise, for the exit toast; `ui.dashboard.background` for `LockBanner`,
  `MapStatusBanner` and the action and undo rows; `ui.dashboard.zone.danger` for `ErrorOverlay`;
  `ui.dashboard.background` for `LoadingOverlay`.
- **Clearance, every instance.** A banner starts at `bannerStartInset(tagsDrawn)` and ends clear of the
  right control column (82 dp, `RIGHT_CONTROL_COLUMN_INSET`). `tagsDrawn` is the bottom-left tag
  stack's own answer, `regulatedZoneTags(...).isNotEmpty()`, derived once in `MapScreen` beside the set
  the stack paints: with a tag drawn the inset adds the tag column, empty it adds nothing.
- **The control owns the column reserve, not the caller.** `reservesControlColumn` carries the one fact
  the control cannot read from its own box — is the banner's parent full width — and `MapBanner` turns
  it into the `end` padding. True for `LockBanner`, `MapStatusBanner` and the action and undo rows,
  whose parent is the whole map area and which therefore genuinely need the reserve; false for the exit
  toast and the two cards, whose parent is the map's left overlay column and already ends where that
  column does (`docs/ui-drawer-guidelines.md` §1). `bannerStartInset` deliberately excludes the column
  either way.

**Banner face — the pill**

- **Wrap-content, centred in the band's free space.** The pill hugs its message and is centred in the
  region the band leaves free, so a short message is a small centred pill and a long one fills the
  region and wraps. No `fillMaxWidth` stretch, and `tagsDrawn` moves the centre by half the tag slot
  (25 dp at defaults) — adaptive rather than stable, decided 2026-09-21.
- **Text:** 16 sp Medium in `ui.toast.text`, **centred** (`MapBannerText`), on 16/10 padding — so a
  wrapped message reads centred inside its centred pill. The style itself is written once, in
  `bannerLineStyle`, which both this line and the action row's message read.
- **The wrap is uncapped:** no `maxLines`, no `ellipsis`. Both exit strings carry their instruction
  late (`… press back again to stop and exit`), so an ellipsis would cut it; the pill is
  bottom-anchored, so extra lines grow upward over the map.

**The three full-width faces — the two cards and the action row**

- **Full width**, keeping the 6 dp end gap they always had, on the same clearance and skin as the pill —
  the clearance rule binds them too, so a tag being drawn moves them 50 dp at defaults.
- **Their interiors are their own** — `LoadingOverlay` shows the spinner, title, phase and percentage,
  `ErrorOverlay` the title, message and Retry, and the action row its message beside its commands. The
  family owns none of them.
- **The action row's own interior** — one full-width line, message **left** with `Modifier.weight(1f)`,
  `maxLines = 2` and an ellipsis, **centred vertically**, then its commands **right and bottom**
  (2026-10-05, the user's word): each command rides `Modifier.align(Alignment.Bottom)`, so a two-line message
  centres against the row while its commands stay on the bottom edge rather than being dragged up with the
  first line. The message reads `bannerLineStyle`, aligned `Start`; each command is §5.6's
  `ConfirmActionButton` in its **compact `SECONDARY` face** — the same control, its size and its numbers home
  in §5.6 — so the row never hand-rolls a button of its own. It is drawn one above the other, up to three at
  once, under the snackbar stack's own dismiss contract (`docs/ui-lists-guidelines.md` §Swipe,
  **Delete lifecycle**).

**Numbers** (shipped defaults — this table is the only place they are written)

| Quantity | Value | Home |
|---|---|---|
| Band gutter | 6 dp | `ui.map.toggle.gutter` |
| Tag square (the tag column's width) | 44 dp | `ui.map.toggle.square` |
| Gap between the tag column and the info text | 6 dp | `ui.map.overlay.gap` |
| `bannerStartInset(tagsDrawn = true)` | 56 dp | 6 + 44 + 6 |
| Card end gap | 6 dp | the band's gutter value (`ui.map.toggle.gutter`), held by the caller; equal to the card's own start inset only while no tag is drawn |
| Right control column | 82 dp | `RIGHT_CONTROL_COLUMN_INSET`, reserved by the caller |

At defaults on a 411 dp screen the pill centres at `W/2 − 13` (192.5 dp) with a tag up and `W/2 − 38`
(167.5 dp) with none. The centring has no Compose harness in this repo (`app/src` carries `main/` and
`test/` only), so it is a device judgement; the inset arithmetic is covered by
[`BannerStartInsetTest`](../app/src/test/java/ykws/android/maro/ui/map/BannerStartInsetTest.kt).

---

### 5.8 Route Panel — `RouteConfirmationPanel`

The dashboard slot's content while a route is being acquired
([`RouteConfirmPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt)). It is the
shared [`DrawerScaffold`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) with
`showBack = false`, `wrapContent = !isLandscape`, `wrapContentMinHeight = dashboardBaseHeight` and the
actions in its `footer`, so it auto-grows to its content like the other selected-item dashboards.

| Block | Spec |
|---|---|
| Header | the title; its trailing slot carries the stage status and the ‹ › dots (shown while more than one route stands) |
| Body | a bordered three-column table — the description (0.75 of the comparison column), the route's Dist · ETA as right-aligned value + left-aligned unit pairs, and a candidate's delta against the selected route with the forced-crossing note — hairline column separators, wrapping top-aligned rows, the selected row on the taken-choice face (`ui.select.container` fill, 1dp `ui.accent` edge, white bold text), paging laterally by swipe or the ‹ › pair |
| Footer | `Save to track` · `Select route` · `Discard route` in one weighted row — §5.6's `ConfirmActionButton`, SECONDARY · PRIMARY · DANGER |

**The pending mark — authority.** A figure the mode does not hold yet prints
[`R.string.route_value_pending`](../app/src/main/res/values/strings.xml) (`--`) wherever it would stand — in this
panel's table and in the drawer's route cells alike — with its unit still beside it. One word for the whole app,
never a literal in Kotlin: no zero, no blank slot and no per-surface glyph stands in for a missing figure.

---

### 5.9 Actions — the four tiers (authority)

**What an action is, and how it clothes itself.** §5.6 owns the **button family** — `ConfirmAction` /
`ConfirmActionButton` — and its role model is the spine: the accent is the surface's own forward outcome
and **only one per surface**, the red is what withholds the work, the outline is everything that neither
writes nor loses, the order is affirmative → neutral → destructive, and a disabled action wears the
outlined face. That family is **tier 1** below; the other four tiers are the other shapes an action takes,
and this table is the one place that names them.

| Tier | Shape | When it is used | Home |
|---|---|---|---|
| 1 | Accent-filled — **at 50 % under a 2 dp accent rim for a middle action** — or red-filled **full-width button**; **compact** (wraps its label, 8 dp corner, 28 dp, 12 sp) wherever it stands off the dialog's own stack | A surface's own outcome, its loss, or its middle doors — dialogs, the route panel, the Route sub-section's `Route`, the Menu's Import/Export pair, which wears the **half-strength accent with its full-opacity rim**, and the map action row's commands, which wear that same face compact (§5.7) | `ConfirmActionButton` (§5.6) |
| 2 | **Icon-only button**, 40–48 dp | A control belonging to a header or a card's chrome — link, filter reset, gear, chevrons | `IconButton` |
| 3 | **Map status square acting as a button** | A mode's own on/off that also reports a state — GPS, tracking, lock, recenter | `MapToggleSquare` family (§5.5) |
| 4 | **Row-level action** | An action belonging to a list row — swipe, chevron gutter, header trash, Undo / Clear / Select-all | per list (§9 of [`ui-drawer-guidelines.md`](ui-drawer-guidelines.md)) |

**Selections are not actions.** The accent **fill** belongs to an action, and a **selection wears a marker
instead**: a chosen choice takes the tonal container `ui.select.container` — the accent at **30 %** — with a
**1dp accent border** and a check glyph (§2.7 / §2.7b), a value read-out over a stepped set takes the accent
in **bold text with no fill**, and a middle action wears the accent as a **50 % fill under a 2 dp accent
rim**: the two ranks of fill are told apart by weight and by context, never by hue
(the dropdown's value, §2.12), and a card or row picked out of a list takes an **accent border with a check mark and
no fill** (§9 of [`ui-drawer-guidelines.md`](ui-drawer-guidelines.md)) — on a list-item card that is the **2 dp
`uiAccent` border**, the 15 % tonal shift and the 24 dp
[`ListSelectionCheck`](../app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt) disc at the head
of the first line, entered through the leading door's
[`ListSelectionTouchZone`](../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt). Binary controls are the deliberate
exception, following M3: a selected `Switch` and a selected `Checkbox` take the primary role, so `ToggleRow`
and `OptionRow` keep `uiAccent` and are consistent rather than exempt. The reserved thing is therefore
narrower than "the accent is for actions": it is **the accent as a filled container on a group of choices**.

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

**Do not apply padding at a higher level** (e.g. `Surface` in `MainActivity`) — it would offset the full-screen map. Apply at the screen root `Box` only.
