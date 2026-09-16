---
name: ColorManagement
status: active
created: 2026-06-16 14:05
modified: 2026-09-16 15:00
---

# Feature: Color Management

**Description:**
Centralised management of all colour tokens in the Maro-II app. Colours live in
`colors.properties` with `${key}` alias interpolation supported by `AppConfig`.
Every colour change must be reflected in `docs/color-scheme.md`.

## Sections

### Colour Taxonomy & Structure

Canonical colour taxonomy + naming convention. Hardcoded `ComposeColor.White` audited (46/48 fixed), stale AppConfig defaults synced, orphaned zone.properties fields removed, alpha/opacity centralized.

#### Todos
- [ ] Maintain the taxonomy in `docs/color-scheme.md` as the single source of truth

#### Key Files
- `app/src/main/assets/colors.properties`
- `docs/color-scheme.md`

### Color Scheme Documentation

`docs/color-scheme.md` is the canonical visual reference with swatches and alias chains.

#### Todos
- [ ] When adding/modifying a colour: update the corresponding table row
- [ ] Swatches must use 20×20 px inline HTML spans
- [ ] Aliased tokens must show `→ source.key = #HEX`

#### Key Files
- `docs/color-scheme.md`
- `app/src/main/assets/colors.properties`

## Implemented

- **zone-tag-alignment (2026-09-16, `feature/no-black-casing`)** — the map's bottom-left zone stack joined the surface family: `RegulationZoneCategoryIcon` is now painted by `MapToggleSquare` with `mapSurfaceFaceActive(colorForCategory())`, so the hand-painted 44 dp, 8 dp and fill went, the emoji takes `ui.map.toggle.icon.size`, the speed number drops to 26 sp inside the surface's 32 dp content box, the strike keeps its ratio, and the column gap, the strip's start inset and portrait clearance and the zone-info line's start gap all read shared keys; the alpha tier flattened onto `ui.map.surface.active.alpha`, retiring `alphaForCategory()` with the two `status.gps.alpha.*` keys. The Ask hop's fourteen findings were then closed in one remediation hop under the new one-home-per-fact rule: `MapSurface.kt` is the only home for the family's caller and exception lists, `colors.properties` is the source of truth for every value so `AppConfig`'s two surface alpha defaults and every doc moved onto it, the colour doc's copy of the member sentence and the recipe doc's restated numbers went, and the two retired keys gained their record. Two findings stay open for the user → `xTrack/ColorManagement/260916_FEAT_PLN_ColorManagement_map-surface-normalization.md` §11–§12
- **Alias Interpolation** — `${key}` resolver in `AppConfig.init()`; green→`status.success`, low-depth→`status.error`
- **ui-token-de-settings** — 14 shared UI tokens de-`settings`-ified (property keys + `AppConfig` accessors + all call sites + doc resync; `ui.settings.divider` → `ui.divider.color`), values and strings unchanged → `xTrack/ColorManagement/260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md`
- **map-surface-normalization (2026-09-16, `feature/no-black-casing`)** — one painting path and one property block for every map surface. `ui.map.surface.*` now owns the fill (`#A8FFFFFF`), the corner (8), the padding (6), the border (`${ui.divider.color}` at 1) and both alphas (`inactive.content.alpha` 0.45, `active.alpha` 0.75), and it is their only home: `ui.map.toggle.inactive.background`, `ui.map.overlay.background`, both `corner.radius` keys, `ui.map.overlay.padding`, both `ui.map.overlay.border.*` keys, `ui.map.toggle.inactive.icon.alpha` and `ui.map.toggle.active.background.alpha` all retired, with one colour accessor surviving — `uiMapSurfaceInactive`, which had no reader before this pass and now has all of them. New `ui/map/MapSurface.kt` paints the fill, corner, border, padding and a **content-only** fade, with `MapToggleSquare` on top adding the row's size and tap, so the whole-box `.alpha()` calls that made the tracking and lock off boxes land twice as faint are gone and those boxes read at the fill's own weight; the glyph dim's code default was realigned to the file's 0.45. Every surface now wears one corner and one outline, so the zone-info line gained the border it never had; the recenter button joined the family on `ui.accent` at the shared active alpha, still absent when there is nothing to recenter; the land/water square takes one resolved face with its reading unchanged; `ui.map.overlay.text.line.height` was added for the zone line's literal; and `LegendToggleButton` was deleted into the shared square, leaving the legend's gate and its collapse behaviour exactly as the previous entry shipped them. The Ask hop sent one defect back — the tracking dot sat at `22 − glyphBox/2` rather than 6 dp, drifting with the emoji's metrics and the font scale — fixed by sizing the content wrapper to the fixed padded box, alongside nine doc and comment corrections: two missing retirements, a false no-reader, a duplicated universal at four homes, a dead `contentDescription` and its call-site string, a lock KDoc that contradicted its glyphs, and the DEMO row's value cell, wrong since before this pass; `apk-build.bat` SUCCESS and the scoped `ui.map` + `config` run green at 126 cases, unmoved → `xTrack/ColorManagement/260916_FEAT_PLN_ColorManagement_map-surface-normalization.md`
- **map-chrome-families (2026-09-15/16, `feature/no-black-casing`)** — the map's toggle row and its two overlay cards were normalised onto two `ui.map.*` families over one shared surface: `ui.map.surface.inactive` (`#A8FFFFFF`, the single fill a switched-off button and a card wear, replacing `semantic.inactive` multiplied by an alpha), `ui.map.toggle.*` (inactive background and glyph dim, the active tint alpha superseding `status.gps/tracking/lock.alpha.active` for the row, and the square, gutter, corner radius and icon size that were literals in five composables) and `ui.map.overlay.*` (background, text colour and a 100–900 weight replacing the bold boolean, text size, corner radius, padding, border and the two gaps). Eighteen keys and their `AppConfig` properties arrived in one pass; the `ui.button.disabled.*` trio of the day before and the two retired `status.*.alpha.active` keys went, `status.gps.alpha.active` staying for the regulated-zone icons that read it; the palette's own doc gained both family sections with the retired rows removed, and the component guidelines' status-icon recipe moved onto the families. Converged values took the zone line from 9 to 10 sp, 3/1 to 6 dp padding and 4 to 8 dp corners, and the legend to a uniform 6 dp padding — each recorded as a risk the device must judge rather than special-cased with a second key → `xTrack/Tracks/FEAT_DSC_Tracks.md` and `xTrack/ZoneTile/FEAT_DSC_ZoneTile.md`

