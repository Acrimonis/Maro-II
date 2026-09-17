<!-- scope: feature -->
# Hide the map's Land/Water Icon — a Layers-tab setting

**Status:** in design, 2026-09-17 — nothing written, nothing built.
**Feature:** Ui_Settings. The map row this setting controls is Ui_General's `top-left-icons` surface, so §4 touches one of its files.
**Origin:** user request — a new section in the Layers tab carrying a "Show Land/Water Icon" toggle that hides the map row's 🌊/🏔️ square and handles the layout that follows.

## 1. What the request turns out to mean

- The square is `EarthWaterIcon` in `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — 44 dp, painted by `MapToggleSquare` on the shared `MapSurface`, one resolved face from `status.earthWater.water` / `status.earthWater.land`, 🌊 when the boat is on water and 🏔️ on land.
- It is **not** a toggle. Its own KDoc records no tap and no semantics, so the setting shows or hides a status square: there is no disabled state, nothing to gate on tap, and no accessibility label to retire.
- The state it reports survives its removal twice over — `CenterMarkerOverlay` takes `isWater` in `MapScreen.kt`, and the dashboard's shore pipeline is mode-aware (`dashboardPositionFor`). Hiding the square removes one of three views of the state, never the state itself, which is the answer to "the setting is redundant".

## 2. The setting

- `showLandWaterIcon: Boolean` on `AppSettings` in `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`, with `KEY_SHOW_LAND_WATER_ICON = "show_land_water_icon"`, read in the loader beside `KEY_COASTLINE_VISIBLE` and written in the saver beside it.
- **Default: plain `true`** in the data class, so an untouched install shows the square exactly as today and no install has anything to migrate.
- **Why not a `layer.*.default` key.** The four `LAYER_*` fields are layer-visibility seeds surfaced as `BuildConfig` by `propBool` in `app/build.gradle.kts`; this is a chrome-visibility preference, whose precedent is a plain Kotlin default — `regulationInfoVisible` (false), `trackLegendExpanded` (true). `maro.properties` is read at runtime by `AppConfig.load()` as well as by Gradle, so the reason is precedent, not the absence of a reader.
- **Recorded objection.** The two readings differ by one build-configurable key; a user who wants this default baked per-build would need the field pattern instead. Taken as precedent, not as a limit.

## 3. Settings UI

- New section in `LayersSettings` in `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, placed immediately after the Coastline section and before Danger Zones: `SectionHeader` + `Spacer(uiSpacingHeaderBottom)` + `CardArea { ToggleRow }` + `Spacer(uiSpacingSectionGap)`.
- No new component and no new token: `SectionHeader`, `CardArea` and `ToggleRow` are the tab's own family, and the new section inherits the 14 dp section rhythm and the container-owned inset (R1–R8).
- The Layers tab is index 0 of `settingsTabLabels` in `MapScreen.kt`; the section needs no tab-plumbing change.
- Strings, EN and FR, following the tab's `settings_*_label` / `_desc` convention: `settings_land_water_icon_label` and `settings_land_water_icon_desc` in `app/src/main/res/values/strings.xml` and `app/src/main/res/values-fr/strings.xml`.

## 4. Map wiring, and the only layout work that is real

- The row in `MapScreen.kt` lays out with `Arrangement.spacedBy(TOP_TOGGLE_GUTTER)`, so wrapping `EarthWaterIcon` in `if (appSettings.showLandWaterIcon)` makes the remaining squares close the gap by themselves — the same shape `RecenterButton` already has one line below.
- **The lock mirror is the one site that needs rework.** The locked-screen duplicate lock square is placed at `TOP_TOGGLE_GUTTER + (TOP_TOGGLE_SQUARE + TOP_TOGGLE_GUTTER) * 3` in `MapScreen.kt` — three squares (GPS, Track, land/water) precede the lock button, and hiding the third makes that count wrong and the duplicate land a square east of the original.
- Fix: one named helper keyed on the same flag, whose KDoc names the row's order as the value's dependency — the count is the row's declaration read from elsewhere, and the precedent for stating that bond is `TOP_TOGGLE_SQUARE`'s "the single home for it" KDoc in `MapControls.kt`.
- The `* 3` at that site is the **only** hard-coded square count in `ui/map` — verified by searching the arithmetic rather than the constants. Chrome keyed on the row's height and gutters (`legendTopOffset`, the landscape dashboard padding, the fan stack) reads no count and needs no change.
- `appSettings` is collected in the same composable as the lock mirror and is already a `MapContent` parameter, so both sites read the flag directly.

