<!-- scope: feature -->
# Home-port normalization and arrival prompt

**Date:** 2026-10-24
**Feature:** Tracks
**Status:** shipped 2026-10-24

## Context

The home port is defined in three scattered places today: the `tracking.originLat/Lon` / `tracking.geofenceRadiusM` properties (placeholder `43.55 / 7.00`, 500 m), a name fragment `salis` in [`TrackNaming`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt), and a hardcoded display name `la Salis` in [`TrackRecorder`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt). The name-based half is wrong — the device's home marker is `⭐📍 Home`, and the shipped comment line missed the port because it checks position only against a misconfigured geofence.

## Decision

The home port is defined and referenced **only** by `maro.properties`:

- position `43.5696, 7.1312`
- radius `50` m
- display name `La Salis`

Functional role:

1. Leaving the port without recording starts a new track (existing auto-start on geofence exit).
2. Entering/exiting gets the comment's `La Salis: departure → return` line, no time-spent duration (shipped).
3. Arriving while recording: wait for idle inside the zone (60 s, reusing `idleThresholdSec`), persist a checkpoint, then a three-door prompt — Save (finalize), Discard, Continue — armed once per entry (re-fire only after re-exit), the return time read from the Save press, and the silent 15 s auto-stop dropped.

## Implementation

- [x] `maro.properties`: `tracking.originLat=43.5696`, `tracking.originLon=7.1312`, `tracking.geofenceRadiusM=50`, and add `tracking.displayName=La Salis`.
- [x] Drop [`TrackNaming.HOME_PORT_LABEL`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt) and the label arm of `namesHomePort`.
- [x] `unlessHomePort`, `routeEndName` and `isHomePortPosition` become positional-only (geofence).
- [x] Drop the hardcoded `HOME_PORT_DISPLAY_NAME`; the comment line reads the display name property.
- [x] Gate the idle signal on "inside the home-port circle", reusing `idleThresholdSec` (60 s).
- [x] Persist a checkpoint, then show a three-door dialog reusing the `recording_exit_*` strings.
- [x] Arm once per entry (re-fire only after re-exit); return time = the Save press.
- [x] Remove the `STOP_DEBOUNCE_MS` 15 s auto-stop.
- [x] "We arrived to the home port" in `values/strings.xml` and `values-fr/strings.xml`.

## Tests

- `TrackNamingTest`: positional-only `namesHomePort` cases.
- Recorder/ViewModel test for the prompt gating (idle-in-zone + arm-once).
- Comment line reads the display name property.

## Open

- None.
