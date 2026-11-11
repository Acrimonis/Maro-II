<!-- scope: feature -->
# Auto-start tracking — flagged zones and the home circle

**Status:** **parked — step 2, maybe later** (2026-11-10). The home-circle half is step 1:
[`261110_FEAT_PLN_Tracks_home-circle-forgotten-start.md`](261110_FEAT_PLN_Tracks_home-circle-forgotten-start.md),
which carries its own copy of the shared rules; on resuming, take the home-circle decisions from there and the
marker ones (D2, D3 exit posture, D5, D7, D8, the wizard, the marker feed) from here. Revision 5, three reviews folded · **Created:** 2026-11-10 · **Owner:** Tracks (the wizard row is Markers' surface)
**Builds on:** [`261110_FEAT_PLN_Tracks_auto-start-exit-debounce.md`](xxArchive/261110_FEAT_PLN_Tracks_auto-start-exit-debounce.md) — its circle-exit fix is **retired** by D1.
**Supersedes:** `FEAT_DOC_Tracks_decisions.md` §3.1 / §3.2 ("no speed-based auto-start … even when geofence is disabled").

## Request

1. A marker gains a checkbox, **Auto-start tracking** (default off), in a new **Tracking** section of the
   marker wizard, stacked **above** the Route role / Routing cost row.
2. **Exit rule** — on a flagged marker, tracking turns on when it is off, the boat is on water, has moved
   (the **idle detection thresholds**) and has exited the marker's radius zone or its proximity zone.
3. **Forgotten-start rule** — tracking has been off for a while (`tracking.autoStart.offMin`, minutes, 5),
   the boat is on water and has moved more than the idle detection thresholds, inside the zone → start.
   It applies to flagged markers **and the home circle**.
4. The home circle's **exit** logic goes; the circle is served by the forgotten-start rule alone.

On a normal departure the forgotten-start rule fires first (30–45 m after the boat starts moving, still
inside its zone); the exit rule is the safety net for flagged markers (a re-departure inside the off-time,
a start the first rule's conditions missed).

## Decisions

- **D1 — the circle has no exit trigger.** `TrackRecorder`'s circle crossing / 10 s arm (shipped earlier
  today) is removed. The circle stays what it was for everything else: the arrival prompt,
  `endedAtHomePort`, the trip-naming exclusion, `startedAtHomePort`, governed by `tracking.homePort.*`.
  **`trackGeofenceEnabled = false` disables the home circle source only**; flagged markers are unaffected
  (the KDoc at `TrackRecorder.kt:142`, "starts on movement alone", is already false and is reworded).
  **Cost:** a re-departure that begins within about `offMin − t(leave circle) − stopDetectionTime` of a stop
  is not auto-recorded at the circle — about 1.8 min after the stop at 2 kn from the centre, 3.4 min at
  6 kn; a later re-departure is caught once the 5 min pass, with a late first point. A flagged marker
  still has its exit rule for that case.
- **D2 — proximity zone = the plain ring.** `proximityOverrideM` metres outward from the zone's body, on
  the surface, no coastline test (the collar the map draws). The recorder reads `proximityOverrideM`
  directly. A null override (legacy, plain GPX waypoint) is unlimited: no proximity exit; a **Pin with a
  null override is no source at all** (it has no body and its ring would be everywhere). Unreachable
  through the wizard, which always stores an override.
- **D3 — two water postures, position-based, best-effort.** The gate asks `(LatLng) -> Boolean?` for the
  completing sample's position, wired to `NavigationViewModel.isWaterOrNull` (no 6 NM cut-off; `null`
  before the index loads; the spatial index is built for concurrent queries, so the recorder's
  `Dispatchers.Default` lane may call it, as `attachPositionClassifier` and `whereAmI` already do).
  - **Exit rule: `true` start, `false` hold, `null` start.** This deviates from the literal "on water" of
    Request 2 when the UI is gone — a departing crossing plus movement is judged evidence enough. *(To
    confirm with the user.)*
  - **Forgotten-start: strict — only `true` starts.**
  - **Consequence:** the home port's auto-start needs the UI process alive (with the screen off and the
    Activity alive it holds: `MapScreen`'s effects keep running and the ViewModel keeps its index). With the
    Activity destroyed, or the service stopped by `onTaskRemoved` (pre-existing), nothing auto-starts.
  - **Berth reading:** asked at the completing fix, 30–46 m out, so a quay that reads land does not block it.
    A person walking a pontoon at 2–3 kn passes movement and water — accepted.
  - **P0 — resolved 2026-11-10:** the user reports the La Salis berth reads on water.
