# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-18 20:20 UTC

## Session Summary

Planning session for the `render-tracks` subfeature:

1. **Subfeature created:** `render-tracks` added to FEAT_DSC_BoatTrace.md with Todos, Rules, Key Files sections.
2. **Requirements gathered:** Configurable track rendering on map — `tracking.render.nb` (0-20, default 5), `tracking.color.active`, `tracking.color.history`, `tracking.color.pinned`.
3. **Plan written:** `xTrack/BoatTrace/FEAT_PLN_BoatTrace_render-tracks.md` — 10 implementation steps covering maro.properties, BuildConfig, AppSettings, LRU cache, FanLayout, incremental overlay diff, color pickers, unified Tracking settings section.
4. **Review completed:** Ask agent flagged perf concerns (incremental diff, LRU cache), recommended Canvas-based color picker, `maxCount` bump, and settings tab unification.

## Key Decisions
- Canvas-based color pickers (no new deps)
- Unify recording + render settings under "Tracking" collapsible section in Navigation tab
- `tracksVisible` default: true
- Pinned color infra only, behavior reserved

## Next Steps
- [ ] Implement all 10 steps in Code mode
- [ ] Build + verify assembleDebug passes
- [ ] Run Ask review on implementation
