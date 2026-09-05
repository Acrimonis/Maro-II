# Hydration — Markers

**Last Bake:** 2026-09-05 10:42
**State:** icon-pin-decoupling + pin-halo-rendering implemented on branch feature/markers-pin. Build green.

## Summary
- **icon-pin-decoupling (implemented)** — icon fully decoupled from pin (pure POI emoji, no pin semantics); pin re-implemented as real persisted `UserMarker.pinned` mirroring tracks (repo/VM/card/drawer/multi-select/filter/rendering); removed obsolete `migratePinnedToIcon`; settings v7 migration. Plan: `260904_FEAT_PLN_Markers_icon-pin-decoupling.md`.
- **pin-halo-rendering (implemented)** — static settings-driven halo ring differentiates pinned (white, strong) from unpinned (light-blue, faint); pin dimming removed (search dimming kept); corridor always-on colored line + pinned under-line halo; selected-marker gold driven by `selectedMarkerId` (forces zones, folds `navigationZonesVisible`, removes `highlightedMarkerId`); corridor/circle focus zoom-to-fit; icon centered on halo; code split into `MarkerAppearance`/`MarkerHalo`. Plan: `260905_FEAT_PLN_Markers_pin-halo-rendering.md`.
- **opacity-normalization (implemented, Ui_Settings)** — all opacity/transparency settings standardized on OPACITY; tracks converted transparency→opacity with v8 migration; marker halo + zone300 relabeled. Plan: `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md`.

## Modified Files
- `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md` — plan (new)
- `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md` — plan (implemented)
- `xTrack/Markers/FEAT_DSC_Markers.md` — front-matter, sections, Implemented, Docs
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md` — plan (new)
- `xTrack/GLOBAL_CONTEXT.md` — focus pointers + Markers summary row

## Pending
- `#todo markers: fix proximity of date points. Rays hit/test all of them.`
- `#todo gps: back to GPS point -> replace delay by swipe of card`
- `#todo: normalize localisation and fill holes`
