<!-- scope: feature -->

# Live track no longer paints in real time — diagnosis & fix plan

**Date:** 2026-09-11
**Branch:** `feature/tracks-recording`
**Symptom (user):** while recording, the currently recorded track no longer grows on the map in
real time — at least in demo mode, possibly broader.

## Summary

Two independent defects stack up on the live-recording overlay path:

1. **Primary (fatal):** the live polyline *creation* effect no longer observes recorder-state
   transitions, because the C3/C4 extraction turned a `by collectAsState()` delegate read into a
   frozen plain-parameter read inside `snapshotFlow`. The `track_recording` polyline is therefore
   never created when recording starts, and both the append and trailing paths silently bail out.
2. **Secondary (latent consistency):** the map resolve path never excludes `isLive`, unlike the counters,
   list title and list filter. Reachable only when a recording is resumed onto a stored track. It
   explains why the breakage *correlates* with the filter work — it is not why the live line is missing.

## Primary defect — creation effect lost its snapshot-read

Recorder state is a `by` delegate in the shell:

- [`MapScreen.kt:419`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:419) — `val trackRecorderState by trackViewModel.uiState.collectAsState()`

It is forwarded to the live seam as a **plain value**:

- [`MapScreen.kt:884`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:884) → [`MapTrackOverlayEffects.kt:316`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:316)

The creation effect is keyed on map/colour only and reads the captured value:

- [`MapTrackOverlayEffects.kt:321`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:321) — `LaunchedEffect(mapView, appSettings.trackingColorActive)`
- [`MapTrackOverlayEffects.kt:323`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:323) — `snapshotFlow { trackRecorderState.state }`

While this code sat inside `fun MapScreen`, that lambda read *through* the `State` delegate, so
`snapshotFlow` was snapshot-tracked and emitted on every `OFF ⇄ ON` transition. As a parameter it is a
frozen `TrackRecorderUiState` instance: the block reads a plain field, registers no snapshot read, and
the flow emits the launch-time value (`OFF`) exactly once. Consequence chain:

| Step | Location | Effect |
|---|---|---|
| `track_recording` never created on `ON` | [`MapTrackOverlayEffects.kt:323`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:323) | no live polyline exists |
| Point append drops every point | [`MapTrackOverlayEffects.kt:388`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:388) — `?: return@collect` | silent loss, no log |
| Trailing dead-reckon segment bails | [`MapTrackOverlayEffects.kt:416`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:416) | no trailing segment |

Mode-agnostic: demo and GPS both feed `newPoint` through the same service-owned recorder
([`TrackRecordingService.kt:255`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:255)), so the failure is
identical in both — matching "at least in demo mode, maybe broader".

Why it can still paint *sometimes*: the checkpoint-recovery **Continue** path builds `track_recording`
polylines directly from the restored points ([`MapScreen.kt:785`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:785)). Once such a line exists the append
effect can extend it, so a **resumed** session keeps painting while a **fresh** recording — the usual
demo-mode case — never paints at all. That asymmetry matches the report and is a useful confirmation
signal if you want one on-device.

Why the extraction is the suspect: the refactor plan mandated byte-for-byte key preservation only for
the *history* diff and explicitly warned that key-tuple drift stops the overlay refreshing —
[`:168`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:168) and
[`:212`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:212) — while the C4
live trio carried no such constraint.

## Secondary defect — map resolve path lacks the live exclusion every other surface has

Unlike the counters ([`MapScreen.kt:1494`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1494)), the list title
([`TrackHistoryOverlay.kt:364`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:364)) and the list filter predicate
([`ListFilter.kt:67`](app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:67)), the map resolve step never excludes the live track:

- [`MapTrackOverlayEffects.kt:60`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:60) — `mapFiltered = allTrackSummaries.filter { it.matchesFilter(appSettings.trackMapFilter, …) }`
- [`MapTrackOverlayEffects.kt:71`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:71) — history set = `filteredSummaries.filter { (it.visibleOnMap || …) && !it.pinned }`
- [`TrackViewModel.kt:213`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:213) — the active summary is marked `isLive = true` and published in `allSummaries`

That contradicts the locked design that the live line sits *outside* the resolve function
([decoupling plan `:53-61`](xTrack/Ui_General/260909_FEAT_PLN_Ui_General_filters-link-decoupling.md:53)). Scope, per Evidence status below: a fresh
recording never exposes an `isLive` summary to this path, so it shows only when a stored track is
resumed — the map then draws a stale `track_hist_<liveId>` copy of the same track alongside the live
line.

Why it *looks* like the filter work caused the regression: the diff effect is keyed on the Map-filter
state ([`MapTrackOverlayEffects.kt:34`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:34)), so editing filters re-runs a full overlay rebuild. The
regression landed in the same window, but the fatal key loss is the C4 extraction, not filter logic.

## Fix design

**Recommended — Option A (minimal, idiomatic) + Option C (self-healing hardening):**

- **A. Key the creation effect on recorder state instead of a frozen read.** Key
  `LaunchedEffect(mapView, trackRecorderState.state, appSettings.trackingColorActive)` and replace the
  `snapshotFlow {}.collect {}` wrapper with a plain `if (state == ON) create-if-absent else remove`.
  Same behaviour, no reliance on snapshot reads of a parameter.
- **C. Make append/trailing self-healing.** If no solid `track_recording` polyline exists, create it
  and then apply the point/segment instead of `return@collect`. This deletes the silent-drop failure
  class so a future key/ordering slip degrades gracefully rather than blanking the line.

**Fix 2 (secondary, latent consistency):** exclude `isLive` from the map-resolve path — add
`!it.isLive` to `mapFiltered` and to the highlighted pull-back at
[`MapTrackOverlayEffects.kt:61-62`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:61), matching the rule that the live line is never part
of the resolve function. Today it is reachable only on resume (see Evidence status); it does not affect
the reported symptom — it keeps the four surfaces consistent instead of papering over the cause.

