# Hydration: Ui_General

**Session:** List Count Display — filtered item counts surfaced in Track list title + menu drawer rows.

1. **Track list title** — `TrackHistoryOverlay.kt` title → `"Track History \u00B7 N"`, `N` = filtered count
   excluding the live/recording track (`trackSummaries.count { !it.isLive }`).
2. **Menu drawer counts** — `MenuDrawerOverlay.kt` new `trackCount`/`markerCount` params (default 0);
   "Tracks" + "Markers" rows render a muted 14.sp count left of the 40.dp chevron, wrapped in a
   `Row(verticalAlignment = CenterVertically)`; label stays left via outer `SpaceBetween`.
3. **Wiring** — `OverlayLayer.kt` MenuDrawerOverlay call site passes
   `trackCount = trackSummaries.count { !it.isLive }`, `markerCount = markers.size`.
4. **Build:** SUCCESS (`apk-build.bat`, assembleDebug). Ask review PASS — no deviations, no out-of-scope edits.
5. **Next:** manual on-device verification pending; then `#commit`.

**Target files:**
- `TrackHistoryOverlay.kt`, `MenuDrawerOverlay.kt`, `OverlayLayer.kt`

**Plan:** `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_list-count-display.md` (status: Implemented)

**Last Bake:** 2026-09-07 15:17 UTC
