<!-- scope: feature -->
# Ui_Settings — Regulated zones transparency (Appearance expander)

**Status:** in design · **Date:** 2026-09-18 · **Branch:** `feature/zones-transparency-settings` (from `origin/develop`)
**Scope:** one persisted pair in `SettingsManager`, one new expander in the Regulated zones card of the Layers tab, the alpha derivation for the regulated-zone polygons, and the plumbing through `CoastlineMapView` and `MapScreen`.
**Relation:** extends the transparency paradigm (Ui_Settings, transparency-normalization 2026-09-06) and mirrors the 300 m band's Appearance expander one-for-one. Cross-feature: the rendered artefact belongs to RegulatedZones — its display-layer contract moves from two hard-coded alphas to a user-set pair.
**Line references** are as of 2026-09-18 and drift — re-locate by symbol.

## Request

- A setting that controls the transparency of the regulated zones drawn on the map, as a two-thumb slider mimicking the 300 m band's transparency control.
- Placement as specified: a collapsible **Regulated Zones Appearance** expander, **first** in the Regulated zones card, holding the Transparency heading, a comment, then the double slider.

## Current behaviour (evidence)

| Aspect | Regulated zones | 300 m band |
|---|---|---|
| Fill alpha | `0x30000000` hard-coded in each of the 8 branches of `regulatedZoneColor()` — ≈18.8% opaque | derived from `zone300FillTransparencyPct`, default 80 |
| Outline alpha | `outlinePaint.alpha = 200` hard-coded — ≈78.4% opaque | derived from `zone300BoundaryTransparencyPct`, default 20 |
| User control | none | `Expander` (300m Zone Appearance) → `SubSectionHeader` (Transparency) → `RangeSliderRow` |

- By Android `Paint` semantics — `setAlpha` replaces the colour's packed alpha rather than multiplying it — the `#CC` prefixes on `regulatedZone.type.*` in `colors.properties` cannot reach the outline today; this is inferred from the API contract, not observed on device.
- Both alphas are reachable from one integer percentage, so the pair can be persisted and applied without touching the per-type hues at all.

## Decisions (settled with the user, 2026-09-18)

| # | Decision | Rationale / cost |
|---|---|---|
| D1 | The transparency applies to the zone **polygons** — fill and outline — only. The bottom-left icon stack and the info panel keep their opaque colours. | The polygons are the artefact the user called the zones; dimming tags would damage legibility. |
| D2 | **One** border + fill pair covers all eight categories. | Every category already shares both alphas and differs only by hue; per-category pairs would mean eight sliders. |
| D3 | A new expander **Regulated Zones Appearance** sits **first** in the Regulated zones card, so the order becomes Appearance → Info text panel → Zone categories. | The reorder is free: expander state is keyed by string (`reg_info`, `reg_categories`), never by index, so nothing persisted depends on position. The key `reg_appearance` needs no registration — `isExpanded` falls back to collapsed, so the expander ships collapsed like the 300 m Appearance one, and expansion state is session-lived either way. |
| D4 | Inside: `Expander` → `NestedCard` → `SubSectionHeader` (Transparency + the comment) → `RangeSliderRow(label = null)`. | §2.8 makes the row label optional when a `SubSectionHeader` supplies the heading; a single row needs no `SectionDivider`. |
| D5 | Defaults **border 20 / fill 80** — the 300 m defaults, both on the 5% grid. | Deliberate trade: the fill moves alpha 48 → 51 and the outline 200 → 204. The exact-preserving pair (81 / 22) is off-grid, so the control could never reproduce its own shipped default. |
| D6 | Behaviour mirrors the 300 m row: local drag state, `steps = 19`, label and commit snapped to 5%, commit only in `onValueChangeFinished`. | Keeps the mimic honest and keeps one idiom for both rows. |
| D7 | No clamp between the thumbs. | Same as the 300 m row; the thumbs are independent quantities rather than a min/max pair. The cost is that inverted thumbs are visually permitted. |
| D8 | One shared pure helper for transparency → alpha, used by the 300 m and the regulated-zone paths. | Two copies of the same arithmetic are two chances to diverge. |
| D9 | Strings: share the subject-free heading and value format, keep each control's own description. | See the table below; `AGENTS.md` deletes a duplicate rather than keeping it in sync. |

