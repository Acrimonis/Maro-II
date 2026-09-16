# FEAT_PLN — Navigation — Dashboard position source

**Feature:** Navigation · **Branch:** feature/tracking-more · **Date:** 2026-09-16 · **Status:** implemented — device pass pending
**Companion plan:** `xTrack/RegulatedZones/260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md` (Step 1, tag stack)

## 1. Report

In GPS mode the dashboard can describe the panned view instead of the boat — observed as a boat inside the 300 m band that the dashboard did not register.

## 2. Rule — decided 2026-09-16

| Mode | Position that feeds the dashboard |
|---|---|
| GPS mode | the boat — `gpsPosition` |
| Demo mode | the marker — `_mapCenter` |

The rule applies to the whole dashboard with no exceptions: water flag, distance to shore, band membership, speed-zone query, zone situation, ETA and exit distance.

## 3. Evidence — one centre, two writers, one pipeline

| Site | Code | Effect |
|---|---|---|
| `MapScreen.kt:447-451` | `inZone300`, `distanceToZone`, `zone300`, `zoneSituation` collected from the ViewModel | the dashboard's inputs |
| `NavigationViewModel.kt:463` | the shore pipeline samples `_mapCenter` | the single position it uses |
| `NavigationViewModel.kt:733` | every GPS fix writes the boat's position | writer 1 — unconditional |
| `MapScreen.kt:980` | every pan writes the panned centre, accepted only while `autoFollowSuppressed` or in demo | writer 2 — conditional |
| `NavigationViewModel.kt:474-476, 481, 503-588` | `inZone`, `distToZone`, `speedZoneQuery`, `zoneSituation`, `isWater` | every dashboard value |

Why writer 2 can win for tens of seconds: fixes are gated — active mode defaults 5 m and 2 s (`SettingsManager.kt:69-70`), and while the policy calls the boat stationary the dormant interval is capped at 10 s only when accuracy is known poor (`maro.properties:11-15`). A pan on a stopped boat therefore leaves the pipeline reading the panned point until the next fix arrives.

Same defect as the tag stack's R8, seen through a different consumer: the value carries two jobs, and whoever writes last wins.

## 4. Fix design

### 4.1 One selection point, before the maths

Replace the pipeline's `_mapCenter` source with the mode-aware selection:

```kotlin
// Dashboard position: the boat in GPS mode, the marker in demo mode.
private val dashboardPosition: Flow<LatLng> =
    combine(_mapCenter, _gpsPosition, settings) { marker, boat, s ->
        if (s.gpsMode && boat != null) boat else marker
    }.distinctUntilChanged()
```

The existing `.sample(SHORE_SAMPLE_INTERVAL_MS)` chain stays in place after it, so the pipeline's cadence and cost are unchanged.

### 4.2 Fallbacks

- No fix yet (`_gpsPosition == null`) → the marker, which is what demo does today.
- GPS switched off (`_gpsPosition` nulled at `NavigationViewModel.kt:920`) → the marker, per the rule.

### 4.3 Deliberately untouched

- The pan callback keeps writing `_mapCenter`: the tag stack's marker point depends on it (companion plan §5.6).
- `DepthViewModel` keeps its own centre, written by the pan and by the follow camera, so depth-at-centre still describes the view rather than the boat.

### 4.4 Consequence to verify on device

`isWater` (the Earth or Water icon), `distanceToShore`, `distToZone`, `inZone300`, `speedZoneQuery` and `zoneSituation` all become boat-based in GPS mode. Nothing else changes: `headingDeg`, the cones and the zone geometry are computed elsewhere.

### 4.5 The band tag — RESOLVED 2026-09-16: one band, two surfaces

User decision: the 300 m band is split by surface — as a dashboard value it follows the GPS position, as a tag it follows the marker. Walk item 1 of `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md`.

