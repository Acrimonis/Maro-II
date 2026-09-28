# Context Hydration — Ui_Settings — 2026-09-28

**Last Bake:** 2026-09-28 20:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** No covered action stopped — no dependency was added, no machine-shaped data file was opened, no device was touched and no code was written, and every claim here came from a file read. This bake was ordered by the user as the cross-feature bookkeeping the Markers walk's item 11 asks for; only this feature's records moved.

## State

This feature's last own session was 2026-09-19 on branch `feature/setting-tabs`: the Settings overlay's fourth tab took no tap, and the pager-to-tab write-back, its `pagerSyncSettled` guard and the bidirectional comment are deleted, so `LaunchedEffect(selectedTab) { pagerState.animateScrollToPage(selectedTab) }` is the only effect, `selectedTab` the single thing that moves the pager, and the page count derives from `settingsTabLabels` in place of the literal 4. The fault's mechanism stays **reasoned, never proven** — Compose Foundation 1.11.1 carries no sources artifact in the local Gradle cache — and the fix was chosen so its outcome does not rest on those semantics.

- **This bake's own write.** The 2026-09-18 session retired the `marker.debug.rays.enabled` hook, and its toggle half landed in `MapScreenSettingsOverlay.kt` — this feature's file: the toggle's write of the hook and the empty sync effect that carried it are gone, leaving `AppSettings.markerDebugRays` as the single carrier. That pointer now sits in `## Implemented`, with the Cross-feature plan attached in `## Docs`.
- Open in this feature's record, untouched by this bake: the device pass over the four tabs and over the merged extra-settings changes, the second-density emulator check, the coastline row grid's Medium finding, and the heading line's colour row reading **Default colour** with nothing to default from.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the derived page count and the one surviving effect; and the debug-rays toggle whose retired hook write this bake records
- `docs/ui-component-guidelines.md` — §2.11's single-source-of-truth row
- `xTrack/Ui_Settings/260919_FEAT_PLN_Ui_Settings_settings-tab-fourth-tap.md` — the plan, its out-of-scope list and its Outcome
- `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md` — the cross-feature plan whose toggle half is this feature's

## Next Step

No code has moved in this feature since 2026-09-19, so the next session opens on the same two checks its record already carries — the four tabs landing together with the pager still holding four pages, and the merged extra-settings changes — beside the Default-colour row and the row-grid Medium that stay open.
