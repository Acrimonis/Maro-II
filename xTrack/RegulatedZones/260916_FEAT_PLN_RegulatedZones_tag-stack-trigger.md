# FEAT_PLN — RegulatedZones — Bottom-left tag stack trigger

**Feature:** RegulatedZones · **Branch:** feature/tracking-more · **Date:** 2026-09-16 · **Status:** implemented — device pass pending

## 1. Report

A zone's tag in the bottom-left stack appears only while the regulated-zone layer is drawn, with one exception — the 300 m band tag — which appears whether or not its layer is drawn.

## 2. Evidence — one value feeds both the layer and the tags

| Site | Code | Effect |
|---|---|---|
| `MapScreen.kt:2312` | `showRegZones = appSettings.regulatedZonesVisible \|\| regulatedZoneOverlayVisible` | the layer gate |
| `MapScreen.kt:2313-2330` | `visibleRegulatedZones = if (showRegZones) … else null` | `null` whenever the layer is off |
| `MapScreen.kt:2340` | `CoastlineMapView(regulatedZones = visibleRegulatedZones …)` | polygon drawing |
| `MapScreen.kt:2454` | `RegulatedZoneWarningStrip(regulatedZones = visibleRegulatedZones …)` | **the tags** |
| `MapScreen.kt:2461` | `RegulatedZoneInfoText(regulatedZones = visibleRegulatedZones …)` | info lines beside the tags |
| `MapScreen.kt:2456, 2463` | `inZone300 = inZone300` | from `NavigationViewModel.inZone300` — no layer dependency |
| `MapScreen.kt:1236, 1817` | `boatPosition = gpsPosition ?: mapCenter` | the old reference point, removed by this plan (§5.6) |

`inZone300` is set from the band geometry at `NavigationViewModel.kt:600` into the state flow at `:215-217`, and its injection into the strip happens at `RegulatedZoneComponents.kt:104` — that is why the 300 m tag survives the layer toggle.

So the tags and the polygons are two consumers of one value: `visibleRegulatedZones` carries both "what to draw on the map" and "what to tag".

## 3. What is already correct

- Containment — "the marker is within the zone" — is already implemented: the strip keeps only zones where `it.contains(boatPosition)` (`RegulatedZoneComponents.kt:77`), and the point handed to it is a non-null `LatLng` on the MapScreen paths.
- The settings gate is already the intended trigger: `filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }` (`MapScreen.kt:2314` → `RegulatedZoneComponents.kt:478-492`), fed by the nine toggles in Zone categories (`MapScreenSettingsOverlay.kt:612-619` → `CategoryToggleGroup`, `RegulatedZoneComponents.kt:311`).
- The 300 m suppression of regulated speed tags while the band is in force (`RegulatedZoneComponents.kt:96, 239`) is a documented rule and stays.

## 4. Root cause

No single defect in the tag builder — the defect is in the wiring. The layer's visibility gate is applied upstream of the strip, so a zone that is enabled in Zone categories and contains the marker still produces no tag when the layer is hidden. The 300 m band is the proof: it is the one tag fed by an independent source, and the one tag that behaves as required.

## 5. Fix design

### 5.1 One new derived value in `MapContent`, layer-independent

Add beside the existing block (`MapScreen.kt:2311-2330`):

```kotlin
// Tags are gated by the Zone categories settings alone — never by the layer's visibility.
val tagRegulatedZones = remember(regulatedZones, appSettings) {
    filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
}
```

`visibleRegulatedZones` keeps its current shape and keeps feeding `CoastlineMapView` only.

### 5.2 Rewire the two tag consumers

`RegulatedZoneWarningStrip` (`MapScreen.kt:2454`) and `RegulatedZoneInfoText` (`MapScreen.kt:2461`) receive `tagRegulatedZones` instead of `visibleRegulatedZones`, and the marker point of §5.6 instead of the old `boatPosition` argument. Nothing else changes — containment, dedupe by `(displayCategory, speedLimitKn)`, priority ordering and the 300 m injection all stay inside the composables.