- `inZone300` keeps the boat under §4.1 and stays the dashboard's band state, feeding the cards and the auto-show engine unchanged.
- The tag stack is handed its own band result computed at the marker point, produced where the strip is fed — `MapScreen.kt:2456` stops passing `inZone300`, so `RegulatedZoneComponents.kt` stays free of position logic and of any edit.
- Cost: one band query per tag frame at a point the stack already has.

### 4.6 The auto-reveal narrowing — RESOLVED 2026-09-16: it keeps the marker

The narrowing in `MapScreen.kt:2318` (`val boat = mapCenter`) decides which zones count as nearby when the layer is hidden, and it stays on the map centre — user decision: "if I am moving the map, it makes sense to reveal where the zone is if I am looking for them; when the map is GPS-centred the marker is the real zone value".

- Its two cases are both correct: while following, the marker is the boat, so the reveal reads the same value the dashboard uses; the divergence exists only while the user is deliberately looking elsewhere, which is when a reveal is wanted.
- So no second selection point is introduced here, and the tag stack and the reveal share one point while the dashboard has its own.

### 4.7 Implementation steps (walk item 4)

1. `NavigationViewModel.kt` — the pure predicate, so it can be unit-tested without Compose:
```kotlin
/** Dashboard position: the boat in GPS mode, the marker in demo or before the first fix. */
internal fun dashboardPositionFor(marker: LatLng, boat: LatLng?, gpsMode: Boolean): LatLng =
    if (gpsMode && boat != null) boat else marker
```

2. The same file — the flow that feeds the pipeline:
```kotlin
private val dashboardPosition: Flow<LatLng> =
    combine(_mapCenter, _gpsPosition, settings) { marker, boat, s ->
        dashboardPositionFor(marker, boat, s.gpsMode)
    }.distinctUntilChanged()
```

3. The pipeline source (`NavigationViewModel.kt:462-465`) — replace `_mapCenter` with `dashboardPosition`, keeping `.sample(SHORE_SAMPLE_INTERVAL_MS).mapLatest { … }` exactly as it is, and rename the lambda's `center` to `at` so the reader is not told it is the map centre when it may be the boat.

4. Nothing else moves: the pan callback keeps writing `_mapCenter` (§4.3), `DepthViewModel` keeps its own centre, and the narrowing of §4.6 keeps the marker.

5. Unit test `DashboardPositionTest` — three cases: GPS with a fix → the boat; GPS without a fix → the marker; demo → the marker, with a non-null boat to prove the mode wins over availability.

6. Apply §4.8's gate: the two boat-driven writes become `syncCenterFromBoat`, and `recenterNow()` restores the centre in the same frame.

7. Build, then the device pass of §6: the band card under a drag in GPS mode, the same values in demo, an approach reveal, and a drag left alone until the timer restores the boat.

### 4.8 R8 — the boat must not grab the centre while auto-follow is suppressed (walk item 9)

Confirmed as a bug rather than a wart. `_mapCenter` has two writers — the pan callback (`MapScreen.kt:980`) and the boat's own writes (`NavigationViewModel.kt:733` on every fix, `:979` on the dead-reckoning fallback) — and the boat's are not gated. So a fix arriving during a drag, during a wizard freeze (`freezeFollow`, `:426`) or with a drawer open (`setDrawerOpen`, `:416`) overwrites the point the user placed, while the camera itself stays put because the follow effect returns early while suppressed (`MapGpsFollowEffects.kt:84`).

The rule the user stated: while the map has been moved and has not been recentred — neither by the button (`recenterNow`, `:432`) nor by the resume timer (`startTimer`, `:438`) — nothing may grab the centre back.

