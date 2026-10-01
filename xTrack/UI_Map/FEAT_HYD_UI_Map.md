# Context Hydration — UI_Map — 2026-09-30

**Last Bake:** 2026-09-30 19:49 UTC — written by `#bake`; absence means never baked

**Directive trace:** No covered action class stopped in this session — no dependency was added, no machine-shaped data file was opened, no code was touched and no device was involved, and every claim below came from files read this session.

## State

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is **4,527 reader lines** and [`fun MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:505) spans **505–3471**; steps 1–2 shrank it to ~2,587 and Inspect mode + Route destination mode grew it back. The remedy is now **implemented** — the plan under `## Docs` ran its four migration phases to completion, each behind a green `apk-build.bat` and a green scoped `ui.map` + `config` unit run: Phase 1 (Bodies) hoisted the two eligible `OverlayLayer` callback bodies, Phase 2 (Builders) moved five data constructions into [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:1), Phase 3 (Chrome) folded the eight written chrome values into [`MapScreenChrome.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt:1), and Phase 4 re-measured every anchor. Zero behavior change. The feature's existing open todos (marker-sizing device pass, dp second-density check, row-grid Medium) stay open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the orchestration shell (4,527 reader lines, `fun MapScreen` 505–3471)
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt` — the five `OverlayLayer` data-construction builders
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt` — the eight written chrome values in one `@Stable` holder
- `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md` — the plan of record
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — `## Docs` gained the plan pointer

## Next Step

On-device smoke via the LOGCAT workflow (user-driven): map renders, inspect arm/step/card, route arm/exit, drawers, filters/sorts/resets, snackbar undo, dialogs.
