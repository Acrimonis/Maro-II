<!-- scope: feature -->
<!-- status: implemented (branch feature/ui-tweaks-ym, build SUCCESS 2026-09-08) -->

# Ui Tweaks — ym (Regulated zones card-in-card, 300m naming, marker point/icon zoom)

Branch: `feature/ui-tweaks-ym` (from `origin/develop`). Three independent UI tweaks under Settings → Layers.

## 1 — Regulated zones / Zone categories: card-within-a-card (guideline §2.4)

**Root cause:** [`RegulatedZoneCategoryToggles()`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:310) drew its own
12dp rounded container (`0x0DFFFFFF` bg + `0x40FFFFFF` border) while [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3982)
already wraps it in a `NestedCard` → a third card level inside the NestedCard, violating the §2.4 inception rule
(Card → Expander → NestedCard → controls; a card inside a NestedCard is forbidden) in
[`ui-component-guidelines.md`](docs/ui-component-guidelines.md:114).

**Fix (implemented):** the composable is now a plain `Column(fillMaxWidth)` whose toggle rows sit directly in the
surrounding `NestedCard`; the self-drawn box and its literal colours were removed and the rows' horizontal padding
zeroed (vertical 2dp kept) so alignment matches its siblings (reg-info content, `BoatSizeSlider`). Single call site
confirmed — no other screen affected.

## 2 — 300 m band naming

- [`settings_zone300_label`](app/src/main/res/values/strings.xml:75): `300 m band` → `300m Zone` (FR `Bande des 300 m` → `Zone des 300 m`).
- [`settings_zone300_appearance_label`](app/src/main/res/values/strings.xml:82): `Zone appearance` → `300m Zone Appearance`
  (FR `Apparence de la zone` → `Apparence de la zone des 300 m`).

Scope: only these two display labels (EN + FR). Description/alert strings referencing "band" are unchanged.

## 3 — Marker point/icon rendering zoom (50–150 %)

**Design (user-confirmed, rule of three):** zoom `z = value/100` (50–150, default 100 = current). The dot radius,
the icon glyph/bitmap and the halo ring all scale by `z`; at zoom 100 the rendering is identical to today. The halo
stays independent (its own slider/colors unchanged); its ring radius follows the marker via the same factor.

**Implemented:**
1. [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt): new `AppSettings.markerPointIconZoom: Int = 100`,
   `KEY_MARKER_POINT_ICON_ZOOM`, load + persist (mirrors `markerHaloSize`).
2. [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) Markers → Marker rendering `NestedCard`: new `SliderRowContent`
   `Point/icon rendering zoom` 50–150 (5% steps), `%d%%` value; passes `appSettings.markerPointIconZoom` into `MarkerOverlay`.
3. [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt): `markerPointIconZoom` param (default 100) added to the
   `DisposableEffect` keys; dot radius scaled via `createDotBitmap(..., radiusMultiplier = zoom)` (incl. the 1.5× selected under-dot);
   icon bitmap/textSize scaled by `zoom`; zoom threaded through `addPinOverlay` / `addCircleOverlay` / `addHaloOverlay`.
4. [`MarkerHalo.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt): `radiusPxFor(sizePct, zoomPct = 100)` = existing R(h) × zoom/100;
   `createBitmap` passes zoom through. Halo at zoom 100 = today.
5. Strings EN + FR: `settings_marker_zoom_label` / `settings_marker_zoom_desc`.

## Verification
- `apk-build.bat` → **BUILD SUCCESSFUL** (only pre-existing warnings; none from this change).
- Visual sanity recommended on device: zoom 100 must equal previous rendering; 50/150 scale dot/icon/halo uniformly.

## Notes
- Not-pinned/halo slider behaviour untouched; track/boat markers untouched; no desc-string rewrites (scope lock).
