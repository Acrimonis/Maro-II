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

## Outcome (2026-09-07)

Implemented on `feature/settings-misc`. Full `apk-build.bat` green. No commit/push (working-tree changes only).

- **Step 1** — `git mv app/src/main/assets/ui-tokens.properties → ui.properties`. Build green (file was unloaded).
- **Step 2** — [`AppConfig.init()`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:529) loads `ui.properties` after `maro.properties`, before `colors.properties` (colors still win). Added typed UI-token accessors (spacing/padding/radius/font/touch/divider/nested-card colours) parsed via local `dp()`/`sp()` helpers stripping the suffix.
- **Step 3** — Re-homed 6 keys: colors→ui `ui.dashboard.dullAlpha`; colors→maro `map.coastline.mainland.width`, `map.coastline.island.width`, `map.isobar.litto3d.width`, `map.isobar.emodnet.width`; maro→ui `ui.landscape.panel.widthScale`. **Deviation:** `overlay.lowDepth.minOpacity` does not exist anywhere (only `overlay.lowDepth.color`) — nothing re-homed for it.
- **Step 4** — Replaced hardcoded `.dp`/`.sp` in settings composables ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)): shared widgets `SectionHeader`/`SettingsToggleRow`/`SliderRowContent`/`SectionDivider`/`SubSectionHeader`/`Card`/`NestedCard`/`Expander` + page composables `SettingsOverlay`/`LayersSettings`/`NavigationSettings`/`PositionSettings`/`SystemSettings`. Remaining hardcoded values are only those with no corresponding token (overlay chrome, tab bar, segmented control, color-picker, bottom sheets). **Deviation:** `ui.font.toggle.size` aligned 14sp→16sp (code-wins) to match the actual shared-widget label size.
- **Step 5** — Docs: `ui-component-guidelines.md` §3/§2.5 → `ui.properties`; `color-scheme.md` §7/§9 re-homed keys + 16sp toggle label; `AppConfig.kt` doc comment. `maro-code.md`/`SETUP.md` don't reference the files.

**Ask review (2026-09-07):** load order, re-homing, accessor surface, docs correct; build green. Non-blocking follow-ups logged: (1) inline 14sp slider-row labels inside `NestedCard`s (MapScreen.kt:3469,3517,3565,3610,3807,3846,3986,4137) not migrated — inconsistent with 16sp shared-widget labels; (2) `BoatSizeSlider` ([`RegulatedZoneComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:383)) hardcodes tokenized nested-card colours + 14sp/16dp/12dp — outside plan's MapScreen.kt scope; (3) 8 dead tokens defined+loaded but never consumed (`uiSpacingGroupedBeforeExpander`, `uiPaddingGroupedToggleVertical`, `uiPaddingSliderVertical`, `uiPaddingContentCompact`, `uiPaddingDrawerVertical`, `uiFontGroupedToggleSize`, `uiFontDrawerSize`, `uiTouchDrawerRowMin`); (4) 16dp label/control spacer has no token.

## Cleanup follow-up (2026-09-07) — all review items addressed

Full leftover-extraction + cleanup audit (Ask) → all items resolved on `feature/settings-misc`. Build green throughout; no commit/push.

- **A1** — 7 inline control-group labels migrated `14.sp` → `AppConfig.uiFontToggleSize.sp` (MapScreen.kt:3469,3517,3565,3610,3807,3846,4137) per ui-component-guidelines §2.5 (control labels = 16sp Medium). The "Info text visible" label (:3986) is a toggle-row label (no Medium weight) — intentionally left at 14.sp. **A1-adjacent (final review):** the "🚤 Boat length" label in `BoatSizeSlider` ([`RegulatedZoneComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:398)) was also migrated `14.sp` → `AppConfig.uiFontToggleSize.sp` (it sits inside a NestedCard via MapScreen.kt:4002) — the value readout (`"${boatSizeM} m"`, Bold) stays at 14.sp.
- **A2/B4** — `BoatSizeSlider` dead non-nested branch deleted; `nested` param removed; caller simplified (RegulatedZoneComponents.kt + MapScreen.kt:4002). Removed the hardcoded nested-card colours.
- **A3** — added `ui.spacing.label.control=16dp` token + `uiSpacingLabelControl` accessor; wired `SettingsToggleRow` + `SliderRowContent` spacers; documented in ui-component-guidelines §3.
- **A4** — `uiFontToggleSize` fallback default fixed `14f` → `16f` (AppConfig.kt) to match ui.properties.
- **B1** — pruned 8 dead tokens from AppConfig.kt + ui.properties (never consumed).
- **B2** — fixed stale doc refs to pruned tokens in ui-component-guidelines §2.3 pseudo-code + §3 table.
- **B6** — corrected stale `zone.properties` doc comment in AppConfig.kt header + init() load-order list.

**Final verification:** apk-build green; 0 references to pruned tokens in app/src + docs; 0 orphaned `ui-tokens.properties` refs; ui.properties ↔ AppConfig.kt token consistency confirmed (no orphans either direction); no control-group label left at 14.sp in a NestedCard.
