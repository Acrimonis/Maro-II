<!-- scope: feature -->
# Automatic tracking — the settings, the auto-save on return, the start on water

**Status:** implemented 2026-11-28 (revision 3) · **Created:** 2026-11-27 · **Owner:** Tracks ·
**Branch:** `feature/salis-out-track`
**Builds on:** the home-port exit rule (`AutoStartDetector`, committed `a9b1d3ab`).

## Request

1. A settings section at the top of the **Navigation** tab controlling the automatic tracking, per mode
   (GPS, Demo).
2. **Auto-save on return:** from open sea into a **50 m** port circle, then staying in it for **30 s**.
3. **Start on water:** the app is on and the boat is on water and moving → track, anywhere.
4. **A pending recovery no longer blocks a start:** save the interrupted trip, start the new one, merge by
   hand if they belong together.

## Today (shipped in `a9b1d3ab`)

- The exit rule starts a recording on a fix clearly outside the 100 m home circle after one inside, past
  the spike guard (GPS only), on water — and **only with no recovery pending**. It fires in either mode.
- The only off switch is `AppSettings.trackGeofenceEnabled`, with **no UI**, which also removes the arrival
  prompt and every other home-circle role. It stays as it is.
- The arrival prompt: a recording that has left the circle and then idles inside it (stop detection, then
  `boatMarkerIdleThresholdSec`) raises Save / Discard / Continue once per entry. Nothing saves on its own.
- The prompt is one-shot: `MapScreen` sets `showArrivalPrompt` and never clears it on `Stopped` /
  `Finalized`, so a recording ended by another path leaves the dialog standing.

## Decisions

- **D1 — one row per rule** (user, 2026-11-27): *Auto-start on leaving La Salis*, *Auto-save on returning
  to La Salis*, *Auto-start on water* — the port's name from `tracking.homePort.displayName`.
- **D2 — Continue after an auto-save, as an action row** (user, 2026-11-27): a pill carries no command
  (commands live in the action lane, `ui/notice/`), so an auto-save posts *Trip saved: [name] · Continue*
  with the action lane's existing dwell. *Continue* reopens the trip through
  `TrackRecorder.resume(track, fromCheckpoint = false)`; the dwell ending commits nothing — the trip is
  saved. A manual stop posts the plain saved pill as today.
- **D3 — the berth is within 50 m** of `43.5696, 7.1313` (user, 2026-11-27).
- **D4 — two toggles per row** (user, 2026-11-28): each row a `MultiSelectRow`
  (`docs/ui-component-guidelines.md` §2.7b) of *GPS* and *Demo*; both off turns that rule off. Six booleans
  on `AppSettings`; the gate reads `if (config.gpsMode) <rule>Gps else <rule>Demo`.
- **D5 — a recovery no longer blocks** (user, 2026-11-28): an automatic start that finds a live checkpoint saves
  every one as a finished track (`TrackRepository.finalizeOrphanedCheckpoint`, ending at its last point),
  then starts. No resume check, even after a crash at sea: a split trip is merged by hand. The recovery
  dialog closes and the service's "Recovery available" label clears.
- **D6 — moving is the stop detection's meaning** (user, 2026-11-28): the start on water reads
  `stopDetectionTimeSec` / `stopDetectionDistanceM` through `AdaptiveGpsPolicy.effectiveThresholdM`, as a
  positive move (below), never as "the policy is not IDLE".
- **D7 — re-armed by the same criteria** (user, 2026-11-28): a stop on water disarms the start on water until
  the boat is seen stopped by those settings, then moves.
- **D8 — values in `maro.properties`** (user, 2026-11-27): `tracking.homePort.arrivalRadiusM=50`,
  `tracking.homePort.arrivalHoldSec=30`, read through `build.gradle.kts` into `BuildConfig` beside the other
  `tracking.homePort.*` keys.

## Part 1 — the settings section

- A new first section of `NavigationSettings` (`ui/map/MapScreenSettingsOverlay.kt`): header, one
  `CardArea`, three rows (D1), each a label, a one-line description and the GPS / Demo `MultiSelectRow`
  (D4). Every string in `strings.xml` and `values-fr/strings.xml`.
- `AppSettings` + `SettingsManager`: `autoStartLeaveGps/Demo`, `autoSaveReturnGps/Demo`,
  `autoStartWaterGps/Demo`, with keys, load and save; mapped into `TrackRecorderConfig.from()` and live through
  `applyConfig`.
- **Defaults:** leave — GPS on, Demo on (today); water — GPS on, Demo off; return — GPS on, Demo on (O2).
- **The detectors keep folding fixes with a rule off,** so switching a rule on mid-trip, or moored in port,
  acts on the next fix rather than on a fresh arming.

## Part 2 — the auto-save on return

### Rule (per accepted fix while recording)

