# Context Hydration — UI_Map — 2026-09-30

**Last Bake:** 2026-09-30 19:49 UTC — written by `#bake`; absence means never baked

**Directive trace:** No covered action class stopped in this session — no dependency was added, no machine-shaped data file was opened, no code was touched and no device was involved, and every claim below came from files read this session.

## State

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is 4,421 lines and [`fun MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:492) ~3,240 of them; steps 1–2 shrank it to ~2,587 and Inspect mode + Route destination mode grew it back. The remedy is planned, not implemented: the new design plan under `## Docs` splits the remaining body into five clusters and three tiers — Tier 1 hoists the ~430-line `OverlayLayer` callback bodies into named functions and the pure derived values into pure functions; Tier 2 extracts `InspectSession`, `RouteSession` and `MapDashboardController`; Tier 3 (optional) moves the route-exit dialog. Zero behavior change, one seam per commit. The feature's existing open todos (marker-sizing device pass, dp second-density check, row-grid Medium) stay open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the monolith under remedy
- `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md` — the plan of record
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — `## Docs` gained the plan pointer

## Next Step

Implement Tier 1 first — hoist the OverlayLayer callback bodies into named functions — then Tier 2, one seam per commit with `apk-build.bat` green after each.