## Rules

- ALL colours go in `colors.properties` — never hardcoded
- Use `${key}` aliases when a colour is shared
- After ANY colour change, update `docs/color-scheme.md`
- Run `gradlew assembleDebug` after colour changes

## Key Files
- `app/src/main/assets/colors.properties`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `docs/color-scheme.md`

## Docs
- `docs/color-scheme.md`
- `xTrack/ColorManagement/260616_FEAT_PLN_ColorManagement_btn-color-harmonization.md` — Button color harmonization
- `xTrack/ColorManagement/260616_FEAT_PLN_ColorManagement_button-colors-discussion.md` — Button colors discussion
- `xTrack/ColorManagement/260616_FEAT_PLN_ColorManagement_props-migration.md` — Color props migration plan
- `xTrack/ColorManagement/260617_FEAT_PLN_ColorManagement_color-taxonomy-hardcoded-whites-audit.md` — Hardcoded ComposeColor.White audit and fix plan
- `xTrack/ColorManagement/260617_FEAT_PLN_ColorManagement_color-taxonomy-alpha-values.md` — Alpha/opacity value centralization plan
- `xTrack/ColorManagement/260916_FEAT_PLN_ColorManagement_map-surface-normalization.md` — One painting path and one property block for every map surface: the toggle squares, the collapsed legend square and both overlay cards (D1–D6)

## Walk
**Level 1 — Date:** 2026-09-16 · **Source:** the zone-tag normalisation discussion — the bottom-left icon stack's ten open points · **Active:** 10 · **Closed:** 2026-09-16
- [x] 1 · Plan home — resolved: amend this feature's shipped map-surface plan, striking its §8 bullet and Ask finding 14
- [x] 2 · Square key read by a non-control — resolved: the tags read `ui.map.toggle.square`, and the palette comment plus the plan state that on purpose
- [x] 3 · Border on a 44 dp tag — resolved: comes with the family rather than as a separate call
- [x] 4 · Stack gap 2 to 6 dp — resolved: the row's gutter, the same key the squares use
- [x] 5 · Bottom 6 dp literal — resolved: the start inset and the portrait bottom clearance both read the gutter key
- [x] 6 · Stack-to-text gap 4 dp — resolved: the info text takes `ui.map.overlay.gap`, 6 dp
- [x] 7 · Palette comment and MapSurface KDoc — resolved: every claim describing the old boundary is rewritten at all four homes, the palette block, both KDocs, `AppConfig`'s comment and the colour doc, with the plan's §8 struck
- [x] 8 · status.gps.alpha names untouched — resolved the other way: both keys retire, `alphaForCategory()` with them
- [x] 9 · Glyph sizes 24 and 28 untouched — resolved the other way: both retune to the 32 dp content box, the emoji onto the shared size and the number to 26 sp
- [x] 10 · Settings preview icon out of scope — resolved: left alone, no change and no record kept
- **Shape — resolved 2026-09-16:** the tags are drawn by the family in its active state, so each takes `ui.map.surface.active.alpha` (0.65), the shared corner, border and padding, and the alpha tier flattens by decision