### 5.3 Remember keys

The `remember(regulatedZones, appSettings)` on the new value is load-bearing: the strip's own `remember(regulatedZones, boatPosition, inZone300)` (`RegulatedZoneComponents.kt:74`) would invalidate on every recomposition if each frame produced a fresh `RegulatedZoneSet`.

### 5.4 Decision D1 — RESOLVED 2026-09-16: the 300 m tag stays unconditional

The band tag is injected on `inZone300` alone (`RegulatedZoneComponents.kt:104`, `:247`) and must keep ignoring the Zone categories toggles — user decision: "I still want the 300m either or not the speed limit zones is active or not". So `RegulatedZoneComponents.kt` is **not edited** by this plan, and the two tag rules stay deliberately different:

| Tag | Shows when |
|---|---|
| Regulated zone | marker inside it ∧ its display category is enabled in Zone categories |
| 300 m band | the marker is inside the band — no category or layer condition |

Recorded as a rule in the feature file so a later session does not "unify" the band tag into the category gate.

### 5.5 Preserved behaviours

- The map overlay is still layer-gated: `CoastlineMapView` keeps `visibleRegulatedZones`, and auto-reveal keeps narrowing the polygons only.
- `regulationInfoVisible` (default false) still gates the info text panel.

### 5.6 The tag reference point — RESOLVED 2026-09-16, Option B

The rule: the stack tests the point under the marker, one value for every state. That point is the boat while the map follows it and the panned view while the user moves the map, so both cases the user asked for are covered without a branch.

| State | What the point is |
|---|---|
| GPS mode, following | the live fix — the boat |
| GPS mode, map moved by the user | the panned view, i.e. the marker |
| Demo, or no fix yet | the map centre — the only point in play |

Why the marker is not the geometric screen centre, and why it is nevertheless the right value:

- The screen shift: the map is drawn with osmdroid's centre offset — `setMapCenterOffset(0, centerOffsetYPx)` (`CoastlineMapView.kt:213, 365`) — and the same dp value shifts the boat marker, the cap arrow and the direction line (`MapScreen.kt:2375, 2384, 2394`), so the boat sits below the screen centre by up to 17 % of the visible map height at the defaults (`mapOffsetBoatFromBottomPct = 33`, `SettingsManager.kt:316`; full offset at `map.offset.lookahead.maxspeedKn=20`, `maro.properties:237`; the demo offset is off by default).
- The camera lead moves the drawing, not the value: the lead (`minOf(speed × elapsed, 30 m)` above ~3 kn, `NavigationViewModel.kt:838`) drives the MapView camera and the depth view model (`MapGpsFollowEffects.kt:86-94, 98`), while `_mapCenter` is written from the fix itself, beside `_gpsPosition` (`NavigationViewModel.kt:731-733`) — so the centre the composer receives is the boat, not a point ahead of it.
- The marker is the logical centre, not the geometric screen centre: a pan reports `mapView.mapCenter` (`CoastlineMapView.kt:249`) into `updateMapCenter` (`MapScreen.kt:980`), and the marker overlay is drawn at the same offset the map is drawn with — which is why the marker is exactly "what I am looking at".
- The value behind the marker has two writers: the fix handler on every fix (`NavigationViewModel.kt:733`, whose comment calls it "the correct GPS position on each fix") and the pan callback, accepted only while auto-follow is suppressed or in demo mode (`MapScreen.kt:980`). That is what makes it the boat while driving and the panned view while looking around.
- Why this rather than testing `gpsPosition` explicitly: the two agree in every state and differ only by `uiMapCenter`'s 333 ms sampling (`MapScreen.kt:443`, `NavigationViewModel.kt:187`) — about half a metre at 5 kn — so the explicit variant buys intent alone, at the price of a second source of truth for one position and a new parameter.
- What this option does not encode is recorded here instead: if the map centre ever stops tracking the boat while following, the tags lose the boat with no compile error — R8's two leaks are the current instances of that risk.
- `autoFollowSuppressed` means "the map was touched", not "the map was dragged": `notifyUserInteraction()` fires on ACTION_DOWN for any touch (`MapGpsFollowEffects.kt:37`) and the hold restores follow after 1–10 s (`NavigationViewModel.kt:441`).