## Transparency → alpha (one home)

```kotlin
/** 0 = opaque, 100 = invisible. Mirrors the historical 300 m band maths: ((100 - pct) * 255) / 100. */
internal fun transparencyPctToAlpha(pct: Int): Int = ((100 - pct.coerceIn(0, 100)) * 255) / 100
```

- Lives in `MapOverlayRenderer.kt` beside `drawZone300` and `drawRegulatedZones`, replacing the identical inline expression inside `drawZone300`.
- Reference values: 0 → 255, 20 → 204, 80 → 51, 100 → 0. Integer division truncates, so an arbitrary percentage does not round-trip to every alpha.

## Implementation scope

1. `data/settings/SettingsManager.kt` — `AppSettings` gains `regulatedZoneFillTransparencyPct: Int = 80` and `regulatedZoneBoundaryTransparencyPct: Int = 20` with KDoc in the transparency vocabulary; read in the prefs builder; written in the apply builder; keys `regulated_zone_fill_transparency_pct` and `regulated_zone_boundary_transparency_pct`.
2. `ui/map/MapOverlayRenderer.kt` — hoist `transparencyPctToAlpha`; `regulatedZoneColor()` collapses to the single per-type colour (the `RegulationZoneColor` pair is deleted, since with the alphas user-owned its two fields could only ever hold the same value) and stops baking `0x30000000`; `drawRegulatedZones` gains `fillTransparencyPct` and `boundaryTransparencyPct`, applies `Color.argb(...)` to the fill and `outlinePaint.alpha` to the outline.
3. `ui/map/CoastlineMapView.kt` — two new parameters; the factory draw call plus the two `last*` seeds beside `lastRegulatedZones` (unseeded, the first composition would remove and redraw the zones); `OverlayTracker` gains `lastRegZoneFillTransparencyPct` and `lastRegZoneBoundaryTransparencyPct`; the Regulated zones `LaunchedEffect` gains both values in its keys **and in its early-return guard** — the guard compares only zone identity and zoom, so percentages left out of the guard would swallow every slider commit and the setting would look dead until the next zoom change.
4. `ui/map/MapScreen.kt` — pass both values from `appSettings` at the `CoastlineMapView(...)` call site.
5. `ui/map/MapScreenSettingsOverlay.kt` — the new expander as the first child of the Regulated zones `CardArea`, expanded state keyed `reg_appearance`.
6. `res/values/strings.xml` + `res/values-fr/strings.xml` — the keys below.

## Strings

| Key | EN | FR |
|---|---|---|
| `settings_regulated_zones_appearance_label` (new) | Regulated Zones Appearance | Apparence des zones réglementées |
| `settings_transparency_border_fill_label` (shared) | Transparency | Transparence |
| `settings_transparency_border_fill_value_fmt` (shared) | Border %1$d%% · Fill %2$d%% | Bord %1$d%% · Remplissage %2$d%% |
| `settings_regulated_zones_transparency_desc` (new, this row only) | Regulated zone fill and border transparency (0% = opaque, 100% = invisible) | Transparence du remplissage et du contour des zones réglementées (0% = opaque, 100% = invisible) |

- The shared heading replaces `settings_zone300_opacity_label` and `settings_marker_halo_transparency_label` (2 call sites); the shared value format replaces `settings_zone300_opacity_value_fmt` and `settings_marker_halo_value_fmt` (3 call sites). The old keys are deleted rather than left behind.
- The heading is named `…_border_fill_label` and deliberately not `settings_transparency_label`, which is already live as the tracks row's own label ("Not-pinned transparency" / "Transparence des non épinglées") — reusing that key would print a track-specific sentence on the zone heading, and re-declaring it would be a duplicate resource.
- The two existing pairs are byte-identical in English and differ only by "Bord" / "Contour" in French; "Bord" is the survivor, matching §2.8's wording.
- Descriptions stay per control because each names its own subject; the 300 m band's own description is untouched.
- If the consolidation is not wanted, the fallback is that the new row adds its own two keys and the two existing pairs stay in place.

