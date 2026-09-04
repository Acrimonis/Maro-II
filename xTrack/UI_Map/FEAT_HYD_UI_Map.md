# UI_Map — Hydration Snapshot

**Baked:** 2026-09-04 18:08 UTC

## Active State
- **Subfeature:** map z-order
- **Branch:** feature/new-tghter-ui

## What Changed This Session
1. **Deterministic overlay z-order** — new `OverlayZOrder.reorder(mv)` partitions `MapView.overlays` into tile → base data layers → tracks → markers (top), preserving within-band order.
2. **Root cause:** OSMdroid paints overlays in list order with no per-overlay z-index; every mutation appended to the end, so markers could render below tracks (last writer won).
3. **Fix:** `reorder()` called after every overlay mutation — 5 track sites in MapScreen, 1 in MarkerOverlay, 6 base-layer sites in CoastlineMapView.
4. **Build:** SUCCESS (`gradlew assembleDebug`).

## Design Decisions
- Classification by title prefix (`track_*`, `marker_*`) plus `MapEventsOverlay` type — consistent with the existing cleanup-by-title pattern.
- Tile overlay preserved at index 0; within-band relative order kept (under-strokes below gold, unconfirmed markers below confirmed).

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt` — new helper
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — 5 reorder call sites
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — 1 call site
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — 6 call sites

## Next Step
- On-device verify: toggle layers, create/edit markers, record a track — markers always above tracks, tracks always above data layers.
