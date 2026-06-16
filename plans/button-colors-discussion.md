# Button Colors — Discussion

> Feature: [`Ui_General`](../xTrack/Ui_General/FEAT_DSC_Ui_General.md) — subfeature `ButtonColors`
> Branch: `feature/btn--colors`
> Canonical color reference: [`docs/color-scheme.md`](../docs/color-scheme.md)

---

## 1. Buttons in Scope

### `ui.button.*` token set (4 properties)

Controls **all right-edge control-stack buttons** — the vertical column of round white
circles at the right edge of the map screen:

| Button | Role | Toggle? | Notes |
|---|---|---|---|
| **Settings gear** | Opens settings overlay | No (always visible) | Top of stack; uses `ui.button.*` because it's part of the right-edge stack |
| **Fan parent** | Three-stripe icon; opens/closes the fan | Yes | Toggle ON = fan visible; OFF = fan collapsed |
| **Depth overlay** | Fan child; toggles depth shading overlay | Yes | Icon: depth-layer glyph |
| **Regulated zones** | Fan child; toggles regulated-zone overlay | Yes | Icon: regulated-zone glyph |
| **300m zone** | Fan child; toggles 300m-band overlay | Yes | Icon: 300m-band glyph |
| **Danger zone** | Fan child; toggles danger overlay | Yes | Icon: danger/warning glyph |
| **Zoom +** | Zoom in | No (momentary) | Icon: `+` glyph |
| **Zoom −** | Zoom out | No (momentary) | Icon: `−` glyph |
| **Active-child badge** | Shows count of active fan children | No (always visible) | Small badge circle over parent |

All these use the uniform `ui.button.background` fill (`#CCFFFFFF`) and
`ui.button.icon` colour (`#1565C0`), with toggle state handled by
`ui.button.iconActiveAlpha` / `ui.button.iconInactiveAlpha` (alpha-only, same hue).

### `ui.arc.anchor.*` token set (2 properties)

Controls a single special button:

| Button | Role | Toggle? | Notes |
|---|---|---|---|
| **Arc anchor** | Opens/closes the arc layout (depth/shadow toggle) | Yes | Separate properties from `ui.button.*` |

The anchor uses `ui.arc.anchor.background` (`#CCFFFFFF` — same default as
`ui.button.background`) and `ui.arc.anchor.color` (`#FF1565C0` — fully opaque
blue, used for both the anchor icon and the badge background).

### Excluded from these tokens

| Button | Token set | Reason |
|---|---|---|
| **GPS status icon** | `status.gps.*` | Top-left 44dp rounded square; functional status indicator (green/amber/red/blue for acquiring/healthy/idle/stale/demo) — not a round white button |
| **EarthWater icon** | `status.earthWater.*` | Top-left 44dp rounded square beside GPS; state-based (water/land/inactive) — not a round white button |

The Settings **gear icon itself** *is* controlled by `ui.button.*` because it sits
in the right-edge stack. The Settings **overlay background / text** are controlled
by `ui.settings.*` tokens (see [`docs/color-scheme.md`](../docs/color-scheme.md) §7).

---

## 2. Current Visual State

All right-edge control-stack buttons present as **white circles with blue icons**:

```
┌──────────────────────────────────────┐
│  ┌──────┐                            │
│  │  ⚙️  │  ← Settings gear           │
│  │  blue│    white circle             │
│  └──────┘                            │
│  ┌──────┐   ┌──┐                     │
│  │  ≡   │   │3││  ← Fan parent +     │
│  │  blue│   │blue│    active-child    │
│  └──────┘   └──┘       badge         │
│  ┌──────┐                            │
│  │  ⊞   │  ← Fan children (depth,    │
│  │  blue│    regulated, 300m, danger) │
│  └──────┘                            │
│  ┌──────┐                            │
│  │  +   │  ← Zoom in                 │
│  │  blue│                            │
│  └──────┘                            │
│  ┌──────┐                            │
│  │  −   │  ← Zoom out                │
│  │  blue│                            │
│  └──────┘                            │
└──────────────────────────────────────┘
```

**Button circle fill:** `#CCFFFFFF` — 80% opaque white. The map content shows
through slightly, which can reduce contrast against light or shallow map areas.

