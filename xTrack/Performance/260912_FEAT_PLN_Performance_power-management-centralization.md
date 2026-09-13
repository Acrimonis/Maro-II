# Performance — Power Management Centralization

**Status:** plan (not implemented) · **Branch:** `feature/staying-alive` · **Date:** 2026-09-12
**Revised:** 2026-09-12 — review pass applied: twelve findings, the interaction reset, and the staleness rule
**Owner feature:** Performance · **New package:** `data/power/`

---

## 1. Problem

Power and background behaviour is spread across five owners, and no component can answer
"should this app be awake right now?"

| Concern | Current owner | Location |
|---|---|---|
| Screen-lit flag | `MainActivity` window | [`MainActivity.kt:98-107`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:98) |
| `keepScreenOn` pref | `SettingsManager` | [`SettingsManager.kt:84`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:84), `:348` |
| Screen toggle UI | Settings overlay | [`MapScreenSettingsOverlay.kt:1148`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1148) |
| **Service start** | `MainActivity.onCreate` | [`MainActivity.kt:159`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:159) |
| **Service stop** | three sites in `MapScreen` | [:1005](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1005), `:1862`, `:1875` |
| Battery-exemption prompt | `MainActivity`, separate prefs file | [:115-145](../../app/src/main/java/ykws/android/maro/MainActivity.kt:115), `maro_battery_prefs` |
| GPS duty cycle, compass gate, refresh cap | Performance | this feature |

Four concrete asymmetries justify the work:

1. The service is started by one layer and stopped by another — there is no single lifecycle owner.
2. `maro_battery_prefs` is the only preference store outside `SettingsManager`.
3. The exemption prompt is triggered by a track-recovery side effect
   (`recoverOrphanedCheckpoints()` at [`MainActivity.kt:118-128`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:118)).
4. The rules are stale — [`FEAT_DSC_Performance.md:46`](FEAT_DSC_Performance.md:46) still claims
   "no background/foreground-service, no wake locks, no `keepScreenOn`".

## 2. Core concept — two independent channels

The design rests on one distinction that the current code conflates:

- **Screen channel** — `FLAG_KEEP_SCREEN_ON`, attached to a *window*. Only works while a window is
  visible. Cannot act in the background, by Android's design.
- **Keep-alive channel** — the foreground service plus the battery-optimization exemption. Keeps the
  process and GPS running with the screen off.

Consequence: "keep the screen on always" is unimplementable and was the source of the original
confusion. The screen channel is front-only *by construction*, and the setting is named to say so.

## 3. Locked decisions

1. **Ownership** — Performance. Code lands in a new `data/power/` package.
2. **Screen channel naming** — the toggle is renamed to a lock phrasing, and the persisted key is kept
   (`keep_screen_on`), so nothing is lost for existing installs.
3. **Terminology collision resolved** — `screen lock` is reserved for the device-timeout feature. The
   existing 📵 guard, which blocks accidental touches, is renamed to **`touch lock`** (its plan file is
   already `260827_FEAT_PLN_Ui_General_touch-input-lock.md`). The `status.lock.*` tokens are **not**
   renamed — a comment suffices; only the user-facing label is reviewed.
4. **Gates dropped** — at-sea/on-land, charging, and app-front/always are all out. No multi-select
   control, no ignore semantics, no cross-gate combination logic, no process-lifecycle dependency.
5. **Screen hold rule** — the screen is held while **moving, or within the grace period of the last
   touch**. Either condition holds it; both must lapse before it releases. Releasing stops *preventing*
   the lock; the device's own timeout takes over.
6. **Grace period** — 1–15 min slider, default 5. Described to the user as "lock after this long with no
   movement **and** no interaction", since the interaction reset makes "idle" insufficient wording.
7. **Unknown and stale fixes** — no fix yet **holds** (transient, self-corrects within seconds); a fix
   older than the staleness bound **releases** (motion cannot be proven, and holding forever is the bad
   outcome). The bound follows the existing GPS reception handling rather than adding a third rule.
8. **Speed source** — the UI position pipeline, passed into the policy as an input. Chosen because the
   screen channel only exists while the UI is alive, and because a service-sourced gate would lose its
   input after phase 3 makes the service conditional. The policy takes speed as a parameter, so phase 3
   may add the service as a second caller without changing the policy.
9. **Movement gate vs master toggle — additive, not replacement.** Master on + gate off = hold whenever
   front, which is today's behaviour.
10. **Purpose reframed** — the movement gate is a *usability* setting (let the phone lock when moored),
    not primarily a battery feature, since the device is typically on shore power when it matters.
11. **Keep-alive reasons** — recording (hard floor), passive idle-marker capture (phase 4a), zone latch
    (phase 4b). The service runs while **any** reason holds.