## Verification

1. New test beside the renderer: pins `transparencyPctToAlpha` at 0 / 20 / 80 / 100 plus out-of-range clamping, and asserts `regulatedZoneColor` returns an opaque fill for every type.
2. `apk-build.bat` green; the scoped `ui.map` + `config` test run steady against its known baseline.
3. Device: the new row at both extremes and at 0% (fully opaque), plus one confirmation that an untouched install looks as it did before.
4. Device: the three expanders of the Regulated zones card all open and collapse within the session — the reorder must not have disturbed `reg_info` / `reg_categories`. Expansion state is session-lived (`SettingsViewModel.expanderStates`), so it is not expected to survive a restart.

## Doc sync

- `docs/ui-component-guidelines.md` §2.8 — the app-wide transparency enumeration gains regulated zones; it currently names tracks, marker halo, 300 m band and low-depth warning.
- `docs/color-scheme.md` — the `regulatedZone.type.*` table states that the polygon fill and outline alpha now come from the settings pair instead of a fixed value.
- Feature files — `Ui_Settings` (`## Implemented` one-liner plus the `## Docs` pointer) and `RegulatedZones` (display-layer contract now settings-driven); `GLOBAL_CONTEXT.md` summary row for Ui_Settings.
- Observed drift, logged and not fixed: that same table lists `#FFFF8F00` and `#FFE53935` while the palette resolves `semantic.caution` and `semantic.danger` (`#CC…`), and those alphas never reach the outline anyway.

## Warts / follow-ups (out of scope)

- `map.zone300.fill=#30E53935` in `colors.properties` has no reader once the fill alpha comes from the setting — a dead key.
- The `#CC` alphas on `regulatedZone.type.anchoringProhibited`, `accessProhibited` and `environmental` are unreachable on the outline because `Paint.alpha` replaces them (inferred, see the evidence table); either respect or remove them in a later pass.

## Outcome

Implemented 2026-09-18. Deviations and findings, recorded rather than smoothed over:

- **The `RegulationZoneColor` pair was deleted.** The plan said `regulatedZoneColor()` returns opaque fills; with the alphas user-owned its two fields could only ever hold the same value, so the function now returns the per-type hue alone and `drawRegulatedZones()` supplies both alphas. That is the one deliberate departure from the approved text.
- **The shared heading is `settings_transparency_border_fill_label`**, not this plan's original `settings_transparency_label` — the latter is live for the tracks row ("Not-pinned transparency" / "Transparence des non épinglées"), caught in the plan review as F1 before it could break the build.
- **The review's other fixes all landed as planned:** the two `last*` seeds beside `lastRegulatedZones`, the transparency pair inside the early-return guard (not only in the `LaunchedEffect` keys), the collapsed session-lived expander state, and the softened `Paint`-semantics wording.
- **A new AAPT warning was caught and removed** — the new description needed `formatted="false"` for its two `%` characters, leaving only the pre-existing `settings_zone300_opacity_desc` warning.
- **The scoped `ui.map` + `config` run reports 189 tests with 5 failures**, all of them the documented pre-existing `maro.properties`-versus-`AppConfig` reds (`HeatmapRampPropertiesTest`, `TrackOutlineTest` — both read shipped widths and touch nothing this change edited). `MapOverlayRendererTest` is green and is the first test in the project to cover a transparency derivation.
- **Deferred to `#bake`:** the `GLOBAL_CONTEXT.md` Feature Summaries row for Ui_Settings; the Focus History entry was pushed by `#focus`.
- **Open for the device pass:** the row at 0% and at both extremes, the three expanders after the reorder, and the untouched-install look.
- **Logged, not fixed:** `map.zone300.fill` is now fully readerless, and the `#CC` alphas on three `regulatedZone.type.*` entries are unreachable on the outline.

[appended at completion]
