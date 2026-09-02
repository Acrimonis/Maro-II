# BoatTrace — Track Direction Arrows

**Date:** 2026-09-01
**Feature:** BoatTrace
**Status:** plan finalised after review — awaiting implementation go-ahead.

## Goal

Draw small direction chevrons at regular intervals along persisted tracks (history + pinned), rotated to the recorded course, behind a Settings toggle "Show tracks direction on map".

## Why feasible (no data change)

[`TrackPoint.kt`](app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt:23) already carries `bearingDeg` (course over ground) and `speedMps` per point. No protobuf migration; existing and imported tracks work as-is.

## Decisions

| Aspect | Decision |
|---|---|
| Toggle | `tracksDirectionVisible` (default false) → "Show tracks direction on map" switch in the Tracks layer card |
| Rendering | One custom `TrackDirectionOverlay` per rendered track — vector chevrons in a single draw pass |
| Orientation | chevron rotated to `bearingDeg` |
| Colour | track's computed ARGB (inherits transparency gradient) |
| Size | ∝ stroke width: length 12–24 px, thickness ≥ 2 px |
| Z-order | chevrons above their own track's polyline, interleaved per track |
| Highlighted track | black halo chevron beneath gold chevron, above the gold core |
| Spacing | pixel-spaced — constant on-screen density (~72 px), re-sampled on integer zoom change |
| Scope | history + pinned (persisted) tracks only |
| Perf | viewport culling + per-track cap; overlay count O(tracks) |

## Review findings (must-fix)

1. Highlight re-add at [`MapScreen.kt:1399`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1399) filters `Polyline` titles — a custom overlay won't be caught. Extend it to move `track_arrow_<id>` to top; arrow cleanup must not rely on the `as? Polyline` cast.
2. Do not key the track `LaunchedEffect` on zoom — rebuilding all polylines every zoom step would regress performance. The overlay re-samples its own geo anchors on integer zoom change; `draw()` projects + culls each frame.
3. `bearingDeg` may be null (imported GPX / older points) — fall back to the segment vector; skip `GAP` points entirely.
4. Concrete pixel spacing ≈ 72 px.
5. Interleaving chevrons after each track's polylines requires restructuring the current flattened add loop (history added, then pinned).
6. `Overlay.draw` override signature must match the project's osmdroid version (`draw(Canvas, MapView, boolean)` vs `onDraw(Canvas, Projection)`).
7. Extract the sampler into a pure, unit-testable function.

## Implementation steps

1. Add `tracksDirectionVisible: Boolean = false` to [`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:183) + `KEY_TRACKS_DIRECTION_VISIBLE` persistence.
2. Add localized string resources for the toggle label + description (EN + FR).
3. Add "Show tracks direction on map" switch row in the Tracks layer card ([`MapScreen.kt:4566`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4566)).
4. Add `tracksDirectionVisible` to the track-overlay `LaunchedEffect` key set (do NOT key on zoom).
5. Implement `TrackDirectionOverlay` custom osmdroid Overlay — chevrons oriented by `bearingDeg`, colour = track's ARGB, size ∝ stroke width (length 12–24 px, thickness ≥ 2 px), one per rendered track, viewport culling + per-track cap; match the project's osmdroid draw override signature.
6. Bearing fallback: derive from segment vector when `bearingDeg` is null; skip `GAP` points.
7. Pixel-spaced sampling: overlay re-samples geo anchors on integer zoom change (~72 px spacing); `draw()` projects + culls; extract pure sampler + unit test.
8. Restructure the add loop to interleave `track_arrow_<id>` immediately after each track's polylines.
9. Highlighted track: black halo chevron beneath gold chevron; extend highlight re-add at [`MapScreen.kt:1399`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1399) to move `track_arrow_<id>` to top.
10. Cleanup: remove `track_arrow_<id>` in history/pinned removal passes and on toggle-off (not via `Polyline` cast).
11. Build via `apk-build.bat` + deploy + E2E verify arrows follow the recorded course at multiple zoom levels.
12. `#bake` snapshot to update BoatTrace hydration + feature summary.

## Phase 2 (planned) — speed-based density (screen-dp)

**Decision:** arrow density encodes speed and is zoom-adaptive. Spacing is expressed in screen dp and scales linearly with speed, clamped below `trackDirectionSpeedFloorKn` and above `trackDirectionSpeedCeilingKn`.

```
spacingDp(v) =
  v ≤ floorKn              → minSpacingDp
  floorKn < v < ceilingKn  → lerp(minSpacingDp, maxSpacingDp, (v−floorKn)/(ceilingKn−floorKn))
  v ≥ ceilingKn            → maxSpacingDp
```

- **Settings (maro.properties + Tracks settings):** `trackDirectionDensity` (UNIFORM | SPEED, default UNIFORM), `trackDirectionSpeedFloorKn` = 3.0, `trackDirectionSpeedCeilingKn` = 35.0, `trackDirectionMinSpacingDp` = 24, `trackDirectionMaxSpacingDp` = 120.
- **UI:** density selector `Uniform / Speed-based` under the master toggle, plus min/max spacing sliders and speed floor/ceiling fields.
- **Sampler:** convert dp → ground metres via the current zoom's metres-per-pixel, then re-sample on integer zoom change — reuses Phase 1's pixel-spaced re-sampling; only the spacing function changes (constant → speed-linear-clamped).
- **Edge cases:** skip `GAP`; speed below the floor is clamped to min spacing (no idle stacking); per-track cap applies.
- **Verification:** E2E on a track with distinct slow + fast sections — confirm arrow density differs and stays readable across zoom levels.
