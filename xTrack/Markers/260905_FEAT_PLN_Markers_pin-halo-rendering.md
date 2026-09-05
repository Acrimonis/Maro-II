# Markers — Pin Halo Rendering

> **Feature:** Markers | **Branch:** feature/markers-pin
> **Created:** 2026-09-05 | **Status:** Plan — Approved (awaiting implementation)

## Goal

Differentiate pinned from unpinned markers on the map via a **static halo ring** around each marker's center dot/icon, replacing the current "dim unpinned" mechanism. Settings-driven, mirroring the pinned-tracks color/transparency settings pattern.

## Locked Decisions

- **Halo = static ring** around the center dot (no icon) or around the emoji (has icon). No animation — battery-safe.
- **No colored dot under an emoji** — when a marker has an icon, the halo rings the icon directly (current `skipDots` behavior preserved).
- **Pin dimming removed entirely** — unpinned markers no longer fade. Halo strength is the only pinned-vs-unpinned differentiator.
- **Search dimming kept** — during a "where am I?" search, non-matching markers still fade (marker + halo together); the match stays full + thicker stroke.
- **Two halo colors** (pinned + unpinned) + **two RangeSlider transparency pairs** (pinned: fill+border; unpinned: fill+border). Pinned and unpinned differ by both color and strength.
- **Halo size is absolute** — radius maps 18px (1× dot) at size 0 → 60px (2× icon) at size 100, applied uniformly to dot and icon anchors (not proportional to the anchor).
- **No halo on unconfirmed** (in-progress) markers.
- **Auto (IDLE_AUTO) markers follow the same pinned-halo rule.**
- **No settings version bump** — additive keys only, safe defaults.
- Halo renders in **both** zone states (zones shown/hidden) since it anchors on the center dot/icon which is always present.
- **Selected marker gold** is driven by `selectedMarkerId` (the marker being viewed) — gold shows whenever a marker is viewed, unifying map-tap and list-open.
- **Corridor line halo = under-line** (thicker colored line beneath the main line), plus rings on the endpoint dots/icons.

## 1. Settings Model

Add to `AppSettings` (mirroring the `trackingColorPinned*` / `trackingTransparencyPinned*` pattern):

| Field | Type | Meaning |
|---|---|---|
| `markerHaloSize` | Int | Halo size (0–100). **Absolute radius**: 18px (1× dot) at 0 → 60px (2× icon) at 100, applied uniformly to dot and icon anchors. Default 50. |
| `markerHaloPinnedColor` | Int (ARGB) | Pinned halo color (default white). |
| `markerHaloUnpinnedColor` | Int (ARGB) | Unpinned halo color (default light blue). |
| `markerHaloPinnedFillOpacityPct` | Int (0–100) | Pinned halo inside-fill opacity (default 25). |
| `markerHaloPinnedBorderOpacityPct` | Int (0–100) | Pinned halo border/stroke opacity (default 80). |
| `markerHaloUnpinnedFillOpacityPct` | Int (0–100) | Unpinned halo inside-fill opacity (default 10). |
| `markerHaloUnpinnedBorderOpacityPct` | Int (0–100) | Unpinned halo border/stroke opacity (default 40). |

- Wire through `SettingsManager` load/save + SharedPreferences keys.
- **Defaults:** pinned = white, fill 25 / border 80 (strong); unpinned = light blue, fill 10 / border 40 (faint). Separation is obvious out of the box.
- **No prefs version bump** (additive keys, safe defaults).

## 2. Settings UI

Add a **separate collapsible "Marker rendering" expander** under the Markers section in `MapScreen` settings (distinct from the auto-markers expander, ~line 4026):
- Two `ColorSwatchRow`s: one for pinned halo color, one for unpinned halo color.
- Two `RangeSlider` pairs (mirroring the pinned-tracks transparency UI at ~line 3820): one labeled "Pinned halo", one "Unpinned halo". Each pair = fill + border.
- New string resources (EN + FR).

## 3. Rendering (MarkerOverlay)

- **Remove** the pin-dimming branch in `baseColor` ([`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:168)) — unpinned markers now render at full `markerColor` like pinned. **Keep** the `highlighted`/`unconfirmed`/match-result precedence and the search-dim branch.
- **Add halo drawing** around each confirmed marker's center anchor:
  - No icon: draw a halo ring around the colored center dot.
  - Has icon: draw a halo ring around the emoji icon.
  - Halo = outer ring (border) + inner fill disc, using the pinned/unpinned halo color + opacity pair selected by `marker.pinned`.
  - **Halo size is absolute** — radius from `markerHaloSize` (18px at 0 → 60px at 100), applied uniformly to dot and icon anchors.
  - **No halo on unconfirmed markers.**
  - Auto markers follow the same rule.
  - Corridor: halo around p1/p2 dots/icons **and** the connecting line (see §3a).
- Halo is drawn **behind** the dot/icon (added to overlay before the dot/icon marker) so layering is icon → halo → marker color.
- **Search interaction:** halo follows the marker's search state — fades with a non-match, full on the match.
- Pass the new settings into `MarkerOverlay` (add params) and include them in the `DisposableEffect` keys so a settings change rebuilds overlays.

## 3a. Corridor rendering — always-on colored connecting line

- **Drop** the current dark 50%-alpha centerline ([`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:523)).
- Draw an **always-present colored connecting line** (marker color, full opacity) between p1 and p2 — rendered in **both** zone states and **regardless of icon presence** (it is the one constant corridor element; drawn independently of dots/icons and the band).
- **Minimum render (both states):** 2 endpoint dots (or icons) + colored line.
- Zones shown: current full pill band (parallels + caps + fill) drawn as today, **plus** the always-on colored line.
- **Pinned corridor line halo = under-line:** a thicker colored line drawn beneath the main connecting line (like the track highlight under-stroke), so the whole line glows along its length.
- Pinned corridor: halo rings on the p1/p2 dots/icons **and** the under-line halo on the connecting line.
- Corridor icons (p1/mid/p2) unchanged.

