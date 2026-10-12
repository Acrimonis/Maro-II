# Markers — Focus Zoom Fractions

> **Feature:** Markers | **Branch:** feature/mrkrs
> **Created:** 2026-09-18 | **Status:** Implemented (`#implement` run 1, 2026-09-18)

## Goal

When a marker is selected and its dashboard opens, the camera frames that marker's extent to a
**stated share of the smaller displayed map dimension** — the corridor at 50%, the circle zone at
30% — instead of the single uniform 64 px border fit that makes every zone type land at the same
size. A pin, which has no extent, borrows the default zone's footprint.

## Locked Decisions

- **The share is measured against the smaller of the map's displayed dimensions** in pixels, and the
  geometry is **centred on the map**. No dashboard-band offset, and the fan's camera offset
  (`mapCenterOffsetDp`) is left alone.
- **Corridor = 0.50, circle zone = 0.30, pin = the zone's 0.30** applied to a nominal 200 m
  footprint — the default circle's diameter, whose `radiusM = 100.0` the wizard seeds.
- **The pin only ever zooms in.** A user already closer than the computed target keeps their view; a
  point has no extent to fit, so pulling back would be a regression. Corridor and circle always apply
  their computed zoom — seeing the whole extent is the point.
- **Every select that shows the marker dashboard re-focuses**: the list click, the map tap and the
  dashboard's Prev/Next.
- **`navigateToTarget` stays the single camera owner.** No second effect fits the marker: the inspect
  mechanism reads the camera to decide ownership, so a parallel hop would be misread as the user's
  own move.

## 1. The share rule

With 256 px tiles and world size `S(z) = 256 · 2^z`:

- `Δx = S · Δlon / 360` — longitude is linear.
- `Δy = S · (g(latNorth) − g(latSouth)) / (2π)` where `g(φ) = ln(tan φ + sec φ)`.

The pixel budget is `T = share · min(viewportWidthPx, viewportHeightPx)`, and the target zoom is the
smaller of the two axes, clamped to the map's own range:

```
zLon = log2( T · 360 / (256 · Δlon) )
zLat = log2( T · 2π / (256 · Δg) )
z    = min(zLon, zLat).coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
```

Consequence to accept knowingly: the share is a **pixel budget on the smaller dimension**, not a
share of the axis the shape stretches along — a tall corridor on a portrait screen hits the budget on
its height, which is a smaller share of the screen height than 0.50.

`MAP_MIN_ZOOM = 8.0` and `MAP_MAX_ZOOM = 18.0` get **one home** in the map package, and
`CoastlineMapView`'s `minZoomLevel` / `maxZoomLevel` reads them rather than repeating the literals.

## 2. Values and their home

`app/src/main/assets/maro.properties`, beside the existing `marker.proximity.*` block:

| Key | Value | Meaning |
|---|---|---|
| `marker.focus.corridor_share` | `0.50` | Corridor extent as a share of the smaller displayed dimension |
| `marker.focus.zone_share` | `0.30` | Circle extent, same measure |
| `marker.focus.pin_footprint_m` | `200` | Pin's nominal footprint; comment records that it mirrors the default circle's diameter (`radiusM = 100.0` in the wizard's form state) |

Read through `AppConfig` as read-only properties with clamps — shares `0.1..1.0`, footprint
`20..2000` — following the `markerProximityZoneMultiplier` pattern (declaration + `initialize()`
parse). The map reads them off `AppConfig` directly: they are not `AppSettings` fields, since none of
the three is a user preference.

## 3. `MarkerFocus.kt` (new, `ui/map/`)

Pure Kotlin, **no osmdroid types**, so it unit-tests on the JVM beside `DashboardPositionTest`:

```kotlin
internal data class MarkerFocus(val centre: LatLng, val zoom: Double)

internal fun markerFocusTarget(
    marker: UserMarker,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    currentZoom: Double,
    corridorShare: Double,
    zoneShare: Double,
    pinFootprintM: Double,
): MarkerFocus?
```

- Corridor → its bbox at `corridorShare`; circle → its bbox at `zoneShare`; pin → a 200 m footprint at
  `zoneShare`, with the zoom never below `currentZoom`.
- The pin's footprint **reuses the model's own metre-to-degree conversion**: the existing private
  `UserMarker.computeBbox` body is extracted into an internal `bboxOf(geometry)` that both the lazy
  `bbox` property and this helper call. The footprint is expressed as
  `MarkerGeometry.Circle(position, pinFootprintM / 2)` so no second copy of the maths exists.
- Returns `null` when the viewport is unmeasured (`0` on either axis), and the caller falls back to a
  centre-only `animateTo`. A geometry with no extent is not that null: it imposes no constraint, so it
  takes the map's own ceiling.

## 4. Integration (`MapScreen.kt`, current-branch anchors)

- **Replace step 1 only** of the `navigateToTarget` effect (line 2136): the block at 2140–2154 becomes
  one animated hop built by the helper —
  `mv.controller.animateTo(GeoPoint(centre.lat, centre.lon), focus.zoom, GPS_ANIMATION_DURATION_MS)`.
  Its whereAmI run (2159), settle delay, drawer open with `worldIds` / `source` (2174–2179) and the
  inspect hand-off tail (2188–2195) stay untouched.
- **Every other select — a map tap, a dashboard Prev/Next step —** is framed by one effect in MapScreen
  keyed on the selection and the drawer state, skipping while an inspect hand-off is in flight. Timing
  does not decide whether it fires: the navigate flow records the id it framed in `lastFramedMarkerId`
  and the effect steps aside for that id, while the selection going null clears the record so that
  re-opening the same marker frames it again. This replaced the first cut's `focusOnly`
  `NavigateTarget` plumbing through [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:139)
  and [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:469), which stay
  untouched: the same rule for fewer moving parts, and the bookkeeping is a remembered id rather than a
  race.

## 5. Verification

Unit tests (`app/src/test/java/ykws/android/maro/ui/map/MarkerFocusTest.kt`), asserting
formula-independent properties:

1. Pin's target **equals** that of a `Circle` of radius `pinFootprintM / 2` at the same centre.
2. Doubling a corridor's length lowers the target zoom by exactly `1.0`.
3. Share 0.50 against 0.25 differs by exactly `1.0` zoom.
4. A zero-extent corridor and a huge corridor clamp to 18.0 and 8.0 respectively.
5. An unmeasured viewport returns `null`.
6. A pin at `currentZoom = 17` with a lower computed target returns `17.0`.

Then `apk-build.bat`, plus the scoped `ui.map` + `config` unit run.

Two behaviours to check rather than assume:

- **Inspect bookkeeping** — a zoom-only hop fires `onZoom` → `onCenterChanged`, and the guard at
  [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2231) sets
  `inspectMapMovedByUser` once an open has settled. If the hop is misread as the user's own move, the
  bookkeeping is corrected rather than worked around.
- **GPS auto-follow** — the next fix must not reclaim the framing; if it does, suppress the takeover
  the way the list path does.

## 6. Out of scope, recorded

- The marker card closes without the camera restore the track card routes through
  ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2240) against the marker
  close at [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2287)). Named, not
  changed.
- The track click-n-move fit at [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2204)
  keeps its 64 px border.

## Outcome

Shipped as written, with two deliberate departures recorded in §4 above: the select-driven framing is
one effect guarded by a remembered id rather than a `focusOnly` navigate target, which left
`MarkerDrawer.kt` and `OverlayLayer.kt` untouched, and a geometry with no extent takes the map's zoom
ceiling instead of reporting nothing. `MarkerFocusTest` covers eight properties, all green, and
`apk-build.bat` succeeded; the scoped `ui.map` + `config` run stayed at its five pre-existing
`maro.properties`-versus-code-default reds (`track.width.selected.casing` among them), none of them in
a key this work touched.

Left open by the Ask hop, not fixed here: nothing pins the three `marker.focus.*` keys to their parse,
so a typo in the properties file would ship silently; a filter-driven scope guard that re-points the
selection also moves the camera, which is one move to the marker the card now shows but was not asked
for; and two behaviours only the device can settle — the `inspectMapMovedByUser` flip under a framing
hop, and the GPS auto-follow takeover.
