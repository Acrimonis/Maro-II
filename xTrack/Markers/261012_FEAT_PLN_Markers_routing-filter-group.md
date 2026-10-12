<!-- scope: feature -->
# Marker filter popup — one Routing title over the three routing axes

**Status:** in design (2026-10-12). Feature `Markers`. Planned on the `feature/mrkr-imexport` lineage; no branch was cut for this work and the user owns that call.

## Decision

The marker filter popup's three routing axes — route role, routing cost and routing-only — are grouped under one title, **Routing**, while each keeps its own card of options. Nothing about filtering changes: only which titles are drawn.

## Target shape

```
Icon          [card]                                  (unchanged)
Pinned        [card]                                  (unchanged)
Origin        [card]                                  (unchanged)
Routing                                               <- one title, new key filter_group_routing
              [card: All · Origin or Dest · None]
              [card: All · Route cost · No route cost]
              [card: All · Route Only · Normal]
```

The card order is **role, cost, Only** — the order the user listed. The surviving title rides the first of the three.

Both marker surfaces render through `FilterControl`, so the list overlay and the menu drawer MARKERS section take the change together.

## Mechanism — the user's own simplification

No new field on `FilterAxisSpec`. The three axes carry the **same** `labelResId`, and `FilterControl` draws a `PopupSectionTitle` only when the axis's title differs from the previously drawn axis's title. One comparison, no type change, no schema change.

Consequence to record: two axes sharing a label id now merge their titles silently. No spec does that today, so nothing else moves; a future axis must not reuse a neighbour's key by accident.

Rejected alternatives, both named so they are not re-proposed:

- one axis carrying all nine options in a single card — ruled out by the user's "each still in their own card";
- an optional `groupLabelResId` on `FilterAxisSpec` — more machinery than the change needs, and the user rejected it.

## Edits

1. `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt`
   `markerFilterAxes()`: reorder to `routeRole`, `routeCost`, `routingOnly`; all three take `labelResId = R.string.filter_group_routing`. `icon`, `pinned` and `origin` keep their own titles and their own order.
2. `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt`
   `FilterControl`: hold the title key drawn last and emit `PopupSectionTitle` only when it changes. Every axis keeps its own `PopupSectionCard`. The track, route and menu popups carry no repeated key, so they render exactly as before.
3. String resources, both locales
   - add `filter_group_routing` — EN `Routing`, FR `Routage`.
   - revalue `filter_option_no_route_role` — EN `None`, FR `Aucun`.
   - retire `filter_axis_route_cost`, `filter_axis_route_role` and `filter_axis_routing_only` from both files.
   - every other option label is untouched: `filter_option_all`, `filter_option_route_role` = `Origin or Dest`, `filter_option_route_cost` = `Route cost`, `filter_option_no_route_cost` = `No route cost`, `filter_option_routing_only` = `Route Only`, `filter_option_normal` = `Normal`.
4. `docs/ui-lists-guidelines.md`
   the Marker Filter Axes table is already stale — it lists only `icon`, `pinned` and `origin` — so add the three routing rows and the shared title, and note the shared-title rule beside the `FilterAxisSpec` snippet.
5. `docs/ui-component-guidelines.md` §2.10 — one sentence for the shared-title rule if the canonical popup shape needs it.

## Scope fence

Presentational only. No change to `UserMarker.matchesFilter`, `ListFilter` persistence, `SettingsManager`, the map filter, the wizard's Routing cost step, the card marks or `maro.properties`. `MarkerFilterMigrationTest` is expected to stay green untouched.

## Verification

- `gradlew :app:assembleDebug` green.
- the marker suites green, and the track, route and menu filter popups confirmed unchanged.
- device pass owed to the user: the three cards under the one Routing title, in the list overlay and in the menu drawer, in both locales.

## Implemented

**Shipped 2026-10-12.** Every decided rule above is in the code. The change is presentational only, and the device pass is owed and is the user's.

- **The grouping** — [`ListFilter.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:193): `markerFilterAxes()` now holds the three routing axes in the order `routeRole` · `routeCost` · `routingOnly`, all three carrying `labelResId = R.string.filter_group_routing`; `icon`, `pinned` and `origin` keep their own titles and their own order.
- **The mechanism** — [`ListOverlayScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:294): `FilterControl` holds the title id drawn last and emits `PopupSectionTitle` only when the axis's id differs from it — a repeated key is one group — while every axis still draws its own `PopupSectionCard`; the row-tap-writes-live behaviour and the popup's dismissal rule are untouched. No new field on `FilterAxisSpec`.
- **Strings** — `filter_group_routing` added (EN `Routing` · FR `Routage`), `filter_option_no_route_role` revalued (EN `None` · FR `Aucun`), and `filter_axis_route_cost` / `filter_axis_route_role` / `filter_axis_routing_only` retired from both locales; every other option label is as it was.
- **Docs** — the Marker Filter Axes table in [`ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:132) gained the three routing rows and the shared-title rule beside the `FilterAxisSpec` snippet; [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:399) §2.10 gained one sentence for the same rule.
- **Decision taken in the user's silence** — the French group word **`Routage`**, one line to reverse.

**Tests:** `apk-build.bat` (`:app:assembleDebug`) BUILD SUCCESSFUL; the marker suites green via `gradlew.bat :app:testDebugUnitTest --tests "*Marker*"` with [`MarkerFilterMigrationTest`](../../app/src/test/java/ykws/android/maro/data/model/MarkerFilterMigrationTest.kt:1) untouched. The track, route and menu filter popups are unchanged — the route and track controls both feed `trackFilterAxes()`, whose three titles all differ. The device pass is owed and is the user's.
