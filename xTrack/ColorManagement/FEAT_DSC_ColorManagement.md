---
name: ColorManagement
status: active
created: 2026-06-16 14:05
modified: 2026-09-16 06:31
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

- **Alias Interpolation** — `${key}` resolver in `AppConfig.init()`; green→`status.success`, low-depth→`status.error`
- **ui-token-de-settings** — 14 shared UI tokens de-`settings`-ified (property keys + `AppConfig` accessors + all call sites + doc resync; `ui.settings.divider` → `ui.divider.color`), values and strings unchanged → `xTrack/ColorManagement/260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md`
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

## Colour Modification Prompt

When asked to modify a colour in the Maro-II app, follow this checklist:

1. **Find the colour** in `app/src/main/assets/colors.properties` using its property key
2. **Update the value** (hex, or use `${alias}` for shared colours)
3. **Update the default** in `app/src/main/java/ykws/android/maro/config/AppConfig.kt` if the property is loaded into a field
4. **Update `docs/color-scheme.md`**: change the hex value, swatch, and alias chain
5. **Check aliases**: if the colour has `${...}` references, verify cascading properties still resolve correctly
6. **Build**: `gradlew assembleDebug`
7. **Deploy**: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell monkey -p ykws.android.maro 1`