**Icon colour:** `#1565C0` — Material Blue 800. All icons are this blue
regardless of button type or toggle state.

**Active/inactive distinction:** Alpha-only. Toggle-ON icons render at
`iconActiveAlpha` (1.0 = fully opaque blue). Toggle-OFF icons render at
`iconInactiveAlpha` (0.25 = very faded blue). There is **no hue change** —
the same blue is simply more or less transparent.

**Arc anchor button:** Same visual formula — white circle (`#CCFFFFFF`),
blue icon (`#FF1565C0` fully opaque), but uses a separate set of properties
(`ui.arc.anchor.*`).

**Result:** The buttons stand out brightly against the dark-themed app
(dashboard background `#1A1A2E`, card background `#16213E`). They look
more like floating action buttons than integrated UI elements.

---

## 3. What Can Be Changed (Without Code Changes)

Per [`docs/color-scheme.md`](../docs/color-scheme.md) design principle:
> Edit the `.properties` file, rebuild the APK — no code changes needed.

The runtime-loading pipeline is:

```
colors.properties → AppConfig.init() → ButtonColors (FanIconComponents.kt:28)
                                    → ArcLayoutToggle (ArcLayoutToggle.kt:60)
```

### Changeable `ui.button.*` properties

| Property | Default | AppConfig field | Controls |
|---|---|---|---|
| `ui.button.background` | `#CCFFFFFF` | `buttonActionBgColor` | Fill colour of every right-edge round button circle |
| `ui.button.icon` | `#1565C0` | `buttonActionIconColor` | Colour of all icon symbols (gear, +/−, layer glyphs, badge) |
| `ui.button.iconActiveAlpha` | `1.0` | `buttonActionIconActiveAlpha` | Icon opacity when toggle is ON or icon is static |
| `ui.button.iconInactiveAlpha` | `0.25` | `buttonActionIconInactiveAlpha` | Icon opacity when toggle is OFF |

### Changeable `ui.arc.anchor.*` properties

| Property | Default | AppConfig field | Controls |
|---|---|---|---|
| `ui.arc.anchor.color` | `#FF1565C0` | `uiArcAnchorColor` | Arc anchor icon colour AND badge background |
| `ui.arc.anchor.background` | `#CCFFFFFF` | `uiArcAnchorBackground` | Arc anchor button circle fill |

**Important:** `ui.arc.anchor.color` serves double duty — it's both the anchor
icon colour and the **badge background** colour for the active-child count badge
(the small 18dp circle over the fan parent). Changing it affects both.

---

## 4. Possible Directions for Exploration

### 4a. Dark Theme Harmony

The app uses a dark dashboard palette (`#1A1A2E` background, `#16213E` cardBg).
The white buttons are the brightest elements on screen.

**Options:**

| Background | Pros | Cons |
|---|---|---|
| `#CC16213E` (semi-transparent cardBg) | Matches dashboard tiles, blends in | Map shows through; contrast issues over dark water |
| `#FF16213E` (solid cardBg) | Solid, predictable contrast | Flattens into dashboard; need high-contrast icons |
| `#CC1A1A2E` (semi-transparent outer bg) | Matches Settings/outer panel | Same transparency issues |
| `#FF1A1A2E` (solid outer bg) | Solid, darkest possible | Even flatter against map |
| Keep white (`#CCFFFFFF`) | High contrast, distinct | Stands out from dark theme |

The existing [`plans/btn-color-harmonization.md`](../plans/btn-color-harmonization.md)
covers this direction extensively — including a post-mortem of why
semi-transparent dark bg failed (§ "Post-Mortem — Why Option A failed").

### 4b. Icon Color Variants

Currently `#1565C0` (blue) on white circles. Alternatives to consider:

| Icon colour | Hex | Contrast on white | Mood |
|---|---|---|---|
| Current blue | `#1565C0` | ~3:1 (marginal) | Neutral, standard |
| White | `#FFFFFF` | 1:1 (invisible on white bg) | Only works with dark bg |
| Dashboard textPrimary | `#E0E0E0` | ~1.2:1 on white (invisible) | Only works with dark bg |
| Status success green | `#4CAF50` | ~2.3:1 on white | Positive/active feel |
| Status warning amber | `#EF6C00` | ~2.8:1 on white | Warm accent |
| Settings accent | `#1565C0` (= current) | ~3:1 | Already the accent |
| Dark navy | `#1A1A2E` | ~6:1 on white | High contrast, matches dashboard |