12. **No wake lock** — deliberate. The foreground service plus the exemption is the sanctioned
    mechanism; `WAKE_LOCK` stays absent from the manifest. Revisit only with field evidence of GPS gaps
    while the screen is off.
13. **Settings home** — extend the existing keep-screen-on area in the System tab. The orphaned
    `settings_section_power` string is removed (it was never referenced).
14. **Where the numbers live — split by nature.** The value the *user* chooses (current threshold,
    current grace) lives in `SettingsManager` prefs. The **bounds, the default and the developer
    constants** — grace 1–15 min, default 5, movement threshold 1 kn, staleness bound 30 s — live in
    `maro.properties`, surfaced as typed `AppConfig` accessors and consumed by `SettingsManager`,
    `PowerKeeper` and the settings slider. Reversed 2026-09-12: the original decision filed all of them
    as prefs, which put slider bounds and a developer constant in a user-editable store. `PowerPolicy`
    itself now references **neither** — every number arrives through `PowerInputs`, which keeps it
    trivially unit-testable.
15. **A push is not a reading.** Freshness is sourced from the data, not inferred from a call arriving:
    `PowerKeeper.onSpeed` requires an `isNewReading` flag, `SpeedFreshness` tracks the newest genuine
    reading, and only a new reading ages from zero. Added 2026-09-12 after the original implementation
    let a re-published cached speed re-stamp itself forever and hold the screen. Demo mode has no speed
    truth at all, so it is **grace-governed**: pan speed is not treated as motion, and dragging the map
    is the interaction that holds the screen.

## 4. Design

### 4.1 `data/power/PowerPolicy.kt` — framework-free, stateful, testable

Mirrors the [`AdaptiveGpsPolicy`](../../app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt)
precedent: no Android imports, clock injected by the caller, unit-tested on the JVM. Stateful because
the grace timer and the zone latch are time-dependent.

Inputs: `keepScreenOn`, `movementGateEnabled`, `speedKn` (nullable), `fixAgeMs`, `lastTouchMs`,
`thresholdKn`, `graceMs`, `stalenessBoundMs`, `recording`, `passiveCaptureEnabled`, zone latch state,
clock.

```
moving    = no fix yet  -> true                      // transient, self-corrects
          = fix stale   -> false                     // cannot prove motion
          = otherwise   -> speedKn > thresholdKn
screenOn  = keepScreenOn && (movementGateEnabled ? (moving || withinGrace(lastTouchMs))
                                                : true)   // additive: gate off = hold while front
keepAlive = recording || passiveCaptureActive || zoneLatched
```

`PowerState(screenOn: Boolean, keepAlive: Boolean)`. The battery-exemption question is deliberately
**outside** `PowerState` — the prompt **trigger** is the keeper's, the **query** itself lives in
`data/power/BatteryExemption.kt`, and neither is a property of the power state.

### 4.2 `data/power/PowerKeeper.kt` — the Android-side owner

Scoped to the **screen channel** in phase 1; grown in phase 3 to own service start/stop.

- Holds `StateFlow<PowerState>`.
- Phase 1 observes settings, the UI position/speed feed, the last-touch timestamp, and
  `TrackRecordingService.isRecording` (for the floor).
- Phase 1 also consumes the app's own freshness signals: in GPS mode the caller pairs the speed with
  `NavigationViewModel.gpsStale`, so a lost fix keeps the value but stops counting as a new reading.
- Phase 3 adds service start/stop ownership — replacing the unconditional start and the three
  `MapScreen` stop sites.
- Owns the battery-exemption **prompt trigger** — replaces the prefs-file + recovery-side-effect trigger.
  The exemption **query** itself is delegated to `data/power/BatteryExemption.kt`, the single home shared
  with the two `MapScreen` gates; the keeper's `isExemptFromBatteryOptimizations()` is the seam kept for
  the phase-3 service lifecycle.
- Does **not** touch the window: that needs an Activity, so it stays a thin applier.

Named `PowerKeeper` deliberately — `PowerManager` would shadow `android.os.PowerManager`.

### 4.3 Dependency direction and the speed source

`data/power/` depends on `data/settings/`, `data/track/` and `spatial/` — never on `ui/`. The
foreground signal that `ui/` would have provided is not needed, since no front/always gate survives.

The app already runs **two GPS subscribers** — the UI pipeline and the service's own `GpsLocationSource`
([`:97`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:97)). The policy
adds **no third subscription**: the caller passes speed in, which is why the source can differ per
phase without touching the policy.

### 4.4 Activity applier and the touch feed

One `DisposableEffect` collecting `PowerKeeper.screenOn` and mutating the window flag. A single
mutation point per state change is what preserves the existing Android 16 workaround documented at
[`MainActivity.kt:94-97`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:94).

