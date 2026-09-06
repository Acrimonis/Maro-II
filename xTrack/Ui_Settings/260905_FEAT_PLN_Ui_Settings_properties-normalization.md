<!-- scope: feature -->

# Properties Normalization — colors / ui / maro

## Context

The app bundles three `.properties` files under `app/src/main/assets/`, all merged into one
`Properties` object by [`AppConfig.init()`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:528)
(load order: `maro.properties` → `colors.properties`; each overrides the previous). Today the
**concern separation is inconsistent**:

| File | Intended | Current reality |
|---|---|---|
| `colors.properties` | all colors | Mostly colors, but also holds non-color values: `ui.dashboard.dullAlpha` (alpha), `map.coastline.*.width` (stroke widths), `map.isobar.*.width` (width bonuses), `overlay.lowDepth.minOpacity` (opacity %). |
| `maro.properties` | spatial/other tunables | Spatial tunables + one UI value `ui.landscape.panel.widthScale`. |
| `ui-tokens.properties` | UI spacing/dimensions/fonts/dividers | **NOT loaded at runtime** (0 code refs) — documentation-only. Code hardcodes `.dp`/`.sp` values instead. |

## Target (user directive)

- **`colors.properties`** — all colors only.
- **`ui.properties`** — all UI-related values (spacing, padding, radius, fonts, dividers, nested-card tokens, alpha constants, UI width scales).
- **`maro.properties`** — all other (spatial/behavioural) tunables.

## Plan

### Step 1 — Rename `ui-tokens.properties` → `ui.properties`
`git mv app/src/main/assets/ui-tokens.properties app/src/main/assets/ui.properties`. Content unchanged for now.

### Step 2 — Wire `ui.properties` into runtime loading
In [`AppConfig.init()`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:528), load `ui.properties`
into the merged `props` (after `maro.properties`, before/after `colors.properties` — colors must still win for
color keys). Add a `UiTokens` accessor surface (or extend `AppConfig`) exposing the spacing/dimension/font
tokens as typed values (dp/sp/px), mirroring how colors are exposed.

### Step 3 — Re-home misplaced entries
- **`colors.properties` → `ui.properties`:** `ui.dashboard.dullAlpha` (alpha constant — UI state).
- **`colors.properties` → `maro.properties`:** `map.coastline.mainland.width`, `map.coastline.island.width`,
  `map.isobar.litto3d.width`, `map.isobar.emodnet.width` (map-render stroke widths — functional render tunables).
- **`colors.properties` → `maro.properties`:** `overlay.lowDepth.minOpacity` (functional threshold — user ruling).
- **`maro.properties` → `ui.properties`:** `ui.landscape.panel.widthScale` (UI layout value).
- Keep in `colors.properties`: `map.depth.ramp.*` RGB channels + alpha (colour data), all `*color*` keys, all
  `semantic.*`, `ui.settings.*` colours, `ui.card.background`, `ui.nested.card.bg`/`border` (colours).

### Step 4 — Replace hardcoded `.dp`/`.sp` in settings composables
In [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) `SettingsOverlay` + the 4 page
composables + shared settings widgets (`SettingsToggleRow`, `SettingsSliderGroup`, `SettingsExpander`,
`SectionHeader`, `SubSectionHeader`, `SectionDivider`), replace hardcoded spacing/padding/radius/font values
with the loaded `ui.properties` tokens. This is the largest step — many sites.

### Step 5 — Update docs
- `docs/ui-component-guidelines.md` §3 Spacing Quick Reference + §2.4b: point to `ui.properties` (was
  `ui-tokens.properties`).
- `docs/color-scheme.md`: ensure §7 lists only colour tokens; note non-colour values moved to `ui.properties`/`maro.properties`.
- `docs/maro-code.md` / `docs/SETUP.md` if they reference the files.

## Implementation Sequence (for Code mode)

Ordered so the build stays green at each checkpoint:

1. **Rename file** — `git mv ui-tokens.properties ui.properties`. No code change yet; build to confirm nothing breaks (file is currently unloaded).
2. **Add `ui.properties` to AppConfig loading** — load it into the merged `props` in `AppConfig.init()` (after `maro.properties`, before `colors.properties` so colors still win). Add typed accessors for the UI tokens actually consumed by settings (spacing/padding/radius/font/divider/nested-card). Build.
3. **Re-home entries** — move the 6 keys between files per Step 3; update the corresponding `AppConfig` reads to pull from the right file (they all read the merged `props`, so only the source-file placement changes; verify each key still resolves). Build.
4. **Replace hardcoded dp/sp in settings composables** — swap hardcoded `.dp`/`.sp` in `SettingsOverlay`, the 4 page composables, and the shared settings widgets for the new accessors. Do this in small batches (per widget/composable), building after each batch. This is the bulk of the work.
5. **Update docs** — point `ui-component-guidelines.md` §3/§2.4b and `color-scheme.md` §7 at `ui.properties`; note re-homed keys. No build needed.
6. **Final verification** — full `apk-build.bat`; grep for any remaining hardcoded `.dp`/`.sp` in the settings composables that now have a token; manual visual check that settings renders identically.

## Files Affected
- `app/src/main/assets/ui-tokens.properties` → renamed to `ui.properties`
- `app/src/main/assets/colors.properties` (remove non-colour entries)
- `app/src/main/assets/maro.properties` (remove `ui.landscape.panel.widthScale`)
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` (load `ui.properties`, add token accessors, re-home reads)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (use tokens instead of hardcoded dp/sp)
- `docs/ui-component-guidelines.md`, `docs/color-scheme.md`, possibly `docs/maro-code.md`, `docs/SETUP.md`

## Verification
- Build via `apk-build.bat`.
- Manual: settings UI renders identically (spacing/padding/fonts unchanged); colours unchanged.
- Grep: no remaining hardcoded `.dp`/`.sp` in the settings composables that have a token.

## Notes / Decisions
- **Resolved:** `overlay.lowDepth.minOpacity` → `maro.properties` (functional threshold).
- **Resolved:** map stroke widths (`map.coastline.*.width`, `map.isobar.*.width`) → `maro.properties`
  (functional render tunables).
- **Resolved:** `ui.dashboard.dullAlpha` → `ui.properties` (UI state alpha).
- **Resolved:** `ui.landscape.panel.widthScale` → `ui.properties` (UI layout value).
- Scope: this plan targets the settings UI first; other surfaces (drawers, lists, dashboard) can adopt
  `ui.properties` tokens in follow-up passes.
