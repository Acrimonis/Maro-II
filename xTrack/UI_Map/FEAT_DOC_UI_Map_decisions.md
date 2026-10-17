<!-- scope: feature -->
# UI_Map — decisions

> The map's shipped decisions, grouped by subject. Each entry states the call, why it was taken and
> where it lives; the plan or doc that carries the full reasoning is named beside it. Refreshed by
> `#doc update` on 2026-10-17 from the feature's `## Implemented` record and the plans' own
> `## Outcome` / `## Decisions` sections.

## Rendering and layer order

- **One deterministic overlay order.** Tiles, base, tracks and markers are reordered through the single
  `OverlayZOrder.reorder(mv)` entry point ([`OverlayZOrder.kt:32`](app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:32)),
  so no layer's attachment order can decide what covers what. → `260903_FEAT_PLN_UI_Map_z-order-fix.md`
- **Refresh is per layer.** Each overlay's mutations sit in its own `LaunchedEffect` block, so a fan
  toggle or a marker CRUD rebuilds that layer alone and cannot blank the others. → `260704_FEAT_PLN_UI_Map_map-refresh-troubleshooting.md`
- **Every paint length is dp, converted at the caller.** The renderer keeps its `…Px` parameters and
  the caller converts through one pure `dpToPx(dp, density)` ([`MapOverlayRenderer.kt:70`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:70)),
  because a width tuned on one phone must scale with the screen rather than stay fixed in pixels.
  → `260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`
- **The map never pads for chrome.** The MapView draws full-bleed; the control columns and the bottom
  band float above it, and the map's own inset is the one it is given (a bottom band in portrait, a
  start edge in landscape). → `260616_FEAT_PLN_UI_Map_overlay-layout-rationalization.md`

## Layer visibility

- **The layer fan is the master gate for routes and tracks.** Child 1 writes `routeTracksVisible`, the
  two header eyes stay the kinds' own, and the render reads the pair through one pure rule,
  `kindLayerOn(master, eye)` ([`MapTrackOverlayEffects.kt:542`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:542)).
  → `261010_FEAT_PLN_UI_Map_route-tracks-master-gate.md`, decision D1.
- **The open dashboard's item is the gates' one exception.** `storedTrackSelection`
  ([`MapTrackOverlayEffects.kt:573`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:573))
  answers the open card's item alone while its half is gated off, because the info on screen must have
  its item on the map (D2). The **filter is never escaped** — the 2026-09-28 rule stands, the two
  governing different things (D3). → same plan, decisions D2–D4.
- **A dimmed eye still writes.** `KindVisibilityToggle(dimmed = …)` draws the inactive alpha while
  staying enabled, so a tap on a dimmed eye reaches its own flag rather than being swallowed. → same plan.
- **The route-and-tracks child keeps its glyph.** Renaming it for its wider meaning was a design change
  nobody asked for and is named as such in the plan. → same plan, decision D5.

## Toggle family and colour

- **Every square resolves through one pure home.** `TopToggleFace` (fill, optional dot) with one resolver
  per square — `gpsFace`, `trackingFace`, `earthWaterFace`, `inspectFace`, `routeFace`, `lockFace`,
  `recenterFace` — reading `AppConfig` only, so a face cannot be spelled twice. → `261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md`, Outcome.
- **The surface paints a white base under the state colour**, at `ui.map.surface.active.alpha`, and
  `MapPulseDot` takes its colour as a parameter with the mark's 33 % floor while `rememberPulseAlpha`
  keeps its 30 % default for the non-toggle caller. → same Outcome.
- **The route square no longer reads the user's line colour**: `RouteToggleButton`'s `lineColor`
  parameter and its call site were retired. → same Outcome.
- **Readerless palette keys are deleted, not kept in sync.** Six keys went after each was verified to
  carry no property, parser line or reader, and the GPS / tracking keys were re-pointed to the states
  they carry. → same Outcome.
- **The routing square's loading dot reports the fine-tile store alone** — the white mark at half the
  regular beat, cleared after a 400 ms grace, so one arming reads as one steady window rather than a
  flicker tile to tile; the regulated-zone asset load never moves it, that asset shipping in the APK.

## Bottom band and the tag column

- **The band's bottom edge is the band gutter in both orientations.** The bottom zone takes
  `padding(bottom = ui.map.toggle.gutter)` and no navigation-bar inset, so the bottom-left tag column
  bottoms out on the same line as the zoom stack; the user's device pass on 2026-10-17 read the two on
  one line. The banner family's placement rule is written once in `docs/ui-component-guidelines.md` §5.7.
