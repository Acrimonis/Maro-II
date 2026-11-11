<!-- scope: feature -->
# Home-circle auto-start — the forgotten-start rule (step 1)

**Status:** implemented 2026-11-10 (revision 2, one review folded) · **Created:** 2026-11-10 · **Owner:** Tracks
**Step 2 (parked):** [`261110_FEAT_PLN_Tracks_marker-auto-start.md`](261110_FEAT_PLN_Tracks_marker-auto-start.md) — flagged markers, the wizard checkbox, the exit and ring triggers.
**Retires:** the circle-exit trigger of [`261110_FEAT_PLN_Tracks_auto-start-exit-debounce.md`](xxArchive/261110_FEAT_PLN_Tracks_auto-start-exit-debounce.md).
**Supersedes:** `FEAT_DOC_Tracks_decisions.md` §2.2, §2.6 (demo "speed > 2.5 kn for 10 s") and §3.1 / §3.2 ("no speed-based auto-start … even when geofence is disabled").

## Request

Leaving La Salis (the home circle, `tracking.homePort.*`) must start a recording reliably. The circle's
**exit** trigger goes; one level rule replaces it: tracking has been off for a while, the boat has rested in
the circle, is on water and has moved more than the idle detection thresholds → start. Arriving never
starts a recording. Markers take no part in step 1.

## The rule — per accepted fix while OFF, all of

An *accepted* fix has a lock or is a demo sample; no-lock re-emits (the source repeats the last position) are
ignored everywhere.

1. **Inside:** within the home circle (`TrackGeofenceChecker`, the same test as `TrackRecorder.kt:664`) at
   the completing fix or at any fix of the movement window, so a boat that began moving near the edge still
   starts although its later fix is already outside.
2. **Rested there (the latch, below):** set.
3. **Off for a while:** at least `tracking.autoStart.offMin` (minutes, 5) since the last stop. No recorded stop
   → holds.
4. **Moved:** the Settings meaning "how far the boat can move within the adaptive time". Of the retained
   fixes (none older than the window, boundary inclusive, at least two), the largest distance from the
   newest to any of them is at least the **policy's effective threshold** (`stopDetectionDistanceM`, or the
   accuracy when it exceeds 20 m — one function shared with `AdaptiveGpsPolicy`). When speeds are present, the
   completing fix and the **previous retained fix** are both ≥ 0.5 m/s (`GpsLocationSource.MIN_SPEED_MPS`).
   The window is `max(stopDetectionTimeSec, 3 × tracking.standbyIntervalMs)`: a 10 s idle setting holds only
   two standby fixes about 10 m apart and would never let a harbour-speed boat start. The thresholds apply
   whether or not `stopDetectionEnabled` is on.
5. **On water:** `isWaterAt(completing position)` answers `true`; `false` or `null` → no start.
6. **No recovery pending:** no valid checkpoint awaits recovery.

Conditions 5 and 6 are evaluated last, only when 1–4 pass. Level, no fixed hold: a miss is retried on the
next fix.

### The latch — "rested inside" without fixes while still

The standby stream delivers a fix only after 10 m of movement, so a boat at rest sends nothing: rest is
judged as **no movement seen for `tracking.autoStart.restMin` (minutes, 5)**, not as still fixes seen.

```
state (lives across transitionTo; never reset by it): latch=false, seen=false, prevInside=false,
                                                      lastMoveMono = detector creation time
per accepted fix f, in ON (observe) and OFF (onSample), in order:
  inside = isInsideGeofence(f)
  farOut = dist(f, origin) − radius > (f.accuracyM ?: 0)          // hysteresis: one jitter fix near the edge keeps it
  if (farOut)                     latch = false
  else if (!seen)                 latch = inside && (f.speedMps == null || f.speedMps < MIN_SPEED_MPS)
  else if (inside && prevInside && f.monoMs − lastMoveMono ≥ restMs) latch = true
  window += f; moving = condition 4 over the window
  if (moving || (f.speedMps ?: 0) ≥ MIN_SPEED_MPS) lastMoveMono = f.monoMs
  seen = true; prevInside = inside
```

- **App opened at the berth:** the first fix is inside and still → latched.
- **Recording on, stopped at the berth, any stop path:** the ON fixes keep `lastMoveMono` current; after hours
  with no fix, the first departure fix sees a gap ≥ `restMin` → latched on that fix.