## 3b. Selected marker — gold treatment on top + zones forced visible

- **`selectedMarkerId`** (the marker currently being viewed in the drawer/dashboard) is the single driver for the selected treatment — unifying map-tap and list-open.
- The selected marker renders the **gold dual-outline** (dark under-stroke + gold) like a selected track, **above** other rendering (top z-order).
- **`selectedMarkerId` also forces the selected marker's zones visible** (re-pointing the old `highlightedMarkerId` `drawZones` behavior, [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:191)) — uniform for map-tap and list-open.
- **Fold in `navigationZonesVisible`:** the separate list-open zones-forcing mechanism becomes redundant and is folded into the `selectedMarkerId`-driven logic.
- Distinct from the pin halo: gold outline = "selected", halo = "pinned".

## 3c. Focus — zoom-to-fit corridor & circle

- **Corridor focus:** center on the p1/p2 midpoint (already `centerPoint`) + `zoomToBoundingBox(marker.bbox)` to fit the **whole corridor zone** (including width).
- **Circle focus:** center + `zoomToBoundingBox(marker.bbox)` to fit the whole circle.
- **Pin focus:** single point — center only (no fit), unchanged.
- Reuse the existing track zoom-to-fit mechanism (`mv.zoomToBoundingBox(bbox, true, 64)`, [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2166)). Marker `bbox` already spans p1/p2 ± width/2 for corridors and the full circle for circles ([`UserMarker.kt`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:70)).

## 3d. Code-health structure & implementation order

- **Split rendering into small single-role files** (mirroring `MarkerColors.kt`):
  - `MarkerAppearance.kt` (new) — centralizes the visual-state decision (highlighted/selected/unconfirmed/match/pinned/unpinned → color + stroke + halo). Pure, testable; removes the growing inline `when` chain from the overlay loop.
  - `MarkerHalo.kt` (new) — owns halo visuals: computes color/opacity from settings + pinned state, builds halo bitmaps (fill disc + border ring) sized to the anchor (dot vs icon). Pure logic, no map coupling.
  - `MarkerOverlay.kt` — keeps only orchestration: decides *whether* a marker gets a halo and calls the above. Stays the conductor, not the doer.
- **Implementation order (abstract first, then implement, then tweak):**
  1. **Abstract first:** extract the appearance decision into `MarkerAppearance` so it reproduces current rendering exactly.
  2. **Validate/correct that step:** build + visually confirm markers render identically to before the extraction; fix any discrepancy before proceeding.
  3. **Implement the full halo + selected logic** in the clean `MarkerAppearance`/`MarkerHalo` classes.
  4. **Tweak freely afterward** in the isolated, testable classes.
- **No halo bitmap caching now** — create fresh per rebuild (marker counts are low). Note caching as a possible future evolution.
- **Selected overrides pinned:** when a marker is selected, drop the pinned halo rendering (gold replaces it while selected).
- **Single selected driver:** `selectedMarkerId` is the one source of both the gold outline AND the force-zones-visible behavior; remove the marker `highlightedMarkerId` logic (re-pointing both its uses) and fold in `navigationZonesVisible`.

## 4. Files to Change

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new fields + load/save + keys.
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings UI block + pass settings to `MarkerOverlay`; corridor/circle focus zoom-to-fit; remove marker `highlightedMarkerId` + fold `navigationZonesVisible` into `selectedMarkerId`-driven logic.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — remove pin dimming, add halo rendering (scales to anchor), always-on corridor line + under-line halo, selected gold + force-zones driven by `selectedMarkerId`.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt` (new) — visual-state decision (color + stroke + halo).
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt` (new) — halo visuals (color/opacity + bitmaps sized to anchor).
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — new labels/descriptions; **also fix the carried-over FR gap** (remove stale `cd_pin_marker`, add `cd_change_icon`).

## Build & Tests

- `gradlew assembleDebug`.
- Manual: verify pinned vs unpinned halo in both zone states; verify no pin dimming remains but search dimming works; verify corridor line in both zone states; verify corridor/circle focus zoom-fits the whole zone; verify selected marker gold on top (map-tap and list-open).
