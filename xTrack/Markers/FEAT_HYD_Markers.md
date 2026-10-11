# Context Hydration — Markers — 2026-10-11

**Last Bake:** 2026-10-11 13:17 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met on this session's changed file, `app/src/main/assets/maro.properties` — it is a properties file, not a machine-shaped binary, and it was read as text — no dependency was added, no `#`-command but the user's own three (`#new mkrEl`, `#bake`, `#commit`, `#push`) ran, no device was touched, and every claim written about the code carries a file read behind it. The branch was cut only on the user's `#new mkrEl` and the implementation ran on the approved plan.

## State

One session, 2026-10-11, on a branch cut from `origin/develop` by the user's `#new mkrEl` (`feature/mkrEl`, no upstream), one change landed.

**The frame includes the reach.** A marker's dashboard open now frames the marker's **body plus the proximity collar the map draws outward from it**, so the whole proximity zone lands on screen — before, the fit framed only the body and the ring spilled past the screen edge. [`markerFocusTarget`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt:45) builds the extent per geometry — a circle at `radius + proximity`, a corridor as a band grown by the proximity each side, a pin (a point) at its reach — reads the reach through its **one home** `markerProximityRangeM`, and keeps the existing `marker.focus.corridorShare` / `zoneShare`. The pin's **zoom-in-only rule is dropped** (a reversal of the 2026-09-18 lock): a pin now zooms out to frame its reach, with `marker.focus.pinFootprintM` kept as its floor. `frameMarker` feeds `AppConfig.markerProximityPinM` / `markerProximityZoneMultiplier`, and the `marker.focus` property comments were restated. `gradlew :app:assembleDebug` SUCCESSFUL with only a pre-existing unrelated warning, and the scoped `ui.map` + `config` run green, `MarkerFocusTest` 8/8.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt` — the extent now body ⊕ proximity; signature drops `currentZoom`, gains the two proximity values
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `frameMarker` feeds the two proximity config values
- `app/src/main/assets/maro.properties` — the `marker.focus` comments restated
- `app/src/test/java/ykws/android/maro/ui/map/MarkerFocusTest.kt` — the collar-growth and pin-reach identities; the two zoom-in-only cases dropped
- `xTrack/Markers/261011_FEAT_PLN_Markers_focus-include-proximity.md` — the plan of record

## Next Step

The device pass on the framed ring — a default circle (reach ≈ 4× radius) and a corridor — is owed. Carried from the 2026-09-28 session, unchanged: the `## Todos` item on giving a tested-but-rejected date point its own marking in the debug overlay. A finding named this session and not changed: `MarkerOverlay` still computes the reach inline at three sites rather than through the one home.
