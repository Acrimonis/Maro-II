<!-- scope: feature -->
# Inspect Mode — live-acquire marker world, and recentre instrumentation

> Host feature: **UI_Map** (inspect mode). Branch: `feature/inspect-mode-fix`.
> Two device-pass issues picked 2026-10-23.

## Problem

1. If inspect acquires a marker it paints gold but the marker dashboard never opens.
2. After the pause recentres the map onto the selected item, the ladder's original anchoring appears lost — the walk should order objects by distance from the original anchor, closest out.

## Diagnosis

1. The sweep ranks from `mapMarkers` (map-filtered), but the live acquire resolves the marker through `openMarkerDetail`'s default — `mgmtMarkers` (`markersViewModel.markers`, list-filtered). When the two filters diverge, the gold paints (`inspectCandidate`) but `openMarkerDetail` finds no marker and abandons. The step path already resolves through the cursor's map-world resolver; the live acquire was the outlier.
2. Static reading shows the ladder is ranked once at the pause from `reference` — the point captured at arming and never re-established — and frozen; no re-rank path exists after the recentre. Instrumentation was added to observe the actual chain on-device rather than guess.

## Change surface

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `openInspectCard` MARKER branch resolves the live acquire in `markersViewModel.mapMarkers`; log points at the marker-not-found guard, each step and the recentre seat.
- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt` — `INSPECT_LOG_TAG`; anchor and pause-ladder logs.

## Owed

- Device test by the user, then logcat (`InspectFlow`) to validate: no `marker not found` line, `recentre idx` and `head[0]` agreeing, and step distances monotonically increasing from the anchor.
- Ask review findings carried forward (not folded): the map-world resolver is stated four times — a dedup against `inspectMarkerLookup`; no log of the camera landing after the settle loop.