The last-touch timestamp is captured in `MainActivity.dispatchTouchEvent` — the one choke point that
sees every touch, including those handled by the osmdroid `MapView`, which never pass through Compose
and would otherwise be missed. The handler only records a timestamp; it must **not** drive
recomposition per touch, or the power feature becomes a performance problem.

## 5. Settings surface

System tab, extended keep-screen-on area:

- `ToggleRow` — "Don't lock the phone while the app is open" (renamed; FR drops the ambiguous
  "allumé" in favour of a lock phrasing).
- `Expander` → `NestedCard`:
  - `ToggleRow` — hold the screen while moving, plus a speed-threshold `SliderRow`
  - `SliderRow` — grace period, 1–15 min, described as no movement and no interaction
  - `ToggleRow` — passive idle markers (phase 4a)
  - `ToggleRow` — zone alerts (phase 4b)

Follows the Card/Expander/NestedCard model and `docs/ui-component-guidelines.md`.

### Semantics matrix — master toggle versus movement gate

| Master | Gate | Behaviour |
|---|---|---|
| off | any | nothing held; the device timeout applies |
| on | off | held whenever the app is in front — today's behaviour, unchanged |
| on | on | held while moving, or within the grace period of the last touch; released otherwise |

## 6. Keep-alive reasons in detail

**Recording floor.** Guaranteed while a track records; no gate can veto it. Today this is
over-satisfied because the service is unconditional — once the service becomes conditional, this
becomes a real rule.

**Passive idle markers (phase 4a).** Capture idle snapshots while backgrounded and *not* recording.
Blocked on a structural issue: `AutoMarkerManager` is recorder-owned with its lifecycle tied to the
recorder ([`TrackRecordingService.kt:90-91`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:90)),
and idle snapshots come from the recorder's own idle detection. Either decouple idle detection from the
recorder, or limit capture to recording sessions (which already works today).

**Zone latch (phase 4b).** A position predicate belonging to keep-alive, not to the screen gate — the
screen must not stay lit merely because the boat sits inside a zone. Design fitted to what exists:

1. Evaluate the predicate **inside the service on its own fixes** — it already owns a GPS source and
   its own `AdaptiveGpsPolicy` ([:97-100](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:97)).
2. Source the zone through the existing `WhereAmIProvider` seam
   ([:234-242](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:234)), keeping
   zone-data ownership in `data/regulation/` and `spatial/`.
3. Treat it as a **latch, not a wake-up**: a reason can only sustain a running session, never start
   one, because detecting the condition requires GPS. The chain is moving → service alive → cross into
   a zone → latch set → slow down or anchor → the latch holds the service up.
4. Surface it on the notification's existing status line — the alert must be visible or the reason is
   not worth building.

### Notification

The status line already carries five segments and already knows about movement
([`:363-372`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:363)). A
notification is visible on the lock screen with the screen off — exactly the situation keep-alive
exists for. Add a zone segment.

Two constraints found during analysis:

- The live status payload is pushed by a UI-driven five-second loop
  ([`MapServiceEffects.kt:138-139`](../../app/src/main/java/ykws/android/maro/ui/map/MapServiceEffects.kt:138)),
  which stops when backgrounded. Anything the service needs to know in the background must be
  evaluated by the service itself.
- The channel is created `IMPORTANCE_DEFAULT`
  ([:418-419](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:418)) while the
  builder sets `PRIORITY_LOW` ([:378](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:378)).
  Importance governs behaviour, and the same channel carries the routine "Ready" status, so a
  noticeable zone alert needs its own channel rather than a raised shared one.

## 7. Order of work

Sequenced by dependency. Phase 1 is deliberately independent of the service so it ships with no
regression risk and delivers visible value on its own; everything after it is gated.

**Phase 0 — corrections, no behaviour change.**
- Rewrite the stale Performance rules at [`:46-47`](FEAT_DSC_Performance.md:46) to state the posture that
  is **true today** — the service is unconditional, `keepScreenOn` exists, the exemption exists, no wake
  locks. The conditional posture is stated in phase 3, once it is true. A rulebook must not run ahead of
  the code.
- Fix the `keepScreenOn` default contradiction (`:84` vs `:348`).

**Phase 1 — screen channel. Blockers: none. Touches no service, no Tasker, no notification.**
- **1a** — `PowerPolicy` screen half: speed gate, interaction reset, grace timer, unknown and stale fix
  rules, with unit tests. Pure, no Android.
- **1b** — screen-channel `PowerKeeper`, new pref keys, Activity applier, `dispatchTouchEvent` touch
  feed, toggle rename, settings section, EN + FR strings, touch-lock rename bookkeeping.
