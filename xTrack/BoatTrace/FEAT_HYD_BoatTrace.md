# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-16 18:42 UTC
**Active Subfeature:** gps-switch-confirm (confirm before switching position source while recording)
**Branch:** feature/fix-track-gps

## Session Summary

**Confirm before switching position source while recording.** Toggling GPS↔demo while a track is
recording used to swap the sample pipeline silently, mixing real and simulated points into one track.
Now [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) intercepts the toggle at the
single `onGpsModeChange` choke point: when `TrackRecorderState.ON`, it stashes a pending value and shows
[`ConfirmSheet`](app/src/main/java/ykws/android/maro/ui/components/ConfirmSheet.kt) (bottom sheet, dashboard
space). Confirm applies the permission-aware switch; cancel/dismiss reverts the switch. Covers all three
entry points (drawer, settings, top-left icon); settings sheet still closes immediately.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — guard + ConfirmSheet rendering
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `gps_switch_confirm_*` strings

## Next Step

- On-device E2E: record a track, toggle position source, verify the confirm sheet appears; confirm/cancel behave correctly.