- **D4 — one pure detector, two rules,** in `AutoStartDetector`. The recorder keeps `lastInsideGeofence` /
  `wasInsideGeofence` (the arrival-prompt re-arm `:697`, refreshed in both states at `:702`;
  `endedAtHomePort` `:1245`; the manual `startedAtHomePort` `:720`). Only `debounceStartTime` and the
  `DEBOUNCE_MS` constant go. The detector derives "home inside" from its own HomeCircle source; no
  separate `homeInside` parameter.
- **D5 — an exit arm has a window:** `tracking.autoStart.armWindowSec` (120 s) after its crossing, kept at or
  above the `stopDetectionTimeSec` slider maximum (90 s). Its purpose is narrow: a drift-out from rest and a
  gate that reads `false`; on a motoring departure the movement before the crossing is already in the
  window, so the start lands at the first fix past `edge + accuracy` (0–20 s after the edge at 2 kn, 0–10 s
  at 6 kn). There is no fixed hold any more.
- **D6 — `startedAtHomePort` and the arrival prompt on an auto start.** `beginRecording` takes `fromHome`
  (today it hard-codes `true` on the auto branch, `:720`), carried by the decision: exit rule — the home
  circle held the boat at the sample before the arming crossing; forgotten-start — it holds the boat at the
  completing fix or any fix of the movement window. Circle and marker completing together → `true`. The
  manual branch and the resume paths (which bypass `beginRecording`) are untouched. **An auto start with
  `fromHome` sets `arrivalPromptArmed = false`** (today `beginRecording` arms it, `:760`): the start is now
  inside the circle, and a stop at the fuel dock would otherwise ask Save / Discard seconds after leaving.
  The exit at `:697-699` re-arms it as it does today.
- **D7 — GPS-error hysteresis on marker exits:** outside only when `gap > accuracyM` (`0` when absent).
- **D8 — state follows the marker as stored:** keyed on `(id, geometry, override)`; an edit with the boat
  inside makes it "first seen", never an arm.
- **D9 — the forgotten-start rule, per sample while OFF, all of:**
  1. **zone:** inside the zone (`gap == 0`; a Pin uses its ring) at the completing fix or at any fix of its
     movement window — so a boat that began moving near the circle's edge still starts;
  2. **rested here (D13):** the source's "rested inside" latch is set;
  3. **off for a while:** at least `offMin` (minutes, default 5) since the last stop (D12);
  4. **moved** (D10);
  5. **water:** the gate answers `true` at the completing fix;
  6. **not suppressed:** no orphan checkpoint awaits recovery and the last-stop seed has resolved (D12).
  Level, no fixed hold: a miss is retried on the next fix.
- **D10 — "moved" reads the idle detection settings.** Home of the meaning: the Settings text "How far the
  boat can move within the adaptive time" — `stopDetectionDistanceM` (15 m, 10–30) within
  `stopDetectionTimeSec` (45 s, 10–90). The detector applies it as a window, not the service's anchor-based
  `AdaptiveGpsPolicy` (which starts in "moving" for a full window and re-anchors on every move): of the
  retained fixes (none older than `stopDetectionTimeSec`, at least two), the largest distance from the
  newest to any of them is at least the **policy's own effective threshold** (`stopDetectionDistanceM`, or
  the accuracy when it exceeds 20 m — extracted from `AdaptiveGpsPolicy` as one shared function, so both
  read one formula). When speeds are present, the completing fix **and the one before it** need at least
  `GpsLocationSource.MIN_SPEED_MPS` (0.5 m/s) — standby fixes carry Doppler speed, so this closes a single
  multipath outlier and the manoeuvring-inside outlier the old 10 s sustain used to absorb, at the cost of
  one fix (5–10 s). Non-demo fixes with `hasLock == false` are ignored (no-lock re-emits repeat the last
  position). The thresholds apply whether or not `stopDetectionEnabled` is on.
  - *Numbers:* at the defaults the Doppler gate binds — about **1 kn**. At 30 m / 10 s it needs about
    6 kn when the window spans two intervals, about 12 kn when it holds only two fixes 5 s apart; the whole
    10 s column is restrictive (10 m / 10 s needs about 2–4 kn). These are the user's own idle settings.
  - *The exit rule uses the same check* after its crossing.