- **1c** — battery-optimization consolidation: `maro_battery_prefs` into `SettingsManager`, and the
  prompt trigger moved to recording start. **Not behaviour-neutral** — it changes when users see the
  dialog, deliberately, so it belongs in the phase notes rather than being presented as a cleanup.

**Phase 2 — Tasker reconciliation. Own feature. Gates every phase below.**
The service cannot become conditional until the Tasker water-state query has a home.
`plans/tasker-water-state-integration.md` holds the earlier work on this integration.

**Phase 3 — conditional service. Blocked by phase 2.**
The service follows keep-alive reasons instead of starting unconditionally at app open; the persistent
notification becomes conditional with it; `PowerKeeper` grows to own service start/stop; the Performance
rule is updated to the conditional posture now that it is true.

**Phase 4 — additional keep-alive reasons. Blocked by phase 3, since a reason can only sustain a
running service.**
- **4a** — passive idle markers, also blocked on the recorder decoupling (§6).
- **4b** — zone latch, the notification segment, and the alert-channel decision.

**Phase 5 — tracking, distributed.**
`docs/maro-code.md` gains the `data/power/` row with phase 1, together with the Ui_General keep-screen-on
claim and the routing-map ownership re-point. Feature-file summarising closes with phase 4.

## 8. Open items

**Settled this pass:** unknown and stale fix handling; the interaction reset; the speed source; the
touch-lock rename; the additive master-versus-gate semantics; the tunables home.

**Recommended, pending confirmation:**
- **`PowerKeeper` scope in phase 1** — recommendation: keep it narrow (screen channel only) rather than
  deferring it, since the applier needs a home and deferring buys a phase-3 refactor.
- **Exemption prompt trigger** — recommendation: recording start, keeping the recovery path as a
  secondary, since recovery fires exactly when the user was already bitten by the missing exemption.
- **Speed floor** — decide whether the 1 kn threshold reuses or **deliberately** diverges from
  `GpsLocationSource.MIN_SPEED_MPS`, rather than quietly becoming a third number meaning "stopped".
  (The grace range is settled: 1–15 min, default 5, set 2026-09-12.)

**Deferred:**
- **Tasker water-state query** — answered at runtime by the service; with a conditional service it goes
  unanswered while idle. Re-homing it to a manifest receiver reading the persisted `isWater` setting is
  possible but returns a **stale** value (nothing updates it while backgrounded). It is the phase 2
  blocker and belongs to the Tasker feature revisit, not to this plan.
- **Persistent notification** — currently always present; becomes recording/keep-alive-only in phase 3.
- **Passive-marker decoupling** — resolve before phase 4a (see §6).

## 9. Risks

- **Stale last-known speed** — mitigated by the stale-fix release rule plus the interaction reset, both
  of which prevent an unbounded hold. A missing permission is explicitly accepted as out of scope: the
  app is unusable without it.
- **Conditional service** silently breaks the Tasker integration and changes notification behaviour — a
  deliberate choice, not a side effect.
- **Passive-marker capture** is pipeline work, not a settings row, and touches BoatTrace.
- **Stale background state** — any predicate depending on the UI update loop is wrong by construction.
- **Android 16 window-flag regression** if the apply path stops being a single mutation.
- **Terminology collision** between the new device-lock setting and the existing 📵 guard — mitigated by
  reserving `screen lock` and renaming the guard to `touch lock`.

## 10. Out of scope

Wake locks; charging gate; at-sea/on-land gate; app-front/always gate; route or guidance logic (no such
feature exists — the only "navigation" in the codebase is click-N-move panning); GPS cadence retuning;
a multi-select trigger component; renaming the `status.lock.*` tokens; serving speed from a third GPS
subscription.

## 11. Test plan

### Unit — `PowerPolicy`

Speed gate above and below threshold; interaction reset extending the hold; grace release when both
conditions lapse; no-fix hold; stale-fix release; recording floor unaffected by gate state; gate
disabled equals hold-while-front.

### Device

1. Build with `apk-build.bat`, deploy with `apk-deploy.bat`.
2. Slide the grace to its minimum (1 min). No debug override is added — the range now makes the wait
   short enough to test honestly, without shipping a debug-only path that would have to be proven
   gated out of release.
3. **Grace release** — master on, gate on, leave the app untouched: confirm the screen locks at the
   grace boundary.
4. **Interaction reset** — at roughly 30 seconds, touch the map: confirm the hold restarts and the
   screen survives a further full grace period. This step is the reason the reset exists, so it must be
   exercised explicitly.
5. **Speed gate** — in demo mode, drive the synthetic feed above 1 kn and confirm the hold; drop below
   and confirm the release after the grace.
6. **Master-off regression** — with the gate off, confirm today's hold-while-front behaviour is
   unchanged.
7. **Recording floor** — start a recording, background the app, confirm points continue to accrue and
   the notification persists.