## 5. Verification

- Build gate: `apk-build.bat`. Read the result against the five pre-existing `maro.properties`-versus-`AppConfig` reds, which sit in this area and are red at HEAD.
- No test is planned: the change is a flag read, a Compose `if` and a one-expression offset. The repo does test `SettingsManager` through a fake `Context` (`MarkerFilterMigrationTest`), so a one-case default test is available if the floor is wanted.
- The device pass is the only judge of the mirror, and it stays with the user: unlock, lock, and check the duplicate lock square lands on the original in both orientations.

## 6. Open, and the user's to settle

- The default when nothing has been chosen — proposed: shown.
- The wording split — proposed: one shared `settings_land_water_icon_label` for header and row label, as the Coastline section does in the same tab; the alternative is a header of its own plus a "Show …" row label.

## 7. Target files

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the field, its key, the loader and the saver
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the new section in `LayersSettings`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the two keys
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the row's `if` and the lock-mirror offset helper
- `docs/ui-component-guidelines.md` section 5.5, `docs/ui-drawer-guidelines.md` layout tree — the two live docs that enumerate the row's squares

## 8. Consequences recorded, not patched

- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md` reads "Top-Start … GPS status icon + EarthWater icon | always". This change makes that row stale; a plan is a record, so the sweep covers the two live docs only and this staleness is written down here rather than edited there.
- `MapSurface.kt`'s reader census ("read seven times — the five row squares") stays true: the call site survives behind the flag even though the row's visible width changes.
- `side_water` / `side_land` stay unreferenced, and no colour token changes — both are other features' open decisions and out of scope here.

## Outcome

Shipped 2026-09-17 on `feature/hide-law` through the `#implement` pipeline. Nothing was deployed; the device pass is owed.

- `showLandWaterIcon` (`show_land_water_icon`, plain `true`, absent key means shown) on `AppSettings`, read and written beside `KEY_COASTLINE_VISIBLE`. The section sits after Coastline in the Layers tab, built from the tab's own `SectionHeader` / `CardArea` / `ToggleRow` — no new component, no new token.
- The map row's `EarthWaterIcon` is behind the flag with no spacer or width arithmetic added, so `Arrangement.spacedBy` reflows the row itself. The locked-screen mirror's count left the literal for `lockMirrorStartOffset()`: 156 dp with the square, 106 dp without, one square plus one gutter per button before the lock.
- Strings: `settings_land_water_icon_label` serves both the header and the row label, as the Coastline section does with its own, plus the one-line `settings_land_water_icon_desc`, in EN and FR.
- Docs: `docs/ui-component-guidelines.md` section 5.5 and the `docs/ui-drawer-guidelines.md` layout tree state the conditional square, and that doc's `Updated:` line was bumped per its own convention. The `xTrack/UI_Map/` inventory row was left stale on purpose.
- The Ask hop returned **revise** with no behavioural finding: two medium — a sentence claiming the row's one conditional slot against the gated recenter square, and the row's source comment naming the wrong order and omitting the lock — and three low, the FR pronoun, the field documented twice in `SettingsManager.kt`, and the doc header. All five were closed in one further Code hop, `apk-build.bat` BUILD SUCCESSFUL both times.
- Deviation from the plan's shape: none. The only additions were the `AppSettings` `@property` line and the corrected row comment.
- Still open: whether the duplicate lock square lands on the original on device once the square is hidden. The arithmetic is exact and integer-valued at the default tokens, so nothing but a locked run can settle it, and that pass is the user's.