```kotlin
// Tags follow the marker: the boat while the map follows it, the view while the user moves the map.
// `mapCenter` is that marker — the strip keeps only the zones containing it.
```

- No new parameter is needed: `mapCenter` is already a `MapContent` parameter, so the strip and the info text are fed from it directly.
- `boatPosition` then has no reader left inside `MapContent` — `MapScreen.kt:2455` and `:2462` were its only two — so the parameter goes, and with it the expression duplicated at the two call sites (`MapScreen.kt:1236, 1817`).
- Residual, logged rather than fixed: `inZone300` is fed by the same `_mapCenter` through the centre-driven shore pipeline (`NavigationViewModel.kt:463`), so the band tag and the zone tags always read one point. That agreement is a gain of this option, and the reason to leave the shore pipeline alone.

### 5.7 Implementation steps (walk item 3)

Ordered, ready to execute. Everything lands in two Kotlin files plus the screen; the composables are not touched.

1. `NavigationViewModel.kt`, beside the band state (`:215-217`) — the band's own answer at the marker, so the tag stack stops borrowing the dashboard's:
```kotlin
/** 300 m band membership of the map marker — the tag stack's own band sign (walk item 1). */
val markerInZone300: StateFlow<Boolean> = _mapCenter
    .sample(SHORE_SAMPLE_INTERVAL_MS)
    .map { repository.isIn300mZone(it.latitude, it.longitude) }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
```
The query is `CoastlineRepository.isIn300mZone` (`CoastlineRepository.kt:385`), analytic and index-backed, so it is one O(1) lookup per sampled marker — no band geometry is re-derived.

2. `MapScreen.kt:447-451` — collect it with the other zone state:
```kotlin
val markerInZone300 by viewModel.markerInZone300.collectAsState()
```

3. `MapContent` signature (`MapScreen.kt:2229-2290`) — add `markerInZone300: Boolean = false` and delete `boatPosition: LatLng? = null` (its only readers are the two below).

4. `MapContent` body, after the `visibleRegulatedZones` block (`:2330`) — the tag set, layer-independent:
```kotlin
// Tags follow the Zone categories settings and the marker — never the layer's visibility.
val tagRegulatedZones = remember(regulatedZones, appSettings) {
    filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
}
```

5. `MapScreen.kt:2453-2469` — the two consumers: `regulatedZones = tagRegulatedZones`, `boatPosition = mapCenter`, `inZone300 = markerInZone300` on both the strip and the info text.

6. Both call sites (`MapScreen.kt:1236`, `:1817`) — drop `boatPosition = gpsPosition ?: mapCenter` and pass `markerInZone300 = markerInZone300`.

7. Build and device checks as in §7; the overlay keeps `visibleRegulatedZones` and stays layer-gated, which the build cannot prove — step 6 is the one that could break it by accident, so the layer toggle is checked on device.

## 6. Accepted behaviour change

Tags now appear while the layer is hidden. Objection: a user who hides the layer to declutter the map still gets a tag whenever the marker sits in a category-enabled zone. That is the requested rule, so it is accepted rather than mitigated.

## 7. Verification

- `apk-build.bat` — package only.
- Device, layer off, inside the Cap d'Antibes speed zone: tag present.
- Device, untick Speed limit in Zone categories: tag gone.
- Device, layer on: rendering and tags unchanged from today.
- Device, GPS on and following: the marker sits on the boat and the tags match it.
- Device, GPS on and the map dragged: the tags follow the marker, and return to the boat when follow re-engages.
- Device, layer off and dragged well clear of a zone: the tag disappears while the marker is outside.
- Device, GPS on, under way, while dragging: a tag can snap back to the boat for one sample when a fix lands — expected today, see R8.
- No unit test reaches this wiring — it is Compose-level; `filterRegulatedZones` has no existing test either, and none is added (the fix is a call-graph rewire, not new logic).

