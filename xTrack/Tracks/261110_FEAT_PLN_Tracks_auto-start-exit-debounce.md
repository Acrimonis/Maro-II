<!-- scope: feature -->
# Auto-start on leaving the home port — the exit debounce

**Status:** in design · **Created:** 2026-11-10 · **Owner:** Tracks

## Problem

On a real trip (GPS mode, app started inside La Salis and left running) the recording did not start
when the boat left the port. The auto-start has existed since the June recorder work, but the
verification todo "leave Port Salis → verify auto-start" in `FEAT_DSC_Tracks.md` was never ticked,
and no unit test covers it.

## Root cause

The OFF branch of `TrackRecorder.processSampleLocked` (`TrackRecorder.kt:674-688`) only keeps the
10 s debounce alive while the current sample is itself an inside→outside crossing:

```kotlin
if (geofenceEnabled && wasInsideGeofence && !insideGeofence) { /* arm, or start after 10 s */ }
else { debounceStartTime = null }
...
wasInsideGeofence = insideGeofence   // line 701
```

- The first fix outside arms the debounce, then line 701 stores "outside" as the previous state.
- The second fix outside is no longer a crossing, so it takes the `else` and clears the arm.
- The 10 s is never reached on a steady exit. It can only fire when GPS jitter takes the boat back
  inside the 150 m circle and out again, which is why it looked "implemented".

## Fix

Arm on the crossing, keep the arm while outside, clear it only on re-entry; start on the first sample
that lands 10 s or more after the crossing.

```kotlin
TrackRecorderState.OFF -> {
    val now = monotonicNowMs()
    when {
        !geofenceEnabled || insideGeofence -> debounceStartTime = null   // inside, or no geofence
        wasInsideGeofence -> debounceStartTime = now                     // the crossing arms
    }
    val armedAt = debounceStartTime
    if (armedAt != null && now - armedAt >= DEBOUNCE_MS) {
        debounceStartTime = null
        beginRecording(isManual = false, startSample = sample)
    }
}
```

- Same field, same 10 s constant, same start sample (the one at the 10 s mark), same
  `startedAtHomePort = true`.
- `transitionTo` (`TrackRecorder.kt:1192`) clears `debounceStartTime` on every state change. It is the
  one choke point for `beginRecording`, `resume`, `stop` and finalize, so an arm can never outlive the
  OFF stretch it was set in (review R1).
- The KDoc at `TrackRecorder.kt:184` says "10s movement debounce"; the code has never tested movement.
  The wording becomes "10 s outside the circle" — see D2.

## Demo mode

Demo mode is already wired for this; it suffers from the same bug and is fixed by the same change.

- `MapServiceEffects.kt:52-99` builds a `TrackSample` from the map centre (the demo boat) and pushes it
  into the service's recorder once a second on a ticker, even while the map is still.
- `NavigationViewModel.kt:1194` clears the GPS position when GPS mode turns off, so the sample's
  position is the map centre, never a stale fix.
- The recorder runs the same geofence test on those samples, so after the fix: pan the map into
  La Salis, pan out past 150 m, leave it there 10 s, and the recording starts, flagged demo
  (`Track.markedFor`).
- No new demo code is needed. The user's "fake GPS at the marker position" is exactly what the demo
  boat already is: the map centre.

## Cleanup — the dead `tracking.homePort.enabled` flag

`tracking.homePort.enabled` → `BuildConfig.TRACK_ENABLED_DEFAULT` → `AppSettings.trackEnabled`, read
from and written back to the `track_enabled` pref, and consumed by nothing (the June recorder plan's
"observes the trackEnabled toggle" never landed). Removed end to end:

