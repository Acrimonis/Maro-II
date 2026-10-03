<!-- scope: feature -->
# Route-end persistence and the short-acquisition guard

## Finding — why the ends revert on restart

- The two-mode persistence already ships: [`SettingsManager`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:180) holds `routeStartSelectionGps/Demo` and `routeDestinationSelectionGps/Demo`, and [`storeRouteEnd`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:924) writes them keyed on the mode.
- The restart loss is the R66 write-back racing the async marker load: [`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:905) builds the eligible sets from `markersViewModel.markers` — the **filtered, initially empty** list — so on startup a persisted `marker:` id resolves as dead and the [`LaunchedEffect`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:940) overwrites the store with the fallback before the markers finish loading.
- The same root also purges a flagged marker the list filter is hiding: eligibility reads the filtered list, not the source of truth.

## Fix 1 — persistence

- Build the route-end eligible sets and the selector's options from [`markersViewModel.allMarkers`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:216), the unfiltered source of truth, instead of `markers`.
- Expose the existing `isLoaded` flag as an observable and gate both R66 write-back `LaunchedEffect`s on it, so a stored marker id is never purged before the first load lands.
- Result: a flagged marker's selection survives an app restart, and a marker hidden by the list filter is no longer treated as deleted.

## Fix 2 — the short-acquisition guard

- In [`armRouteMode`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1803), resolve the pair through `routeEndsAtTrigger()`; when both ends resolve and the haversine distance between them is under `route.min.acquisition.lengthM`, do **not** arm and show a transient map toast naming the non-action.
- New `maro.properties` key `route.min.acquisition.lengthM=100` with an [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:126) accessor — one home for the threshold, default 100 m.
- The toast reuses the existing transient map banner (the `MapBanner` pill with auto-dismiss, as the import and track-op banners do), with a new string in both locale files.
- A pure, unit-tested distance gate (`routeArmDistanceOk` or similar) keeps the threshold testable without a screen.

## Build order

1. `MarkersViewModel`: expose `isLoaded` as an observable.
2. `MapScreen`: read route-end eligibility and options from `allMarkers`; gate the R66 write-backs on the loaded flag.
3. `AppConfig` + `maro.properties`: add `route.min.acquisition.lengthM`.
4. `MapScreen.armRouteMode`: the distance gate and the toast.
5. Strings in `values/strings.xml` and `values-fr/strings.xml`.
6. Unit test for the distance gate; `gradlew :app:assembleDebug :app:testDebugUnitTest`.

## Outcome

Shipped 2026-09-30 on `feature/route-card`: route-end eligibility and options read `allMarkers`, the R66
write-backs are gated on `markersLoaded` so a flagged-marker selection survives restart, and `armRouteMode`
refuses a pair under `route.min.acquisition.lengthM` (100 m) with a transient toast via the pure
`routeEndsClearMinimum`. Build and the whole unit suite green, nothing device-validated.