- **Banner clearance is one function of the tag stack.** Every banner starts at
  `bannerStartInset(tagsDrawn)` and ends clear of the right control column (82 dp), with `tagsDrawn`
  the tag stack's own `regulatedZoneTags(...).isNotEmpty()`; the family's control, skin and faces are
  single-sourced in `docs/ui-component-guidelines.md` §5.7.
- **The tag stack orders by restriction, not by date.** `CATEGORY_PRIORITY`
  ([`RegulatedZoneComponents.kt:46`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:46))
  paints the most restrictive tag at the bottom, and the set is derived for the marker rather than the
  boat — so a dragged map shows what the user is looking at — and is never gated by the layer's
  visibility. → `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md`.

## Selection, inspect and the dash family

- **Inspect mode is an entry point, not a second renderer.** A pick writes the canonical selection, so
  the camera, the look and the cards are the same surface an ordinary tap reaches. → `260917_FEAT_PLN_UI_Map_inspect-mode.md`
- **Live acquire replaced lift-and-dwell.** While armed the nearest inspectable item is re-ranked on
  every sweep and its dashboard opens at once; the pause's expiry recentres and freezes the ladder, and
  a drag after that recentre resets the card, the ladder and the captured frame. The three rules are
  pure (`inspectAcquireAction` and its siblings, [`InspectMode.kt:312`](app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:312)).
  → `261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- **One frame for the whole dash family.** The marker card, the track card and the route panel share a
  12 dp header, a scrollable body, the body-only dissolve, square corners in both orientations and one
  8/8/8 walk row; the footer's pills are the route footer's own tier-1 `ConfirmActionButton` pair, which
  is also what stops a cross-type swap lifting the row. A swap is damped by `map.inspect.switchMarginPct`
  and the incoming card is pre-sized at the outgoing one's height. → same plan, findings of §8.

## Map offset and orientation

- **De-centring is a dual offset.** The boat is shifted by a geo `setCenter` correction **and** by the
  screen-space `CenterMarkerOverlay` shift, so the marker, the arrows and the overlays keep their own
  geometry while the water ahead grows; the keys are `map.offset.lookahead.maxspeedKn`,
  `map.offset.lookahead.boatFromBottomPct`, `map.offset.gps` and `map.offset.demo`, and the offset is
  suppressed while stationary, while panned and wherever the mode's own gate says so.
  → `260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md`, `260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md`
- **Demo heading-up is a map rotation, never a nav heading.** The two-finger gesture writes a bearing
  that rotates `mapOrientation` ([`MapGpsFollowEffects.kt:55`](app/src/main/java/ykws/android/maro/ui/map/MapGpsFollowEffects.kt:55)),
  `demoBearingDeg` stays unset, the auto-show cone keeps reading the nav heading, and the cap arrow
  always draws straight up. Default is north-up, `demoHeadingUp` off
  ([`SettingsManager.kt:250`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:250)).
- **Marker size is a curve with tunable ends.** The growth exponent is `map.marker.size.zoomExponent`
  (0.35 as settled), the base pair is `map.marker.size.boatBaseDp` / `.dotBaseDp`, and the sprite, the
  wizard's crosshair and the cap arrow read the same settings; the reference zoom and the coast-shrink
  pair stay code. → `260919_FEAT_PLN_UI_Map_marker-zoom-scale.md`, `FEAT_DOC_UI_Map_marker-sizing.md`

## Chrome and drawer framework

- **Two layers, one rule each.** Layer 0 (map, dashboard, controls, status icons, tag strip) is never
  conditional; Layer 1 (the transient surfaces) is a single `OverlayLayer` call whose surfaces are pure
  content inside a `DrawerSlot` — no `AnimatedVisibility`, no scrim, no shadow of their own.
  → `docs/ui-drawer-guidelines.md`
- **The map's overlay row is two columns.** The left column fills (top toggle row, then the bottom band)
  and the right column is content-sized (settings, the two fans, zoom); the insets live in the column
  that owns the edge rather than on the whole screen. → `260616_FEAT_PLN_UI_Map_overlay-layout-rationalization.md`
- **The monolith's remedy is four named phases.** Bodies, Builders, Chrome and Close took the callback
  bodies, the data constructions, the eight chrome values into an `@Stable MapScreenChrome` with a
  `Saver` serialising `selectedTab` alone, and the anchors' re-measure — with zero behaviour change.
  → `260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md`
