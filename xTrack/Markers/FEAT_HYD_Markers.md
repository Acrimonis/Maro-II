# Context Hydration — Markers — 2026-10-11

**Last Bake:** 2026-10-11 18:44 UTC — written by `#bake` on `feature/mrkRs`; the same day's earlier bake (13:17, on `feature/mkrEl`) is folded in below rather than lost, and the line's absence means never baked.

**Directive trace:** All five covered action classes were met by both sessions and none stopped either — no dependency was added, no machine-shaped data file was opened, each branch was cut only on the user's own `#new`, every implementation ran on an explicit order (the direct instructions and the `#implement` pipeline, each hop through a mode hand-off), no device was touched by the agent (the user rebuilt, deployed and reported; no log was fetched), and every claim written about the code carries a file read behind it. One boundary was named rather than crossed: the `strokeAlpha` hits in `ui/icons/*.kt` are the generated Material icons' own API and were deliberately left alone.

## State

Two sessions landed on 2026-10-11, each cut from `origin/develop` by the user's `#new` — `feature/mkrEl` (the focus framing) and `feature/mrkRs` (this one) — and this file is their union after `feature/mrkRs` was rebased onto `origin/develop`, the one conflict in shared code being `routeEndName`.

**The frame includes the reach (2026-10-11, `feature/mkrEl`).** A marker's dashboard open now frames the marker's **body plus the proximity collar the map draws outward from it**, so the whole proximity zone lands on screen; before, the fit framed only the body and the ring spilled past the edge. `markerFocusTarget` builds the extent per geometry — a circle at `radius + proximity`, a corridor as a band grown by the proximity each side, a pin at its reach — reads the reach through its one home `markerProximityRangeM`, and keeps `marker.focus.corridorShare` / `zoneShare`. The pin's **zoom-in-only rule is dropped** (a reversal of the 2026-09-18 lock), with `marker.focus.pinFootprintM` kept as its floor. `MarkerFocusTest` 8/8.

**The routing-weight-only flag (2026-10-11, `feature/mrkRs`).** A marker may exist for its routing weight alone: `UserMarker.routingOnly` — the last constructor parameter, KDoc'd and additive, so a legacy `user_markers.json` reads `false` with no migration — excluded from naming and where-am-i by the one pure predicate `MarkerMatcher.countsForWhereAmI`, applied in `resolveAllMarkers` before any match is resolved: the single producer every consumer reads, so `TrackRecorder` was untouched. Route behaviour and the route acquisition are unchanged.

**The wizard and the card.** The Routing cost step carries the shared `OptionRow` on the value line's own row, bound through `updateForm`; the form field is seeded at all three construction sites, copied by `updateMarker` and passed by both save paths; `wizard_routing_cost_only` (Only / Uniquement) was added and the non-Pin top revalued to **Wall / Mur**; and the card shows a ☑ left of the 💲 through `coordinateHeader`, between the 🚩 origin mark and the cost mark.

**The map cue.** One flag, `MarkerAppearance.isRoutingOnly`, read by both painters: every ring dashes — the Pin's and the Circle's proximity strokes, the Circle's zone outline and its selected under-stroke, the corridor band's parallels and caps, and the halo's border ring through a new `MarkerHaloSpec.dashed` reading the marker's one dash rhythm, widened from `private` to `internal` — and every fill hatches through the `BitmapShader` that `hatchShader` tiles, applied at all five fill paints.

**The filter.** The marker filter gained the `routingOnly` axis after `routeRole`: ALL (default) · ONLY · NORMAL, its predicate pure in `UserMarker.matchesFilter`, labels in both locales, and four cases added to `MarkerFilterMigrationTest`, now 22/22. Both referentials inherited it, the list and the map each holding its own filter.

**The paint keys.** Six values left `MarkerOverlay`'s constants for `maro.properties` — `marker.zone.outlineAlpha` / `marker.zone.fillAlpha`, `marker.proximity.outlineAlpha` / `marker.proximity.fillAlpha`, `marker.routingOnly.stripeSpacingDp` / `marker.routingOnly.stripeWidthDp` — read through `AppConfig` with the shipped figures as the code fallbacks. Their names were then normalized so each states the art it paints: `outline`, never `stroke`; `fill`; `stripe`. The four deleted constants (`ZONE_FILL_ALPHA_FRACTION`, `PROXIMITY_ALPHA_FRACTION`, `HATCH_PERIOD_DP`, `HATCH_STROKE_DP`) are gone rather than kept beside the file.

**The route-end naming narrowed from both sides.** `routeEndName` now refuses an end twice over, merged rather than picked in the rebase: develop's rule leaves it unnamed when the end is the boat's own home port, told by the Place or by the name itself, and the routing-only guard leaves it unnamed when the flagged marker is a routing-weight-only one — while the zone branch inherits the matcher's own exclusion.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt` — the extent now body ⊕ proximity; the signature drops `currentZoom` and gains the two proximity values
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `frameMarker` feeding the two proximity values, and `routeEndName` carrying both naming refusals
- `app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt` — `namesHomePort` and the origin-led name chain
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — `routingOnly` and its KDoc
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — the exclusion in `resolveAllMarkers` and the pure `countsForWhereAmI`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — the six paint reads, the dash and hatch wiring, `hatchShader`, the dash rhythm widened to `internal`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt` · `MarkerHalo.kt` — `isRoutingOnly` and the halo spec's `dashed` flag
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — the ☑ mark in `coordinateHeader`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — the form field, its three seeds, the update copy and both save paths
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt` — the Only row on the value line
- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the `routingOnly` axis and its predicate arm
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — the six keys, their fallbacks and the restated `marker.focus` comments
- `app/src/main/res/values/strings.xml` · `app/src/main/res/values-fr/strings.xml` — `wizard_routing_cost_only`, the Wall / Mur revalue, the axis labels
- `app/src/test/java/ykws/android/maro/spatial/MarkerMatcherRoutingOnlyTest.kt` · `data/model/MarkerFilterMigrationTest.kt` · `ui/map/MarkerRoutingCostHeaderTest.kt` · `ui/map/MarkerFocusTest.kt` · `data/track/TrackNamingTest.kt` · `ui/map/RouteExitDoorsTest.kt` — the cases both sessions added
- `xTrack/Markers/261011_FEAT_PLN_Markers_routing-only-flag.md` · `xTrack/Markers/261011_FEAT_PLN_Markers_focus-include-proximity.md` — the two plans of record

## Next Step

Nothing is open in the code; `:app:assembleDebug`, the marker, track and matcher suites and `apk-build.bat` are all green. **Device passes are owed on both changes and are the user's**: the framed ring — a default circle (reach ≈ 4× radius) and a corridor — and, from this session, the wizard row's new placement, the card's ☑, and whether the hatch and the dash read at all against the tuned fill alpha.

Four findings from this session's review stay unwritten as work: the corridor's proximity dash is unconditional for every marker, so it carries no routing-only meaning there; the two builders' parameter is still named `dashed` while it also hatches the fill; nothing pins `MarkerAppearance.of`; and the ring's dash pen and rhythm — and the halo's own rhythm, shared with it — have no property. One finding still stands from `feature/mkrEl`: `MarkerOverlay` computes the reach inline at three sites rather than through the one home. The `## Todos` item about a rejected date point's marking on the debug overlay is untouched and still open.