- **Tracking off on the return:** the same — the first departure fix after `restMin` of silence latches.
- **Motoring in without stopping:** every fix is movement, the gap never reaches `restMin` → never latched.
- **App opened while motoring in:** first fix moving → not latched. Cost: the app opened while already leaving
  starts nothing.
- **Left over:** a pause of `restMin` or more inside on arrival, then berthing, starts a recording — closed by
  the D5 safety net.

## Decisions

- **D1 — exit trigger removed.** The circle keeps every other role: arrival prompt, `endedAtHomePort`, the
  trip-naming exclusion, `startedAtHomePort`. `trackGeofenceEnabled = false` disables the rule.
- **D2 — water is strict and position-based**, wired to `NavigationViewModel.isWaterOrNull` (no 6 NM cut-off,
  `null` before load, safe from the recorder's `Dispatchers.Default` lane). Not `isOnWater` / `boatIsWater`.
  The La Salis berth reads water (user, 2026-11-10). A person walking a pontoon after a rest passes movement
  and water — accepted; the D5 safety net ends that recording.
- **D3 — one pure detector,** `AutoStartDetector`, with a single home-circle input (no zone list: step 2 will
  change the signature when it comes). No exit-rule code.
- **D4 — the recorder keeps** `lastInsideGeofence` / `wasInsideGeofence` (`:697`, `:702`, `:720`, `:1245`);
  `debounceStartTime` and `DEBOUNCE_MS` go.
- **D5 — the auto start and the arrival prompt.**
  - `beginRecording(isManual = false)` keeps `startedAtHomePort = true` and sets
    `arrivalPromptArmed = !lastInsideGeofence` (set for the completing fix at `:671`): a start already
    outside arms the prompt for this trip's return; a start inside leaves it to the exit re-arm at
    `:697-699`, so a fuel-dock stop does not ask Save / Discard seconds after leaving.
  - **The manual path is untouched** (`:720`, and `:760` stays `true`), so `TrackRecorderArrivalPromptTest`
    keeps passing.
  - **Safety net:** an auto-started trip that has never left the circle raises the prompt once after idling
    inside for `offMin`. It ends the false starts the latch leaves over (a long pause on arrival, a pontoon
    walk). Cost: a fuel-dock stop longer than `offMin` gets one prompt; Continue is one tap.
- **D6 — one clock.** Every interval uses the recorder's `monotonicNowMs` at processing time; a sample's wall
  timestamp is never read.
- **D7 — the last stop is persisted.** On the `Stopped` event (`discard` `:606`, finalize `:1376`) the service
  writes the wall time to its existing `maro_service_prefs` (`TrackRecordingService.kt:138`); `ensureRecorder`
  reads it synchronously and the recorder converts it once:
  `lastStopMono = monoNow − (wallNow − stopWallMs).coerceAtLeast(0)`. This covers a discard (which deletes the
  file, so `listTracks` could not see it), needs no async seed and no "seed unresolved" suppression. No value
  → "no recorded stop".
- **D8 — recovery pending.** `TrackRepository` gains the one checkpoint listing that the service's inline scan
  (`:143-144`) and the recorder's `orphansPending: () -> Boolean` both use, with the same validity filter as
  `recoverOrphanedCheckpoints` (stale checkpoints it would delete do not count), so a stale file cannot
  suppress the rule. The one-shot `hasOrphans` flag is not used.
- **D9 — values:** `tracking.autoStart.offMin=5` and `tracking.autoStart.restMin=5` (minutes) in
  `maro.properties` → `AppConfig` → `TrackRecorderConfig`, which also gains the two detection thresholds; all
  live through `applyConfig`. The movement window's floor reads the existing `tracking.standbyIntervalMs`.
  *Note:* `offMin` and `restMin` overlap — manoeuvring right after a stop is already blocked by the rest — and
  could merge later.

## Numbers (defaults)

- Start: about 30 m from the berth, about 60 s after moving off at 1 kn, 30 s at 2 kn, 10 s at 6 kn; inside the
  150 m circle. The Doppler check makes about 1 kn the floor.
- With the window floor at 15 s, a 30 m / 10 s setting needs about 4 kn.
- **Cost:** a re-departure within roughly `offMin` of a stop and before leaving the circle is not recorded; a
  later one starts at the 5-minute mark if still inside, with a late first point.
- **Limit:** with the UI process gone nothing starts; today's circle exit did not need the UI.

## Against today's home-port behaviour

```
Started before, not now: a re-departure within ~offMin of a stop; the UI process gone or the index not loaded;
  a slow exit (< ~1 kn Doppler, or under the idle distance, while inside); land read at every fix inside (quay,
  slipway, trailer launch); a recovery pending; the app opened while already leaving; a car leaving the port.
Starts now, did not before: leaving the berth and staying inside (fuel dock, berth change; the prompt stays
  disarmed, the safety net covers it); a pause ≥ restMin on arrival (safety net); a pontoon walk after a rest
  (safety net); the first point lands about 30 m out instead of 10 s past the edge.
```

## Wiring

- `AutoStartDetector.kt` (new, pure Kotlin): `onSample(fix, params, home, lastStopMono, orphansPending, waterAt)`
  in OFF, `observe(fix, params, home)` in ON beside `addPoint` (`:693-694`); `Decision(fromHome)`. `Fix`:
  position, monotonic time, accuracy, speed, lock, demo.
- `TrackRecorder` hooks, defaulted so existing tests auto-start nothing: `waterAt = { null }`,
  `orphansPending = { false }`, `lastStopWallMs: Long? = null`.
- `WhereAmIProvider.isWaterAt: ((LatLng) -> Boolean?)?` (`@Volatile`), registered and cleared in
  `MapMarkerEffects.kt:92-105` beside `whereAmI` and `idleCapture` (same lifecycle; it has the
  `NavigationViewModel`).
- Demo: no special case — demo samples (map centre, `hasLock = false`, `isDemo = true`) count.

## Tests

`AutoStartDetectorTest` (pure, hand-driven monotonic clock, fixed 5 s / 10 m inputs, exact expectations):
1. All conditions → starts on that fix.
2. Each one missing → nothing: outside; latch clear; stopped 4 min ago (6 min → starts); moved too little; water
   `false`; water `null`; one fix; recovery pending.
3. Latch flows: app opened at the berth; recording stopped at the berth, hours of silence, depart; tracking off on
   the return, silence, depart — each starts. Motoring in and berthing without a pause → nothing; a pause shorter
   than `restMin` → nothing; a pause of `restMin` → latched (closed by the safety net, recorder test). First fix
   inside while moving → not latched. Latch cleared on leaving, re-entry while moving → not latched. One jitter
   fix just past the edge within accuracy keeps the latch. A no-lock fix neither sets nor clears it.
4. Edge: moving off 35 m inside the edge at 2, 4, 6 kn → starts.
5. Movement: 90° turn passes; demo counts; Doppler < 0.5 m/s on the completing or the previous retained fix →
   no; accuracy > 20 m raises the threshold; old fixes drop out; the window floor at 15 s lets 30 m / 10 s start
   at 4 kn and not at 3 kn.
6. A car leaving the port (water `false`) → nothing. Params changed between fixes apply on the next.

`TrackRecorderAutoStartTest` (rewritten): the circle-exit cases removed; geofence off → nothing (kept);
`lastStopWallMs` written on discard and on a finalized save, not on ON, and read back by a re-created
recorder; a sample with a stale `timestampEpochMs` changes nothing; suppression while a valid checkpoint
exists, not for a stale one; an auto start sets `startedAtHomePort = true`; a start inside: a stop inside raises
no prompt until the boat has left and returned; a start whose completing fix is outside: the return prompts; the
safety net prompts once after `offMin` idle inside a trip that never left; defaults start nothing.
`TrackRecorderArrivalPromptTest` unchanged and green. `AdaptiveGpsPolicy` tests green with the extracted threshold.

## Files

- New: `data/track/AutoStartDetector.kt`; the tests above.
- `data/track/TrackRecorder.kt` — the detector calls, `beginRecording` (auto branch only), the safety-net prompt,
  the hooks, `TrackRecorderConfig` (+4 fields, `from`); KDoc `:142`, `:184`; comments `:675-677`, `:1194`.
- `data/track/TrackRecordingService.kt` — the stop write and read, water wiring, the shared checkpoint listing;
  comment `:339-341`.
- `data/track/WhereAmIProvider.kt`; `ui/map/MapMarkerEffects.kt`.
- `data/track/TrackRepository.kt` — the shared checkpoint listing.
- `data/location/AdaptiveGpsPolicy.kt` — the shared threshold function.
- `data/track/Track.kt:89` — `startedAtHomePort` KDoc.
- `config/AppConfig.kt` (`:1369-1370`, two keys), `assets/maro.properties` (two keys; comments `:623-624`,
  `:655-656`).
- Docs: `FEAT_DSC_Tracks.md` (Summary, the "OFF→ON via geofence exit" rule), `FEAT_DOC_Tracks_decisions.md`
  §2.2, §2.6, §3.1, §3.2, `docs/maro-code.md:18` (the new file), the retired plan's Outcome.

## Out of scope

- Everything in step 2 (flagged markers, wizard, exit and ring triggers).
- A service-owned coastline index; the stale "Recovery available" label.

## Review

- **Revision 1 (Ask, 2026-11-10), folded into revision 2:**
  - *Blockers:* the latch could never be set from standby fixes (a still boat sends none) → rest judged as no
    movement for `restMin`, state across transitions; a pause on arrival latched and the disarmed prompt made the
    false start endless → rest of `restMin`, prompt armed when the start is outside, the safety net.
  - *Fixes:* manual path untouched; latch-clear hysteresis restored; window floor for the idle extremes; the last
    stop persisted in prefs (covers discards); "first seen" requires still; "previous retained fix"; the shared
    checkpoint listing with the validity filter; docs and decisions §2.2 / §2.6 listed; the tests list completed.
  - *Notes adopted:* numbers corrected; a single home input instead of a zone list; `isWaterAt` registered in
    `MapMarkerEffects`; the behaviour-change list.
- **Revision 2:** not re-reviewed.

## Outcome

Implemented 2026-11-10. `AutoStartDetectorTest` (28) and the rewritten `TrackRecorderAutoStartTest` (18) added, `TrackRecorderArrivalPromptTest` and the `AdaptiveGpsPolicy` suite unchanged and green; `apk-build.bat` and 1 764 tests green; the device pass is owed.

Deviations from the plan as written:

- `orphansPending` is `suspend () -> Boolean` — the validity filter decodes checkpoints on IO — and the detector's `onSample` is `suspend` with it.
- The repository's two entries are `hasCheckpointFiles()` (the name scan, used by the service's "Recovery available" label, which stays stale) and `hasRecoverableCheckpoint()` (the validity filter without the delete); both share `checkpointFiles()`.
- `beginRecording` takes `startedAtHomePort` explicitly: manual passes `lastInsideGeofence`, an auto start `Decision.fromHome`.
- The persisted stop is written by the service through a recorder hook `onStopped(wallMs)` called inside `transitionTo(OFF)`, so an exit waiting for OFF cannot cancel it; the recorder tests cover the hook and the `lastStopWallMs` read-back, the pref write itself being Android-bound.

**Ask review of the implementation (2026-11-10), one pass.** The rule matches the plan on every point. Fixed: the stop write racing `onDestroy` (moved into the hook above); the vacuous "outside" test; stale comments (`SettingsManager`, the clock KDoc); the detector's own inside test (now `TrackGeofenceChecker`); the `hasRecoverableCheckpoint` KDoc (it still quarantines and decodes each call); boundary tests for exactly `offMs` and exactly `restMs`. Open, unfixed:

- the recorder's ON-branch `observe` call and the off clock after a `discard()` have no recorder-level test;
- window-age boundary, `farOut` with a null accuracy, and `forget()` on a returning home are untested;
- `AppConfig.trackingStandbyIntervalMs` is read directly in the recorder, so it is not live through `applyConfig`;
- `Decision.fromHome` is always `true` (the step 2 seam);
- a null Doppler speed counts as still, so a first fix without speed near the berth can latch a moving boat (rare);
- the safety-net timer clears its flag, then suspends, then emits (the idle timer has the same pattern);
- the recorder tests use real-time sleeps and the real wall clock for `lastStopWallMs`.
