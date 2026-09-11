# Ui_Settings — Sub-section title colour (grey headings inside cards)

**Status:** planned, decision-complete (not implemented) · **Date:** 2026-09-11 · **Branch:** `feature/settings-menu-clean`
**Scope:** the sub-section titles rendered by [`SubSectionHeader`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1643) and [`SingleColorSubSection`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1669) (the 7 call sites inherit), §2.9 of [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md), and one comment in [`colors.properties`](../../app/src/main/assets/colors.properties:148).
**Relation:** follow-on to the row-family normalization ([`260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md`](260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md)). **Not part of R8** — captured here so R8 can close without dragging a behaviour change into a docs commit.
**Line references** are as of 2026-09-11 and drift — re-locate by symbol.

## Current roles and values (evidence)

| Element | Colour | Size / weight |
|---|---|---|
| `SectionHeader` (top-level section title) | `ui.settings.accent` | 18sp Bold |
| `SubSectionHeader` title | `ui.settings.text.muted` (`#B0BEC5`) | 16sp SemiBold |
| `SubSectionHeader` description | `ui.settings.text.secondary` (`#78909C`) | 13sp |
| `SingleColorSubSection` title | `ui.settings.text.muted` (`#B0BEC5`) | 16sp SemiBold |
| `Expander` label + row labels (`ToggleRow`, `SliderRow`) | `ui.settings.text.primary` (`#FFFFFF`) | 16sp |
| `CardDescription` | `ui.settings.text.muted` (`#B0BEC5`) | 13sp |

## Problem

- Inside a card, a **muted 16sp SemiBold** sub-section title sits next to **white 16sp** row labels. Same size, no weight contrast: the grey heading reads as *accidental* and the visual hierarchy is **inverted** (the de-emphasised-looking text is the heading, the bright text is the content).
- `ui.settings.text.muted` is overloaded: it is both the 16sp heading colour *and* the 13sp explanatory colour (`CardDescription`, `ToggleRow` description), so it no longer encodes one role.
- The token's own stated purpose disagrees with §2.9. [`colors.properties`](../../app/src/main/assets/colors.properties:148) comments `primary` as **"Section titles and primary labels"** and `muted` as **"Descriptive text and switch labels"** — yet §2.9 paints headings with `muted`.
- **7 affected titles** via **2 composables**:
  - Tracks → direction: **×3**
  - Markers → halo: **×2**
  - 300 m band ("Zone color" and its sibling): **×2**

## Options

| Option | Change | Trade-off |
|---|---|---|
| **A** | Promote sub-section titles to `ui.settings.text.primary` (`#FFFFFF`); keep 16sp SemiBold; express hierarchy through **weight + spacing** instead of colour. | Headings become bright — must not blend into the white row labels beneath them; relies on SemiBold + the 2dp gap. |
| **B** | Keep the grey but demote sub-section titles to a **14sp caption**. | Preserves de-emphasis, but changes size hierarchy and keeps `muted` overloaded. |
| **C** | Leave the code as-is and re-document `muted` as the heading colour. | Zero churn, but cements the inverted hierarchy and contradicts the token comment. |

## Decision

**Option A** — promote `SubSectionHeader` + `SingleColorSubSection` titles to `ui.settings.text.primary`, keep 16sp SemiBold, and get hierarchy from **weight (SemiBold vs Medium) + spacing** rather than colour.

**Fallback:** if a device check shows the headings blending into the rows, adopt **B's 14sp caption sizing** rather than reintroducing grey — i.e. keep `primary` and shrink, do not revert the colour.

## Scope when implemented

- [`SubSectionHeader`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1643) — title colour `muted` → `primary`.
- [`SingleColorSubSection`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1669) — title colour `muted` → `primary` (both the description-carrying and the swatch-on-title-line branches).
- The **7 call sites inherit** — no call-site edits.
- §2.9 of [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) — update the `SubSectionHeader` colour.
- The `ui.settings.text.muted` comment in [`colors.properties`](../../app/src/main/assets/colors.properties:148) — re-state its role (descriptive text only) if needed.

**No behaviour change** — colour + typography only; no setting, mapping or layout logic is touched.

## Verification (when implemented)

- Device pass on the Tracks → direction, Markers → halo, and 300 m band cards: headings legible, clearly above their rows, not blending.
- Fallback trigger is that same device pass.