**Considered and rejected:**

- **Option B — pass `State<TrackRecorderUiState>` (or a getter lambda) so `snapshotFlow` stays live.**
  Keeps the current design shape but widens a pure-composable signature to carry a Compose `State` and
  leaves the silent-drop trap in place. Not recommended.
- Keying only on `trackRecorderState` (whole object): re-creates/removes the polyline on every stats
  tick — churn and flicker.
- Re-reading the ViewModel inside the seam: couples the overlay file to a state source it does not own.

**Open design question for review:** today an already-created line keeps its original stroke colour if
`trackingColorActive` changes mid-recording. Option A re-runs on colour change; we can either repaint
the existing line's `outlinePaint.color` (small win) or leave the guard as-is.

## Verification

1. `apk-build.bat` → SUCCESS (no new warnings).
2. On-device, demo mode (user deploys; logcat pulled only on request): start recording, pan the map —
   the active line must grow point by point in `trackingColorActive` at 10f stroke.
3. On-device, GPS mode: same live growth; stop → live line disappears; checkpoint **Continue**
   (`TrackEvent.Resumed`) still rebuilds the line
   ([`MapScreen.kt:785`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:785)).
4. GAP handling: pause/resume within a recording still inserts a dashed seam instead of a straight line.
5. Filter regressions: set a Map filter that excludes the recording's date range — the live line still
   draws (never filterable) and no `track_hist_<liveId>` duplicate appears; history/pinned tracks,
   highlighted-from-list reveal, and menu/list counters behave as before.

## Files affected

- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — both fixes.
- No expected changes to `MapScreen.kt` (Option A keeps the signature), settings, or strings.

## Evidence status

- **In-progress summary presence — CLOSED (2026-09-11).** While recording, the recorder writes only
  `{id}_checkpoint.bin` ([`TrackRecorder.kt:273`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:273), [`:996`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:996)) and
  [`rebuildIndex()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:274) filters checkpoint files out of the index — so a **fresh**
  recording has no `isLive` summary in `allTrackSummaries` at all, and `refreshSummaries()` is not
  called periodically during recording (only on init/stop/discard/resume/metadata changes). The
  duplicate-draw is therefore reachable only when a recording is **resumed** onto a stored track: that
  path re-saves the main `.bin` ([`TrackRecordingService.kt:335`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:335)) with `endTimeMs = null` →
  `isLive = true`. `visibleOnMap` defaults to `true` ([`Track.kt:38`](app/src/main/java/ykws/android/maro/data/track/Track.kt:38), [`Track.kt:68`](app/src/main/java/ykws/android/maro/data/track/Track.kt:68)) and the
  recorder writes it as `true` ([`TrackRecorder.kt:1082`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1082)), so the resolve path admits it. Fix 2 stands
  as a latent-consistency correction, **not** the cause of the reported symptom.
- **Pre-extraction source of the live trio — CONFIRMED (2026-09-11), regression is extraction-induced.**
  `git log` shows [`MapTrackOverlayEffects.kt`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) has exactly one commit — `9d95a16`
  ("Feature/mapscreen refactor (#225)"). In its first parent (the monolith `MapScreen.kt`), a
  `findstr` for `snapshotFlow` returns **no match anywhere in the file**, and the polyline lifecycle
  reads the state directly (`trackRecorderState.state == …ON` at line 701, `!= …ON` at 917). The
  `snapshotFlow { trackRecorderState.state }` wrapper — the statement that cannot observe a transition —
  was therefore introduced by the extraction itself, not inherited.

## Adjacent observation (not in scope)

The same frozen-parameter pattern exists at [`MapServiceEffects.kt:51`](app/src/main/java/ykws/android/maro/ui/map/MapServiceEffects.kt:51)
(`snapshotFlow { appSettings.gpsMode }` with `appSettings` forwarded as a plain value). It is the same
bug class and a candidate for a separate audit — flagged, not fixed here.

## Implemented

- **✓ Fix 1a — creation effect re-keyed on recorder state.** `LaunchedEffect(mapView,
  trackRecorderState.state, appSettings.trackingColorActive)` with the frozen-parameter
  `snapshotFlow` wrapper removed ([`MapTrackOverlayEffects.kt:327`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:327)).
- **✓ Fix 1b — self-healing append and trailing.** Both create the solid `track_recording` line on
  demand instead of silently dropping the point/segment ([`:394`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:394), [`:432`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:432)).
- **✓ Fix 2 — `isLive` excluded from the map-resolve path** ([`:62-66`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:62)).
- **✓ Reliability:** reproduction is now structural, not statistical — the failing statement no longer
  exists. Build `gradlew.bat assembleDebug` → SUCCESSFUL (2026-09-11, `feature/tracks-recording`), no
  warnings attributable to the edited file. Ask review: SOUND, no blockers.
- **Pending — on-device confirmation (not deployed):** fresh demo recording paints live; GPS mode;
  resume still paints; GAP seam still dashed; filter cases (live line survives a Map filter that
  excludes its date range; no duplicate history copy on resume); counters unchanged.

## Follow-ups (flagged, not done)

- Factor the repeated "new live polyline" literal (four sites plus the dashed variant) into a
  file-local builder so stroke/colour/title cannot drift.
- [`MapScreen.kt:785-841`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:785) holds a fifth copy of the same live-polyline rebuild — fold it into the same builder.
- Audit the frozen-parameter `snapshotFlow` shape at [`MapServiceEffects.kt:51`](app/src/main/java/ykws/android/maro/ui/map/MapServiceEffects.kt:51) (same bug class as this regression).
