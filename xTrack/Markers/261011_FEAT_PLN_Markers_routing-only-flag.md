# FEAT_PLN — Markers · the `routingOnly` flag

**Feature:** Markers · **Date:** 2026-10-11 · **Branch:** `feature/mrkRs` · **Status:** implemented, device pass owed

## What it is

A marker-level boolean, `UserMarker.routingOnly`, meaning **the marker exists only for its routing
weight**: it is a cost the route engine reads and nothing else. A flagged marker never names a track or
a route, and the where-am-i lookup ignores it, so it can never say where the boat is. Route behaviour and
the route acquisition are untouched — the flag only removes the marker from the naming surfaces.

## Decisions

- **Additive field.** `routingOnly: Boolean = false` is the last constructor parameter of `UserMarker`.
  Legacy `user_markers.json` reads `false` (`Json` sets `ignoreUnknownKeys = true`), so there is **no
  migration and no schema bump** — the same shape `routeOrigin` / `routeDestination` shipped with.
- **One exclusion point.** `MarkerMatcher.resolveAllMarkers` filters the incoming list through
  `MarkerMatcher.countsForWhereAmI(marker) = !marker.routingOnly` **before** any match is resolved. That
  one result is what every consumer reads — the where-am-i card, the recorder's naming helpers and
  `whereAmISync`, the service bridge via `WhereAmIProvider`, and `routeEndName`'s zone lookup — so nothing
  in `TrackRecorder.kt` changes.
- **The predicate is pure and internal**, so a JVM test pins the exclusion without a
  `CoastlineSpatialIndex`.
- **Route naming guards by hand.** The zone branch of `routeEndName` inherits the matcher filter; the
  flagged-selection fallback takes the resolved marker only when it is **not** `routingOnly`, so a flagged
  routing-only end keeps its fixed or dated name (`From origin` / `To destination` / the dated name).
  `routeEndPoints`, `routeEndOptions`, eligibility and the acquisition are left as they were.
- **Form and UI.** `CreateFormState.routingOnly` is seeded at every construction site (the two edit seeds,
  the create seed), copied by `updateMarker`'s whitelist, and passed by both save paths. The wizard's
  **Routing cost** section renders the app's one checkbox-and-label row, `OptionRow`, directly under the
  slider, bound to the form field through `updateForm` — independent of any set cost, exactly as the two
  route roles are. The label is `wizard_routing_cost_only` (EN `Only`, FR `Uniquement`), a `@StringRes`
  id in both locales, no literal in the composable.
- **Card mark.** `coordinateHeader` prefixes the card's mark group with `☑` (U+2611) while the marker is
  `routingOnly`, placed between the 🚩 origin mark and the 💲 cost mark, so the flag reads on the card
  immediately left of the dollar — and opens the group on its own when no cost is set.
- **Value line and the wall's word.** In the **Routing cost** section the **Only** row shares the value
 line's own row, left of the value, rather than standing under the slider, and the non-Pin top reads
 `Wall` (FR `Mur`) at both the slider's end and the value line — a Pin keeps `Max`, having no body to
 bar. The string key keeps its name; the model still calls the value blocked.

## Files touched

- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — the field, its KDoc, the class
  `@property` line.
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — the filter in `resolveAllMarkers` and
  the pure `countsForWhereAmI` predicate.
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the `routeEndName` fallback guard and its
  KDoc.
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — the form field, the three seeds, the
  `updateMarker` copy, both save paths.
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt` — the `OptionRow` under
  the slider, the class KDoc, the import.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — the `☑` mark in
  `coordinateHeader` and its display-order KDoc.
- `app/src/test/java/ykws/android/maro/ui/map/MarkerRoutingCostHeaderTest.kt` — the `routingOnly` glyph
  cases (present when set, left of the dollar, and alone without a cost).
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — `wizard_routing_cost_only`.
- `app/src/test/java/ykws/android/maro/spatial/MarkerMatcherRoutingOnlyTest.kt` — the exclusion test.

## Verification

- `gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**.
- The marker, track and matcher suites — green; `MarkerMatcherRoutingOnlyTest` ran 2/2, 0 failures, and
  the card-header glyph cases in `MarkerRoutingCostHeaderTest` are green.

## Owed

