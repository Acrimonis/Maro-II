# Context Hydration — Ui_Settings — 2026-09-17

**Last Bake:** 2026-09-17 20:44 UTC

**Directive trace:** three of the five covered action classes were met and none stopped — no dependency added, no machine-shaped data file opened, no work started without an order, since the branch and the `#focus` write were both instructed. Nothing touched the device, and the one unsourced claim, a sentence in the plan explaining why the default stays out of `maro.properties`, was caught in review and corrected.

## State

The Layers tab's "Show Land/Water Icon" section shipped on `feature/hide-law`, uncommitted at bake time. One persisted `showLandWaterIcon` (`show_land_water_icon`, plain `true`, an absent key meaning shown) hides the map row's 🌊/🏔️ square; the row reflows through its own `Arrangement.spacedBy`, and the locked-screen mirror's hard-coded three-square offset became `lockMirrorStartOffset()` — 156 dp with the square, 106 dp without — the count's single home, its KDoc naming the row's order as the value's dependency. The strings are one `settings_land_water_icon_label` serving both the section header and the row label, plus a one-line description, in EN and FR. `apk-build.bat` was SUCCESSFUL on both hops of the `#implement` pipeline, and the Ask hop's five prose findings were closed in a second Code hop.

Carried forward, unverified since the 2026-09-11 bake: `SlideDirection.FADE_ONLY` in `DrawerSlot.kt` was recorded as unreachable dead code and remains a cleanup candidate.

## Target Files

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the setting, its key, and the loader and saver pair
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the row's guard and `lockMirrorStartOffset()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the new section in `LayersSettings`

## Next Step

Whether the locked-screen duplicate lock square lands on the original once the square is hidden — decidable only on device, the arithmetic being exact and integer-valued at the default tokens.
