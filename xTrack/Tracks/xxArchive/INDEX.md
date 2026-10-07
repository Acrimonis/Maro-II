<!-- scope: archive-index -->
# Tracks — Archived Plans Index

Retired Tracks feature plans. Bodies are **not** read by default — no summarising command enters this folder, and a body needs an explicit per-file request.

| File | Created | Archived | Status | Summary | Tags | Superseded-by |
|---|---|---|---|---|---|---|
| `260618_FEAT_PLN_Tracks_adaptive-isstill.md` | 2026-06-18 | 2026-10-07 | shipped | Adaptive `isStill()` — pure position-only anchor + time-window policy; the old adaptive settings removed with no migration | gps, settings | — |
| `260620_FEAT_PLN_Tracks_gps-line-acquisition.md` | 2026-06-20 | 2026-10-07 | shipped | Removed the PASSIVE_PROVIDER listener (strict FIFO) and added the implied-speed spike gate | gps | `260622_FEAT_PLN_Tracks_spike-rejection-v2.md` |
| `260620_FEAT_PLN_Tracks_gps-background.md` | 2026-06-20 | 2026-10-07 | shipped | `TrackRecordingService` became the always-on foreground service with a persistent notification | service, notification | — |
| `260622_FEAT_PLN_Tracks_spike-rejection-v2.md` | 2026-06-22 | 2026-10-07 | shipped | Four-gate spike rejection — GPS recovery, context cap, sea-only direction, acceleration | gps | — |
| `260622_FEAT_PLN_Tracks_pinned-tracks.md` | 2026-06-22 | 2026-10-07 | shipped | Pin icon replaces the eye; `pinned` proto field; separate pinned transparency pair | data, ui | — |
| `260717_FEAT_PLN_Tracks_tracks-paint-order.md` | 2026-07-17 | 2026-10-07 | shipped | Z-order flipped so newest paints on top, plus highlight-to-top | render | — |
| `260911_FEAT_PLN_Tracks_live-track-paint-regression.md` | 2026-09-11 | 2026-10-07 | shipped | Live polyline never created after the C3/C4 extraction — creation re-keyed on recorder state | render, debug | — |
| `260911_FEAT_PLN_Tracks_resume-confirm-backup.md` | 2026-09-11 | 2026-10-07 | shipped | Resume confirmation sheet with a default-checked backup copy | ui, data | — |
| `260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` | 2026-09-14 | 2026-10-07 | shipped | Selected-track speed heatmap — banded ramp, declared tick scale, persisted eye toggle | render | `260914_FEAT_PLN_Tracks_render-modes.md` |
| `260914_FEAT_PLN_Tracks_render-modes.md` | 2026-09-14 | 2026-10-07 | shipped | Heatmap generalized to every stored track under one three-way switch | render | `260917_FEAT_PLN_Tracks_render-axes-split.md` |
| `260914_FEAT_PLN_Tracks_pinned-cue-casing.md` | 2026-09-14 | 2026-10-07 | shipped | Track outlines — per-type widths, opaque cased selection, chevron rim, legend gate | render | — |
| `260915_FEAT_PLN_Tracks_ramp-alpha-ceiling-removal.md` | 2026-09-15 | 2026-10-07 | shipped | Removed the `coreAlpha` ceiling — a band's alpha is the track's own fade alone | render | — |
| `260915_FEAT_PLN_Tracks_carry-window-removal.md` | 2026-09-15 | 2026-10-07 | shipped | Removed `carryMaxSec` — stored-then-derived speed, else null | render | — |
| `260916_FEAT_PLN_Tracks_legend-collapse-toggle.md` | 2026-09-16 | 2026-10-07 | shipped | Speed-scale collapse toggle — two faces on one anchor, persisted `trackLegendExpanded` | ui, render | — |
| `260917_FEAT_PLN_Tracks_render-axes-split.md` | 2026-09-17 | 2026-10-07 | shipped | Menu's tri-state became the twin box — Arrows and Colours as two independent chips | ui, render | `261007_FEAT_PLN_Tracks_path-render-engine.md` |
| `260919_FEAT_PLN_Tracks_ramp-step-recut.md` | 2026-09-19 | 2026-10-07 | shipped | Ramp step grid re-cut; `HEATMAP_MAX_FAMILIES` 8 → 9 so family 9 parses | render, data | — |
| `260919_FEAT_PLN_Tracks_position-filter.md` | 2026-09-19 | 2026-10-07 | shipped | Track position filter — All / On water / On land, sampled counts on the summary | data, ui | — |
| `260922_FEAT_PLN_Tracks_trace-flag-and-display.md` | 2026-09-22 | 2026-10-07 | shipped | The saved-route flag and its display — lists, keys, colour pair, card and refusals | data, render, ui | `260923_FEAT_PLN_Tracks_trace-word-to-route.md` |