- **Device pass** — the flag itself is a pure model/UI change with a JVM test, but the wizard row's
  placement beside the value line and the round-trip through a saved marker are better seen on the device.
  The deployment and the test are the user's to run.
- The Markers feature is not yet tracked in `GLOBAL_CONTEXT.md`; this plan's pointer enters that map at the
  first `#track` / `#bake` for the feature.

## Ordered follow-ons — the plain plan (2026-10-11)

Three requests were discussed and are not yet built. All three read the `routingOnly` field that already
ships through model, form and save; none of them changes route behaviour or where-am-i.

**1. A little space between the box and its word.** In `OptionRow` the box and its label sit as close as
the box's own invisible tap area allows, and the row's own note says that area *is* the gap. The change is
to give the label a small start padding, so the two read apart: the width becomes a `${ui.*}` token in
`ui.properties`, never a literal, and the note is rewritten to state the new gap. Because the row is shared
— the dialogs' option rows, the route panel's pin and the wizard all read it — the gap moves on every
screen at once. Open: how wide, best judged on the device, since the tap area already leaves roughly 14 dp
of air and a wider gap may read loose at the dialogs' 14 sp label.

**2. A routing-only marker looks different on the map.** The decision belongs to `MarkerAppearance.of`,
which already resolves a marker's colour, stroke width and halo, with `MarkerOverlay` painting it — so a
routing-only variant extends one decision rather than adding drawing code elsewhere. A dashed ring is cheap:
`DashPathEffect` is already the map's vocabulary for the low-confidence isobath, a saved route and the live
track's gap. Open: **a Pin has no ring to dash**, its "zone" being a proximity cone drawn only in where-am-i
debug, so what a routing-only Pin shows must be decided. Open: the hatch — Android's `Paint` has no hatch,
so a hatched fill means a tiled `BitmapShader` or a shader-drawn overlay, the expensive half, and it must be
checked against both draw paths or the marker looks different by zoom and by state. Care: a dash already
means something else here, and ring plus hatch on a small zone at low zoom may read as noise.

**3. A marker filter: All / Route Only / Normal.** The marker filter is already a map of axes ANDed by
`UserMarker.matchesFilter` and drawn from `markerFilterAxes()`, so this is the shipped `routeRole` axis
verbatim — key `routingOnly`, options ALL · ONLY · NORMAL, labels in both locales, and a migration-test
case. Both the list and the map get it for free, each already holding its own filter with its own pref key
and the `markerFilterLinked` link between them. Open: the wording — the panel would then sit beside
`Routing cost` and `Route role`, so a third route word must read distinctly, and "Normal" is the ambiguous
half of its own pair.

**Order.** The three are independent. The gap is the cheapest and touches no data; the map cue is the
dearest, because of the hatch; the filter is the middle, and its shape is already proven by the axis it
mirrors. Verification for each: `gradlew :app:assembleDebug` plus the marker, track and matcher suites, and
a device pass for anything the eye must judge.

## Outcome — delivered 2026-10-11 on `feature/mrkRs`

All three follow-ons shipped, with one amendment the user gave after the plan above was written and which
**supersedes its "ring first, hatch only if it earns its keep" line: both cues are required** — every ring
dashed and the fill visibly hatched.

- **The gap** — `ui.spacing.checkbox.label` (4 dp) added to `ui.properties`, read through
  `AppConfig.uiSpacingCheckboxLabel` and laid on `OptionRow`'s label, whose KDoc now states that one token
  as the row's own gap rather than the box's inset.
- **The map cue** — one flag, `MarkerAppearance.isRoutingOnly`, read by both painters. Every ring a
  routing-only marker draws dashes: the Pin's and the Circle's proximity strokes, the Circle's zone outline
  and its selected under-stroke, the corridor band's parallels and caps, and the halo's border ring — the
  last needing a `dashed` flag on `MarkerHaloSpec` and the marker's dash rhythm widened from `private` to
  `internal` so the halo reads the same hand. Every fill hatches: `hatchShader` tiles a 45° line bitmap and
  hangs it on the five fill paints — the three proximity fills, the Circle zone fill and the corridor band
  fill. The corridor's always-on centre line stays solid: it is the marker's position, not a ring, and a
  Pin's proximity ring is the second ring the request named.