If background changes to dark (`#16213E`), icon options shift:

| Icon colour | Hex | Contrast on `#16213E` |
|---|---|---|
| White | `#FFFFFF` | **~13:1** — WCAG AAA |
| Current blue | `#1565C0` | ~2.9:1 — marginal |
| Dashboard textPrimary | `#E0E0E0` | ~8:1 — good |
| Success green | `#4CAF50` | ~6:1 — okay |

### 4c. Background Opacity Tuning

The current `#CC` alpha prefix = 80% opacity. Adjusting this affects how much
the underlying map shows through the button circles:

| Alpha | Hex prefix | Effect |
|---|---|---|
| 100% | `#FF` | Solid fill; no map bleed-through; maximum contrast for icons |
| 80% | `#CC` | Current; subtle map show-through |
| 60% | `#99` | More transparent; buttons visibly float above map |
| 0% | (no bg) | Icons directly on map; maximum map visibility |

Solid (`#FF`) eliminates the transparency-contrast instability — the icon always
renders against the designed background colour regardless of what's on the map
underneath.

### 4d. Per-Button Differentiation

Currently all buttons are uniform. Possible differentiation strategies:

| Strategy | Approach | Risk |
|---|---|---|
| **Type-based** | Settings always one colour, fan children another, zoom another | Fragments visual language; more properties needed |
| **State-based glow** | Active buttons get a subtle glow/halo | Cannot do via `colors.properties` alone — needs code |
| **Position only** | Keep uniform colour, rely on position/icon shape for identity | Current approach; simplest |
| **Toggle hue** | Active = blue, inactive = grey (hue switch instead of alpha) | Different toggle language from alpha-only; inconsistent across buttons |

The current design intentionally uses **uniform appearance with positional
differentiation** — buttons are identified by their icon glyph and position
in the stack, not by colour. Any per-button differentiation would require
either new `colors.properties` tokens or code changes.

### 4e. Arc Anchor Harmony

The arc anchor button currently:
- Uses the same white bg default (`#CCFFFFFF`) as all other buttons — but via
  its own `ui.arc.anchor.background` property
- Uses fully opaque blue (`#FF1565C0`) for its icon, vs `#1565C0` for other
  buttons — visually identical since `ui.button.icon` at 1.0 alpha = `#FF1565C0`

**Key difference:** `ui.arc.anchor.color` also controls the active-child badge
background (the small 18dp circle showing "3" over the fan parent). Changing the
anchor colour affects both the anchor icon and the badge.

**Options:**
- Keep aligned with `ui.button.*` tokens (same colour family)
- Make anchor intentionally distinct (different colour to mark it as the
  "root" control)
- Separate badge colour from anchor colour (would require new property)

---

## 5. Non-Color Concerns (Explicitly Out of Scope)

This discussion covers **fill colours and icon colours only**. The following are
**not** in scope for color changes via `colors.properties`:

| Concern | Reason out of scope | Controlled by |
|---|---|---|
| Icon SVG shapes | Icon vector geometry is in code | `FanIconComponents.kt` draw calls |
| Button size / padding | Dimension changes need code | `MapControlButton.kt` modifiers |
| Badge position / offset | Layout changes need code | `FanLayout.kt` / `ArcLayoutToggle.kt` |
| Button spacing | Layout changes need code | `MapScreen.kt` Column arrangement |
| Animation / transitions | Alpha transitions in code | `FanLayout.kt` animate* calls |

---

## 6. Summary of Existing Related Plans

| Document | Coverage |
|---|---|
| [`plans/btn-color-harmonization.md`](../plans/btn-color-harmonization.md) | Full harmonization proposal: dark bg + white icons; post-mortem of failed Option A; normalization of all buttons to `MapControlButton` |
| [`plans/color-props-migration-plan.md`](../plans/color-props-migration-plan.md) | Migration plan to move ALL colours into `colors.properties` (broader scope) |
| [`docs/color-scheme.md`](../docs/color-scheme.md) | Canonical reference for all colour tokens |