- `maro.properties:630` — the key goes; the block header (lines 623-624, "and whether it starts at
  all … and the enabled flag") is reworded to the origin, radius and display name it now holds.
- `app/build.gradle.kts:97` — the `TRACK_ENABLED_DEFAULT` field goes.
- `SettingsManager.kt:273` — the `trackEnabled` field goes; `:780` — its read goes.
- `SettingsManager.kt:1043` — the `putBoolean` becomes `.remove(KEY_TRACK_ENABLED)`, joining the
  three home-port removals under the comment at `:1044`, so the value an older build stored is
  dropped on the next save. `KEY_TRACK_ENABLED` stays for that removal alone.
- No test, UI or string references it (grep of the repo, 2026-11-10); old plans that mention it are
  history and stay as written.

## Tests

New `TrackRecorderAutoStartTest`, in the style of `TrackRecorderArrivalPromptTest`, with
`Dispatchers.Unconfined` and the injected `monotonicNowMs` clock driven by hand (no 10 s sleeps).

1. Inside → outside → outside at +10 s → one `Started`, the track `startedAtHomePort`.
2. Inside → outside → outside at +9 s → nothing.
3. Inside → outside → back inside at +5 s → outside again → starts only 10 s after the second crossing.
4. First sample already outside, never inside → never starts.
5. `geofenceEnabled = false` → never starts.
6. Demo samples (`isDemo = true`, `hasLock = false`) on case 1 → starts, track marked demo.
7. Inside → outside → manual start at +3 s → manual stop at +20 s, still outside → no second,
   automatic start on the next outside samples (R1).
8. A recording stopped inside the circle, then a fresh exit → starts again (the next trip of the day).

## Decisions

- **D1 — fix shape:** arm on the crossing, clear on re-entry (above). Taken by the agent; nothing the
  user sees changes beyond the start now happening.
- **D2 — no movement gate:** the rule in `FEAT_DSC_Tracks.md` is "geofence exit (10 s debounce)", and
  the code is time-only. Keep it time-only, fix the KDoc. Objection: a boat moored near the circle's
  edge could start on jitter alone — a 150 m circle around La Salis makes that unlikely.
- **D3 — no sample, no start:** the check runs per sample. The standby cadence asks for a fix every
  5 s *and* 10 m of movement, so a boat that crosses and then stops dead just outside gets no further
  fix and does not start until it moves again. Accepted: no timer is added; a departing boat keeps
  moving.

## Review (2026-11-10, self-review against the code)

- **R1 — stale arm across a manual start (fixed in plan):** with the arm surviving outside the circle,
  a manual start before the 10 s, then a manual stop at sea, would leave the old arm set; the next
  outside sample would auto-start a new recording straight after the user stopped one. Cleared in
  `transitionTo`, test 7.
- **R2 — next trip of the day (confirmed):** a stop inside the port leaves "inside" as the previous
  state, so the next exit arms normally. A manual stop at sea leaves "outside", so nothing starts until
  the boat has been back inside — the intended reading of "on exit". Test 8.
- **R3 — finalize window (withdrawn by the Ask hop):** `processSample` and `finalizeTrack` share
  `lifecycleMutex`, so a sample arriving during a finalize waits and is then processed; no crossing is
  missed. The `if (isFinalizing) return` guard at `TrackRecorder.kt:663` and its "dropped" comment look
  unreachable — dead code, outside this change.
- **R4 — demo needs the map on screen (noted):** demo samples come from a Compose effect, so they stop
  while the app is in the background. A desk test keeps the map open; GPS mode is unaffected (the
  service samples).
- **R5 — demo timing (noted):** the demo ticker is 1 Hz, so the start lands 10–11 s after the pan out.
- **R6 — clock read per sample (accepted):** the new branch reads `monotonicNowMs()` on every OFF
  sample instead of only on crossings; it is one `elapsedRealtime()` call per 5 s fix.

## Files

- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — OFF branch, KDoc line 184.
- `app/src/test/java/ykws/android/maro/data/track/TrackRecorderAutoStartTest.kt` — new.
- `app/src/main/assets/maro.properties`, `app/build.gradle.kts`,
  `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the flag cleanup.
- `xTrack/Tracks/FEAT_DSC_Tracks.md` — the verification todo stays open until the device run.

## Out of scope (noted)

- `TrackRecorderConfig`'s defaults (43.55 / 7.00 / 500 m) are stale beside `maro.properties`; only
  tests that construct the config bare see them.

## Outcome

Implemented 2026-11-10. `TrackRecorderAutoStartTest` (10 tests), the `data.track` and `data.settings`
suites and `apk-build.bat` are green; the device pass is owed.

**Superseded in part (2026-11-10):** the circle-exit trigger and its debounce were removed by
[`261110_FEAT_PLN_Tracks_home-circle-forgotten-start.md`](261110_FEAT_PLN_Tracks_home-circle-forgotten-start.md);
the dead-flag cleanup stands.

- The OFF branch arms on the crossing, holds the arm while outside, clears it on re-entry and starts
  at ≥ 10 s; `transitionTo` clears the arm on every OFF↔ON change; the KDoc now says "10 s outside".
- The dead `tracking.homePort.enabled` → `TRACK_ENABLED_DEFAULT` → `AppSettings.trackEnabled` chain is
  gone; `track_enabled` is removed from the prefs on the next save.
- Demo mode needed no code: its map-centre samples run through the same branch (test 6 feeds them).
- **Ask hop:** no defect in the fix or the cleanup; every OFF↔ON path reaches `transitionTo`. It found
  three test gaps — the clear-on-re-entry branch, the exact-10 s boundary and the `Started` event
  count — now closed by the two added tests and the event assertion. Not asserted: `startedAtHomePort`
  and the demo mark on the started track, which the recorder does not expose before a stop.
- The "leave La Salis → verify auto-start" device todo in `FEAT_DSC_Tracks.md` stays open.
