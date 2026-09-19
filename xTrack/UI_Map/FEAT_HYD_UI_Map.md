# Context Hydration — UI_Map — 2026-09-19

**Last Bake:** 2026-09-19 07:35 UTC

**Directive trace:** Dependency, machine-shaped file, unordered start, device touch and unsourced claim — none arose this session, so none stopped; every claim about the code came out of a file read, and the two false claims found were this session's own and were repaired.

## State
The marker's scale is configuration and the map's zoom range is settled at 11–20: `map.marker.size.zoomExponent` at 0.35 (was the 0.45 constant), `map.marker.size.boatBaseDp` at 36.8 and `map.marker.size.dotBaseDp` at 9.2, all clamped on parse. `ZOOM_EXPONENT`, `BOAT_BASE_DP` and `DOT_BASE_DP` are deleted, so the sprite, the wizard's crosshair and the cap arrow read the settings while the reference zoom, the coast-shrink pair and the arrow's speed clamps stay code; offshore at level 20 the boat reads 256 dp against the old curve's 388 dp, the dot a quarter of it. `apk-build.bat` SUCCESSFUL with no warnings across the session's four hops, and the scoped `ui.map` + `config` run holds at 213 tests with only the six properties-versus-default reds, none of them reading the new keys. Inspect mode remains designed and unimplemented, owing its own device and code checks.

## Target Files
- `app/src/main/assets/maro.properties` — the `map.marker.size.*` keys and their documentation
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the three sizing accessors
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — the sprite's base read, the crosshair and the cap arrow
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — the 11–20 range
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md` — the plan, its two passes and their Outcome
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — the corrected curve and its tunables table

## Next Step
Device pass against the plan's two tables: the sprite at levels 19 and 20 offshore and inshore, and the 15 % raise on the base pair at each level.
