# FEAT_PLN — Route — the marker cost as a well

> Continues [`261010_FEAT_PLN_Route_marker-cost-integration.md`](261010_FEAT_PLN_Route_marker-cost-integration.md:1),
> which shipped the costed marker as a distance-free plateau. This pass replaces that plateau with a
> **body plus proximity collar** and re-admits a costed **Pin**.

## What was wrong

- **The plateau had no gradient.** `markerPriceSec` charged every cell inside the mark the same
  `base × step × strength`, so a line either paid the whole crossing or skipped the mark — the binary
  "go around it" the user reported, with no partial offset ever available.
- **The strength and the switch were not key material.** On `selective` the fine tile is cached under
  [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:28), which carried the
  markers but neither `markerStrength` nor `route.marker.enabled`; both are read per cell by the
  rasteriser and live nowhere on a marker, so a slider or switch move served stale prices from cache.
- **A Pin could not be costed.** The wizard hid the cost side for a Pin and the save nulled it, and the
  projection dropped a Pin outright — a point had "no area to price".

## The law

A mark is a **flat core** (the marker's own body, priced at the shipped plateau) plus a **collar** from
the body's edge out to the marker's own **proximity**, decaying linearly to zero. A Pin is a body of
radius `0`, so its whole price is a cone from its centre. The collar is the proximity the map already
draws and the where-am-I match already gates on — one reach per marker, no second control.

Both prices are multiples of the same base, the cell's open-water time — a speed zone pays
`base × (pace/limit − 1) × λ`, a marker `base × step × strength × factor` — so at the shipped 25 kn pace:

| Source | λ 5 (around) | λ 2.5 (best) | λ 0 (fast) |
|---|---|---|---|
| 10 kn zone | 7.5 × base | 3.75 × base | 0 |
| 5 kn zone or band | 20 × base | 10 × base | 0 |
| marker step 5, at the core | 5 × base | 5 × base | 5 × base |
| marker step 9, at the core | 9 × base | 9 × base | 9 × base |

**The marker is λ-free** while zones and the band scale with λ, so a step-5 marker already outranks a
10 kn zone at the best rung and prices alone at the fast rung where every zone is free. That asymmetry,
not the λ 5 case, is what reads as "too strong".

## Decisions (the user's)

- The well **reuses the marker's own proximity**; no new field and no new control, and the coupling to the
  where-am-I gate is accepted and noted in the rule's one home.
- **A Pin is never a wall**, so its cost tops out at the top **price** and a stored `10` on a Pin is read
  back at that cap.
- **The route stays arm-only**: no automatic re-run on a cost, proximity, strength or switch edit.

## Implemented

- [`RouteMarker.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:1) — the
  well's own law: `contains` is the body alone (the wall's own area), `wellFactor` is the core-to-collar
  fall-off in one home `collarFactor`, and `boundaryDistanceM` measures to the **outer** priced edge so
  the pull's group proof stays sound beyond the well.
- [`MultipassWorld.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:252)
  — a Pin projects as a zero-radius body, its cost capped at the top price and never a wall.
- [`UserMarker.kt`](../../app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:243) —
  `markerProximityRangeM` is the per-type proximity fallback's **one home**, now read by
  [`UserMarkerRepository.proximityRangeM`](../../app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt:116)
  and by the projection alike, so a legacy marker with no stored override still projects.
- [`TileKey.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:96) — the two
  omissions closed: `markerStrength` and `markerPriceEnabled` are key material, threaded through
  [`RouteGridBuilder.fineTileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:507).
- [`RoutingCostStep.kt`](../../app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt:66)
  — both sections for every type; a Pin's track ends at the top price under the new
  `wizard_routing_cost_top` label, present in both locales.
- [`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:997) —
  `formCostForSave` is the save's one rule: an auto marker carries none, a Pin keeps a capped price.
- Tests: `RouteMarkerCostTest` rewritten for the well (core, collar, boundary, the Pin's cone, and the
  wall's body-only block); `TileKeyTest` gained the two flips, a moved-ring case and a by-name guard for
  the two values a field-count cannot see; `FineTileMapTest` follows the new constructor.
- Evidence: `gradlew testDebugUnitTest` → **1118 passed, 11 skipped, 2 failed**, the two failures being
  [`MapPulseDotTest`](../../app/src/test/java/ykws/android/maro/ui/map/MapPulseDotTest.kt:48), which
  expects `ui.map.pulse.dot.floor=0.33` / `.ms=555` while the shipped
  [`colors.properties`](../../app/src/main/assets/colors.properties:99) carries `0.25` / `666` — a stale
  test on the branch, with that file unmodified in `git status`, so **not** this change's. `gradlew
  assembleDebug` → BUILD SUCCESSFUL.
- Omitted deliberately: no `## Implemented` pointer was written into
  [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md:1) and no hydration touched — those are `#bake`'s, on the
  user's word.
