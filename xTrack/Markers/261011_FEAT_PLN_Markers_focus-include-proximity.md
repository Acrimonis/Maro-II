<!-- scope: feature -->
# Markers — Focus framing includes the proximity zone

> **Feature:** Markers | **Branch:** feature/mkrEl
> **Created:** 2026-10-11 | **Status:** Implemented (Code-mode run, 2026-10-11)

## Goal

When a marker's dashboard opens, the camera's zoom-to-fit now frames the marker's **body plus the
proximity collar the map draws outward from it**, so the whole proximity zone lands on screen.
Previously the fit ([`260918_FEAT_PLN_Markers_focus-zoom-fractions.md`](260918_FEAT_PLN_Markers_focus-zoom-fractions.md:1))
framed only the body, so a proximity ring — a circle's reach is `radius + proximity` — spilled past the
screen edge even though the ring was drawn.

## Decisions

- The framed extent is the geometry grown by its proximity reach: a circle by `radius + proximity`, a
  corridor by the proximity on every side, and a pin — a point with no body — by its reach alone.
- The reach is read through its **one home**, `markerProximityRangeM(marker, pinM, multiplier)`, so the
  framing and the drawn ring can never disagree about where the zone ends. No second copy is added.
- The existing `marker.focus.*` shares are reused unchanged: `corridorShare` for a corridor, `zoneShare`
  for a circle and a pin.
- The pin's **zoom-in-only rule is dropped** — a deliberate reversal of the 2026-09-18 lock: a pin now
  zooms out to frame its reach, and `marker.focus.pinFootprintM` becomes its floor so a tiny reach never
  frames unusably tight.
- The centre stays `UserMarker.centerPoint`; the collar adds no dashboard-band offset.

## 1. The rule

```
reach  = markerProximityRangeM(marker, proximityPinM, proximityZoneMultiplier)
extent = body ⊕ collar            // circle: radius+reach; corridor: band+reach each side; pin: reach
share  = if (corridor) corridorShare else zoneShare
zoom   = zoomForExtent(extent, share × min(viewportW, viewportH))
centre = marker.centerPoint
```

The formula is unchanged from the 2026-09-18 plan — only the extent that feeds it grew.

## 2. Edits

- [`MarkerFocus.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt:1) — the signature
  drops `currentZoom` and gains `proximityPinM` / `proximityZoneMultiplier`; the extent is built per
  geometry through `markerProximityRangeM`; the pin's floor is `max(reach, pinFootprintM / 2)`; the
  zoom-in-only branch is deleted.
- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3405) `frameMarker` —
  passes `AppConfig.markerProximityPinM` and `AppConfig.markerProximityZoneMultiplier`.
- [`maro.properties`](../../app/src/main/assets/maro.properties:780) — the `marker.focus` comments now
  state the frame is the body **plus** the collar, and that `pinFootprintM` is the pin's floor.
- [`MarkerFocusTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/MarkerFocusTest.kt:1) — the
  collar-growth and pin-reach identities added; the two zoom-in-only cases dropped with the rule.

## 3. Verification

- `gradlew :app:assembleDebug` SUCCESSFUL, no new warnings.
- Scoped `ui.map` + `config` unit run green, `MarkerFocusTest` 8/8.
- Device pass owed: the framed ring on a default circle (reach ≈ 4× radius) and on a corridor.

## Outcome

Implemented 2026-10-11 on `feature/mkrEl`. `markerFocusTarget` frames the body plus the proximity
collar at the existing shares, reading the reach through its one home; `frameMarker` feeds the two
proximity values; the pin's zoom-in-only rule was removed with `pinFootprintM` kept as its floor. Build
and the scoped suite are green.

Findings, named and not changed: [`MarkerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:243)
still computes the reach inline at three sites (pin, circle, corridor) rather than through the one home,
and `MarkersViewModel`'s save path has its own form-based default; both are pre-existing and outside this
change's surface.