1. **From open sea:** the trip has been farther outside the 100 m home circle than its accuracy since it
   started **or since its last Continue** — an auto- or on-water-started trip always has.
2. **Entry:** a fix inside the arrival circle (same origin, `arrivalRadiusM`).
3. **Spike guard on the entry fix** (GPS only): implied speed from the previous accepted fix at most
   `tracking.boatMaxSpeedKn`. No Doppler test — an arriving boat moves.
4. **Hold:** a timer of `arrivalHoldSec` on the recorder's monotonic clock from the entry; a fix farther
   outside the arrival circle than its accuracy cancels it. Timer-based — the boat at the berth may send
   no fix for 30 s at the 1 s / 1 m recording rate.
5. **Fire:** checkpoint, then the normal finalize (`stop()`'s path: `endedAtHomePort`, naming, simplify,
   `Finalized`), then the D2 row in place of the saved pill.
6. **Gate:** the return row's toggle for the current mode (D4).

### With the other pieces

- **The arrival prompt** keeps the 50–100 m ring (a fuel dock, the outer harbour) and the modes the auto-save
  is off for; inside 50 m the save lands at 30 s, before the prompt's stop window plus idle threshold.
  `MapScreen` clears `showArrivalPrompt` on `Stopped` / `Finalized`.
- **Continue** reopens the trip; rule 1 then needs a fresh exit, so a boat still at the berth is not saved
  again 30 s later.
- **The next departure:** the inside fixes arm the exit rule, so leaving starts a new trip.

## Part 3 — the start on water

