# Settings — Opacity/Transparency Nomenclature Normalization

> **Feature:** Ui_Settings (cross-cutting: tracks, zone300, low-depth, markers) | **Branch:** feature/markers-pin
> **Created:** 2026-09-05 | **Status:** Plan — Approved (awaiting implementation)

## Goal

Normalize all opacity/transparency settings to a single, best-practice convention: **OPACITY** (0 = fully transparent/invisible, 100 = fully opaque/visible). Today the app mixes two opposite semantics and the marker-halo UI label is actively wrong.

## Current state (inventory)

| Group | Fields | UI label | Semantics | Status |
|---|---|---|---|---|
| Low-depth warning | `lowDepthWarningMinOpacityPct` (25) | "Warning opacity" | opacity | ✅ already correct |
| 300 m band | `zone300FillOpacityPct` (20), `zone300BoundaryOpacityPct` (80) | "Opacity" | opacity | ✅ already correct |
| Tracks (not-pinned) | `trackingTransparencyNewest/Oldest` | "Not-pinned transparency" | **transparency** (0=opaque,100=invisible) | ❌ inverted |
| Tracks (pinned) | `trackingTransparencyPinnedNewest/Oldest` | "Pinned transparency" | **transparency** | ❌ inverted |
| Marker halo | `markerHalo*OpacityPct` (fill/border) | **"Transparency"** | opacity | ❌ label wrong |

## Locked Decisions

- **Standardize on OPACITY** (higher = more visible/opaque) across all groups.
- **Marker halo:** relabel UI "Transparency" → "Opacity"; keep opacity semantics (already correct). Value format "Zone/Border" → "Fill/Border".
- **Tracks:** convert from transparency to opacity — invert stored values (`opacity = 100 - transparency`) and relabel "transparency" → "opacity". Requires a **prefs version bump** (values invert).
- **Zone300:** relabel its value format "Zone/Border" → "Fill/Border" to unify with marker halo (it is NOT confirm-only — it needs a string edit).
- **Low-depth:** already opacity; confirm only.
- **Unify value-format strings** across groups to "Fill X% · Border Y%".
- **BuildConfig:** rename `TRACKING_TRANSPARENCY_*` → `TRACKING_OPACITY_*` with inverted defaults (80/20/100/80) — single source of truth.
- **Migration v8 removes old keys** after writing new opacity keys.

## 1. Marker halo (relabel only)

- [`strings.xml`](app/src/main/res/values/strings.xml:331) `settings_marker_halo_transparency_label` "Transparency" → "Opacity".
- `settings_marker_halo_value_fmt` "Zone %1$d / Border %2$d" → "Fill %1$d% · Border %2$d%".
- Update the `SubSectionHeader` usage in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4103) (no code change beyond string).
- FR strings updated to match.

## 2. Tracks — convert transparency → opacity

- **Settings model** ([`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:218)): rename fields `trackingTransparency*` → `trackingOpacity*`; invert defaults (`opacity = 100 - transparency`).
- **Migration:** prefs version bump (7 → 8) rewriting persisted `tracking_transparency_*` → `tracking_opacity_*` with inverted values (`new = 100 - old`).
- **Rendering math** ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:343)): `computeTrackPolylineAppearance` currently does `alpha = (100 - transparency)/100`. With opacity stored, change to `alpha = opacity/100`.
- **Callers:** update all `trackingTransparency*` references in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:606), [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:161) to the new opacity fields.
- **UI strings:** `settings_transparency_label/desc/value_fmt` and `settings_pinned_transparency_*` → opacity wording ("0% = invisible, 100% = opaque").
- **BuildConfig:** `TRACKING_TRANSPARENCY_*` constants → opacity values (or keep and invert at read).

## 3. Zone300 + low-depth

- **Zone300:** already opacity semantics; **relabel** its value format `settings_zone300_opacity_value_fmt` "Zone %1$d%% · Border %2$d%%" → "Fill %1$d%% · Border %2$d%%" to unify with marker halo.
- **Low-depth:** already opacity; confirm only (no change).

## 4. Files to Change

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — rename track fields, invert defaults, migration v8 (write new keys + remove old).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — track rendering math (`computeTrackPolylineAppearance` alpha = opacity/100, rename params + KDoc) + settings UI + marker-halo label + stale inline comments.
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — track field references.
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — track field references (params + defaults + remember keys + calls).
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — relabel all (note: `settings_transparency_value_fmt` is shared by not-pinned AND pinned sections — one resource, both affected).
- `app/build.gradle.kts` / BuildConfig — rename `TRACKING_TRANSPARENCY_*` → `TRACKING_OPACITY_*` with inverted defaults (80/20/100/80).
- `docs/ui-component-guidelines.md` — add the opacity convention (higher = more visible; use "Opacity" + "Fill/Border" value format).

## Build & Tests

- `gradlew assembleDebug`.
- Manual: verify track opacity sliders behave (higher = more visible); verify marker halo + zone300 "Opacity"/"Fill/Border" labels; verify low-depth unchanged.
- Unit test for the v8 settings migration (transparency → opacity inversion).
