# Tracks — Hydration Snapshot

**Baked at:** 2026-09-14 15:52 UTC
**Active Subfeature:** selected-track-speed-heatmap
**Branch:** feature/track-speed (created from origin/develop 2026-09-14)

## Session Summary

This session did three things on one branch:

1. **Feature rename** — `BoatTrace` became `Tracks` everywhere (feature directory, `FEAT_DSC`/`FEAT_HYD`, routing map, summary row, cross-feature pointers, code comments): 57 paths moved, 142 token replacements, zero `BoatTrace` left outside the rename record and `xxArchive`. The confusion it was meant to end is real, since `TracksImport` still exists as its own feature.
2. **Selected-track speed heatmap** — the selected track can now render as a banded speed ramp instead of the gold highlight. Ramp families carry their own colour grid and draw step with no count key; the scale is a declared tick table (`positionKn:textKn`) whose printed label may deliberately differ from its position; nothing is drawn inside the colour bar; the bar's foot is `track.heatmap.scaleMinKn` (2 kn); the drawer header's eye toggle stores its choice, so `track.heatmap.mode` is only a first-run default. Two `#implement` runs plus a fix hop for a cold-start defect where the settings flow was seeded empty.
3. **Rulebook** — push, commit and deploy are now jointly user-owned in `AGENTS.md`; none is ever proposed as a next step.

## Next Step

Device E2E of the heatmap on a real track, at the user's call: the legend's legibility with the 2–35 kn window, the tick labels at their cheated positions, and the eye toggle surviving a relaunch.

## Key Files

- `app/src/main/java/ykws/android/maro/config/HeatmapRamp.kt` (ramp, families without a count key, the tick table, the scale minimum)
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt` (`resolveSpeeds`, `colorAt`, `bandedAppearances`, `bandTable`)
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt` (the bar, the printed rows, `tickOffsetDp`)
- `app/src/main/java/ykws/android/maro/data/track/TrackSpeed.kt` (the shared nullable derive primitive)
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` (the selected-track rendering branch)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (legend host, mode state, eye toggle)
- `app/src/main/assets/maro.properties` (the heatmap block: mode, ticks, minimum, families, alpha, tint, carry)
- `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` (the contract, sections 1–19)
