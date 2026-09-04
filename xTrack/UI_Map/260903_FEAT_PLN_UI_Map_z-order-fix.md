# UI_Map — Map overlay z-order fix

**Branch:** feature/map-z-order
**Feature:** UI_Map
**Status:** planning → implement

## Problem

Markers and tracks share the single flat `MapView.overlays` list. OSMdroid paints it in index order, and every mutation appends to the end, so "last writer wins." The track effects re-append tracks after the markers, so markers can render below tracks; toggling a layer "fixes" it until the next track refresh re-appends tracks on top.

## Goal

Deterministic overlay order: base data layers → tracks → markers (top). The boat marker and heading line are Compose overlays above the whole map and are unaffected.

## Solution

One canonical `OverlayZOrder.reorder(mv)` that partitions `mv.overlays` into bands — base (non-track, non-marker), tracks, markers — preserving the tile overlay at index 0 and preserving within-band relative order. Call it after every overlay mutation.

## Steps

1. Add `OverlayZOrder.kt` with `reorder(mv)` plus `isTrackOverlay` / `isMarkerOverlay` classification.
2. Call `reorder(mv)` after:
   - track history effect (`MapScreen.kt` ~1338)
   - pinned-track effect (`MapScreen.kt` ~1438)
   - live recording restore (`MapScreen.kt` ~1031)
   - trailing-line effect (`MapScreen.kt` ~1570)
   - highlight/active "bring to front" sites (`MapScreen.kt` ~1444, ~1457)
   - `MarkerOverlay` rebuild (`MarkerOverlay.kt` ~387)
3. Build (`gradlew assembleDebug`) and verify.

## Key files

- `app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt` (new)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (6 call sites)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` (1 call site)

## Classification rules

- tile: `mv.overlays[0]` (basemap) — always preserved first.
- track: `Polyline`/`TrackDirectionOverlay` whose `title` starts with `track_hist_`, `track_arrow_`, `track_recording`, or `track_trailing`.
- marker: `Polyline`/`Polygon`/`Marker` whose `title` starts with `marker_`, or a `MapEventsOverlay` (marker tap catcher).
- base: everything else, preserving current relative order.