- One gate, one home, so the rule cannot drift between the two writes:
```kotlin
/** Suppressed auto-follow means the user owns the centre — a fix may not grab it back. */
private fun syncCenterFromBoat(position: LatLng) {
    if (!_autoFollowSuppressed.value) updateMapCenter(position.latitude, position.longitude)
}
```
- `recenterNow()` gains one line so the button restores the centre in the same frame instead of waiting for the next fix: `_gpsPosition.value?.let { syncCenterFromBoat(it) }`.
- Live fixes keep updating `_gpsPosition`, the recording and — through §4.1 — the dashboard, so only the ownership of the centre changes.
- Two further defects fall out of the same gate: the marker wizard's corridor preview reads `mapCenter` (`MapScreen.kt:1137`), so a fix could move its second point under the user, and `savePosition()` persists `_mapCenter`, so the view restored at next launch is currently not the one the user left.
- Nothing re-drives the camera from the value, so the drawn map is untouched by this fix; the depth readout keeps its own centre as in §4.3.

## 5. Effect on the auto-show engine

`zone300AutoShow` and `regulatedZoneAutoShow` read `shore.inZone` and `shore.distToZone` (`NavigationViewModel.kt:622-680`), so the approach trigger follows the boat in GPS mode after this change — today a pan can arm or disarm a reveal. Intended, and it needs a device check on approach.

## 6. Verification

- `apk-build.bat` — package only.
- Unit test on the selection predicate: GPS plus a fix → the boat; GPS without a fix → the marker; demo → the marker. The predicate is pure, so this is the one test the pair of plans can carry.
- Device, GPS, inside the band, map dragged away: the band card, the distance and the water flag stay on the boat.
- Device, demo: the same values follow the marker.
- Device, GPS, on approach to a zone: the reveal still fires at the configured distance.

## 7. Files

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` | the `dashboardPosition` selection feeding the shore pipeline |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | the tag's own band result at the marker (§4.5), and the narrowing of §4.6 if accepted |
| `xTrack/Navigation/FEAT_DSC_Navigation.md` | section + `## Docs` pointer on completion |

## 8. Interaction with the companion plan

- Tags keep the marker; the dashboard keeps the boat; the only shared value left is `inZone300` (§4.5).
- R8 of the companion plan — a fix landing mid-drag snapping the value back — stops mattering for the dashboard, because the pan can no longer win there. It remains for the tag stack.
- Order of work: independent of each other; both touch `MapScreen.kt` only in different places.

## 9. Review of this plan

- **V1 — the rule is placed where it covers everything, and that is also its risk.** The pipeline is the single home of every dashboard value, so one selection covers the whole dashboard with no exceptions, as asked. The objection: it also silently moves values the user did not name — the water flag, the distance to shore, and the auto-show triggers (§5). All are consistent with the rule, and all are listed for the device pass rather than left to be discovered.
- **V2 — the map centre still carries two jobs, and this plan does not change that.** It stays pan-writable for the tag stack and for depth, so it is not "the boat" and cannot be treated as one. The band tag (§4.5) is where that shows, and it is deliberately left open rather than guessed.
- **V3 — the fix does not need the pan to stop writing.** Removing writer 2 would have been the smaller diff, but it would break the tag stack's marker point, which the user chose in Step 1. The selection point is the correct seam.
- **V4 — the pure predicate is the one test worth adding.** Unlike the tag-stack plan, this logic is a pure function of two positions and a mode flag, so it can be tested without Compose — recommended, and the only new test in the pair.
- **V5 — `_gpsPosition` is the boat's estimate when fixes stop** (`NavigationViewModel.kt:954-980`), so the dashboard stays truthful during a reception gap rather than freezing on an old centre. Objection: it is an estimate, capped by `DEAD_RECKONING_MAX_MS`; if it stops, the position stays at the last estimate rather than reverting to the marker, which is the right bias for a safety readout.
- **V6 — nothing else consumes the pipeline's input**, so the blast radius is the shore flow's own consumers, all listed in §4.4. Verified by search: the flow's outputs are read by the dashboard, the band tag, `CoastlineMapView`'s band geometry and the auto-show engine.
- **V7 — the pair of plans now states two different points on purpose**, boat for the dashboard and marker for the stack. That is a rule worth writing into both feature files at completion, or a future session will "unify" them and reintroduce one of the two faults.