- **D11 — values:** `maro.properties` → `AppConfig` → `TrackRecorderConfig` (live through `applyConfig`):
  `tracking.autoStart.offMin` (minutes, 5), `tracking.autoStart.armWindowSec` (120). `TrackRecorderConfig`
  gains **four** fields: those two and the two detection thresholds (already live through
  `observeSettings` → `TrackRecorderConfig.from`).
- **D12 — last stop, one clock, orphans.**
  - **One clock, monotonic.** Every interval the detector measures uses the recorder's `monotonicNowMs` at
    processing time; a sample's wall timestamp is never used (no-lock re-emits carry a stale one).
  - `lastStopMono` is set in `transitionTo` **only when the new state is OFF** (`discard` `:602`,
    `finalizeTrack` `:1378`).
  - **Seed on recorder creation** (START_STICKY, a reopen after `onTaskRemoved`): from `listTracks()`'s
    newest `endTimeMs` over **non-route** summaries (a saved route's end time is in the future:
    `TrackFromCourse.kt:118,129`): `lastStopMono = monoNow − (wallNow − endTimeMs).coerceAtLeast(0)`. Until
    the async seed resolves the forgotten-start rule is suppressed; no saved track → "no stop".
  - **Orphans:** the recorder takes `orphansPending: () -> Boolean`, which re-lists the `*_checkpoint.bin`
    files (cheap; while OFF none is the recorder's own). The service's one-shot `hasOrphans` scan
    (`TrackRecordingService.kt:144`) never clears after the prompt is answered, so it is not used.
- **D13 — "rested inside" latch (no start on arrival).** Without it the forgotten-start rule starts a
  recording as the boat motors **into** the circle or a flagged zone, then the arrival prompt asks Save /
  Discard. Per source: **set** when the boat is first seen inside (the source's first sample, e.g. the app
  opened at the berth or at sea inside the zone) or when it is inside and has **not** moved (below D10)
  for one detection window; **cleared** when the boat leaves the zone (`gap > accuracy`). The rule fires
  only while it is set. *Lost case, accepted:* a boat that enters a flagged zone while moving, with
  tracking off and the app watching, does not start until it has rested there. The user's practical use is
  the departure from La Salis / the home port (2026-11-10), where no start on arrival is the wanted
  behaviour.

## Detector

`data/track/AutoStartDetector.kt` — pure Kotlin, no coroutines, no Android types.

```kotlin
sealed interface Zone { fun gap(p: LatLng): Double }       // Circle, Corridor, Pin, HomeCircle
class AutoStartParams(val armWindowMs: Long, val offMinMs: Long, val idleDistanceM: Double, val idleWindowMs: Long)
class Fix(val p: LatLng, val monoMs: Long, val accuracyM: Float?, val speedMps: Float?, val hasLock: Boolean, val isDemo: Boolean)
class Decision(val fromHome: Boolean)
class AutoStartDetector {
    fun observe(f: Fix, p: AutoStartParams, sources: List<Source>)                    // ON: exit state, latches, window
    fun onSample(f: Fix, p: AutoStartParams, sources: List<Source>, lastStopMono: Long?,
                 suppressed: Boolean, waterAt: (LatLng) -> Boolean?): Decision?        // OFF
    fun reset()                                                                         // arms only
}
```

- `gap` = metres outside the zone's edge: `0` inside; Circle `max(0, d − radius)`; Corridor
  `max(0, segmentDistance − width/2)` (the matcher's step-4 quantity; agrees with `isInsideGeometry` up to
  the planar approximation's rounding); Pin = distance to the pin.
- `observe` runs in **ON** so exit states and latches are current when a recording ends.
- Sources: the HomeCircle (forgotten-start only, when `geofenceEnabled`), and per flagged marker a radius
  exit (Circle / Corridor), a proximity exit (override set) and a forgotten-start source.

| Rule | Source | Starts when |
|---|---|---|
| exit, radius | flagged Circle / Corridor | `gap == 0` → `gap > accuracy`, then moved (D10) within the arm window, gate not `false` |
| exit, proximity | flagged, override set | `gap ≤ override` → `gap > override + accuracy`, same |
| forgotten-start | flagged zone or home circle | D9 conditions 1–6 on one sample |

## Wiring

- **Recorder plumbing** (constructor, defaulted so the existing tests keep auto-starting nothing):
  `waterAt: (LatLng) -> Boolean? = { null }` (strict rule sees `null` → no start), `orphansPending: () ->
  Boolean = { false }`, `applyAutoStartMarkers(list)` beside `applyConfig`, `seedLastStop(wallEndMs: Long?)`
  called by the service once `listTracks()` answers.
- **Geometry** — `MarkerMatcher.zoneGapM(boat, geometry)`: Circle and Corridor share the step-4 pre-filter;
  the Pin path stays on `directDist`.
- **Marker feed (UI-free)** — the service collects `UserMarkerRepository.revision` (a replaying
  `StateFlow`; `markerChanges` has no replay) in `serviceScope`, runs `loadAll()`, keeps
  `origin == USER && autoStartTracking`, hands the immutable list over (`@Volatile` swap).
- **Water gate** — one more `@Volatile` field on `WhereAmIProvider`, `isWaterAt: ((LatLng) -> Boolean?)?`
  (same bridge, same lifecycle), registered from `MapServiceEffects`, cleared on dispose.
- **Demo mode** — no special case: the demo position is the map centre; demo samples (`hasLock = false`,
  `isDemo = true`) count.

## Marker model, wizard, persistence

- `UserMarker.autoStartTracking: Boolean = false`, declared **last** (protobuf numbers by declaration order);
  its `@property` line added to the KDoc. Old JSON and blobs read `false`. A Maro-to-Maro marker file
  carries the flag (`MarkerGpxImporter.kt:209-216`) — kept, the transfer is lossless by design.
- `MarkersViewModel` sites: form field beside `:199`, marker → form `:539` / `:858`, default `:801`, create
  `:1015`, update `:1078`.
- **Wizard** — `CardArea { Tracking section; SectionDivider(); SectionRow(Route role | Routing cost) }`
  (§2.3 stacking above the card's one `SectionRow`). `SectionDivider` is imported (it is not today);
  `OptionRow` is reused; `StepSectionHeading` becomes `internal` in `ui/components`; its description is the
  hint (three strings). The drawer scrolls (`DrawerScaffold scrollable = true`), so the taller step fits on
  small screens. `RoutingCostStep`'s KDoc ("two sections side by side", "No wizard step is added") is
  rewritten.
- Strings `wizard_tracking_title`, `wizard_tracking_description`, `wizard_auto_start_tracking`, both locales.

## Tests

`AutoStartDetectorTest` — pure, hand-driven monotonic clock:
1. Exit rule: crossed and moved → starts at the first fix past `edge + accuracy`; crossed but idle → nothing;
   gate `false` holds, later `true` starts; gate `null` starts; a gate shut past the window drops the arm.
2. Proximity: arms only past `override + accuracy`; no override never arms; Pin: ring only, no radius
   trigger; Pin with null override: no source.
3. Corridor `gap` (segment, end cap, beyond width); hysteresis on a 5 m marker with `accuracyM = 12`.
4. Re-entry clears each arm separately; nested zones independent; removed marker dropped; unflagged never
   arms; geometry edit with the boat inside arms nothing; stop inside a flagged zone, then leave → arms.
5. Forgotten-start with all conditions → starts on that sample.
6. Each condition alone missing → nothing: outside; latch clear; stopped 4 min ago (6 min → starts);
   moved too little; gate `false`; gate `null`; one fix; orphans pending; seed unresolved.
7. **Arrival:** motor into the circle / a flagged zone and berth → nothing; rest one window, then depart →
   starts. App opened inside the zone (first seen) → latch set.
8. Movement: 90° turn passes; no-lock duplicate ignored; demo sample counts; Doppler below 0.5 m/s on either
   of the last two fixes → no; accuracy > 20 m raises the threshold as the policy does; fixes older than the
   window drop out; extremes 30 m / 10 s need about 6 kn.
9. Outbound departure from the circle's edge at 2, 4, 6 kn → starts (zone by window).
10. `fromHome`: circle → true; marker exit from inside the circle → true; far marker → false; both → true.
    Car-speed track through a zone with gate `null` → nothing.
11. Thresholds and `offMin` changed through the params take effect on the next sample.
12. Clock: a seed from a wall end time 10 min ago → the off-time holds; a route's future end time is ignored;
    a wall timestamp on a sample is never read.

Recorder (`TrackRecorderAutoStartTest`, rewritten; the "manual start inside the wait" test renamed
"manual start leaves no arm"): `lastStopMono` set on discard and on a finalized save, not on ON; a
re-created recorder seeded from the saved track; suppression while a checkpoint file exists, cleared once it
is gone; `startedAtHomePort` on a marker start; **after a home-circle auto start, a stop inside the circle
does not raise the arrival prompt until the boat has left and returned**; defaults (`waterAt = { null }`)
start nothing.
Model: flag round-trip, legacy blob and JSON read `false`, form ↔ marker. Matcher: `zoneGapM` equals the
inline pre-filter. Policy: the extracted threshold function keeps `AdaptiveGpsPolicy`'s tests green.

## Files

- New: `data/track/AutoStartDetector.kt`.
- `data/track/TrackRecorder.kt` — the detector calls; `transitionTo` resets and sets `lastStopMono` on OFF;
  circle-exit branch, `debounceStartTime`, `DEBOUNCE_MS` go; `beginRecording(fromHome)` and the prompt arm;
  the constructor hooks; `TrackRecorderConfig` (four fields, `from`); KDoc at `:142` and `:184`; comments
  `:675-677`, `:1194`.
- `data/track/TrackRecordingService.kt` — marker collection, the water callback, the orphan probe, the seed;
  comment `:339-341`.
- `data/track/WhereAmIProvider.kt` — `isWaterAt`. `ui/map/MapServiceEffects.kt` — its registration.
- `data/track/TrackRepository.kt` — a cheap `hasCheckpointFiles()` if none exists.
- `data/location/AdaptiveGpsPolicy.kt` — the shared effective-threshold function.
- `data/track/Track.kt:89` — the `startedAtHomePort` KDoc ("a geofence exit").
- `spatial/MarkerMatcher.kt` — `zoneGapM`.
- `data/model/markers/UserMarker.kt`, `ui/map/MarkersViewModel.kt`,
  `ui/markers/wizard/steps/RoutingCostStep.kt`, `ui/components/` (the shared heading), `res/values{,-fr}/strings.xml`.
- `config/AppConfig.kt` (`:1369-1370` comment and the two keys), `assets/maro.properties` (two keys; comments
  `:623-624`, `:655-656`).
- Docs: `FEAT_DSC_Tracks.md` (Summary; the "OFF→ON via geofence exit" rule), `FEAT_DOC_Tracks_decisions.md`
  §3.1 / §3.2 superseded, `FEAT_DSC_Markers.md` (pointer), `docs/maro-code.md:18` (the new file), the earlier
  plan's Outcome ("retired by").

## Out of scope (noted)

- A marker-list filter axis or glyph for the flag.
- The arrival prompt and the trip-naming exclusion stay on the home circle.
- A service-owned coastline index (D3).
- The service's stale "Recovery available" label (`TrackRecordingService.kt:447`), which the same one-shot
  `hasOrphans` scan causes.

## Open — for the user

- **D3:** exit rule starting when the water state is unknown (UI gone), against the literal "on water".

## Review

- **Revision 1 (Ask), folded:** `observe` in ON; a position-based gate over `isWaterOrNull`; the recorder keeps
  its two geofence fields; hysteresis; state keyed on geometry; the `revision` feed; declaration order;
  sealed zones; `zoneGapM` notes; `geofenceEnabled` scope.
- **Revision 2 (Ask), folded:** the movement check's reachability; zone by window at the circle's edge; the
  short-stop cost; `lastStopMs` on OFF only; orphan suppression; `fromHome`; no-lock and speed; the shared
  heading; stale comments.
- **Revision 4 (user):** `offMin` in minutes, default 5; "moved" and the exit hold both read the idle settings.
- **Revision 4 (Ask), folded into revision 5:**
  - *Blockers:* start on arrival → the "rested inside" latch (D13); mixed clocks → one monotonic clock and a
    converted, route-free seed (D12); `hasOrphans` never clears → a re-listing probe (D12).
  - *Fixes:* "same rule as the policy" corrected to the Settings text plus the policy's shared threshold
    (D10); numbers recomputed (D10, D5, D1); the arrival prompt disarmed on a home auto start (D6);
    `stopDetectionEnabled` off still applies the thresholds (D10); decisions §3.1 / §3.2 superseded and the
    toggle's scope stated (D1); stale text listed (Files); recorder hooks and four config fields (Wiring,
    D11); default `waterAt = { null }`.
  - *Notes adopted:* two-fix Doppler (D10); arm window purpose and floor (D5); null-override Pin (D2);
    water field on `WhereAmIProvider`; `homeInside` derived.
- **Revision 5:** not re-reviewed.

## Outcome

_(open)_