- **The filter** — the `routingOnly` axis after `routeRole`: ALL (default) · ONLY · NORMAL, the predicate
  pure in `UserMarker.matchesFilter`, labels `filter_axis_routing_only` / `filter_option_routing_only` /
  `filter_option_normal` in both locales (Routage uniquement · Routage seul · Normal), and four cases added
  to `MarkerFilterMigrationTest`.

Verification: `:app:assembleDebug` SUCCESSFUL, the marker, track and matcher suites green
(`MarkerFilterMigrationTest` 22/22), and `apk-build.bat` SUCCESSFUL.

### Review findings, carried as owed

- The corridor's proximity parallels and caps were **already** dashed for every marker, so there the dash is
  not a routing-only cue; making it conditional would un-dash an ordinary corridor's preview, a visible
  change outside this order — the user's ruling, not a quiet fix.
- The two builders' new parameter is named `dashed` while it also hatches the fill, so the name
  under-describes it.
- Nothing pins `MarkerAppearance.of`: neither `isRoutingOnly` nor the halo's `dashed` flag is asserted,
  though the class is pure and JVM-testable.
- The hatch's contrast is bounded by the fill's own alpha — 20% for a zone, 10% for a proximity ring — so a
  brighter stripe is the lever if the device pass reads it as too faint, not a different mechanism.
- Whether OSMdroid's `Polygon` honours a paint shader is a rendering fact only the device pass settles; and
  `MarkerOverlay` is ~950 lines with its eleven-argument `of(...)` call duplicated in two loops.

## Zone and proximity paint moved to maro.properties (2026-10-11, same branch)

The four paint opacities and the two stripe figures left `MarkerOverlay`'s constants for the file that owns
values, carrying the user's chosen numbers:

| Key | Value | What it paints |
|-----|-------|----------------|
| `marker.zone.outlineAlpha` | `1.0` | the zone's outline — the Circle's ring, the corridor band's parallels and caps |
| `marker.zone.fillAlpha` | `0.25` | the zone's fill — the Circle's zone polygon, the corridor band's polygon |
| `marker.proximity.outlineAlpha` | `1.0` | the proximity ring's outline |
| `marker.proximity.fillAlpha` | `0.125` | the proximity ring's fill, and the ink the routing-only stripes take |
| `marker.routingOnly.stripeSpacingDp` | `10` | the spacing between the routing-only fill's stripes |
| `marker.routingOnly.stripeWidthDp` | `5` | one stripe's width |

**Naming, normalized 2026-10-11 in the same session.** Every key names the art it paints: `outlineAlpha`
for a ring's outline (never `strokeAlpha`), `fillAlpha` for an area's fill, `stripeSpacingDp` and
`stripeWidthDp` for a fill's stripes. The values above are the ones the user settled on in that pass —
the shipped defaults remain 1.0/0.25, 0.75/0.175 and 6/1.

`AppConfig` carries them as `markerZoneOutlineAlpha`, `markerZoneFillAlpha`, `markerProximityOutlineAlpha`,
`markerProximityFillAlpha`, `markerRoutingOnlyStripeSpacingDp` and `markerRoutingOnlyStripeWidthDp` — the
alphas coerced to `0.0-1.0`, the stripe pair floored at 2 dp and 0.5 dp so a zero cannot blank the texture.
The four constants `ZONE_FILL_ALPHA_FRACTION`, `PROXIMITY_ALPHA_FRACTION`, `HATCH_PERIOD_DP` and
`HATCH_STROKE_DP` are **deleted** rather than kept beside the file, per one-home-per-fact, and the zone's
outline takes its own opacity through `dimColor` at the two outline call sites while the centre dot, the
icon and the always-on corridor centre line keep the marker's full colour.

The change is **global**, the user's own ruling: every marker's zone and proximity ring move from 1.00/0.20
and 0.50/0.10 to 1.00/0.25 and 0.75/0.175, and the stripes remain the routing-only cue — the routing-only
markers take no separate fill. `DIMMED_ALPHA_FRACTION` still multiplies on top of each.

`gradlew :app:assembleDebug :app:testDebugUnitTest --tests "*Marker*" --tests "*Track*" --tests "*Matcher*"`
SUCCESSFUL with no failures.
