# BoatTrace — Hydration Snapshot

**Baked at:** 2026-07-17 16:15 UTC
**Active Subfeature:** tracks-paint-order (z-order flip + highlight-to-top)
**Branch:** feature/tracks-paint-order

## Session Summary

**Tracks Paint Order — IMPLEMENTED.** Three changes in MapScreen.kt, BUILD SUCCESSFUL.

### Change 1: History tracks z-order flip
Accumulated all history overlays (solid segments, gap lines, auto-marker 🕐 pins) into `historyOverlays: MutableList<Overlay>`, reversed, then `mv.overlays.addAll()`. Flips from oldest-on-top to newest-on-top. `computeTrackPolylineAppearance` index unchanged (0=newest).

### Change 2: Pinned tracks z-order flip
Same `pinnedOverlays` accumulation + reverse pattern.

### Change 3: Highlight-to-top
After active track move-to-end, if `highlightedTrackId != null`, filters overlays by `Polyline.title ==` (exact) for polylines and `Marker.title.startsWith` (with trailing `_` delimiter) for auto-marker pins. Remove + re-add at end.

Final z-order: `oldest history → newest history → oldest pinned → newest pinned → active → highlighted`

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — All three changes

## Plan

- `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md`