**Reverses** `FEAT_DOC_Tracks_decisions.md` §3.1 and §3.2 ("auto-start on movement alone is NOT used outside
the home circle"); both are rewritten when it lands.

**Covers what the exit rule cannot:** the app opened at sea, a trip from another port, a start the exit
rule missed (the map gone at the exit, a service restart after leaving).

### Rule (per accepted fix while recording is off)

1. **Outside the home circle**, farther than the fix's accuracy — inside it the exit rule owns the start;
   the berth reads water, so without this the berth would start a trip that the auto-save then ends, in a
   loop. On the fix that leaves the circle the exit rule is asked first; one start per fix.
2. **On water:** `waterAt` answers `true`.
3. **Spike guard:** the exit rule's (GPS only).
4. **A positive move (D6):** the fix lies at least `tracking.waterStart.distanceM` (50 m, O1) from the
   reference point — widened to the fix's accuracy by `effectiveThresholdM` — and
   its Doppler speed is at least 0.5 m/s. The reference is the first accepted fix after the detector starts
   or re-arms, so the app opened under way starts once the boat has covered that distance.
5. **Armed (D7):** armed at the detector's start; a recording stopped or discarded outside the home circle
   disarms it; it re-arms once the boat is seen stopped — no move beyond the stop-detection distance for
   the stop-detection window, read as a fix gap at least that long, as the standby stream sends nothing
   while still — and that rest position becomes the reference.
6. **Recovery:** D5.
7. **Gate:** the water row's toggle for the current mode (D4).

### Risks

- **Waterfront read as water** (a quay, a beach, a coastal road where the coastline is coarse): a walk or a
  drive starts a trip. Doppler, the spike guard and the fire distance narrow it; nothing removes it.
- **A foreign port's berth reads water:** the trip there is never saved on its own (the auto-save is La
  Salis'), and runs at the 1 s rate until stopped by hand; after that stop, D7 keeps it stopped until a
  rest and a departure.
- **Demo:** the map centre panned over water starts a demo trip at once — hence Demo off by default.
- **A trip stopped at sea restarts after a stop-and-go:** the exit rule's "a trip stopped at sea never
  restarts" holds no longer once this is on; the feature file's summary is rewritten with it.

## Part 4 — a recovery no longer blocks (D5)

- Both start rules drop the "no recovery pending" condition. On a start, before `beginRecording`: list the
  live checkpoints (`liveCheckpointsLocked`, stale ones excluded), `finalizeOrphanedCheckpoint` each, then
  start.
- The recovery offer closes: `TrackViewModel`'s recovery state clears and the service's "Recovery available"
  label is recomputed, so neither offers a checkpoint that is already a saved track.
- A finalized checkpoint keeps the stats `withDerivedStats` gives it; it is named as a recovery's Save names
  it today.

## Other risks

- **A stop at the berth longer than 30 s** (crew pick-up) splits the trip — Continue (D2) rejoins it within
  the row's dwell, the merge after it.
- **A spike into the arrival circle while sailing:** cancelled by the next 1 Hz fix outside; the
  implied-speed guard covers a spike followed by a fix gap.
- **The 100 m circle against the 2026-10-25 widening:** the radius was raised from 100 to 150 m because a
  route's destination sat 106–113 m from the port, outside the circle, so the port's marker named it; at
  100 m that naming returns — accepted (O3).

## Order

1. Settings section and the start gates (Part 1).
2. Auto-save, Continue, the prompt cleared on stop (Part 2).
3. Recovery no longer blocks (Part 4).
4. Start on water (Part 3) and the decisions §3.1 / §3.2 rewrite.

## Open points

None. Settled 2026-11-28 (user):

- **O1 → 50 m:** the start on water fires once the boat is 50 m from its reference point, a new key
  `tracking.waterStart.distanceM=50` in `maro.properties`. A pontoon walk that stays within 50 m of where
  the boat rested starts nothing; a longer one still can.
- **O2 → on:** the auto-save defaults to on in both modes.
- **O3 → 100 m:** the home radius stays 100 m; the port's marker naming a route that ends 100–150 m out is
  accepted.

## Files (expected)

- `data/settings/SettingsManager.kt` — six toggles, keys, load / save.
- `data/track/TrackRecorder.kt` — `TrackRecorderConfig` (toggles, arrival radius, hold), the gates, the
  arrival hold beside the idle timer, the stop that disarms the start on water, the recovery finalize before
  a start.
- `data/track/` — the arrival rule and the start on water as pure detectors beside `AutoStartDetector`.
- `data/track/TrackRepository.kt` — a finalize-all for the live checkpoints; `TrackViewModel.kt`,
  `TrackRecordingService.kt` — the recovery offer and label.
- `ui/map/MapScreenSettingsOverlay.kt` — the section; `ui/map/MapScreen.kt` — the prompt cleared on stop, the
  D2 row; `ui/notice/` — the row's key.
- `res/values/strings.xml`, `res/values-fr/strings.xml`; `assets/maro.properties`; `app/build.gradle.kts`.
- `xTrack/Tracks/FEAT_DSC_Tracks.md`, `FEAT_DOC_Tracks_decisions.md` §3.1 / §3.2.

## Tests

- Arrival: entry, hold, cancel by a fix out, spike, from-open-sea, re-arm only by an exit after Continue,
  each gate.
- Start on water: home circle excluded, land and unknown water, spike, no move, Doppler floor, fire
  distance, app opened under way, disarm on a stop at sea, re-arm by a rest, each gate.
- Recovery: a start finalizes every live checkpoint and starts; a stale one is not finalized.
- Recorder: save at 30 s, the prompt kept in the ring, Continue then no second save, the next departure
  starts, one start per fix when both start rules agree.

## Outcome

Implemented 2026-11-28 through `#impl`: Parts 1–4 in the order above. `assembleDebug` and 1 917 unit tests
green (`ArrivalDetectorTest` 10, `WaterStartDetectorTest` 14, `AutoStartDetectorTest` 17,
`TrackRecorderAutoStartTest` 24). The device pass is owed.

Deviations from the plan as written:

- **D8:** the three values (`tracking.homePort.arrivalRadiusM`, `arrivalHoldSec`, `tracking.waterStart.distanceM`)
  are read by `AppConfig` from `maro.properties` at run time, like the other `tracking.*` keys, not baked into
  `BuildConfig` — still one source per value.
- **The arrival hold's timer** is a coroutine `delay` on the recorder's lane, not the injected monotonic clock.
- **The row's wording** is *Back in port: "[name]" saved · Continue*.
- **The shared tests** (the far-outside test, the implied-speed guard) live once in `FixGuards`.
- **A resumed trip** re-arms the arrival prompt only when resumed outside the home circle (`!lastInsideGeofence`),
  so Continue at the berth asks nothing until the trip has left.

**Ask review (2026-11-28), one pass — no blocker.** Fixed: the decisions §3.1 / §3.2 and the feature summary
rewritten; the water rule's rest is now the stop detection's anchor-and-window test, so a boat swinging at anchor
rests; a hold ended by the toggle switched off, or spent before a save that fails, no longer hides the arrival
prompt; the prompt no longer returns after Continue at the berth; one checkpoint that fails to save no longer
hides the others (and cancellation is rethrown); the guard copied three times is `FixGuards`; the stop sheet
closes with the recording; `finalizeTrack`'s detached doc block merged; the arrival radius is capped at the home
radius. Open, unfixed:

- **Leave off, water on:** with *Start on leaving* off and *Start on water* on, the water rule still starts the
  departure ~50 m past the home circle, with `startedAtHomePort = false` — the user's call.
- A Discard in the recovery dialog racing an automatic start can see its checkpoint saved instead — accepted by the user 2026-11-28: nothing is lost, the extra track is deleted by hand.
- After Continue, Discard deletes the whole reopened trip (the resume keeps its id) — older behaviour, now
  easier to reach.
- `RecoveriesSaved.trackIds` is read by tests only.
- The 50–100 m ring with the auto-save on has no recorder test of its own.
