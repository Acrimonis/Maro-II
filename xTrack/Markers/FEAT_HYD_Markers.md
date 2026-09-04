# Hydration — Markers

**Last Bake:** 2026-09-04 21:41
**State:** icon-pin-decoupling: plan approved, pending implementation. Branch feature/markers-pins-vs-icons created, synced with develop (1 incoming commit merged), pushed.

## Summary
- **icon-pin-decoupling (in progress)** — split `icon` (pure POI emoji) from `pin` (real persisted flag mirroring tracks). Plan: `260904_FEAT_PLN_Markers_icon-pin-decoupling.md`.
  - Icon: wizard wording "Set icon"/"Change icon", filter axis `icon` (WITH_ICON/WITHOUT_ICON), consolidate `typeIcon` → `MarkerGeometry.iconFor`, TrackRecorder icon semantics.
  - Pin: `UserMarker.pinned` field (no backfill), repo/VM `setPinned`, PushPin card + drawer toggles, multi-select Pin spec, Pinned filter axis, pinned full / unpinned dimmed rendering (binary HIDDEN/SHOW_ALL layer).
- Post-merge note: MapScreen now renders from the filtered `markers` list, so pin/icon filters also gate map visibility.

## Modified Files
- `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md` — plan (new)
- `xTrack/Markers/FEAT_DSC_Markers.md` — front-matter, subfeature entry, Docs link
- `xTrack/GLOBAL_CONTEXT.md` — focus pointers + merge resolution + Markers summary row

## Pending
- Implement the 10 remaining todos in the plan (data model → repo → VM → UI → filter → rendering → cleanup → build).
- `#todo markers: fix proximity of date points. Rays hit/test all of them.`
- `#todo gps: back to GPS point -> replace delay by swipe of card`
- `#todo: normalize localisation and fill holes`
