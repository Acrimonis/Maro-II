<!-- scope: feature -->
# Markers filters — the two route axes, and the pinned report

Session opened 2026-09-28 on `feature/misc-ui-n-routing`, from the user's report that the marker
filters misbehave in the map referential. The user then ordered the filter fixed as described and the
dead code removed, and corrected the axis shape once: the two criteria must be settable independently.

## 1. The report, as given

- **Pinned seems inert in the map filter**, with a suspected refresh cause; the other entries are to be
  validated in the same pass.
- **The routing axis is to be reworked**: `Route` — All / cost set / a route role — with a better
  wording, so a marker's two route roles become filterable beside its cost.

## 2. What the reads rule out (static, no device)

- **The pure predicate is complete.** `UserMarker.matchesFilter` reads `icon`, `pinned`, `origin` and
  `routeCost` ([`ListFilter.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:96)),
  and `markerFilterAxes()` publishes those criteria as axes — the cost one and, beside it, the route
  role — pinned among them
  ([`ListFilter.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt)).
- **The map's marker set is the map filter's own.** `_mapMarkers` is fed by one `combine` that calls
  `MarkerSelectionPolicy` — filter-only, no cap, no focus
  ([`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:300),
  [`MapSelectionPolicy.kt`](../../app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt:68)),
  and it is what the overlay draws
  ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2553)).
- **The two referentials do not share a slot.** The menu's marker filter is `markerMapFilter` and the
  list overlay's is `markerListFilter`
  ([`OverlayLayer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:401),
  [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3049)), each with its own
  pref key ([`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:1049)).
- **The axes combine, they do not compete.** Every axis is one key in `axes`, and the predicate ANDs the
  keys together ([`ListFilter.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:97))
  — which is what makes two independent route criteria expressible.
- **The track side already re-filters its pinned escape**, with `trackMapFilter`
  ([`MapTrackOverlayEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:171),
  [`:527`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:527)).

## 3. The pinned report — what remains open

Nothing in the read path explains it, so the defect is unproven either way until one logged pass says
which stage fails. The three suspects:

- **S1 — the write.** The map-referential handler was the only one of the pair with no log line, and now
  has one ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3049)).
- **S2 — the stream.** `_mapMarkers` is a `StateFlow`, so an emission equal to the standing list writes
  nothing; a filter change computing an equal list is invisible.
- **S3 — the overlay pass.** `MarkerOverlay`'s `DisposableEffect` rebuilds on the marker list alone
  ([`MarkerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:175)); the
  OSMdroid sweep is the other half of that pass.

## 4. The two route axes — as ordered

Independence needs **two axes, not two options**: a panel is single-select per axis, so two options in
one axis are mutually exclusive by construction, while two axes are ANDed and each can be cut alone.

- The grouped fallback — All · Routing helper · Not routing helper — is **declined**, and the reason is
  that it is narrower than what was asked: it can never express *cost set and no route role*, and it
  collapses the two roles into one word.
- **Axis 1 — `routeCost`, restored as shipped**: label `filter_axis_route_cost`, options ALL (default)
  first, then `WITH_COST` and `WITHOUT_COST`. The rename built earlier this session is reverted with it,
  and the retired-key prune goes too — `routeCost` is a live key again, and the prune would have stripped
  it.
- **Axis 2 — `routeRole`, new**: label `filter_axis_route_role` (EN `Route role` · FR `Rôle de route`),
  options ALL (default) · `ROLE` (`filter_option_route_role`, EN `Origin or Dest` · FR `Origine ou
  destination`) · `NONE` (`filter_option_no_route_role`, new — EN `No route role` · FR `Sans rôle de
  route`).
- **Predicate**: the `routeCost` branch returns verbatim, and the role axis reads the marker predicate's
  own boolean form, uniform with its four neighbours rather than a `when` of its own:

```kotlin
"routeRole" -> value == "ALL" ||
    (value == "ROLE" && (this.routeOrigin || this.routeDestination)) ||
    (value == "NONE" && !this.routeOrigin && !this.routeDestination)
```

- The cost axis' label and options keep their shipped values, so `filter_axis_route` — created earlier
  this session — is deleted as unused, and no stored filter is invalidated by any of this.
- The two axes combine on one marker, and a test pins exactly that: a marker with a cost and no role
  answers `routeCost=WITH_COST` and `routeRole=NONE` held at once.

## 5. Dead code removed with it

- `allMarkerIds` and its "ghost-pin" KDoc
  ([`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:196)).
- `MarkerOverlay`'s `markerLayerState` and `modifier` parameters, their KDoc lines, the call site's
  argument and any import left unused — the body read neither.
- `drawGeometry`, the local pinned to `true`
  ([`MarkerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:227)).
- The retired-string pair, and the single-axis string, in both locales.

## 6. Verification

- `MarkerFilterMigrationTest`: the `routeCost` cases restored, `routeRole` cases over a marker with one
  role, both roles and neither, and one case holding both axes at once — the independence proof. The
  prune case goes with the prune, and the class KDoc names the axes again.
- One device pass with the `MaroMapRefresh` log, the pinned axis exercised on both referentials; the
  map-referential handler and the map marker set each carry a log line so the pass is decisive.

## 7. Owed after this

- The pinned outcome, once the log names its stage (S1, S2 or S3).
- `#bake`, for the hydration, the Focus History entry and the Markers summary row.

## Outcome

Earlier this session the axis was built as one `Route` axis with `Cost set` and `Origin or Destination`
as mutually exclusive options, and its key renamed from `routeCost`. The user's correction — the two
criteria must be settable independently — made that form wrong rather than incomplete, and it was
withdrawn before leaving the branch: the cost axis returns exactly as it shipped, the role axis is added
beside it, and the rename's retired-key machinery is deleted with the rename. The dead code and the two
`MaroMapRefresh` log lines from the same pass stand.
