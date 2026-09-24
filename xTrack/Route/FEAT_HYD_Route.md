# Context Hydration — Route — 2026-09-24

**Last Bake:** 2026-09-24 11:14 UTC — written by `#bake`

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code came from a file read, a hop's report or a subtask's own output. One delegation is named rather than glossed: the Architect tools in this session carry no shell, so the git moves (`#new`, the re-cut, the closing commit and push) and the bake's two long-line `GLOBAL_CONTEXT` edits ran through Code subtasks on the user's explicit `#`-invocations.

## State

The harness shipped on `feature/route-avoid` (cut from `origin/develop` `85ef085`, re-cut from `feature/route-dummy` `13bc023`): `RouteEngineChoice` beside the seam with `dummy` and `avoid` rows, `route.engine.id=dummy` seeded into `AppSettings.routeEngineId` with an unclaimed id falling back and reported at startup, the System-tab section between Language and Screen, and `RouteAvoidEngine` drawing the same straight line priced at the live set pace. The ViewModel's engine became a selection flow that captures the session engine on the Idle→Choosing edge and releases it on Idle, so the choice applies at the next arming and a live route keeps the engine that drew it. The Ask hop returned **revise** once — the avoid engine priced at the static property default — and a second Code hop closed it, the dropdown showing the resolved id and its label dropped so the section header is the one label; two should-fixes were declined by decision, the section-description wording (user-facing, left for the user's eye) and the dummy/avoid duplication (the two placeholders diverge next). Build green under `apk-build.bat`.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt` — the registry
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the second engine
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the selection flow and the session engine
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the lookup and the pace-provider wiring
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the new System-tab section
- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the dropdown row
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` · `config/AppConfig.kt` · `MainActivity.kt` · `maro.properties` — the persisted id and its report
- `xTrack/Route/260924_FEAT_PLN_Route_algorithm-harness.md` — the plan, with its Outcome

## Next Step

The device pass over the new Settings section and the engine switch; the section-description wording awaits the user's eye.