## 8. Files

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | new `tagRegulatedZones`; strip and info text fed `mapCenter`; the `boatPosition` parameter removed from `MapContent` and its argument dropped at the two call sites |
| `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` | none — D1 keeps the band tag unconditional |
| `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md` | section + `## Docs` pointer on completion |

## 9. Notes

- `FEAT_HYD_RegulatedZones.md` is baked 2026-06-13 and no longer describes the shipped code (it still names `more-dedebug` as newly created and `design` at 15/18) — the hydration is refreshed by the next `#bake`.
- The tag column's painting path is `MapSurface`/`MapToggleSquare` (`RegulatedZoneComponents.kt:163`, §11 of `260916_FEAT_PLN_ColorManagement_map-surface-normalization.md`) and is untouched by this plan.

## 10. Review of this plan (2026-09-16)

- **R1 — RESOLVED 2026-09-16, Option B adopted: one point, the marker.** The review's first reading — that the map centre already is the boat — was wrong, and the user caught it: the osmdroid offset draws the boat up to 17 % of the visible map height below the screen centre, so the boat is not where the eye lands. But the value the code calls the centre is the point under that marker, so it is the boat while driving and the panned view while looking around — which is the user's rule with no branch. Rule, evidence and implementation in §5.6; R7 and R9 record how the justification and the choice moved.
- **R2 — no action: the boat-size filter still applies to tags.** `filterRegulatedZones` drops zones where `appliesTo(boatSizeM)` is false (`RegulatedZoneComponents.kt:485`), so a ≥ 24 m zone shows no tag even with its category enabled. Kept, because it matches the layer and the corridor's 6 m boat.
- **R3 — confirmed: no change is needed inside the tag composables.** Containment (`:77`), the null-speed drop for SPEED_LIMIT (`:94`), the 300 m suppression (`:96`) and the band injection (`:104`) all already encode the required rules; the defect was purely the upstream layer gate.
- **R4 — confirmed: auto-reveal narrowing cannot hide a tag.** The narrowed set is built with `isNear(boat, radiusM)`, and `isNear` returns true for a point inside the ring before any radius test (`RegulatedZoneComponents.kt:537`), so a containing zone always survives the narrowing.
- **R5 — plan hygiene from the first review:** the Files table lost the optional component edit, D1 was closed with the user's wording, and the two tag rules are stated as a table.
- **R6 — untouched by design and worth knowing:** `regulationInfoVisible` defaults to false (`SettingsManager.kt:144`), so the side text panel is opt-in; the fix applies the same source to it for consistency but changes nothing a default install sees.
- **R7 — correction from the second review: the camera lead does not move `_mapCenter`, so the case for the explicit two-case rule was overstated.** The lead drives only the MapView camera and the depth view model (`MapGpsFollowEffects.kt:86-94, 98`), while `_mapCenter` is written from the fix beside `_gpsPosition` (`NavigationViewModel.kt:731-733`) — §5.6 is corrected accordingly.
- **R8 — what "the marker" really is, and where it can differ.** The value behind the marker has two writers — the fix handler (`NavigationViewModel.kt:733`, unconditional) and the pan callback (`MapScreen.kt:980`, suppressed or demo only) — so a fix landing mid-drag snaps the value, and the tags with it, back to the boat for that sample, and while the wizard or a drawer holds the freeze (`MapGpsFollowEffects.kt:84`) the value keeps advancing with the boat while the drawn marker stays put. Neither option removes those leaks; removing them means making the fix handler respect `autoFollowSuppressed`, a small `NavigationViewModel` change and a separate decision.
- **R9 — the reference point moved from A to B after R7 and R8 landed.** The explicit `gpsPosition`-while-following variant was chosen first, then review showed it agrees with the marker value in every state and differs only by the 333 ms sampling, while costing a second source and a new parameter. Option B is recorded in §5.6, and the intent the branch would have stated lives there as a comment plus this bullet.
