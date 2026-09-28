# Context Hydration — UI_Map — 2026-09-28

**Last Bake:** 2026-09-28 20:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** No covered action class stopped in this bake — no dependency was added, no machine-shaped data file was opened, no code was touched and no device was involved, and every claim it writes came from a file read in the session. The bake itself was ordered by the user, as the cross-feature bookkeeping the Markers walk's item 11 asks for.

## State

The marker's scale is configuration and the zoom range is settled at 11–20: `map.marker.size.zoomExponent` at 0.35, `map.marker.size.boatBaseDp` at 36.8 and `map.marker.size.dotBaseDp` at 9.2, all clamped on parse, with `ZOOM_EXPONENT`, `BOAT_BASE_DP` and `DOT_BASE_DP` deleted so the sprite, the wizard's crosshair and the cap arrow share the settings. Inspect mode is **shipped**, not designed: its 2026-09-17 walk is closed and the epic carries the one-liner — the previous hydration's "remains designed and unimplemented" sentence was stale and this bake corrects it.

- **This bake's own writes.** The 2026-09-18 session's tap-zone and ray-clear work took two of this feature's files — `MapScreen.kt` and `MapMarkerEffects.kt` — and its pointer was owed here; it now sits in `## Implemented` with the plan of record, which the source plan left to "each owner's next bake".
- **The map-opened exception reaches this feature's record too.** The marker-filter entry's clause was superseded on 2026-09-28: the map draws its own filter's set with no reveal, highlighted or pinned escape, and the map-opened panel stands on a filter write while a list card closes on the list's write and a spy card on the map's. The rule lives in Ui_General's `## Rules`; `260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md` now carries the same note, since that plan is where the superseded close arms were written.
- Open in this feature's record, untouched by this bake: the device pass against the marker-sizing plan's two tables, the second-density check for the dp pass, and the row-grid Medium the dp review left open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` · `MapMarkerEffects.kt` — the tap zone's hit test and the ray overlay, now pointed at from `## Implemented`
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — the sprite's base read, the wizard's crosshair, the cap arrow and the tap zone
- `app/src/main/assets/maro.properties` — the `map.marker.size.*` keys and the six `map.marker.tap.*` keys
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the sizing and tap accessors
- `xTrack/UI_Map/260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md` — amended for the map-opened exception
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md` — the sizing curve, its two passes and their Outcome
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — the corrected curve and its tunables table
- `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md` — the cross-feature plan of record, owned by Markers

## Next Step

Nothing in code moved since 2026-09-19; this bake wrote pointers and corrected one stale claim. The next session opens on the two checks the record already carries — the sprite at levels 19 and 20 offshore and inshore with the 15 % raise on the base pair, and the strokes at a second density — with the row-grid Medium staying open until one of them answers. The walk of 2026-09-17 stays closed and this bake does not resume it.