- **Summary — closed 2026-09-16:** the ten points ran to the end — the stack is painted by the family in its active state, with the square side, the row's gutter, the overlay's gap and both insets read from shared keys, the glyph retune accepted and `alphaForCategory()` plus the two `status.gps.alpha.*` keys retired.
- Dropped: a separate border call, keeping the tags hand-painted, the neutral square-key rename, a named constant for the bottom clearance, the 4 dp stack-to-text gap, and any record of the settings preview icon. Nothing carried.

**Level 1 — Date:** 2026-09-16 · **Source:** the `#implement` Ask hop's fourteen findings on the zone-tag alignment · **Active:** 11 · **Closed:** 2026-09-16
- [x] 1 · The file's content alpha 0.75 against the code's 0.45 — resolved: the properties file is the source of truth, so AppConfig's fallback and every doc realign to 0.75
- [x] 2 · active.alpha 0.65 in the file against 0.75 elsewhere — resolved by the same rule: the docs and the default move to 0.65, which dims all five active squares and joins the device-judged list
- [x] 3 · A fifth home still calls the tag outside the family — resolved by deletion rather than a fifth rewrite: one home keeps the family's member list, the other copies go
- [x] 4 · ui.map.overlay.gap's reader list, three homes — resolved: the three enumerations deleted under D13
- [x] 5 · No retirement record for the two removed keys — resolved: both recorded, `ui.map.surface.active.alpha` named as successor
- [x] 6 · Three exception sentences close the list flatly — resolved: one home now, `MapSurface.kt`
- [x] 7 · Dead emojiForType and colorForType — resolved: both deleted, the provider keeping only what the tags read
- [x] 8 · The 44×44 KDoc literal in the tag's own file — resolved: key-based on `ui.map.toggle.square`
- [x] 9 · The clearance comment that says cb — resolved: it names `ui.map.toggle.gutter`
- [x] 10 · A comment claiming the content alpha dims the dot — resolved: the false clause deleted
- [x] 11 · An unarchived superseded recipe plan — resolved: archived to `xTrack/RegulatedZones/xxArchive/` with an index row, its Outcome written and its live strip rule promoted to the feature's `## Rules`
- [x] 12 · RegulatedZones front matter still reads June — resolved: `modified` set to 2026-09-16
- [x] 13 · Green tests carry no evidence — no action, the hop's own honesty note
- [x] 14 · D7–D10 matched exactly — no action, the hop's completeness note

- **Summary — closed 2026-09-16:** all fourteen findings resolved — the properties file became the source of truth for every value with the two `AppConfig` defaults and every doc moved onto it, the family's caller and exception lists collapsed to `MapSurface.kt` alone, three stale reader lists, a false claim and two loose literals were corrected, the dead `emojiForType()`/`colorForType()` pair was deleted, and the superseded icon-warnings plan was retired into `xTrack/RegulatedZones/xxArchive/` with an index row, its Outcome written and its live strip rule promoted to `## Rules`.
- Dropped: nothing. Nothing carried.

## Colour Modification Prompt

When asked to modify a colour in the Maro-II app, follow this checklist:

1. **Find the colour** in `app/src/main/assets/colors.properties` using its property key
2. **Update the value** (hex, or use `${alias}` for shared colours)
3. **Update the default** in `app/src/main/java/ykws/android/maro/config/AppConfig.kt` if the property is loaded into a field
4. **Update `docs/color-scheme.md`**: change the hex value, swatch, and alias chain
5. **Check aliases**: if the colour has `${...}` references, verify cascading properties still resolve correctly
6. **Build**: `gradlew assembleDebug`
7. **Deploy**: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell monkey -p ykws.android.maro 1`
