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
        Spacer(${ui.padding.card.horizontal})   ← after last expander, matches card h-pad
    }
}
```

### 2.4 Inside-Expander Content

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

`SettingsExpander` defaults: `uiSettingsTextPrimary`, 14sp, Medium. Never override `labelStyle` per call site.

### 2.6 Section Dividers

**Visible divider** (`#26FFFFFF`, 6dp gap above/below) between distinct content blocks inside a card (e.g., "Colors" vs sliders in Track settings). **Spacer only** (8dp) between simple toggle rows that are not sections (e.g., Categories).

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
| Expander→content | header+8dp spacer | 8dp |
| Last expander→card close | `ui.spacing.header.bottom` | 8dp |

Full token list: [`ui-tokens.properties`](../app/src/main/assets/ui-tokens.properties).

---

## 4. Anti-Patterns

- ❌ `SettingsToggleRow` inside a grouped card (double-wrapping)
- ❌ Inner content card using `uiCardBackground` (stacked 15% white)
- ❌ Per-call `labelStyle` on `SettingsExpander`
- ❌ Visible dividers between top-level cards (use spacer)
- ❌ `SliderRowContent(label="", …)` (use inline Row+Slider)

---

## 5. Non-Settings Surfaces

### 5.1 Drawer Cards (`MenuDrawerOverlay`)

Same `uiCardBackground` + 12dp radius. Rows: 16×10dp pad, `heightIn(min = 48dp)` touch target. Between cards: `${ui.spacing.header.bottom}`.

### 5.2 List Item Cards (`TrackHistoryOverlay` + `MarkerManagementOverlay`)

Unified pattern documented in [`ui-drawer-guidelines.md` §9](ui-drawer-guidelines.md#9-list-item-card-pattern-track--marker). Shell: `Row(height(IntrinsicSize.Min), clip(12dp), uiCardBackground)` + `Box(4dp, fillMaxHeight, accentColor)` + `Column(weight 1f, pad 8×4dp)`. Shared tokens for header (11sp muted), title (15sp SemiBold white), detail (14sp white), comment (13sp muted), action icons (`IconButton(36dp)` + `Icon(24dp, tint=ButtonColors.icon)`). Per-type variations in accent color source and metadata format.

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
