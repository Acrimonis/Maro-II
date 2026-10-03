<!-- scope: feature -->
# Saved routes — a darker own-colour casing, the same reinforcement the active route wears

**Status:** in design · nothing implemented
**Branch:** `feature/route-rendering` (pending — `#new route-rendering` issued, not yet run)
**Feature:** Route — the saved-route half edits the Tracks render pipeline

## Request

- Encases routes rendering: a border in the route's own colour, darkened — the R93 reinforcement extended from the active line to saved routes.
- Applies even when the route is speed-coloured (the Colours chip bands it): the rim follows the speed colours, darkened.
- Exception: during acquisition only the selected rung is cased.

## Decisions

- **D1 — the banded-mode casing colour.** Resolved by the user: the casing is each speed band's own colour, darkened — the rim follows the ramp segment by segment, never the base pair.
- **D2 — the casing width.** Taken by the agent: a new key `map.track.width.route.casing`, seeded at the active route's 4:3 casing-to-core ratio over the route core (≈ 3.556 dp), floored at the route core so it can never hide under it; device-tuned after.
- **D3 — the selected-route casing.** Taken by the agent: the own-colour casing replaces the black selection casing for routes; selection stays the z-lift and full opacity. Cheaper and matches the request's "one border in the route's own colour".
- **D4 — owner feature.** Taken by the agent: Route — this extends R93; the saved-route half touches Tracks' files but the requirement and the acquisition exception are Route's.

## Current state

- The **active/followed route** already wears exactly this: [`RouteHost.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:163) draws `route_casing` under the selected line, coloured `reinforcedColor(routeLineColor, AppConfig.uiReinforceDarkenPct)` and wide `route.line.casing.widthDp` (8 dp over the 6 dp core). R93 scopes it to the selected rung during acquisition and the followed line while `Following` — the user's exception is already shipped and needs no change.
- **Saved routes** draw through the track pipeline as [`TrackRenderPath.ROUTE`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:636) → [`plainPath`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:906): a bare core with no casing; in Colours mode a route bands and reads like a coloured track. The only casing is the generic **black** selection casing ([`selectedTrackCasing`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:613)), and only while selected.
- `TrackRenderPlan` ([`MapTrackOverlayEffects.kt:639`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:639)) does not carry the route flag: [`trackRenderPlan`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:663) takes `route` but folds it into `path`, so the dispatcher cannot tell a banded route from a banded track.
- The route loop builds its pair through [`computeTrackPolylineAppearance`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:430) at [`MapTrackOverlayEffects.kt:289`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:289), and the banded path quantises the line into [`SpeedBand`](app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt:19) runs via [`bandedAppearances`](app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt:75) — the geometry the casing reuses.
- [`reinforcedColor`](app/src/main/java/ykws/android/maro/ui/color/ColorReinforcement.kt:24) is pure, RGB-only and alpha-preserving, and the caller reads the lever `AppConfig.uiReinforceDarkenPct`.

## Design

One darker own-colour casing under every saved route, in both fill modes:

- Plain route: one casing underlay = the route's own interpolated pair colour darkened, width `map.track.width.route.casing`, alpha preserved from the core.
- Coloured route: one casing underlay per speed band = that band's colour darkened, reusing the same `SpeedBand` geometry through [`buildBandSegmentOverlays`](app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt:69) — no speed re-resolution, no new band computation.
- A selected route keeps the own-colour casing and skips the black selection casing; its cue stays the z-lift and full opacity.
- Pinned routes take the casing too — the pin keeps buying only the escape from the count.

## Steps

- **S0 — the branch.** Create `feature/route-rendering` from `origin/develop` with `--no-track` (the issued `#new route-rendering`; run by the Code pass, not executable from Architect).
- **S1 — carry the role on the plan.** Add `route: Boolean = false` to [`TrackRenderPlan`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:639); [`trackRenderPlan`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:663) sets it `true` on the route branch and `route` (false) on the normal branch. Legend code reads `.path` only, so nothing there moves.
- **S2 — the width key and the rebuild keys.** Add `map.track.width.route.casing` to [`maro.properties`](app/src/main/assets/maro.properties:502) beside `map.track.width.route`, and an [`AppConfig`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:1421) accessor `trackWidthRouteCasingDp` floored at the route core; add both `AppConfig.trackWidthRouteCasingDp` and `AppConfig.uiReinforceDarkenPct` to the effect's rebuild key list beside the other widths ([`MapTrackOverlayEffects.kt:97`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:97)).
- **S3 — the route casing in the dispatcher.** In [`storedTrackRendering`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:862): for a route, build the casing underlay and prepend it before the core; for the plain path capture the core appearance once and derive the casing from it; for the banded path thread a casing spec into [`bandedPath`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:944) so each band's casing is built beside its core.
- **S4 — the selection-casing skip.** Apply [`selectedTrackCasing`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:613) only when `plan.selected && !plan.route`, so a selected route never stacks the black border over its own.
- **S5 — tests.** Pin the plan's `route` field; a plain route and a coloured route each carry a casing before the core; a selected route carries no black casing; the casing colour is `reinforcedColor(core, darkenPct)`; and the width properties test gains the new key.
- **S6 — build and verify.** `apk-build.bat`, the scoped `ui.map` + `config` + `track` suites, and the device pass owed.

## Performance

- A plain route adds **one** casing polyline — negligible.
- A coloured route adds **one casing polyline per speed band**, reusing the already-computed `SpeedBand` geometry: no speed re-resolution, no new band computation, only a second overlay per band, so the banded path's polyline count doubles for those routes alone.
- The route set is capped by `route.renderCount` (default 5, bounded 0–20), so the worst case is a constant-factor increase on a small, already-linear path; the dominant cost stays the track layer's own redraw.

## Risks

- `TrackRenderPath.BANDED` is shared by routes and recorded tracks, so the role must ride the plan and never be inferred from the path.
- The casing must share the route's overlay title so prefix teardown and the z-lift keep catching it.
- The floor on the casing width is what keeps it visible over the core; without it a retuned file could hide the border entirely.
- The per-band casing doubles the banded polylines for coloured routes — accepted, measured on the device pass.

## Verification

- `apk-build.bat` clean; the scoped `ui.map` + `config` + `track` suites green, including the new casing cases and the width properties guard.
- Device pass owed: a saved route beside a recording, a speed-coloured route with its darkened band rim, a selected route with no black border, and the active route unchanged.

## Outcome

Pivoted 2026-10-03: the casing under-stroke was rolled back — the rim read invisible on the device and the direction arrows cut it into dashes. The saved-route display shipped as a **dashed stroke** instead: a `route` role on `TrackRenderPlan`, a `dashed` flag through the segment builders, and the rhythm read from `map.track.width.route.dashOn` / `dashOff` (seeded at the GAP rhythm). See `FEAT_DSC_Route.md` ## Implemented.
